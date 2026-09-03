# Kafka Outbox Verifier

<p align="center">
  <b>Un CLI en lecture seule qui audite une table outbox transactionnelle existante pour les pannes qu'un relais n'annonce jamais lui-même.</b>
</p>

<p align="center">
  <a href="README.md">🇬🇧 Read in English</a>
</p>

---

## Pourquoi cet outil existe

De nombreuses bibliothèques open source implémentent le pattern outbox transactionnel
(`goharvest`, `kafkaesque`, et d'autres). Aucune n'audite un outbox déjà en production — elles
aident à construire le relais, pas à dire qu'il est bloqué. Un relais qui a arrêté de sonder
silencieusement, un job de purge jamais câblé, ou un message rejoué indéfiniment se ressemblent
tous de l'extérieur : la table grossit, et personne ne le remarque avant qu'un client ne s'en
plaigne.

Cet outil lit la table une seule fois et signale ce qu'un relais sain ne produirait jamais : des
lignes bloquées en attente, des lignes envoyées jamais purgées, et des compteurs de tentatives qui
ne s'arrêtent jamais de grimper. Il ne présume d'aucune bibliothèque particulière ayant écrit la
ligne — seulement que la table suit l'état via des timestamps nullables, ce qui correspond à la
forme réelle de la plupart des implémentations d'outbox (voir
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns) et sa
table `portability_outbox`, celle contre laquelle cet outil a été construit).

## Ce qu'il vérifie

| Signal | Ce que ça signifie |
|---|---|
| **Stagnantes** | Lignes encore en attente (ni complétion, ni échec) depuis plus longtemps que le seuil de stagnation — le relais est en panne, pas juste lent. |
| **En surcharge (bloat)** | Lignes complétées depuis plus longtemps que le seuil de purge et jamais purgées — un job de purge n'est pas câblé. |
| **Latence de publication (p50/p95/p99)** | Le temps réel pris par les lignes complétées, informatif — pas de seuil, juste de la visibilité. |
| **Échouées** | Lignes avec un timestamp d'échec renseigné — informatif ; une ligne mise en dead-letter après de vraies tentatives est un comportement attendu, pas un bug en soi. |
| **Boucles de retry** | Lignes dont le compteur de tentatives atteint le seuil configuré — quelque chose échoue en boucle sans jamais atteindre un état terminal. |

Le code de sortie est `0` uniquement quand les compteurs de lignes stagnantes, en surcharge et en
boucle de retry sont tous à zéro — c'est ça, « sain », indépendamment du nombre d'échecs ou des
chiffres de latence.

## Démarrage rapide

```bash
mvn clean package
```

Produit `target/kafka-outbox-verifier-<version>-jar-with-dependencies.jar`, avec le driver
PostgreSQL déjà embarqué.

```bash
java -jar kafka-outbox-verifier.jar \
  --jdbc-url=jdbc:postgresql://localhost:5432/provisioning \
  --user=provisioning --password=secret \
  --table=portability_outbox \
  --completed-at-column=published_at \
  --failed-at-column=failed_at \
  --retry-count-column=attempts
```

`--id-column` et `--created-at-column` valent par défaut `id` et `created_at` ;
`--completed-at-column` vaut par défaut `published_at`. `--failed-at-column` et
`--retry-count-column` sont optionnels — omettez celui que votre table ne possède pas, et le
signal correspondant est simplement ignoré plutôt que deviné.

Les seuils par défaut sont 5 minutes (stagnation), 24 heures (surcharge) et 5 tentatives (boucle
de retry) — surchargeables via `--stagnation-minutes`, `--bloat-hours`, `--retry-threshold`.

## Utilisation en CI ou en tâche planifiée

C'est un diagnostic en lecture seule, pas une vérification ponctuelle de migration — il est fait
pour tourner régulièrement contre une base réelle, par exemple via une GitHub Action planifiée ou
une tâche cron sur une connexion via `kubectl port-forward`, pour alerter (via son code de sortie)
dès que l'outbox dérive hors de l'état sain, plutôt que de le découvrir seulement quand un client
se plaint.

## Ce qui a été vérifié

Testé contre une base H2 en mémoire dont la forme reproduit exactement la table
`portability_outbox` de `bpmn-provisioning-patterns` (9 tests couvrant chaque signal, y compris le
cas d'une table sans colonne d'échec ni de compteur de tentatives). Toute la logique de comparaison
tourne en Java, pas en SQL, volontairement — une expression `CASE` brute sur cette même table a
déjà cassé une fois dans cet écosystème parce que H2 et PostgreSQL ne s'accordent pas sur le
typage implicite (voir l'historique de `OutboxRepository.markFailed` dans
`bpmn-provisioning-patterns`).

**Exécuté contre un vrai PostgreSQL 16.15** le 3 septembre 2026, sur une table
`portability_outbox` peuplée d'une ligne bloquée, d'une non purgée, d'une en boucle de retry et
d'une en échec : chaque signal a été correctement remonté, et le code de sortie valait `1` sur la
table malade puis `0` une fois qu'il ne restait que la ligne saine. La réserve que portait cette
section est levée.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
