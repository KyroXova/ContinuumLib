package com.kyroxova.continuumlib.model.resolution;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.Severity;

import java.util.List;
import java.util.Objects;

public record ResolutionPlan(EnvironmentId source, EnvironmentId target, List<Resolution> resolutions,
                             List<Diagnostic> diagnostics) {
    public ResolutionPlan(EnvironmentId source, EnvironmentId target, List<Resolution> resolutions) {
        this(source, target, resolutions, List.of());
    }
    public ResolutionPlan {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        resolutions = List.copyOf(Objects.requireNonNull(resolutions, "resolutions"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public boolean isComplete() {
        return !resolutions.isEmpty() && diagnostics.stream().noneMatch(d -> d.severity() == Severity.ERROR)
                && resolutions.stream().noneMatch(r ->
                r.status() == ResolutionStatus.UNSUPPORTED || r.status() == ResolutionStatus.AMBIGUOUS);
    }
}
