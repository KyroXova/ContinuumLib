package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;

/**
 * Universal Reflection & Field/Method Accessor Shim.
 * Replaces net.minecraftforge.fml.util.ObfuscationReflectionHelper across all loaders and versions.
 */
public final class ReflectionHelperShim {

    private static final Logger LOGGER = Logger.getLogger(ReflectionHelperShim.class.getName());

    private ReflectionHelperShim() {}

    public static <T, E> void setPrivateValue(Class<?> clazz, T instance, E value, String fieldName) {
        try {
            Field field = findField(clazz, fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Throwable t) {
            LOGGER.fine(String.format("[ReflectionHelperShim] Failed to set field %s on %s: %s",
                    fieldName, clazz.getName(), t.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, E> E getPrivateValue(Class<?> clazz, T instance, String fieldName) {
        try {
            Field field = findField(clazz, fieldName);
            field.setAccessible(true);
            return (E) field.get(instance);
        } catch (Throwable t) {
            LOGGER.fine(String.format("[ReflectionHelperShim] Failed to get field %s on %s: %s",
                    fieldName, clazz.getName(), t.getMessage()));
            return null;
        }
    }

    public static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (f.getName().equals(fieldName)) {
                    f.setAccessible(true);
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found on class " + clazz.getName());
    }

    public static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && parameterTypesMatch(m.getParameterTypes(), parameterTypes)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException("Method " + methodName + " not found on class " + clazz.getName());
    }

    private static boolean parameterTypesMatch(Class<?>[] a, Class<?>[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) return false;
        }
        return true;
    }
}
