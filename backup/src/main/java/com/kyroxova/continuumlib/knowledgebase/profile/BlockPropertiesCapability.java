package com.kyroxova.continuumlib.knowledgebase.profile;

/**
 * Capability era for Block Properties.
 */
public enum BlockPropertiesCapability {
    /**
     * Legacy constructor properties (1.7.10 - 1.12.2 Material passed into Block constructor).
     */
    LEGACY_MATERIAL_CONSTRUCTOR,

    /**
     * Modern BlockBehaviour.Properties with mandatory Material parameter (1.14 - 1.19.4).
     */
    PROPERTIES_WITH_MATERIAL,

    /**
     * Contemporary BlockBehaviour.Properties without Material parameter (1.20+ - 26.3+).
     */
    PROPERTIES_WITHOUT_MATERIAL
}
