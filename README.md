# XSD Schema Subset Extractor

A Java-based utility and Gradle plugin that extracts minimal, self-contained XML Schema (XSD) files from a large multi-file schema definition.

Given one or more root element names, the extractor traces all transitive dependencies—types, groups, attribute groups, attributes, and polymorphic subtypes—and produces a single flat XSD per root element with no `<xs:import>` or `<xs:include>` statements.

---

## What It Handles

### Transitive Dependency Resolution
Starting from a root element, the extractor walks every reference reachable from that element and includes it in the output:

- `type` and `base` attribute references (complex and simple types)
- `ref` attribute references (elements, groups, attributes, attribute groups)
- `memberTypes` (xs:union)
- `itemType` (xs:list)
- `substitutionGroup` head elements

All references are namespace-aware and resolved across any number of imported or included schema files.

### Polymorphic Extension Type (PET) Support
When a type is marked `abstract="true"`, it acts as a Polymorphic Extension Type — XML instances use `xsi:type` to supply a concrete subtype at runtime. The extractor automatically detects these and pulls in **every type that extends or restricts the abstract type**, recursively:

- Direct subtypes of an abstract type are included
- If a pulled-in subtype is itself abstract (a nested PET), its own subtypes are included
- This continues until no new abstract types remain

For example, if `InstrumentPET` is abstract with two concrete subtypes and one abstract subtype (`DerivativeInstrumentPET`), all concrete leaves of `DerivativeInstrumentPET` are included automatically.

### Multi-File Schema Traversal
Source schemas that span multiple files via `<xs:include>` or `<xs:import>` are fully supported. The extractor parses the entire reachable schema graph upfront and resolves cross-file references transparently.

### Split Mode (default)
Each root element produces its own output file. All required types from every imported or included schema are inlined directly into that single file. The output file:

- Has no `<xs:import>` or `<xs:include>` elements
- Uses the root element's own target namespace
- Is named `<RootElement>_v<Version>.xsd` (e.g. `Order_v1_0.xsd`)

### Merge Mode
All root elements are extracted together into a single output file, preserving the original schema file structure with import/include paths rewritten to reference only the files that were actually needed.

### Output Fidelity
The extractor writes output by lifting element text directly from the source files rather than round-tripping through DOM serialization, which means:

- **Attribute order** is preserved exactly as in the source schema
- **XML character references** such as `&#x20;` and `&#x7E;` in pattern facets are kept as-is
- **Unescaped `>` in text content** (e.g. documentation strings) is left unescaped, matching the source
- The XML declaration is `<?xml version="1.0" encoding="UTF-8"?>` with no `standalone` attribute

---

## Example Schemas

The [examples/schemas/](examples/schemas/) directory contains a complete set of financial-domain
schemas that exercise every extractor feature:

| File | Role |
|------|------|
| `FinancialMessage_Catalog_v1_0.xsd` | Entry-point schema; imports Definitions via `xs:import` |
| `FinancialMessage_Definitions_v1_0.xsd` | Root elements (`Order`, `Settlement`); all PET hierarchies; includes Common via `xs:include` |
| `FinancialMessage_Common_v1_0.xsd` | Shared types (same namespace as Definitions) |

Features demonstrated by the example schemas:

- **Nested PET**: `DerivativeInstrumentPET` extends `InstrumentPET` and is itself abstract, with `OptionType` and `FutureType` as concrete leaves
- **`&#x20;` character reference** in `IdentifierTokenType` pattern facet
- **Unescaped `>`** in documentation text
- **`xs:list`** (`OrderSideListType`) and **`xs:union`** (`IdentifierOrUnknownType`)
- **`xs:group`** (`MessageHeaderGroup`) and **`xs:attributeGroup`** (`VersionedAttrs`)
- **Cross-namespace `xs:import`** (Catalog → Definitions) and **same-namespace `xs:include`** (Definitions → Common)
- **Custom namespace attributes** (`fm:version`) preserved in output

---

## Project Structure

| Module | Description |
|--------|-------------|
| **[core/](core/README.md)** | Core library: schema parsing, dependency tracing, PET expansion, and XSD generation |
| **[app/](app/README.md)** | Standalone CLI application wrapping the core library |
| **[plugin/](plugin/README.md)** | Gradle plugin for integrating extraction into a Gradle build |
| **[maven-plugin/](maven-plugin/README.md)** | Maven plugin for integrating extraction into a Maven build |
| **[examples/](examples/README.md)** | Example financial-domain schemas with both a Gradle and Maven test project |

---

## Quick Start

### CLI

```bash
./gradlew :app:run --args="-o build/extracted-schemas -r Order,Settlement -n FinancialMessage_Catalog -v 1.0 -d examples/schemas"
```

