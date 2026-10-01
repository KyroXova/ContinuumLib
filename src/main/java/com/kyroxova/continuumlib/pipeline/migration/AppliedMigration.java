package com.kyroxova.continuumlib.pipeline.migration;

import java.util.Objects;

/**
 * Record of a migration applied by either the source or bytecode transformation layer.
 */
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
        return new AppliedMigration(
                rule.rulePackId(),
                rule.type(),
                rule.sourceOwner(),
                rule.sourceName(),
                rule.sourceDescriptor(),
                rule.targetOwner(),
                rule.targetName(),
                rule.targetDescriptor(),
                rule.layer(),
                confidence,
                file,
                line
        );
    }
}
