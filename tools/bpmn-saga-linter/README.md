# BPMN Saga Linter

<p align="center">
  <b>Checks that a timeout in your BPMN saga can't silently skip the rollback everything else relies on.</b>
</p>

<p align="center">
  <a href="README.fr.md">🇫🇷 Lire en Français</a>
</p>

---

## Why this exists

`bpmnlint` validates that a BPMN file is well-formed — elements connected, no dangling flows, no
duplicate ids. It has no opinion on whether the process it just approved is *safe*.

Here's the failure it can't see. A saga provisions something, waits for an external system, and
has two ways that wait can end badly: the other side explicitly rejects, or the SLA timer fires
because they never answered at all. The rejection branch routes to a compensation task that rolls
everything back. The timeout branch — added later, by someone else, in a hurry — routes straight
to an end event.

The XML is valid. The diagram looks reasonable. And every timed-out instance leaves provisioned
work stranded, with nothing in the logs to say so.

This linter walks the flow graph from every boundary timer and fails the build if one can't reach
a compensation task.

## What it checks

| Check | Severity |
|---|---|
| Every boundary timer event can reach a compensation task via sequence flow | **Failure** (exit 1) |
| A gateway has no branch reaching compensation | Warning (informational, doesn't affect exit code) |

Processes with no compensation task at all are skipped entirely — not every BPMN file is a saga,
and a plain approval workflow with a timeout has nothing to roll back.

**How compensation is identified:** any node whose id or name contains `compensate` or `rollback`
(case-insensitive). Real sagas — including the one this was built against — model compensation as
an ordinary service task reached by plain sequence flow, not via BPMN's formal compensation-event
mechanism, so matching on naming convention is what's actually checkable without engine-level
semantics. If your codebase names rollback tasks differently, this linter won't recognise them.

## Quick Start

```bash
mvn clean package
java -jar target/bpmn-saga-linter-*-jar-with-dependencies.jar path/to/processes/
```

Point it at a file or a directory (scanned recursively for `*.bpmn`). Exit code is `1` if any
boundary timer can't reach compensation, `0` otherwise.

```text
Linting: bulk-sim-provisioning.bpmn
  ℹ️  No boundary timer events in this process.
Linting: number-portability-saga.bpmn
  ✅ Boundary timer 'SLA Timeout' reaches compensation.
```

And on a process with the bug:

```text
Linting: timer-skips-compensation.bpmn
  ❌ Boundary timer 'SLA Timeout' (slaTimeout) cannot reach any compensation task — a timeout
     here would skip the rollback the rest of this process relies on.
```

## Running in GitHub Actions

```yaml
- name: Lint BPMN sagas
  run: |
    git clone --depth 1 https://github.com/jihedbfr-art/dev-tools-workbench.git /tmp/dtw
    mvn -f /tmp/dtw/tools/bpmn-saga-linter/pom.xml clean package
    java -jar /tmp/dtw/tools/bpmn-saga-linter/target/bpmn-saga-linter-*-jar-with-dependencies.jar \
      src/main/resources/processes
```

No release has been published yet, so the step above builds from source.

## What was verified

Six tests, all passing: a deliberately broken saga (timer routed to an end event) is caught, a
correct one is accepted, a process without compensation is skipped, and both real BPMN files from
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns) pass
with no false positives — that project's `number-portability-saga.bpmn` deliberately converges its
SLA timeout and rejection branch on the same compensation task, which is exactly the shape this
linter is checking for. The packaged jar was also run manually against both.

No BPMN engine on the classpath: this reads plain DOM via the JDK's own XML parser, matching
elements by local name so files using any namespace prefix (`bpmn:`, `bpmn2:`) work the same.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
