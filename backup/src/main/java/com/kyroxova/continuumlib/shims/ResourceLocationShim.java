package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for ResourceLocation / Identifier creation.
 * Bridges new ResourceLocation(namespace, path) across 1.7.9 -> 26.3+.
 * On 1.21+, the constructor was restricted in favor of ResourceLocation.fromNamespaceAndPath(...)
 * and ResourceLocation.parse(...).
 */
public final class ResourceLocationShim {

    private static final Logger LOGGER = Logger.getLogger(ResourceLocationShim.class.getName());

    private ResourceLocationShim() {}

    public static Object create(String location) {
        if (location == null) return null;
        int colon = location.indexOf(':');
        if (colon != -1) {
            return create(location.substring(0, colon), location.substring(colon + 1));
        } else {
            return create("minecraft", location);
        }
    }

    public static Object create(String namespace, String path) {
        String normalizedPath = normalizePath(path);
        try {
            Class<?> rlClass = null;
            try {
                rlClass = Class.forName("net.minecraft.resources.Identifier");
            } catch (ClassNotFoundException ignored) {
                try {
                    rlClass = Class.forName("net.minecraft.resources.ResourceLocation");
                } catch (ClassNotFoundException ignored2) {}
            }
            if (rlClass != null) {
                // Attempt 1: Modern 1.21+ / 26.3+ fromNamespaceAndPath(...)
                try {
                    Method modernFactory = rlClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                    return modernFactory.invoke(null, namespace, normalizedPath);
                } catch (NoSuchMethodException ignored) {}

                // Attempt 2: Standard Constructor(String, String)
                try {
                    Constructor<?> ctor = rlClass.getConstructor(String.class, String.class);
                    return ctor.newInstance(namespace, normalizedPath);
                } catch (NoSuchMethodException ignored) {}

                // Attempt 3: Single string parse(String)
                try {
                    Method parseMethod = rlClass.getMethod("parse", String.class);
                    return parseMethod.invoke(null, namespace + ":" + normalizedPath);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable t) {
            LOGGER.fine("[ResourceLocationShim] Error creating ResourceLocation: " + t.getMessage());
        }
        return namespace + ":" + normalizedPath;
    }

    /**
     * Normalizes plural/singular path variations between 1.20.6 and 1.21+.
     */
    public static String normalizePath(String path) {
        if (path == null) return null;
        if (path.startsWith("recipes/")) {
            return "recipe/" + path.substring(8);
        }
        if (path.startsWith("loot_tables/")) {
            return "loot_table/" + path.substring(12);
        }
        if (path.startsWith("structures/")) {
            return "structure/" + path.substring(11);
        }
        if (path.startsWith("advancements/")) {
            return "advancement/" + path.substring(13);
        }
        if (path.startsWith("tags/blocks/")) {
            return "tags/block/" + path.substring(12);
        }
        if (path.startsWith("tags/items/")) {
            return "tags/item/" + path.substring(11);
        }
        if (path.startsWith("tags/fluids/")) {
            return "tags/fluid/" + path.substring(12);
        }
        if (path.startsWith("tags/entity_types/")) {
            return "tags/entity_type/" + path.substring(18);
        }
        if (path.startsWith("tags/game_events/")) {
            return "tags/game_event/" + path.substring(17);
        }
        return path;
    }

    /**
     * Denormalizes singular paths back to legacy plural paths for <= 1.20.6 targets.
     */
    public static String denormalizePath(String path) {
        if (path == null) return null;
        if (path.startsWith("recipe/")) {
            return "recipes/" + path.substring(7);
        }
        if (path.startsWith("loot_table/")) {
            return "loot_tables/" + path.substring(11);
        }
        if (path.startsWith("structure/")) {
            return "structures/" + path.substring(10);
        }
        if (path.startsWith("advancement/")) {
            return "advancements/" + path.substring(12);
        }
        if (path.startsWith("tags/block/")) {
            return "tags/blocks/" + path.substring(11);
        }
        if (path.startsWith("tags/item/")) {
            return "tags/items/" + path.substring(10);
        }
        if (path.startsWith("tags/fluid/")) {
            return "tags/fluids/" + path.substring(11);
        }
        if (path.startsWith("tags/entity_type/")) {
            return "tags/entity_types/" + path.substring(17);
        }
        if (path.startsWith("tags/game_event/")) {
            return "tags/game_events/" + path.substring(16);
        }
        return path;
    }

    public static Object parse(String location) {
        if (location == null) return null;
        int colon = location.indexOf(':');
        if (colon >= 0) {
            return create(location.substring(0, colon), location.substring(colon + 1));
        } else {
            return create("minecraft", location);
        }
    }

    public static String getNormalizedPath(Object resourceLocation) {
        if (resourceLocation == null) return "";
        try {
            Method getPath = resourceLocation.getClass().getMethod("getPath");
            String path = (String) getPath.invoke(resourceLocation);
            return normalizePath(path);
        } catch (Throwable t) {
            return resourceLocation.toString();
        }
    }

    public static String getDenormalizedPath(Object resourceLocation) {
        if (resourceLocation == null) return "";
        try {
            Method getPath = resourceLocation.getClass().getMethod("getPath");
            String path = (String) getPath.invoke(resourceLocation);
            return denormalizePath(path);
        } catch (Throwable t) {
            return resourceLocation.toString();
        }
    }
}
