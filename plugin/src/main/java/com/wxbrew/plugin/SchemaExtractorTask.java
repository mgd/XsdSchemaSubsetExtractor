package com.wxbrew.plugin;

import com.wxbrew.extractor.FileSystemSchemaResolver;
import com.wxbrew.extractor.XsdExtractor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.List;

public abstract class SchemaExtractorTask extends DefaultTask {

    @InputFile
    @Optional
    public abstract RegularFileProperty getMainSchema();

    @Input
    @Optional
    public abstract Property<String> getSchemaName();

    @Input
    @Optional
    public abstract Property<String> getSchemaVersion();

    @InputDirectory
    @Optional
    public abstract DirectoryProperty getSchemaDir();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Input
    public abstract ListProperty<String> getRootElements();

    @Input
    @Optional
    public abstract Property<Boolean> getMergeRootElementsIntoSingleSchema();

    @TaskAction
    public void executeExtraction() {
        File mainSchemaFile = getMainSchema().isPresent() ? getMainSchema().get().getAsFile() : null;
        File schemaDirFile = getSchemaDir().isPresent() ? getSchemaDir().get().getAsFile() : null;
        File outputDirFile = getOutputDir().get().getAsFile();
        List<String> roots = getRootElements().get();
        boolean merge = getMergeRootElementsIntoSingleSchema().getOrElse(false);

        String name = getSchemaName().getOrNull();
        String version = getSchemaVersion().getOrNull();

        File resolvedSchemaDir = schemaDirFile;
        if (resolvedSchemaDir == null) {
            if (mainSchemaFile != null) {
                resolvedSchemaDir = mainSchemaFile.getParentFile();
            } else {
                resolvedSchemaDir = new File(".");
            }
        }

        getLogger().lifecycle("Main schema: {}", mainSchemaFile);
        getLogger().lifecycle("Schema directory: {}", resolvedSchemaDir);
        getLogger().lifecycle("Schema name parameter: {}", name != null ? name : "None");
        getLogger().lifecycle("Schema version parameter: {}", version != null ? version : "None");
        getLogger().lifecycle("Output directory: {}", outputDirFile);
        getLogger().lifecycle("Root elements: {}", roots);
        getLogger().lifecycle("Merge root elements into single schema: {}", merge);

        try {
            FileSystemSchemaResolver resolver = new FileSystemSchemaResolver(resolvedSchemaDir);
            XsdExtractor extractor = new XsdExtractor(resolver);
            extractor.extract(
                    mainSchemaFile,
                    name,
                    version,
                    schemaDirFile,
                    outputDirFile,
                    roots,
                    merge
            );
            getLogger().lifecycle("XSD schema subset extraction completed successfully.");
        } catch (Exception e) {
            throw new RuntimeException("XSD schema subset extraction failed", e);
        }
    }
}
