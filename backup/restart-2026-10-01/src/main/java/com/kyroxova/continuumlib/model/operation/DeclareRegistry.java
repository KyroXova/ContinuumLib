package com.kyroxova.continuumlib.model.operation;

import java.util.Objects;

public record DeclareRegistry(String fieldName, RegistryKind registryKind, SourceExpression modIdExpression)
        implements SemanticOperation {
    public DeclareRegistry {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(registryKind, "registryKind");
        Objects.requireNonNull(modIdExpression, "modIdExpression");
    }
}
