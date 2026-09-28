package com.kyroxova.bootstrapper.environment;

import java.util.Locale;

/**
 * Supported Minecraft mod loaders and platforms.
 */
public enum LoaderType {
    FORGE("forge", "Minecraft Forge"),
    NEOFORGE("neoforge", "NeoForged / NeoForge"),
    FABRIC("fabric", "Fabric Loader"),
    QUILT("quilt", "Quilt Loader"),
    TEST_ENVIRONMENT("test", "Test / Standalone Environment");

    private final String id;
    private final String displayName;

    LoaderType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LoaderType fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            return FORGE;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (LoaderType type : values()) {
            if (type.id.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown loader type: " + name);
    }
}
