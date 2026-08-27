package com.jihedailabs.devtools.idempotentscaffolder;

import java.util.Locale;

/**
 * The one thing that genuinely differs between databases in this pattern: how the insert of an
 * already-seen event id reports "nothing was inserted".
 *
 * <p>PostgreSQL can say it in the statement itself ({@code ON CONFLICT DO NOTHING} returns an
 * update count of 0). H2 has no such clause outside PostgreSQL compatibility mode, so the
 * duplicate surfaces as a constraint violation the caller has to catch. The generated repository
 * handles both, because a project that tests on H2 and runs on PostgreSQL — the common case, and
 * the one that produces the bug this ecosystem already hit — goes through both paths.
 */
public enum Dialect {

    POSTGRES,
    H2;

    public static Dialect parse(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "postgres", "postgresql" -> POSTGRES;
            case "h2" -> H2;
            default -> throw new IllegalArgumentException(
                    "Unknown dialect '" + raw + "' — supported values are: postgres, h2");
        };
    }

    /** The INSERT the generated repository will run, with the four positional parameters. */
    public String insertStatement(String table, String eventIdColumn) {
        String base = "INSERT INTO " + table
                + " (" + eventIdColumn + ", topic, aggregate_id, processed_at) VALUES (?, ?, ?, ?)";
        return this == POSTGRES
                ? base + " ON CONFLICT (" + eventIdColumn + ") DO NOTHING"
                : base;
    }

    /** True when a duplicate shows up as an update count of 0 rather than as a thrown exception. */
    public boolean reportsDuplicateAsZeroRows() {
        return this == POSTGRES;
    }

    public String displayName() {
        return this == POSTGRES ? "PostgreSQL" : "H2";
    }
}
