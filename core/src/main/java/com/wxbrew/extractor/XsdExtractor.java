package com.wxbrew.extractor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class XsdExtractor {

    public enum ComponentType {
        ELEMENT, TYPE, GROUP, ATTRIBUTE_GROUP, ATTRIBUTE
    }

    public record ComponentRef(ComponentType type, QName qName) {}

    public record ComponentInfo(Element element, String systemId) {}

    private final SchemaResolver schemaResolver;
    private final Map<String, Document> parsedSchemas = new LinkedHashMap<>();
    private final Map<String, String> sourceContents = new LinkedHashMap<>();
    private final Map<ComponentRef, ComponentInfo> registry = new HashMap<>();
    /** Maps each type to the set of types that directly extend or restrict it. */
    private final Map<ComponentRef, Set<ComponentRef>> subtypeMap = new HashMap<>();

    public XsdExtractor(SchemaResolver schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    public static String getXsdFilename(String schemaName, String schemaVersion) {
        return schemaName + ".xsd";
    }

    private static String stripVersionSuffix(String filename) {
        if (filename == null) {
            return null;
        }
        return filename.replaceAll("_v\\d+(?:_\\d+)*\\.xsd$", ".xsd");
    }

    private String getOutputFilename(String systemId, String mainAbsoluteId, String rootElement, String schemaVersion) {
        String originalName = new File(systemId).getName();
        String cleanName = stripVersionSuffix(originalName);
        if (rootElement == null) {
            return cleanName;
        }
        if (systemId.equals(mainAbsoluteId)) {
            if (schemaVersion != null && !schemaVersion.trim().isEmpty()) {
                return rootElement + "_v" + schemaVersion.trim().replace('.', '_') + ".xsd";
            }
            return rootElement + ".xsd";
        } else {
            return rootElement + "_" + cleanName;
        }
    }

    public void extract(
            File mainSchema,
            String schemaName,
            String schemaVersion,
            File schemaDir,
            File outputDir,
            List<String> rootElements,
            boolean mergeRootElementsIntoSingleSchema
    ) throws Exception {
        String resolvedName = schemaName;
        String resolvedVersion = schemaVersion;
        File resolvedSchemaDir = schemaDir;

        if (resolvedName == null && mainSchema != null) {
            String filename = mainSchema.getName();
            String n = filename;
            if (n.endsWith(".xsd")) {
                n = n.substring(0, n.length() - 4);
            }
            int vIndex = n.lastIndexOf("_v");
            if (vIndex >= 0) {
                resolvedVersion = n.substring(vIndex + 2).replace('_', '.');
                n = n.substring(0, vIndex);
            }
            resolvedName = n;
        }

        if (resolvedName == null) {
            throw new IllegalArgumentException("Schema name must be specified or parsed from mainSchema");
        }

        if (resolvedSchemaDir == null) {
            if (mainSchema != null) {
                resolvedSchemaDir = mainSchema.getParentFile();
            } else {
                resolvedSchemaDir = new File(".");
            }
        }

        extract(resolvedSchemaDir.getAbsolutePath(), resolvedName, resolvedVersion, outputDir, rootElements, mergeRootElementsIntoSingleSchema);
    }

    public void extract(
            String schemaDir,
            String schemaName,
            String schemaVersion,
            File outputDir,
            List<String> rootElements,
            boolean mergeRootElementsIntoSingleSchema
    ) throws Exception {
        String mainSchemaFilename = getXsdFilename(schemaName, schemaVersion);
        File resolvedFile = new File(schemaDir, mainSchemaFilename);
        if (!resolvedFile.exists() && schemaVersion != null && !schemaVersion.trim().isEmpty()) {
            String versionedName = schemaName + "_v" + schemaVersion.trim().replace('.', '_') + ".xsd";
            File versionedFile = new File(schemaDir, versionedName);
            if (versionedFile.exists()) {
                resolvedFile = versionedFile;
            }
        }
        String mainSchemaPath = resolvedFile.getAbsolutePath();

        // 1. Parse all schemas recursively
        parseSchemaRecursive(null, mainSchemaPath);

        // 2. Register all components
        for (Map.Entry<String, Document> entry : parsedSchemas.entrySet()) {
            registerComponents(entry.getValue(), entry.getKey());
        }

        // 2b. Build reverse inheritance map so abstract PETs can pull in their subtypes
        buildSubtypeMap();

        if (mergeRootElementsIntoSingleSchema) {
            extractSubset(mainSchemaPath, outputDir, rootElements, null, schemaVersion);
        } else {
            for (String root : rootElements) {
                extractSubset(mainSchemaPath, outputDir, Collections.singletonList(root), root, schemaVersion);
            }
        }
    }

    private void extractSubset(
            String mainSchemaPath,
            File outputDir,
            List<String> rootElements,
            String rootElement,
            String schemaVersion
    ) throws Exception {
        // 3. Trace dependencies
        Set<ComponentRef> needed = new LinkedHashSet<>();
        Queue<ComponentRef> queue = new ArrayDeque<>();

        for (String rootName : rootElements) {
            boolean found = false;
            for (Map.Entry<ComponentRef, ComponentInfo> entry : registry.entrySet()) {
                ComponentRef ref = entry.getKey();
                if (ref.type() == ComponentType.ELEMENT && ref.qName().getLocalPart().equals(rootName)) {
                    queue.add(ref);
                    needed.add(ref);
                    found = true;
                }
            }
            if (!found) {
                System.out.println("Warning: Root element '" + rootName + "' not found in schema registry.");
            }
        }

        while (!queue.isEmpty()) {
            ComponentRef current = queue.poll();
            ComponentInfo info = registry.get(current);
            if (info != null) {
                findReferences(info.element(), ref -> {
                    if (registry.containsKey(ref) && !needed.contains(ref)) {
                        needed.add(ref);
                        queue.add(ref);
                    }
                });

                // If this is an abstract type (PET), also pull in every type that directly
                // extends or restricts it. Those subtypes are themselves processed by the BFS,
                // so nested abstract PETs are handled automatically.
                if ("true".equals(info.element().getAttribute("abstract"))) {
                    Set<ComponentRef> subs = subtypeMap.getOrDefault(current, Collections.emptySet());
                    for (ComponentRef sub : subs) {
                        if (registry.containsKey(sub) && !needed.contains(sub)) {
                            needed.add(sub);
                            queue.add(sub);
                        }
                    }
                }
            }
        }

        // 4. Determine which files we need to write
        Set<String> filesToGenerate = new HashSet<>();
        for (ComponentRef ref : needed) {
            ComponentInfo info = registry.get(ref);
            if (info != null) {
                filesToGenerate.add(info.systemId());
            }
        }

        // Always generate the main schema, even if no components are selected
        String mainAbsoluteId = schemaResolver.getAbsoluteSystemId(null, mainSchemaPath);
        if (mainAbsoluteId != null) {
            filesToGenerate.add(mainAbsoluteId);
        }

        // Identify the namespace of the root element in split mode
        String rootNamespace = null;
        if (rootElement != null) {
            for (ComponentRef ref : needed) {
                if (ref.qName().getLocalPart().equals(rootElement)) {
                    rootNamespace = ref.qName().getNamespaceURI();
                    break;
                }
            }
        }

        // 5. Generate subset documents and serialize
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir);
        }

        if (rootElement != null) {
            // Split mode: write output using text-based extraction to preserve original attribute
            // order and XML character references (e.g. &#x20;) exactly as in the source files.
            String rootSchemaSystemId = mainAbsoluteId;
            if (rootNamespace != null) {
                for (Map.Entry<String, Document> e : parsedSchemas.entrySet()) {
                    String tns = e.getValue().getDocumentElement().getAttribute("targetNamespace");
                    if (rootNamespace.equals(tns)) {
                        rootSchemaSystemId = e.getKey();
                        break;
                    }
                }
            }

            String outFilename = getOutputFilename(mainAbsoluteId, mainAbsoluteId, rootElement, schemaVersion);
            File outFile = new File(outputDir, outFilename);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

                String rootSourceContent = sourceContents.get(rootSchemaSystemId);
                writer.write(buildSchemaRootTagText(rootSourceContent, rootNamespace));
                writer.write("\n");

                for (ComponentRef ref : needed) {
                    ComponentInfo info = registry.get(ref);
                    if (info == null || info.element() == null) continue;

                    String sourceContent = sourceContents.get(info.systemId());
                    if (sourceContent == null) continue;

                    Element el = info.element();
                    String prefix = el.getPrefix() != null ? el.getPrefix() : "xs";
                    String xsTagName = prefix + ":" + el.getLocalName();
                    String nameValue = el.getAttribute("name");

                    String elementText = extractTopLevelElementText(sourceContent, xsTagName, nameValue);
                    if (elementText != null) {
                        writer.write(elementText);
                        writer.write("\n");
                    } else {
                        System.err.println("Warning: Could not extract text for <" + xsTagName + " name=\"" + nameValue + "\">");
                    }
                }

                writer.write("</xs:schema>\n");
            }
            return;
        }

        // Merged mode: DOM-based output
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        for (String systemId : filesToGenerate) {
            Document origDoc = parsedSchemas.get(systemId);
            if (origDoc == null) continue;

            Document outDoc = builder.newDocument();
            Element origRoot = origDoc.getDocumentElement();
            Element newRoot = (Element) outDoc.importNode(origRoot, false);
            outDoc.appendChild(newRoot);

            // Copy annotations and comments
            for (Node child = origRoot.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) child;
                    if ("annotation".equals(el.getLocalName())) {
                        newRoot.appendChild(outDoc.importNode(el, true));
                    }
                } else if (child.getNodeType() == Node.COMMENT_NODE) {
                    newRoot.appendChild(outDoc.importNode(child, true));
                }
            }

            // Copy relevant imports and includes
            for (Node child = origRoot.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) child;
                String tagName = el.getLocalName();
                if (!"include".equals(tagName) && !"import".equals(tagName)) continue;

                String schemaLoc = el.getAttribute("schemaLocation");
                if (!schemaLoc.isEmpty()) {
                    String targetSystemId = schemaResolver.getAbsoluteSystemId(systemId, schemaLoc);
                    if (targetSystemId != null && filesToGenerate.contains(targetSystemId)) {
                        Element newImport = (Element) outDoc.importNode(el, false);
                        String targetFilename = getOutputFilename(targetSystemId, mainAbsoluteId, null, schemaVersion);
                        newImport.setAttribute("schemaLocation", targetFilename);
                        newRoot.appendChild(newImport);
                    }
                } else {
                    newRoot.appendChild(outDoc.importNode(el, true));
                }
            }

            // Copy needed components belonging to this file
            for (Node child = origRoot.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) child;
                String name = el.getAttribute("name");
                if (name.isEmpty()) continue;

                String tagName = el.getLocalName();
                ComponentType type = null;
                if ("element".equals(tagName)) {
                    type = ComponentType.ELEMENT;
                } else if ("complexType".equals(tagName) || "simpleType".equals(tagName)) {
                    type = ComponentType.TYPE;
                } else if ("group".equals(tagName)) {
                    type = ComponentType.GROUP;
                } else if ("attributeGroup".equals(tagName)) {
                    type = ComponentType.ATTRIBUTE_GROUP;
                } else if ("attribute".equals(tagName)) {
                    type = ComponentType.ATTRIBUTE;
                }

                if (type != null) {
                    String tns = origRoot.getAttribute("targetNamespace");
                    QName qName = new QName(tns != null ? tns : "", name);
                    ComponentRef ref = new ComponentRef(type, qName);
                    if (needed.contains(ref)) {
                        newRoot.appendChild(outDoc.importNode(el, true));
                        newRoot.appendChild(outDoc.createTextNode("\n"));
                    }
                }
            }

            String sourceContent = sourceContents.get(systemId);
            String outFilename = getOutputFilename(systemId, mainAbsoluteId, null, schemaVersion);
            File outFile = new File(outputDir, outFilename);
            serializeDocument(outDoc, outFile, sourceContent);
        }
    }

    private void parseSchemaRecursive(String parentSystemId, String schemaLocation) throws Exception {
        String absoluteSystemId = schemaResolver.getAbsoluteSystemId(parentSystemId, schemaLocation);
        if (absoluteSystemId == null || parsedSchemas.containsKey(absoluteSystemId)) {
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        byte[] contentBytes;
        try (InputStream is = schemaResolver.resolve(parentSystemId, schemaLocation)) {
            if (is == null) {
                System.err.println("Warning: Could not resolve schema: " + schemaLocation + " from parent: " + parentSystemId);
                return;
            }
            contentBytes = is.readAllBytes();
        }

        sourceContents.put(absoluteSystemId, new String(contentBytes, StandardCharsets.UTF_8));

        Document doc;
        try (InputStream is = new ByteArrayInputStream(contentBytes)) {
            doc = builder.parse(is);
        }

        parsedSchemas.put(absoluteSystemId, doc);

        // Scan for imports and includes
        Element root = doc.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                String tagName = el.getLocalName();
                if ("include".equals(tagName) || "import".equals(tagName)) {
                    String loc = el.getAttribute("schemaLocation");
                    if (!loc.isEmpty()) {
                        parseSchemaRecursive(absoluteSystemId, loc);
                    }
                }
            }
        }
    }

    private void registerComponents(Document doc, String systemId) {
        Element root = doc.getDocumentElement();
        String tns = root.getAttribute("targetNamespace");

        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                String name = el.getAttribute("name");
                if (name.isEmpty()) {
                    continue;
                }

                ComponentType type = null;
                String tagName = el.getLocalName();
                if ("element".equals(tagName)) {
                    type = ComponentType.ELEMENT;
                } else if ("complexType".equals(tagName) || "simpleType".equals(tagName)) {
                    type = ComponentType.TYPE;
                } else if ("group".equals(tagName)) {
                    type = ComponentType.GROUP;
                } else if ("attributeGroup".equals(tagName)) {
                    type = ComponentType.ATTRIBUTE_GROUP;
                } else if ("attribute".equals(tagName)) {
                    type = ComponentType.ATTRIBUTE;
                }

                if (type != null) {
                    QName qName = new QName(tns != null ? tns : "", name);
                    ComponentRef ref = new ComponentRef(type, qName);
                    registry.put(ref, new ComponentInfo(el, systemId));
                }
            }
        }
    }

    private void findReferences(Node node, Consumer<ComponentRef> callback) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;

            checkAttributeRef(el, "type", ComponentType.TYPE, callback);
            checkAttributeRef(el, "base", ComponentType.TYPE, callback);
            checkAttributeRef(el, "itemType", ComponentType.TYPE, callback);

            String memberTypes = el.getAttribute("memberTypes");
            if (!memberTypes.isEmpty()) {
                for (String part : memberTypes.split("\\s+")) {
                    if (!part.isEmpty()) {
                        resolveAndAdd(el, part, ComponentType.TYPE, callback);
                    }
                }
            }

            String ref = el.getAttribute("ref");
            if (!ref.isEmpty()) {
                String tagName = el.getLocalName();
                if ("element".equals(tagName)) {
                    resolveAndAdd(el, ref, ComponentType.ELEMENT, callback);
                } else if ("group".equals(tagName)) {
                    resolveAndAdd(el, ref, ComponentType.GROUP, callback);
                } else if ("attribute".equals(tagName)) {
                    resolveAndAdd(el, ref, ComponentType.ATTRIBUTE, callback);
                } else if ("attributeGroup".equals(tagName)) {
                    resolveAndAdd(el, ref, ComponentType.ATTRIBUTE_GROUP, callback);
                }
            }

            checkAttributeRef(el, "substitutionGroup", ComponentType.ELEMENT, callback);
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            findReferences(child, callback);
        }
    }

    /**
     * Populates {@link #subtypeMap} by scanning every registered TYPE for xs:extension/xs:restriction
     * declarations, then recording that the extending type is a subtype of the base type.
     */
    private void buildSubtypeMap() {
        for (Map.Entry<ComponentRef, ComponentInfo> entry : registry.entrySet()) {
            ComponentRef ref = entry.getKey();
            if (ref.type() != ComponentType.TYPE) continue;
            ComponentInfo info = entry.getValue();
            if (info.element() == null) continue;

            findBaseTypeRefs(info.element(), baseRef -> {
                subtypeMap.computeIfAbsent(baseRef, k -> new HashSet<>()).add(ref);
            });
        }
    }

    /** Traverses a node tree looking only at xs:extension/xs:restriction base attributes. */
    private void findBaseTypeRefs(Node node, Consumer<ComponentRef> callback) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;
            String localName = el.getLocalName();
            if ("extension".equals(localName) || "restriction".equals(localName)) {
                checkAttributeRef(el, "base", ComponentType.TYPE, callback);
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            findBaseTypeRefs(child, callback);
        }
    }

    private void checkAttributeRef(Element el, String attrName, ComponentType targetType, Consumer<ComponentRef> callback) {
        String val = el.getAttribute(attrName);
        if (!val.isEmpty()) {
            resolveAndAdd(el, val, targetType, callback);
        }
    }

    private void resolveAndAdd(Element context, String prefixedName, ComponentType targetType, Consumer<ComponentRef> callback) {
        int colon = prefixedName.indexOf(':');
        String prefix = colon >= 0 ? prefixedName.substring(0, colon) : "";
        String localName = colon >= 0 ? prefixedName.substring(colon + 1) : prefixedName;

        String ns = context.lookupNamespaceURI(prefix.isEmpty() ? null : prefix);
        if (ns == null) {
            if (prefix.isEmpty()) {
                ns = getTargetNamespace(context);
            }
        }

        if ("http://www.w3.org/2001/XMLSchema".equals(ns)) {
            return;
        }

        QName qName = new QName(ns != null ? ns : "", localName);
        callback.accept(new ComponentRef(targetType, qName));
    }

    private String getTargetNamespace(Element el) {
        Node current = el;
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                Element currentEl = (Element) current;
                if ("schema".equals(currentEl.getLocalName())) {
                    return currentEl.getAttribute("targetNamespace");
                }
            }
            current = current.getParentNode();
        }
        return "";
    }

    /**
     * Builds the opening <xs:schema ...> tag text from the raw source file content, placing xmlns:xs
     * before other namespace declarations and overriding targetNamespace / xmlns:uci with rootNamespace.
     * Parses the source tag text directly to preserve original attribute order for non-namespace attributes.
     */
    private String buildSchemaRootTagText(String sourceContent, String rootNamespace) {
        // Find the <xs:schema line in the source (usually line 2)
        String schemaLine = null;
        if (sourceContent != null) {
            for (String line : sourceContent.split("\n", -1)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<xs:schema")) {
                    schemaLine = trimmed;
                    break;
                }
            }
        }
        if (schemaLine == null) return "<xs:schema>";

        List<String[]> attrs = parseTagAttributes(schemaLine);

        StringBuilder xsNs = new StringBuilder();
        StringBuilder otherNs = new StringBuilder();
        StringBuilder nonNs = new StringBuilder();

        for (String[] attr : attrs) {
            String name = attr[0];
            String value = attr[1];
            if ("xmlns:xs".equals(name)) {
                xsNs.append(" ").append(name).append("=\"").append(value).append("\"");
            } else if (name.startsWith("xmlns:") || "xmlns".equals(name)) {
                if (rootNamespace != null && ("xmlns:uci".equals(name) || "xmlns".equals(name))) {
                    value = rootNamespace;
                }
                otherNs.append(" ").append(name).append("=\"").append(value).append("\"");
            } else {
                if (rootNamespace != null && "targetNamespace".equals(name)) {
                    value = rootNamespace;
                }
                nonNs.append(" ").append(name).append("=\"").append(value).append("\"");
            }
        }

        return "<xs:schema" + xsNs + otherNs + nonNs + ">";
    }

    /** Parses attribute name=value pairs from a raw XML start tag string, preserving source order. */
    private List<String[]> parseTagAttributes(String tagText) {
        List<String[]> result = new ArrayList<>();
        int i = 0;
        // Skip to after the tag name
        while (i < tagText.length() && tagText.charAt(i) != ' ' && tagText.charAt(i) != '>' && tagText.charAt(i) != '/') i++;

        while (i < tagText.length()) {
            while (i < tagText.length() && Character.isWhitespace(tagText.charAt(i))) i++;
            if (i >= tagText.length() || tagText.charAt(i) == '>' || tagText.charAt(i) == '/') break;

            int nameStart = i;
            while (i < tagText.length() && tagText.charAt(i) != '=' && !Character.isWhitespace(tagText.charAt(i))) i++;
            String name = tagText.substring(nameStart, i);

            while (i < tagText.length() && (Character.isWhitespace(tagText.charAt(i)) || tagText.charAt(i) == '=')) i++;

            if (i < tagText.length() && tagText.charAt(i) == '"') {
                i++;
                int valueStart = i;
                while (i < tagText.length() && tagText.charAt(i) != '"') i++;
                result.add(new String[]{name, tagText.substring(valueStart, i)});
                if (i < tagText.length()) i++;
            }
        }
        return result;
    }

    /**
     * Extracts the raw source text of a top-level XSD element (starting at one-tab indentation)
     * identified by its qualified tag name (e.g. "xs:element") and name attribute value.
     * Returns the text exactly as it appears in the source file, preserving attribute order,
     * XML character references, and unescaped characters.
     */
    private String extractTopLevelElementText(String sourceContent, String xsTagName, String nameValue) {
        String[] lines = sourceContent.split("\n", -1);
        String startPrefix = "\t<" + xsTagName;
        String endTag = "\t</" + xsTagName + ">";
        String nameAttr = "name=\"" + nameValue + "\"";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith(startPrefix)) continue;
            if (!line.contains(nameAttr)) continue;

            // Self-closing element
            if (line.stripTrailing().endsWith("/>")) {
                return line;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(line);
            i++;

            while (i < lines.length) {
                String next = lines[i];
                sb.append("\n").append(next);
                // Closing tag at one-tab indent signals end of this top-level element
                if (next.startsWith("\t</") && next.trim().equals("</" + xsTagName + ">")) {
                    break;
                }
                i++;
            }

            return sb.toString();
        }
        return null;
    }

    /**
     * Serializes a DOM Document to a file using a custom writer that preserves DOM attribute
     * order and does not escape '>' in text content.
     */
    private void serializeDocument(Document doc, File outFile, String sourceContent) throws Exception {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            serializeNode(doc.getDocumentElement(), writer);
            writer.write("\n");
        }
    }

    private void serializeNode(Node node, Writer writer) throws IOException {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE -> {
                Element el = (Element) node;
                writer.write("<");
                writer.write(el.getNodeName());
                NamedNodeMap attrs = el.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    Node attr = attrs.item(i);
                    writer.write(" ");
                    writer.write(attr.getNodeName());
                    writer.write("=\"");
                    writer.write(escapeAttrValue(attr.getNodeValue()));
                    writer.write("\"");
                }
                boolean hasChildren = el.hasChildNodes();
                if (!hasChildren) {
                    writer.write("/>");
                } else {
                    writer.write(">");
                    for (Node child = el.getFirstChild(); child != null; child = child.getNextSibling()) {
                        serializeNode(child, writer);
                    }
                    writer.write("</");
                    writer.write(el.getNodeName());
                    writer.write(">");
                }
            }
            case Node.TEXT_NODE -> writer.write(escapeText(node.getNodeValue()));
            case Node.COMMENT_NODE -> {
                writer.write("<!--");
                writer.write(node.getNodeValue());
                writer.write("-->");
            }
            case Node.CDATA_SECTION_NODE -> {
                writer.write("<![CDATA[");
                writer.write(node.getNodeValue());
                writer.write("]]>");
            }
            case Node.PROCESSING_INSTRUCTION_NODE -> {
                writer.write("<?");
                writer.write(node.getNodeName());
                String data = node.getNodeValue();
                if (data != null && !data.isEmpty()) {
                    writer.write(" ");
                    writer.write(data);
                }
                writer.write("?>");
            }
        }
    }

    private String escapeAttrValue(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    private String escapeText(String text) {
        // Only < and & are mandatory escapes in text content; > is allowed as-is
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }
}
