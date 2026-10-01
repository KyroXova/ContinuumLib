package com.kyroxova.continuumlib.model.symbol;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.MappingNamespace;

import java.util.Objects;

public record SymbolKey(
        EnvironmentId environment,
        String owner,
        SymbolKind kind,
        String name,
        String descriptor,
        MappingNamespace namespace
) {
    public SymbolKey {
        Objects.requireNonNull(environment, "environment");
        owner = requireText(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        name = requireText(name, "name");
        descriptor = requireText(descriptor, "descriptor");
        Objects.requireNonNull(namespace, "namespace");
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        // JVM identifiers are not Java source identifiers. Preserve obfuscated names exactly.
        return value;
    }
}
