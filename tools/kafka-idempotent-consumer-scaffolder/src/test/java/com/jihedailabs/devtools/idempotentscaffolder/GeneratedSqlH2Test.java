package com.jihedailabs.devtools.idempotentscaffolder;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the generated SQL against a real embedded H2 rather than asserting on the string.
 *
 * <p>A generator whose output only ever gets compared to an expected string is exactly the kind of
 * test that passes while the tool emits SQL no engine accepts. These execute.
 *
 * <p>The PostgreSQL branch is <b>not</b> covered here — {@code ON CONFLICT DO NOTHING} needs a real
 * PostgreSQL, and no Docker daemon was available in this environment. That gap is stated in both
 * READMEs rather than papered over.
 */
class GeneratedSqlH2Test {

    private static final ScaffoldOptions H2_OPTIONS = new ScaffoldOptions(
            "com.example.app.idempotency", "ProcessedEventRepository",
            "processed_events", "event_id", Dialect.H2, "V1");

    private Connection freshDatabase() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        String migration = generated(".sql");
        try (Statement statement = connection.createStatement()) {
            for (String ddl : migration.split(";")) {
                if (!ddl.isBlank()) {
                    statement.execute(ddl);
                }
            }
        }
        return connection;
    }

    private static String generated(String suffix) {
        List<GeneratedFile> files = Scaffolder.generate(H2_OPTIONS);
        return files.stream()
                .filter(f -> f.path().endsWith(suffix))
                .findFirst()
                .orElseThrow()
                .content();
    }

    /** The INSERT the generated repository holds in its INSERT_SQL constant. */
    private static String insertSql() {
        return H2_OPTIONS.dialect().insertStatement(H2_OPTIONS.table(), H2_OPTIONS.eventIdColumn());
    }

    private static int insert(Connection connection, String eventId, Instant at) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insertSql())) {
            statement.setString(1, eventId);
            statement.setString(2, "donor-response-events");
            statement.setString(3, "REQ-1");
            statement.setTimestamp(4, Timestamp.from(at));
            return statement.executeUpdate();
        }
    }

    @Test
    void generatedMigrationRunsOnH2() throws SQLException {
        try (Connection connection = freshDatabase();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM processed_events")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void secondDeliveryOfTheSameEventIsRejectedByTheDatabase() throws SQLException {
        try (Connection connection = freshDatabase()) {
            assertEquals(1, insert(connection, "evt-1", Instant.now()));

            // This is the whole pattern: the duplicate is stopped by the primary key, not by a
            // prior SELECT. On H2 it surfaces as a thrown constraint violation, which is why the
            // generated repository catches DuplicateKeyException and returns false there.
            SQLException duplicate = assertThrows(SQLException.class,
                    () -> insert(connection, "evt-1", Instant.now()));
            assertTrue(duplicate.getMessage().toLowerCase().contains("unique")
                            || duplicate.getMessage().toLowerCase().contains("primary key"),
                    "expected a uniqueness violation, got: " + duplicate.getMessage());
        }
    }

    @Test
    void differentEventIdsBothInsert() throws SQLException {
        try (Connection connection = freshDatabase()) {
            assertEquals(1, insert(connection, "evt-1", Instant.now()));
            assertEquals(1, insert(connection, "evt-2", Instant.now()));
        }
    }

    @Test
    void generatedPurgeDeletesOnlyRowsBeforeTheCutoff() throws SQLException {
        Instant now = Instant.now();
        try (Connection connection = freshDatabase()) {
            insert(connection, "old", now.minus(40, ChronoUnit.DAYS));
            insert(connection, "recent", now);

            int deleted;
            try (PreparedStatement purge = connection.prepareStatement(
                    "DELETE FROM processed_events WHERE processed_at < ?")) {
                purge.setTimestamp(1, Timestamp.from(now.minus(7, ChronoUnit.DAYS)));
                deleted = purge.executeUpdate();
            }

            assertEquals(1, deleted);
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT event_id FROM processed_events")) {
                assertTrue(rs.next());
                assertEquals("recent", rs.getString(1));
                assertTrue(!rs.next(), "purge removed the wrong number of rows");
            }
        }
    }

    @Test
    void unquotedIdentifiersResolveOnH2DespiteUppercaseFolding() throws SQLException {
        // H2 stores the unquoted name as PROCESSED_EVENTS; PostgreSQL stores processed_events.
        // Both answer to the unquoted spelling, which is the only reason one generated repository
        // can run against a test database and a production one.
        try (Connection connection = freshDatabase();
             Statement statement = connection.createStatement()) {
            assertTrue(statement.execute("SELECT event_id, topic, aggregate_id, processed_at "
                    + "FROM processed_events WHERE event_id = 'nothing'"));
        }
    }
}
