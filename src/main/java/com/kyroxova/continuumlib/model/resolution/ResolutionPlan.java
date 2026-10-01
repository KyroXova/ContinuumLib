package com.kyroxova.continuumlib.model.resolution;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.util.List;
import java.util.Objects;

public record ResolutionPlan(EnvironmentId source, EnvironmentId target, List<Resolution> resolutions) {
    public ResolutionPlan {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        resolutions = List.copyOf(Objects.requireNonNull(resolutions, "resolutions"));
    }

    public boolean isComplete() {
        return resolutions.stream().noneMatch(r ->
                r.status() == ResolutionStatus.UNSUPPORTED || r.status() == ResolutionStatus.AMBIGUOUS);
    }
}
