package com.kyroxova.continuumlib.filter.config;

import com.kyroxova.continuumlib.filter.rule.ExclusionRuleSet;
import com.kyroxova.continuumlib.filter.rule.InclusionRuleSet;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Encapsulates the discovered ContinuumLib project configuration.
 */
public record ContinuumProjectConfiguration(
        Path configurationRoot,
        InclusionRuleSet inclusions,
        ExclusionRuleSet exclusions
) {
    public ContinuumProjectConfiguration {
        Objects.requireNonNull(inclusions, "inclusions");
        Objects.requireNonNull(exclusions, "exclusions");
    }

    public static ContinuumProjectConfiguration empty(Path root) {
        return new ContinuumProjectConfiguration(root, InclusionRuleSet.EMPTY, ExclusionRuleSet.EMPTY);
    }
}