See [app/README.md](app/README.md) for the full CLI reference.

### Gradle Plugin

```groovy
// build.gradle
plugins {
    id 'com.wxbrew.schema-extractor' version '1.0.0'
}

schemaExtractor {
    schemaName    = 'FinancialMessage_Catalog'
    schemaVersion = '1.0'
    schemaDir     = file('examples/schemas')
    outputDir     = layout.buildDirectory.dir('extracted-schemas')
    rootElements  = ['Order', 'Settlement']
    mergeRootElementsIntoSingleSchema = false
}
```

```bash
./gradlew extractXsdSubset
```

See [plugin/README.md](plugin/README.md) for full setup and configuration.

### Maven Plugin

```xml
<plugin>
    <groupId>com.wxbrew</groupId>
    <artifactId>xsd-schema-subset-extractor-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals><goal>extract-xsd-subset</goal></goals>
        </execution>
    </executions>
    <configuration>
        <schemaName>FinancialMessage_Catalog</schemaName>
        <schemaVersion>1.0</schemaVersion>
        <schemaDir>${project.basedir}/src/main/schemas</schemaDir>
        <rootElements>
            <rootElement>Order</rootElement>
            <rootElement>Settlement</rootElement>
        </rootElements>
    </configuration>
</plugin>
```

```bash
mvn generate-resources
```

See [maven-plugin/README.md](maven-plugin/README.md) for full setup and configuration.

### Java API

```java
XsdExtractor extractor = new XsdExtractor(new FileSystemSchemaResolver(schemaDir));
extractor.extract(
    null,                                      // mainSchema file (optional when name/version provided)
    "FinancialMessage_Catalog",                // schemaName
    "1.0",                                     // schemaVersion
    schemaDir,                                 // directory containing all schema files
    outputDir,                                 // where to write output
    List.of("Order", "Settlement"),            // root elements to extract
    false                                      // false = split mode, true = merge mode
);
```

See [core/README.md](core/README.md) for API details.

---

## Build Commands

### Gradle

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all Gradle modules and run tests |
| `./gradlew validateAll` | Run the full local validation flow, including Maven plugin verification, with Java 25 |
| `./gradlew clean` | Delete all Gradle build outputs |
| `./gradlew :core:test` | Run unit tests |
| `./gradlew publishAllPublicationsToLocalRepoRepository` | Publish plugin to the local file repo used by `examples/test-project` |
| `./gradlew :core:publishToMavenLocal` | Publish core to `~/.m2` (required before building the Maven plugin) |

### Maven

Run Maven plugin validation with Java 25. `./gradlew validateAll` wires this up automatically by launching Maven with an explicit Java 25 `JAVA_HOME`.

| Command | Description |
|---------|-------------|
| `cd maven-plugin && mvn install` | Build and install the Maven plugin to `~/.m2` |
| `cd maven-plugin && mvn clean` | Delete Maven plugin build output |

---

## Development Containers

| Branch | Purpose | Dev container |
|---|---|---|
| `java/dev/devcontainer` | Lightweight default path for downstream developers | `.devcontainer/devcontainer.json` |
| `java/dev/claude-devcontainer` | Protected status-quo path for Claude Code users and automation that still needs the heavier image | `.devcontainer/claude/devcontainer.json` |

- `.devcontainer/devcontainer.json` installs Java 25, Gradle, Maven, and GitHub CLI, but does **not** install Node/npm or Claude Code.
- `.devcontainer/claude/devcontainer.json` preserves the existing `ghcr.io/wxbrew/devcontainers/java-gradle-mvn-claude:latest` image.
- GitHub Actions CI now installs Temurin JDK 25 explicitly before running the Gradle and Maven validation steps.
- Configure branch protection for `java/dev/claude-devcontainer` in the repository settings.

---

## SonarQube in CI

The GitHub Actions CI workflow now runs a SonarQube/SonarCloud scan after the existing build and test steps whenever the `SONAR_TOKEN` repository secret is configured.

Set this repository secret and, if needed, these repository variables:

- `SONAR_TOKEN` (secret) — required to authenticate the scan and backlog sync
- `SONAR_HOST_URL` — defaults to `https://sonarcloud.io`
- `SONAR_ORGANIZATION` — defaults to the GitHub repository owner
- `SONAR_PROJECT_KEY` — defaults to `<owner>_<repo>`

On pushes to `main`, the workflow also syncs open SonarQube findings into grouped GitHub backlog issues labeled `sonarqube` and `technical-debt`, so repeated findings for the same Sonar rule are tracked together.

---

## Requirements

- **Java**: JDK 25
- **Gradle**: 9.0 or higher (wrapper included)
