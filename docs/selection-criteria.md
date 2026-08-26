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
4. **`db-dialect-portability-tester`** — replays a fixed query set against H2 and PostgreSQL to
   catch the dialect differences that pass on an in-memory test profile and break in production.
5. **`bpmn-saga-linter`** — checks that a BPMN saga diagram has a compensation path for every
   compensable activity. `bpmnlint` validates the XML is well-formed; it has no opinion on
   whether the saga is actually safe.
6. **`kafka-idempotent-consumer-scaffolder`** — generates the `processed_events` table and
   repository boilerplate for the idempotent-consumer pattern, instead of hand-rolling it again.

This list will grow or shrink as each tool gets built and actually used — a tool earns its
README, not the other way around.
