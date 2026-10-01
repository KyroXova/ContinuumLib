package com.kyroxova.continuumlib.resolver;

import com.kyroxova.continuumlib.knowledge.capability.CapabilityProvider;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.operation.SemanticOperation;
import com.kyroxova.continuumlib.model.project.ProjectModel;
import com.kyroxova.continuumlib.model.resolution.Resolution;
import com.kyroxova.continuumlib.model.resolution.ResolutionPlan;
import com.kyroxova.continuumlib.model.resolution.ResolutionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ContinuumLibResolver {
    private final List<CapabilityProvider> providers;

    public ContinuumLibResolver(List<CapabilityProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    public ResolutionPlan resolve(ProjectModel project, EnvironmentId source, EnvironmentId target) {
        List<Resolution> resolutions = new ArrayList<>();
        for (SemanticOperation operation : project.operations()) {
            List<Resolution> matches = providers.stream()
                    .map(provider -> provider.resolve(operation, source, target))
                    .flatMap(java.util.Optional::stream)
                    .toList();
            if (matches.isEmpty()) {
                resolutions.add(new Resolution(operation, ResolutionStatus.UNSUPPORTED,
                        "ContinuumLib:resolver", "No capability provider supports this source-target operation"));
            } else if (matches.size() > 1) {
                resolutions.add(new Resolution(operation, ResolutionStatus.AMBIGUOUS,
                        "ContinuumLib:resolver", "Multiple capability providers matched this operation"));
            } else {
                resolutions.add(matches.get(0));
            }
        }
        return new ResolutionPlan(source, target, resolutions, project.diagnostics());
    }
}
