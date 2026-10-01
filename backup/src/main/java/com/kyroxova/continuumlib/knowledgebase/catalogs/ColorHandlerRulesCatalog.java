package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for BlockColor, ItemColor, and Tinting Providers across Forge, NeoForge, and Fabric.
 */
public final class ColorHandlerRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        // 1. BlockColors.register polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/color/block/BlockColors", "register",
                "(Lnet/minecraft/client/color/block/BlockColor;[Lnet/minecraft/world/level/block/Block;)V",
                "com/kyroxova/continuumlib/shims/ColorHandlerShim", "registerBlockColor",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "BlockColors.register -> ColorHandlerShim.registerBlockColor"
        ));

        // 2. ItemColors.register polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/color/item/ItemColors", "register",
                "(Lnet/minecraft/client/color/item/ItemColor;[Lnet/minecraft/world/level/ItemLike;)V",
                "com/kyroxova/continuumlib/shims/ColorHandlerShim", "registerItemColor",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "ItemColors.register -> ColorHandlerShim.registerItemColor"
        ));

        // 3. RegisterColorHandlersEvent.Block.register polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/client/event/RegisterColorHandlersEvent$Block", "register",
                "(Lnet/minecraft/client/color/block/BlockColor;[Lnet/minecraft/world/level/block/Block;)V",
                "com/kyroxova/continuumlib/shims/ColorHandlerShim", "registerBlockColorFromEvent",
                "(Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)V",
                null, null, null,
                "RegisterColorHandlersEvent$Block.register -> ColorHandlerShim.registerBlockColorFromEvent"
        ));

        // 4. RegisterColorHandlersEvent.Item.register polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/client/event/RegisterColorHandlersEvent$Item", "register",
                "(Lnet/minecraft/client/color/item/ItemColor;[Lnet/minecraft/world/level/ItemLike;)V",
                "com/kyroxova/continuumlib/shims/ColorHandlerShim", "registerItemColorFromEvent",
                "(Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)V",
                null, null, null,
                "RegisterColorHandlersEvent$Item.register -> ColorHandlerShim.registerItemColorFromEvent"
        ));
    }
}
