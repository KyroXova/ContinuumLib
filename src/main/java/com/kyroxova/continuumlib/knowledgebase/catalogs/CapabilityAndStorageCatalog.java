package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.FieldRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Capabilities, Item Handlers, Storage, and Data Attachments.
 * Bridges Forge ICapabilityProvider / LazyOptional across NeoForge Block/Item/Entity Capabilities,
 * NeoForge Data Attachments, and Fabric Transfer API.
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

        // 6. NeoForge Data Attachments: IAttachmentHolder.getData polyfill (for Forge / Fabric)
        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/attachment/IAttachmentHolder", "getData",
                null,
                "com/kyroxova/continuumlib/shims/CapabilityShim", "getDataAttachment",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "IAttachmentHolder.getData -> CapabilityShim.getDataAttachment (Fabric)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/attachment/IAttachmentHolder", "setData",
                null,
                "com/kyroxova/continuumlib/shims/CapabilityShim", "setDataAttachment",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "IAttachmentHolder.setData -> CapabilityShim.setDataAttachment (Fabric)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/attachment/IAttachmentHolder", "hasData",
                null,
                "com/kyroxova/continuumlib/shims/CapabilityShim", "hasDataAttachment",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                null, null, LoaderType.FABRIC,
                "IAttachmentHolder.hasData -> CapabilityShim.hasDataAttachment (Fabric)"
        ));

        // 7. NeoForge BlockCapability / ItemCapability / EntityCapability queries on Fabric/Forge
        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/capabilities/BlockCapability", "find",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/CapabilityShim", "findBlockCapability",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "BlockCapability.find -> CapabilityShim.findBlockCapability (Fabric)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/capabilities/ItemCapability", "find",
                "(Lnet/minecraft/world/item/ItemStack;Ljava/lang/Object;)Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/CapabilityShim", "findItemCapability",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "ItemCapability.find -> CapabilityShim.findItemCapability (Fabric)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/capabilities/EntityCapability", "find",
                "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/CapabilityShim", "findEntityCapability",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "EntityCapability.find -> CapabilityShim.findEntityCapability (Fabric)"
        ));
    }
}
