package com.kyroxova.continuumlib.knowledge.rule;

import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.model.environment.*;
import org.objectweb.asm.Opcodes;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

/** Strict, offline XML rule reader. Caller owns the input stream. No includes or external entities. */
public final class RulePackReader {
    private record Environment(EnvironmentId id, Map<String, String> artifacts) {}
    public RulePack read(InputStream input) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var parser = factory.newDocumentBuilder();
            parser.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            Element root = parser.parse(input).getDocumentElement();
            if (!root.getTagName().equals("rules")) throw new IllegalArgumentException("Expected rules root");
            attributes(root, "schema", "id", "evidence");
            if (!value(root, "schema").equals("1")) throw new IllegalArgumentException("Unsupported rule schema");
            Environment source = null, target = null;
            Map<String, String> classes = new LinkedHashMap<>();
            Map<MemberReference, MemberReference> members = new LinkedHashMap<>();
            List<CallBridge.Rule> bridges = new ArrayList<>();
            List<ConstructorFactory.Rule> constructors = new ArrayList<>();
            for (Element node : children(root)) {
                switch (node.getTagName()) {
                    case "source" -> {
                        if (source != null) throw new IllegalArgumentException("Duplicate source");
                        source = environment(node);
                    }
                    case "target" -> {
                        if (target != null) throw new IllegalArgumentException("Duplicate target");
                        target = environment(node);
                    }
                    case "class" -> {
                        attributes(node, "from", "to"); leaf(node);
                        unique(classes, value(node, "from"), value(node, "to"));
                    }
                    case "member", "bridge", "constructor-factory" -> {
                        boolean bridge = node.getTagName().equals("bridge");
                        attributes(node, bridge ? Set.of("opcode", "from-owner", "from-name", "from-descriptor", "to-owner", "to-name", "to-descriptor")
                                : Set.of("from-owner", "from-name", "from-descriptor", "to-owner", "to-name", "to-descriptor"));
                        leaf(node);
                        MemberReference from = member(node, "from"), to = member(node, "to");
                        if (bridge) {
                            int opcode = switch (value(node, "opcode")) {
                                case "INVOKESTATIC" -> Opcodes.INVOKESTATIC;
                                case "INVOKEVIRTUAL" -> Opcodes.INVOKEVIRTUAL;
                                case "INVOKEINTERFACE" -> Opcodes.INVOKEINTERFACE;
                                case "GETFIELD" -> Opcodes.GETFIELD;
                                case "PUTFIELD" -> Opcodes.PUTFIELD;
                                case "GETSTATIC" -> Opcodes.GETSTATIC;
                                case "PUTSTATIC" -> Opcodes.PUTSTATIC;
                                default -> throw new IllegalArgumentException("Unsupported bridge opcode");
                            };
                            bridges.add(new CallBridge.Rule(from, opcode, to));
                        } else if (node.getTagName().equals("constructor-factory")) constructors.add(new ConstructorFactory.Rule(from, to));
                        else unique(members, from, to);
                    }
                    default -> throw new IllegalArgumentException("Unknown rule element: " + node.getTagName());
                }
            }
            if (source == null || target == null) throw new IllegalArgumentException("Source and target are required");
            new CallBridge(bridges); // Reject duplicate bridge keys in this file.
            new ConstructorFactory(constructors);
            return new RulePack(value(root, "id"), value(root, "evidence"), source.id(), target.id(),
                    source.artifacts(), target.artifacts(), classes, members, bridges, constructors);
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException e) {
            throw new IOException("Invalid ContinuumLib rule pack: " + e.getMessage(), e);
        }
    }
    private static Environment environment(Element node) {
        attributes(node, "minecraft", "loader", "namespace", "java");
        var id = new EnvironmentId(value(node, "minecraft"), Loader.valueOf(value(node, "loader")),
                MappingNamespace.valueOf(value(node, "namespace")), Integer.parseInt(value(node, "java")));
        Map<String, String> artifacts = new LinkedHashMap<>();
        for (Element artifact : children(node)) {
            if (!artifact.getTagName().equals("artifact")) throw new IllegalArgumentException("Expected artifact");
            attributes(artifact, "name", "sha256"); leaf(artifact);
            unique(artifacts, value(artifact, "name"), value(artifact, "sha256"));
        }
        return new Environment(id, artifacts);
    }
    private static MemberReference member(Element node, String prefix) {
        return new MemberReference(value(node, prefix + "-owner"), value(node, prefix + "-name"), value(node, prefix + "-descriptor"));
    }
    private static <K,V> void unique(Map<K,V> into, K key, V value) {
        if (into.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate rule key: " + key);
    }
    private static String value(Element node, String attribute) {
        String value = node.getAttribute(attribute);
        if (value.isBlank()) throw new IllegalArgumentException("Missing " + attribute + " on " + node.getTagName());
        return value;
    }
    private static void attributes(Element node, String... allowed) { attributes(node, Set.of(allowed)); }
    private static void attributes(Element node, Set<String> allowed) {
        var attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++)
            if (!allowed.contains(attributes.item(i).getNodeName()))
                throw new IllegalArgumentException("Unknown attribute: " + attributes.item(i).getNodeName());
    }
    private static List<Element> children(Element node) {
        var elements = new ArrayList<Element>();
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) elements.add(element);
            else if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !child.getTextContent().isBlank()) throw new IllegalArgumentException("Unexpected text in " + node.getTagName());
        }
        return elements;
    }
    private static void leaf(Element node) {
        if (!children(node).isEmpty()) throw new IllegalArgumentException("Unexpected child in " + node.getTagName());
    }
}
