# dev-tools-workbench

[English version](./README.md)

Des outils de développement petits et ciblés, pour des trous que l'outillage mature ne couvre
pas — pas une nouvelle collection de choses que `hadolint`, `Spring Boot Admin` ou `pgbadger`
font déjà mieux. Chaque outil ici existe parce qu'il a d'abord été confronté au paysage existant ;
voir [`docs/selection-criteria.md`](docs/selection-criteria.md) pour la méthode de sélection et
[`docs/rejected-candidates.md`](docs/rejected-candidates.md) pour les idées qui n'ont pas survécu
au filtre.

## Statut

Ce dépôt démarre vide, volontairement. La liste des outils prévus en premier — un simulateur de
portabilité de numéro (MNP), un linter de SPI Keycloak, un auditeur d'outbox Kafka, un testeur de
portabilité de dialecte H2/PostgreSQL, un linter de compensation de saga BPMN, et un générateur de
squelette de consommateur idempotent Kafka — se trouve dans
[`docs/selection-criteria.md`](docs/selection-criteria.md). Rien n'est publié tant que ça ne
fonctionne pas et n'a pas été réellement utilisé, pas juste commité pour prouver que la liste
existe.

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
