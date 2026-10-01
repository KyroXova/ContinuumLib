package com.kyroxova.continuumlib.model.operation;

import java.util.Objects;

public record RegisterBlock(
        String registryField,
        String fieldName,
        String registrationId,
        String implementationType,
        SourceExpression factoryExpression,
        BlockProperties properties
) implements SemanticOperation {
    public RegisterBlock {
        Objects.requireNonNull(registryField, "registryField");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(implementationType, "implementationType");
        Objects.requireNonNull(factoryExpression, "factoryExpression");
        Objects.requireNonNull(properties, "properties");
    }
}
