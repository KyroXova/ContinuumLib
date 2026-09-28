package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.environment.MCVersion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalizes datapack and resourcepack paths between Minecraft version epochs.
 * Handles the major Minecraft 1.21+ folder naming transition where Mojang changed plural folders to singular:
 * - recipes/ <-> recipe/
 * - loot_tables/ <-> loot_table/
 * - structures/ <-> structure/
 * - advancements/ <-> advancement/
 * - predicates/ <-> predicate/
 * - item_modifiers/ <-> item_modifier/
 * - tags/blocks/ <-> tags/block/
 * - tags/items/ <-> tags/item/
 * - tags/entity_types/ <-> tags/entity_type/
 * - tags/fluids/ <-> tags/fluid/
 */
public final class DataPackResourcePathNormalizer {

    private static final Map<String, String> PLURAL_TO_SINGULAR = new LinkedHashMap<>();
    private static final Map<String, String> SINGULAR_TO_PLURAL = new LinkedHashMap<>();

    static {
        registerMapping("recipes", "recipe");
        registerMapping("loot_tables", "loot_table");
        registerMapping("structures", "structure");
        registerMapping("advancements", "advancement");
        registerMapping("predicates", "predicate");
        registerMapping("item_modifiers", "item_modifier");
        registerMapping("tags/blocks", "tags/block");
        registerMapping("tags/items", "tags/item");
        registerMapping("tags/entity_types", "tags/entity_type");
        registerMapping("tags/fluids", "tags/fluid");
        registerMapping("tags/game_events", "tags/game_event");
    }

    private static void registerMapping(String plural, String singular) {
        PLURAL_TO_SINGULAR.put(plural, singular);
        SINGULAR_TO_PLURAL.put(singular, plural);
    }

    private DataPackResourcePathNormalizer() {}

    /**
     * Normalizes an entry path (e.g. data/modid/recipes/my_recipe.json) for the given target version.
     *
     * @param entryPath zip/jar entry path
     * @param targetVersion the version the jar is being built for
     * @return transformed path with correct plural or singular folder naming
     */
    public static String normalizePath(String entryPath, MCVersion targetVersion) {
        if (entryPath == null || !entryPath.startsWith("data/")) {
            return entryPath;
        }

        boolean targetIsModernSingular = targetVersion.isAtLeast(MCVersion.of("1.21"));

        // Match pattern: data/<namespace>/<folder>/...
        String[] parts = entryPath.split("/", 4);
        if (parts.length < 3) {
            return entryPath;
        }

        String prefix = parts[0] + "/" + parts[1] + "/"; // e.g. data/mymod/
        String subPath = entryPath.substring(prefix.length()); // e.g. recipes/my_item.json or tags/blocks/ores.json

        if (targetIsModernSingular) {
            // Convert plural -> singular
            for (Map.Entry<String, String> mapping : PLURAL_TO_SINGULAR.entrySet()) {
                String plural = mapping.getKey();
                String singular = mapping.getValue();
                if (subPath.startsWith(plural + "/")) {
                    return prefix + singular + "/" + subPath.substring(plural.length() + 1);
                }
            }
        } else {
            // Target is <= 1.20.6: Convert singular -> plural
            for (Map.Entry<String, String> mapping : SINGULAR_TO_PLURAL.entrySet()) {
                String singular = mapping.getKey();
                String plural = mapping.getValue();
                if (subPath.startsWith(singular + "/")) {
                    return prefix + plural + "/" + subPath.substring(singular.length() + 1);
                }
            }
        }

        return entryPath;
    }

    /**
     * Normalizes resource location path string when querying recipes or tags dynamically.
     */
    public static String normalizeResourceLocationPath(String path, boolean targetIsModernSingular) {
        if (path == null) return null;

        if (targetIsModernSingular) {
            for (Map.Entry<String, String> mapping : PLURAL_TO_SINGULAR.entrySet()) {
                if (path.startsWith(mapping.getKey() + "/")) {
                    return mapping.getValue() + "/" + path.substring(mapping.getKey().length() + 1);
                }
            }
        } else {
            for (Map.Entry<String, String> mapping : SINGULAR_TO_PLURAL.entrySet()) {
                if (path.startsWith(mapping.getKey() + "/")) {
                    return mapping.getValue() + "/" + path.substring(mapping.getKey().length() + 1);
                }
            }
        }
        return path;
    }
}
