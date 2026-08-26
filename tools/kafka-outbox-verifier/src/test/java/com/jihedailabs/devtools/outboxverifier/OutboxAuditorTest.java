package com.jihedailabs.devtools.outboxverifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Table shape mirrors bpmn-provisioning-patterns' real portability_outbox: id, created_at,
 * published_at (null while pending), failed_at (null unless dead-lettered), attempts.
 */
class OutboxAuditorTest {

    private Connection connection;
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE portability_outbox (
                        id VARCHAR(36) PRIMARY KEY,
                        created_at TIMESTAMP NOT NULL,
                        published_at TIMESTAMP NULL,
                        failed_at TIMESTAMP NULL,
                        attempts INT NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    private void insertRow(String id, Instant createdAt, Instant publishedAt, Instant failedAt, int attempts) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO portability_outbox (id, created_at, published_at, failed_at, attempts) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setTimestamp(2, Timestamp.from(createdAt));
            statement.setTimestamp(3, publishedAt == null ? null : Timestamp.from(publishedAt));
            statement.setTimestamp(4, failedAt == null ? null : Timestamp.from(failedAt));
            statement.setInt(5, attempts);
            statement.executeUpdate();
        }
    }

    private OutboxAuditor auditorWithAllColumns() {
        OutboxColumns columns = new OutboxColumns("id", "created_at", "published_at", "failed_at", "attempts");
        return new OutboxAuditor(connection, "portability_outbox", columns, Thresholds.defaults());
    }

    @Test
    void healthyOutboxReportsNoIssues() throws SQLException {
        // Published a few seconds after creation, purged well within the bloat window.
        insertRow("healthy-1", NOW.minusSeconds(30), NOW.minusSeconds(28), null, 0);
        // Still pending, but well under the 5-minute stagnation threshold.
        insertRow("healthy-2", NOW.minusSeconds(10), null, null, 0);

        OutboxReport report = auditorWithAllColumns().audit(NOW);

        assertTrue(report.isHealthy());
        assertEquals(2, report.totalRows());
        assertEquals(0, report.failedCount());
    }

    @Test
    void pendingRowPastStagnationThresholdIsFlagged() throws SQLException {
        insertRow("stuck-1", NOW.minus(Duration.ofMinutes(10)), null, null, 0);
        insertRow("fine-1", NOW.minusSeconds(5), null, null, 0);

        OutboxReport report = auditorWithAllColumns().audit(NOW);

        assertEquals(List.of("stuck-1"), report.stagnantRowIds());
        assertFalse(report.isHealthy());
    }

    @Test
    void completedRowNotPurgedPastBloatThresholdIsCounted() throws SQLException {
        insertRow("bloated-1", NOW.minus(Duration.ofHours(48)), NOW.minus(Duration.ofHours(47)), null, 0);
        insertRow("purged-1", NOW.minusSeconds(60), NOW.minusSeconds(58), null, 0);

        OutboxReport report = auditorWithAllColumns().audit(NOW);

        assertEquals(1, report.bloatedCount());
        assertFalse(report.isHealthy());
    }

    @Test
    void retryLoopIsFlaggedAtThreshold() throws SQLException {
        insertRow("looping-1", NOW.minusSeconds(30), null, null, 5); // == default threshold
        insertRow("retrying-a-bit", NOW.minusSeconds(30), null, null, 2);

        OutboxReport report = auditorWithAllColumns().audit(NOW);

        assertEquals(List.of("looping-1"), report.retryLoopRowIds());
        assertFalse(report.isHealthy());
    }

    @Test
    void failedRowsAreCountedButDoNotAloneMakeTheOutboxUnhealthy() throws SQLException {
        // A dead-lettered row after exhausting retries is expected behaviour, not a relay bug —
        // it's surfaced as information, not folded into isHealthy().
        insertRow("dead-lettered-1", NOW.minus(Duration.ofMinutes(30)), null, NOW.minus(Duration.ofMinutes(20)), 3);

        OutboxReport report = auditorWithAllColumns().audit(NOW);

        assertEquals(1, report.failedCount());
        assertTrue(report.stagnantRowIds().isEmpty(), "a failed row is not pending, so it must not also count as stagnant");
        assertTrue(report.isHealthy());
    }

    @Test
    void latencyPercentilesReflectPublishDelay() throws SQLException {
        insertRow("fast", NOW.minusSeconds(100), NOW.minusSeconds(99), null, 0);   // 1s
        insertRow("medium", NOW.minusSeconds(100), NOW.minusSeconds(95), null, 0); // 5s
        insertRow("slow", NOW.minusSeconds(100), NOW.minusSeconds(50), null, 0);   // 50s

        OutboxReport report = auditorWithAllColumns().audit(NOW);

        assertEquals(Duration.ofSeconds(5), report.latencyP50());
        assertEquals(Duration.ofSeconds(50), report.latencyP95());
        assertEquals(Duration.ofSeconds(50), report.latencyP99());
    }

    @Test
    void worksWithoutOptionalFailedAtAndRetryColumns() throws SQLException {
        // Some outbox tables genuinely don't track retries or dead-lettering separately —
        // the auditor must not assume those columns exist.
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE minimal_outbox (
                        id VARCHAR(36) PRIMARY KEY,
                        created_at TIMESTAMP NOT NULL,
                        sent_at TIMESTAMP NULL
                    )
                    """);
            statement.execute("INSERT INTO minimal_outbox (id, created_at, sent_at) VALUES ('m-1', '2026-08-25 11:59:00', '2026-08-25 11:59:01')");
        }

        OutboxColumns columns = new OutboxColumns("id", "created_at", "sent_at", null, null);
        OutboxAuditor auditor = new OutboxAuditor(connection, "minimal_outbox", columns, Thresholds.defaults());

        OutboxReport report = auditor.audit(NOW);

        assertEquals(1, report.totalRows());
        assertEquals(0, report.failedCount());
        assertTrue(report.retryLoopRowIds().isEmpty());
        assertTrue(report.isHealthy());
    }

    @Test
    void percentileOfSingleValueIsThatValue() {
        Duration only = Duration.ofSeconds(7);
        assertEquals(only, OutboxAuditor.percentile(List.of(only), 0.50));
        assertEquals(only, OutboxAuditor.percentile(List.of(only), 0.99));
    }

    @Test
    void percentileOfEmptyListIsNull() {
        assertNull(OutboxAuditor.percentile(List.of(), 0.50));
    }
}
