package com.wxbrew.plugin;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** Configuration extension for the {@code schemaExtractor} block in a Gradle build script. */
public interface SchemaExtractorExtension {
    /** {@return the entry-point schema file; takes precedence over schema name/version when set} */
    RegularFileProperty getMainSchema();
    /** {@return the base name of the entry-point schema (without version suffix or {@code .xsd})} */
    Property<String> getSchemaName();
    /** {@return the version string used to locate the file: {@code <name>_v<version>.xsd}} */
    Property<String> getSchemaVersion();
    /** {@return the directory containing all source schema files} */
    DirectoryProperty getSchemaDir();
    /** {@return the directory where extracted output files are written} */
    DirectoryProperty getOutputDir();
    /** {@return the root element names to extract; each produces its own output file in split mode} */
    ListProperty<String> getRootElements();
    /** {@return true when all root elements should be merged into one output file} */
    Property<Boolean> getMergeRootElementsIntoSingleSchema();
}
