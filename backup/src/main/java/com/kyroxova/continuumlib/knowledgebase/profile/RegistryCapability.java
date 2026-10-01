package com.kyroxova.continuumlib.knowledgebase.profile;

/**
 * Capability era for Registry Containers.
 */
public enum RegistryCapability {
    /**
     * Legacy Forge GameRegistry (1.7.10 - 1.10.2).
     */
    FORGE_GAME_REGISTRY,

    /**
     * Forge RegistryEvent.Register<T> (1.11 - 1.13.2).
     */
    FORGE_REGISTRY_EVENTS,

    /**
     * Standard Forge DeferredRegister<T> (1.14 - 1.20.1 Forge).
     */
    FORGE_DEFERRED_REGISTER,

    /**
     * NeoForge typed block registry (1.20.4 - 26.3+ NeoForge): DeferredRegister.Blocks.
     */
    NEOFORGE_DEFERRED_BLOCKS,

    /**
     * NeoForge typed items registry (1.20.4 - 26.3+ NeoForge): DeferredRegister.Items.
     */
    NEOFORGE_DEFERRED_ITEMS,

    /**
     * NeoForge generic DeferredRegister<T> using modern ResourceKey or Registries constants.
     */
    NEOFORGE_DEFERRED_REGISTER,

    /**
     * Vanilla / Fabric direct Registry.register(...) calls.
     */
    FABRIC_REGISTRY,

    /**
     * Paper / Bukkit server plugin registration (NamespacedKey / JavaPlugin).
     */
    PAPER_PLUGIN_REGISTRY
}
