package com.wxbrew.plugin;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public interface SchemaExtractorExtension {
    RegularFileProperty getMainSchema();
    Property<String> getSchemaName();
    Property<String> getSchemaVersion();
    DirectoryProperty getSchemaDir();
    DirectoryProperty getOutputDir();
    ListProperty<String> getRootElements();
    Property<Boolean> getMergeRootElementsIntoSingleSchema();
}
