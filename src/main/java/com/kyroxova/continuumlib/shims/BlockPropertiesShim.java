package com.kyroxova.continuumlib.shims;

import java.util.logging.Logger;

/**
 * Polyfill shim for Block properties in Minecraft 1.20+ where {@code net.minecraft.world.level.material.Material} was deleted.
 * Native 1.18.2 Forge code invokes:
 * {@code BlockBehaviour.Properties.of(Material.WOOD)} or {@code BlockBehaviour.Properties.of(Material.WOOD, MaterialColor.COLOR_BROWN)}
 * This shim intercepts that invocation and synthesizes modern Properties with equivalent settings.
 */
public final class BlockPropertiesShim {

    private static final Logger LOGGER = Logger.getLogger(BlockPropertiesShim.class.getName());

    private BlockPropertiesShim() {}

    /**
     * Intercepts: BlockBehaviour$Properties.of(Material)
     */
    public static Object ofLegacyMaterial(Object material) {
        // Invoked at runtime when running on 1.20+
        return createModernProperties(material, null);
    }

    /**
     * Intercepts: BlockBehaviour$Properties.of(Material, MaterialColor)
     */
    public static Object ofLegacyMaterialAndColor(Object material, Object materialColor) {
        return createModernProperties(material, materialColor);
    }

    /**
     * Intercepts: BlockBehaviour$Properties.of(Material, Function<BlockState, MaterialColor>)
     */
    public static Object ofLegacyMaterialAndFunction(Object material, Object function) {
        return createModernProperties(material, null);
    }

    private static Object createModernProperties(Object material, Object color) {
        try {
            Class<?> propertiesClass = Class.forName("net.minecraft.world.level.block.state.BlockBehaviour$Properties");
            // Modern 1.20+ has: public static BlockBehaviour.Properties of()
            Object props = propertiesClass.getMethod("of").invoke(null);

            if (color != null) {
                try {
                    Class<?> mapColorClass = Class.forName("net.minecraft.world.level.material.MapColor");
                    if (mapColorClass.isInstance(color)) {
                        propertiesClass.getMethod("mapColor", mapColorClass).invoke(props, color);
                    }
                } catch (Throwable ignored) {}
            }

            return props;
        } catch (Throwable t) {
            LOGGER.fine("[BlockPropertiesShim] Could not invoke modern Properties.of() via reflection: " + t.getMessage());
            return null;
        }
    }
}
