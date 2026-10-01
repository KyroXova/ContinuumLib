package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for DamageSource across Minecraft 1.7.9 -> 26.3+.
 * In 1.19.4 and earlier, DamageSource had static fields (GENERIC, MAGIC, FALL, OUT_OF_WORLD)
 * and static factory methods (DamageSource.playerAttack(player)).
 * In 1.20+, DamageSource was completely overhauled into data-driven DamageTypes accessed via level.damageSources().
 */
public final class DamageSourceShim {

    private static final Logger LOGGER = Logger.getLogger(DamageSourceShim.class.getName());

    private DamageSourceShim() {}

    public static Object generic() {
        return resolveDamageSource("generic", "GENERIC");
    }

    public static Object magic() {
        return resolveDamageSource("magic", "MAGIC");
    }

    public static Object fall() {
        return resolveDamageSource("fall", "FALL");
    }

    public static Object outOfWorld() {
        return resolveDamageSource("outOfWorld", "OUT_OF_WORLD");
    }

    public static Object inFire() {
        return resolveDamageSource("inFire", "IN_FIRE");
    }

    public static Object onFire() {
        return resolveDamageSource("onFire", "ON_FIRE");
    }

    public static Object lightningBolt() {
        return resolveDamageSource("lightningBolt", "LIGHTNING_BOLT");
    }

    public static Object lava() {
        return resolveDamageSource("lava", "LAVA");
    }

    public static Object hotFloor() {
        return resolveDamageSource("hotFloor", "HOT_FLOOR");
    }

    public static Object inWall() {
        return resolveDamageSource("inWall", "IN_WALL");
    }

    public static Object cramming() {
        return resolveDamageSource("cramming", "CRAMMING");
    }

    public static Object drown() {
        return resolveDamageSource("drown", "DROWN");
    }

    public static Object starve() {
        return resolveDamageSource("starve", "STARVE");
    }

    public static Object cactus() {
        return resolveDamageSource("cactus", "CACTUS");
    }

    public static Object flyIntoWall() {
        return resolveDamageSource("flyIntoWall", "FLY_INTO_WALL");
    }

    public static Object wither() {
        return resolveDamageSource("wither", "WITHER");
    }

    public static Object anvil() {
        return resolveDamageSource("anvil", "ANVIL");
    }

    public static Object fallingBlock() {
        return resolveDamageSource("fallingBlock", "FALLING_BLOCK");
    }

    public static Object dragonBreath() {
        return resolveDamageSource("dragonBreath", "DRAGON_BREATH");
    }

    public static Object dryOut() {
        return resolveDamageSource("dryOut", "DRY_OUT");
    }

    public static Object sweetBerryBush() {
        return resolveDamageSource("sweetBerryBush", "SWEET_BERRY_BUSH");
    }

    public static Object freeze() {
        return resolveDamageSource("freeze", "FREEZE");
    }

    public static Object fallingStalactite() {
        return resolveDamageSource("fallingStalactite", "FALLING_STALACTITE");
    }

    public static Object stalagmite() {
        return resolveDamageSource("stalagmite", "STALAGMITE");
    }

    public static Object playerAttack(Object player) {
        if (player == null) return generic();

        try {
            // 1. Modern 1.20+: player.level().damageSources().playerAttack(player)
            Method levelMethod = player.getClass().getMethod("level");
            Object level = levelMethod.invoke(player);
            Method damageSourcesMethod = level.getClass().getMethod("damageSources");
            Object sources = damageSourcesMethod.invoke(level);
            Method playerAttackMethod = sources.getClass().getMethod("playerAttack", Class.forName("net.minecraft.world.entity.player.Player"));
            return playerAttackMethod.invoke(sources, player);
        } catch (Throwable t1) {
            try {
                // 2. Legacy <= 1.19.4: DamageSource.playerAttack(Player)
                Class<?> dsClass = Class.forName("net.minecraft.world.damagesource.DamageSource");
                Method playerAttackMethod = dsClass.getMethod("playerAttack", Class.forName("net.minecraft.world.entity.player.Player"));
                return playerAttackMethod.invoke(null, player);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static Object resolveDamageSource(String modernMethodName, String legacyFieldName) {
        try {
            // Attempt 1: Legacy static field (<= 1.19.4)
            Class<?> dsClass = Class.forName("net.minecraft.world.damagesource.DamageSource");
            Field field = dsClass.getField(legacyFieldName);
            return field.get(null);
        } catch (Throwable ignored) {
            // Attempt 2: Modern 1.20+ via DamageSources accessor
            try {
                // Try client level
                Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
                Method getInstance = mcClass.getMethod("getInstance");
                Object mc = getInstance.invoke(null);
                if (mc != null) {
                    Method getLevel = mc.getClass().getMethod("level");
                    Object level = getLevel.invoke(mc);
                    if (level != null) {
                        Method dsMethod = level.getClass().getMethod("damageSources");
                        Object sources = dsMethod.invoke(level);
                        Method m = sources.getClass().getMethod(modernMethodName);
                        return m.invoke(sources);
                    }
                }
            } catch (Throwable ignored2) {}

            try {
                // Try NeoForge ServerLifecycleHooks
                Class<?> slhClass = Class.forName("net.neoforged.neoforge.server.ServerLifecycleHooks");
                Method getServer = slhClass.getMethod("getCurrentServer");
                Object server = getServer.invoke(null);
                if (server != null) {
                    Method overworld = server.getClass().getMethod("overworld");
                    Object level = overworld.invoke(server);
                    Method dsMethod = level.getClass().getMethod("damageSources");
                    Object sources = dsMethod.invoke(level);
                    Method m = sources.getClass().getMethod(modernMethodName);
                    return m.invoke(sources);
                }
            } catch (Throwable ignored3) {}

            try {
                // Try Forge ServerLifecycleHooks
                Class<?> slhClass = Class.forName("net.minecraftforge.server.ServerLifecycleHooks");
                Method getServer = slhClass.getMethod("getCurrentServer");
                Object server = getServer.invoke(null);
                if (server != null) {
                    Method overworld = server.getClass().getMethod("overworld");
                    Object level = overworld.invoke(server);
                    Method dsMethod = level.getClass().getMethod("damageSources");
                    Object sources = dsMethod.invoke(level);
                    Method m = sources.getClass().getMethod(modernMethodName);
                    return m.invoke(sources);
                }
            } catch (Throwable ignored4) {}

            LOGGER.fine("[DamageSourceShim] Resolving modern damage source for " + modernMethodName);
            return null;
        }
    }
}
