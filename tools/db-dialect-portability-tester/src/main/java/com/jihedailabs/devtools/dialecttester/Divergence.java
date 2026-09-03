package com.jihedailabs.devtools.dialecttester;

/**
 * One place the two engines disagreed about the same statement.
 *
 * <p>{@link Kind#COLUMN_TYPE} is a warning rather than a failure: {@code VARCHAR} on one engine and
 * {@code character varying} on the other is naming, not behaviour, and failing a build on it would
 * teach people to ignore the tool. Everything else changes what the application sees.
 */
public record Divergence(Kind kind, int statementIndex, String statement, String detail) {

    public enum Kind {
        /** One engine executed the statement and the other refused it. */
        REJECTED_BY_ONE(true),
        /** Both refused it, for different reasons - usually a genuine error in the script. */
        REJECTED_BY_BOTH(false),
        /** Same query, different number of rows. */
        ROW_COUNT(true),
        /** Same row, different value in a cell. */
        CELL_VALUE(true),
        /** Same statement, different number of rows affected. */
        UPDATE_COUNT(true),
        /** One returned rows where the other reported an update count. */
        RESULT_SHAPE(true),
        /** Same data, different column type name. */
        COLUMN_TYPE(false);

        private final boolean failure;

        Kind(boolean failure) {
            this.failure = failure;
        }

        /** True when the difference can change application behaviour, not just its spelling. */
        public boolean isFailure() {
            return failure;
        }
    }

    public boolean isFailure() {
        return kind.isFailure();
    }
}
