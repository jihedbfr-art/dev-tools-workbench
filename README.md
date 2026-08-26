# dev-tools-workbench

[![CI - msisdn-portability-simulator](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-msisdn-portability-simulator.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-msisdn-portability-simulator.yml)
[![CI - keycloak-spi-linter](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-keycloak-spi-linter.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-keycloak-spi-linter.yml)
[![CI - kafka-outbox-verifier](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-kafka-outbox-verifier.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-kafka-outbox-verifier.yml)
[![CI - bpmn-saga-linter](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-bpmn-saga-linter.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-bpmn-saga-linter.yml)

[Version française](./README.fr.md)

Small, focused developer tools for gaps that mature tooling doesn't cover — not another
collection of things `hadolint`, `Spring Boot Admin`, or `pgbadger` already do better. Each tool
here exists because it was screened against the current landscape first; see
[`docs/selection-criteria.md`](docs/selection-criteria.md) for how that screening works and
[`docs/rejected-candidates.md`](docs/rejected-candidates.md) for the ideas that didn't survive it.

## Status

- [`msisdn-portability-simulator`](tools/msisdn-portability-simulator) — shipped. Simulates the
  donor side of a mobile number portability (MNP) exchange behind an ACQ-style REST gateway.
- [`keycloak-spi-linter`](tools/keycloak-spi-linter) — shipped. Catches broken
  `META-INF/services` registrations for Keycloak SPI providers before they fail at deploy time.
- [`kafka-outbox-verifier`](tools/kafka-outbox-verifier) — shipped. Audits an existing
  transactional outbox table for stuck rows, unpurged sent rows, and retry loops.
- [`bpmn-saga-linter`](tools/bpmn-saga-linter) — shipped. Fails the build when a boundary timer
  in a saga can't reach the compensation task the rest of the process relies on.
- An H2/PostgreSQL dialect portability tester and a Kafka idempotent-consumer scaffolder are
  next — see [`docs/selection-criteria.md`](docs/selection-criteria.md) for the full shortlist
  and why each one made the cut.

## Structure

Each tool lands in `tools/<tool-name>/` as a self-contained module with its own README — no
shared framework forcing every tool into the same shape, since a CLI linter and a Spring Boot
scaffolder have nothing in common beyond living in the same repo.

```
dev-tools-workbench/
├── tools/                     one self-contained module per accepted tool
├── docs/
│   ├── selection-criteria.md  how a tool gets accepted, and the current shortlist
│   └── rejected-candidates.md ideas screened out, and what already covers them
└── LICENSE
```

## License

MIT — see [LICENSE](LICENSE).

---

<div align="center">
  <img src="assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
