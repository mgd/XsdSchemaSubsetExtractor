package com.wxbrew.maven;

import com.wxbrew.extractor.FileSystemSchemaResolver;
import com.wxbrew.extractor.XsdExtractor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.List;

/**
 * Extracts minimal, self-contained XSD subset files from a larger schema definition.
 * Binds to the generate-resources phase by default.
 */
@Mojo(name = "extract-xsd-subset", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class SchemaExtractorMojo extends AbstractMojo {

    /** Base name of the entry-point schema file, without version suffix or .xsd extension. */
    @Parameter
    private String schemaName;

    /** Version string used to locate the schema file: schemaName + "_v" + version.replace('.','_') + ".xsd". */
    @Parameter
    private String schemaVersion;

    /** Directory containing all source schema files (entry-point and its imports/includes). */
    @Parameter
    private File schemaDir;

    /**
     * Path to the entry-point .xsd file. Optional when schemaName and schemaVersion are supplied.
     * When provided, schemaDir defaults to this file's parent directory.
     */
    @Parameter
    private File mainSchema;

    /** Where to write the extracted output files. */
    @Parameter(defaultValue = "${project.build.directory}/extracted-schemas", required = true)
    private File outputDirectory;

    /** Root elements to extract. Each produces its own output file in split mode. */
    @Parameter(required = true)
    private List<String> rootElements;

    /**
     * When false (default), each root element produces its own self-contained output file.
     * When true, all root elements are merged into a single output file.
     */
    @Parameter(defaultValue = "false")
    private boolean mergeRootElementsIntoSingleSchema;

    @Override
    public void execute() throws MojoExecutionException {
        File resolvedSchemaDir = schemaDir;
        if (resolvedSchemaDir == null) {
            resolvedSchemaDir = (mainSchema != null) ? mainSchema.getParentFile() : new File(".");
        }

        getLog().info("Main schema:    " + mainSchema);
        getLog().info("Schema dir:     " + resolvedSchemaDir);
        getLog().info("Schema name:    " + schemaName);
        getLog().info("Schema version: " + schemaVersion);
        getLog().info("Output dir:     " + outputDirectory);
        getLog().info("Root elements:  " + rootElements);
        getLog().info("Merge mode:     " + mergeRootElementsIntoSingleSchema);

        outputDirectory.mkdirs();

        try {
            FileSystemSchemaResolver resolver = new FileSystemSchemaResolver(resolvedSchemaDir);
            XsdExtractor extractor = new XsdExtractor(resolver);
            extractor.extract(
                mainSchema,
                schemaName,
                schemaVersion,
                schemaDir,
                outputDirectory,
                rootElements,
                mergeRootElementsIntoSingleSchema
            );
            getLog().info("XSD schema subset extraction completed successfully.");
        } catch (Exception e) {
            throw new MojoExecutionException("XSD subset extraction failed: " + e.getMessage(), e);
        }
    }
}
