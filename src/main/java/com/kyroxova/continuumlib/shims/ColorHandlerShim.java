package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for BlockColor and ItemColor tinting registration.
 * Unifies color provider registration across Forge (ColorHandlerEvent, RegisterColorHandlersEvent),
 * NeoForge (RegisterColorHandlersEvent.Block/Item), Fabric (ColorProviderRegistry),
 * and vanilla BlockColors / ItemColors across 1.7.9 -> 26.3+.
 */
public final class ColorHandlerShim {

    private static final Logger LOGGER = Logger.getLogger(ColorHandlerShim.class.getName());

    private static final Map<Object, Object> BLOCK_COLORS = new ConcurrentHashMap<>();
    private static final Map<Object, Object> ITEM_COLORS = new ConcurrentHashMap<>();

    private ColorHandlerShim() {}

    /**
     * Registers a BlockColor provider for one or more blocks.
     */
    public static Object registerBlockColor(Object blockColor, Object... blocks) {
        if (blockColor == null || blocks == null) return null;
        for (Object b : blocks) {
            if (b != null) {
                BLOCK_COLORS.put(b, blockColor);
            }
        }

        // 1. Try modern BlockColors.register(BlockColor, Block...)
        try {
            Class<?> bcClass = Class.forName("net.minecraft.client.color.block.BlockColors");
            for (Method m : bcClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, blockColor, blocks);
                    return blockColor;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Fabric ColorProviderRegistry.BLOCK.register(BlockColorProvider, Block...)
        try {
            Class<?> cprClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry");
            Object blockRegistry = cprClass.getField("BLOCK").get(null);
            for (Method m : blockRegistry.getClass().getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockRegistry, blockColor, blocks);
                    return blockColor;
                }
            }
        } catch (Throwable ignored) {}

        return blockColor;
    }

    /**
     * Registers an ItemColor provider for one or more items.
     */
    public static Object registerItemColor(Object itemColor, Object... items) {
        if (itemColor == null || items == null) return null;
        for (Object i : items) {
            if (i != null) {
                ITEM_COLORS.put(i, itemColor);
            }
        }

        // 1. Try modern ItemColors.register(ItemColor, ItemLike...)
        try {
            Class<?> icClass = Class.forName("net.minecraft.client.color.item.ItemColors");
            for (Method m : icClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(null, itemColor, items);
                    return itemColor;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Fabric ColorProviderRegistry.ITEM.register(ItemColorProvider, ItemConvertible...)
        try {
            Class<?> cprClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry");
            Object itemRegistry = cprClass.getField("ITEM").get(null);
            for (Method m : itemRegistry.getClass().getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(itemRegistry, itemColor, items);
                    return itemColor;
                }
            }
        } catch (Throwable ignored) {}

        return itemColor;
    }

    /**
     * Dispatches registration from RegisterColorHandlersEvent.Block or ColorHandlerEvent.Block.
     */
    public static void registerBlockColorFromEvent(Object event, Object blockColor, Object... blocks) {
        if (event == null || blockColor == null || blocks == null) return;
        for (Object b : blocks) {
            if (b != null) BLOCK_COLORS.put(b, blockColor);
        }

        try {
            for (Method m : event.getClass().getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(event, blockColor, blocks);
                    return;
                } else if ("getBlockColors".equals(m.getName()) && m.getParameterCount() == 0) {
                    Object bc = m.invoke(event);
                    if (bc != null) {
                        for (Method regM : bc.getClass().getMethods()) {
                            if ("register".equals(regM.getName()) && regM.getParameterCount() == 2) {
                                try {
                                    regM.setAccessible(true);
                                } catch (Throwable ignored) {}
                                regM.invoke(bc, blockColor, blocks);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        registerBlockColor(blockColor, blocks);
    }

    /**
     * Dispatches registration from RegisterColorHandlersEvent.Item or ColorHandlerEvent.Item.
     */
    public static void registerItemColorFromEvent(Object event, Object itemColor, Object... items) {
        if (event == null || itemColor == null || items == null) return;
        for (Object i : items) {
            if (i != null) ITEM_COLORS.put(i, itemColor);
        }

        try {
            for (Method m : event.getClass().getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(event, itemColor, items);
                    return;
                } else if ("getItemColors".equals(m.getName()) && m.getParameterCount() == 0) {
                    Object ic = m.invoke(event);
                    if (ic != null) {
                        for (Method regM : ic.getClass().getMethods()) {
                            if ("register".equals(regM.getName()) && regM.getParameterCount() == 2) {
                                try {
                                    regM.setAccessible(true);
                                } catch (Throwable ignored) {}
                                regM.invoke(ic, itemColor, items);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        registerItemColor(itemColor, items);
    }

    public static Object getBlockColor(Object block) {
        return block != null ? BLOCK_COLORS.get(block) : null;
    }

    public static Object getItemColor(Object item) {
        return item != null ? ITEM_COLORS.get(item) : null;
    }

    public static Object getColor(Object blockOrItem) {
        if (blockOrItem == null) return null;
        Object bc = BLOCK_COLORS.get(blockOrItem);
        if (bc != null) return bc;
        return ITEM_COLORS.get(blockOrItem);
    }

    public static int getRegisteredBlockColorCount() {
        return BLOCK_COLORS.size();
    }

    public static int getRegisteredItemColorCount() {
        return ITEM_COLORS.size();
    }

    public static Map<Object, Object> getRegisteredBlockColors() {
        return Collections.unmodifiableMap(BLOCK_COLORS);
    }

    public static Map<Object, Object> getRegisteredItemColors() {
        return Collections.unmodifiableMap(ITEM_COLORS);
    }

    public static void clearRegistrations() {
        BLOCK_COLORS.clear();
        ITEM_COLORS.clear();
    }
}
