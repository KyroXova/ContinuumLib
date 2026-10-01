package com.kyroxova.continuumlib.knowledgebase.rules;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;

/**
 * Rewrites a method call to route through an embedded ContinuumLib Polyfill Shim.
 * Crucial when an API was removed without direct replacement (e.g. Material.STONE in MC 1.20+).
 */
public final class PolyfillRule implements TransformationRule {

    private final String sourceOwner;
    private final String sourceName;
    private final String sourceDesc;

    private final String shimOwner;
    private final String shimName;
    private final String shimDesc;

    private final MCVersion minVersion;
    private final MCVersion maxVersion;
    private final LoaderType targetLoader;
    private final String description;

    public PolyfillRule(String sourceOwner, String sourceName, String sourceDesc,
                        String shimOwner, String shimName, String shimDesc,
                        MCVersion minVersion, MCVersion maxVersion,
                        LoaderType targetLoader, String description) {
        this.sourceOwner = sourceOwner.replace('.', '/');
        this.sourceName = sourceName;
        this.sourceDesc = sourceDesc;
        this.shimOwner = shimOwner.replace('.', '/');
        this.shimName = shimName;
        this.shimDesc = shimDesc;
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

    public String getSourceClass() {
        return sourceOwner;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceDesc() {
        return sourceDesc;
    }

    public String getShimOwner() {
        return shimOwner;
    }

    public String getShimName() {
        return shimName;
    }

    public String getTargetMethod() {
        return shimName;
    }

    public String getShimDesc() {
        return shimDesc;
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
        return description != null ? description : "Polyfill " + sourceOwner + "." + sourceName + " -> " + shimOwner + "." + shimName;
    }
}
