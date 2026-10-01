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

        // 5. Screen.renderBackground(PoseStack) -> ScreenRenderingShim.renderBackground
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/Screen", "renderBackground",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderBackground",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_0, null, null,
                "Screen.renderBackground(PoseStack) -> ScreenRenderingShim.renderBackground"
        ));

        // 6. Screen.renderTooltip(PoseStack, ItemStack, int, int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/Screen", "renderTooltip",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderTooltip",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;II)V",
                v1_20_0, null, null,
                "Screen.renderTooltip(PoseStack, ItemStack) -> ScreenRenderingShim.renderTooltip"
        ));

        // 7. Screen.renderTooltip(PoseStack, Component, int, int)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/Screen", "renderTooltip",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderTooltip",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;II)V",
                v1_20_0, null, null,
                "Screen.renderTooltip(PoseStack, Component) -> ScreenRenderingShim.renderTooltip"
        ));

        MCVersion v1_17_0 = MCVersion.of("1.17");
        MCVersion v1_16_5 = MCVersion.of("1.16.5");

        // 8. KeyBinding <-> KeyMapping class redirects
        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule(
                "net/minecraft/client/settings/KeyBinding",
                "net/minecraft/client/KeyMapping",
                v1_17_0, null, null,
                "KeyBinding -> KeyMapping (1.17+)"
        ));

        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule(
                "net/minecraft/client/KeyMapping",
                "net/minecraft/client/settings/KeyBinding",
                null, v1_16_5, null,
                "KeyMapping -> KeyBinding (<= 1.16.5)"
        ));

        // 9. MatrixStack <-> PoseStack class redirects (1.16.5 <-> 1.17+)
        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule(
                "com/mojang/blaze3d/matrix/MatrixStack",
                "com/mojang/blaze3d/vertex/PoseStack",
                v1_17_0, null, null,
                "MatrixStack -> PoseStack (1.17+)"
        ));

        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule(
                "com/mojang/blaze3d/vertex/PoseStack",
                "com/mojang/blaze3d/matrix/MatrixStack",
                null, v1_16_5, null,
                "PoseStack -> MatrixStack (<= 1.16.5)"
        ));

        // 10. ItemRenderer.renderGuiItem and renderGuiItemDecorations polyfills
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/entity/ItemRenderer", "renderGuiItem",
                "(Lnet/minecraft/world/item/ItemStack;II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderGuiItem",
                "(Ljava/lang/Object;Ljava/lang/Object;II)V",
                v1_20_0, null, null,
                "ItemRenderer.renderGuiItem -> ScreenRenderingShim.renderGuiItem"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/entity/ItemRenderer", "renderGuiItemDecorations",
                "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderGuiItemDecorations",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;II)V",
                v1_20_0, null, null,
                "ItemRenderer.renderGuiItemDecorations -> ScreenRenderingShim.renderGuiItemDecorations"
        ));

        // 11. ClientRegistry.registerKeyBinding polyfills
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/client/ClientRegistry", "registerKeyBinding",
                "(Lnet/minecraft/client/KeyMapping;)V",
                "com/kyroxova/continuumlib/shims/KeyMappingShim", "registerKeyMapping",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "ClientRegistry.registerKeyBinding -> KeyMappingShim.registerKeyMapping"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/client/registry/ClientRegistry", "registerKeyBinding",
                "(Lnet/minecraft/client/settings/KeyBinding;)V",
                "com/kyroxova/continuumlib/shims/KeyMappingShim", "registerKeyMapping",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "Legacy ClientRegistry.registerKeyBinding -> KeyMappingShim.registerKeyMapping"
        ));
    }
}
