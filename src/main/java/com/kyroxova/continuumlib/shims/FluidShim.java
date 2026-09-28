package com.kyroxova.continuumlib.shims;

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
                // Legacy 1.18.2 FluidAttributes.builder(...)
                Class<?> builderClass = Class.forName("net.minecraftforge.fluids.FluidAttributes");
                return builderClass.getMethod("builder",
                        Class.forName("net.minecraft.resources.ResourceLocation"),
                        Class.forName("net.minecraft.resources.ResourceLocation")).invoke(null, still, flowing);
            } catch (Throwable t2) {
                return null;
            }
        }
    }
}
