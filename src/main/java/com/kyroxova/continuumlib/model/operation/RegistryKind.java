package com.kyroxova.continuumlib.model.operation;

import java.util.Locale;

public enum RegistryKind {
    BLOCK,
    ITEM,
    BLOCK_ENTITY_TYPE,
    ENTITY_TYPE,
    FLUID,
    SOUND_EVENT,
    PARTICLE_TYPE,
    MENU_TYPE,
    RECIPE_SERIALIZER,
    CREATIVE_TAB,
    BIOME,
    CONFIGURED_FEATURE,
    PLACED_FEATURE,
    CUSTOM;

    public static RegistryKind fromForgeRegistryExpression(String expression) {
        String name = expression.substring(expression.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        return switch (name) {
            case "BLOCKS" -> BLOCK;
            case "ITEMS" -> ITEM;
            case "BLOCK_ENTITIES", "TILE_ENTITIES" -> BLOCK_ENTITY_TYPE;
            case "ENTITIES", "ENTITY_TYPES" -> ENTITY_TYPE;
            case "FLUIDS" -> FLUID;
            case "SOUND_EVENTS", "SOUNDS" -> SOUND_EVENT;
            case "PARTICLE_TYPES", "PARTICLES" -> PARTICLE_TYPE;
            case "CONTAINERS", "MENU_TYPES", "MENUS" -> MENU_TYPE;
            case "RECIPE_SERIALIZERS" -> RECIPE_SERIALIZER;
            case "CREATIVE_MODE_TABS" -> CREATIVE_TAB;
            case "BIOMES" -> BIOME;
            case "CONFIGURED_FEATURES" -> CONFIGURED_FEATURE;
            case "PLACED_FEATURES" -> PLACED_FEATURE;
            default -> CUSTOM;
        };
    }
}
