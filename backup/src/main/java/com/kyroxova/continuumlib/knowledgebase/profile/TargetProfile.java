package com.kyroxova.continuumlib.knowledgebase.profile;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.cir.CirRegistryType;

import java.util.*;

/**
 * Concrete capability profile for a target Minecraft version and mod loader.
 * Describes target API capabilities rather than hardcoded string replacements.
 */
public final class TargetProfile {

    private final TargetSpec targetSpec;
    private final IdentifierCapability identifierCapability;
    private final Map<CirRegistryType, RegistryCapability> registryCapabilities = new EnumMap<>(CirRegistryType.class);
    private final Map<CirRegistryType, ReferenceCapability> referenceCapabilities = new EnumMap<>(CirRegistryType.class);
    private final BlockPropertiesCapability blockPropertiesCapability;

    public TargetProfile(TargetSpec targetSpec) {
        this.targetSpec = Objects.requireNonNull(targetSpec, "targetSpec cannot be null");
        MCVersion version = targetSpec.getVersion();
        LoaderType loader = targetSpec.getLoader();

        // 1. Identifier Capability
        if (version.isAtLeast(MCVersion.of("26.0")) || (loader == LoaderType.NEOFORGE && version.isAtLeast(MCVersion.of("26.0")))) {
            this.identifierCapability = IdentifierCapability.IDENTIFIER_FACTORY;
        } else if (version.isAtLeast(MCVersion.of("1.20.4"))) {
            this.identifierCapability = IdentifierCapability.RESOURCE_LOCATION_FACTORY;
        } else if (version.isAtLeast(MCVersion.of("1.13"))) {
            this.identifierCapability = IdentifierCapability.RESOURCE_LOCATION_CONSTRUCTOR;
        } else {
            this.identifierCapability = IdentifierCapability.LEGACY_STRING;
        }

        // 2. Block Properties Capability
        if (version.isAtLeast(MCVersion.of("1.20"))) {
            this.blockPropertiesCapability = BlockPropertiesCapability.PROPERTIES_WITHOUT_MATERIAL;
        } else if (version.isAtLeast(MCVersion.of("1.14"))) {
            this.blockPropertiesCapability = BlockPropertiesCapability.PROPERTIES_WITH_MATERIAL;
        } else {
            this.blockPropertiesCapability = BlockPropertiesCapability.LEGACY_MATERIAL_CONSTRUCTOR;
        }

        // 3. Registry & Reference Capabilities
        if (loader == LoaderType.NEOFORGE) {
            for (CirRegistryType type : CirRegistryType.values()) {
                registryCapabilities.put(type, RegistryCapability.NEOFORGE_DEFERRED_REGISTER);
                referenceCapabilities.put(type, ReferenceCapability.DEFERRED_HOLDER);
            }
            registryCapabilities.put(CirRegistryType.BLOCK, RegistryCapability.NEOFORGE_DEFERRED_BLOCKS);
            referenceCapabilities.put(CirRegistryType.BLOCK, ReferenceCapability.DEFERRED_BLOCK);

            registryCapabilities.put(CirRegistryType.ITEM, RegistryCapability.NEOFORGE_DEFERRED_ITEMS);
            referenceCapabilities.put(CirRegistryType.ITEM, ReferenceCapability.DEFERRED_ITEM);
        } else if (loader == LoaderType.FORGE) {
            if (version.isAtLeast(MCVersion.of("1.14"))) {
                for (CirRegistryType type : CirRegistryType.values()) {
                    registryCapabilities.put(type, RegistryCapability.FORGE_DEFERRED_REGISTER);
                    referenceCapabilities.put(type, ReferenceCapability.REGISTRY_OBJECT);
                }
            } else if (version.isAtLeast(MCVersion.of("1.11"))) {
                for (CirRegistryType type : CirRegistryType.values()) {
                    registryCapabilities.put(type, RegistryCapability.FORGE_REGISTRY_EVENTS);
                    referenceCapabilities.put(type, ReferenceCapability.RAW_INSTANCE);
                }
            } else {
                for (CirRegistryType type : CirRegistryType.values()) {
                    registryCapabilities.put(type, RegistryCapability.FORGE_GAME_REGISTRY);
                    referenceCapabilities.put(type, ReferenceCapability.RAW_INSTANCE);
                }
            }
        } else if (loader == LoaderType.FABRIC || loader == LoaderType.QUILT) {
            for (CirRegistryType type : CirRegistryType.values()) {
                registryCapabilities.put(type, RegistryCapability.FABRIC_REGISTRY);
                referenceCapabilities.put(type, ReferenceCapability.RAW_INSTANCE);
            }
        } else if (loader == LoaderType.PAPER || loader == LoaderType.SPIGOT) {
            for (CirRegistryType type : CirRegistryType.values()) {
                registryCapabilities.put(type, RegistryCapability.PAPER_PLUGIN_REGISTRY);
                referenceCapabilities.put(type, ReferenceCapability.PLUGIN_KEY);
            }
        }
    }

    public static TargetProfile forSpec(TargetSpec targetSpec) {
        return new TargetProfile(targetSpec);
    }

    public TargetSpec getTargetSpec() {
        return targetSpec;
    }

    public IdentifierCapability getIdentifierCapability() {
        return identifierCapability;
    }

    public BlockPropertiesCapability getBlockPropertiesCapability() {
        return blockPropertiesCapability;
    }

    public RegistryCapability getRegistryCapability(CirRegistryType type) {
        return registryCapabilities.getOrDefault(type, RegistryCapability.FORGE_DEFERRED_REGISTER);
    }

    public ReferenceCapability getReferenceCapability(CirRegistryType type) {
        return referenceCapabilities.getOrDefault(type, ReferenceCapability.REGISTRY_OBJECT);
    }

    public String getIdentifierClassSimpleName() {
        return identifierCapability == IdentifierCapability.IDENTIFIER_FACTORY ? "Identifier" : "ResourceLocation";
    }

    public String getIdentifierClassCanonical() {
        return "net.minecraft.resources." + getIdentifierClassSimpleName();
    }

    public boolean usesBuiltInRegistries() {
        return targetSpec.getVersion().isAtLeast(MCVersion.of("1.19.3"));
    }

    @Override
    public String toString() {
        return String.format("TargetProfile{%s, idCap=%s, propCap=%s}",
                targetSpec, identifierCapability, blockPropertiesCapability);
    }
}
