package com.kyroxova.continuumlib.model.version;

import java.util.Objects;

public record SymbolLifecycle(
        MinecraftVersion introduced,
        MinecraftVersion deprecated,
        MinecraftVersion removed,
        String replacementStableId
) {
    public SymbolLifecycle {
        Objects.requireNonNull(introduced, "introduced");
        if (deprecated != null && deprecated.compareTo(introduced) < 0) {
            throw new IllegalArgumentException("Deprecation cannot precede introduction");
        }
        if (removed != null && removed.compareTo(introduced) <= 0) {
            throw new IllegalArgumentException("Removal must follow introduction");
        }
        if (deprecated != null && removed != null && removed.compareTo(deprecated) < 0) {
            throw new IllegalArgumentException("Removal cannot precede deprecation");
        }
    }

    public SymbolAvailability availabilityAt(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        if (version.compareTo(introduced) < 0) {
            return SymbolAvailability.UNAVAILABLE;
        }
        if (removed != null && version.compareTo(removed) >= 0) {
            return SymbolAvailability.REMOVED;
        }
        if (deprecated != null && version.compareTo(deprecated) >= 0) {
            return SymbolAvailability.DEPRECATED;
        }
        return SymbolAvailability.AVAILABLE;
    }
}
