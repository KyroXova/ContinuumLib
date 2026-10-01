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

    /**
     * Intercepts: GuiComponent.blit(PoseStack poseStack, int x, int y, int u, int v, int width, int height)
     */
    public static void blit(Object graphicsOrPose, Object textureOrX, int yOrU, int vOrWidth, int widthOrHeight, int heightOrTexW, int texH) {
        if (graphicsOrPose == null) return;
        try {
            // Modern GuiGraphics.blit(ResourceLocation, int, int, int, int, int, int)
            for (Method m : graphicsOrPose.getClass().getMethods()) {
                if ("blit".equals(m.getName()) && m.getParameterCount() == 7) {
                    m.invoke(graphicsOrPose, textureOrX, yOrU, vOrWidth, widthOrHeight, heightOrTexW, texH);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Intercepts: GuiComponent.drawString(PoseStack poseStack, Font font, String/Component text, int x, int y, int color)
     */
    public static void drawString(Object graphicsOrPose, Object font, Object text, int x, int y, int color) {
        ScreenRenderingShim.drawString(font, graphicsOrPose, text, x, y, color);
    }

    /**
     * Intercepts: GuiComponent.drawCenteredString(PoseStack poseStack, Font font, String/Component text, int x, int y, int color)
     */
    public static void drawCenteredString(Object graphicsOrPose, Object font, Object text, int x, int y, int color) {
        if (font == null || graphicsOrPose == null) return;
        try {
            // Modern GuiGraphics.drawCenteredString(Font, String/Component, int, int, int)
            for (Method m : graphicsOrPose.getClass().getMethods()) {
                if ("drawCenteredString".equals(m.getName()) && m.getParameterCount() == 5) {
                    m.invoke(graphicsOrPose, font, text, x, y, color);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Legacy GuiComponent.drawCenteredString
            Class<?> guiComponentClass = Class.forName("net.minecraft.client.gui.GuiComponent");
            Object pose = ScreenRenderingShim.extractPose(graphicsOrPose);
            for (Method m : guiComponentClass.getMethods()) {
                if ("drawCenteredString".equals(m.getName()) && m.getParameterCount() == 6) {
                    m.invoke(null, pose, font, text, x, y, color);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }
}
