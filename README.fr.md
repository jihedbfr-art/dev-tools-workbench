# dev-tools-workbench

[![CI - msisdn-portability-simulator](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-msisdn-portability-simulator.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-msisdn-portability-simulator.yml)
[![CI - keycloak-spi-linter](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-keycloak-spi-linter.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-keycloak-spi-linter.yml)
[![CI - kafka-outbox-verifier](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-kafka-outbox-verifier.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-kafka-outbox-verifier.yml)
[![CI - bpmn-saga-linter](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-bpmn-saga-linter.yml/badge.svg)](https://github.com/jihedbfr-art/dev-tools-workbench/actions/workflows/ci-bpmn-saga-linter.yml)

[English version](./README.md)

Des outils de développement petits et ciblés, pour des trous que l'outillage mature ne couvre
pas — pas une nouvelle collection de choses que `hadolint`, `Spring Boot Admin` ou `pgbadger`
font déjà mieux. Chaque outil ici existe parce qu'il a d'abord été confronté au paysage existant ;
voir [`docs/selection-criteria.md`](docs/selection-criteria.md) pour la méthode de sélection et
[`docs/rejected-candidates.md`](docs/rejected-candidates.md) pour les idées qui n'ont pas survécu
au filtre.

## Statut

- [`msisdn-portability-simulator`](tools/msisdn-portability-simulator) — livré. Simule le côté
  donneur d'un échange de portabilité de numéro (MNP) derrière une passerelle REST façon ACQ.
- [`keycloak-spi-linter`](tools/keycloak-spi-linter) — livré. Détecte les enregistrements
  `META-INF/services` cassés d'un provider SPI Keycloak avant qu'ils n'échouent au déploiement.
- [`kafka-outbox-verifier`](tools/kafka-outbox-verifier) — livré. Audite une table outbox
  transactionnelle existante à la recherche de lignes bloquées, non purgées, ou en boucle de
  retry.
- [`bpmn-saga-linter`](tools/bpmn-saga-linter) — livré. Fait échouer le build quand un timer de
  frontière d'une saga ne peut pas atteindre la tâche de compensation sur laquelle repose le
  reste du processus.
- Un testeur de portabilité de dialecte H2/PostgreSQL et un générateur de squelette de
  consommateur idempotent Kafka sont les suivants — voir
  [`docs/selection-criteria.md`](docs/selection-criteria.md) pour la liste complète et pourquoi
  chacun a été retenu.

## Structure

Chaque outil arrive dans `tools/<nom-outil>/` comme un module autonome avec son propre README —
pas de framework commun qui forcerait chaque outil dans le même moule, un linter CLI et un
scaffolder Spring Boot n'ayant rien en commun au-delà de vivre dans le même dépôt.

```
dev-tools-workbench/
├── tools/                     un module autonome par outil accepté
├── docs/
│   ├── selection-criteria.md  comment un outil est accepté, et la liste actuelle
│   └── rejected-candidates.md idées écartées, et ce qui les couvre déjà
└── LICENSE
```

## Licence

MIT — voir [LICENSE](LICENSE).

---

<div align="center">
  <img src="assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
