# BPMN Saga Linter

<p align="center">
  <b>Vérifie qu'un timeout dans votre saga BPMN ne peut pas contourner silencieusement le rollback sur lequel tout le reste repose.</b>
</p>

<p align="center">
  <a href="README.md">🇬🇧 Read in English</a>
</p>

---

## Pourquoi cet outil existe

`bpmnlint` valide qu'un fichier BPMN est bien formé — éléments connectés, pas de flux orphelin,
pas d'id en double. Il n'a aucun avis sur le fait que le processus qu'il vient d'approuver soit
*sûr*.

Voici la panne qu'il ne peut pas voir. Une saga provisionne quelque chose, attend un système
externe, et cette attente peut mal finir de deux façons : l'autre partie rejette explicitement, ou
le timer de SLA se déclenche parce qu'elle n'a jamais répondu. La branche de rejet route vers une
tâche de compensation qui annule tout. La branche de timeout — ajoutée plus tard, par quelqu'un
d'autre, dans l'urgence — route directement vers un événement de fin.

Le XML est valide. Le diagramme a l'air correct. Et chaque instance expirée laisse du travail
provisionné à l'abandon, sans rien dans les logs pour le signaler.

Ce linter parcourt le graphe de flux depuis chaque timer de frontière et fait échouer le build si
l'un d'eux ne peut pas atteindre une tâche de compensation.

## Ce qu'il vérifie

| Vérification | Sévérité |
|---|---|
| Chaque timer de frontière peut atteindre une tâche de compensation via les flux de séquence | **Échec** (code 1) |
| Une passerelle n'a aucune branche atteignant la compensation | Avertissement (informatif, sans effet sur le code de sortie) |

Les processus sans aucune tâche de compensation sont ignorés entièrement — tous les fichiers BPMN
ne sont pas des sagas, et un simple workflow d'approbation avec un timeout n'a rien à annuler.

**Comment la compensation est identifiée :** tout nœud dont l'id ou le nom contient `compensate`
ou `rollback` (insensible à la casse). Les vraies sagas — y compris celle contre laquelle cet
outil a été construit — modélisent la compensation comme une tâche de service ordinaire atteinte
par un flux de séquence classique, pas via le mécanisme formel d'événement de compensation de
BPMN ; la correspondance par convention de nommage est donc ce qui est réellement vérifiable sans
la sémantique du moteur. Si votre code nomme ses tâches de rollback autrement, ce linter ne les
reconnaîtra pas.

## Démarrage rapide

```bash
mvn clean package
java -jar target/bpmn-saga-linter-*-jar-with-dependencies.jar chemin/vers/processes/
```

Pointez-le vers un fichier ou un répertoire (scanné récursivement pour les `*.bpmn`). Le code de
sortie est `1` si un timer de frontière ne peut pas atteindre la compensation, `0` sinon.

```text
Linting: bulk-sim-provisioning.bpmn
  ℹ️  No boundary timer events in this process.
Linting: number-portability-saga.bpmn
  ✅ Boundary timer 'SLA Timeout' reaches compensation.
```

Et sur un processus qui a le bug :

```text
Linting: timer-skips-compensation.bpmn
  ❌ Boundary timer 'SLA Timeout' (slaTimeout) cannot reach any compensation task — a timeout
     here would skip the rollback the rest of this process relies on.
```

## Utilisation dans GitHub Actions

```yaml
- name: Lint BPMN sagas
  run: |
    git clone --depth 1 https://github.com/jihedbfr-art/dev-tools-workbench.git /tmp/dtw
    mvn -f /tmp/dtw/tools/bpmn-saga-linter/pom.xml clean package
    java -jar /tmp/dtw/tools/bpmn-saga-linter/target/bpmn-saga-linter-*-jar-with-dependencies.jar \
      src/main/resources/processes
```

Aucune release n'est encore publiée, l'étape ci-dessus compile donc depuis les sources.

## Ce qui a été vérifié

Six tests, tous au vert : une saga délibérément cassée (timer routé vers un événement de fin) est
détectée, une saga correcte est acceptée, un processus sans compensation est ignoré, et les deux
vrais fichiers BPMN de
[`bpmn-provisioning-patterns`](https://github.com/jihedbfr-art/bpmn-provisioning-patterns) passent
sans faux positif — le `number-portability-saga.bpmn` de ce projet fait délibérément converger son
timeout de SLA et sa branche de rejet vers la même tâche de compensation, ce qui est exactement la
forme que ce linter recherche. Le jar packagé a également été exécuté manuellement sur les deux.

Aucun moteur BPMN dans le classpath : la lecture se fait en DOM brut via le parseur XML du JDK, en
faisant correspondre les éléments par nom local, pour que les fichiers utilisant n'importe quel
préfixe de namespace (`bpmn:`, `bpmn2:`) fonctionnent de la même façon.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
