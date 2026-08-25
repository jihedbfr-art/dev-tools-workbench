# MSISDN Portability Simulator (MNP Gateway)

<p align="center">
  <b>A standalone, lightweight Mobile Number Portability (MNP) simulator for BSS testing.</b>
</p>

<p align="center">
  <a href="README.fr.md">🇫🇷 Lire en Français</a>
</p>

---

## Why this exists

Testing telecom BSS (Business Support Systems) workflows often requires an MNP (Mobile Number Portability) Gateway to query whether a number belongs to a competitor or is native. In production, this usually relies on proprietary SS7/Diameter signaling or expensive ACQ (All Call Query) REST gateways (e.g., Tietoevry, Titan.ium).

In development, engineers are forced to mock this step by blindly returning `true` or `ACCEPTED`. This hides integration bugs: what happens if the donor operator times out? What if they reject the port? What happens to your BPMN saga or outbox?

This simulator acts as a fake donor operator. It exposes a modern JSON REST API to lookup numbers or request ports, and allows you to **inject configurable chaos** (latency, timeouts, rejections) to harden your orchestrations.

## Features

- **ACQ REST API**: Standard `GET /lookup` and `POST /port`.
- **Configurable Scenarios**: Set global probabilities for `ACCEPTED`, `REJECTED`, or `TIMEOUT`.
- **Operator-specific Overrides**: Simulate a specific operator being down or unreliable.
- **Kafka Integration (Optional)**: Can listen to asynchronous `number-portability-events` and reply via HTTP webhooks, easily integrating into existing Saga patterns.

## Quick Start

### 1. Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

The application starts on port `8080` with an in-memory H2 database seeded with three Tunisian operators (TT, OOR, ORA) and a few test MSISDNs.

### 2. Lookup a Number (ACQ)

```bash
curl -s http://localhost:8080/api/mnp/lookup/+21622999999 | jq
```
```json
{
  "msisdn": "+21622999999",
  "portedStatus": true,
  "donorNetwork": "OOR",
  "recipientNetwork": "TT",
  "routingNumber": "D001"
}
```

### 3. Request a Port

```bash
curl -X POST http://localhost:8080/api/mnp/port \
  -H "Content-Type: application/json" \
  -d '{"msisdn": "+21650000000", "recipientOperatorCode": "OOR"}' | jq
```
```json
{
  "msisdn": "+21650000000",
  "donorOperatorCode": "ORA",
  "recipientOperatorCode": "OOR",
  "decision": "ACCEPTED"
}
```
*(Note: Based on `application.yml` scenario configurations, this might return `REJECTED` or block before throwing a 504 Timeout on the client side).*

## Integration with Kafka & BPMN Sagas

If you are using this alongside a Saga orchestrator (e.g., Camunda), you can enable the Kafka listener:

```yaml
mnp-simulator:
  integration:
    enabled: true
    notification-topic: number-portability-events
    donor-response-base-url: http://localhost:8080 # Your orchestrator webhook URL
```

When it reads a `donor.notification.requested` event from Kafka, it will process the scenario. If it decides to `TIMEOUT`, it will silently drop the request. Otherwise, it will POST `{"decision": "..."}` back to your orchestrator's webhook, simulating a real asynchronous B2B integration.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
