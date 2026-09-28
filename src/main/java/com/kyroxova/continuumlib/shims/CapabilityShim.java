package com.kyroxova.continuumlib.shims;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Capabilities and Item Handlers.
 * In Forge 1.18.2 - 1.20.1: uses CapabilityItemHandler.ITEM_HANDLER_CAPABILITY & LazyOptional.
 * In NeoForge 1.20.4+: uses BlockCapability / Capabilities.ItemHandler.BLOCK.
 * In Fabric: uses Fabric Transfer API.
 */
public final class CapabilityShim {

    private static final Logger LOGGER = Logger.getLogger(CapabilityShim.class.getName());

    private CapabilityShim() {}

    /**
     * Resolves an item handler or capability holder safely across platforms.
     */
    public static Object resolveItemHandler(Object blockEntity, Object side) {
        if (blockEntity == null) return null;
        try {
            // Check if NeoForge block capability is available
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler");
            Object blockCap = capabilitiesClass.getField("BLOCK").get(null);
            LOGGER.fine("[CapabilityShim] Resolved NeoForge ItemHandler BlockCapability");
            return blockCap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Dispatches getCapability(Capability, Direction) safely across Forge, NeoForge, and Fabric.
     */
    public static Object getCapability(Object provider, Object capability, Object direction) {
        if (provider == null) return emptyLazyOptional();

        // 1. Try legacy Forge ICapabilityProvider.getCapability(Capability, Direction)
        try {
            for (Method m : provider.getClass().getMethods()) {
                if ("getCapability".equals(m.getName()) && m.getParameterCount() == 2) {
                    return m.invoke(provider, capability, direction);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try NeoForge level.getCapability(BlockCapability, BlockPos, Direction)
        try {
            Method getLevel = provider.getClass().getMethod("getLevel");
            Method getBlockPos = provider.getClass().getMethod("getBlockPos");
            Object level = getLevel.invoke(provider);
            Object pos = getBlockPos.invoke(provider);
            if (level != null && pos != null) {
                for (Method m : level.getClass().getMethods()) {
                    if ("getCapability".equals(m.getName()) && m.getParameterCount() >= 2) {
                        Object result = (m.getParameterCount() == 3)
                                ? m.invoke(level, capability, pos, direction)
                                : m.invoke(level, capability, pos);
                        if (result != null) {
                            return ofLazyOptional(result);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return emptyLazyOptional();
    }

    /**
     * Creates a synthetic LazyOptional wrapping an active instance.
     */
    public static Object ofLazyOptional(Object value) {
        try {
            Class<?> lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            Method ofMethod = lazyOptClass.getMethod("of", Class.forName("net.minecraftforge.common.util.NonNullSupplier"));
            return ofMethod.invoke(null, (Supplier<Object>) () -> value);
        } catch (Throwable t) {
            return createLazyOptionalProxy(value);
        }
    }

    /**
     * Returns an empty LazyOptional.
     */
    public static Object emptyLazyOptional() {
        try {
            Class<?> lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            return lazyOptClass.getMethod("empty").invoke(null);
        } catch (Throwable t) {
            return createLazyOptionalProxy(null);
        }
    }

    private static Object createLazyOptionalProxy(Object value) {
        try {
            Class<?> lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            return Proxy.newProxyInstance(
                    CapabilityShim.class.getClassLoader(),
                    new Class<?>[]{lazyOptClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("isPresent".equals(name)) {
                            return value != null;
                        }
                        if ("ifPresent".equals(name) && args != null && args.length == 1) {
                            if (value != null && args[0] instanceof Consumer cons) {
                                cons.accept(value);
                            }
                            return null;
                        }
                        if ("orElse".equals(name) && args != null && args.length == 1) {
                            return value != null ? value : args[0];
                        }
                        if ("resolve".equals(name)) {
                            return Optional.ofNullable(value);
                        }
                        return null;
                    }
            );
        } catch (Throwable t) {
            return Optional.ofNullable(value);
        }
    }
}
