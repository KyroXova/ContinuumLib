package com.kyroxova.continuumlib.knowledge.mapping;

import com.kyroxova.continuumlib.model.environment.*;
import com.kyroxova.continuumlib.model.symbol.*;
import com.kyroxova.continuumlib.knowledge.symbol.InMemorySymbolDatabase;
import com.kyroxova.continuumlib.bytecode.ClassInfo;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Artifact-scoped aliases from Mapping-IO supported text formats. Namespace labels are
 * supplied explicitly: a file's 'official' or 'named' label does not identify Mojmap by itself.
 * Missing member descriptors are rejected rather than conflating overloads.
 */
public final class MappingIoImporter implements MappingImporter {
    private final Map<String, MappingNamespace> namespaces;
    private final MappingNamespace declarationNamespace;
    private final Map<String, ClassInfo> declarations;
    public MappingIoImporter(Map<String, MappingNamespace> namespaces) {
        this(namespaces, null, null);
    }
    /** Descriptorless fields are scoped to actual supplied declarations; absent-side fields are omitted. */
    public MappingIoImporter(Map<String, MappingNamespace> namespaces, MappingNamespace declarationNamespace, Map<String, ClassInfo> declarations) {
        if (namespaces.isEmpty()) throw new IllegalArgumentException("At least one explicit namespace binding is required");
        this.namespaces = Map.copyOf(namespaces);
        if (new HashSet<>(namespaces.values()).size() != namespaces.size())
            throw new IllegalArgumentException("Each file namespace must have a distinct environment namespace");
        if ((declarationNamespace == null) != (declarations == null)
                || (declarationNamespace != null && !namespaces.containsValue(declarationNamespace)))
            throw new IllegalArgumentException("Declaration namespace must be explicitly bound with its artifact declarations");
        this.declarationNamespace = declarationNamespace;
        this.declarations = declarations == null ? null : Map.copyOf(declarations);
    }
    @Override public Collection<SymbolName> importMappings(InputStream input, EnvironmentId environment, MappingProvenance provenance) throws IOException {
        Objects.requireNonNull(environment); Objects.requireNonNull(provenance);
        if (!provenance.checksum().matches("[0-9a-fA-F]{64}") || provenance.sourceName().isBlank() || provenance.license().isBlank())
            throw new IOException("Mapping provenance requires a SHA-256 checksum, source name and license declaration");
        byte[] bytes = input.readAllBytes();
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        if (!digest.equalsIgnoreCase(provenance.checksum())) throw new IOException("Mapping SHA-256 mismatch");
        var tree = new MemoryMappingTree();
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        MappingReader.read(new StringReader(text), tree);
        Map<Integer, MappingNamespace> selected = new TreeMap<>();
        for (var binding : namespaces.entrySet()) {
            int id = tree.getNamespaceId(binding.getKey());
            if (id == MappingTreeView.NULL_NAMESPACE_ID) throw new IOException("Mapping namespace not present: " + binding.getKey());
            selected.put(id, binding.getValue());
        }
        String prefix = environment.minecraftVersion() + ":" + environment.loader() + ":" + environment.javaVersion() + ":" + digest;
        var symbols = new ArrayList<SymbolName>();
        for (var type : tree.getClasses()) {
            var aliases = new ArrayList<SymbolKey>();
            for (var ns : selected.entrySet()) {
                String name = type.getName(ns.getKey());
                if (name != null && !name.isEmpty()) aliases.add(key(environment, ns.getValue(), name, SymbolKind.CLASS, name, "L" + name + ";"));
            }
            add(symbols, prefix + ":class:" + type.getSrcName(), aliases);
            for (var field : new ArrayList<>(type.getFields())) {
                if (field.getSrcDesc() == null && declarations != null) {
                    int namespace = selected.entrySet().stream().filter(e -> e.getValue() == declarationNamespace).findFirst().orElseThrow().getKey();
                    String owner = type.getName(namespace), name = field.getName(namespace);
                    var actualOwner = declarations.get(owner);
                    var actualFields = actualOwner == null ? List.<ClassInfo.Member>of() : actualOwner.fields().stream().filter(f -> f.name().equals(name)).toList();
                    if (actualFields.size() > 1) throw new IOException("Ambiguous descriptorless field: " + owner + "." + name);
                    if (actualFields.isEmpty()) continue; // Not declared in this supplied client/server artifact scope.
                    field.setSrcDesc(tree.mapDesc(actualFields.get(0).descriptor(), namespace, MappingTreeView.SRC_NAMESPACE_ID));
                }
                member(symbols, environment, selected, prefix, field, SymbolKind.FIELD);
            }
            for (var method : type.getMethods()) member(symbols, environment, selected, prefix, method,
                    method.getSrcName().equals("<init>") ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD);
        }
        try { new InMemorySymbolDatabase(symbols); }
        catch (IllegalArgumentException e) { throw new IOException("Conflicting mapping aliases", e); }
        return List.copyOf(symbols);
    }
    private static void member(List<SymbolName> symbols, EnvironmentId environment, Map<Integer, MappingNamespace> selected,
                               String prefix, MappingTreeView.MemberMappingView member, SymbolKind kind) throws IOException {
        if (member.getSrcDesc() == null || member.getSrcDesc().isEmpty())
            throw new IOException("Mapping member lacks a descriptor; enrich it from API artifacts first: " + member.getOwner().getSrcName() + "." + member.getSrcName());
        var aliases = new ArrayList<SymbolKey>();
        for (var ns : selected.entrySet()) {
            String owner = member.getOwner().getName(ns.getKey()), name = member.getName(ns.getKey());
            if ((name == null || name.isEmpty()) && member.getSrcName().startsWith("<")) name = member.getSrcName();
            if (owner != null && !owner.isEmpty() && name != null && !name.isEmpty())
                aliases.add(key(environment, ns.getValue(), owner, kind, name, member.getDesc(ns.getKey())));
        }
        add(symbols, prefix + ":" + kind + ":" + member.getOwner().getSrcName() + ":" + member.getSrcName() + ":" + member.getSrcDesc(), aliases);
    }
    private static SymbolKey key(EnvironmentId base, MappingNamespace namespace, String owner, SymbolKind kind, String name, String descriptor) {
        var environment = new EnvironmentId(base.minecraftVersion(), base.loader(), namespace, base.javaVersion());
        return new SymbolKey(environment, owner, kind, name, descriptor, namespace);
    }
    private static void add(List<SymbolName> symbols, String id, List<SymbolKey> aliases) {
        if (!aliases.isEmpty()) symbols.add(new SymbolName(id, aliases));
    }
}
