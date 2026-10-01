package com.kyroxova.continuumlib.filter.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Categorized, extensible Minecraft registry element types.
 * Not hardcoded to any single Minecraft version.
 */
public record RegistryType(String name) {
    public static final RegistryType BLOCK = new RegistryType("block");
    public static final RegistryType ITEM = new RegistryType("item");
    public static final RegistryType BLOCK_ENTITY = new RegistryType("block_entity");
    public static final RegistryType ENTITY_TYPE = new RegistryType("entity_type");
    public static final RegistryType FLUID = new RegistryType("fluid");
    public static final RegistryType MENU = new RegistryType("menu");
    public static final RegistryType SOUND_EVENT = new RegistryType("sound_event");
    public static final RegistryType PARTICLE = new RegistryType("particle");
    public static final RegistryType RECIPE_TYPE = new RegistryType("recipe_type");
    public static final RegistryType RECIPE_SERIALIZER = new RegistryType("recipe_serializer");
    public static final RegistryType ENCHANTMENT = new RegistryType("enchantment");
    public static final RegistryType EFFECT = new RegistryType("effect");
    public static final RegistryType ATTRIBUTE = new RegistryType("attribute");
    public static final RegistryType CUSTOM_REGISTRY_ENTRY = new RegistryType("custom_registry_entry");

    public RegistryType {
        Objects.requireNonNull(name, "name");
        name = name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Registry type name cannot be blank");
        }
    }

    public static RegistryType from(String value) {
        return new RegistryType(value);
    }

    @Override
    public String toString() {
        return name;
    }
}
