package com.kyroxova.continuumlib.knowledge.snapshot;

import com.kyroxova.continuumlib.bytecode.ClassInfo;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import org.objectweb.asm.Opcodes;
import java.util.Objects;

public record MemberSnapshot(
        String owner,
        String name,
        String descriptor,
        int access,
        String signature,
        Kind kind
) implements Comparable<MemberSnapshot> {
    public enum Kind { METHOD, CONSTRUCTOR, FIELD }

    public MemberSnapshot {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(kind, "kind");
    }

    public static MemberSnapshot from(String owner, ClassInfo.Member member, Kind kind) {
        return new MemberSnapshot(owner, member.name(), member.descriptor(), member.access(), member.signature(), kind);
    }

    public MemberReference toReference() {
        return new MemberReference(owner, name, descriptor);
    }

    public boolean isStatic() { return (access & Opcodes.ACC_STATIC) != 0; }
    public boolean isPublic() { return (access & Opcodes.ACC_PUBLIC) != 0; }
    public boolean isProtected() { return (access & Opcodes.ACC_PROTECTED) != 0; }
    public boolean isPrivate() { return (access & Opcodes.ACC_PRIVATE) != 0; }
    public boolean isFinal() { return (access & Opcodes.ACC_FINAL) != 0; }
    public boolean isAbstract() { return (access & Opcodes.ACC_ABSTRACT) != 0; }

    @Override
    public int compareTo(MemberSnapshot o) {
        int c = owner.compareTo(o.owner);
        if (c != 0) return c;
        c = name.compareTo(o.name);
        if (c != 0) return c;
        return descriptor.compareTo(o.descriptor);
    }
}
