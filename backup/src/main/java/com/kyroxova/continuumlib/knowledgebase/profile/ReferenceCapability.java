package com.kyroxova.continuumlib.knowledgebase.profile;

/**
 * Capability era for registered object references/holders.
 */
public enum ReferenceCapability {
    /**
     * Direct raw instance (1.7.10 - 1.12.2): Block, Item.
     */
    RAW_INSTANCE,

    /**
     * Forge RegistryObject<T> (1.14 - 1.20.1 Forge).
     */
    REGISTRY_OBJECT,

    /**
     * NeoForge DeferredBlock<T> (1.20.4 - 26.3+ NeoForge).
     */
    DEFERRED_BLOCK,

    /**
     * NeoForge DeferredItem<T> (1.20.4 - 26.3+ NeoForge).
     */
    DEFERRED_ITEM,

    /**
     * NeoForge DeferredHolder<R, T> (1.20.4 - 26.3+ NeoForge).
     */
    DEFERRED_HOLDER,

    /**
     * Bukkit / Paper NamespacedKey handle for custom blocks, items, recipes.
     */
    PLUGIN_KEY
}
