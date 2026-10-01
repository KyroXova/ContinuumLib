package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public final class KeyMappingShim {

    private static final Logger LOGGER = Logger.getLogger(KeyMappingShim.class.getName());
    private static final List<Object> REGISTERED_KEYS = Collections.synchronizedList(new ArrayList<>());

    private KeyMappingShim() {}

    public static Object registerKeyMapping(Object keyMapping) {
        if (keyMapping == null) return null;
        REGISTERED_KEYS.add(keyMapping);

        // 1. Try Fabric KeyBindingHelper
        try {
            Class<?> helperClass = Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
            for (Method m : helperClass.getMethods()) {
                if ("registerKeyBinding".equals(m.getName()) && m.getParameterCount() == 1) {
                    return m.invoke(null, keyMapping);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Forge ClientRegistry (1.17 - 1.19+)
        try {
            Class<?> registryClass = Class.forName("net.minecraftforge.client.ClientRegistry");
            for (Method m : registryClass.getMethods()) {
                if ("registerKeyBinding".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(null, keyMapping);
                    return keyMapping;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try Legacy Forge ClientRegistry (<= 1.16.5)
        try {
            Class<?> legacyRegistryClass = Class.forName("net.minecraftforge.fml.client.registry.ClientRegistry");
            for (Method m : legacyRegistryClass.getMethods()) {
                if ("registerKeyBinding".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(null, keyMapping);
                    return keyMapping;
                }
            }
        } catch (Throwable ignored) {}

        // 4. Fallback: Add to Minecraft.options.keyMappings array directly if options exists
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = mcClass.getMethod("getInstance");
            Object mc = getInstance.invoke(null);
            if (mc != null) {
                Field optionsField = mc.getClass().getField("options");
                Object options = optionsField.get(mc);
                if (options != null) {
                    Field keysField = options.getClass().getField("keyMappings");
                    Object oldArray = keysField.get(options);
                    int len = Array.getLength(oldArray);
                    Object newArray = Array.newInstance(oldArray.getClass().getComponentType(), len + 1);
                    System.arraycopy(oldArray, 0, newArray, 0, len);
                    Array.set(newArray, len, keyMapping);
                    keysField.set(options, newArray);
                }
            }
        } catch (Throwable ignored) {}

        return keyMapping;
    }

    public static Object register(Object keyMapping) {
        return registerKeyMapping(keyMapping);
    }

    public static boolean isDown(Object keyMapping) {
        if (keyMapping == null) return false;
        try {
            for (Method m : keyMapping.getClass().getMethods()) {
                if (("isDown".equals(m.getName()) || "isKeyDown".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(keyMapping);
                    if (res instanceof Boolean b) return b;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Field f = keyMapping.getClass().getField("isDown");
            try {
                f.setAccessible(true);
            } catch (Throwable ignored) {}
            Object res = f.get(keyMapping);
            if (res instanceof Boolean b) return b;
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean consumeClick(Object keyMapping) {
        if (keyMapping == null) return false;
        try {
            for (Method m : keyMapping.getClass().getMethods()) {
                if (("consumeClick".equals(m.getName()) || "isPressed".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(keyMapping);
                    if (res instanceof Boolean b) return b;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[KeyMappingShim] Error consuming click: " + t.getMessage());
        }
        return false;
    }

    public static boolean matches(Object keyMapping, int keyCode, int scanCode) {
        if (keyMapping == null) return false;
        try {
            for (Method m : keyMapping.getClass().getMethods()) {
                if ("matches".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(keyMapping, keyCode, scanCode);
                    if (res instanceof Boolean b) return b;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static List<Object> getRegisteredKeys() {
        return Collections.unmodifiableList(REGISTERED_KEYS);
    }
}
