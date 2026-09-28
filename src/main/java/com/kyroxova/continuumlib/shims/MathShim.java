package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Vector, Axis, and Quaternion mathematical rotations.
 * Bridges legacy com.mojang.math.Vector3f rotation calls to modern Axis / JOML on 1.19.3+.
 */
public final class MathShim {

    private static final Logger LOGGER = Logger.getLogger(MathShim.class.getName());

    private MathShim() {}

    /**
     * Bridges Vector3f.YP.rotationDegrees(degrees)
     */
    public static Object rotateYDegrees(float degrees) {
        return rotateAxisDegrees("YP", degrees);
    }

    /**
     * Bridges Vector3f.XP.rotationDegrees(degrees)
     */
    public static Object rotateXDegrees(float degrees) {
        return rotateAxisDegrees("XP", degrees);
    }

    /**
     * Bridges Vector3f.ZP.rotationDegrees(degrees)
     */
    public static Object rotateZDegrees(float degrees) {
        return rotateAxisDegrees("ZP", degrees);
    }

    private static Object rotateAxisDegrees(String axisName, float degrees) {
        try {
            // Modern 1.19.3+ Axis (e.g. Axis.YP.rotationDegrees(degrees))
            Class<?> axisClass = Class.forName("com.mojang.math.Axis");
            Field axisField = axisClass.getField(axisName);
            Object axisInstance = axisField.get(null);
            Method rotMethod = axisInstance.getClass().getMethod("rotationDegrees", float.class);
            return rotMethod.invoke(axisInstance, degrees);
        } catch (Throwable t1) {
            try {
                // Legacy Vector3f (<= 1.19.2)
                Class<?> vectorClass = Class.forName("com.mojang.math.Vector3f");
                Field axisField = vectorClass.getField(axisName);
                Object vecInstance = axisField.get(null);
                Method rotMethod = vecInstance.getClass().getMethod("rotationDegrees", float.class);
                return rotMethod.invoke(vecInstance, degrees);
            } catch (Throwable t2) {
                LOGGER.fine("[MathShim] Could not calculate axis rotation: " + t2.getMessage());
                return null;
            }
        }
    }
}
