# XSD Schema Subset Extractor

A Java-based utility and Gradle plugin to extract minimal, self-contained XML Schema (XSD) files from a large schema definition. 

This program traces all types transitively required by selected root elements and generates single-file, zero-import/zero-include XSDs for those elements, preserving target namespaces and inlining required types directly.

## Project Structure

This project is organized as a Gradle multi-project build:

- **[core/](core/README.md)**: The core library containing the dependency tracing graph, XML parsing, target namespace modification, and XSD generation logic.
- **[app/](app/README.md)**: A standalone command-line interface (CLI) client to extract schemas from the console.
- **[plugin/](plugin/README.md)**: A Gradle plugin to integrate subset extraction tasks directly into a Gradle build pipeline.

---

## Dependencies / Requirements

- **Java**: JDK 21 or higher
- **Gradle**: Gradle 9.0 or higher (wrapper included)

---

## Global Build and Task Commands

### 1. Build and Test Everything
Build the core library, CLI, and plugin, and run all unit tests:
```bash
./gradlew build
```

### 2. Clean the Project
Clean all compiled outputs, classes, and build files:
```bash
./gradlew clean
```

### 3. Run Tests only
Run the unit tests in the core module:
```bash
./gradlew test
```

### 4. Publish the Plugin Locally
Publish the plugin to the local Maven repository to make it available for local projects:
```bash
./gradlew publish
```

---

## Module Instructions

For module-specific documentation, refer to the individual README files:

1. **[Core Library Instructions](core/README.md)**: API usage, schema resolvers, and programmatic integration.
2. **[CLI App Instructions](app/README.md)**: Installation, CLI flags, options, and standalone jar execution.
3. **[Gradle Plugin Instructions](plugin/README.md)**: Gradle setup, project DSL configuration, and task usage.
