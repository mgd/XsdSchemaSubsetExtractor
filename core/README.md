# XSD Extractor Core Library (`:core`)

The Core module is a standalone Java 21 library that parses XML Schema (XSD) files, resolves
relative includes/imports, traces element and type dependencies, and writes minimal self-contained
subset schemas.

## Features

- **Transitive Dependency Resolution**: Follows every reachable reference from root elements —
  types, groups, attribute groups, attributes, union member types, list item types, and
  substitution group heads — across any number of imported or included schema files.
- **Polymorphic Extension Type (PET) Support**: Detects abstract complexTypes and automatically
  includes every concrete subtype that extends or restricts them. If a pulled-in subtype is itself
  abstract (a nested PET), its subtypes are included too, recursively.
- **Single-File Self-Contained Output**: In split mode (default), all required types from every
  imported or included schema are inlined into one output file per root element.
  No `<xs:import>` or `<xs:include>` statements remain.
- **Output Fidelity**: Source text is extracted verbatim rather than serialized through DOM,
  so attribute order, XML character references (`&#x20;` etc.), and unescaped `>` in
  documentation are preserved exactly.
- **Namespace Alignment**: Keeps `targetNamespace` in sync with the root element's namespace.
- **Flexible Schema Resolution**: Extensible `SchemaResolver` interface with a built-in
  `FileSystemSchemaResolver` implementation.

## Usage in Java Code

```java
import com.wxbrew.extractor.FileSystemSchemaResolver;
import com.wxbrew.extractor.XsdExtractor;
import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        File schemaDir = new File("examples/schemas");
        File outputDir = new File("build/extracted-schemas");

        FileSystemSchemaResolver resolver = new FileSystemSchemaResolver(schemaDir);
        XsdExtractor extractor = new XsdExtractor(resolver);

        extractor.extract(
            null,                                       // mainSchema File (optional if name/version are set)
            "FinancialMessage_Catalog",                 // schemaName
            "1.0",                                      // schemaVersion
            schemaDir,                                  // schemaDir
            outputDir,                                  // outputDir
            List.of("Order", "Settlement"),             // rootElements
            false                                       // false = split mode, true = merge mode
        );
    }
}
```

## Running Tests

```bash
./gradlew :core:test
```
