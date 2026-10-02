package com.kyroxova.continuumlib.pipeline.migration;

import java.util.Objects;

public record AppliedMigration(
        String rulePackId,
        MigrationType type,
        String sourceOwner,
        String sourceName,
        String sourceDescriptor,
        String targetOwner,
        String targetName,
        String targetDescriptor,
        MigrationLayer layer,
        MigrationConfidence confidence,
        String file,
        int line
) {
    public AppliedMigration {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(confidence, "confidence");
    }

    public static AppliedMigration from(CanonicalMigrationRule rule, MigrationConfidence confidence, String file, int line) {
        return from(rule, rule.layer(), confidence, file, line);
    }

    public static AppliedMigration from(
            CanonicalMigrationRule rule,
            MigrationLayer appliedLayer,
            MigrationConfidence confidence,
            String file,
            int line
    ) {
        return new AppliedMigration(
                rule.rulePackId(),
                rule.type(),
                rule.sourceOwner(),
                rule.sourceName(),
                rule.sourceDescriptor(),
                rule.targetOwner(),
                rule.targetName(),
                rule.targetDescriptor(),
                appliedLayer,
                confidence,
                file,
                line
        );
    }

    public boolean matches(CanonicalMigrationRule rule) {
        return Objects.equals(rulePackId, rule.rulePackId())
                && type == rule.type()
                && Objects.equals(sourceOwner, rule.sourceOwner())
                && Objects.equals(sourceName, rule.sourceName())
                && Objects.equals(sourceDescriptor, rule.sourceDescriptor())
                && Objects.equals(targetOwner, rule.targetOwner())
                && Objects.equals(targetName, rule.targetName())
                && Objects.equals(targetDescriptor, rule.targetDescriptor());
    }
}
