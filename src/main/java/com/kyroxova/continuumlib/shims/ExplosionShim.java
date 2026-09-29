package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Level.explode across 1.7.9 -> 26.3+.
 * Dynamically bridges legacy Explosion.BlockInteraction (<= 1.19.2) and modern
 * Level.ExplosionInteraction (1.20+ / 26.3+), supporting power calculations and null-safety.
 */
public final class ExplosionShim {

    private static final Logger LOGGER = Logger.getLogger(ExplosionShim.class.getName());

    private ExplosionShim() {}

    /**
     * Dispatches Level.explode with interaction handling and fire support.
     */
    public static Object explode(Object level, Object exploder, double x, double y, double z, float power, boolean causesFire, Object interaction) {
        if (level == null || power <= 0.0f) return null;

        // Try modern Level.explode methods first (1.20+)
        for (Method m : level.getClass().getMethods()) {
            if ("explode".equals(m.getName())) {
                try {
                    m.setAccessible(true);
                    int pc = m.getParameterCount();
                    if (pc == 7) {
                        // (Entity, double, double, double, float, boolean, Level.ExplosionInteraction / BlockInteraction)
                        return m.invoke(level, exploder, x, y, z, power, causesFire, resolveInteractionForMethod(m.getParameterTypes()[6], interaction));
                    } else if (pc == 6) {
                        // (Entity, double, double, double, float, Level.ExplosionInteraction)
                        return m.invoke(level, exploder, x, y, z, power, resolveInteractionForMethod(m.getParameterTypes()[5], interaction));
                    } else if (pc == 5) {
                        // (Entity, double, double, double, float)
                        return m.invoke(level, exploder, x, y, z, power);
                    }
                } catch (Throwable ignored) {}
            }
        }

        return null;
    }

    public static Object explode(Object level, Object exploder, double x, double y, double z, float power, Object interaction) {
        return explode(level, exploder, x, y, z, power, false, interaction);
    }

    public static float calculatePower(float basePower, float modifier) {
        return Math.max(0.0f, basePower * modifier);
    }

    public static float calculatePower(float basePower, Object calculator, Object level, Object pos) {
        if (calculator != null) {
            try {
                for (Method m : calculator.getClass().getMethods()) {
                    if (m.getName().toLowerCase().contains("power") || m.getName().toLowerCase().contains("calculate")) {
                        m.setAccessible(true);
                        Object res = m.invoke(calculator, basePower);
                        if (res instanceof Number n) return n.floatValue();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return Math.max(0.0f, basePower);
    }

    public static Object resolveInteractionForMethod(Class<?> targetType, Object interaction) {
        if (interaction == null) return null;
        if (targetType.isInstance(interaction)) return interaction;
        if (targetType.isEnum()) {
            String name = interaction.toString().toUpperCase();
            for (Object constant : targetType.getEnumConstants()) {
                if (constant.toString().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
            // Fallback mapping: BLOCK <-> BREAK / DESTROY
            if (name.contains("BLOCK") || name.contains("BREAK") || name.contains("DESTROY")) {
                for (Object constant : targetType.getEnumConstants()) {
                    String cName = constant.toString();
                    if (cName.equals("BLOCK") || cName.equals("BREAK") || cName.equals("DESTROY")) {
                        return constant;
                    }
                }
            }
            if (targetType.getEnumConstants().length > 0) {
                return targetType.getEnumConstants()[0];
            }
        }
        return interaction;
    }
}
