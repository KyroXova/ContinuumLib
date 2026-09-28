package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Block and Fluid Render Layers.
 * In 1.18.2, mods set render layers via ItemBlockRenderTypes.setRenderLayer(Block, RenderType).
 * In modern Minecraft (1.19+), render layers are defined via model JSONs ("render_type")
 * or client initialization extensions.
 * This shim allows legacy calls to execute safely across all Minecraft versions without crashing.
 */
public final class RenderTypeShim {

    private static final Logger LOGGER = Logger.getLogger(RenderTypeShim.class.getName());

    private RenderTypeShim() {}

    /**
     * Intercepts: ItemBlockRenderTypes.setRenderLayer(Block, RenderType)
     */
    public static void setRenderLayer(Object block, Object renderType) {
        if (block == null || renderType == null) return;

        try {
            // Attempt legacy ItemBlockRenderTypes.setRenderLayer(Block, RenderType)
            Class<?> clazz = Class.forName("net.minecraft.client.renderer.ItemBlockRenderTypes");
            Method method = clazz.getMethod("setRenderLayer",
                    Class.forName("net.minecraft.world.level.block.Block"),
                    Class.forName("net.minecraft.client.renderer.RenderType"));
            method.invoke(null, block, renderType);
        } catch (Throwable t) {
            // On modern 1.20+ / NeoForge / Fabric, render types are handled via model JSON
            LOGGER.fine("[RenderTypeShim] SetRenderLayer skipped on modern client (handled via model json): " + block);
        }
    }

    /**
     * Intercepts: ItemBlockRenderTypes.setRenderLayer(Fluid, RenderType)
     */
    public static void setFluidRenderLayer(Object fluid, Object renderType) {
        if (fluid == null || renderType == null) return;

        try {
            Class<?> clazz = Class.forName("net.minecraft.client.renderer.ItemBlockRenderTypes");
            Method method = clazz.getMethod("setRenderLayer",
                    Class.forName("net.minecraft.world.level.material.Fluid"),
                    Class.forName("net.minecraft.client.renderer.RenderType"));
            method.invoke(null, fluid, renderType);
        } catch (Throwable ignored) {}
    }
}
