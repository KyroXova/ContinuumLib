package com.kyroxova.continuumlib.bytecode;

import java.util.*;

/** Conservative declaration lookup, not a JVM access or behavioral compatibility verifier.
 * Multiple interface declarations are deliberately left ambiguous for a later verifier.
 */
public final class MemberLookup {
    public enum Status { FOUND, MISSING_MEMBER, INCOMPLETE_CLASSPATH, AMBIGUOUS, CYCLIC_HIERARCHY }
    public record Result(Status status, String declaringOwner, ClassInfo.Member member) {}
    private final Map<String, ClassInfo> classes;
    public MemberLookup(Map<String, ClassInfo> classes) { this.classes = Map.copyOf(classes); }
    public Result find(MemberReference reference) {
        return search(reference.owner(), reference, new HashSet<>());
    }
    private Result search(String owner, MemberReference ref, Set<String> path) {
        if (!path.add(owner)) return result(Status.CYCLIC_HIERARCHY);
        try {
            ClassInfo type = classes.get(owner);
            if (type == null) return result(Status.INCOMPLETE_CLASSPATH);
            boolean method = ref.descriptor().startsWith("(");
            for (ClassInfo.Member member : method ? type.methods() : type.fields()) {
                if (member.name().equals(ref.name()) && member.descriptor().equals(ref.descriptor())) {
                    // Static/private interface methods are callable only on their declaring
                    // interface, never inherited by implementing classes or subinterfaces.
                    if (method && !owner.equals(ref.owner())
                            && (type.access() & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0
                            && (member.access() & (org.objectweb.asm.Opcodes.ACC_STATIC | org.objectweb.asm.Opcodes.ACC_PRIVATE)) != 0) continue;
                    return new Result(Status.FOUND, owner, member);
                }
            }
            if (ref.name().startsWith("<")) return result(Status.MISSING_MEMBER);
            // Class methods search the superclass before interfaces; fields do the reverse.
            if (method && type.superName() != null) {
                Result parent = search(type.superName(), ref, path);
                if (parent.status() != Status.MISSING_MEMBER) return parent;
            }
            Map<String, Result> candidates = new LinkedHashMap<>();
            for (String itf : type.interfaces()) {
                Result found = search(itf, ref, path);
                if (found.status() == Status.FOUND) candidates.put(found.declaringOwner(), found);
                else if (found.status() != Status.MISSING_MEMBER) return found;
            }
            if (candidates.size() > 1) return result(Status.AMBIGUOUS);
            if (!candidates.isEmpty()) return candidates.values().iterator().next();
            if (!method && type.superName() != null) return search(type.superName(), ref, path);
            return result(Status.MISSING_MEMBER);
        } finally { path.remove(owner); }
    }
    private static Result result(Status status) { return new Result(status, null, null); }
}
