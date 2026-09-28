package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Enchantments, LootContext, and Data Generation.
 */
public final class EnchantmentAndLootRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_0 = MCVersion.of("1.20");
        MCVersion v1_21_0 = MCVersion.of("1.21");

        // 1. LootContext.Builder -> LootParams.Builder (1.20+)
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/level/storage/loot/LootContext$Builder",
                "net/minecraft/world/level/storage/loot/LootParams$Builder",
                v1_20_0, null, null,
                "LootContext.Builder -> LootParams.Builder"
        ));

        // 2. EnchantmentHelper.getItemEnchantmentLevel -> EnchantmentShim.getItemEnchantmentLevel
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/enchantment/EnchantmentHelper", "getItemEnchantmentLevel",
                "(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I",
                "com/kyroxova/continuumlib/shims/EnchantmentShim", "getItemEnchantmentLevel",
                "(Ljava/lang/Object;Ljava/lang/Object;)I",
                v1_21_0, null, null,
                "EnchantmentHelper.getItemEnchantmentLevel polyfill for data-driven enchantments"
        ));
    }
}
