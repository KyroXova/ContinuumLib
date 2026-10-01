package com.kyroxova.continuumlib.knowledgebase.profile;

/**
 * Capability era for Resource Identifiers.
 */
public enum IdentifierCapability {
    /**
     * Historical un-namespaced or legacy string IDs (1.7.10 - 1.12.2).
     */
    LEGACY_STRING,

    /**
     * Modern ResourceLocation with direct constructor (1.13 - 1.20.2): new ResourceLocation(modId, path).
     */
    RESOURCE_LOCATION_CONSTRUCTOR,

    /**
     * ResourceLocation with factory method (1.20.4 - 1.21.x): ResourceLocation.fromNamespaceAndPath(modId, path).
     */
    RESOURCE_LOCATION_FACTORY,

    /**
     * Modern NeoForge 26.x+ Identifier with factory: Identifier.fromNamespaceAndPath(modId, path).
     */
    IDENTIFIER_FACTORY
}
