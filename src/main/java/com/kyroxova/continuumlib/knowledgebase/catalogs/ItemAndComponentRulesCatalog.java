package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Items, Properties, NBT, and Data Components.
 * Bridges:
 * 1. Creative Mode Tabs across 1.12.2 -> 26.3+ (Item.Properties.tab and CreativeModeTab.builder).
 * 2. Bidirectional Data Components <-> NBT:
 *    - Legacy ItemStack NBT (getTag/setTag/getOrCreateTag/hasTag/removeTagKey) -> DataComponents.CUSTOM_DATA on 1.20.5+.
 *    - Modern ItemStack Data Components (get/set/has/remove) -> CompoundTag on pre-1.20.5 versions.
 */
public final class ItemAndComponentRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_2 = MCVersion.of("1.19.2");
        MCVersion v1_19_3 = MCVersion.of("1.19.3");
        MCVersion v1_20_4 = MCVersion.of("1.20.4");
        MCVersion v1_20_5 = MCVersion.of("1.20.5");

        // 1. Creative Tab on Item.Properties (1.19.3+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/Item$Properties", "tab",
                "(Lnet/minecraft/world/item/CreativeModeTab;)Lnet/minecraft/world/item/Item$Properties;",
                "com/kyroxova/continuumlib/shims/CreativeTabShim", "tab",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_3, null, null,
                "Item.Properties.tab(CreativeModeTab) polyfill"
        ));

        // 2. CreativeModeTab.builder() polyfill for pre-1.19.3 targets
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/CreativeModeTab", "builder",
                "()Lnet/minecraft/world/item/CreativeModeTab$Builder;",
                "com/kyroxova/continuumlib/shims/CreativeTabShim", "createBuilder",
                "()Ljava/lang/Object;",
                null, v1_19_2, null,
                "CreativeModeTab.builder() polyfill for pre-1.19.3"
        ));

        // 3. Legacy ItemStack NBT -> Modern Data Components (1.20.5+)
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

        // 4. Modern ItemStack Data Components -> Pre-1.20.5 NBT
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "get",
                "(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "get",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_4, null,
                "ItemStack.get(DataComponentType) -> ItemStackShim.get() for pre-1.20.5"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "set",
                "(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "set",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_4, null,
                "ItemStack.set(DataComponentType, Object) -> ItemStackShim.set() for pre-1.20.5"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "has",
                "(Lnet/minecraft/core/component/DataComponentType;)Z",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "has",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                null, v1_20_4, null,
                "ItemStack.has(DataComponentType) -> ItemStackShim.has() for pre-1.20.5"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/ItemStack", "remove",
                "(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/ItemStackShim", "remove",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_4, null,
                "ItemStack.remove(DataComponentType) -> ItemStackShim.remove() for pre-1.20.5"
        ));
    }
}
