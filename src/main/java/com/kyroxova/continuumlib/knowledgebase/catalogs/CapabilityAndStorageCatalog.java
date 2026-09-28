package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.FieldRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Capabilities, Item Handlers, and Storage.
 * Bridges Forge CapabilityItemHandler across NeoForge Block Capabilities and Fabric Transfer API.
 */
public final class CapabilityAndStorageCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_4 = MCVersion.of("1.20.4");

        // 1. Forge CapabilityItemHandler polyfill (legacy compatibility)
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/items/CapabilityItemHandler", "ITEM_HANDLER_CAPABILITY",
                "Lnet/minecraftforge/common/capabilities/Capability;",
                "com/kyroxova/continuumlib/shims/CapabilityShim", "resolveItemHandler",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_4, null, LoaderType.NEOFORGE,
                "CapabilityItemHandler.ITEM_HANDLER_CAPABILITY -> CapabilityShim.resolveItemHandler"
        ));

        // 2. FieldRedirect: CapabilityItemHandler.ITEM_HANDLER_CAPABILITY -> Capabilities$ItemHandler.BLOCK (NeoForge)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraftforge/items/CapabilityItemHandler", "ITEM_HANDLER_CAPABILITY",
                "Lnet/minecraftforge/common/capabilities/Capability;",
                "net/neoforged/neoforge/capabilities/Capabilities$ItemHandler", "BLOCK",
                "Lnet/neoforged/neoforge/capabilities/BlockCapability;",
                v1_20_4, null, LoaderType.NEOFORGE,
                "CapabilityItemHandler.ITEM_HANDLER_CAPABILITY -> Capabilities.ItemHandler.BLOCK"
        ));

        // 3. FieldRedirect: CapabilityEnergy.ENERGY -> Capabilities$EnergyStorage.BLOCK (NeoForge)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraftforge/energy/CapabilityEnergy", "ENERGY",
                "Lnet/minecraftforge/common/capabilities/Capability;",
                "net/neoforged/neoforge/capabilities/Capabilities$EnergyStorage", "BLOCK",
                "Lnet/neoforged/neoforge/capabilities/BlockCapability;",
                v1_20_4, null, LoaderType.NEOFORGE,
                "CapabilityEnergy.ENERGY -> Capabilities.EnergyStorage.BLOCK"
        ));

        // 4. FieldRedirect: CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY -> Capabilities$FluidHandler.BLOCK (NeoForge)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraftforge/fluids/capability/CapabilityFluidHandler", "FLUID_HANDLER_CAPABILITY",
                "Lnet/minecraftforge/common/capabilities/Capability;",
                "net/neoforged/neoforge/capabilities/Capabilities$FluidHandler", "BLOCK",
                "Lnet/neoforged/neoforge/capabilities/BlockCapability;",
                v1_20_4, null, LoaderType.NEOFORGE,
                "CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY -> Capabilities.FluidHandler.BLOCK"
        ));

        // 5. ICapabilityProvider.getCapability polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/common/capabilities/ICapabilityProvider", "getCapability",
                "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;",
                "com/kyroxova/continuumlib/shims/CapabilityShim", "getCapability",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_4, null, null,
                "ICapabilityProvider.getCapability -> CapabilityShim.getCapability"
        ));
    }
}
