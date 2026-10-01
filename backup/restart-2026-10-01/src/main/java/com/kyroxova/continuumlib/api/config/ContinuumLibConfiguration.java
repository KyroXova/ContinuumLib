package com.kyroxova.continuumlib.api.config;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.util.List;
import java.util.Objects;

public record ContinuumLibConfiguration(
        EnvironmentId base,
        List<EnvironmentId> targets,
        OutputConfiguration output
) {
    public ContinuumLibConfiguration {
        Objects.requireNonNull(base, "base");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        Objects.requireNonNull(output, "output");
    }
}
