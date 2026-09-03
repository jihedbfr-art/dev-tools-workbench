package com.jihedailabs.devtools.dialecttester;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Runs one statement on one connection and records what happened, including the failure. */
public final class StatementRunner {

    /** Rows read from a single statement before the comparison gives up on it. */
    static final int MAX_ROWS = 10_000;

    private StatementRunner() {
    }

    public static ExecutionOutcome run(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                return new ExecutionOutcome.Updated(statement.getUpdateCount());
            }
            try (ResultSet rs = statement.getResultSet()) {
                return readRows(rs);
            }
        } catch (SQLException e) {
            // The message is the payload here, not an error to swallow: "this engine refuses the
            // statement and that one accepts it" is the single most useful thing the tool reports.
            return new ExecutionOutcome.Rejected(oneLine(e.getMessage()));
        }
    }

    private static ExecutionOutcome readRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();

        List<String> columnTypes = new ArrayList<>(columns);
        for (int c = 1; c <= columns; c++) {
            columnTypes.add(meta.getColumnTypeName(c));
        }

        List<List<String>> values = new ArrayList<>();
        while (rs.next() && values.size() < MAX_ROWS) {
            List<String> row = new ArrayList<>(columns);
            for (int c = 1; c <= columns; c++) {
                row.add(ExecutionOutcome.normalise(rs.getObject(c), columnTypes.get(c - 1)));
            }
            values.add(row);
        }
        return new ExecutionOutcome.Rows(columnTypes, values);
    }

    private static String oneLine(String message) {
        if (message == null) {
            return "(no message)";
        }
        String flat = message.replaceAll("\\s+", " ").trim();
        return flat.length() <= 300 ? flat : flat.substring(0, 297) + "...";
    }
}
