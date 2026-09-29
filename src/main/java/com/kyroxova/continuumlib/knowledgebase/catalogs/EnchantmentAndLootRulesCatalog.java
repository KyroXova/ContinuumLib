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

        // 3. EnchantmentHelper.getEnchantmentLevel -> EnchantmentShim.getEnchantmentLevel
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/enchantment/EnchantmentHelper", "getEnchantmentLevel",
                "(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I",
                "com/kyroxova/continuumlib/shims/EnchantmentShim", "getEnchantmentLevel",
                "(Ljava/lang/Object;Ljava/lang/Object;)I",
                null, null, null,
                "EnchantmentHelper.getEnchantmentLevel polyfill"
        ));

        // 4. EnchantmentHelper.hasEnchantment -> EnchantmentShim.hasEnchantment
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/enchantment/EnchantmentHelper", "hasEnchantment",
                "(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)Z",
                "com/kyroxova/continuumlib/shims/EnchantmentShim", "hasEnchantment",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                null, null, null,
                "EnchantmentHelper.hasEnchantment polyfill"
        ));

        // 5. EnchantmentHelper.getEnchantmentLevel(Enchantment, LivingEntity) -> EnchantmentShim.getEnchantmentLevel
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/enchantment/EnchantmentHelper", "getEnchantmentLevel",
                "(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/entity/LivingEntity;)I",
                "com/kyroxova/continuumlib/shims/EnchantmentShim", "getEnchantmentLevel",
                "(Ljava/lang/Object;Ljava/lang/Object;)I",
                null, null, null,
                "EnchantmentHelper.getEnchantmentLevel(LivingEntity) polyfill"
        ));
    }
}
