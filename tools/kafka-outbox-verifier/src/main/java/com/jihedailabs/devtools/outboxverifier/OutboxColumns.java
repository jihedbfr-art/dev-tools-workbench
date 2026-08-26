package com.jihedailabs.devtools.outboxverifier;

/**
 * Names the columns this outbox table actually uses. There is no assumed status enum — most
 * real outbox tables (this one included, see bpmn-provisioning-patterns' portability_outbox)
 * track state as nullable timestamps instead: a row is pending while its "completed at" column
 * is null, sent once it's set, and failed once (if the table has one) its "failed at" column is
 * set. {@code failedAtColumn} and {@code retryCountColumn} are optional — pass null when the
 * table has no such column, and that signal is simply skipped rather than guessed at.
 */
public record OutboxColumns(
        String idColumn,
        String createdAtColumn,
        String completedAtColumn,
        String failedAtColumn,
        String retryCountColumn
) {
    public OutboxColumns {
        if (idColumn == null || idColumn.isBlank()) {
            throw new IllegalArgumentException("idColumn is required");
        }
        if (createdAtColumn == null || createdAtColumn.isBlank()) {
            throw new IllegalArgumentException("createdAtColumn is required");
        }
        if (completedAtColumn == null || completedAtColumn.isBlank()) {
            throw new IllegalArgumentException("completedAtColumn is required");
        }
    }

    public boolean hasFailedAtColumn() {
        return failedAtColumn != null && !failedAtColumn.isBlank();
    }

    public boolean hasRetryCountColumn() {
        return retryCountColumn != null && !retryCountColumn.isBlank();
    }
}
