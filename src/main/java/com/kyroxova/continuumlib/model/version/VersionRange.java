package com.kyroxova.continuumlib.model.version;

import java.util.Objects;

public record VersionRange(MinecraftVersion inclusiveStart, MinecraftVersion exclusiveEnd) {
    public VersionRange {
        Objects.requireNonNull(inclusiveStart, "inclusiveStart");
        if (exclusiveEnd != null && inclusiveStart.compareTo(exclusiveEnd) >= 0) {
            throw new IllegalArgumentException("Version range end must be after its start");
        }
    }

    public static VersionRange between(String inclusiveStart, String exclusiveEnd) {
        return new VersionRange(MinecraftVersion.parse(inclusiveStart), MinecraftVersion.parse(exclusiveEnd));
    }

    public static VersionRange from(String inclusiveStart) {
        return new VersionRange(MinecraftVersion.parse(inclusiveStart), null);
    }

    public boolean contains(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        return version.compareTo(inclusiveStart) >= 0
                && (exclusiveEnd == null || version.compareTo(exclusiveEnd) < 0);
    }
}
