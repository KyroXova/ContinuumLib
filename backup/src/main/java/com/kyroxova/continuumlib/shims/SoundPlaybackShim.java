package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Level.playSound across 1.7.9 -> 26.3+.
 * Bridges SoundCategory (<= 1.16.5) and SoundSource (1.17+ / 26.3+), ensuring valid playback dispatch.
 */
public final class SoundPlaybackShim {

    private static final Logger LOGGER = Logger.getLogger(SoundPlaybackShim.class.getName());

    public enum VirtualSoundSource {
        MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE, UI
    }

    private SoundPlaybackShim() {}

    /**
     * Dispatches playSound across versions, adapting between SoundCategory and SoundSource.
     */
    public static boolean playSound(Object level, Object entityOrPlayer, Object pos, Object soundEvent, Object sourceOrCategory, float volume, float pitch) {
        if (level == null || soundEvent == null) return false;

        Object adaptedSource = resolveSoundSource(sourceOrCategory);
        Object directSound = unwrapHolder(soundEvent);

        for (Method m : getAllMethods(level.getClass())) {
            if ("playSound".equals(m.getName())) {
                try {
                    m.setAccessible(true);
                    int pc = m.getParameterCount();
                    if (pc == 6) {
                        Class<?>[] pTypes = m.getParameterTypes();
                        Object targetSound = pTypes[2].isInstance(soundEvent) ? soundEvent : directSound;
                        Object targetSource = adaptToParamType(pTypes[3], adaptedSource, sourceOrCategory);
                        m.invoke(level, entityOrPlayer, pos, targetSound, targetSource, volume, pitch);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // Try double coordinates fallback if pos has x, y, z
        if (pos != null) {
            try {
                double x = 0, y = 0, z = 0;
                for (Method m : getAllMethods(pos.getClass())) {
                    if ("getX".equals(m.getName()) && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        x = ((Number) m.invoke(pos)).doubleValue();
                    } else if ("getY".equals(m.getName()) && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        y = ((Number) m.invoke(pos)).doubleValue();
                    } else if ("getZ".equals(m.getName()) && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        z = ((Number) m.invoke(pos)).doubleValue();
                    }
                }
                return playSound(level, entityOrPlayer, x, y, z, soundEvent, sourceOrCategory, volume, pitch);
            } catch (Throwable ignored) {}
        }

        return false;
    }

    public static boolean playSound(Object level, Object entityOrPlayer, double x, double y, double z, Object soundEvent, Object sourceOrCategory, float volume, float pitch) {
        if (level == null || soundEvent == null) return false;

        Object adaptedSource = resolveSoundSource(sourceOrCategory);
        Object directSound = unwrapHolder(soundEvent);

        for (Method m : getAllMethods(level.getClass())) {
            if ("playSound".equals(m.getName())) {
                try {
                    m.setAccessible(true);
                    int pc = m.getParameterCount();
                    if (pc == 8) {
                        Class<?>[] pTypes = m.getParameterTypes();
                        Object targetSound = pTypes[4].isInstance(soundEvent) ? soundEvent : directSound;
                        Object targetSource = adaptToParamType(pTypes[5], adaptedSource, sourceOrCategory);
                        m.invoke(level, entityOrPlayer, x, y, z, targetSound, targetSource, volume, pitch);
                        return true;
                    } else if (pc == 7) {
                        Class<?>[] pTypes = m.getParameterTypes();
                        Object targetSound = pTypes[3].isInstance(soundEvent) ? soundEvent : directSound;
                        Object targetSource = adaptToParamType(pTypes[4], adaptedSource, sourceOrCategory);
                        m.invoke(level, x, y, z, targetSound, targetSource, volume, pitch);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static Object adaptToParamType(Class<?> paramType, Object adaptedSource, Object original) {
        if (paramType == null || paramType.equals(Object.class)) return adaptedSource;
        if (paramType.isInstance(adaptedSource)) return adaptedSource;
        if (paramType.isInstance(original)) return original;
        if (paramType.isEnum()) {
            String name = getCategoryName(original);
            for (Object constant : paramType.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
        }
        return adaptedSource;
    }

    /**
     * Resolves an input category or source into the appropriate runtime SoundSource or SoundCategory enum.
     */
    public static Object resolveSoundSource(Object sourceOrCategory) {
        if (sourceOrCategory == null) return VirtualSoundSource.MASTER;

        String name = (sourceOrCategory instanceof Enum<?> e) ? e.name() : sourceOrCategory.toString();

        // 1. Try modern net.minecraft.sounds.SoundSource
        try {
            Class<?> sourceClass = Class.forName("net.minecraft.sounds.SoundSource");
            for (Object constant : sourceClass.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy net.minecraft.util.SoundCategory
        try {
            Class<?> catClass = Class.forName("net.minecraft.util.SoundCategory");
            for (Object constant : catClass.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fallback: VirtualSoundSource
        for (VirtualSoundSource v : VirtualSoundSource.values()) {
            if (v.name().equalsIgnoreCase(name)) {
                return v;
            }
        }

        return sourceOrCategory;
    }

    private static Object unwrapHolder(Object holderOrObject) {
        if (holderOrObject == null) return null;
        if (holderOrObject instanceof String || holderOrObject instanceof Number || holderOrObject instanceof Boolean) {
            return holderOrObject;
        }
        if (!holderOrObject.getClass().getName().contains("Holder")) {
            return holderOrObject;
        }
        try {
            for (Method m : getAllMethods(holderOrObject.getClass())) {
                if ("value".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(holderOrObject);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return holderOrObject;
    }

    private static Method[] getAllMethods(Class<?> clazz) {
        Method[] publicMethods = clazz.getMethods();
        Method[] declaredMethods = clazz.getDeclaredMethods();
        Method[] all = new Method[publicMethods.length + declaredMethods.length];
        System.arraycopy(publicMethods, 0, all, 0, publicMethods.length);
        System.arraycopy(declaredMethods, 0, all, publicMethods.length, declaredMethods.length);
        return all;
    }

    public static String getCategoryName(Object sourceOrCategory) {
        if (sourceOrCategory == null) return "MASTER";
        if (sourceOrCategory instanceof Enum<?> e) return e.name();
        return sourceOrCategory.toString();
    }

    public static Object createSoundEvent(Object loc) {
        if (loc == null) return null;
        try {
            Class<?> seClass = Class.forName("net.minecraft.sounds.SoundEvent");
            try {
                Method factory = seClass.getMethod("createVariableRangeEvent", loc.getClass());
                return factory.invoke(null, loc);
            } catch (NoSuchMethodException ignored) {}

            for (Method m : seClass.getMethods()) {
                if ("createVariableRangeEvent".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        return m.invoke(null, loc);
                    } catch (Throwable ignored) {}
                }
            }

            for (java.lang.reflect.Constructor<?> ctor : seClass.getConstructors()) {
                if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isInstance(loc)) {
                    return ctor.newInstance(loc);
                }
            }

            for (java.lang.reflect.Constructor<?> ctor : seClass.getConstructors()) {
                if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0].isInstance(loc)
                        && ctor.getParameterTypes()[1].equals(java.util.Optional.class)) {
                    return ctor.newInstance(loc, java.util.Optional.empty());
                }
            }
        } catch (Throwable t) {
            LOGGER.warning("[SoundPlaybackShim] Failed to create SoundEvent: " + t.getMessage());
        }
        return null;
    }
}
