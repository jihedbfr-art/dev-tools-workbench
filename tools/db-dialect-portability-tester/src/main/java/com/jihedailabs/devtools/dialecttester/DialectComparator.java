package com.jihedailabs.devtools.dialecttester;

import java.util.ArrayList;
import java.util.List;

/**
 * Replays a script against two engines in the same order and collects every disagreement.
 *
 * <p>Order matters and the two runs are independent: a statement that fails on one engine still
 * runs on the other, because stopping at the first divergence would hide the ones behind it. That
 * does mean a failed CREATE TABLE cascades into the statements that depend on it — the report says
 * which statement was the first to diverge, and that one is the one to fix.
 */
public final class DialectComparator {

    private final String leftName;
    private final String rightName;

    public DialectComparator(String leftName, String rightName) {
        this.leftName = leftName;
        this.rightName = rightName;
    }

    public PortabilityReport compare(List<SqlScript.Statement> statements,
                                     DatabaseSession left,
                                     DatabaseSession right) {
        List<Divergence> divergences = new ArrayList<>();

        for (SqlScript.Statement statement : statements) {
            ExecutionOutcome leftOutcome = left.run(statement.sql());
            ExecutionOutcome rightOutcome = right.run(statement.sql());
            divergences.addAll(compareOutcomes(statement, leftOutcome, rightOutcome));
        }

        return new PortabilityReport(leftName, rightName, statements.size(), divergences);
    }

    private List<Divergence> compareOutcomes(SqlScript.Statement statement,
                                             ExecutionOutcome left,
                                             ExecutionOutcome right) {
        List<Divergence> found = new ArrayList<>();
        String label = statement.display();
        int index = statement.index();

        if (left instanceof ExecutionOutcome.Rejected leftRejected
                && right instanceof ExecutionOutcome.Rejected rightRejected) {
            found.add(new Divergence(Divergence.Kind.REJECTED_BY_BOTH, index, label,
                    leftName + ": " + leftRejected.message() + " | "
                            + rightName + ": " + rightRejected.message()));
            return found;
        }

        if (left instanceof ExecutionOutcome.Rejected rejected) {
            found.add(new Divergence(Divergence.Kind.REJECTED_BY_ONE, index, label,
                    rightName + " accepted it; " + leftName + " refused: " + rejected.message()));
            return found;
        }
        if (right instanceof ExecutionOutcome.Rejected rejected) {
            found.add(new Divergence(Divergence.Kind.REJECTED_BY_ONE, index, label,
                    leftName + " accepted it; " + rightName + " refused: " + rejected.message()));
            return found;
        }

        if (left instanceof ExecutionOutcome.Updated leftUpdated
                && right instanceof ExecutionOutcome.Updated rightUpdated) {
            if (leftUpdated.count() != rightUpdated.count()) {
                found.add(new Divergence(Divergence.Kind.UPDATE_COUNT, index, label,
                        leftName + " affected " + leftUpdated.count() + " row(s), "
                                + rightName + " affected " + rightUpdated.count()));
            }
            return found;
        }

        if (left instanceof ExecutionOutcome.Rows leftRows && right instanceof ExecutionOutcome.Rows rightRows) {
            found.addAll(compareRows(index, label, leftRows, rightRows));
            return found;
        }

        found.add(new Divergence(Divergence.Kind.RESULT_SHAPE, index, label,
                leftName + " returned " + shapeOf(left) + ", " + rightName + " returned " + shapeOf(right)));
        return found;
    }

    private List<Divergence> compareRows(int index, String label,
                                         ExecutionOutcome.Rows left, ExecutionOutcome.Rows right) {
        List<Divergence> found = new ArrayList<>();

        for (int c = 0; c < Math.min(left.columnTypes().size(), right.columnTypes().size()); c++) {
            String leftType = left.columnTypes().get(c);
            String rightType = right.columnTypes().get(c);
            if (!sameType(leftType, rightType)) {
                found.add(new Divergence(Divergence.Kind.COLUMN_TYPE, index, label,
                        "column " + (c + 1) + " is " + leftType + " on " + leftName
                                + " and " + rightType + " on " + rightName));
            }
        }

        if (left.rowCount() != right.rowCount()) {
            found.add(new Divergence(Divergence.Kind.ROW_COUNT, index, label,
                    leftName + " returned " + left.rowCount() + " row(s), "
                            + rightName + " returned " + right.rowCount()));
            return found;
        }

        for (int r = 0; r < left.rowCount(); r++) {
            List<String> leftRow = left.values().get(r);
            List<String> rightRow = right.values().get(r);
            for (int c = 0; c < Math.min(leftRow.size(), rightRow.size()); c++) {
                if (!leftRow.get(c).equals(rightRow.get(c))) {
                    found.add(new Divergence(Divergence.Kind.CELL_VALUE, index, label,
                            "row " + (r + 1) + ", column " + (c + 1) + ": "
                                    + leftName + " gave " + leftRow.get(c) + ", "
                                    + rightName + " gave " + rightRow.get(c)));
                }
            }
        }
        return found;
    }

    /**
     * Type names that mean the same thing on both engines are not a difference.
     *
     * <p>PostgreSQL says {@code character varying} where H2 says {@code VARCHAR}, {@code int4}
     * where H2 says {@code INTEGER}. Reporting those would be reporting the driver's vocabulary,
     * not the schema.
     */
    private static boolean sameType(String left, String right) {
        return canonicalType(left).equals(canonicalType(right));
    }

    private static String canonicalType(String typeName) {
        String t = typeName == null ? "" : typeName.trim().toUpperCase().replace(" ", "");
        return switch (t) {
            case "CHARACTERVARYING", "VARCHAR" -> "VARCHAR";
            case "CHARACTER", "BPCHAR", "CHAR" -> "CHAR";
            case "INT4", "INT", "INTEGER", "SERIAL" -> "INTEGER";
            case "INT8", "BIGINT", "BIGSERIAL" -> "BIGINT";
            case "INT2", "SMALLINT" -> "SMALLINT";
            case "BOOL", "BOOLEAN" -> "BOOLEAN";
            case "FLOAT8", "DOUBLEPRECISION", "DOUBLE" -> "DOUBLE";
            case "FLOAT4", "REAL" -> "REAL";
            case "NUMERIC", "DECIMAL" -> "NUMERIC";
            case "TIMESTAMP", "TIMESTAMPWITHOUTTIMEZONE" -> "TIMESTAMP";
            case "TIMESTAMPTZ", "TIMESTAMPWITHTIMEZONE" -> "TIMESTAMPTZ";
            case "TEXT", "CLOB", "CHARACTERLARGEOBJECT" -> "TEXT";
            case "BYTEA", "BLOB", "BINARYLARGEOBJECT", "VARBINARY" -> "BINARY";
            default -> t;
        };
    }

    private static String shapeOf(ExecutionOutcome outcome) {
        if (outcome instanceof ExecutionOutcome.Rows rows) {
            return "a result set of " + rows.rowCount() + " row(s)";
        }
        if (outcome instanceof ExecutionOutcome.Updated updated) {
            return "an update count of " + updated.count();
        }
        return "an error";
    }
}
