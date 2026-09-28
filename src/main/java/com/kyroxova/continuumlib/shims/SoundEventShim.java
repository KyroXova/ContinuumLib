package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for SoundEvent creation.
 * In 1.19.3+, the new SoundEvent(ResourceLocation) constructor was made private,
 * replaced by static factory SoundEvent.createVariableRangeEvent(ResourceLocation).
 */
public final class SoundEventShim {

    private static final Logger LOGGER = Logger.getLogger(SoundEventShim.class.getName());

    private SoundEventShim() {}

    public static Object create(Object resourceLocation) {
        if (resourceLocation == null) return null;

        try {
            Class<?> soundClass = Class.forName("net.minecraft.sounds.SoundEvent");

            // Attempt 1: Modern 1.19.3+ SoundEvent.createVariableRangeEvent(ResourceLocation)
            try {
                Method createMethod = soundClass.getMethod("createVariableRangeEvent", Class.forName("net.minecraft.resources.ResourceLocation"));
                return createMethod.invoke(null, resourceLocation);
            } catch (NoSuchMethodException ignored) {}

            // Attempt 2: Legacy Constructor new SoundEvent(ResourceLocation)
            try {
                Constructor<?> ctor = soundClass.getConstructor(Class.forName("net.minecraft.resources.ResourceLocation"));
                return ctor.newInstance(resourceLocation);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[SoundEventShim] Error creating SoundEvent: " + t.getMessage());
        }
        return null;
    }

    public static Object createVariableRangeEvent(Object resourceLocation) {
        return create(resourceLocation);
    }

    public static Object createFixedRangeEvent(Object resourceLocation, float range) {
        if (resourceLocation == null) return null;
        try {
            Class<?> soundClass = Class.forName("net.minecraft.sounds.SoundEvent");

            // Attempt 1: Modern SoundEvent.createFixedRangeEvent(ResourceLocation, float)
            try {
                Method createFixed = soundClass.getMethod("createFixedRangeEvent", Class.forName("net.minecraft.resources.ResourceLocation"), float.class);
                return createFixed.invoke(null, resourceLocation, range);
            } catch (NoSuchMethodException ignored) {}

            // Attempt 2: Legacy Constructor new SoundEvent(ResourceLocation, float)
            try {
                Constructor<?> ctor = soundClass.getConstructor(Class.forName("net.minecraft.resources.ResourceLocation"), float.class);
                return ctor.newInstance(resourceLocation, range);
            } catch (NoSuchMethodException ignored) {}

            // Fallback: standard create
            return create(resourceLocation);
        } catch (Throwable t) {
            LOGGER.fine("[SoundEventShim] Error creating fixed range SoundEvent: " + t.getMessage());
            return null;
        }
    }
}
