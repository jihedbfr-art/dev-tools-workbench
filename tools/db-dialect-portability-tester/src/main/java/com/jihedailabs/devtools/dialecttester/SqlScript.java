package com.jihedailabs.devtools.dialecttester;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a SQL file into the statements to replay, in order.
 *
 * <p>The splitting is deliberately simple: statements end at a semicolon that is not inside a
 * string literal, and {@code --} starts a comment that runs to end of line. That covers the query
 * sets this tool is pointed at - migrations and the handful of statements an application actually
 * issues. It does not attempt to parse PL/pgSQL bodies, where semicolons are structural; a script
 * containing one should be split by hand into separate files.
 *
 * <p>A comment of the form {@code -- name: something} labels the statement that follows, so the
 * report can say which query diverged rather than printing its first sixty characters.
 */
public final class SqlScript {

    /** One statement from the script, with the label it was given if any. */
    public record Statement(int index, String label, String sql) {

        /** What the report prints when the statement has no label of its own. */
        public String display() {
            if (label != null && !label.isBlank()) {
                return label;
            }
            String flat = sql.replaceAll("\\s+", " ").trim();
            return flat.length() <= 70 ? flat : flat.substring(0, 67) + "...";
        }
    }

    private SqlScript() {
    }

    public static List<Statement> parse(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static List<Statement> parse(String script) {
        List<Statement> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String pendingLabel = null;

        boolean inString = false;
        int i = 0;
        while (i < script.length()) {
            char c = script.charAt(i);

            if (inString) {
                current.append(c);
                if (c == '\'') {
                    // A doubled quote inside a literal is an escaped quote, not the end of it.
                    if (i + 1 < script.length() && script.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i += 2;
                        continue;
                    }
                    inString = false;
                }
                i++;
                continue;
            }

            if (c == '\'') {
                inString = true;
                current.append(c);
                i++;
                continue;
            }

            if (c == '-' && i + 1 < script.length() && script.charAt(i + 1) == '-') {
                int lineEnd = script.indexOf('\n', i);
                String comment = (lineEnd < 0 ? script.substring(i) : script.substring(i, lineEnd)).trim();
                String label = labelFrom(comment);
                if (label != null && current.toString().isBlank()) {
                    // A label only applies to a statement that has not started yet; a comment in
                    // the middle of one is just a comment.
                    pendingLabel = label;
                }
                if (lineEnd < 0) {
                    break;
                }
                current.append('\n');
                i = lineEnd + 1;
                continue;
            }

            if (c == ';') {
                if (!current.toString().isBlank()) {
                    statements.add(new Statement(statements.size(), pendingLabel, current.toString().trim()));
                    pendingLabel = null;
                }
                current.setLength(0);
                i++;
                continue;
            }

            current.append(c);
            i++;
        }

        if (!current.toString().isBlank()) {
            statements.add(new Statement(statements.size(), pendingLabel, current.toString().trim()));
        }
        return statements;
    }

    private static String labelFrom(String comment) {
        String body = comment.replaceFirst("^--\\s*", "");
        if (!body.toLowerCase().startsWith("name:")) {
            return null;
        }
        String label = body.substring("name:".length()).trim();
        return label.isEmpty() ? null : label;
    }
}
