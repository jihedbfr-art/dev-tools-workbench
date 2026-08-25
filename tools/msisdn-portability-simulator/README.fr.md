# Simulateur de Portabilité MSISDN (MNP Gateway)

<p align="center">
  <b>Un simulateur de portabilité (MNP) léger et autonome pour les tests BSS.</b>
</p>

<p align="center">
  <a href="README.md">🇬🇧 Read in English</a>
</p>

---

## Pourquoi cet outil existe

Tester des workflows télécom BSS nécessite souvent un MNP Gateway pour vérifier si un numéro appartient à un concurrent. En production, cela repose sur des systèmes lourds (SS7/Diameter) ou des passerelles ACQ (All Call Query) coûteuses (Tietoevry, Titan.ium).

En développement, les ingénieurs sont contraints de mocker cette étape en retournant aveuglément `true` ou `ACCEPTED`. Cela masque les vrais bugs d'intégration : que se passe-t-il si l'opérateur donneur ne répond pas (timeout) ? S'il rejette la demande ? Comment réagit votre saga BPMN ?

Ce simulateur agit comme un opérateur donneur factice. Il expose une API REST moderne pour les requêtes de portabilité, et vous permet d'**injecter du chaos configurable** (latence, timeouts, rejets) pour éprouver la résilience de vos orchestrateurs.

## Fonctionnalités

- **API REST ACQ** : `GET /lookup` et `POST /port`.
- **Scénarios configurables** : Définissez les probabilités globales de `ACCEPTED`, `REJECTED`, et `TIMEOUT`.
- **Surcharges par opérateur** : Simulez l'instabilité d'un opérateur donneur spécifique.
- **Intégration Kafka (Optionnelle)** : Peut écouter le topic `number-portability-events` et répondre via un webhook HTTP asynchrone (idéal pour les Sagas).

## Démarrage rapide

### 1. Compiler et Lancer

```bash
mvn clean install
mvn spring-boot:run
```

L'application démarre sur le port `8080` avec une base H2 en mémoire, pré-chargée avec trois opérateurs tunisiens (TT, OOR, ORA) et des MSISDN de test.

### 2. Consulter un numéro (ACQ)

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

### 3. Demander un portage

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

## Intégration Kafka pour Saga BPMN

Si vous utilisez cet outil avec un orchestrateur de processus, activez le module d'intégration Kafka :

```yaml
mnp-simulator:
  integration:
    enabled: true
    notification-topic: number-portability-events
    donor-response-base-url: http://localhost:8080 # L'URL de votre orchestrateur
```

À la réception d'un événement `donor.notification.requested`, le simulateur calcule son scénario. S'il décide d'un `TIMEOUT`, il ignore silencieusement la requête. Sinon, il exécute un POST asynchrone `{"decision": "..."}` vers le webhook de votre orchestrateur pour simuler un vrai retour B2B.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
