# Example Schemas

Financial-domain XSD schemas that exercise every feature of the extractor. Use these as a
reference when evaluating output or testing changes to the extraction logic.

## Schema Structure

```
FinancialMessage_Catalog_v1_0.xsd          ← entry-point (xs:import → Definitions)
  └── FinancialMessage_Definitions_v1_0.xsd ← root elements + all types (xs:include → Common)
        └── FinancialMessage_Common_v1_0.xsd ← shared simple types, attribute groups, complex types
```

The extractor is configured with `schemaName=FinancialMessage_Catalog` and
`schemaVersion=1.0`, producing one output file per root element:

| Root element | Output file |
|---|---|
| `Order` | `Order_v1_0.xsd` |
| `Settlement` | `Settlement_v1_0.xsd` |

## Features Demonstrated

### Multi-file traversal
- `xs:import` (cross-namespace): Catalog → Definitions
- `xs:include` (same namespace): Definitions → Common

### Polymorphic Extension Types (PETs)
Each abstract type causes the extractor to pull in all concrete subtypes recursively.

| Abstract type | Concrete subtypes | Notes |
|---|---|---|
| `InstrumentPET` | `EquityInstrumentType`, `DebtInstrumentType` | Also has nested PET |
| `DerivativeInstrumentPET` | `OptionType`, `FutureType` | Nested PET — extends `InstrumentPET` and is itself abstract |
| `PartyPET` | `IndividualPartyType`, `InstitutionalPartyType` | |
| `PaymentInstructionPET` | `WireTransferType`, `BookTransferType` | |

### Other schema constructs
- `xs:group` — `MessageHeaderGroup` referenced by both root element types
- `xs:attributeGroup` — `VersionedAttrs` defined in Common, referenced in Definitions
- `xs:list` — `OrderSideListType` (list of `OrderSideType`)
- `xs:union` — `IdentifierOrUnknownType` (union of `IdentifierTokenType` and `xs:string`)
- `&#x20;` character reference in the `IdentifierTokenType` pattern facet
- Unescaped `>` in documentation text (preserved verbatim in output)
- Custom namespace attributes (`fm:version`) on schema components
