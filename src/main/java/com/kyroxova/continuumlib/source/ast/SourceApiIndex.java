package com.kyroxova.continuumlib.source.ast;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import org.objectweb.asm.Opcodes;

import java.util.*;

public final class SourceApiIndex {
    public record Method(MemberReference reference, boolean isStatic, boolean isInterface) {}
    public record Field(MemberReference reference, boolean isStatic) {}

    private final Map<String, ClassInfo> classes;

    public SourceApiIndex(Map<String, ClassInfo> classes) {
        this.classes = Map.copyOf(classes);
    }

    public static SourceApiIndex empty() {
        return new SourceApiIndex(Map.of());
    }

    public Optional<Method> uniqueMethod(String owner, String name) {
        String internalOwner = internal(owner);
        LinkedHashMap<String, Method> candidates = new LinkedHashMap<>();
        collectMethods(internalOwner, internalOwner, name, new HashSet<>(), candidates);
        return candidates.size() == 1
                ? Optional.of(candidates.values().iterator().next())
                : Optional.empty();
    }

    public Optional<MemberReference> uniqueConstructor(String owner) {
        ClassInfo type = classes.get(internal(owner));
        if (type == null) return Optional.empty();

        List<MemberReference> constructors = type.methods().stream()
                .filter(member -> member.name().equals("<init>"))
                .map(member -> new MemberReference(type.name(), member.name(), member.descriptor()))
                .toList();
        return constructors.size() == 1 ? Optional.of(constructors.get(0)) : Optional.empty();
    }

    public Optional<Field> uniqueField(String owner, String name) {
        List<Field> fields = new ArrayList<>();
        collectFields(internal(owner), name, new HashSet<>(), fields);
        return fields.size() == 1 ? Optional.of(fields.get(0)) : Optional.empty();
    }

    private void collectMethods(
            String rootOwner,
            String owner,
            String name,
            Set<String> visiting,
            Map<String, Method> candidates
    ) {
        if (!visiting.add(owner)) return;
        ClassInfo type = classes.get(owner);
        if (type == null) return;

        boolean isInterface = (type.access() & Opcodes.ACC_INTERFACE) != 0;
        for (ClassInfo.Member member : type.methods()) {
            if (!member.name().equals(name) || member.name().startsWith("<")) continue;
            if (!owner.equals(rootOwner) && isInterface
                    && (member.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) != 0) {
                continue;
            }
            candidates.putIfAbsent(
                    member.descriptor(),
                    new Method(
                            new MemberReference(owner, name, member.descriptor()),
                            (member.access() & Opcodes.ACC_STATIC) != 0,
                            isInterface
                    )
            );
        }

        if (type.superName() != null) {
            collectMethods(rootOwner, type.superName(), name, visiting, candidates);
        }
        for (String itf : type.interfaces()) {
            collectMethods(rootOwner, itf, name, visiting, candidates);
        }
    }

    private void collectFields(String owner, String name, Set<String> visiting, List<Field> fields) {
        if (!visiting.add(owner)) return;
        ClassInfo type = classes.get(owner);
        if (type == null) return;

        for (ClassInfo.Member member : type.fields()) {
            if (member.name().equals(name)) {
                fields.add(new Field(
                        new MemberReference(owner, name, member.descriptor()),
                        (member.access() & Opcodes.ACC_STATIC) != 0
                ));
            }
        }
        if (!fields.isEmpty()) return;

        for (String itf : type.interfaces()) {
            collectFields(itf, name, visiting, fields);
        }
        if (type.superName() != null) {
            collectFields(type.superName(), name, visiting, fields);
        }
    }

    private static String internal(String owner) {
        return owner.replace('.', '/');
    }
}
