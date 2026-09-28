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
            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
            Object id = resourceLocationClass.getConstructor(String.class, String.class).newInstance(modId, name);

            // Register into Fabric Registry
            Method registerMethod = registryClass.getMethod("register", registryClass, resourceLocationClass, Object.class);
            Object registeredValue = supplier.get();
            registerMethod.invoke(null, registryType, id, registeredValue);

            // Return a synthetic supplier wrapping the registered value
            return (Supplier<?>) () -> registeredValue;
        } catch (Throwable t) {
            // Queue for delayed registration or fallback
            PENDING_REGISTRATIONS.computeIfAbsent(modId, k -> new ConcurrentHashMap<>()).put(name, supplier);
            return supplier;
        }
    }

    /**
     * Resolves a RegistryObject or DeferredHolder safely.
     */
    public static Object resolveHolder(Object holder) {
        if (holder instanceof Supplier<?> supplier) {
            return supplier.get();
        }
        try {
            Method getMethod = holder.getClass().getMethod("get");
            return getMethod.invoke(holder);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to resolve registry holder: " + holder, t);
        }
    }
}
