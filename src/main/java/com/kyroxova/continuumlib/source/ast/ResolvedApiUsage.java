package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.ast.Node;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import java.util.Objects;

public record ResolvedApiUsage(
        Node astNode,
        MemberReference resolvedMember,
        Kind kind,
        int line,
        int column
) {
    public enum Kind {
        METHOD_CALL,
        CONSTRUCTOR_CALL,
        STATIC_METHOD_CALL,
        FIELD_ACCESS,
        TYPE_REFERENCE
    }

    public ResolvedApiUsage {
        Objects.requireNonNull(astNode, "astNode");
        Objects.requireNonNull(resolvedMember, "resolvedMember");
        Objects.requireNonNull(kind, "kind");
    }
}
