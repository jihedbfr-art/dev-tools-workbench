package com.jihedailabs.devtools.dialecttester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * What one engine did with one statement: rows, an update count, or a refusal.
 *
 * <p>Values are captured as normalised strings rather than as their JDBC types. Comparing raw
 * objects across two drivers reports differences that are not differences - a {@code BigDecimal}
 * with a different scale, a {@code Boolean} that arrived as {@code "t"}. Those are rendering, and
 * flagging them would bury the divergences that actually change behaviour.
 */
public sealed interface ExecutionOutcome {

    record Rows(List<String> columnTypes, List<List<String>> values) implements ExecutionOutcome {
        public int rowCount() {
            return values.size();
        }
    }

    record Updated(int count) implements ExecutionOutcome {
    }

    record Rejected(String message) implements ExecutionOutcome {
    }

    /**
     * Renders a value the way the comparison should see it.
     *
     * <p>Numbers lose trailing zeros so {@code 1.0} and {@code 1} match. The {@code t} / {@code f}
     * spelling is collapsed <b>only</b> for columns the driver declares as boolean: doing it for
     * every string would silently equate a genuine {@code 'F'} status code with {@code false},
     * which is exactly the kind of divergence this tool exists to surface.
     */
    static String normalise(Object value, String columnTypeName) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }

        String text = value.toString();
        if (columnTypeName != null && columnTypeName.toUpperCase(Locale.ROOT).startsWith("BOOL")) {
            if (text.equalsIgnoreCase("t") || text.equalsIgnoreCase("true")) {
                return "true";
            }
            if (text.equalsIgnoreCase("f") || text.equalsIgnoreCase("false")) {
                return "false";
            }
        }
        return text;
    }
}
