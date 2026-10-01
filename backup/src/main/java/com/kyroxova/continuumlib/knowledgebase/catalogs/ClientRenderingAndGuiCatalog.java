package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.FieldRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Client Rendering, Render Types, Model Loaders, Widgets, and GUI Components.
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

        // ItemBlockRenderTypes.setRenderLayer(Fluid, RenderType)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/ItemBlockRenderTypes", "setRenderLayer",
                "(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/client/renderer/RenderType;)V",
                "com/kyroxova/continuumlib/shims/RenderTypeShim", "setFluidRenderLayer",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_19_0, null, null,
                "ItemBlockRenderTypes.setRenderLayer(Fluid) polyfill for modern clients"
        ));

        // 2. Model Loaders: Forge ModelLoaderRegistry -> RenderTypeShim.registerModelLoader
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/client/model/ModelLoaderRegistry", "registerLoader",
                null,
                "com/kyroxova/continuumlib/shims/RenderTypeShim", "registerModelLoader",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "Forge ModelLoaderRegistry.registerLoader -> RenderTypeShim.registerModelLoader (Fabric)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/client/event/ModelEvent$RegisterGeometryLoaders", "register",
                null,
                "com/kyroxova/continuumlib/shims/RenderTypeShim", "registerGeometryLoader",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "NeoForge RegisterGeometryLoaders.register -> RenderTypeShim.registerGeometryLoader (Fabric)"
        ));

        // 3. Button constructor -> ButtonShim.create
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/components/Button", "<init>",
                "(IIIILnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)V",
                "com/kyroxova/continuumlib/shims/ButtonShim", "create",
                "(IIIILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_3, null, null,
                "Button constructor -> ButtonShim.create (1.19.3+)"
        ));

        // 4. ItemTransforms.TransformType -> ItemDisplayContext (1.19.4+)
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/client/renderer/block/model/ItemTransforms$TransformType",
                "net/minecraft/world/item/ItemDisplayContext",
                v1_19_4, null, null,
                "ItemTransforms.TransformType -> ItemDisplayContext"
        ));

        // 5. GuiComponent.fill -> GuiComponentShim.fill (1.20+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/GuiComponent", "fill",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V",
                "com/kyroxova/continuumlib/shims/GuiComponentShim", "fill",
                "(Ljava/lang/Object;IIIII)V",
                v1_20_0, null, null,
                "GuiComponent.fill -> GuiComponentShim.fill"
        ));

        // 6. GuiComponent.drawCenteredString (1.20+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/GuiComponent", "drawCenteredString",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
                "com/kyroxova/continuumlib/shims/GuiComponentShim", "drawCenteredString",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;III)V",
                v1_20_0, null, null,
                "GuiComponent.drawCenteredString(String) -> GuiComponentShim.drawCenteredString"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/GuiComponent", "drawCenteredString",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                "com/kyroxova/continuumlib/shims/GuiComponentShim", "drawCenteredString",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;III)V",
                v1_20_0, null, null,
                "GuiComponent.drawCenteredString(Component) -> GuiComponentShim.drawCenteredString"
        ));

        // 7. ItemRenderer.renderGuiItem -> ScreenRenderingShim.renderGuiItem (1.20+)
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

        // Container Screen background, foreground, and textured rect polyfill rules
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/inventory/GuiContainer", "drawGuiContainerBackgroundLayer",
                "(FII)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "drawGuiContainerBackgroundLayer",
                "(Ljava/lang/Object;FII)V",
                null, null, null,
                "GuiContainer.drawGuiContainerBackgroundLayer -> ScreenRenderingShim.drawGuiContainerBackgroundLayer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/inventory/GuiContainer", "drawGuiContainerForegroundLayer",
                "(II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "drawGuiContainerForegroundLayer",
                "(Ljava/lang/Object;II)V",
                null, null, null,
                "GuiContainer.drawGuiContainerForegroundLayer -> ScreenRenderingShim.drawGuiContainerForegroundLayer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "renderBg",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;FII)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderContainerBackground",
                "(Ljava/lang/Object;Ljava/lang/Object;FII)V",
                null, null, null,
                "AbstractContainerScreen.renderBg(PoseStack) -> ScreenRenderingShim.renderContainerBackground"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "renderBg",
                "(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderContainerBackground",
                "(Ljava/lang/Object;Ljava/lang/Object;FII)V",
                null, null, null,
                "AbstractContainerScreen.renderBg(GuiGraphics) -> ScreenRenderingShim.renderContainerBackground"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "renderLabels",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderContainerForeground",
                "(Ljava/lang/Object;Ljava/lang/Object;II)V",
                null, null, null,
                "AbstractContainerScreen.renderLabels(PoseStack) -> ScreenRenderingShim.renderContainerForeground"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "renderLabels",
                "(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "renderContainerForeground",
                "(Ljava/lang/Object;Ljava/lang/Object;II)V",
                null, null, null,
                "AbstractContainerScreen.renderLabels(GuiGraphics) -> ScreenRenderingShim.renderContainerForeground"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/gui/Gui", "drawTexturedModalRect",
                "(IIIIII)V",
                "com/kyroxova/continuumlib/shims/ScreenRenderingShim", "drawTexturedModalRect",
                "(Ljava/lang/Object;IIIIII)V",
                null, null, null,
                "Gui.drawTexturedModalRect -> ScreenRenderingShim.drawTexturedModalRect"
        ));

        // 8. BlockEntityRenderer registration polyfills
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/blockentity/BlockEntityRenderers", "register",
                "(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/client/renderer/blockentity/BlockEntityRendererProvider;)V",
                "com/kyroxova/continuumlib/shims/ClientRendererShim", "registerBlockEntityRenderer",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "BlockEntityRenderers.register -> ClientRendererShim.registerBlockEntityRenderer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/client/registry/ClientRegistry", "bindTileEntityRenderer",
                "(Lnet/minecraft/tileentity/TileEntityType;Ljava/util/function/Function;)V",
                "com/kyroxova/continuumlib/shims/ClientRendererShim", "registerBlockEntityRenderer",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "ClientRegistry.bindTileEntityRenderer -> ClientRendererShim.registerBlockEntityRenderer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/client/event/EntityRenderersEvent$RegisterRenderers", "registerBlockEntityRenderer",
                "(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/client/renderer/blockentity/BlockEntityRendererProvider;)V",
                "com/kyroxova/continuumlib/shims/ClientRendererShim", "registerFromEvent",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, null,
                "RegisterRenderers.registerBlockEntityRenderer -> ClientRendererShim.registerFromEvent"
        ));

        // 9. EntityRenderer registration polyfills
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/entity/EntityRenderers", "register",
                "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/client/renderer/entity/EntityRendererProvider;)V",
                "com/kyroxova/continuumlib/shims/ClientRendererShim", "registerEntityRenderer",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "EntityRenderers.register -> ClientRendererShim.registerEntityRenderer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/client/registry/RenderingRegistry", "registerEntityRenderingHandler",
                "(Lnet/minecraft/entity/EntityType;Lnet/minecraftforge/fml/client/registry/IRenderFactory;)V",
                "com/kyroxova/continuumlib/shims/ClientRendererShim", "registerEntityRenderer",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "RenderingRegistry.registerEntityRenderingHandler -> ClientRendererShim.registerEntityRenderer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/client/event/EntityRenderersEvent$RegisterRenderers", "registerEntityRenderer",
                "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/client/renderer/entity/EntityRendererProvider;)V",
                "com/kyroxova/continuumlib/shims/ClientRendererShim", "registerFromEvent",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, null,
                "RegisterRenderers.registerEntityRenderer -> ClientRendererShim.registerFromEvent"
        ));

        // 10. Legacy OpenGL 2.1 Fixed-Function Matrix & State Calls (GL11 / GlStateManager -> LegacyRenderShim)
        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glPushMatrix",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "pushMatrix",
                "()V",
                null, null, null,
                "GL11.glPushMatrix -> LegacyRenderShim.pushMatrix"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glPopMatrix",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "popMatrix",
                "()V",
                null, null, null,
                "GL11.glPopMatrix -> LegacyRenderShim.popMatrix"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glTranslatef",
                "(FFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "translate",
                "(FFF)V",
                null, null, null,
                "GL11.glTranslatef -> LegacyRenderShim.translate"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glTranslated",
                "(DDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "translate",
                "(DDD)V",
                null, null, null,
                "GL11.glTranslated -> LegacyRenderShim.translate"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glScalef",
                "(FFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "scale",
                "(FFF)V",
                null, null, null,
                "GL11.glScalef -> LegacyRenderShim.scale"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glScaled",
                "(DDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "scale",
                "(DDD)V",
                null, null, null,
                "GL11.glScaled -> LegacyRenderShim.scale"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glRotatef",
                "(FFFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "rotate",
                "(FFFF)V",
                null, null, null,
                "GL11.glRotatef -> LegacyRenderShim.rotate"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glRotated",
                "(DDDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "rotate",
                "(DDDD)V",
                null, null, null,
                "GL11.glRotated -> LegacyRenderShim.rotate"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glColor4f",
                "(FFFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "color",
                "(FFFF)V",
                null, null, null,
                "GL11.glColor4f -> LegacyRenderShim.color"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glColor3f",
                "(FFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "color",
                "(FFF)V",
                null, null, null,
                "GL11.glColor3f -> LegacyRenderShim.color"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glColor4ub",
                "(BBBB)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "glColor4ub",
                "(BBBB)V",
                null, null, null,
                "GL11.glColor4ub -> LegacyRenderShim.glColor4ub"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glEnable",
                "(I)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "glEnable",
                "(I)V",
                null, null, null,
                "GL11.glEnable -> LegacyRenderShim.glEnable"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glDisable",
                "(I)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "glDisable",
                "(I)V",
                null, null, null,
                "GL11.glDisable -> LegacyRenderShim.glDisable"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glBlendFunc",
                "(II)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "blendFunc",
                "(II)V",
                null, null, null,
                "GL11.glBlendFunc -> LegacyRenderShim.blendFunc"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glDepthMask",
                "(Z)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "depthMask",
                "(Z)V",
                null, null, null,
                "GL11.glDepthMask -> LegacyRenderShim.depthMask"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glDepthFunc",
                "(I)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "depthFunc",
                "(I)V",
                null, null, null,
                "GL11.glDepthFunc -> LegacyRenderShim.depthFunc"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glLineWidth",
                "(F)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "glLineWidth",
                "(F)V",
                null, null, null,
                "GL11.glLineWidth -> LegacyRenderShim.glLineWidth"
        ));

        kb.registerRule(new PolyfillRule(
                "org/lwjgl/opengl/GL11", "glBindTexture",
                "(II)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "glBindTexture",
                "(II)V",
                null, null, null,
                "GL11.glBindTexture -> LegacyRenderShim.glBindTexture"
        ));

        // GlStateManager matrix calls
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "pushMatrix",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "pushMatrix",
                "()V",
                null, null, null,
                "GlStateManager.pushMatrix -> LegacyRenderShim.pushMatrix"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "popMatrix",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "popMatrix",
                "()V",
                null, null, null,
                "GlStateManager.popMatrix -> LegacyRenderShim.popMatrix"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "translate",
                "(FFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "translate",
                "(FFF)V",
                null, null, null,
                "GlStateManager.translate(FFF) -> LegacyRenderShim.translate"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "translate",
                "(DDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "translate",
                "(DDD)V",
                null, null, null,
                "GlStateManager.translate(DDD) -> LegacyRenderShim.translate"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "scale",
                "(FFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "scale",
                "(FFF)V",
                null, null, null,
                "GlStateManager.scale(FFF) -> LegacyRenderShim.scale"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "scale",
                "(DDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "scale",
                "(DDD)V",
                null, null, null,
                "GlStateManager.scale(DDD) -> LegacyRenderShim.scale"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "rotate",
                "(FFFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "rotate",
                "(FFFF)V",
                null, null, null,
                "GlStateManager.rotate -> LegacyRenderShim.rotate"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "color",
                "(FFFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "color",
                "(FFFF)V",
                null, null, null,
                "GlStateManager.color -> LegacyRenderShim.color"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "enableBlend",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "enableBlend",
                "()V",
                null, null, null,
                "GlStateManager.enableBlend -> LegacyRenderShim.enableBlend"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/GlStateManager", "disableBlend",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "disableBlend",
                "()V",
                null, null, null,
                "GlStateManager.disableBlend -> LegacyRenderShim.disableBlend"
        ));

        // 11. Legacy Tessellator / WorldRenderer / BufferBuilder calls
        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "getInstance",
                "()Lnet/minecraft/client/renderer/Tessellator;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "getInstance",
                "()Lcom/kyroxova/continuumlib/shims/LegacyRenderShim;",
                null, null, null,
                "Tessellator.getInstance -> LegacyRenderShim.getInstance"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/client/renderer/Tessellator", "instance",
                "Lnet/minecraft/client/renderer/Tessellator;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "instance",
                "Lcom/kyroxova/continuumlib/shims/LegacyRenderShim;",
                null, null, null,
                "Tessellator.instance -> LegacyRenderShim.instance"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "startDrawingQuads",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "startDrawingQuads",
                "(Ljava/lang/Object;)V",
                null, null, null,
                "Tessellator.startDrawingQuads -> LegacyRenderShim.startDrawingQuads"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "startDrawing",
                "(I)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "startDrawing",
                "(Ljava/lang/Object;I)V",
                null, null, null,
                "Tessellator.startDrawing -> LegacyRenderShim.startDrawing"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "addVertexWithUV",
                "(DDDDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "addVertexWithUV",
                "(Ljava/lang/Object;DDDDD)V",
                null, null, null,
                "Tessellator.addVertexWithUV -> LegacyRenderShim.addVertexWithUV"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "addVertex",
                "(DDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "addVertex",
                "(Ljava/lang/Object;DDD)V",
                null, null, null,
                "Tessellator.addVertex -> LegacyRenderShim.addVertex"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "setColorRGBA",
                "(IIII)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "setColorRGBA",
                "(Ljava/lang/Object;IIII)V",
                null, null, null,
                "Tessellator.setColorRGBA -> LegacyRenderShim.setColorRGBA"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "setColorRGBA_F",
                "(FFFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "setColorRGBA_F",
                "(Ljava/lang/Object;FFFF)V",
                null, null, null,
                "Tessellator.setColorRGBA_F -> LegacyRenderShim.setColorRGBA_F"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "setColorOpaque",
                "(III)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "setColorOpaque",
                "(Ljava/lang/Object;III)V",
                null, null, null,
                "Tessellator.setColorOpaque -> LegacyRenderShim.setColorOpaque"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "setNormal",
                "(FFF)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "setNormal",
                "(Ljava/lang/Object;FFF)V",
                null, null, null,
                "Tessellator.setNormal -> LegacyRenderShim.setNormal"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "setTranslation",
                "(DDD)V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "setTranslation",
                "(Ljava/lang/Object;DDD)V",
                null, null, null,
                "Tessellator.setTranslation -> LegacyRenderShim.setTranslation"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "draw",
                "()I",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "draw",
                "(Ljava/lang/Object;)I",
                null, null, null,
                "Tessellator.draw()I -> LegacyRenderShim.draw"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "draw",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "drawVoid",
                "(Ljava/lang/Object;)V",
                null, null, null,
                "Tessellator.draw()V -> LegacyRenderShim.drawVoid"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "getWorldRenderer",
                "()Lnet/minecraft/client/renderer/WorldRenderer;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "getBuffer",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "Tessellator.getWorldRenderer -> LegacyRenderShim.getBuffer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/Tessellator", "getBuilder",
                "()Lnet/minecraft/client/renderer/BufferBuilder;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "getBuffer",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "Tessellator.getBuilder -> LegacyRenderShim.getBuffer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/BufferBuilder", "pos",
                "(DDD)Lnet/minecraft/client/renderer/BufferBuilder;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "pos",
                "(Ljava/lang/Object;DDD)Lcom/kyroxova/continuumlib/shims/LegacyRenderShim;",
                null, null, null,
                "BufferBuilder.pos -> LegacyRenderShim.pos"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/BufferBuilder", "tex",
                "(DD)Lnet/minecraft/client/renderer/BufferBuilder;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "tex",
                "(Ljava/lang/Object;DD)Lcom/kyroxova/continuumlib/shims/LegacyRenderShim;",
                null, null, null,
                "BufferBuilder.tex -> LegacyRenderShim.tex"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/BufferBuilder", "color",
                "(IIII)Lnet/minecraft/client/renderer/BufferBuilder;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "color",
                "(Ljava/lang/Object;IIII)Lcom/kyroxova/continuumlib/shims/LegacyRenderShim;",
                null, null, null,
                "BufferBuilder.color(IIII) -> LegacyRenderShim.color"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/BufferBuilder", "normal",
                "(FFF)Lnet/minecraft/client/renderer/BufferBuilder;",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "normal",
                "(Ljava/lang/Object;FFF)Lcom/kyroxova/continuumlib/shims/LegacyRenderShim;",
                null, null, null,
                "BufferBuilder.normal -> LegacyRenderShim.normal"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/client/renderer/BufferBuilder", "endVertex",
                "()V",
                "com/kyroxova/continuumlib/shims/LegacyRenderShim", "endVertex",
                "(Ljava/lang/Object;)V",
                null, null, null,
                "BufferBuilder.endVertex -> LegacyRenderShim.endVertex"
        ));
    }
}
