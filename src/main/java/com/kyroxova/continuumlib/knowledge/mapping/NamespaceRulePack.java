package com.kyroxova.continuumlib.knowledge.mapping;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.symbol.*;
import java.util.*;

/** Converts complete selected aliases into a same-version namespace remapping pack.
 * Mapping names alone must never be presented as cross-version semantic migrations.
 */
public final class NamespaceRulePack {
    private NamespaceRulePack() {}
    public static RulePack create(String id, MappingProvenance provenance, EnvironmentId source, EnvironmentId target,
                                  Map<String, String> sourceArtifacts, Map<String, String> targetArtifacts,
                                  Collection<SymbolName> symbols) {
        if (!source.minecraftVersion().equals(target.minecraftVersion()) || source.loader() != target.loader()
                || source.javaVersion() != target.javaVersion() || source.mappings() == target.mappings())
            throw new IllegalArgumentException("Namespace remapping requires one version, loader and Java runtime and distinct namespaces");
        Map<String, String> classes = new LinkedHashMap<>();
        Map<MemberReference, MemberReference> members = new LinkedHashMap<>();
        for (SymbolName symbol : symbols) {
            SymbolKey from = alias(symbol, source), to = alias(symbol, target);
            if (from == null) continue;
            if (to == null) throw new IllegalArgumentException("Missing target namespace alias: " + symbol.stableId());
            if (from.kind() != to.kind()) throw new IllegalArgumentException("Alias kind mismatch: " + symbol.stableId());
            if (from.kind() == SymbolKind.CLASS) unique(classes, from.owner(), to.owner());
            else if (from.kind() == SymbolKind.CONSTRUCTOR || from.name().equals("<clinit>")) {
                if (!from.name().equals(to.name())) throw new IllegalArgumentException("Initializers cannot be renamed");
            } else unique(members, new MemberReference(from.owner(), from.name(), from.descriptor()),
                    new MemberReference(to.owner(), to.name(), to.descriptor()));
        }
        if (classes.isEmpty()) throw new IllegalArgumentException("No class aliases for the selected environment pair");
        return new RulePack(id, provenance.sourceName() + "; " + provenance.source() + "; SHA-256=" + provenance.checksum()
                + "; license=" + provenance.license() + "; namespace renames only", source, target, sourceArtifacts, targetArtifacts, classes, members, List.of());
    }
    private static SymbolKey alias(SymbolName symbol, EnvironmentId environment) {
        var matches = symbol.aliases().stream().filter(key -> key.environment().equals(environment) && key.namespace() == environment.mappings()).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("Ambiguous namespace alias: " + symbol.stableId());
        return matches.isEmpty() ? null : matches.get(0);
    }
    private static <K,V> void unique(Map<K,V> values, K key, V value) {
        V previous = values.putIfAbsent(key, value);
        if (previous != null && !previous.equals(value)) throw new IllegalArgumentException("Conflicting namespace mapping: " + key);
    }
}
