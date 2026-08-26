package com.jihedailabs.devtools.outboxverifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads an outbox table exactly once and computes every signal in Java rather than in SQL — H2
 * and PostgreSQL diverge on date arithmetic and CAST behavior in ways that have already broken
 * this exact kind of table once in this ecosystem (see bpmn-provisioning-patterns'
 * OutboxRepository.markFailed, which relied on an untyped CASE expression that PostgreSQL
 * refused to run). A plain SELECT plus in-memory comparisons sidesteps that whole class of bug.
 */
public class OutboxAuditor {

    private final Connection connection;
    private final String tableName;
    private final OutboxColumns columns;
    private final Thresholds thresholds;

    public OutboxAuditor(Connection connection, String tableName, OutboxColumns columns, Thresholds thresholds) {
        this.connection = connection;
        this.tableName = tableName;
        this.columns = columns;
        this.thresholds = thresholds;
    }

    public OutboxReport audit(Instant now) throws SQLException {
        List<OutboxRow> rows = fetchRows();

        List<String> stagnant = new ArrayList<>();
        List<String> retryLoops = new ArrayList<>();
        List<Duration> latencies = new ArrayList<>();
        int bloated = 0;
        int failed = 0;

        for (OutboxRow row : rows) {
            if (row.isPending() && Duration.between(row.createdAt(), now).compareTo(thresholds.stagnationThreshold()) > 0) {
                stagnant.add(row.id());
            }
            if (row.isCompleted()) {
                latencies.add(Duration.between(row.createdAt(), row.completedAt()));
                if (Duration.between(row.completedAt(), now).compareTo(thresholds.bloatThreshold()) > 0) {
                    bloated++;
                }
            }
            if (row.isFailed()) {
                failed++;
            }
            if (row.retryCount() != null && row.retryCount() >= thresholds.retryLoopThreshold()) {
                retryLoops.add(row.id());
            }
        }

        return new OutboxReport(
                rows.size(),
                stagnant,
                bloated,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                percentile(latencies, 0.99),
                failed,
                retryLoops
        );
    }

    private List<OutboxRow> fetchRows() throws SQLException {
        // Identifiers are interpolated unquoted on purpose: column/table names are operator-
        // supplied CLI input, not untrusted request data, so injection isn't the concern — but
        // quoting them would be. A quoted identifier is case-sensitive; an unquoted one gets
        // folded by each database's own default (uppercase on H2, lowercase on PostgreSQL),
        // which is exactly how the table was created if the operator didn't quote it either.
        // Quoting here would only make this tool disagree with the database about its own table.
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(columns.idColumn()).append(", ")
                .append(columns.createdAtColumn()).append(", ")
                .append(columns.completedAtColumn());
        if (columns.hasFailedAtColumn()) {
            sql.append(", ").append(columns.failedAtColumn());
        }
        if (columns.hasRetryCountColumn()) {
            sql.append(", ").append(columns.retryCountColumn());
        }
        sql.append(" FROM ").append(tableName);

        List<OutboxRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString());
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String id = String.valueOf(resultSet.getObject(1));
                Instant createdAt = toInstant(resultSet.getTimestamp(2));
                Instant completedAt = toInstant(resultSet.getTimestamp(3));
                Instant failedAt = columns.hasFailedAtColumn() ? toInstant(resultSet.getTimestamp(4)) : null;
                int retryColumnIndex = columns.hasFailedAtColumn() ? 5 : 4;
                Integer retryCount = null;
                if (columns.hasRetryCountColumn()) {
                    int value = resultSet.getInt(retryColumnIndex);
                    retryCount = resultSet.wasNull() ? null : value;
                }
                rows.add(new OutboxRow(id, createdAt, completedAt, failedAt, retryCount));
            }
        }
        return rows;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    static Duration percentile(List<Duration> values, double p) {
        if (values.isEmpty()) {
            return null;
        }
        List<Duration> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(p * sorted.size());
        int index = Math.max(0, Math.min(sorted.size() - 1, rank - 1));
        return sorted.get(index);
    }
}
