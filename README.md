# dev-tools-workbench

[Version française](./README.fr.md)

Small, focused developer tools for gaps that mature tooling doesn't cover — not another
collection of things `hadolint`, `Spring Boot Admin`, or `pgbadger` already do better. Each tool
here exists because it was screened against the current landscape first; see
[`docs/selection-criteria.md`](docs/selection-criteria.md) for how that screening works and
[`docs/rejected-candidates.md`](docs/rejected-candidates.md) for the ideas that didn't survive it.

## Status

This repo is starting empty on purpose. The shortlist of tools planned first — an MNP
portability simulator, a Keycloak SPI linter, a Kafka outbox auditor, an H2/PostgreSQL dialect
portability tester, a BPMN saga compensation linter, and a Kafka idempotent-consumer scaffolder —
is in [`docs/selection-criteria.md`](docs/selection-criteria.md). Nothing is published until it
works and has been used for real, not just committed to prove the list exists.

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
