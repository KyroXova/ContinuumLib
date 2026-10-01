package com.kyroxova.continuumlib.bytecode;

import org.objectweb.asm.Opcodes;
import java.util.*;

/** Conservative propagation of exact rename rules through a supplied source hierarchy. */
final class HierarchyMemberNames {
    private final Map<String, ClassInfo> classes;
    private final Map<MemberReference, MemberReference> rules;
    private record Signature(String name, String descriptor) {}
    private final Set<Signature> changedNames;
    private final Set<Signature> platformNames;
    HierarchyMemberNames(Map<String, ClassInfo> classes, Map<MemberReference, MemberReference> rules) {
        this.classes = Map.copyOf(classes); this.rules = Map.copyOf(rules);
        Set<Signature> names = new HashSet<>();
        rules.forEach((from, to) -> { if (!from.name().equals(to.name())) names.add(new Signature(from.name(), from.descriptor())); });
        changedNames = Set.copyOf(names);
        Set<Signature> platform = new HashSet<>();
        rules.forEach((from, to) -> { if (from.owner().startsWith("java/")) platform.add(new Signature(from.name(), from.descriptor())); });
        platformNames = Set.copyOf(platform);
    }
    String name(MemberReference reference) {
        if (reference.name().startsWith("<") || !changedNames.contains(new Signature(reference.name(), reference.descriptor()))) return reference.name();
        if (unmappedPlatform(reference.owner(), reference)) return reference.name();
        if (!reference.descriptor().startsWith("(")) {
            var found = new MemberLookup(classes).find(reference);
            if (found.status() != MemberLookup.Status.FOUND)
                throw new IllegalArgumentException("Cannot resolve potentially renamed field: " + reference + " (" + found.status() + ")");
            var rule = rules.get(new MemberReference(found.declaringOwner(), reference.name(), reference.descriptor()));
            return rule == null ? reference.name() : rule.name();
        }
        ClassInfo root = required(reference.owner());
        boolean declared = root.methods().stream().anyMatch(m -> matches(m, reference));
        var names = methodNames(reference.owner(), reference, declared, new HashSet<>());
        if (names.size() > 1) throw new IllegalArgumentException("Conflicting inherited method renames for " + reference + ": " + names);
        return names.isEmpty() ? reference.name() : names.iterator().next();
    }
    private Set<String> methodNames(String owner, MemberReference ref, boolean rootDeclares, Set<String> path) {
        if (unmappedPlatform(owner, ref)) return Set.of();
        // Object has no superclass. Explicit Object renames still require its declaration data.
        if (owner.equals("java/lang/Object") && !classes.containsKey(owner)
                && !rules.containsKey(new MemberReference(owner, ref.name(), ref.descriptor()))) return Set.of();
        if (!path.add(owner)) throw new IllegalArgumentException("Cyclic source hierarchy at " + owner);
        try {
            ClassInfo type = required(owner);
            var declaration = type.methods().stream().filter(m -> matches(m, ref)).findFirst();
            if (declaration.isPresent()) {
                int access = declaration.get().access();
                boolean own = owner.equals(ref.owner());
                if ((access & Opcodes.ACC_PRIVATE) != 0) return Set.of();
                if ((access & Opcodes.ACC_STATIC) != 0 && (own || rootDeclares)) return Set.of();
                if (!own && (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0
                        && !packageName(owner).equals(packageName(ref.owner()))) return Set.of();
                var mapped = rules.get(new MemberReference(owner, ref.name(), ref.descriptor()));
                if (mapped != null) return Set.of(mapped.name());
            }
            Set<String> names = new TreeSet<>();
            if (type.superName() != null) names.addAll(methodNames(type.superName(), ref, rootDeclares, path));
            for (String itf : type.interfaces()) names.addAll(methodNames(itf, ref, rootDeclares, path));
            return names;
        } finally { path.remove(owner); }
    }
    private boolean unmappedPlatform(String owner, MemberReference ref) {
        // The JVM reserves java.* for platform classes, which cannot inherit mod/game APIs.
        // Do not extend this exemption to arbitrary libraries: they may subclass game types.
        // Explicit platform migrations still require declaration-backed hierarchy resolution.
        return owner.startsWith("java/") && !platformNames.contains(new Signature(ref.name(), ref.descriptor()));
    }
    private ClassInfo required(String owner) {
        ClassInfo type = classes.get(owner);
        if (type == null) throw new IllegalArgumentException("Incomplete source hierarchy: " + owner);
        return type;
    }
    private static boolean matches(ClassInfo.Member member, MemberReference ref) {
        return member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor());
    }
    private static String packageName(String owner) { int slash = owner.lastIndexOf('/'); return slash < 0 ? "" : owner.substring(0, slash); }
}
