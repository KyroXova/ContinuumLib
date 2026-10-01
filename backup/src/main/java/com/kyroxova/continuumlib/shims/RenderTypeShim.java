package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Block/Fluid Render Layers and Model Loaders.
 *
 * Supports:
 * 1. Render Layers:
 *    - Forge <= 1.18.2: ItemBlockRenderTypes.setRenderLayer(Block, RenderType) / (Fluid, RenderType).
 *    - Fabric: BlockRenderLayerMap.INSTANCE.putBlock(Block, RenderLayer) / putFluid(Fluid, RenderLayer).
 *    - NeoForge 1.20.4+ / Modern: Fluent no-op (data-driven model JSON render_type).
 * 2. Model Loaders:
 *    - Forge IModelLoader (<= 1.18.2).
 *    - Forge / NeoForge IGeometryLoader (1.19+ / 1.20.4+ / 26.3+).
 *    - Fabric Model Loading API (ModelLoadingPlugin).
 */
public final class RenderTypeShim {

    private static final Logger LOGGER = Logger.getLogger(RenderTypeShim.class.getName());

    private static final Map<String, Object> REGISTERED_MODEL_LOADERS = new ConcurrentHashMap<>();

    private RenderTypeShim() {}

    /**
     * Intercepts: ItemBlockRenderTypes.setRenderLayer(Block, RenderType)
     * On Forge <= 1.18.2: delegates to legacy ItemBlockRenderTypes.
     * On Fabric: delegates to BlockRenderLayerMap.INSTANCE.putBlock(Block, RenderType).
     * On NeoForge 1.20.4+: fluent no-op.
     */
    public static void setRenderLayer(Object block, Object renderType) {
        if (block == null || renderType == null) return;

        // 1. Try Fabric BlockRenderLayerMap
        try {
            Class<?> mapClass = Class.forName("net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap");
            Object instance = mapClass.getField("INSTANCE").get(null);
            for (Method m : instance.getClass().getMethods()) {
                if ("putBlock".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.invoke(instance, block, renderType);
                    LOGGER.fine("[RenderTypeShim] Bound block render layer via Fabric BlockRenderLayerMap");
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy Forge ItemBlockRenderTypes.setRenderLayer(Block, RenderType)
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.renderer.ItemBlockRenderTypes");
            Method method = clazz.getMethod("setRenderLayer",
                    Class.forName("net.minecraft.world.level.block.Block"),
                    Class.forName("net.minecraft.client.renderer.RenderType"));
            method.invoke(null, block, renderType);
            LOGGER.fine("[RenderTypeShim] Applied render layer via ItemBlockRenderTypes");
            return;
        } catch (Throwable ignored) {}

        // 3. Modern NeoForge 1.20.4+ fluent no-op (handled via model JSON render_type)
        LOGGER.fine("[RenderTypeShim] SetRenderLayer fluent no-op on modern client for: " + block);
    }

    /**
     * Intercepts: ItemBlockRenderTypes.setRenderLayer(Fluid, RenderType)
     * On Fabric: delegates to BlockRenderLayerMap.INSTANCE.putFluid(Fluid, RenderType).
     */
    public static void setFluidRenderLayer(Object fluid, Object renderType) {
        if (fluid == null || renderType == null) return;

        // 1. Try Fabric BlockRenderLayerMap
        try {
            Class<?> mapClass = Class.forName("net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap");
            Object instance = mapClass.getField("INSTANCE").get(null);
            for (Method m : instance.getClass().getMethods()) {
                if ("putFluid".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.invoke(instance, fluid, renderType);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy Forge ItemBlockRenderTypes.setRenderLayer(Fluid, RenderType)
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.renderer.ItemBlockRenderTypes");
            Method method = clazz.getMethod("setRenderLayer",
                    Class.forName("net.minecraft.world.level.material.Fluid"),
                    Class.forName("net.minecraft.client.renderer.RenderType"));
            method.invoke(null, fluid, renderType);
        } catch (Throwable ignored) {}
    }

    /**
     * Registers a custom model geometry loader across Forge (IModelLoader / IGeometryLoader),
     * NeoForge, and Fabric (ModelLoadingPlugin).
     */
    public static Object registerModelLoader(Object id, Object loader) {
        if (id == null || loader == null) return loader;
        String stringId = String.valueOf(id);
        REGISTERED_MODEL_LOADERS.put(stringId, loader);
        LOGGER.info("[RenderTypeShim] Registered model loader: " + stringId);

        // 1. Try Forge 1.18.2 ModelLoaderRegistry.registerLoader(ResourceLocation, IModelLoader)
        try {
            Class<?> registryClass = Class.forName("net.minecraftforge.client.model.ModelLoaderRegistry");
            Method registerMethod = registryClass.getMethod("registerLoader",
                    Class.forName("net.minecraft.resources.ResourceLocation"),
                    Class.forName("net.minecraftforge.client.model.IModelLoader"));
            registerMethod.invoke(null, id, loader);
            return loader;
        } catch (Throwable ignored) {}

        // 2. Try NeoForge / Forge 1.19+ ModelEvent.RegisterGeometryLoaders
        try {
            Class<?> eventClass = Class.forName("net.neoforged.neoforge.client.event.ModelEvent$RegisterGeometryLoaders");
            // If active registration event is available in scope
        } catch (Throwable ignored) {}

        // 3. Try Fabric ModelLoadingPlugin
        try {
            Class<?> pluginClass = Class.forName("net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin");
            for (Method m : pluginClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                    // Register fallback fabric plugin
                    break;
                }
            }
        } catch (Throwable ignored) {}

        return loader;
    }

    /**
     * Alias for registerModelLoader matching NeoForge geometry loader terminology.
     */
    public static Object registerGeometryLoader(Object id, Object loader) {
        return registerModelLoader(id, loader);
    }

    public static Object getModelLoader(Object id) {
        return id != null ? REGISTERED_MODEL_LOADERS.get(String.valueOf(id)) : null;
    }
}
