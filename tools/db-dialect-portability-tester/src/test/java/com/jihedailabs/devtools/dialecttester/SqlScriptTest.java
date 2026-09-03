package com.jihedailabs.devtools.dialecttester;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptTest {

    @Test
    void splitsOnSemicolonsAndDropsBlanks() {
        List<SqlScript.Statement> statements = SqlScript.parse(
                "CREATE TABLE t (id INT);\n\n  ;\nINSERT INTO t VALUES (1);\n");

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE t (id INT)", statements.get(0).sql());
        assertEquals("INSERT INTO t VALUES (1)", statements.get(1).sql());
    }

    @Test
    void doesNotSplitOnASemicolonInsideAStringLiteral() {
        // A statement cut in half here would be reported as a syntax error on both engines, which
        // reads as "your SQL is broken" when the SQL was fine.
        List<SqlScript.Statement> statements = SqlScript.parse(
                "INSERT INTO t VALUES ('a;b');\nSELECT 1;");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES ('a;b')", statements.get(0).sql());
    }

    @Test
    void handlesADoubledQuoteInsideALiteral() {
        List<SqlScript.Statement> statements = SqlScript.parse("SELECT 'it''s; fine';");

        assertEquals(1, statements.size());
        assertEquals("SELECT 'it''s; fine'", statements.get(0).sql());
    }

    @Test
    void stripsLineCommentsButKeepsTheStatement() {
        List<SqlScript.Statement> statements = SqlScript.parse(
                "-- a note about the next one\nSELECT 1; -- trailing note\nSELECT 2;");

        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0).sql());
        assertEquals("SELECT 2", statements.get(1).sql());
    }

    @Test
    void readsTheNameCommentAsALabel() {
        List<SqlScript.Statement> statements = SqlScript.parse(
                "-- name: claim the outbox batch\nSELECT id FROM outbox;");

        assertEquals("claim the outbox batch", statements.get(0).label());
        assertEquals("claim the outbox batch", statements.get(0).display());
    }

    @Test
    void aLabelBelongsToTheStatementThatFollowsIt() {
        List<SqlScript.Statement> statements = SqlScript.parse(
                "-- name: first\nSELECT 1;\nSELECT 2;");

        assertEquals("first", statements.get(0).label());
        assertEquals(null, statements.get(1).label(), "the label must not carry over");
    }

    @Test
    void unlabelledStatementsAreDisplayedByTheirTextTruncated() {
        String longSelect = "SELECT " + "column_name, ".repeat(20) + "1 FROM t";
        List<SqlScript.Statement> statements = SqlScript.parse(longSelect + ";");

        String display = statements.get(0).display();
        assertEquals(70, display.length());
        assertTrue(display.endsWith("..."));
    }

    @Test
    void keepsAFinalStatementWithNoTrailingSemicolon() {
        List<SqlScript.Statement> statements = SqlScript.parse("SELECT 1;\nSELECT 2");

        assertEquals(2, statements.size());
        assertEquals("SELECT 2", statements.get(1).sql());
    }
}
