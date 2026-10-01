package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for TagKey and Tag resolution across Minecraft versions.
 */
public final class TagShim {

    private static final Logger LOGGER = Logger.getLogger(TagShim.class.getName());

    private TagShim() {}

    public static Object createBlockTag(Object resourceLocation) {
        return createTag("net.minecraft.tags.BlockTags", resourceLocation);
    }

    public static Object createItemTag(Object resourceLocation) {
        return createTag("net.minecraft.tags.ItemTags", resourceLocation);
    }

    public static Object createFluidTag(Object resourceLocation) {
        return createTag("net.minecraft.tags.FluidTags", resourceLocation);
    }

    public static Object createEntityTypeTag(Object resourceLocation) {
        return createTag("net.minecraft.tags.EntityTypeTags", resourceLocation);
    }

    private static Object createTag(String tagHolderClassName, Object resourceLocation) {
        if (resourceLocation == null) return null;

        try {
            Class<?> tagClass = Class.forName(tagHolderClassName);

            // 1. Try create(ResourceLocation)
            try {
                Method createMethod = tagClass.getMethod("create", Class.forName("net.minecraft.resources.ResourceLocation"));
                return createMethod.invoke(null, resourceLocation);
            } catch (NoSuchMethodException ignored) {}

            // 2. Try bind(String)
            try {
                Method bindMethod = tagClass.getMethod("bind", String.class);
                return bindMethod.invoke(null, resourceLocation.toString());
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[TagShim] Error creating tag: " + t.getMessage());
        }
        return null;
    }
}
