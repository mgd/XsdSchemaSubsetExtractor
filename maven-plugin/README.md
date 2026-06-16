# XSD Extractor Maven Plugin (`maven-plugin`)

Maven plugin that wraps the Core library, providing a `extract-xsd-subset` goal that integrates
XSD subset extraction into any Maven build.

**Coordinates:** `com.wxbrew:xsd-schema-subset-extractor-maven-plugin:1.0.0`

## Prerequisites

The `xsd-schema-subset-extractor` core library must be in your local Maven repository before building this plugin:

```bash
# From the repo root
./gradlew :core:publishToMavenLocal
```

## Building and Installing

```bash
cd maven-plugin
mvn install
```

This compiles the plugin and installs it to `~/.m2/repository/com/wxbrew/xsd-schema-subset-extractor-maven-plugin/`.

## Usage in a Maven Project

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.wxbrew</groupId>
            <artifactId>xsd-schema-subset-extractor-maven-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>extract-xsd-subset</goal>
                    </goals>
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
    </plugins>
</build>
```

## Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `schemaName` | `String` | — | Base name of the entry-point schema (without version suffix or `.xsd`) |
| `schemaVersion` | `String` | — | Version used to locate the file: `name + "_v" + version.replace('.','_') + ".xsd"` |
| `schemaDir` | `File` | — | Directory containing all source schema files |
| `mainSchema` | `File` | — | Optional direct path to the entry-point `.xsd` file; `schemaDir` defaults to its parent |
| `outputDirectory` | `File` | `${project.build.directory}/extracted-schemas` | Where to write extracted output files |
| `rootElements` | `List<String>` | — | Root elements to extract (required) |
| `mergeRootElementsIntoSingleSchema` | `boolean` | `false` | Merge all root elements into one file instead of one file each |

## Running the Goal Directly

```bash
mvn com.wxbrew:xsd-schema-subset-extractor-maven-plugin:1.0.0:extract-xsd-subset
```

Or, after adding the plugin to your `pom.xml`:

```bash
mvn generate-resources
```

The goal binds to the `generate-resources` phase by default.

## Cleaning Output

```bash
mvn clean
```

The `outputDirectory` is under `target/` by default, so it is removed by `mvn clean`.
