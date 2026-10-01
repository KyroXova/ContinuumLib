package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for BlockEntityRenderer and EntityRenderer registration.
 * Unifies client renderer registration across Forge (ClientRegistry, RenderingRegistry, EntityRenderersEvent),
 * NeoForge (EntityRenderersEvent.RegisterRenderers), Fabric (BlockEntityRendererRegistry, EntityRendererRegistry),
 * and standard vanilla (BlockEntityRenderers, EntityRenderers) across 1.7.9 -> 26.3+.
 */
public final class ClientRendererShim {

    private static final Logger LOGGER = Logger.getLogger(ClientRendererShim.class.getName());

    private static final Map<Object, Object> BLOCK_ENTITY_RENDERERS = new ConcurrentHashMap<>();
    private static final Map<Object, Object> ENTITY_RENDERERS = new ConcurrentHashMap<>();

    private ClientRendererShim() {}

    /**
     * Registers a BlockEntityRenderer provider/factory for a BlockEntityType across all mod loaders.
     */
    public static Object registerBlockEntityRenderer(Object blockEntityType, Object rendererProvider) {
        if (blockEntityType == null || rendererProvider == null) return null;
        BLOCK_ENTITY_RENDERERS.put(blockEntityType, rendererProvider);

        // 1. Try modern vanilla BlockEntityRenderers.register(BlockEntityType, BlockEntityRendererProvider)
        try {
            Class<?> berClass = Class.forName("net.minecraft.client.renderer.blockentity.BlockEntityRenderers");
            for (Method m : berClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, blockEntityType, rendererProvider);
                    return rendererProvider;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Fabric BlockEntityRendererRegistry.register(BlockEntityType, BlockEntityRendererFactory)
        try {
            Class<?> berrClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry");
            for (Method m : berrClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, blockEntityType, rendererProvider);
                    return rendererProvider;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try legacy Forge ClientRegistry.bindTileEntityRenderer(TileEntityType, Function/RendererFactory)
        try {
            Class<?> crClass = Class.forName("net.minecraftforge.fml.client.registry.ClientRegistry");
            for (Method m : crClass.getMethods()) {
                if ("bindTileEntityRenderer".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, blockEntityType, rendererProvider);
                    return rendererProvider;
                }
            }
        } catch (Throwable ignored) {}

        return rendererProvider;
    }

    /**
     * Registers an EntityRenderer provider/factory for an EntityType across all mod loaders.
     */
    public static Object registerEntityRenderer(Object entityType, Object rendererProvider) {
        if (entityType == null || rendererProvider == null) return null;
        ENTITY_RENDERERS.put(entityType, rendererProvider);

        // 1. Try modern vanilla EntityRenderers.register(EntityType, EntityRendererProvider)
        try {
            Class<?> erClass = Class.forName("net.minecraft.client.renderer.entity.EntityRenderers");
            for (Method m : erClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, entityType, rendererProvider);
                    return rendererProvider;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Fabric EntityRendererRegistry.register(EntityType, EntityRendererFactory)
        try {
            Class<?> errClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry");
            for (Method m : errClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, entityType, rendererProvider);
                    return rendererProvider;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try legacy Forge RenderingRegistry.registerEntityRenderingHandler(EntityType, IRenderFactory)
        try {
            Class<?> rrClass = Class.forName("net.minecraftforge.fml.client.registry.RenderingRegistry");
            for (Method m : rrClass.getMethods()) {
                if ("registerEntityRenderingHandler".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, entityType, rendererProvider);
                    return rendererProvider;
                }
            }
        } catch (Throwable ignored) {}

        return rendererProvider;
    }

    /**
     * Event dispatch polyfill for Forge/NeoForge EntityRenderersEvent.RegisterRenderers.registerBlockEntityRenderer.
     */
    public static void registerBlockEntityRendererFromEvent(Object event, Object type, Object provider) {
        if (event == null || type == null || provider == null) return;
        BLOCK_ENTITY_RENDERERS.put(type, provider);

        try {
            for (Method m : event.getClass().getMethods()) {
                if ("registerBlockEntityRenderer".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(event, type, provider);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        registerBlockEntityRenderer(type, provider);
    }

    /**
     * Event dispatch polyfill for Forge/NeoForge EntityRenderersEvent.RegisterRenderers.registerEntityRenderer.
     */
    public static void registerEntityRendererFromEvent(Object event, Object type, Object provider) {
        if (event == null || type == null || provider == null) return;
        ENTITY_RENDERERS.put(type, provider);

        try {
            for (Method m : event.getClass().getMethods()) {
                if ("registerEntityRenderer".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(event, type, provider);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        registerEntityRenderer(type, provider);
    }

    /**
     * General event dispatch polyfill.
     */
    public static void registerFromEvent(Object event, Object type, Object provider) {
        if (event == null || type == null || provider == null) return;

        // 1. Try event.registerBlockEntityRenderer
        try {
            for (Method m : event.getClass().getMethods()) {
                if ("registerBlockEntityRenderer".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(event, type, provider);
                    BLOCK_ENTITY_RENDERERS.put(type, provider);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try event.registerEntityRenderer
        try {
            for (Method m : event.getClass().getMethods()) {
                if ("registerEntityRenderer".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(event, type, provider);
                    ENTITY_RENDERERS.put(type, provider);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fallback
        registerBlockEntityRenderer(type, provider);
    }

    public static Object getBlockEntityRenderer(Object blockEntityType) {
        return blockEntityType != null ? BLOCK_ENTITY_RENDERERS.get(blockEntityType) : null;
    }

    public static Object getEntityRenderer(Object entityType) {
        return entityType != null ? ENTITY_RENDERERS.get(entityType) : null;
    }

    public static int getRegisteredBlockEntityRendererCount() {
        return BLOCK_ENTITY_RENDERERS.size();
    }

    public static int getRegisteredEntityRendererCount() {
        return ENTITY_RENDERERS.size();
    }

    public static Map<Object, Object> getRegisteredBlockEntityRenderers() {
        return Collections.unmodifiableMap(BLOCK_ENTITY_RENDERERS);
    }

    public static Map<Object, Object> getRegisteredEntityRenderers() {
        return Collections.unmodifiableMap(ENTITY_RENDERERS);
    }

    public static void clearRegistrations() {
        BLOCK_ENTITY_RENDERERS.clear();
        ENTITY_RENDERERS.clear();
    }
}
