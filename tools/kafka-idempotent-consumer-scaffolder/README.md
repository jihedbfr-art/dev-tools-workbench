# Kafka Idempotent Consumer Scaffolder

<p align="center">
  <b>Generates the <code>processed_events</code> migration and the repository behind the idempotent-consumer pattern — with the dialect and transaction traps already written into the code it emits.</b>
</p>

<p align="center">
  <a href="README.fr.md">🇫🇷 Lire en Français</a>
</p>

---

## Why this exists

Kafka gives you at-least-once delivery. Every consumer that touches a database therefore needs the
same forty lines: a table keyed by event id, an insert that lets the database reject the second
delivery, and a caller that skips the work when it was rejected. Spring provides none of it, and
the libraries that do (`spring-kafka` retry topics, various dedup starters) solve a different
problem — they retry, they don't remember.

So it gets hand-rolled, project after project. That is fine right up to the three places it is
routinely written wrong:

- **`SELECT` before `INSERT`.** It reads as the obvious implementation and it is a race: two
  consumers in the same rebalance window can both read "not seen yet" before either one writes.
  Only the unique constraint decides this correctly.
- **The guard committing on its own.** If `markProcessed` lands in its own transaction and the
  handler then throws, the event is marked done and its effect never happened — and the
  redelivery Kafka is about to send gets skipped. The guard has to share the handler's
  transaction.
- **The insert that only works on one engine.** `ON CONFLICT DO NOTHING` returns zero rows on
  PostgreSQL; H2 outside PostgreSQL compatibility mode cannot parse the clause at all and reports
  the duplicate by throwing. Test on H2, run on PostgreSQL, and one of the two paths has never
  executed.

This tool writes the version that handles all three, and puts the reason for each one in a comment
next to the code — so the next person to touch it knows what they'd be undoing.

## What it generates

| File | What it is |
|---|---|
| `db/migration/V1__processed_events.sql` | The table, its primary key (which *is* the idempotency mechanism), and the index the retention delete needs. |
| `<package>/ProcessedEventRepository.java` | A `JdbcTemplate` repository with `markProcessed(...)` and `purgeProcessedBefore(...)`, handling both the zero-rows and thrown-duplicate outcomes. |

Every identifier in the generated SQL is lowercase and unquoted, and the generator refuses names
that would need quoting. That is not a style rule: H2 folds unquoted identifiers to UPPERCASE and
PostgreSQL folds them to lowercase, so a quoted name created on one engine is unreachable by the
same query on the other. This ecosystem already lost time to that exact bug in
`kafka-outbox-verifier`.

## Quick Start

```bash
mvn clean package
```

Produces `target/kafka-idempotent-consumer-scaffolder-<version>-jar-with-dependencies.jar`.

```bash
java -jar kafka-idempotent-consumer-scaffolder.jar \
  --package=com.example.app.idempotency \
  --dialect=postgres \
  --out=src/main
```

| Option | Default | |
|---|---|---|
| `--package` | *required* | Java package for the generated repository. |
| `--class-name` | `ProcessedEventRepository` | |
| `--table` | `processed_events` | Lowercase, unquoted. |
| `--event-id-column` | `event_id` | |
| `--dialect` | `postgres` | `postgres` or `h2` — decides the INSERT only; the DDL is identical. |
| `--migration-version` | `V1` | Flyway version prefix. |
| `--out` | `./generated` | |
| `--dry-run` | | Print the files instead of writing them. |
| `--force` | | Overwrite existing files (refused otherwise). |

Then wire it up — this is the part the generator cannot do for you:

```java
@KafkaListener(topics = "donor-response-events")
@Transactional
public void onEvent(String payload) {
    DonorResponseEvent event = objectMapper.readValue(payload, DonorResponseEvent.class);

    if (!processedEvents.markProcessed(event.eventId(), "donor-response-events", event.requestId())) {
        return; // redelivery, already handled
    }

    // business work, in this same transaction
}
```

## Where the generated code comes from

Not from a blog post: from
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns), where
this pattern is running with `ProcessedEventRepository` and
`V1__outbox_and_processed_events.sql`. The comments in the generated code each mark something that
went wrong there first.

## What was verified

- **15 tests, none skipped.** The suite asserts on what the generator emits *and* runs it: the
  generated DDL is executed against a real embedded H2, the generated INSERT is run twice with the
  same event id, and the second one is rejected by the primary key. A generator tested only
  against expected strings passes happily while emitting SQL no engine accepts.
- **The generated Java compiles.** Both the PostgreSQL and H2 outputs were compiled with `javac`
  against `bpmn-provisioning-patterns`' real Spring classpath (`spring-jdbc`, `spring-context`).
  The scaffolder itself has no Spring dependency — it emits Spring code, it never runs any.
- **The PostgreSQL INSERT has not been executed.** `ON CONFLICT DO NOTHING` needs a real
  PostgreSQL and no Docker daemon was available in this environment. The clause is standard
  PostgreSQL and the repository's other branch is what H2 exercises, but treat the PostgreSQL path
  as reviewed, not run.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
