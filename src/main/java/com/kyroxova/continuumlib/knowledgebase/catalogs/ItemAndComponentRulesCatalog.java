package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Items, Properties, NBT, and Data Components.
 */
public final class ItemAndComponentRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_3 = MCVersion.of("1.19.3");
        MCVersion v1_20_5 = MCVersion.of("1.20.5");

        // 1. Creative Tab on Item.Properties
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/Item$Properties", "tab",
                "(Lnet/minecraft/world/item/CreativeModeTab;)Lnet/minecraft/world/item/Item$Properties;",
                "com/kyroxova/continuumlib/shims/CreativeTabShim", "tab",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_3, null, null,
                "Item.Properties.tab(CreativeModeTab) polyfill"
        ));

        // 2. ItemStack NBT -> Data Components (1.20.5+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "getTag",
                "()Lnet/minecraft/nbt/CompoundTag;",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "getTag",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_5, null, null,
                "ItemStack.getTag() -> ItemStackShim.getTag()"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "getOrCreateTag",
                "()Lnet/minecraft/nbt/CompoundTag;",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "getOrCreateTag",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_5, null, null,
                "ItemStack.getOrCreateTag() -> ItemStackShim.getOrCreateTag()"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "setTag",
                "(Lnet/minecraft/nbt/CompoundTag;)V",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "setTag",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_5, null, null,
                "ItemStack.setTag(CompoundTag) -> ItemStackShim.setTag()"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "hasTag",
                "()Z",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "hasTag",
                "(Ljava/lang/Object;)Z",
                v1_20_5, null, null,
                "ItemStack.hasTag() -> ItemStackShim.hasTag()"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "removeTagKey",
                "(Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "removeTagKey",
                "(Ljava/lang/Object;Ljava/lang/String;)V",
                v1_20_5, null, null,
                "ItemStack.removeTagKey(String) -> ItemStackShim.removeTagKey()"
        ));
    }
}
