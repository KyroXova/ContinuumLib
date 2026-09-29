package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Enchantments and EnchantmentHelper queries across 1.7.9 -> 26.3+.
 * Dynamically handles direct Enchantment instances, Holder<Enchantment> (1.20.5+ / 1.21+),
 * and ItemStack enchantment component tags.
 */
public final class EnchantmentShim {

    private static final Logger LOGGER = Logger.getLogger(EnchantmentShim.class.getName());

    private EnchantmentShim() {}

    /**
     * Retrieves the level of an enchantment on an ItemStack or LivingEntity.
     * Supports both direct Enchantment objects and modern Holder<Enchantment>.
     */
    public static int getEnchantmentLevel(Object enchantmentOrHolder, Object itemStackOrEntity) {
        if (enchantmentOrHolder == null || itemStackOrEntity == null) return 0;

        Object directEnchantment = unwrapHolder(enchantmentOrHolder);

        // 1. Try modern EnchantmentHelper.getItemEnchantmentLevel / getEnchantmentLevel
        try {
            Class<?> helperClass = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper");
            for (Method m : helperClass.getMethods()) {
                if (("getEnchantmentLevel".equals(m.getName()) || "getItemEnchantmentLevel".equals(m.getName()))
                        && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(null, enchantmentOrHolder, itemStackOrEntity);
                        if (res instanceof Integer lvl) return lvl;
                    } catch (Throwable ignored) {
                        if (directEnchantment != null && directEnchantment != enchantmentOrHolder) {
                            try {
                                Object res = m.invoke(null, directEnchantment, itemStackOrEntity);
                                if (res instanceof Integer lvl) return lvl;
                            } catch (Throwable ignored2) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try reading directly from mock or target reflection (e.g. target.getEnchantmentLevel(enchantment))
        try {
            for (Method m : getAllMethods(itemStackOrEntity.getClass())) {
                if ("getEnchantmentLevel".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        if (directEnchantment != null && directEnchantment != enchantmentOrHolder) {
                            try {
                                Object res = m.invoke(itemStackOrEntity, directEnchantment);
                                if (res instanceof Integer lvl && lvl > 0) return lvl;
                            } catch (Throwable ignored) {}
                        }
                        Object res = m.invoke(itemStackOrEntity, enchantmentOrHolder);
                        if (res instanceof Integer lvl && lvl > 0) return lvl;
                        if (directEnchantment != null && directEnchantment != enchantmentOrHolder) {
                            try {
                                Object res2 = m.invoke(itemStackOrEntity, directEnchantment);
                                if (res2 instanceof Integer lvl2) return lvl2;
                            } catch (Throwable ignored) {}
                        }
                        if (res instanceof Integer lvl) return lvl;
                    } catch (Throwable ignored) {
                        if (directEnchantment != null) {
                            try {
                                Object res = m.invoke(itemStackOrEntity, directEnchantment);
                                if (res instanceof Integer lvl) return lvl;
                            } catch (Throwable ignored2) {}
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return 0;
    }

    /**
     * Alias for getEnchantmentLevel to bridge EnchantmentHelper.getItemEnchantmentLevel.
     */
    public static int getItemEnchantmentLevel(Object enchantmentOrHolder, Object itemStackOrEntity) {
        return getEnchantmentLevel(enchantmentOrHolder, itemStackOrEntity);
    }

    /**
     * Checks if an ItemStack has the given enchantment (level > 0).
     */
    public static boolean hasEnchantment(Object enchantmentOrHolder, Object itemStack) {
        return getEnchantmentLevel(enchantmentOrHolder, itemStack) > 0;
    }

    /**
     * Unwraps a Holder<Enchantment> if the object is a Holder, or returns the object itself.
     */
    public static Object unwrapHolder(Object holderOrObject) {
        if (holderOrObject == null) return null;
        if (holderOrObject instanceof String || holderOrObject instanceof Number || holderOrObject instanceof Boolean) {
            return holderOrObject;
        }
        if (!isHolderClass(holderOrObject.getClass())) {
            return holderOrObject;
        }
        try {
            for (Method m : holderOrObject.getClass().getMethods()) {
                if ("value".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(holderOrObject);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return holderOrObject;
    }

    private static boolean isHolderClass(Class<?> clazz) {
        if (clazz == null) return false;
        if (clazz.getName().contains("Holder")) return true;
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.getName().contains("Holder")) return true;
        }
        return false;
    }

    private static Method[] getAllMethods(Class<?> clazz) {
        Method[] publicMethods = clazz.getMethods();
        Method[] declaredMethods = clazz.getDeclaredMethods();
        Method[] all = new Method[publicMethods.length + declaredMethods.length];
        System.arraycopy(publicMethods, 0, all, 0, publicMethods.length);
        System.arraycopy(declaredMethods, 0, all, publicMethods.length, declaredMethods.length);
        return all;
    }
}
