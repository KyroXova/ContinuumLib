package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.domain.FilterDomain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Filter rule targeting a resource file by relative path.
 */
public record ResourceFilterRule(
        String path,
        EnvironmentCondition condition,
        Path sourceFile
) implements FilterRule {

    public ResourceFilterRule {
        Objects.requireNonNull(path, "path");
        path = path.trim().replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Resource filter path cannot be blank");
        }
        if (condition == null) {
            condition = EnvironmentCondition.ALWAYS;
        }
    }

    @Override
    public FilterDomain domain() {
        return FilterDomain.RESOURCE;
    }

    @Override
    public String targetIdentifier() {
        return path;
    }
}
