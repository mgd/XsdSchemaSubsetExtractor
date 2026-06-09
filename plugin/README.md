# XSD Extractor Gradle Plugin (`:plugin`)

The Gradle Plugin integrates the Core library into any Gradle project to automate XSD subset extraction during build processes.

## Publishing Locally

Before applying the plugin to a project, publish it to your local Maven repository:

```bash
./gradlew :plugin:publish
```

This publishes the plugin artifacts to a local directory (e.g. `plugin/build/repo` or your local Maven Cache).

## Usage in a Gradle Project

To apply the plugin to another project, add the following to its `settings.gradle` and `build.gradle` (as shown in the scratch test project):

### `settings.gradle`
```groovy
pluginManagement {
    repositories {
        mavenLocal()
        maven { url = uri("${rootDir}/../../plugin/build/repo") } // Path to the local repo directory
        gradlePluginPortal()
    }
}
```

### `build.gradle`
```groovy
plugins {
    id 'com.wxbrew.xsd-extractor' version '1.0.0'
}

extractXsdSubset {
    // Directory containing original schema files
    schemaDir = file("path/to/schemas")
    
    // Name of the main entry-point schema (without .xsd suffix)
    schemaName = "UCI_Versioning"
    
    // Schema version to load (e.g., 2.5.0)
    schemaVersion = "2.5.0"
    
    // List of root elements to extract
    rootElements = ["Entity", "PositionReport"]
    
    // Whether to merge all elements into a single file or generate separate files per root element
    mergeRootElementsIntoSingleSchema = false
    
    // Directory where the extracted schemas should be written
    outputDir = file("build/extracted-schemas")
}
```

## Running the Task

Execute the extraction task:

```bash
./gradlew extractXsdSubset
```

## Cleaning Outputs

To clean the extracted schemas, simply delete the output directory or run:

```bash
./gradlew clean
```
(If the output directory is located under `build/`, it is automatically cleaned when running the standard `clean` task.)
