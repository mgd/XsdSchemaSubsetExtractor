package com.wxbrew.app;

import com.wxbrew.extractor.FileSystemSchemaResolver;
import com.wxbrew.extractor.XsdExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Command-line entry point for the XSD schema subset extractor. */
public class App {

    /** Creates the application instance. */
    public App() {}

    /**
     * Parses command-line arguments and runs the schema extraction.
     *
     * @param args command-line arguments; run with {@code --help} for usage
     */
    public static void main(String[] args) {
        String mainSchema = null;
        String outputDir = null;
        String schemaDir = null;
        String schemaName = null;
        String schemaVersion = null;
        boolean merge = false;
        List<String> rootElements = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-m":
                case "--main-schema":
                    if (i + 1 < args.length) {
                        mainSchema = args[++i];
                    }
                    break;
                case "-n":
                case "--schema-name":
                    if (i + 1 < args.length) {
                        schemaName = args[++i];
                    }
                    break;
                case "-v":
                case "--schema-version":
                    if (i + 1 < args.length) {
                        schemaVersion = args[++i];
                    }
                    break;
                case "-o":
                case "--output-dir":
                    if (i + 1 < args.length) {
                        outputDir = args[++i];
                    }
                    break;
                case "-d":
                case "--schema-dir":
                    if (i + 1 < args.length) {
                        schemaDir = args[++i];
                    }
                    break;
                case "-r":
                case "--root-elements":
                    if (i + 1 < args.length) {
                        String roots = args[++i];
                        rootElements.addAll(Arrays.asList(roots.split(",")));
                    }
                    break;
                case "-g":
                case "--merge":
                    merge = true;
                    break;
                case "-h":
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (outputDir == null || rootElements.isEmpty()) {
            System.err.println("Error: Missing required parameters.");
            printUsage();
            System.exit(1);
        }

        File mainSchemaFile = mainSchema != null ? new File(mainSchema) : null;
        File schemaDirFile = schemaDir != null ? new File(schemaDir) : null;
        File outputDirFile = new File(outputDir);

        File resolvedSchemaDir = schemaDirFile;
        if (resolvedSchemaDir == null) {
            if (mainSchemaFile != null) {
                resolvedSchemaDir = mainSchemaFile.getParentFile();
            } else {
                resolvedSchemaDir = new File(".");
            }
        }

        System.out.println("Main schema: " + (mainSchemaFile != null ? mainSchemaFile.getAbsolutePath() : "None"));
        System.out.println("Schema directory: " + resolvedSchemaDir.getAbsolutePath());
        System.out.println("Schema name parameter: " + (schemaName != null ? schemaName : "None"));
        System.out.println("Schema version parameter: " + (schemaVersion != null ? schemaVersion : "None"));
        System.out.println("Output directory: " + outputDirFile.getAbsolutePath());
        System.out.println("Root elements: " + rootElements);
        System.out.println("Merge root elements into single schema: " + merge);

        try {
            FileSystemSchemaResolver resolver = new FileSystemSchemaResolver(resolvedSchemaDir);
            XsdExtractor extractor = new XsdExtractor(resolver);
            extractor.extract(
                    mainSchemaFile,
                    schemaName,
                    schemaVersion,
                    schemaDirFile,
                    outputDirFile,
                    rootElements,
                    merge
            );
            System.out.println("Schema extraction completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during schema extraction:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar app.jar [options]");
        System.out.println("Options:");
        System.out.println("  -m, --main-schema <path>       Path to the main .xsd file (optional if name and version are set)");
        System.out.println("  -n, --schema-name <name>       Base name of the main schema (e.g., UCI_MessageDefinitions)");
        System.out.println("  -v, --schema-version <version> Version of the schema (e.g., 2.5.0)");
        System.out.println("  -o, --output-dir <path>        (Required) Path to directory to write the subset schemas");
        System.out.println("  -r, --root-elements <el1,el2>  (Required) Comma-separated list of root elements to extract");
        System.out.println("  -d, --schema-dir <path>        (Optional) Directory containing imported/included schemas");
        System.out.println("  -g, --merge                    (Optional) Merge all root elements into a single schema instead of splitting");
        System.out.println("  -h, --help                     Show this help message");
    }
}
