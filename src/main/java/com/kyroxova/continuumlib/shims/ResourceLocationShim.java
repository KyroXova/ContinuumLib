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

    public static Object create(String namespace, String path) {
        String normalizedPath = normalizePath(path);
        try {
            Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");

            // Attempt 1: Modern 1.21+ ResourceLocation.fromNamespaceAndPath(...)
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

        } catch (Throwable t) {
            LOGGER.fine("[ResourceLocationShim] Error creating ResourceLocation: " + t.getMessage());
        }
        return namespace + ":" + normalizedPath;
    }

    /**
     * Normalizes plural/singular path variations (e.g., recipes/ -> recipe/)
     */
    private static String normalizePath(String path) {
        if (path == null) return null;
        // In 1.21+, recipe/ is used instead of recipes/
        if (path.startsWith("recipes/")) {
            return "recipe/" + path.substring(8);
        }
        if (path.startsWith("loot_tables/")) {
            return "loot_table/" + path.substring(12);
        }
        if (path.startsWith("structures/")) {
            return "structure/" + path.substring(11);
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
}
