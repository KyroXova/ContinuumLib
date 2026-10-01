package com.kyroxova.continuumlib.filter.condition;

import java.util.Objects;

/**
 * Reusable condition model matching Minecraft version, loader, loader version,
 * Java version, mapping namespace, and output mode.
 */
public record EnvironmentCondition(
        String minecraft,
        String loader,
        String loaderVersion,
        String java,
        String namespace,
        String outputMode
) {
    public static final EnvironmentCondition ALWAYS = new EnvironmentCondition(null, null, null, null, null, null);

    public boolean matches(TargetContext context) {
        Objects.requireNonNull(context, "context");

        if (minecraft != null && !minecraft.isBlank()) {
            if (!VersionConstraint.parse(minecraft).test(context.environmentId().minecraftVersion())) {
                return false;
            }
        }

        if (loader != null && !loader.isBlank()) {
            String targetLoader = context.environmentId().loader().name();
            if (!loader.trim().equalsIgnoreCase(targetLoader)) {
                return false;
            }
        }

        if (loaderVersion != null && !loaderVersion.isBlank()) {
            if (context.loaderVersion() == null) {
                return false;
            }
            if (!VersionConstraint.parse(loaderVersion).test(context.loaderVersion())) {
                return false;
            }
        }

        if (java != null && !java.isBlank()) {
            if (!VersionConstraint.testJava(java, context.environmentId().javaVersion())) {
                return false;
            }
        }

        if (namespace != null && !namespace.isBlank()) {
            String targetNamespace = context.environmentId().mappings().name();
            if (!namespace.trim().equalsIgnoreCase(targetNamespace)) {
                return false;
            }
        }

        if (outputMode != null && !outputMode.isBlank()) {
            if (!outputMode.trim().equalsIgnoreCase(context.outputMode())) {
                return false;
            }
        }

        return true;
    }
}
