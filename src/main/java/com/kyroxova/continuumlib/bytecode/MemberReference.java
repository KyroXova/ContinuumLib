package com.kyroxova.continuumlib.bytecode;

import java.util.Objects;

/** Exact JVM identity; descriptors distinguish overloads and fields. */
public record MemberReference(String owner, String name, String descriptor) {
    public MemberReference {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        Objects.requireNonNull(descriptor);
        if (owner.isEmpty() || name.isEmpty() || descriptor.isEmpty()) {
            throw new IllegalArgumentException("A member requires owner, name and descriptor");
        }
    }
}
