package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Enchantments and EnchantmentHelper across 1.7.9 -> 26.3+.
 * In 1.21+, Enchantments shifted from static Java classes to data-driven registry keys.
 */
public final class EnchantmentShim {

    private static final Logger LOGGER = Logger.getLogger(EnchantmentShim.class.getName());

    private EnchantmentShim() {}

    /**
     * Intercepts: EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)
     */
    public static int getItemEnchantmentLevel(Object enchantmentOrHolder, Object itemStack) {
        if (enchantmentOrHolder == null || itemStack == null) return 0;

        try {
            Class<?> helperClass = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper");

            // Attempt 1: Legacy EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)
            try {
                Method method = helperClass.getMethod("getItemEnchantmentLevel",
                        Class.forName("net.minecraft.world.item.enchantment.Enchantment"),
                        Class.forName("net.minecraft.world.item.ItemStack"));
                return (int) method.invoke(null, enchantmentOrHolder, itemStack);
            } catch (NoSuchMethodException ignored) {}

            // Attempt 2: Modern 1.21+ EnchantmentHelper.getItemEnchantmentLevel(Holder<Enchantment>, ItemStack)
            try {
                Method method = helperClass.getMethod("getItemEnchantmentLevel",
                        Class.forName("net.minecraft.core.Holder"),
                        Class.forName("net.minecraft.world.item.ItemStack"));
                return (int) method.invoke(null, enchantmentOrHolder, itemStack);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[EnchantmentShim] Error querying enchantment level: " + t.getMessage());
        }
        return 0;
    }
}
