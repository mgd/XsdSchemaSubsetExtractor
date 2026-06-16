# XSD Extractor Gradle Plugin (`:plugin`)

**Plugin ID:** `com.wxbrew.schema-extractor`  
**Maven Central:** `com.wxbrew:xsd-schema-subset-extractor-gradle-plugin:1.0.0`

The Gradle Plugin integrates the Core library into any Gradle project to automate XSD subset
extraction during the build.

## Publishing Locally

Before applying the plugin to another project, publish it to the local file repository:

```bash
./gradlew publishAllPublicationsToLocalRepoRepository
```

## Usage in a Gradle Project

### `settings.gradle`
```groovy
pluginManagement {
    repositories {
        // Path relative to settings.gradle's location; adjust depth to match your layout
        maven { url = uri("${settingsDir}/../../plugin/build/repo") }
        gradlePluginPortal()
    }
}
```

### `build.gradle`
```groovy
plugins {
    id 'com.wxbrew.schema-extractor' version '1.0.0'
}

schemaExtractor {
    // Base name of the entry-point schema file (without version suffix or .xsd)
    schemaName = 'FinancialMessage_Catalog'

    // Version string used to locate the file: schemaName + "_v" + version.replace('.','_') + ".xsd"
    schemaVersion = '1.0'

    // Directory containing all source schema files (entry-point and its imports/includes)
    schemaDir = file('examples/schemas')

    // Where to write the extracted output files
    outputDir = layout.buildDirectory.dir('extracted-schemas')

    // Root elements to extract; each produces its own output file in split mode
    rootElements = ['Order', 'Settlement']

    // false = one file per root element (split mode); true = single merged file
    mergeRootElementsIntoSingleSchema = false
}
```

## Running the Task

```bash
./gradlew extractXsdSubset
```

Output files are written to the configured `outputDir` named
`<RootElement>_v<version>.xsd` (e.g. `Order_v1_0.xsd`, `Settlement_v1_0.xsd`).

## Cleaning Outputs

```bash
./gradlew clean
```

If `outputDir` is under `build/`, it is removed automatically by the standard `clean` task.
