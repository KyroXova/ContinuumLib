package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.domain.FilterDomain;
import com.kyroxova.continuumlib.filter.domain.RegistryType;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Filter rule targeting a specific Minecraft registry element.
 */
public record RegistryFilterRule(
        RegistryType registryType,
        String id,
        EnvironmentCondition condition,
        Path sourceFile
) implements FilterRule {

    public RegistryFilterRule {
        Objects.requireNonNull(registryType, "registryType");
        Objects.requireNonNull(id, "id");
        id = id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Registry filter id cannot be blank");
        }
        if (condition == null) {
            condition = EnvironmentCondition.ALWAYS;
        }
    }

    @Override
    public FilterDomain domain() {
        return FilterDomain.REGISTRY;
    }

    @Override
    public String targetIdentifier() {
        return registryType.name().toUpperCase() + " " + id;
    }
}
