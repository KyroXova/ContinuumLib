package com.kyroxova.continuumlib.knowledge.mapping;

import com.kyroxova.continuumlib.api.config.MappingRequest;
import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.symbol.SymbolKind;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Normalizes API declarations only. Does not rewrite/distribute Minecraft classes or infer migrations. */
public final class DeclarationNamespace {
    private DeclarationNamespace() {}
    public static Map<String, ClassInfo> remap(Map<String, ClassInfo> input, MappingRequest request, EnvironmentId environment) throws IOException {
        if (request == null) return input;
        if (request.from() == environment.mappings() || !request.namespaces().containsValue(environment.mappings()))
            throw new IOException("Declaration mappings require distinct input and rule namespaces, both explicitly bound");
        var provenance = new MappingProvenance(request.file().getFileName().toString(), request.file().toUri(), request.sha256(), request.license());
        Map<String, String> classes = new HashMap<>();
        Map<MemberReference, String> members = new HashMap<>();
        try (var stream = Files.newInputStream(request.file())) {
            var symbols = new MappingIoImporter(request.namespaces(), request.from(), input).importMappings(stream, environment, provenance);
            for (var symbol : symbols) {
                var from = symbol.aliases().stream().filter(s -> s.namespace() == request.from()).findFirst().orElse(null);
                if (from == null) continue;
                var to = symbol.aliases().stream().filter(s -> s.namespace() == environment.mappings()).findFirst()
                        .orElseThrow(() -> new IOException("Missing target declaration alias: " + symbol.stableId()));
                if (from.kind() == SymbolKind.CLASS) classes.put(from.owner(), to.owner());
                else if (!from.name().startsWith("<")) members.put(new MemberReference(from.owner(), from.name(), from.descriptor()), to.name());
            }
        }
        if (classes.isEmpty()) throw new IOException("No class aliases for requested declaration namespaces");
        var types = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return classes.getOrDefault(name, name); }
        };
        Map<String, ClassInfo> result = new TreeMap<>();
        for (var type : input.values()) {
            var fields = type.fields().stream().map(m -> member(type.name(), m, false, members, types)).toList();
            var methods = type.methods().stream().map(m -> member(type.name(), m, true, members, types)).toList();
            var mapped = new ClassInfo(types.mapType(type.name()), type.superName() == null ? null : types.mapType(type.superName()),
                    type.interfaces().stream().map(types::mapType).toList(), type.access(), fields, methods);
            if (result.putIfAbsent(mapped.name(), mapped) != null) throw new IOException("Mapping creates duplicate API class: " + mapped.name());
            checkDuplicates(fields, mapped.name()); checkDuplicates(methods, mapped.name());
        }
        return Map.copyOf(result);
    }
    private static ClassInfo.Member member(String owner, ClassInfo.Member member, boolean method,
                                           Map<MemberReference, String> members, Remapper types) {
        return new ClassInfo.Member(members.getOrDefault(new MemberReference(owner, member.name(), member.descriptor()), member.name()),
                method ? types.mapMethodDesc(member.descriptor()) : types.mapDesc(member.descriptor()), member.access(),
                member.signature() == null ? null : types.mapSignature(member.signature(), !method));
    }
    private static void checkDuplicates(List<ClassInfo.Member> members, String owner) throws IOException {
        var seen = new HashSet<MemberReference>();
        for (var member : members) if (!seen.add(new MemberReference(owner, member.name(), member.descriptor())))
            throw new IOException("Mapping creates duplicate API member: " + owner + "." + member.name() + member.descriptor());
    }
}
