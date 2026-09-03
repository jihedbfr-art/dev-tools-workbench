# How a tool gets in here

This repo doesn't collect every plausible CLI idea. Before a tool gets built, it has to survive
two questions:

1. **Does something mature already do this well?** If yes, it doesn't belong here — pointing
   people to `hadolint` or `pgbadger` is more honest than shipping a worse clone.
2. **If nothing mature exists, why not?** Sometimes it's because the need is real but narrow
   (a specific framework combination, a specific domain like telecom BSS). Sometimes it's because
   the need isn't actually there. Only the first case is worth building.

Candidates were screened against the current tooling landscape (GitHub, Maven Central, npm, PyPI)
before being accepted. A short note on what was ruled out and why lives in
[`docs/rejected-candidates.md`](rejected-candidates.md) — most ideas that looked good on paper
turned out to be `Spring Boot Admin`, `hadolint`, or `pgbadger` already solving the problem
better.

## What's accepted so far

1. **[`msisdn-portability-simulator`](../tools/msisdn-portability-simulator)** — shipped.
   Simulates the donor/recipient exchange of a mobile number portability (MNP) request between
   operators. Every MNP implementation is closed-source vendor software; nothing open exists to
   test against.
2. **[`keycloak-spi-linter`](../tools/keycloak-spi-linter)** — shipped. Checks that a Keycloak
   SPI provider's `META-INF/services` registration and factory contracts are correct before it
   gets deployed to a real server, instead of finding out at boot time.
3. **[`kafka-outbox-verifier`](../tools/kafka-outbox-verifier)** — shipped. Audits an existing
   transactional outbox table for stuck rows, unpurged sent rows, and retry loops. Not another
   outbox library (several already exist) — a diagnostic for one already running in production.
4. **[`db-dialect-portability-tester`](../tools/db-dialect-portability-tester)** — shipped.
   Replays a query set against H2 and a real PostgreSQL and reports every place they disagree.
   Migration tools check that a schema applies, ORMs hide the dialect and are the reason you stop
   noticing it, and `sqlfluff` lints style against one dialect without executing anything. The
   identifier-casing trap it catches first is the one `kafka-outbox-verifier` shipped with.
5. **[`bpmn-saga-linter`](../tools/bpmn-saga-linter)** — shipped. Checks that every boundary
   timer in a BPMN saga can reach a compensation task. `bpmnlint` validates the XML is
   well-formed; it has no opinion on whether a timeout silently skips the rollback.
6. **[`kafka-idempotent-consumer-scaffolder`](../tools/kafka-idempotent-consumer-scaffolder)** —
   shipped. Generates the `processed_events` table and repository boilerplate for the
   idempotent-consumer pattern, instead of hand-rolling it again. `spring-kafka` retry topics and
   the dedup starters solve the neighbouring problem — they retry, they don't remember — and the
   three ways this gets hand-written wrong (SELECT-before-INSERT, a guard that commits on its own,
   an INSERT that only parses on one engine) are what the generated comments exist for.

All six are built. The screening was the deliverable, not the number: the great majority of the
candidates that went in were already covered by mature tooling, and each one is recorded with its
reason in [`rejected-candidates.md`](rejected-candidates.md). What grows this repository from here
is a tool that survives the same two questions, not a target count.
