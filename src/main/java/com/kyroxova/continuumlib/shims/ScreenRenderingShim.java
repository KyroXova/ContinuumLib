package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Screen, GUI, and Client UI Rendering.
 * In Minecraft 1.20+, Mojang replaced passing PoseStack to screens with GuiGraphics.
 * This shim intercepts legacy calls expecting PoseStack and translates them to modern GuiGraphics invocations.
 */
public final class ScreenRenderingShim {

    private static final Logger LOGGER = Logger.getLogger(ScreenRenderingShim.class.getName());

    private ScreenRenderingShim() {}

    /**
     * Extracts PoseStack from GuiGraphics on 1.20+ or returns the object if already a PoseStack.
     */
    public static Object extractPose(Object graphicsOrPose) {
        if (graphicsOrPose == null) return null;
        try {
            // Check if it's GuiGraphics (1.20+)
            Method poseMethod = graphicsOrPose.getClass().getMethod("pose");
            return poseMethod.invoke(graphicsOrPose);
        } catch (Throwable ignored) {
            // Already a PoseStack on <= 1.19.4
            return graphicsOrPose;
        }
    }

    /**
     * Bridges font.draw(poseStack, text, x, y, color) to guiGraphics.drawString(font, text, x, y, color)
     */
    public static int drawString(Object font, Object graphicsOrPose, Object text, float x, float y, int color) {
        try {
            // 1. Try modern GuiGraphics.drawString(Font, Component, int, int, int)
            Method drawString = graphicsOrPose.getClass().getMethod("drawString", font.getClass(), text.getClass(), int.class, int.class, int.class);
            return (int) drawString.invoke(graphicsOrPose, font, text, (int) x, (int) y, color);
        } catch (Throwable t1) {
            try {
                // 2. Fallback to legacy Font.draw(PoseStack, Component/String, float, float, int)
                Object pose = extractPose(graphicsOrPose);
                Method legacyDraw = font.getClass().getMethod("draw", pose.getClass(), text.getClass(), float.class, float.class, int.class);
                return (int) legacyDraw.invoke(font, pose, text, x, y, color);
            } catch (Throwable t2) {
                return 0;
            }
        }
    }

    /**
     * Bridges screen.renderComponentTooltip(poseStack, components, mouseX, mouseY)
     */
    public static void renderComponentTooltip(Object screen, Object graphicsOrPose, List<?> components, int mouseX, int mouseY) {
        try {
            // Try modern GuiGraphics.renderComponentTooltip(Font, List, int, int)
            Method renderTooltip = graphicsOrPose.getClass().getMethod("renderComponentTooltip",
                    Class.forName("net.minecraft.client.gui.Font"), List.class, int.class, int.class);
            Method getFont = screen.getClass().getMethod("getFont");
            Object font = getFont.invoke(screen);
            renderTooltip.invoke(graphicsOrPose, font, components, mouseX, mouseY);
        } catch (Throwable t1) {
            try {
                // Legacy Screen.renderComponentTooltip(PoseStack, List, int, int)
                Object pose = extractPose(graphicsOrPose);
                Method legacyTooltip = screen.getClass().getMethod("renderComponentTooltip", pose.getClass(), List.class, int.class, int.class);
                legacyTooltip.invoke(screen, pose, components, mouseX, mouseY);
            } catch (Throwable ignored) {}
        }
    }
}
