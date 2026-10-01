package com.kyroxova.continuumlib.model.environment;

import java.util.Objects;

public record EnvironmentId(
        String minecraftVersion,
        Loader loader,
        MappingNamespace mappings,
        int javaVersion
) {
    public EnvironmentId {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(mappings, "mappings");
        minecraftVersion = minecraftVersion.trim();
        if (minecraftVersion.isEmpty()) {
            throw new IllegalArgumentException("minecraftVersion cannot be blank");
        }
        if (javaVersion < 8) {
            throw new IllegalArgumentException("javaVersion must be at least 8");
        }
    }
}
