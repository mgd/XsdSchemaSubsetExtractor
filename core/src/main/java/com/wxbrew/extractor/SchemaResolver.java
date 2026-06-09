package com.wxbrew.extractor;

import java.io.IOException;
import java.io.InputStream;

public interface SchemaResolver {
    /**
     * Resolves a schema location relative to a parent system ID.
     *
     * @param parentSystemId the system ID of the parent schema
     * @param schemaLocation the location of the schema to import/include
     * @return the InputStream for the schema, or null if not resolved
     */
    InputStream resolve(String parentSystemId, String schemaLocation) throws IOException;

    /**
     * Returns a unique absolute path or identifier for the resolved schema.
     */
    String getAbsoluteSystemId(String parentSystemId, String schemaLocation);
}
