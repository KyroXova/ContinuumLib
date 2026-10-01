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

            // Attempt 1: Modern 1.19.3+ / 26.3+ SoundEvent.createVariableRangeEvent(loc)
            for (Method m : soundClass.getMethods()) {
                if ("createVariableRangeEvent".equals(m.getName()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(resourceLocation)) {
                    return m.invoke(null, resourceLocation);
                }
            }

            // Attempt 2: Legacy Constructor new SoundEvent(loc)
            for (Constructor<?> ctor : soundClass.getConstructors()) {
                if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(resourceLocation)) {
                    return ctor.newInstance(resourceLocation);
                }
            }

            // Attempt 3: Record Constructor new SoundEvent(loc, Optional.empty())
            for (Constructor<?> ctor : soundClass.getConstructors()) {
                if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0].isInstance(resourceLocation)
                        && ctor.getParameterTypes()[1].equals(java.util.Optional.class)) {
                    return ctor.newInstance(resourceLocation, java.util.Optional.empty());
                }
            }

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

            // Attempt 1: Modern SoundEvent.createFixedRangeEvent(loc, float)
            for (Method m : soundClass.getMethods()) {
                if ("createFixedRangeEvent".equals(m.getName()) && m.getParameterCount() == 2
                        && m.getParameterTypes()[0].isInstance(resourceLocation)
                        && m.getParameterTypes()[1] == float.class) {
                    return m.invoke(null, resourceLocation, range);
                }
            }

            // Attempt 2: Legacy Constructor new SoundEvent(loc, float)
            for (Constructor<?> ctor : soundClass.getConstructors()) {
                if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0].isInstance(resourceLocation)
                        && ctor.getParameterTypes()[1] == float.class) {
                    return ctor.newInstance(resourceLocation, range);
                }
            }

            // Fallback: standard create
            return create(resourceLocation);
        } catch (Throwable t) {
            LOGGER.fine("[SoundEventShim] Error creating fixed range SoundEvent: " + t.getMessage());
            return null;
        }
    }
}
