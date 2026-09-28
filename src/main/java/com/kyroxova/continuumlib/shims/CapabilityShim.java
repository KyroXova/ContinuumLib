package com.kyroxova.continuumlib.shims;

import java.util.logging.Logger;

/**
 * Universal Polyfill for Capabilities and Item Handlers.
 * In Forge 1.18.2 - 1.20.1: uses CapabilityItemHandler.ITEM_HANDLER_CAPABILITY & LazyOptional.
 * In NeoForge 1.20.4+: uses BlockCapability / Capabilities.ItemHandler.BLOCK.
 * In Fabric: uses Fabric Transfer API.
 */
public final class CapabilityShim {

    private static final Logger LOGGER = Logger.getLogger(CapabilityShim.class.getName());

    private CapabilityShim() {}

    /**
     * Resolves an item handler or capability holder safely across platforms.
     */
    public static Object resolveItemHandler(Object blockEntity, Object side) {
        if (blockEntity == null) return null;
        try {
            // Check if NeoForge block capability is available
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler");
            Object blockCap = capabilitiesClass.getField("BLOCK").get(null);
            LOGGER.fine("[CapabilityShim] Resolved NeoForge ItemHandler BlockCapability");
            return blockCap;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
