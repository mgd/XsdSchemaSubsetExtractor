# XSD Extractor CLI Application (`:app`)

The Standalone Application module wraps the Core library in a user-friendly CLI tool.

## Build and Package

```bash
./gradlew :app:assemble
```

This generates:
- Distribution ZIP/tar archives in `app/build/distributions/`
- Application JAR in `app/build/libs/`

## Running the Application

### 1. Run using Gradle (Development)
```bash
./gradlew :app:run --args="-o build/extracted-schemas -r Order,Settlement -n FinancialMessage_Catalog -v 1.0 -d examples/schemas"
```

### 2. Run via Executable JAR
```bash
java -jar app/build/libs/app-1.0.0.jar -o build/extracted-schemas -r Order,Settlement -n FinancialMessage_Catalog -v 1.0 -d examples/schemas
```

## CLI Reference

```text
Usage: java -jar app.jar [options]
Options:
  -m, --main-schema <path>       Path to the main .xsd file (optional if name and version are set)
  -n, --schema-name <name>       Base name of the main schema (e.g., FinancialMessage_Catalog)
  -v, --schema-version <version> Version of the schema (e.g., 1.0)
  -o, --output-dir <path>        (Required) Path to directory to write the subset schemas
  -r, --root-elements <el1,el2>  (Required) Comma-separated list of root elements to extract
  -d, --schema-dir <path>        (Optional) Directory containing imported/included schemas
  -g, --merge                    (Optional) Merge all root elements into a single schema instead of splitting
  -h, --help                     Show this help message
```
