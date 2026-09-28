package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Screen, GUI, Font, and Client UI Rendering.
 * Bridges PoseStack <-> GuiGraphics (1.20+).
 */
public final class ScreenAndUIRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_0 = MCVersion.of("1.20");

        // 1. Font.draw(PoseStack, text, x, y, color) -> ScreenRenderingShim.drawString
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/Font", "draw",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "drawString",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFI)I",
                v1_20_0, null, null,
                "Font.draw(PoseStack, Component) -> ScreenRenderingShim.drawString"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/Font", "draw",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "drawString",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFI)I",
                v1_20_0, null, null,
                "Font.draw(PoseStack, String) -> ScreenRenderingShim.drawString"
        ));

        // 2. Font.drawShadow(PoseStack, text, x, y, color) -> ScreenRenderingShim.drawShadow
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/Font", "drawShadow",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "drawShadow",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFI)I",
                v1_20_0, null, null,
                "Font.drawShadow(PoseStack, Component) -> ScreenRenderingShim.drawShadow"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/Font", "drawShadow",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "drawShadow",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFI)I",
                v1_20_0, null, null,
                "Font.drawShadow(PoseStack, String) -> ScreenRenderingShim.drawShadow"
        ));

        // 3. Screen.render(PoseStack, mouseX, mouseY, partialTicks) -> ScreenRenderingShim.renderScreen
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/Screen", "render",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderScreen",
                "(Ljava/lang/Object;Ljava/lang/Object;IIF)V",
                v1_20_0, null, null,
                "Screen.render(PoseStack) -> ScreenRenderingShim.renderScreen"
        ));

        // 4. Screen.renderComponentTooltip(PoseStack, list, x, y)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/Screen", "renderComponentTooltip",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/List;II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderComponentTooltip",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/List;II)V",
                v1_20_0, null, null,
                "Screen.renderComponentTooltip(PoseStack) -> ScreenRenderingShim.renderComponentTooltip"
        ));
    }
}
