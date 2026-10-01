package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Fluid Systems and Fluid Types.
 * Bridges Forge 1.18.2 FluidAttributes across 1.19.2+ FluidType (NeoForge/Forge) and Fabric FluidVariant.
 */
public final class FluidShim {

    private static final Logger LOGGER = Logger.getLogger(FluidShim.class.getName());

    private FluidShim() {}

    /**
     * Bridges legacy FluidAttributes.builder(...)
     */
    public static Object createFluidAttributes(Object still, Object flowing) {
        try {
            // Check if modern FluidType exists (1.19.2+)
            Class<?> fluidTypeProps = Class.forName("net.neoforged.neoforge.fluids.FluidType$Properties");
            Object props = fluidTypeProps.getMethod("create").invoke(null);
            LOGGER.fine("[FluidShim] Created modern FluidType.Properties");
            return props;
        } catch (Throwable t1) {
            try {
                Class<?> forgeFluidTypeProps = Class.forName("net.minecraftforge.fluids.FluidType$Properties");
                return forgeFluidTypeProps.getMethod("create").invoke(null);
            } catch (Throwable t2) {
                try {
                    // Legacy 1.18.2 FluidAttributes.builder(...)
                    Class<?> builderClass = Class.forName("net.minecraftforge.fluids.FluidAttributes");
                    return builderClass.getMethod("builder",
                            Class.forName("net.minecraft.resources.ResourceLocation"),
                            Class.forName("net.minecraft.resources.ResourceLocation")).invoke(null, still, flowing);
                } catch (Throwable t3) {
                    return null;
                }
            }
        }
    }

    /**
     * Bridges getDensity query across FluidAttributes and FluidType.
     */
    public static int getDensity(Object fluidHolder) {
        if (fluidHolder == null) return 1000;
        for (Method m : getAllMethods(fluidHolder.getClass())) {
            if ("getDensity".equals(m.getName()) && m.getParameterCount() == 0) {
                try {
                    m.setAccessible(true);
                    Object res = m.invoke(fluidHolder);
                    if (res instanceof Number n) return n.intValue();
                } catch (Throwable ignored) {}
            }
        }
        return 1000;
    }

    /**
     * Bridges getViscosity query across FluidAttributes and FluidType.
     */
    public static int getViscosity(Object fluidHolder) {
        if (fluidHolder == null) return 1000;
        for (Method m : getAllMethods(fluidHolder.getClass())) {
            if ("getViscosity".equals(m.getName()) && m.getParameterCount() == 0) {
                try {
                    m.setAccessible(true);
                    Object res = m.invoke(fluidHolder);
                    if (res instanceof Number n) return n.intValue();
                } catch (Throwable ignored) {}
            }
        }
        return 1000;
    }

    /**
     * Bridges getTemperature query across FluidAttributes and FluidType.
     */
    public static int getTemperature(Object fluidHolder) {
        if (fluidHolder == null) return 300;
        for (Method m : getAllMethods(fluidHolder.getClass())) {
            if ("getTemperature".equals(m.getName()) && m.getParameterCount() == 0) {
                try {
                    m.setAccessible(true);
                    Object res = m.invoke(fluidHolder);
                    if (res instanceof Number n) return n.intValue();
                } catch (Throwable ignored) {}
            }
        }
        return 300;
    }

    /**
     * Bridges isGaseous query across FluidAttributes and FluidType.
     */
    public static boolean isGaseous(Object fluidHolder) {
        if (fluidHolder == null) return false;
        for (Method m : getAllMethods(fluidHolder.getClass())) {
            if ("isGaseous".equals(m.getName()) && m.getParameterCount() == 0) {
                try {
                    m.setAccessible(true);
                    Object res = m.invoke(fluidHolder);
                    if (res instanceof Boolean b) return b;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static Method[] getAllMethods(Class<?> clazz) {
        Method[] publicMethods = clazz.getMethods();
        Method[] declaredMethods = clazz.getDeclaredMethods();
        Method[] all = new Method[publicMethods.length + declaredMethods.length];
        System.arraycopy(publicMethods, 0, all, 0, publicMethods.length);
        System.arraycopy(declaredMethods, 0, all, publicMethods.length, declaredMethods.length);
        return all;
    }

    /**
     * Retrieves the FluidState at a given BlockPos across versions.
     */
    public static Object getFluidState(Object level, Object pos) {
        if (level == null || pos == null) return null;
        try {
            for (Method m : level.getClass().getMethods()) {
                if ("getFluidState".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(level, pos);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Fallback: Level.getBlockState(pos).getFluidState()
        try {
            Object blockState = WorldShim.getBlockState(level, pos);
            if (blockState != null) {
                for (Method m : blockState.getClass().getMethods()) {
                    if ("getFluidState".equals(m.getName()) && m.getParameterCount() == 0) {
                        try {
                            m.setAccessible(true);
                            return m.invoke(blockState);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Checks if a FluidState is a source block across versions.
     */
    public static boolean isSource(Object fluidState) {
        if (fluidState == null) return false;

        // 1. Try FluidState.isSource() (1.14+)
        try {
            for (Method m : fluidState.getClass().getMethods()) {
                if ("isSource".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(fluidState);
                        if (res instanceof Boolean b) return b;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try FluidState.getAmount() == 8
        try {
            for (Method m : fluidState.getClass().getMethods()) {
                if (("getAmount".equals(m.getName()) || "amount".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(fluidState);
                        if (res instanceof Number n) return n.intValue() == 8;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
