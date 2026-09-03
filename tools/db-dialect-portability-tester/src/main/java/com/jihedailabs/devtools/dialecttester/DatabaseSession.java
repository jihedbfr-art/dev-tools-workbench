package com.jihedailabs.devtools.dialecttester;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

/**
 * One engine's side of a comparison run, wrapped so the script leaves nothing behind.
 *
 * <p>The whole replay happens inside a single transaction that is rolled back at the end. Both
 * engines here have transactional DDL, so the tables a script creates disappear with it — which is
 * what makes it safe to point this at a database that already has data in it, and what makes the
 * run repeatable instead of failing the second time with "relation already exists".
 *
 * <p>Each statement gets a savepoint first. Without one, a statement PostgreSQL rejects aborts the
 * surrounding transaction, and every statement after it fails with "current transaction is aborted"
 * — turning one real divergence into a page of fake ones.
 */
public final class DatabaseSession implements AutoCloseable {

    private final String name;
    private final Connection connection;
    private int savepointCounter;

    public DatabaseSession(String name, Connection connection) throws SQLException {
        this.name = name;
        this.connection = connection;
        connection.setAutoCommit(false);
    }

    public String name() {
        return name;
    }

    public ExecutionOutcome run(String sql) {
        Savepoint savepoint = null;
        try {
            savepoint = connection.setSavepoint("s" + (++savepointCounter));
        } catch (SQLException e) {
            // An engine without savepoint support still gets compared; it just loses the isolation
            // between statements, which the report cannot help.
            savepoint = null;
        }

        ExecutionOutcome outcome = StatementRunner.run(connection, sql);

        if (outcome instanceof ExecutionOutcome.Rejected && savepoint != null) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException e) {
                return new ExecutionOutcome.Rejected(
                        outcome instanceof ExecutionOutcome.Rejected rejected ? rejected.message() : e.getMessage());
            }
        }
        return outcome;
    }

    /** Undoes everything the script did. Called on close, so it happens even on an exception. */
    @Override
    public void close() throws SQLException {
        try {
            connection.rollback();
        } finally {
            connection.close();
        }
    }
}
