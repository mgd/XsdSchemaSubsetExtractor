package com.wxbrew.extractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** {@link SchemaResolver} that resolves schema locations against the local filesystem. */
public class FileSystemSchemaResolver implements SchemaResolver {
    private final File baseDir;

    /**
     * Creates a resolver that looks up schemas relative to {@code baseDir}.
     *
     * @param baseDir the root directory containing the schema files
     */
    public FileSystemSchemaResolver(File baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public InputStream resolve(String parentSystemId, String schemaLocation) throws IOException {
        File targetFile = getFile(parentSystemId, schemaLocation);
        if (targetFile != null && targetFile.exists()) {
            return new FileInputStream(targetFile);
        }
        return null;
    }

    @Override
    public String getAbsoluteSystemId(String parentSystemId, String schemaLocation) {
        File targetFile = getFile(parentSystemId, schemaLocation);
        return targetFile != null ? targetFile.getAbsolutePath() : null;
    }

    private File getFile(String parentSystemId, String schemaLocation) {
        if (schemaLocation == null || schemaLocation.trim().isEmpty()) {
            return null;
        }

        File file = new File(schemaLocation);
        if (file.isAbsolute()) {
            return file;
        }

        if (parentSystemId != null) {
            File parentFile = new File(parentSystemId);
            File relativeToParent = new File(parentFile.getParentFile(), schemaLocation);
            if (relativeToParent.exists()) {
                return relativeToParent;
            }
        }

        if (baseDir != null) {
            File relativeToBase = new File(baseDir, schemaLocation);
            if (relativeToBase.exists()) {
                return relativeToBase;
            }
        }

        // Default fallback
        return baseDir != null ? new File(baseDir, schemaLocation) : file;
    }
}
