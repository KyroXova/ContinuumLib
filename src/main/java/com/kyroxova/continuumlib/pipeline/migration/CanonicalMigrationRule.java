package com.kyroxova.continuumlib.pipeline.migration;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.util.Objects;

/**
 * Full semantic identity of a migration rule across source and bytecode layers.
 * Preserves owners, names, descriptors, opcodes, environments, originating pack, and layer.
 */
public record CanonicalMigrationRule(
        String rulePackId,
        MigrationType type,
        String sourceOwner,
        String sourceName,
        String sourceDescriptor,
        String targetOwner,
        String targetName,
        String targetDescriptor,
        int opcode,
        EnvironmentId sourceEnvironment,
        EnvironmentId targetEnvironment,
        MigrationLayer layer,
        String evidence
) {
    public CanonicalMigrationRule {
        Objects.requireNonNull(type, "type");
        if (layer == null) {
            layer = MigrationLayer.SOURCE_AST;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String rulePackId;
        private MigrationType type;
        private String sourceOwner;
        private String sourceName;
        private String sourceDescriptor;
        private String targetOwner;
        private String targetName;
        private String targetDescriptor;
        private int opcode;
        private EnvironmentId sourceEnvironment;
        private EnvironmentId targetEnvironment;
        private MigrationLayer layer = MigrationLayer.SOURCE_AST;
        private String evidence;

        public Builder rulePackId(String id) { this.rulePackId = id; return this; }
        public Builder type(MigrationType type) { this.type = type; return this; }
        public Builder sourceOwner(String owner) { this.sourceOwner = owner; return this; }
        public Builder sourceName(String name) { this.sourceName = name; return this; }
        public Builder sourceDescriptor(String desc) { this.sourceDescriptor = desc; return this; }
        public Builder targetOwner(String owner) { this.targetOwner = owner; return this; }
        public Builder targetName(String name) { this.targetName = name; return this; }
        public Builder targetDescriptor(String desc) { this.targetDescriptor = desc; return this; }
        public Builder opcode(int opcode) { this.opcode = opcode; return this; }
        public Builder sourceEnvironment(EnvironmentId env) { this.sourceEnvironment = env; return this; }
        public Builder targetEnvironment(EnvironmentId env) { this.targetEnvironment = env; return this; }
        public Builder layer(MigrationLayer layer) { this.layer = layer; return this; }
        public Builder evidence(String evidence) { this.evidence = evidence; return this; }

        public CanonicalMigrationRule build() {
            return new CanonicalMigrationRule(
                    rulePackId, type, sourceOwner, sourceName, sourceDescriptor,
                    targetOwner, targetName, targetDescriptor, opcode,
                    sourceEnvironment, targetEnvironment, layer, evidence
            );
        }
    }
}
