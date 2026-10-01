package com.kyroxova.continuumlib.filter.condition;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.util.Objects;

/**
 * Execution and evaluation context for filtering rules.
 */
public record TargetContext(
        EnvironmentId environmentId,
        String loaderVersion,
        String outputMode
) {
    public TargetContext {
        Objects.requireNonNull(environmentId, "environmentId");
        if (outputMode == null || outputMode.isBlank()) {
            outputMode = "per_version";
        }
    }

    public static TargetContext of(EnvironmentId id) {
        return new TargetContext(id, null, "per_version");
    }

    public static TargetContext of(EnvironmentId id, String loaderVersion, String outputMode) {
        return new TargetContext(id, loaderVersion, outputMode);
    }
}
