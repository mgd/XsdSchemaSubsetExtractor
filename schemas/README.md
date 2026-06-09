# Schema Definitions

This directory contains the original XML Schema (XSD) files for the Universal Command and Control Interface (UCI).

## Root Schema

- **[UCI_MessageDefinitions_v2_5_0.xsd](file:///home/md/Public%20Libs/XsdSchemaSubsetExtractor/schemas/UCI_MessageDefinitions_v2_5_0.xsd)**
  This is the primary entry point schema. It defines all the messages and core elements/types and includes the security markings schema.

## Included/Imported Schemas

- **[UCI_SecurityMarkings_v2_5_0.xsd](file:///home/md/Public%20Libs/XsdSchemaSubsetExtractor/schemas/UCI_SecurityMarkings_v2_5_0.xsd)**
  Included by the root schema. Defines security/classification fields.
- **[UCI_Versioning_v2_5_0.xsd](file:///home/md/Public%20Libs/XsdSchemaSubsetExtractor/schemas/UCI_Versioning_v2_5_0.xsd)**
  Imports the root schema. Defines versioning-related attributes and simple types.
