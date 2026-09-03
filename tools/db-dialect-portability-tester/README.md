# DB Dialect Portability Tester

<p align="center">
  <b>Replays the same SQL against H2 and a real PostgreSQL and reports every place they disagree — the dialect differences that pass on an in-memory test profile and surface in production.</b>
</p>

<p align="center">
  <a href="README.fr.md">🇫🇷 Lire en Français</a>
</p>

---

## Why this exists

The standard Spring Boot arrangement runs tests against in-memory H2 and production against
PostgreSQL. That trade is usually worth it — the test suite starts in a second instead of a minute.
What it buys you is a class of bug that is invisible where you look for bugs and only appears where
you do not: the query is fine, the schema is fine, and the statement means something different on
the engine that matters.

This is not hypothetical in this ecosystem. `kafka-outbox-verifier` shipped with quoted lowercase
identifiers: H2 folds unquoted names to `UPPERCASE`, PostgreSQL folds them to `lowercase`, so a
table created unquoted and queried quoted resolves on exactly one of them. Every test passed.
`bpmn-provisioning-patterns` lost time to a `CASE` expression that H2 and PostgreSQL typed
differently.

Nothing existing covers this. Migration tools check that a schema applies; ORMs abstract the
dialect away and are the reason you stop noticing it; `sqlfluff` lints style against one dialect
without executing anything. This executes, against both, and compares.

## What it reports

| Finding | Meaning | Fails the run |
|---|---|---|
| `REJECTED_BY_ONE` | One engine executed the statement, the other refused it. | yes |
| `ROW_COUNT` | Same query, different number of rows. | yes |
| `CELL_VALUE` | Same row, different value. | yes |
| `UPDATE_COUNT` | Same statement, different number of rows affected. | yes |
| `RESULT_SHAPE` | One returned a result set where the other returned an update count. | yes |
| `COLUMN_TYPE` | Same data, different column type name — `LENGTH()` is `BIGINT` on H2 and `int4` on PostgreSQL, which matters to a caller choosing `getLong` over `getInt`. | no |
| `REJECTED_BY_BOTH` | Both refused it. Consistent, so not a portability problem — usually a genuine error in the script, sometimes a constraint doing its job. | no |

Type names that are the same type in two vocabularies — `VARCHAR` and `character varying`,
`INTEGER` and `int4` — are not reported at all. A tool that flags those teaches people to ignore it.

Exit code is `0` when nothing that changes behaviour differed, `1` when something did, `2` when the
run could not happen at all.

## Quick Start

```bash
mvn clean package
```

Produces `target/db-dialect-portability-tester-<version>-jar-with-dependencies.jar`, with both
drivers inside it.

```bash
java -jar db-dialect-portability-tester.jar \
  --script=examples/outbox-and-idempotency.sql \
  --postgres-url=jdbc:postgresql://localhost:5432/app \
  --user=postgres --password=secret
```

The bundled [`examples/outbox-and-idempotency.sql`](examples/outbox-and-idempotency.sql) is the
real outbox and idempotency schema from
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns) followed
by the statements the application actually issues. Run against PostgreSQL 16, it prints:

```
Compared 10 statement(s): H2 vs PostgreSQL

  ❌ [REJECTED_BY_ONE] statement 5 — relay - claim the batch, with the table name quoted
       PostgreSQL accepted it; H2 refused: Table "portability_outbox" not found
       (candidates are: "PORTABILITY_OUTBOX")

  ⚠️  [COLUMN_TYPE] statement 7 — build a diagnostic label by concatenation
       column 1 is CHARACTER VARYING on H2 and text on PostgreSQL
  ⚠️  [REJECTED_BY_BOTH] statement 9 — idempotency - the redelivery must be refused by the primary key
       H2: Unique index or primary key violation | PostgreSQL: duplicate key value violates unique constraint

1 divergence(s) that change behaviour, 2 note(s) that do not.
```

Statement 9 is the interesting non-finding: both engines refuse the duplicate insert, which is the
idempotent-consumer pattern working. Consistent refusal is not a portability problem, and the tool
says so instead of counting it against you.

### Writing a script

Statements are separated by semicolons — semicolons inside string literals do not split, so
`INSERT INTO t VALUES ('a;b')` survives. A `-- name: something` comment labels the statement that
follows it, and the report uses the label instead of truncating the SQL.

## It never writes to your database

The whole replay runs inside one transaction that is rolled back at the end, on both engines. Both
have transactional DDL, so tables the script creates disappear with it. That is what makes it safe
to point at a database that already has data, and what makes a second run behave like the first
instead of failing with `relation already exists`.

Each statement also gets its own savepoint. Without one, the first statement PostgreSQL rejects
aborts the transaction and every statement after it fails with `current transaction is aborted` —
one real divergence turning into a page of invented ones.

## Running it in CI

The script belongs in the repository next to the migrations, and the comparison belongs on every
pull request that touches them. A PostgreSQL service container and one `java -jar` is the whole
integration; the exit code fails the build.

Worth running twice, with `--h2-url` set to add `;MODE=PostgreSQL` on the second pass. Default H2
answers "does this work on the engine my tests use"; H2 in PostgreSQL compatibility mode answers
"does the compatibility mode actually cover the gap". They are different questions and the second
one is how you decide whether the mode is worth turning on.

## What was verified

- **17 tests, none skipped**, run against **PostgreSQL 16.15 in a container** — the divergences
  asserted here were observed, not assumed: the quoted-identifier refusal, `to_regclass` existing
  on one engine only, `SELECT 1 || 2` which H2 coerces and PostgreSQL rejects, and `LENGTH()`
  returning a different width on each.
- **The rollback is tested, not just intended**: one test creates a table through the comparison
  and then asserts, on a fresh connection, that it is not there.
- **Without a PostgreSQL URL the comparison tests skip and say so.** They are not quietly counted
  as passing — the assumption sits in `@BeforeEach` rather than `@BeforeAll` precisely because an
  aborted `@BeforeAll` reports `Tests run: 0`, which reads like a green build. CI additionally
  fails the job if any test was skipped.
- **No Testcontainers.** A tool about "the same thing behaves differently depending on the engine
  underneath" should not itself break when the local engine moves — and it does: `docker-java` is
  rejected by Docker Engine 29's API here, which would have left the suite running nothing at all.
  The PostgreSQL half is a JDBC URL, supplied by a service container in CI.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
