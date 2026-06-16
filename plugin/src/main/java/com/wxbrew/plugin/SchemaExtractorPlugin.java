package com.wxbrew.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Gradle plugin that registers the {@code extractXsdSubset} task. */
public class SchemaExtractorPlugin implements Plugin<Project> {

    /** Creates the plugin instance. */
    public SchemaExtractorPlugin() {}

    @Override
    public void apply(Project project) {
        SchemaExtractorExtension extension = project.getExtensions().create("schemaExtractor", SchemaExtractorExtension.class);

        project.getTasks().register("extractXsdSubset", SchemaExtractorTask.class, task -> {
            task.getMainSchema().set(extension.getMainSchema());
            task.getSchemaName().set(extension.getSchemaName());
            task.getSchemaVersion().set(extension.getSchemaVersion());
            task.getSchemaDir().set(extension.getSchemaDir());
            task.getOutputDir().set(extension.getOutputDir());
            task.getRootElements().set(extension.getRootElements());
            task.getMergeRootElementsIntoSingleSchema().set(extension.getMergeRootElementsIntoSingleSchema());
        });
    }
}
