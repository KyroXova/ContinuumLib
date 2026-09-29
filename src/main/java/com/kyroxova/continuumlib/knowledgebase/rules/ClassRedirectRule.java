package com.kyroxova.continuumlib.knowledgebase.rules;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;

import java.util.Objects;

/**
 * Redirects references from an old or platform-specific class to a replacement class.
 * Example: net/minecraftforge/registries/RegistryObject -> net/neoforged/neoforge/registries/DeferredHolder
 */
public final class ClassRedirectRule implements TransformationRule {

    private final String sourceInternalName;
    private final String targetInternalName;
    private final MCVersion minVersion;
    private final MCVersion maxVersion;
    private final LoaderType targetLoader;
    private final String description;

    public ClassRedirectRule(String sourceInternalName, String targetInternalName,
                             MCVersion minVersion, MCVersion maxVersion,
                             LoaderType targetLoader, String description) {
        this.sourceInternalName = normalize(sourceInternalName);
        this.targetInternalName = normalize(targetInternalName);
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        this.targetLoader = targetLoader;
        this.description = description;
    }

    private static String normalize(String name) {
        return name.replace('.', '/');
    }

    public String getSourceInternalName() {
        return sourceInternalName;
    }

    public String getSourceClass() {
        return sourceInternalName;
    }

    public String getTargetInternalName() {
        return targetInternalName;
    }

    public String getTargetClass() {
        return targetInternalName;
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
        return description != null ? description : "Redirect class " + sourceInternalName + " -> " + targetInternalName;
    }
}
