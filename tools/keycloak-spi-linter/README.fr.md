# Keycloak SPI Linter

<p align="center">
  <b>Un linter CLI autonome et rapide pour les configurations SPI Keycloak (META-INF/services).</b>
</p>

<p align="center">
  <a href="README.md">🇬🇧 Read in English</a>
</p>

---

## Le Problème

Lors du développement d'extensions Keycloak (SPI), vous devez déclarer vos implémentations de factory dans `META-INF/services/<interface-pleinement-qualifiée>`. Si ce fichier est manquant, mal nommé, ou si votre classe n'a pas de constructeur sans argument, **Keycloak ne signalera aucune erreur à la compilation**. L'extension ne se chargera tout simplement pas au moment de l'exécution, ou lèvera des erreurs de réflexion cryptiques au démarrage du serveur.

## La Solution

`keycloak-spi-linter` est un outil CLI pur Java conçu pour s'exécuter dans votre pipeline CI (ex: GitHub Actions). Il valide vos classes compilées et vos fichiers `META-INF/services` selon 5 règles strictes :

1. **Existence :** La classe référencée dans le fichier services doit exister.
2. **Contrat :** La classe doit implémenter l'interface Keycloak spécifiée par le nom du fichier.
3. **Instanciation :** La classe doit posséder un constructeur public sans argument.
4. **ID Valide :** La méthode `getId()` ne doit pas retourner `null` ou une chaîne vide.
5. **Unicité :** Deux providers du même type de SPI ne peuvent pas partager le même ID.

## Démarrage Rapide

### 1. Compiler le Linter

```bash
mvn clean package
```
Cela génère un fat jar dans `target/keycloak-spi-linter-<version>-jar-with-dependencies.jar`.

### 2. Exécuter le Linter

Vous exécutez le linter en pointant vers le dossier `target/classes` de votre projet d'extension Keycloak.

**Important :** Parce que le linter utilise la réflexion standard du JDK pour inspecter dynamiquement vos classes, il a besoin des dépendances de votre projet (ex: les jars Keycloak SPI) pour les charger avec succès. Vous pouvez les fournir en passant des répertoires classpath supplémentaires.

```bash
java -jar keycloak-spi-linter.jar /chemin/vers/votre/projet/target/classes /chemin/vers/votre/projet/target/dependency
```

Si une règle est violée, la CLI sort avec le code `1` et affiche une erreur claire :
```text
Validating SPI: org.keycloak.events.EventListenerProviderFactory
  ❌ Class 'com.example.MyProviderFactory' does not have a public getId() method.
Validating SPI: org.keycloak.authentication.AuthenticatorFactory
  ❌ Class 'com.example.BadAuthenticatorFactory' is missing a public no-arg constructor.
```

## Utilisation dans GitHub Actions

Intégrez facilement cet outil dans votre CI avant l'étape de déploiement :

```yaml
jobs:
  build-and-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Compiler le projet cible et télécharger ses dépendances
        run: |
          mvn clean compile
          mvn dependency:copy-dependencies

      - name: Compiler le linter
        run: |
          git clone --depth 1 https://github.com/jihedbfr-art/dev-tools-workbench.git /tmp/dev-tools-workbench
          mvn -f /tmp/dev-tools-workbench/tools/keycloak-spi-linter/pom.xml clean package

      - name: Exécuter Keycloak SPI Linter
        run: |
          java -jar /tmp/dev-tools-workbench/tools/keycloak-spi-linter/target/keycloak-spi-linter-*-jar-with-dependencies.jar \
            target/classes target/dependency
```

Il n'y a pas encore de release publiée — le workflow ci-dessus compile le linter depuis les
sources à chaque exécution. Vérifiez le README de ce dépôt pour savoir si un artefact packagé
existe entre-temps.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
