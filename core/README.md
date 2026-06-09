# XSD Extractor Core Library (`:core`)

The Core module is a standalone Java 21 library that parses XML Schema (XSD) files, resolves relative includes/imports, traces element/type dependencies, and writes a minimal clean subset schema.

## Features

- **Transitive Dependency Resolution**: Automatically traces all types (simple, complex, groups, attributes) required by the target root elements.
- **Single-File Self-Contained Output**: In split mode (default), all types from included/imported schemas are merged directly into a single output file matching the root element's namespace, leaving no `<xs:import>` or `<xs:include>` tags.
- **Custom Namespace Alignment**: Keeps targetNamespace in sync with the root element's namespace to guarantee references resolve cleanly.
- **Flexible Schema Resolving**: Extensible `SchemaResolver` interface with a `FileSystemSchemaResolver` implementation.

## Usage in Java Code

To use the extractor in your Java project:

```java
import com.wxbrew.extractor.FileSystemSchemaResolver;
import com.wxbrew.extractor.XsdExtractor;
import java.io.File;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        File schemaDir = new File("path/to/schemas");
        File outputDir = new File("build/extracted-schemas");
        
        // 1. Create a resolver pointing to the source schemas directory
        FileSystemSchemaResolver resolver = new FileSystemSchemaResolver(schemaDir);
        
        // 2. Initialize the extractor
        XsdExtractor extractor = new XsdExtractor(resolver);
        
        // 3. Extract the subset for a list of root elements
        extractor.extract(
            null,                                     // mainSchema File (optional if name/version are used)
            "UCI_Versioning",                         // schemaName
            "2.5.0",                                  // schemaVersion
            schemaDir,                                // schemaDir
            outputDir,                                // outputDir
            Arrays.asList("Entity", "PositionReport"),// rootElements
            false                                     // mergeRootElementsIntoSingleSchema (false to split)
        );
    }
}
```

## Running Tests

To run the unit tests in the core module:

```bash
./gradlew :core:test
```
