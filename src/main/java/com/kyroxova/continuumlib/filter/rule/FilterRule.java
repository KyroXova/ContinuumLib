package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.domain.FilterDomain;

import java.nio.file.Path;

/**
 * Common interface for all target-aware project filter rules.
 */
public sealed interface FilterRule permits
        RegistryFilterRule, SourceFilterRule, ClassFilterRule, ResourceFilterRule {

    FilterDomain domain();

    EnvironmentCondition condition();

    Path sourceFile();

    String targetIdentifier();
}
