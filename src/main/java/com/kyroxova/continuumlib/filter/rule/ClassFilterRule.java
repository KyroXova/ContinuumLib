package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.domain.FilterDomain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Filter rule targeting a class by fully qualified name.
 */
public record ClassFilterRule(
        String className,
        EnvironmentCondition condition,
        Path sourceFile
) implements FilterRule {

    public ClassFilterRule {
        Objects.requireNonNull(className, "className");
        className = className.trim().replace('/', '.');
        if (className.isEmpty()) {
            throw new IllegalArgumentException("Class filter name cannot be blank");
        }
        if (condition == null) {
            condition = EnvironmentCondition.ALWAYS;
        }
    }

    @Override
    public FilterDomain domain() {
        return FilterDomain.CLASS;
    }

    @Override
    public String targetIdentifier() {
        return className;
    }
}
