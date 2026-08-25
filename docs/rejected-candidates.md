# Rejected candidates

Ideas that were screened and ruled out before this repo started, and why. Kept here so the same
idea doesn't get re-proposed without remembering the reason it was dropped.

Most rejections fall into two buckets: a mature tool already owns the space, or the need is real
but already covered natively by the platform it targets.

## Keycloak / IAM

- **Realm diff / seed / admin CLI wrapper** — `keycloak-config-cli` and the official `kcadm.sh`
  already cover declarative config and admin operations.
- **Token inspector** — `jwt-cli` and jwt.io already do this well.
- **Theme scaffolder** — `keycloakify` has taken over custom theme development (React-based,
  actively maintained).
- **Client secret rotation** — this is what Vault's Keycloak secrets engine and Kubernetes
  secret management already handle.

## Spring / Java

- **Bean dependency graph, actuator dashboard, profile linter** — IntelliJ's inspections and
  Spring Boot Admin already do this, and better than a new CLI would.
- **N+1 detector** — Hypersistence Optimizer already owns this space.
- **Starter scaffolder** — Spring Initializr and JHipster-style generators are the established
  path here.
- **Feign contract diff** — this is exactly what Spring Cloud Contract and Pact are for.

## Kafka

- **Topic doctor, consumer lag, schema diff, DLQ replay** — `kafka-ui`, AKHQ, and the official
  `kafka-consumer-groups.sh` cover these well.
- **Local sandbox** — Lenses.io's `fast-data-dev` image already does one-command Kafka + demo data.
- **Message tracing** — this is what distributed tracing (OpenTelemetry, Zipkin) is for; a
  Kafka-specific tracer would duplicate that.

## Camunda / BPMN

- **Process diff, stuck-instance finder, test data builder** — Camunda Cockpit, Optimize, and
  BPM Assert already ship this in the official tooling.
- **DMN table tester, migration planner** — covered by Camunda's own DMN simulator and the
  official Camunda 7→8 migration tooling.

## API / OpenAPI

- **Breaking change detection, pagination/versioning linting, RFC 7807 linting** — `Optic` and
  `Spectral` have this space locked down as CI-first tools.
- **Contract mocking** — `Prism` generates a working mock straight from an OpenAPI spec.
- **GraphQL N+1** — Apollo tracing and DataLoader already instrument this at the framework level.

## Database

- **Migration safety, index advice, schema drift** — `squawk`, `pgbadger`, `atlas`, and the
  mainstream ORMs' migration tooling already cover this well.
- **Anonymization** — `postgresql-anonymizer` is a mature Postgres extension for exactly this.

## Observability

- **Log correlation, trace diffing, thread/heap dump analysis, GC log visualization** — this is
  ELK/Loki/Jaeger/Tempo territory, plus dedicated tools like `fastthread.io` and `gceasy.io`.
  Nothing here justifies a new standalone tool over the existing stack.

## DevOps / security

- **Dockerfile linting, secret scanning, dependency freshness, SBOM diffing, license compliance,
  security headers** — `hadolint`, `gitleaks`/`trufflehog`, Dependabot/Renovate, `syft`, `fossa`,
  and `securityheaders.com` are each the de facto standard for their slice. Rebuilding any of
  these would be starting from zero against tools with years of hardening.

## AI engineering

- **Prompt diffing, token cost tracking, RAG chunk inspection, eval harnesses, prompt injection
  testing, agent trace visualization** — `promptfoo`, LangSmith, LangFuse, `ragas`, and `garak`
  already own this space, several of them venture-funded and iterating fast. An `agent-skill-linter`
  survived screening only because it's specific to this ecosystem's own `SKILL.md` format
  (already implemented as `tools/lint_skills.py` in `ai-skills` and `cyber-skills` — no separate
  tool needed here).

## General CI / productivity

- **Commit message linting, changelog generation, PR size checks, monorepo affected-detection,
  env var sync, local dev diagnostics** — `commitlint`, `semantic-release`, `danger-js`,
  Nx/Turborepo, and `dotenv-linter` are each mature enough that a new entrant adds nothing.
