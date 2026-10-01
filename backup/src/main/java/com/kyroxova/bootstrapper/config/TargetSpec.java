package com.kyroxova.bootstrapper.config;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;

import java.util.Objects;

/**
 * Defines a specific compilation or runtime target environment.
 */
public final class TargetSpec {

    private final MCVersion version;
    private final LoaderType loader;
    private final String mappings;
    private final int javaTarget;

    public TargetSpec(MCVersion version, LoaderType loader, String mappings, int javaTarget) {
        this.version = Objects.requireNonNull(version, "Target version cannot be null");
        this.loader = Objects.requireNonNull(loader, "Target loader cannot be null");
        this.mappings = mappings == null ? "mojmap" : mappings;
        this.javaTarget = javaTarget <= 0 ? defaultJavaFor(version) : javaTarget;
    }

    public static TargetSpec of(String version, String loader) {
        return new TargetSpec(MCVersion.of(version), LoaderType.fromString(loader), "mojmap", 0);
    }

    public static TargetSpec of(String version, String loader, String mappings) {
        return new TargetSpec(MCVersion.of(version), LoaderType.fromString(loader), mappings, 0);
    }

    public static int defaultJavaFor(MCVersion version) {
        if (version.isBefore(MCVersion.of("1.17"))) {
            return 8;
        } else if (version.isBefore(MCVersion.V1_18_2)) {
            return 16;
        } else if (version.isBefore(MCVersion.of("1.20.5"))) {
            return 17;
        } else {
            return 21;
        }
    }

    public MCVersion getVersion() {
        return version;
    }

    public LoaderType getLoader() {
        return loader;
    }

    public String getMappings() {
        return mappings;
    }

    public int getJavaTarget() {
        return javaTarget;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetSpec that)) return false;
        return javaTarget == that.javaTarget &&
                Objects.equals(version, that.version) &&
                loader == that.loader &&
                Objects.equals(mappings, that.mappings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, loader, mappings, javaTarget);
    }

    @Override
    public String toString() {
        return loader.getId() + "-" + version.getRaw() + " (" + mappings + ", Java " + javaTarget + ")";
    }
}
