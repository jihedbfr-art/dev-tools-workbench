# Keycloak SPI Linter

<p align="center">
  <b>A fast, standalone CLI linter for Keycloak SPI configurations (META-INF/services).</b>
</p>

<p align="center">
  <a href="README.fr.md">🇫🇷 Lire en Français</a>
</p>

---

## The Problem

When developing Keycloak extensions (SPIs), you must declare your provider factories in `META-INF/services/<fully-qualified-interface>`. If this file is missing, misspelled, or if your class lacks a no-argument constructor, **Keycloak will not complain during compilation**. The extension simply won't load at runtime, or it will throw confusing reflection errors during server startup.

## The Solution

`keycloak-spi-linter` is a pure Java CLI tool designed to run in your CI pipeline (e.g., GitHub Actions). It validates your compiled classes and `META-INF/services` files against 5 strict rules:

1. **Existence:** The class referenced in the services file must exist.
2. **Contract:** The class must implement the Keycloak interface specified by the file name.
3. **Instantiability:** The class must have a public, no-argument constructor.
4. **Valid ID:** The `getId()` method must return a non-null, non-empty String.
5. **Uniqueness:** No two providers of the same SPI type can share the same ID.

## Quick Start

### 1. Build the Linter

```bash
mvn clean package
```
This produces a fat jar in `target/keycloak-spi-linter-<version>-jar-with-dependencies.jar`.

### 2. Run the Linter

You run the linter by pointing it to the `target/classes` directory of your Keycloak extension project. 

**Important:** Because the linter uses standard JDK reflection to dynamically inspect your classes, it needs your project's dependencies (e.g., Keycloak SPI jars) to load them successfully. You can provide these by passing additional classpath directories.

```bash
java -jar keycloak-spi-linter.jar /path/to/your/project/target/classes /path/to/your/project/target/dependency
```

If a rule is violated, the CLI exits with code `1` and prints a clear error:
```text
Validating SPI: org.keycloak.events.EventListenerProviderFactory
  ❌ Class 'com.example.MyProviderFactory' does not have a public getId() method.
Validating SPI: org.keycloak.authentication.AuthenticatorFactory
  ❌ Class 'com.example.BadAuthenticatorFactory' is missing a public no-arg constructor.
```

## Running in GitHub Actions

You can easily integrate this into your CI before the deployment step:

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
          
      - name: Build the target project and download its dependencies
        run: |
          mvn clean compile
          mvn dependency:copy-dependencies

      - name: Build the linter
        run: |
          git clone --depth 1 https://github.com/jihedbfr-art/dev-tools-workbench.git /tmp/dev-tools-workbench
          mvn -f /tmp/dev-tools-workbench/tools/keycloak-spi-linter/pom.xml clean package

      - name: Run Keycloak SPI Linter
        run: |
          java -jar /tmp/dev-tools-workbench/tools/keycloak-spi-linter/target/keycloak-spi-linter-*-jar-with-dependencies.jar \
            target/classes target/dependency
```

There's no published release yet — the workflow above builds the linter from source each run. Check
this repo's README for whether a packaged release exists by the time you're reading this.

---

<div align="center">
  <img src="../../assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
