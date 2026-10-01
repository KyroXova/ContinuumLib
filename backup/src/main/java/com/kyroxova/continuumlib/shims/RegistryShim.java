package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Registry Shim.
 * Bridges Forge DeferredRegister and RegistryObject calls across Forge, NeoForge, and Fabric.
 */
public final class RegistryShim {

    private static final Logger LOGGER = Logger.getLogger(RegistryShim.class.getName());

    // Holds pending registrations when running on Fabric or non-Forge platforms
    private static final Map<String, Map<String, Supplier<?>>> PENDING_REGISTRATIONS = new ConcurrentHashMap<>();

    private RegistryShim() {}

    /**
     * Intercepts DeferredRegister.register(String name, Supplier<T> supplier) on Fabric.
     */
    public static Object registerFabric(Object registryType, String modId, String name, Supplier<?> supplier) {
        LOGGER.info(String.format("[RegistryShim] Intercepted registry entry: %s:%s", modId, name));

        try {
            // Check if modern Fabric Registry is present
            Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
            Object id = ResourceLocationShim.create(modId, name);

            // Register into Fabric Registry
            Method registerMethod = registryClass.getMethod("register", registryClass, Class.forName("net.minecraft.resources.ResourceLocation"), Object.class);
            Object registeredValue = supplier.get();
            registerMethod.invoke(null, registryType, id, registeredValue);

            LegacyMetadataShim.onDynamicRegister(registryType, modId, name, registeredValue);

            // Return a synthetic supplier wrapping the registered value
            return (Supplier<?>) () -> registeredValue;
        } catch (Throwable t) {
            // Queue for delayed registration or fallback
            PENDING_REGISTRATIONS.computeIfAbsent(modId, k -> new ConcurrentHashMap<>()).put(name, supplier);
            try {
                Object registeredValue = supplier.get();
                LegacyMetadataShim.onDynamicRegister(registryType, modId, name, registeredValue);
            } catch (Throwable ignored) {}
            return supplier;
        }
    }

    /**
     * Universal DeferredRegister.create factory that works across any loader and parameter type.
     */
    public static Object createDeferredRegister(Object regKeyOrRegistry, String modId) {
        if (regKeyOrRegistry == null) {
            return null;
        }
        try {
            // 1. Try NeoForge DeferredRegister.create(ResourceKey, String) or create(Registry, String) or create(Identifier, String)
            Class<?> defRegClass = Class.forName("net.neoforged.neoforge.registries.DeferredRegister");
            for (Method m : defRegClass.getMethods()) {
                if ("create".equals(m.getName()) && m.getParameterCount() == 2 && m.getParameterTypes()[1] == String.class) {
                    try {
                        return m.invoke(null, regKeyOrRegistry, modId);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (ClassNotFoundException ignored) {}

        try {
            // 2. Try Forge DeferredRegister.create(IForgeRegistry, String)
            Class<?> defRegClass = Class.forName("net.minecraftforge.registries.DeferredRegister");
            for (Method m : defRegClass.getMethods()) {
                if ("create".equals(m.getName()) && m.getParameterCount() == 2 && m.getParameterTypes()[1] == String.class) {
                    try {
                        return m.invoke(null, regKeyOrRegistry, modId);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (ClassNotFoundException ignored) {}

        return null;
    }

    /**
     * Resolves a RegistryObject, DeferredHolder, RegistryDelegate, or Holder safely.
     */
    public static Object resolveHolder(Object holder) {
        return getValue(holder);
    }

    /**
     * Gets the underlying object value from a Supplier, RegistryObject, DeferredHolder, or Holder.
     */
    public static Object getValue(Object holder) {
        if (holder == null) return null;
        if (holder instanceof Supplier<?> supplier) {
            return supplier.get();
        }
        try {
            // Try get()
            Method getMethod = holder.getClass().getMethod("get");
            return getMethod.invoke(holder);
        } catch (NoSuchMethodException e) {
            try {
                // Try value() (modern Holder)
                Method valueMethod = holder.getClass().getMethod("value");
                return valueMethod.invoke(holder);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to resolve registry holder value: " + holder, t);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to resolve registry holder: " + holder, t);
        }
    }

    /**
     * Gets the ResourceLocation ID from a RegistryObject, DeferredHolder, or Holder.
     */
    public static Object getId(Object holder) {
        if (holder == null) return null;
        try {
            // 1. Try getId()
            Method getId = holder.getClass().getMethod("getId");
            return getId.invoke(holder);
        } catch (Throwable ignored) {}

        try {
            // 2. Try getKey().location()
            Method getKey = holder.getClass().getMethod("getKey");
            Object key = getKey.invoke(holder);
            if (key != null) {
                Method location = key.getClass().getMethod("location");
                return location.invoke(key);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Checks if a holder/delegate is currently bound or present.
     */
    public static boolean isPresent(Object holder) {
        if (holder == null) return false;
        try {
            Method isPresent = holder.getClass().getMethod("isPresent");
            return (boolean) isPresent.invoke(holder);
        } catch (Throwable ignored) {}

        try {
            Method isBound = holder.getClass().getMethod("isBound");
            return (boolean) isBound.invoke(holder);
        } catch (Throwable ignored) {}

        return true;
    }
}
