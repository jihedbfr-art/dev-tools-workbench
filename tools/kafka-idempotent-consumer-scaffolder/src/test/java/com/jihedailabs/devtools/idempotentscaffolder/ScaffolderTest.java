package com.jihedailabs.devtools.idempotentscaffolder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffolderTest {

    private static final String DOUBLE_QUOTE = "\"";

    private static ScaffoldOptions options(Dialect dialect) {
        return new ScaffoldOptions("com.example.app.idempotency", "ProcessedEventRepository",
                "processed_events", "event_id", dialect, "V1");
    }

    private static String fileEndingWith(List<GeneratedFile> files, String suffix) {
        return files.stream()
                .filter(f -> f.path().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated file ending in " + suffix))
                .content();
    }

    private static List<String> statementLinesOf(Dialect dialect) {
        return fileEndingWith(Scaffolder.generate(options(dialect)), ".sql").lines()
                .filter(line -> !line.trim().startsWith("--"))
                .filter(line -> !line.isBlank())
                .toList();
    }

    @Test
    void generatesMigrationAndRepositoryAtConventionalPaths() {
        List<GeneratedFile> files = Scaffolder.generate(options(Dialect.POSTGRES));

        assertEquals(2, files.size());
        assertEquals("db/migration/V1__processed_events.sql", files.get(0).path());
        assertEquals("com/example/app/idempotency/ProcessedEventRepository.java", files.get(1).path());
    }

    @Test
    void postgresInsertLetsTheStatementSwallowTheDuplicate() {
        String insert = Dialect.POSTGRES.insertStatement("processed_events", "event_id");

        assertTrue(insert.endsWith("ON CONFLICT (event_id) DO NOTHING"),
                "PostgreSQL should report a redelivery as zero rows updated");
        assertTrue(fileEndingWith(Scaffolder.generate(options(Dialect.POSTGRES)), ".java").contains(insert),
                "the generated INSERT_SQL constant should hold exactly that statement");
    }

    @Test
    void h2InsertHasNoOnConflictClause() {
        // Only the statement matters, not the prose: the generated javadoc explains ON CONFLICT for
        // both dialects, so asserting on the whole file would fail for the wrong reason.
        String insert = Dialect.H2.insertStatement("processed_events", "event_id");

        assertFalse(insert.contains("ON CONFLICT"),
                "H2 outside PostgreSQL compatibility mode cannot parse ON CONFLICT");
        assertTrue(fileEndingWith(Scaffolder.generate(options(Dialect.H2)), ".java").contains(insert),
                "the generated INSERT_SQL constant should hold exactly that statement");
    }

    @Test
    void bothDialectsKeepTheDuplicateKeyCatch() {
        // The generated code has to survive the common setup: tests on H2, production on
        // PostgreSQL. Emitting only the branch the chosen dialect uses would break the other half.
        for (Dialect dialect : Dialect.values()) {
            String repository = fileEndingWith(Scaffolder.generate(options(dialect)), ".java");
            assertTrue(repository.contains("catch (DuplicateKeyException"),
                    dialect + " repository dropped the thrown-duplicate branch");
            assertTrue(repository.contains("return rows > 0;"),
                    dialect + " repository dropped the zero-rows branch");
        }
    }

    @Test
    void migrationDdlIsIdenticalForBothDialects() {
        // Only the INSERT differs between engines. If the DDL itself ever starts differing, that is
        // a decision worth making on purpose rather than discovering in a migration diff. The
        // header comment names the dialect, so compare the statements and not the prose.
        assertEquals(statementLinesOf(Dialect.POSTGRES), statementLinesOf(Dialect.H2));
    }

    @Test
    void neverQuotesIdentifiers() {
        // Regression guard for the casing bug this ecosystem already paid for once: quoted
        // identifiers are case-sensitive, and H2 folds unquoted names up where PostgreSQL folds
        // them down. Only the comment block is allowed to mention a quoted name.
        for (String line : statementLinesOf(Dialect.POSTGRES)) {
            assertFalse(line.contains(DOUBLE_QUOTE), "generated DDL must not quote identifiers: " + line);
        }
    }

    @Test
    void rejectsIdentifiersThatWouldNeedQuoting() {
        assertThrows(IllegalArgumentException.class, () -> new ScaffoldOptions(
                "com.example", "ProcessedEventRepository", "ProcessedEvents", "event_id",
                Dialect.POSTGRES, "V1"));
    }

    @Test
    void rejectsMalformedPackageClassAndVersion() {
        assertThrows(IllegalArgumentException.class, () -> new ScaffoldOptions(
                "Com.Example", "ProcessedEventRepository", "processed_events", "event_id",
                Dialect.POSTGRES, "V1"));
        assertThrows(IllegalArgumentException.class, () -> new ScaffoldOptions(
                "com.example", "processedEventRepository", "processed_events", "event_id",
                Dialect.POSTGRES, "V1"));
        assertThrows(IllegalArgumentException.class, () -> new ScaffoldOptions(
                "com.example", "ProcessedEventRepository", "processed_events", "event_id",
                Dialect.POSTGRES, "1"));
    }

    @Test
    void rejectsUnknownDialect() {
        assertThrows(IllegalArgumentException.class, () -> Dialect.parse("oracle"));
    }

    @Test
    void honoursCustomTableAndColumnNames() {
        ScaffoldOptions custom = new ScaffoldOptions("com.example", "SeenEvents",
                "consumed_messages", "message_id", Dialect.POSTGRES, "V7");
        List<GeneratedFile> files = Scaffolder.generate(custom);

        assertEquals("db/migration/V7__consumed_messages.sql", files.get(0).path());
        assertEquals("com/example/SeenEvents.java", files.get(1).path());
        assertTrue(fileEndingWith(files, ".sql")
                .contains("CONSTRAINT pk_consumed_messages PRIMARY KEY (message_id)"));
        assertTrue(fileEndingWith(files, ".java").contains("ON CONFLICT (message_id) DO NOTHING"));
    }
}
