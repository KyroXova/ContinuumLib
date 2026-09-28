package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Minecraft Text & Chat Components.
 * Bridges legacy TranslatableComponent and TextComponent calls (used up through 1.18.2)
 * to modern 1.19+ Component.translatable(...) and Component.literal(...) methods.
 */
public final class ComponentShim {

    private static final Logger LOGGER = Logger.getLogger(ComponentShim.class.getName());

    private ComponentShim() {}

    /**
     * Intercepts: new TranslatableComponent(String key, Object... args)
     */
    public static Object translatable(String key, Object... args) {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            if (args != null && args.length > 0) {
                Method translatableWithArgs = componentClass.getMethod("translatable", String.class, Object[].class);
                return translatableWithArgs.invoke(null, key, args);
            } else {
                Method translatableSimple = componentClass.getMethod("translatable", String.class);
                return translatableSimple.invoke(null, key);
            }
        } catch (Throwable t) {
            // Fallback for legacy runtime where TranslatableComponent still exists
            try {
                Class<?> legacyClass = Class.forName("net.minecraft.network.chat.TranslatableComponent");
                if (args != null && args.length > 0) {
                    return legacyClass.getConstructor(String.class, Object[].class).newInstance(key, args);
                } else {
                    return legacyClass.getConstructor(String.class).newInstance(key);
                }
            } catch (Throwable ignored) {
                return key;
            }
        }
    }

    /**
     * Intercepts: new TextComponent(String text)
     */
    public static Object literal(String text) {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method literalMethod = componentClass.getMethod("literal", String.class);
            return literalMethod.invoke(null, text);
        } catch (Throwable t) {
            try {
                Class<?> legacyClass = Class.forName("net.minecraft.network.chat.TextComponent");
                return legacyClass.getConstructor(String.class).newInstance(text);
            } catch (Throwable ignored) {
                return text;
            }
        }
    }
}
