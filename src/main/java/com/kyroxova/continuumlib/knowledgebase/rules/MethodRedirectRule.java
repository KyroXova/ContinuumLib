package com.kyroxova.continuumlib.knowledgebase.rules;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;

/**
 * Rewrites a method call instruction (owner, name, descriptor, opcode).
 */
public final class MethodRedirectRule implements TransformationRule {

    private final String sourceOwner;
    private final String sourceName;
    private final String sourceDesc;

    private final String targetOwner;
    private final String targetName;
    private final String targetDesc;
    private final int targetOpcode; // -1 if unchanged

    private final MCVersion minVersion;
    private final MCVersion maxVersion;
    private final LoaderType targetLoader;
    private final String description;

    public MethodRedirectRule(String sourceOwner, String sourceName, String sourceDesc,
                              String targetOwner, String targetName, String targetDesc, int targetOpcode,
                              MCVersion minVersion, MCVersion maxVersion,
                              LoaderType targetLoader, String description) {
        this.sourceOwner = sourceOwner.replace('.', '/');
        this.sourceName = sourceName;
        this.sourceDesc = sourceDesc;
        this.targetOwner = targetOwner.replace('.', '/');
        this.targetName = targetName;
        this.targetDesc = targetDesc;
        this.targetOpcode = targetOpcode;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        this.targetLoader = targetLoader;
        this.description = description;
    }

    public boolean matches(String owner, String name, String desc) {
        return this.sourceOwner.equals(owner.replace('.', '/')) &&
                this.sourceName.equals(name) &&
                (this.sourceDesc == null || this.sourceDesc.equals(desc));
    }

    public String getSourceOwner() {
        return sourceOwner;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceDesc() {
        return sourceDesc;
    }

    public String getTargetOwner() {
        return targetOwner;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getTargetDesc() {
        return targetDesc;
    }

    public int getTargetOpcode() {
        return targetOpcode;
    }

    @Override
    public boolean appliesTo(TargetSpec baseSpec, TargetSpec targetSpec) {
        if (targetLoader != null && targetSpec.getLoader() != targetLoader && targetLoader != LoaderType.TEST_ENVIRONMENT) {
            return false;
        }
        if (minVersion != null && targetSpec.getVersion().isBefore(minVersion)) {
            return false;
        }
        if (maxVersion != null && targetSpec.getVersion().isAfter(maxVersion)) {
            return false;
        }
        return true;
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Redirect method " + sourceOwner + "." + sourceName + " -> " + targetOwner + "." + targetName;
    }
}
