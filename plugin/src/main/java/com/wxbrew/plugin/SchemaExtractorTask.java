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

/** Gradle task that invokes {@link XsdExtractor} to produce a minimal XSD subset. */
public abstract class SchemaExtractorTask extends DefaultTask {

    /** Creates the task instance. */
    public SchemaExtractorTask() {}

    /** {@return the entry-point schema file; takes precedence over schema name/version when set} */
    @InputFile
    @Optional
    public abstract RegularFileProperty getMainSchema();

    /** {@return the base name of the entry-point schema (without version suffix or {@code .xsd})} */
    @Input
    @Optional
    public abstract Property<String> getSchemaName();

    /** {@return the version string used to locate the file: {@code <name>_v<version>.xsd}} */
    @Input
    @Optional
    public abstract Property<String> getSchemaVersion();

    /** {@return the directory containing all source schema files} */
    @InputDirectory
    @Optional
    public abstract DirectoryProperty getSchemaDir();

    /** {@return the directory where extracted output files are written} */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /** {@return the root element names to extract; each produces its own output file in split mode} */
    @Input
    public abstract ListProperty<String> getRootElements();

    /** {@return true when all root elements should be merged into one output file} */
    @Input
    @Optional
    public abstract Property<Boolean> getMergeRootElementsIntoSingleSchema();

    /** Runs the XSD subset extraction. */
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
