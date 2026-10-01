package com.kyroxova.continuumlib.knowledge.capability;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.operation.SemanticOperation;
import com.kyroxova.continuumlib.model.resolution.Resolution;

import java.util.Optional;

public interface CapabilityProvider {
    Optional<Resolution> resolve(SemanticOperation operation, EnvironmentId source, EnvironmentId target);
}
