# DB Dialect Portability Tester

<p align="center">
  <b>Rejoue le même SQL contre H2 et un vrai PostgreSQL, et signale chaque endroit où ils divergent — les différences de dialecte qui passent sur un profil de test en mémoire et se manifestent en production.</b>
</p>

<p align="center">
  <a href="README.md">🇬🇧 Read in English</a>
</p>

---

## Pourquoi cet outil existe

Le montage Spring Boot standard fait tourner les tests sur H2 en mémoire et la production sur
PostgreSQL. Le compromis est généralement bon — la suite démarre en une seconde au lieu d'une
minute. Ce qu'il achète, c'est une catégorie de bugs invisible là où on cherche les bugs et qui
n'apparaît que là où on ne cherche pas : la requête est correcte, le schéma est correct, et
l'instruction ne veut pas dire la même chose sur le moteur qui compte.

Ce n'est pas théorique dans cet écosystème. `kafka-outbox-verifier` est parti en production avec
des identifiants quotés en minuscules : H2 replie les noms non quotés en `MAJUSCULES`, PostgreSQL
en `minuscules`, donc une table créée sans guillemets et interrogée avec guillemets ne se résout
que sur l'un des deux. Tous les tests passaient. `bpmn-provisioning-patterns` a perdu du temps sur
une expression `CASE` typée différemment par les deux moteurs.

Rien d'existant ne couvre ça. Les outils de migration vérifient qu'un schéma s'applique ; les ORM
masquent le dialecte — et sont la raison pour laquelle on cesse de le voir ; `sqlfluff` analyse le
style contre un seul dialecte sans rien exécuter. Ici on exécute, sur les deux, et on compare.

## Ce qui est signalé

| Constat | Signification | Fait échouer |
|---|---|---|
| `REJECTED_BY_ONE` | Un moteur a exécuté l'instruction, l'autre l'a refusée. | oui |
| `ROW_COUNT` | Même requête, nombre de lignes différent. | oui |
| `CELL_VALUE` | Même ligne, valeur différente. | oui |
| `UPDATE_COUNT` | Même instruction, nombre de lignes affectées différent. | oui |
| `RESULT_SHAPE` | L'un renvoie un jeu de résultats là où l'autre renvoie un compteur. | oui |
| `COLUMN_TYPE` | Mêmes données, nom de type de colonne différent — `LENGTH()` est `BIGINT` sur H2 et `int4` sur PostgreSQL, ce qui compte pour un appelant qui choisit entre `getLong` et `getInt`. | non |
| `REJECTED_BY_BOTH` | Les deux ont refusé. Comportement cohérent, donc pas un problème de portabilité — généralement une vraie erreur dans le script, parfois une contrainte qui fait son travail. | non |

Les noms de types qui désignent le même type dans deux vocabulaires — `VARCHAR` et
`character varying`, `INTEGER` et `int4` — ne sont pas signalés du tout. Un outil qui les remonte
apprend à ses utilisateurs à l'ignorer.

Code de sortie : `0` si rien de comportemental ne diffère, `1` si quelque chose diffère, `2` si la
comparaison n'a pas pu avoir lieu.

## Démarrage rapide

```bash
mvn clean package
```

Produit `target/db-dialect-portability-tester-<version>-jar-with-dependencies.jar`, avec les deux
pilotes à l'intérieur.

```bash
java -jar db-dialect-portability-tester.jar \
  --script=examples/outbox-and-idempotency.sql \
  --postgres-url=jdbc:postgresql://localhost:5432/app \
  --user=postgres --password=secret
```

Le fichier [`examples/outbox-and-idempotency.sql`](examples/outbox-and-idempotency.sql) fourni est
le vrai schéma outbox et idempotence de
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns), suivi
des instructions que l'application émet réellement. Exécuté contre PostgreSQL 16, il affiche :

