# Kafka Idempotent Consumer Scaffolder

<p align="center">
  <b>Génère la migration <code>processed_events</code> et le repository du pattern consommateur idempotent — avec les pièges de dialecte et de transaction déjà écrits dans le code produit.</b>
</p>

<p align="center">
  <a href="README.md">🇬🇧 Read in English</a>
</p>

---

## Pourquoi cet outil existe

Kafka garantit une livraison *at-least-once*. Tout consommateur qui touche une base a donc besoin
des mêmes quarante lignes : une table indexée par identifiant d'événement, un insert qui laisse la
base rejeter la deuxième livraison, et un appelant qui saute le traitement quand elle l'a rejetée.
Spring n'en fournit rien, et les bibliothèques qui existent (retry topics de `spring-kafka`,
starters de déduplication divers) résolvent un autre problème : elles réessaient, elles ne se
souviennent pas.

Alors on le réécrit à la main, projet après projet. Ça se passe très bien jusqu'aux trois endroits
où c'est régulièrement écrit faux :

- **Le `SELECT` avant l'`INSERT`.** C'est l'implémentation qui vient naturellement, et c'est une
  course : deux consommateurs dans la même fenêtre de rééquilibrage peuvent tous deux lire
  « jamais vu » avant que l'un des deux n'écrive. Seule la contrainte d'unicité tranche
  correctement.
- **La garde qui committe toute seule.** Si `markProcessed` part dans sa propre transaction et que
  le handler échoue ensuite, l'événement est marqué traité alors que son effet n'a jamais eu lieu —
  et la redélivrance que Kafka s'apprête à envoyer sera ignorée. La garde doit partager la
  transaction du handler.
- **L'insert qui ne marche que sur un moteur.** `ON CONFLICT DO NOTHING` renvoie zéro ligne sur
  PostgreSQL ; H2, hors mode de compatibilité PostgreSQL, ne sait même pas analyser la clause et
  signale le doublon en levant une exception. On teste sur H2, on tourne sur PostgreSQL, et l'un
  des deux chemins n'a jamais été exécuté.

Cet outil écrit la version qui traite les trois, et place la raison de chacun en commentaire à côté
du code — pour que la personne suivante sache ce qu'elle défait.

## Ce qui est généré

| Fichier | Ce que c'est |
|---|---|
| `db/migration/V1__processed_events.sql` | La table, sa clé primaire (qui *est* le mécanisme d'idempotence) et l'index dont la purge a besoin. |
| `<package>/ProcessedEventRepository.java` | Un repository `JdbcTemplate` avec `markProcessed(...)` et `purgeProcessedBefore(...)`, gérant à la fois le cas « zéro ligne » et le cas « exception levée ». |

Tous les identifiants du SQL généré sont en minuscules et sans guillemets, et le générateur refuse
les noms qui en auraient besoin. Ce n'est pas une règle de style : H2 replie les identifiants non
quotés en MAJUSCULES et PostgreSQL en minuscules, donc un nom quoté créé sur un moteur devient
introuvable par la même requête sur l'autre. Cet écosystème a déjà perdu du temps sur ce bug exact
dans `kafka-outbox-verifier`.

## Démarrage rapide

```bash
mvn clean package
```

Produit `target/kafka-idempotent-consumer-scaffolder-<version>-jar-with-dependencies.jar`.

```bash
java -jar kafka-idempotent-consumer-scaffolder.jar \
  --package=com.example.app.idempotency \
  --dialect=postgres \
  --out=src/main
```

| Option | Défaut | |
|---|---|---|
| `--package` | *obligatoire* | Package Java du repository généré. |
| `--class-name` | `ProcessedEventRepository` | |
| `--table` | `processed_events` | Minuscules, sans guillemets. |
| `--event-id-column` | `event_id` | |
| `--dialect` | `postgres` | `postgres` ou `h2` — ne décide que de l'INSERT ; le DDL est identique. |
| `--migration-version` | `V1` | Préfixe de version Flyway. |
| `--out` | `./generated` | |
| `--dry-run` | | Affiche les fichiers au lieu de les écrire. |
| `--force` | | Écrase les fichiers existants (refusé sinon). |

Reste le câblage — la partie que le générateur ne peut pas faire à ta place :

```java
@KafkaListener(topics = "donor-response-events")
@Transactional
public void onEvent(String payload) {
    DonorResponseEvent event = objectMapper.readValue(payload, DonorResponseEvent.class);

    if (!processedEvents.markProcessed(event.eventId(), "donor-response-events", event.requestId())) {
        return; // redélivrance, déjà traitée
    }

    // le travail métier, dans cette même transaction
}
```

## D'où vient le code généré

Pas d'un article de blog : de
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns), où ce
pattern tourne avec `ProcessedEventRepository` et `V1__outbox_and_processed_events.sql`. Chaque
commentaire du code généré marque quelque chose qui a d'abord mal tourné là-bas.

## Ce qui a été vérifié

- **15 tests, aucun ignoré.** La suite ne se contente pas de comparer des chaînes : le DDL généré
  est exécuté contre un vrai H2 embarqué, l'INSERT généré est joué deux fois avec le même
  identifiant d'événement, et le second est rejeté par la clé primaire. Un générateur testé
  uniquement contre des chaînes attendues passe au vert en produisant du SQL qu'aucun moteur
  n'accepte.
- **Le Java généré compile.** Les sorties PostgreSQL et H2 ont toutes deux été compilées avec
  `javac` contre le vrai classpath Spring de `bpmn-provisioning-patterns` (`spring-jdbc`,
  `spring-context`). Le scaffolder lui-même n'a aucune dépendance Spring — il produit du code
  Spring, il n'en exécute jamais.
- **L'INSERT PostgreSQL n'a pas été exécuté.** `ON CONFLICT DO NOTHING` demande un vrai PostgreSQL
  et aucun démon Docker n'était disponible dans cet environnement. La clause est du PostgreSQL
  standard et l'autre branche du repository est celle qu'H2 exerce, mais le chemin PostgreSQL est à
  considérer comme relu, pas comme exécuté.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
