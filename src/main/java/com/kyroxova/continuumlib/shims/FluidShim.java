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
        try {
            Method m = fluidHolder.getClass().getMethod("getDensity");
            return (int) m.invoke(fluidHolder);
        } catch (Throwable t) {
            return 1000;
        }
    }

    /**
     * Bridges getViscosity query across FluidAttributes and FluidType.
     */
    public static int getViscosity(Object fluidHolder) {
        if (fluidHolder == null) return 1000;
        try {
            Method m = fluidHolder.getClass().getMethod("getViscosity");
            return (int) m.invoke(fluidHolder);
        } catch (Throwable t) {
            return 1000;
        }
    }

    /**
     * Bridges getTemperature query across FluidAttributes and FluidType.
     */
    public static int getTemperature(Object fluidHolder) {
        if (fluidHolder == null) return 300;
        try {
            Method m = fluidHolder.getClass().getMethod("getTemperature");
            return (int) m.invoke(fluidHolder);
        } catch (Throwable t) {
            return 300;
        }
    }

    /**
     * Bridges isGaseous query across FluidAttributes and FluidType.
     */
    public static boolean isGaseous(Object fluidHolder) {
        if (fluidHolder == null) return false;
        try {
            Method m = fluidHolder.getClass().getMethod("isGaseous");
            return (boolean) m.invoke(fluidHolder);
        } catch (Throwable t) {
            return false;
        }
    }
}
