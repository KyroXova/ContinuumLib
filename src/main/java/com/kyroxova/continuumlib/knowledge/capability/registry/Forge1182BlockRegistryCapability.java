package com.kyroxova.continuumlib.knowledge.capability.registry;

import com.kyroxova.continuumlib.knowledge.capability.CapabilityProvider;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.operation.DeclareRegistry;
import com.kyroxova.continuumlib.model.operation.RegisterBlock;
import com.kyroxova.continuumlib.model.operation.SemanticOperation;
import com.kyroxova.continuumlib.model.resolution.Resolution;
import com.kyroxova.continuumlib.model.resolution.ResolutionStatus;

import java.util.Optional;

public final class Forge1182BlockRegistryCapability implements CapabilityProvider {
    @Override
    public Optional<Resolution> resolve(
            SemanticOperation operation,
            EnvironmentId source,
            EnvironmentId target
    ) {
        if (!(operation instanceof DeclareRegistry) && !(operation instanceof RegisterBlock)) {
            return Optional.empty();
        }
        if (!isForge1182(source) || !source.equals(target)) {
            return Optional.empty();
        }
        return Optional.of(new Resolution(
                operation,
                ResolutionStatus.DIRECT,
                "ContinuumLib:forge-1.18.2:block-registry:v1",
                "The source operation is native to the target environment"));
    }

    private boolean isForge1182(EnvironmentId environment) {
        return environment.loader() == Loader.FORGE && environment.minecraftVersion().equals("1.18.2");
    }
}
