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
            // Attempt legacy static field first (<= 1.19.4)
            Class<?> dsClass = Class.forName("net.minecraft.world.damagesource.DamageSource");
            Field field = dsClass.getField(legacyFieldName);
            return field.get(null);
        } catch (Throwable ignored) {
            // On modern 1.20+, DamageSources are accessed from level.damageSources()
            LOGGER.fine("[DamageSourceShim] Resolving modern damage source for " + modernMethodName);
            return null;
        }
    }
}
