package com.kyroxova.continuumlib.bytecode;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import java.util.Map;

/** Applies explicit namespace/member renames to existing class bodies.
 * Signature, owner or invocation-kind changes require separate semantic bridges.
 */
public final class ClassAdapter {
    private final Map<String, String> classes;
    private final Map<MemberReference, MemberReference> members;

    public ClassAdapter(Map<String, String> classes, Map<MemberReference, MemberReference> members) {
        this.classes = Map.copyOf(classes);
        this.members = Map.copyOf(members);
        Remapper types = remapper();
        this.members.forEach((from, to) -> {
            String descriptor = from.descriptor().startsWith("(")
                    ? types.mapMethodDesc(from.descriptor()) : types.mapDesc(from.descriptor());
            if (!types.mapType(from.owner()).equals(to.owner()) || !descriptor.equals(to.descriptor())) {
                throw new IllegalArgumentException("Member change requires a semantic bridge: " + from + " -> " + to);
            }
            if (from.name().startsWith("<") || to.name().startsWith("<")) {
                throw new IllegalArgumentException("Constructors and initializers cannot be renamed");
            }
        });
    }

    public byte[] adapt(byte[] original) {
        if (classes.isEmpty() && members.isEmpty()) return original.clone();
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(original).accept(new ClassRemapper(writer, remapper()), 0);
        return writer.toByteArray();
    }

    private Remapper remapper() {
        return new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return classes.getOrDefault(name, name); }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                return renamed(owner, name, descriptor);
            }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                return renamed(owner, name, descriptor);
            }
            private String renamed(String owner, String name, String descriptor) {
                var mapped = members.get(new MemberReference(owner, name, descriptor));
                return mapped == null ? name : mapped.name();
            }
        };
    }
}
