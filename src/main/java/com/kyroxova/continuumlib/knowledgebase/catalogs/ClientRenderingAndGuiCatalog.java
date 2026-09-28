package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Client Rendering, Render Types, Widgets, and GUI Components.
 */
public final class ClientRenderingAndGuiCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_0 = MCVersion.of("1.19");
        MCVersion v1_19_3 = MCVersion.of("1.19.3");
        MCVersion v1_19_4 = MCVersion.of("1.19.4");
        MCVersion v1_20_0 = MCVersion.of("1.20");

        // 1. ItemBlockRenderTypes.setRenderLayer -> RenderTypeShim.setRenderLayer
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/ItemBlockRenderTypes", "setRenderLayer",
                "(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/client/renderer/RenderType;)V",
                "com/kyroxova/continuumlib/shims/RenderTypeShim", "setRenderLayer",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_19_0, null, null,
                "ItemBlockRenderTypes.setRenderLayer polyfill for modern clients"
        ));

        // 2. Button constructor -> ButtonShim.create
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/components/Button", "<init>",
                "(IIIILnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)V",
                "com/kyroxova/continuumlib/shims/ButtonShim", "create",
                "(IIIILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_3, null, null,
                "Button constructor -> ButtonShim.create (1.19.3+)"
        ));

        // 3. ItemTransforms.TransformType -> ItemDisplayContext (1.19.4+)
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/client/renderer/block/model/ItemTransforms$TransformType",
                "net/minecraft/world/item/ItemDisplayContext",
                v1_19_4, null, null,
                "ItemTransforms.TransformType -> ItemDisplayContext"
        ));

        // 4. GuiComponent.fill -> GuiComponentShim.fill (1.20+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/GuiComponent", "fill",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V",
                "com/kyroxova/continuumlib/shims/GuiComponentShim", "fill",
                "(Ljava/lang/Object;IIIII)V",
                v1_20_0, null, null,
                "GuiComponent.fill -> GuiComponentShim.fill"
        ));
    }
}
