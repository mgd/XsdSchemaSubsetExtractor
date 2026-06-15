# Examples

This directory contains everything needed to run the extractor end-to-end against a
self-contained financial-domain schema set, for both Gradle and Maven builds.

## Structure

```
examples/
  schemas/               Source XSD schemas (input to the extractor)
  test-project/          Gradle project applying the Gradle plugin
  maven-test-project/    Maven project applying the Maven plugin
```

## schemas/

Three interdependent XSD files that exercise every extractor feature:

| File | Role |
|------|------|
| `FinancialMessage_Catalog_v1_0.xsd` | Entry-point; imports Definitions via `xs:import` (cross-namespace) |
| `FinancialMessage_Definitions_v1_0.xsd` | Root elements `Order` and `Settlement`; all PET hierarchies; includes Common via `xs:include` |
| `FinancialMessage_Common_v1_0.xsd` | Shared types in the same namespace as Definitions |

See [schemas/README.md](schemas/README.md) for a full feature breakdown.

---

## test-project/ — Gradle Plugin

A standalone Gradle project that applies `com.wxbrew.schema-extractor`.

### Prerequisites

```bash
# From the repo root — publishes the plugin to examples/test-project's local file repo
./gradlew publishAllPublicationsToLocalRepoRepository
```

### Run

```bash
# From the repo root
./gradlew --project-dir examples/test-project extractXsdSubset
```

Output: `examples/test-project/build/extracted-schemas/Order_v1_0.xsd`, `Settlement_v1_0.xsd`

### Clean

```bash
./gradlew --project-dir examples/test-project clean
```

---

## maven-test-project/ — Maven Plugin

A standalone Maven project that applies `com.wxbrew:xsd-schema-subset-extractor-maven-plugin`.

### Prerequisites

```bash
# 1. Publish core to local Maven repo
./gradlew :core:publishToMavenLocal

# 2. Build and install the Maven plugin
cd maven-plugin && mvn install && cd ..
```

### Run

```bash
cd examples/maven-test-project
mvn generate-resources
```

Output: `examples/maven-test-project/target/extracted-schemas/Order_v1_0.xsd`, `Settlement_v1_0.xsd`

### Clean

```bash
mvn clean
```
