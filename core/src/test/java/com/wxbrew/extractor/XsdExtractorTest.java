package com.wxbrew.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class XsdExtractorTest {

    @Test
    public void testGetXsdFilename() {
        assertEquals("MySchema.xsd", XsdExtractor.getXsdFilename("MySchema", null));
        assertEquals("MySchema.xsd", XsdExtractor.getXsdFilename("MySchema", ""));
        assertEquals("MySchema.xsd", XsdExtractor.getXsdFilename("MySchema", "2.5.0"));
    }

    @Test
    public void testExtractionMerged(@TempDir File tempDir) throws Exception {
        // Prepare dummy schemas
        File schemaDir = new File(tempDir, "schemas");
        assertTrue(schemaDir.mkdirs());

        String mainXsd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "           targetNamespace=\"http://example.org/test\"\n" +
                "           xmlns:tns=\"http://example.org/test\"\n" +
                "           elementFormDefault=\"qualified\">\n" +
                "    <xs:include schemaLocation=\"TestSchemaSub.xsd\"/>\n" +
                "    <xs:element name=\"RootA\" type=\"tns:TypeA\"/>\n" +
                "    <xs:element name=\"RootB\" type=\"tns:TypeB\"/>\n" +
                "    <xs:complexType name=\"TypeA\">\n" +
                "        <xs:sequence>\n" +
                "            <xs:element name=\"fieldA\" type=\"xs:string\"/>\n" +
                "            <xs:element name=\"sharedField\" type=\"tns:SharedType\"/>\n" +
                "        </xs:sequence>\n" +
                "    </xs:complexType>\n" +
                "    <xs:complexType name=\"TypeB\">\n" +
                "        <xs:sequence>\n" +
                "            <xs:element name=\"fieldB\" type=\"xs:int\"/>\n" +
                "        </xs:sequence>\n" +
                "    </xs:complexType>\n" +
                "</xs:schema>";

        String subXsd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "           targetNamespace=\"http://example.org/test\"\n" +
                "           xmlns:tns=\"http://example.org/test\"\n" +
                "           elementFormDefault=\"qualified\">\n" +
                "    <xs:complexType name=\"SharedType\">\n" +
                "        <xs:sequence>\n" +
                "            <xs:element name=\"value\" type=\"xs:boolean\"/>\n" +
                "        </xs:sequence>\n" +
                "    </xs:complexType>\n" +
                "    <xs:complexType name=\"UnusedType\">\n" +
                "        <xs:sequence>\n" +
                "            <xs:element name=\"unused\" type=\"xs:string\"/>\n" +
                "        </xs:sequence>\n" +
                "    </xs:complexType>\n" +
                "</xs:schema>";

        Files.write(new File(schemaDir, "TestSchemaMain.xsd").toPath(), mainXsd.getBytes());
        Files.write(new File(schemaDir, "TestSchemaSub.xsd").toPath(), subXsd.getBytes());

        File outputDir = new File(tempDir, "output-merged");

        FileSystemSchemaResolver resolver = new FileSystemSchemaResolver(schemaDir);
        XsdExtractor extractor = new XsdExtractor(resolver);

        // Extract with merge = true
        extractor.extract(
                schemaDir.getAbsolutePath(),
                "TestSchemaMain",
                "1.0.0",
                outputDir,
                Arrays.asList("RootA", "RootB"),
                true
        );

        // Verify files generated
        File outMain = new File(outputDir, "TestSchemaMain.xsd");
        File outSub = new File(outputDir, "TestSchemaSub.xsd");

        assertTrue(outMain.exists());
        assertTrue(outSub.exists());

        String mainContent = Files.readString(outMain.toPath());
        String subContent = Files.readString(outSub.toPath());

        // Both roots should be in the main schema
        assertTrue(mainContent.contains("name=\"RootA\""));
        assertTrue(mainContent.contains("name=\"RootB\""));
        assertTrue(mainContent.contains("name=\"TypeA\""));
        assertTrue(mainContent.contains("name=\"TypeB\""));

        // SharedType should be in sub schema, but UnusedType should be removed
        assertTrue(subContent.contains("name=\"SharedType\""));
        assertFalse(subContent.contains("name=\"UnusedType\""));
    }

    @Test
    public void testExtractionSplit(@TempDir File tempDir) throws Exception {
        // Prepare dummy schemas
        File schemaDir = new File(tempDir, "schemas");
        assertTrue(schemaDir.mkdirs());

        String mainXsd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "           targetNamespace=\"http://example.org/test\"\n" +
                "           xmlns:tns=\"http://example.org/test\"\n" +
                "           elementFormDefault=\"qualified\">\n" +
                "    <xs:include schemaLocation=\"TestSchemaSub.xsd\"/>\n" +
                "    <xs:element name=\"RootA\" type=\"tns:TypeA\"/>\n" +
                "    <xs:element name=\"RootB\" type=\"tns:TypeB\"/>\n" +
                "    <xs:complexType name=\"TypeA\">\n" +
                "        <xs:sequence>\n" +
                "            <xs:element name=\"fieldA\" type=\"xs:string\"/>\n" +
                "            <xs:element name=\"sharedField\" type=\"tns:SharedType\"/>\n" +
                "        </xs:sequence>\n" +
                "    </xs:complexType>\n" +
                "    <xs:complexType name=\"TypeB\">\n" +
                "        <xs:sequence>\n" +
                "            <xs:element name=\"fieldB\" type=\"xs:int\"/>\n" +
                "        </xs:sequence>\n" +
                "    </xs:complexType>\n" +
                "</xs:schema>";

        String subXsd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "           targetNamespace=\"http://example.org/test\"\n" +
                "           xmlns:tns=\"http://example.org/test\"\n" +
                "           elementFormDefault=\"qualified\">\n" +
                "    <xs:complexType name=\"SharedType\">\n" +
                "        <xs:sequence>\n" +
                "            <xs:element name=\"value\" type=\"xs:boolean\"/>\n" +
                "        </xs:sequence>\n" +
                "    </xs:complexType>\n" +
                "</xs:schema>";

        Files.write(new File(schemaDir, "TestSchemaMain.xsd").toPath(), mainXsd.getBytes());
        Files.write(new File(schemaDir, "TestSchemaSub.xsd").toPath(), subXsd.getBytes());

        File outputDir = new File(tempDir, "output-split");

        FileSystemSchemaResolver resolver = new FileSystemSchemaResolver(schemaDir);
        XsdExtractor extractor = new XsdExtractor(resolver);

        // Extract with merge = false (split)
        extractor.extract(
                schemaDir.getAbsolutePath(),
                "TestSchemaMain",
                "1.0.0",
                outputDir,
                Arrays.asList("RootA", "RootB"),
                false
        );

        // RootA should generate exactly: RootA_v1_0_0.xsd (fully self-contained, no sub-schemas)
        File outRootAMain = new File(outputDir, "RootA_v1_0_0.xsd");
        File outRootASub = new File(outputDir, "RootA_v1_0_0_TestSchemaSub.xsd");

        assertTrue(outRootAMain.exists());
        assertFalse(outRootASub.exists());

        String rootAMainContent = Files.readString(outRootAMain.toPath());

        // RootA main should contain RootA, TypeA, and SharedType (inlined)
        assertTrue(rootAMainContent.contains("name=\"RootA\""));
        assertFalse(rootAMainContent.contains("name=\"RootB\""));
        assertTrue(rootAMainContent.contains("name=\"TypeA\""));
        assertFalse(rootAMainContent.contains("name=\"TypeB\""));
        assertTrue(rootAMainContent.contains("name=\"SharedType\""));
        assertFalse(rootAMainContent.contains("include"));

        // RootB should generate: RootB_v1_0_0.xsd, and NOT generate RootB_v1_0_0_TestSchemaSub.xsd
        File outRootBMain = new File(outputDir, "RootB_v1_0_0.xsd");
        File outRootBSub = new File(outputDir, "RootB_v1_0_0_TestSchemaSub.xsd");

        assertTrue(outRootBMain.exists());
        assertFalse(outRootBSub.exists());

        String rootBMainContent = Files.readString(outRootBMain.toPath());
        assertTrue(rootBMainContent.contains("name=\"RootB\""));
        assertFalse(rootBMainContent.contains("name=\"RootA\""));
        assertTrue(rootBMainContent.contains("name=\"TypeB\""));
        assertFalse(rootBMainContent.contains("name=\"TypeA\""));
        assertFalse(rootBMainContent.contains("name=\"SharedType\""));
        assertFalse(rootBMainContent.contains("include"));
    }
}
