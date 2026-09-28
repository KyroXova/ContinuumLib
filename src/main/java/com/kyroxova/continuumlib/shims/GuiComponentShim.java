package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for 2D GuiComponent blit and fill drawing.
 * In Minecraft 1.20+, net.minecraft.client.gui.GuiComponent was removed
 * and merged into net.minecraft.client.gui.GuiGraphics.
 */
public final class GuiComponentShim {

    private static final Logger LOGGER = Logger.getLogger(GuiComponentShim.class.getName());

    private GuiComponentShim() {}

    /**
     * Intercepts: GuiComponent.fill(PoseStack poseStack, int minX, int minY, int maxX, int maxY, int color)
     */
    public static void fill(Object graphicsOrPose, int minX, int minY, int maxX, int maxY, int color) {
        if (graphicsOrPose == null) return;

        try {
            // 1. Try modern GuiGraphics.fill(minX, minY, maxX, maxY, color)
            Method fillMethod = graphicsOrPose.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class);
            fillMethod.invoke(graphicsOrPose, minX, minY, maxX, maxY, color);
        } catch (Throwable t1) {
            try {
                // 2. Legacy GuiComponent.fill(PoseStack, minX, minY, maxX, maxY, color)
                Class<?> guiComponentClass = Class.forName("net.minecraft.client.gui.GuiComponent");
                Method fillMethod = guiComponentClass.getMethod("fill",
                        Class.forName("com.mojang.blaze3d.vertex.PoseStack"),
                        int.class, int.class, int.class, int.class, int.class);
                fillMethod.invoke(null, graphicsOrPose, minX, minY, maxX, maxY, color);
            } catch (Throwable ignored) {}
        }
    }
}
