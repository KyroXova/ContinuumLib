package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill Shim for DataComponents across Minecraft versions (1.20.5 -> 26.3+).
 * Safely resolves DataComponentType instances added in 1.21.2+ / 26.3:
 * - ITEM_MODEL
 * - CONSUMABLE
 * - EQUIPPABLE
 * - GLIDER
 * - TOOLTIP_STYLE
 * - WEAPON
 * - ATTACK_RANGE
 *
 * If running on a modern version where the static field exists, reflects and returns it.
 * If running on an earlier version, provides a synthetic component type that integrates
 * seamlessly with ItemStackShim.
 */
public final class DataComponentShim {

    private static final Logger LOGGER = Logger.getLogger(DataComponentShim.class.getName());
    private static final Map<String, Object> SYNTHETIC_CACHE = new ConcurrentHashMap<>();

    private DataComponentShim() {}

    public static Object itemModel() {
        return resolveComponent("ITEM_MODEL", "minecraft:item_model");
    }

    public static Object consumable() {
        return resolveComponent("CONSUMABLE", "minecraft:consumable");
    }

    public static Object equippable() {
        return resolveComponent("EQUIPPABLE", "minecraft:equippable");
    }

    public static Object glider() {
        return resolveComponent("GLIDER", "minecraft:glider");
    }

    public static Object tooltipStyle() {
        return resolveComponent("TOOLTIP_STYLE", "minecraft:tooltip_style");
    }

    public static Object weapon() {
        return resolveComponent("WEAPON", "minecraft:weapon");
    }

    public static Object attackRange() {
        return resolveComponent("ATTACK_RANGE", "minecraft:attack_range");
    }

    public static Object resolveComponent(String fieldName, String fallbackKey) {
        try {
            Class<?> dcClass = Class.forName("net.minecraft.core.component.DataComponents");
            Field f = dcClass.getField(fieldName);
            f.setAccessible(true);
            Object val = f.get(null);
            if (val != null) return val;
        } catch (Throwable ignored) {}

        // Fallback synthetic component type with consistent key
        return SYNTHETIC_CACHE.computeIfAbsent(fieldName, k -> new SyntheticComponentType(fallbackKey));
    }

    /**
     * Synthetic placeholder for DataComponentType when running on older targets.
     */
    public static final class SyntheticComponentType {
        private final String key;

        public SyntheticComponentType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        @Override
        public String toString() {
            return key;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (obj instanceof SyntheticComponentType other) {
                return key.equals(other.key);
            }
            return key.equals(obj.toString());
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
