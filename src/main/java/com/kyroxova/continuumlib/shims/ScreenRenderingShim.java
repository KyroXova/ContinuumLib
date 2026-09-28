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
        if (font == null) return 0;
        try {
            // 1. Try modern GuiGraphics.drawString(Font, Component/String, int, int, int)
            for (Method m : graphicsOrPose.getClass().getMethods()) {
                if ("drawString".equals(m.getName()) && m.getParameterCount() == 5) {
                    return (int) m.invoke(graphicsOrPose, font, text, (int) x, (int) y, color);
                }
            }
        } catch (Throwable ignored) {}

        try {
            // 2. Fallback to legacy Font.draw(PoseStack, Component/String, float, float, int)
            Object pose = extractPose(graphicsOrPose);
            for (Method m : font.getClass().getMethods()) {
                if ("draw".equals(m.getName()) && m.getParameterCount() == 5) {
                    return (int) m.invoke(font, pose, text, x, y, color);
                }
            }
        } catch (Throwable t2) {
            LOGGER.fine("[ScreenRenderingShim] Error in drawString: " + t2.getMessage());
        }
        return 0;
    }

    /**
     * Bridges font.drawShadow(poseStack, text, x, y, color) to guiGraphics.drawString with dropShadow = true
     */
    public static int drawShadow(Object font, Object graphicsOrPose, Object text, float x, float y, int color) {
        if (font == null) return 0;
        try {
            // Modern GuiGraphics.drawString(Font, Component/String, int, int, int, boolean)
            for (Method m : graphicsOrPose.getClass().getMethods()) {
                if ("drawString".equals(m.getName()) && m.getParameterCount() == 6) {
                    return (int) m.invoke(graphicsOrPose, font, text, (int) x, (int) y, color, true);
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Legacy Font.drawShadow(PoseStack, Component/String, float, float, int)
            Object pose = extractPose(graphicsOrPose);
            for (Method m : font.getClass().getMethods()) {
                if ("drawShadow".equals(m.getName()) && m.getParameterCount() == 5) {
                    return (int) m.invoke(font, pose, text, x, y, color);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ScreenRenderingShim] Error in drawShadow: " + t.getMessage());
        }
        return 0;
    }

    /**
     * Bridges screen.render(poseStack, mouseX, mouseY, partialTick)
     */
    public static void renderScreen(Object screen, Object graphicsOrPose, int mouseX, int mouseY, float partialTick) {
        if (screen == null) return;
        try {
            // Modern Screen.render(GuiGraphics, int, int, float)
            for (Method m : screen.getClass().getMethods()) {
                if ("render".equals(m.getName()) && m.getParameterCount() == 4) {
                    m.invoke(screen, graphicsOrPose, mouseX, mouseY, partialTick);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Legacy Screen.render(PoseStack, int, int, float)
            Object pose = extractPose(graphicsOrPose);
            for (Method m : screen.getClass().getMethods()) {
                if ("render".equals(m.getName()) && m.getParameterCount() == 4) {
                    m.invoke(screen, pose, mouseX, mouseY, partialTick);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ScreenRenderingShim] Error in renderScreen: " + t.getMessage());
        }
    }

    /**
     * Bridges screen.renderComponentTooltip(poseStack, components, mouseX, mouseY)
     */
    public static void renderComponentTooltip(Object screen, Object graphicsOrPose, List<?> components, int mouseX, int mouseY) {
        if (screen == null) return;
        try {
            // Try modern GuiGraphics.renderComponentTooltip(Font, List, int, int)
            Method renderTooltip = graphicsOrPose.getClass().getMethod("renderComponentTooltip",
                    Class.forName("net.minecraft.client.gui.Font"), List.class, int.class, int.class);
            Method getFont = screen.getClass().getMethod("getFont");
            Object font = getFont.invoke(screen);
            renderTooltip.invoke(graphicsOrPose, font, components, mouseX, mouseY);
            return;
        } catch (Throwable ignored) {}

        try {
            // Legacy Screen.renderComponentTooltip(PoseStack, List, int, int)
            Object pose = extractPose(graphicsOrPose);
            Method legacyTooltip = screen.getClass().getMethod("renderComponentTooltip", pose.getClass(), List.class, int.class, int.class);
            legacyTooltip.invoke(screen, pose, components, mouseX, mouseY);
        } catch (Throwable ignored) {}
    }

    /**
     * Bridges screen.renderBackground(poseStack) to modern screen.renderBackground(guiGraphics, mouseX, mouseY, partialTick)
     */
    public static void renderBackground(Object screen, Object graphicsOrPose) {
        if (screen == null) return;
        try {
            // Modern 1.20.2+: renderBackground(GuiGraphics, int, int, float)
            for (Method m : screen.getClass().getMethods()) {
                if ("renderBackground".equals(m.getName()) && m.getParameterCount() == 4) {
                    m.invoke(screen, graphicsOrPose, 0, 0, 0.0f);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Modern 1.20.0-1.20.1: renderBackground(GuiGraphics)
            for (Method m : screen.getClass().getMethods()) {
                if ("renderBackground".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(screen, graphicsOrPose);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Legacy <= 1.19.4: renderBackground(PoseStack)
            Object pose = extractPose(graphicsOrPose);
            for (Method m : screen.getClass().getMethods()) {
                if ("renderBackground".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(screen, pose);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Bridges screen.renderTooltip(poseStack, itemStack/component, mouseX, mouseY)
     */
    public static void renderTooltip(Object screen, Object graphicsOrPose, Object itemOrComponent, int mouseX, int mouseY) {
        if (screen == null) return;
        try {
            // Modern 1.20+: GuiGraphics.renderTooltip(Font, ItemStack/Component, int, int)
            for (Method m : graphicsOrPose.getClass().getMethods()) {
                if ("renderTooltip".equals(m.getName()) && (m.getParameterCount() == 4 || m.getParameterCount() == 3)) {
                    Method getFont = screen.getClass().getMethod("getFont");
                    Object font = getFont.invoke(screen);
                    if (m.getParameterCount() == 4) {
                        m.invoke(graphicsOrPose, font, itemOrComponent, mouseX, mouseY);
                    } else {
                        m.invoke(graphicsOrPose, font, itemOrComponent);
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Legacy <= 1.19.4: Screen.renderTooltip(PoseStack, ItemStack/Component, int, int)
            Object pose = extractPose(graphicsOrPose);
            for (Method m : screen.getClass().getMethods()) {
                if ("renderTooltip".equals(m.getName()) && m.getParameterCount() == 4) {
                    m.invoke(screen, pose, itemOrComponent, mouseX, mouseY);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Bridges ItemRenderer.renderGuiItem(ItemStack, int, int) to GuiGraphics.renderItem(ItemStack, int, int)
     */
    public static void renderGuiItem(Object itemRendererOrGraphics, Object itemStack, int x, int y) {
        if (itemRendererOrGraphics == null || itemStack == null) return;
        try {
            // 1. Try modern GuiGraphics.renderItem(ItemStack, int, int)
            for (Method m : itemRendererOrGraphics.getClass().getMethods()) {
                if ("renderItem".equals(m.getName()) && m.getParameterCount() == 3) {
                    m.invoke(itemRendererOrGraphics, itemStack, x, y);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // 2. Legacy ItemRenderer.renderGuiItem(ItemStack, int, int)
            for (Method m : itemRendererOrGraphics.getClass().getMethods()) {
                if ("renderGuiItem".equals(m.getName()) && m.getParameterCount() == 3) {
                    m.invoke(itemRendererOrGraphics, itemStack, x, y);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Bridges ItemRenderer.renderGuiItemDecorations(Font, ItemStack, int, int) to GuiGraphics.renderItemDecorations
     */
    public static void renderGuiItemDecorations(Object itemRendererOrGraphics, Object font, Object itemStack, int x, int y) {
        if (itemRendererOrGraphics == null || itemStack == null) return;
        try {
            // 1. Try modern GuiGraphics.renderItemDecorations(Font, ItemStack, int, int)
            for (Method m : itemRendererOrGraphics.getClass().getMethods()) {
                if ("renderItemDecorations".equals(m.getName()) && m.getParameterCount() == 4) {
                    m.invoke(itemRendererOrGraphics, font, itemStack, x, y);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // 2. Legacy ItemRenderer.renderGuiItemDecorations(Font, ItemStack, int, int)
            for (Method m : itemRendererOrGraphics.getClass().getMethods()) {
                if ("renderGuiItemDecorations".equals(m.getName()) && m.getParameterCount() == 4) {
                    m.invoke(itemRendererOrGraphics, font, itemStack, x, y);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }
}
