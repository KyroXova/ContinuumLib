package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Logger;

public final class BlockStatePropertyShim {

    private static final Logger LOGGER = Logger.getLogger(BlockStatePropertyShim.class.getName());
    private static final Map<Object, Map<String, Object>> VIRTUAL_STATE_PROPERTIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BlockStatePropertyShim() {}

    public static Object getValue(Object blockState, Object property) {
        if (blockState == null || property == null) return null;

        try {
            Method getValueMethod = blockState.getClass().getMethod("getValue",
                    Class.forName("net.minecraft.world.level.block.state.properties.Property"));
            return getValueMethod.invoke(blockState, property);
        } catch (Throwable ignored) {}

        String propName = extractPropertyName(property);
        Map<String, Object> props = VIRTUAL_STATE_PROPERTIES.get(blockState);
        if (props != null && props.containsKey(propName)) {
            return props.get(propName);
        }

        return getDefaultPropertyValue(property);
    }

    public static Object setValue(Object blockState, Object property, Object value) {
        if (blockState == null || property == null) return blockState;

        try {
            Method setValueMethod = blockState.getClass().getMethod("setValue",
                    Class.forName("net.minecraft.world.level.block.state.properties.Property"),
                    Comparable.class);
            return setValueMethod.invoke(blockState, property, value);
        } catch (Throwable ignored) {}

        String propName = extractPropertyName(property);
        VIRTUAL_STATE_PROPERTIES.computeIfAbsent(blockState, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(propName, value);
        return blockState;
    }

    public static Object cycle(Object blockState, Object property) {
        if (blockState == null || property == null) return blockState;

        try {
            Method cycleMethod = blockState.getClass().getMethod("cycle",
                    Class.forName("net.minecraft.world.level.block.state.properties.Property"));
            return cycleMethod.invoke(blockState, property);
        } catch (Throwable ignored) {}

        Object curr = getValue(blockState, property);
        if (curr instanceof Boolean b) {
            return setValue(blockState, property, !b);
        } else if (curr instanceof Integer i) {
            return setValue(blockState, property, i + 1);
        }
        return blockState;
    }

    public static boolean hasProperty(Object blockState, Object property) {
        if (blockState == null || property == null) return false;

        try {
            Method hasMethod = blockState.getClass().getMethod("hasProperty",
                    Class.forName("net.minecraft.world.level.block.state.properties.Property"));
            return (boolean) hasMethod.invoke(blockState, property);
        } catch (Throwable ignored) {}

        String propName = extractPropertyName(property);
        Map<String, Object> props = VIRTUAL_STATE_PROPERTIES.get(blockState);
        return props != null && props.containsKey(propName);
    }

    public static String extractPropertyName(Object property) {
        if (property == null) return "";
        try {
            Method getNameMethod = property.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(property);
        } catch (Throwable ignored) {}
        return property.toString();
    }

    public static Object getDefaultPropertyValue(Object property) {
        if (property == null) return null;
        String name = extractPropertyName(property).toLowerCase();
        if (name.contains("waterlogged") || name.contains("powered") || name.contains("lit") || name.contains("open")) {
            return Boolean.FALSE;
        }
        if (name.contains("power") || name.contains("age") || name.contains("level") || name.contains("layers")) {
            return 0;
        }
        return null;
    }
}
