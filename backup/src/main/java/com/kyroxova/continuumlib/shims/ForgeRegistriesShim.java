package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;

/**
 * Universal Shim for ForgeRegistries when running on modern loaders (NeoForge 1.20.4+ / 26.x).
 */
public final class ForgeRegistriesShim {

    public static final Object BLOCKS = resolveRegistryKey("BLOCK");
    public static final Object ITEMS = resolveRegistryKey("ITEM");
    public static final Object BLOCK_ENTITIES = resolveRegistryKey("BLOCK_ENTITY_TYPE");
    public static final Object ENTITIES = resolveRegistryKey("ENTITY_TYPE");
    public static final Object ENTITY_TYPES = resolveRegistryKey("ENTITY_TYPE");
    public static final Object BLOCK_ENTITY_TYPES = resolveRegistryKey("BLOCK_ENTITY_TYPE");
    public static final Object ATTRIBUTES = resolveRegistryKey("ATTRIBUTE");
    public static final Object SOUND_EVENTS = resolveRegistryKey("SOUND_EVENT");
    public static final Object PARTICLE_TYPES = resolveRegistryKey("PARTICLE_TYPE");
    public static final Object CONTAINERS = resolveRegistryKey("MENU");
    public static final Object FLUIDS = resolveRegistryKey("FLUID");
    public static final Object RECIPE_SERIALIZERS = resolveRegistryKey("RECIPE_SERIALIZER");
    public static final Object CREATIVE_MODE_TABS = resolveRegistryKey("CREATIVE_MODE_TAB");

    private static Object resolveRegistryKey(String fieldName) {
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            Field f = registriesClass.getField(fieldName);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private ForgeRegistriesShim() {}
}
