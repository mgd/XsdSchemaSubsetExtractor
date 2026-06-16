package com.wxbrew.extractor;

import java.io.IOException;
import java.io.InputStream;

/** Strategy for locating and reading XSD schema files. */
public interface SchemaResolver {
    /**
     * Resolves a schema location relative to a parent system ID.
     *
     * @param parentSystemId the system ID of the parent schema, or null for the root
     * @param schemaLocation the location string from an xs:include or xs:import
     * @return an InputStream for the schema content, or null if not resolvable
     * @throws IOException if the schema file cannot be read
     */
    InputStream resolve(String parentSystemId, String schemaLocation) throws IOException;

    /**
     * Returns a unique absolute identifier for the resolved schema.
     *
     * @param parentSystemId the system ID of the parent schema, or null for the root
     * @param schemaLocation the location string from an xs:include or xs:import
     * @return the absolute system ID, or null if the location cannot be resolved
     */
    String getAbsoluteSystemId(String parentSystemId, String schemaLocation);
}
