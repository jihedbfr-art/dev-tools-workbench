package com.jihedailabs.devtools.dialecttester;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The comparison against a real PostgreSQL. Not a second H2 in compatibility mode pretending to be
 * one — the entire value of this tool is that the other engine is the one production runs.
 *
 * <p>The database is supplied from outside: {@code -Dportability.postgres.url}, or the
 * {@code PORTABILITY_POSTGRES_URL} environment variable, with matching {@code .user} /
 * {@code .password}. CI provides it as a service container. Nothing here starts a container, which
 * keeps this suite from depending on whether the local Docker engine's API happens to match what a
 * container library was built against.
 *
 * <p>Without a URL these are <b>skipped</b>, never silently passed. A suite that reports PASSED
 * because it found nothing to run is how a tool shipped broken in this repository once already.
 */
class DialectComparatorPostgresTest {

    private static String url;
    private static String user;
    private static String password;

    // Per test rather than once for the class: an assumption in @BeforeAll aborts the whole
    // container and surefire then reports "Tests run: 0", which reads as a green build. Per test,
    // the absence shows up as one skipped test per case, which reads as what it is.
    @BeforeEach
    void requirePostgres() {
        url = setting("portability.postgres.url", "PORTABILITY_POSTGRES_URL", null);
        assumeTrue(url != null && !url.isBlank(),
                "no PostgreSQL supplied (-Dportability.postgres.url): the half of this comparison "
                        + "that matters cannot run");
        user = setting("portability.postgres.user", "PORTABILITY_POSTGRES_USER", "postgres");
        password = setting("portability.postgres.password", "PORTABILITY_POSTGRES_PASSWORD", "postgres");
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Connection postgres() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private PortabilityReport compare(String script) throws SQLException {
        try (DatabaseSession h2 = new DatabaseSession("H2", DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
             DatabaseSession pg = new DatabaseSession("PostgreSQL", postgres())) {

            return new DialectComparator("H2", "PostgreSQL").compare(SqlScript.parse(script), h2, pg);
        }
    }

    private static List<Divergence> ofKind(PortabilityReport report, Divergence.Kind kind) {
        return report.divergences().stream().filter(d -> d.kind() == kind).toList();
    }

    @Test
    void portableScriptReportsNothing() throws SQLException {
        PortabilityReport report = compare("""
                CREATE TABLE outbox (id VARCHAR(36) PRIMARY KEY, attempts INTEGER NOT NULL);
                INSERT INTO outbox (id, attempts) VALUES ('a', 1);
                INSERT INTO outbox (id, attempts) VALUES ('b', 2);
                SELECT id, attempts FROM outbox ORDER BY id;
                """);

        assertTrue(report.divergences().isEmpty(),
                () -> "expected a clean run, got:\n" + report.render());
        assertTrue(report.isPortable());
    }

    @Test
    void quotedLowercaseIdentifierIsRefusedByH2AndAcceptedByPostgres() throws SQLException {
        // The exact bug kafka-outbox-verifier shipped with. The table is created unquoted, so H2
        // stores it as OUTBOX and PostgreSQL as outbox; a quoted lowercase reference then resolves
        // on one engine only, and the tests that run on H2 never see it.
        PortabilityReport report = compare("""
                CREATE TABLE outbox (id VARCHAR(36) PRIMARY KEY);
                SELECT id FROM "outbox";
                """);

        List<Divergence> rejected = ofKind(report, Divergence.Kind.REJECTED_BY_ONE);
        assertEquals(1, rejected.size(), () -> report.render());
        assertTrue(rejected.get(0).detail().contains("H2 refused"), rejected.get(0).detail());
        assertTrue(report.failures().size() == 1 && !report.isPortable(), () -> report.render());
    }

    @Test
    void postgresOnlyFunctionIsRejectedByH2() throws SQLException {
        PortabilityReport report = compare("SELECT to_regclass('pg_class');");

        assertEquals(1, ofKind(report, Divergence.Kind.REJECTED_BY_ONE).size(), () -> report.render());
    }

    @Test
    void h2ConcatenatesIntegersAndPostgresRefusesTo() throws SQLException {
        // H2 coerces both sides of || to text; PostgreSQL has no integer || integer operator. A
        // string built this way passes every test on H2 and fails on the first production call.
        PortabilityReport report = compare("SELECT 1 || 2;");

        List<Divergence> rejected = ofKind(report, Divergence.Kind.REJECTED_BY_ONE);
        assertEquals(1, rejected.size(), () -> report.render());
        assertTrue(rejected.get(0).detail().contains("PostgreSQL refused"), rejected.get(0).detail());
    }

    @Test
    void aTypeNameDifferenceIsAWarningAndKeepsTheRunPortable() throws SQLException {
        // LENGTH() comes back BIGINT on H2 and int4 on PostgreSQL. That matters to a caller using
        // getLong versus getInt, so it is reported — but it does not change what the query
        // returns, so it must not fail the run.
        PortabilityReport report = compare("SELECT LENGTH('abc');");

        assertEquals(1, ofKind(report, Divergence.Kind.COLUMN_TYPE).size(), () -> report.render());
        assertTrue(report.failures().isEmpty(), () -> report.render());
        assertTrue(report.isPortable(), "a naming difference alone must not fail the run");
    }

    @Test
    void interchangeableTypeNamesAreNotReported() throws SQLException {
        // VARCHAR versus character varying, INTEGER versus int4: one type, two vocabularies.
        // Reporting these would drown the findings that matter.
        PortabilityReport report = compare("""
                CREATE TABLE t (id VARCHAR(10), n INTEGER, big BIGINT, flag BOOLEAN);
                INSERT INTO t VALUES ('x', 1, 2, TRUE);
                SELECT id, n, big, flag FROM t;
                """);

        assertTrue(ofKind(report, Divergence.Kind.COLUMN_TYPE).isEmpty(), () -> report.render());
        assertTrue(report.isPortable(), () -> report.render());
    }

    @Test
    void aRejectedStatementDoesNotPoisonTheStatementsAfterIt() throws SQLException {
        // Without a savepoint per statement, the failure below aborts the PostgreSQL transaction
        // and every later statement fails with "current transaction is aborted" — one real
        // divergence turning into a page of invented ones.
        PortabilityReport report = compare("""
                CREATE TABLE t (id INTEGER);
                SELECT to_regclass('pg_class');
                INSERT INTO t VALUES (1);
                SELECT id FROM t;
                """);

        assertEquals(1, report.failures().size(),
                () -> "only the PostgreSQL-only function should have diverged:\n" + report.render());
        assertEquals(1, report.failures().get(0).statementIndex(), () -> report.render());
    }

    @Test
    void theRunLeavesNothingBehindInPostgres() throws SQLException {
        String table = "leftover_check_" + UUID.randomUUID().toString().replace("-", "");
        compare("CREATE TABLE " + table + " (id INTEGER);\nINSERT INTO " + table + " VALUES (1);");

        // A tool pointed at a real database must not write to it. The replay runs inside one
        // transaction that is rolled back, which is also what makes a second run behave like the
        // first instead of failing with "relation already exists".
        try (Connection fresh = postgres();
             Statement statement = fresh.createStatement();
             ResultSet rs = statement.executeQuery("SELECT to_regclass('" + table + "') IS NULL")) {

            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1), "the comparison left a table behind in the target database");
        }
    }

    @Test
    void updateCountsAreComparedAndAgreeHere() throws SQLException {
        PortabilityReport report = compare("""
                CREATE TABLE t (id INTEGER);
                INSERT INTO t VALUES (1);
                INSERT INTO t VALUES (2);
                DELETE FROM t WHERE id > 0;
                """);

        assertTrue(ofKind(report, Divergence.Kind.UPDATE_COUNT).isEmpty(), () -> report.render());
        assertTrue(report.isPortable(), () -> report.render());
    }
}
