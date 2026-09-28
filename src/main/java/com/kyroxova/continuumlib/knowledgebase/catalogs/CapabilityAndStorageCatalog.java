package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Capabilities, Item Handlers, and Storage.
 * Bridges Forge CapabilityItemHandler across NeoForge Block Capabilities and Fabric.
 */
public final class CapabilityAndStorageCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_4 = MCVersion.of("1.20.4");

        // 1. Forge CapabilityItemHandler bridge
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/items/CapabilityItemHandler", "ITEM_HANDLER_CAPABILITY",
                "Lnet/minecraftforge/common/capabilities/Capability;",
                "com/kyroxova/continuumlib/shims/CapabilityShim", "resolveItemHandler",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_4, null, LoaderType.NEOFORGE,
                "CapabilityItemHandler.ITEM_HANDLER_CAPABILITY -> CapabilityShim.resolveItemHandler"
        ));
    }
}
