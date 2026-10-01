package com.kyroxova.continuumlib.knowledge.symbol;

import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.knowledge.snapshot.MemberSnapshot;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import java.util.Objects;

public record SymbolVersion(
        EnvironmentId environment,
        MemberReference target,
        MemberSnapshot.Kind kind
) {
    public SymbolVersion {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
    }

    public static SymbolVersion of(EnvironmentId environment, MemberReference target, MemberSnapshot.Kind kind) {
        return new SymbolVersion(environment, target, kind);
    }
}
