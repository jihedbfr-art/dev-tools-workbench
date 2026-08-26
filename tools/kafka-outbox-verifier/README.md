# Kafka Outbox Verifier

<p align="center">
  <b>A read-only CLI that audits an existing transactional outbox table for the failure modes a relay never announces on its own.</b>
</p>

<p align="center">
  <a href="README.fr.md">🇫🇷 Lire en Français</a>
</p>

---

## Why this exists

Plenty of open-source libraries implement the transactional outbox pattern (`goharvest`,
`kafkaesque`, and others). None of them audit an outbox that's already running in production —
they help you build the relay, not tell you it's stuck. A relay that silently stopped polling, a
purge job that was never wired up, or a message retried forever look identical from the outside:
the table just keeps growing, and nobody notices until a customer does.

This tool reads the table exactly once and reports what a healthy relay would never produce:
rows stuck pending, sent rows nobody purged, and retry counts that never stop climbing. It makes
no assumption about which library (if any) wrote the row — only that the table tracks state as
nullable timestamps, which is how most real outbox implementations are actually shaped (see
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns)'s
`portability_outbox`, the table this tool was built against).

## What it checks

| Signal | What it means |
|---|---|
| **Stagnant** | Rows still pending (no completion, no failure timestamp) longer than the stagnation threshold — the relay is down, not just slow. |
| **Bloated** | Rows completed longer ago than the bloat threshold and never purged — a purge job isn't wired up. |
| **Publish latency (p50/p95/p99)** | How long completed rows actually took, informational — no threshold, just visibility. |
| **Failed** | Rows with a failure timestamp set — informational, a dead-lettered row after real retries is expected behavior, not a bug on its own. |
| **Retry loops** | Rows whose retry count has hit the configured threshold — something keeps failing without ever landing in a terminal state. |

The exit code is `0` only when stagnant, bloated, and retry-loop counts are all zero — that's what
"healthy" means here, independent of the failed count or the latency numbers.

## Quick Start

```bash
mvn clean package
```

Produces `target/kafka-outbox-verifier-<version>-jar-with-dependencies.jar`, bundled with a
PostgreSQL driver.

```bash
java -jar kafka-outbox-verifier.jar \
  --jdbc-url=jdbc:postgresql://localhost:5432/provisioning \
  --user=provisioning --password=secret \
  --table=portability_outbox \
  --completed-at-column=published_at \
  --failed-at-column=failed_at \
  --retry-count-column=attempts
```

`--id-column` and `--created-at-column` default to `id` and `created_at`; `--completed-at-column`
defaults to `published_at`. `--failed-at-column` and `--retry-count-column` are optional — omit
either one if your table doesn't track that, and the corresponding signal is simply skipped
instead of guessed at.

Thresholds default to 5 minutes (stagnation), 24 hours (bloat), and 5 attempts (retry loop) —
override with `--stagnation-minutes`, `--bloat-hours`, `--retry-threshold`.

## Running in CI or on a schedule

This is a read-only diagnostic, not a one-time migration check — it's meant to run repeatedly
against a live database, e.g. as a scheduled GitHub Action or a cron job hitting a
`kubectl port-forward`'d connection, alerting (via its exit code) when the outbox drifts out of
the healthy state rather than catching it only when a customer complains.

## What was verified

Tested against an in-memory H2 database shaped exactly like `bpmn-provisioning-patterns`'
`portability_outbox` table (9 tests covering every signal, including the case where a table has
neither a failure timestamp nor a retry count column). All comparison logic runs in Java, not
SQL, on purpose — a raw `CASE` expression in this exact table already broke once in this
ecosystem because H2 and PostgreSQL disagree on implicit typing (see
`bpmn-provisioning-patterns`' `OutboxRepository.markFailed` history). Not yet run against a live
PostgreSQL instance in this environment (no Docker daemon available at build time) — the JDBC
driver is bundled and the query is plain ANSI SQL with no H2-specific syntax, but treat that as
unverified until it's actually been run once against Postgres.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