```
Compared 10 statement(s): H2 vs PostgreSQL

  ❌ [REJECTED_BY_ONE] statement 5 — relay - claim the batch, with the table name quoted
       PostgreSQL accepted it; H2 refused: Table "portability_outbox" not found
       (candidates are: "PORTABILITY_OUTBOX")

  ⚠️  [COLUMN_TYPE] statement 7 — build a diagnostic label by concatenation
       column 1 is CHARACTER VARYING on H2 and text on PostgreSQL
  ⚠️  [REJECTED_BY_BOTH] statement 9 — idempotency - the redelivery must be refused by the primary key
       H2: Unique index or primary key violation | PostgreSQL: duplicate key value violates unique constraint

1 divergence(s) that change behaviour, 2 note(s) that do not.
```

L'instruction 9 est le non-constat intéressant : les deux moteurs refusent l'insertion en double,
c'est-à-dire le pattern consommateur idempotent qui fonctionne. Un refus cohérent n'est pas un
problème de portabilité, et l'outil le dit au lieu de le compter contre vous.

### Écrire un script

Les instructions sont séparées par des points-virgules — ceux à l'intérieur d'une chaîne ne coupent
pas, donc `INSERT INTO t VALUES ('a;b')` survit. Un commentaire `-- name: quelque chose` étiquette
l'instruction qui suit, et le rapport utilise l'étiquette au lieu de tronquer le SQL.

## Il n'écrit jamais dans votre base

Le rejeu complet se déroule dans une seule transaction annulée à la fin, sur les deux moteurs. Tous
deux ont un DDL transactionnel, donc les tables créées par le script disparaissent avec elle. C'est
ce qui rend sûr de le pointer sur une base qui contient déjà des données, et ce qui fait qu'une
seconde exécution se comporte comme la première au lieu d'échouer sur `relation already exists`.

Chaque instruction obtient aussi son propre savepoint. Sans cela, la première instruction refusée
par PostgreSQL avorte la transaction et toutes les suivantes échouent sur
`current transaction is aborted` — une divergence réelle transformée en une page de divergences
inventées.

## L'utiliser en CI

Le script a sa place dans le dépôt, à côté des migrations, et la comparaison a sa place sur chaque
pull request qui y touche. Un service container PostgreSQL et un `java -jar` suffisent ; le code de
sortie fait échouer le build.

Il vaut la peine de le lancer deux fois, avec `--h2-url` complété par `;MODE=PostgreSQL` au second
passage. H2 par défaut répond à « est-ce que ça marche sur le moteur qu'utilisent mes tests » ; H2
en mode de compatibilité PostgreSQL répond à « est-ce que ce mode couvre réellement l'écart ». Ce
sont deux questions différentes, et la seconde est celle qui décide si le mode vaut le coup.

## Ce qui a été vérifié

- **17 tests, aucun ignoré**, exécutés contre **PostgreSQL 16.15 dans un conteneur** — les
  divergences affirmées ici ont été observées, pas supposées : le refus de l'identifiant quoté,
  `to_regclass` qui n'existe que d'un côté, `SELECT 1 || 2` que H2 coerce et que PostgreSQL rejette,
  et `LENGTH()` qui ne renvoie pas la même largeur selon le moteur.
- **L'annulation est testée, pas seulement voulue** : un test crée une table via la comparaison,
  puis vérifie sur une connexion neuve qu'elle n'y est pas.
- **Sans URL PostgreSQL, les tests de comparaison sont ignorés et le disent.** Ils ne sont pas
  silencieusement comptés comme passés — l'hypothèse est dans `@BeforeEach` et non dans
  `@BeforeAll`, précisément parce qu'un `@BeforeAll` avorté affiche `Tests run: 0`, ce qui se lit
  comme un build vert. La CI fait en plus échouer le job si un seul test a été ignoré.
- **Pas de Testcontainers.** Un outil dont le sujet est « la même chose se comporte différemment
  selon le moteur en dessous » ne doit pas casser quand le moteur local bouge — et c'est le cas
  ici : `docker-java` est rejeté par l'API de Docker Engine 29, ce qui aurait laissé la suite ne
  rien exécuter du tout. La moitié PostgreSQL est une URL JDBC, fournie par un service container
  en CI.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
