package com.jihedailabs.devtools.idempotentscaffolder;

import java.util.regex.Pattern;

/**
 * Everything the generator needs, already validated.
 *
 * <p>The identifier rules here are deliberately narrow: lowercase, unquoted, no reserved
 * punctuation. That is not stylistic. Quoted mixed-case identifiers are the exact trap that broke
 * {@code kafka-outbox-verifier} in this ecosystem — H2 folds unquoted names to UPPERCASE and
 * PostgreSQL folds them to lowercase, so a quoted {@code "processed_events"} created on one engine
 * cannot be found by the same query on the other. Refusing anything that would need quoting means
 * the generated SQL can never land in that state.
 */
public record ScaffoldOptions(
        String packageName,
        String className,
        String table,
        String eventIdColumn,
        Dialect dialect,
        String migrationVersion) {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final Pattern JAVA_PACKAGE = Pattern.compile("[a-z_][a-z0-9_]*([.][a-z_][a-z0-9_]*)*");
    private static final Pattern JAVA_CLASS = Pattern.compile("[A-Z][A-Za-z0-9_]*");
    private static final Pattern MIGRATION_VERSION = Pattern.compile("V[0-9]+(_[0-9]+)*");

    public ScaffoldOptions {
        requireMatch(JAVA_PACKAGE, packageName, "package",
                "must be a lowercase dotted Java package, e.g. com.example.app.idempotency");
        requireMatch(JAVA_CLASS, className, "class name",
                "must be a Java class name starting with an uppercase letter");
        requireMatch(SQL_IDENTIFIER, table, "table",
                "must be a lowercase unquoted SQL identifier — see the note on identifier folding");
        requireMatch(SQL_IDENTIFIER, eventIdColumn, "event id column",
                "must be a lowercase unquoted SQL identifier — see the note on identifier folding");
        requireMatch(MIGRATION_VERSION, migrationVersion, "migration version",
                "must look like a Flyway version prefix, e.g. V1 or V2_1");
    }

    private static void requireMatch(Pattern pattern, String value, String what, String rule) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + what + " '" + value + "' — " + rule);
        }
    }

    /** Source path the generated repository belongs at, relative to a source root. */
    public String repositoryPath() {
        return packageName.replace('.', '/') + "/" + className + ".java";
    }

    /** Migration path, following the Flyway convention the reference project uses. */
    public String migrationPath() {
        return "db/migration/" + migrationVersion + "__" + table + ".sql";
    }
}
