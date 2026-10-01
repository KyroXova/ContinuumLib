package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Universal Shim for Particle systems across 1.7.9 -> 26.3+.
 * Bridges Level/World.addParticle, ServerLevel/ServerWorld.sendParticles/spawnParticle,
 * and ParticleOptions / ParticleType cross-version definitions.
 */
public final class ParticleShim {

    private static final Logger LOGGER = Logger.getLogger(ParticleShim.class.getName());

    private static final Map<String, Object> REGISTERED_PARTICLE_TYPES = new ConcurrentHashMap<>();
    private static final AtomicLong SPAWNED_PARTICLE_COUNT = new AtomicLong(0);

    private ParticleShim() {}

    /**
     * Client-side particle spawning shim (Level.addParticle).
     */
    public static void addParticle(Object level, Object particleOptions, double x, double y, double z,
                                   double xSpeed, double ySpeed, double zSpeed) {
        addParticle(level, particleOptions, false, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    public static void spawnParticle(Object level, Object particleOptions, double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed) {
        addParticle(level, particleOptions, false, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * Client-side particle spawning shim with overrideLimiter flag.
     */
    public static void addParticle(Object level, Object particleOptions, boolean overrideLimiter,
                                   double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        if (level == null || particleOptions == null) return;
        SPAWNED_PARTICLE_COUNT.incrementAndGet();

        Object resolved = resolveParticle(particleOptions);
        if (resolved == null) resolved = particleOptions;

        // 1. Try modern addParticle(ParticleOptions, boolean, double, double, double, double, double, double)
        try {
            for (Method m : level.getClass().getMethods()) {
                if ("addParticle".equals(m.getName()) && m.getParameterCount() == 8) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(level, particleOptions, overrideLimiter, x, y, z, xSpeed, ySpeed, zSpeed);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try standard addParticle(ParticleOptions, double, double, double, double, double, double)
        try {
            for (Method m : level.getClass().getMethods()) {
                if ("addParticle".equals(m.getName()) && m.getParameterCount() == 7) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(level, particleOptions, x, y, z, xSpeed, ySpeed, zSpeed);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try legacy World.spawnParticle
        try {
            for (Method m : level.getClass().getMethods()) {
                if ("spawnParticle".equals(m.getName()) && m.getParameterCount() >= 7) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(level, particleOptions, x, y, z, xSpeed, ySpeed, zSpeed);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ParticleShim] Error spawning particle: " + t.getMessage());
        }
    }

    public static Object resolveParticle(Object particle) {
        if (particle == null) return null;
        if (!(particle instanceof String name)) {
            return particle;
        }

        Object registered = getParticleType(name);
        if (registered != null) return registered;

        String cleanName = name.trim();
        String upperName = cleanName.toUpperCase(Locale.ROOT).replace(':', '_').replace('.', '_').replace('/', '_');

        // 1. Try modern ParticleTypes static fields
        try {
            Class<?> ptClass = Class.forName("net.minecraft.core.particles.ParticleTypes");
            for (Field f : ptClass.getFields()) {
                if (f.getName().equalsIgnoreCase(upperName) || f.getName().equalsIgnoreCase(cleanName)) {
                    return f.get(null);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try BuiltInRegistries.PARTICLE_TYPE
        try {
            Class<?> birClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field ptField = birClass.getField("PARTICLE_TYPE");
            Object registry = ptField.get(null);
            Object rl = ResourceLocationShim.parse(cleanName);
            for (Method m : registry.getClass().getMethods()) {
                if ("get".equals(m.getName()) && m.getParameterCount() == 1) {
                    Object res = m.invoke(registry, rl);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try legacy EnumParticleTypes (<= 1.12.2)
        try {
            Class<?> eptClass = Class.forName("net.minecraft.util.EnumParticleTypes");
            Method getByName = eptClass.getMethod("getByName", String.class);
            return getByName.invoke(null, cleanName.toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {}

        return particle;
    }

    /**
     * Server-side particle spawning shim (ServerLevel.sendParticles).
     */
    public static int sendParticles(Object serverLevel, Object particleOptions, double x, double y, double z,
                                    int count, double xOffset, double yOffset, double zOffset, double speed) {
        if (serverLevel == null || particleOptions == null) return 0;
        SPAWNED_PARTICLE_COUNT.addAndGet(count);

        try {
            for (Method m : serverLevel.getClass().getMethods()) {
                if (("sendParticles".equals(m.getName()) || "spawnParticle".equals(m.getName()))) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    if (m.getParameterCount() == 9) {
                        Object res = m.invoke(serverLevel, particleOptions, x, y, z, count, xOffset, yOffset, zOffset, speed);
                        if (res instanceof Integer i) return i;
                        return count;
                    } else if (m.getParameterCount() == 10) {
                        Object res = m.invoke(serverLevel, particleOptions, false, x, y, z, count, xOffset, yOffset, zOffset, speed);
                        if (res instanceof Integer i) return i;
                        return count;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ParticleShim] Error sending particles: " + t.getMessage());
        }
        return count;
    }

    /**
     * Creates and registers a ParticleType across Minecraft versions.
     */
    public static Object createParticleType(String id, boolean overrideLimiter) {
        try {
            Class<?> simpleParticleClass = Class.forName("net.minecraft.core.particles.SimpleParticleType");
            Object pt = simpleParticleClass.getConstructor(boolean.class).newInstance(overrideLimiter);
            registerParticleType(id, pt);
            return pt;
        } catch (Throwable ignored) {}

        VirtualParticleType vpt = new VirtualParticleType(id, overrideLimiter);
        registerParticleType(id, vpt);
        return vpt;
    }

    public static Object createParticleType(String id) {
        return createParticleType(id, false);
    }

    public static void registerParticleType(String id, Object particleType) {
        if (id != null && particleType != null) {
            REGISTERED_PARTICLE_TYPES.put(id, particleType);
        }
    }

    public static Object getParticleType(String id) {
        return id != null ? REGISTERED_PARTICLE_TYPES.get(id) : null;
    }

    public static long getSpawnedParticleCount() {
        return SPAWNED_PARTICLE_COUNT.get();
    }

    public static void resetSpawnedParticleCount() {
        SPAWNED_PARTICLE_COUNT.set(0);
    }

    public static Map<String, Object> getRegisteredParticleTypes() {
        return Collections.unmodifiableMap(REGISTERED_PARTICLE_TYPES);
    }

    /**
     * Fallback standalone ParticleType representation for testing and non-MC environments.
     */
    public static class VirtualParticleType {
        private final String id;
        private final boolean overrideLimiter;

        public VirtualParticleType(String id, boolean overrideLimiter) {
            this.id = id;
            this.overrideLimiter = overrideLimiter;
        }

        public String getId() {
            return id;
        }

        public boolean isOverrideLimiter() {
            return overrideLimiter;
        }

        @Override
        public String toString() {
            return "VirtualParticleType[" + id + ", overrideLimiter=" + overrideLimiter + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VirtualParticleType that)) return false;
            return overrideLimiter == that.overrideLimiter && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, overrideLimiter);
        }
    }
}
