package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Block interactions across Minecraft versions.
 * Converts legacy InteractionResult to modern ItemInteractionResult (1.20.5 - 1.21.1)
 * and bridges calls to Block.use(...) on modern runtimes (1.20.5+ / 1.21+ / 26.3+).
 */
public final class BlockInteractionShim {

    private static final Logger LOGGER = Logger.getLogger(BlockInteractionShim.class.getName());

    private BlockInteractionShim() {}

    /**
     * Intercepts and bridges legacy Block.use(...) invocations on modern runtimes.
     */
    public static Object useBlock(Object block, Object state, Object level, Object pos, Object player, Object hand, Object hitResult) {
        if (block == null) return null;

        try {
            Class<?> blockClass = block.getClass();

            // Attempt 1: Modern 1.20.5+ useItemOn(...)
            try {
                // Determine player's held item
                Object itemStack = null;
                if (player != null && hand != null) {
                    try {
                        Method getItemInHand = player.getClass().getMethod("getItemInHand", hand.getClass());
                        itemStack = getItemInHand.invoke(player, hand);
                    } catch (Throwable ignored) {}
                }
                if (itemStack == null) {
                    try {
                        Class<?> isClass = Class.forName("net.minecraft.world.item.ItemStack");
                        itemStack = isClass.getField("EMPTY").get(null);
                    } catch (Throwable ignored) {}
                }

                // Look for useItemOn
                for (Method m : blockClass.getMethods()) {
                    if ("useItemOn".equals(m.getName()) && m.getParameterCount() == 7) {
                        Object res = m.invoke(block, itemStack, state, level, pos, player, hand, hitResult);
                        return toInteractionResult(res);
                    }
                }
            } catch (Throwable ignored) {}

            // Attempt 2: Modern 1.20.5+ useWithoutItem(...)
            try {
                for (Method m : blockClass.getMethods()) {
                    if ("useWithoutItem".equals(m.getName()) && m.getParameterCount() == 5) {
                        return m.invoke(block, state, level, pos, player, hitResult);
                    }
                }
            } catch (Throwable ignored) {}

            // Attempt 3: Legacy <= 1.20.4 use(...)
            try {
                for (Method m : blockClass.getMethods()) {
                    if ("use".equals(m.getName()) && m.getParameterCount() == 6) {
                        return m.invoke(block, state, level, pos, player, hand, hitResult);
                    }
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[BlockInteractionShim] Error invoking block use: " + t.getMessage());
        }

        return null;
    }

    /**
     * Converts InteractionResult -> ItemInteractionResult on 1.20.5 - 1.21.1.
     */
    public static Object toItemInteractionResult(Object interactionResult) {
        if (interactionResult == null) return null;

        try {
            Class<?> itemResultClass = Class.forName("net.minecraft.world.ItemInteractionResult");
            String resultName = interactionResult.toString();

            if ("SUCCESS".equals(resultName)) {
                try {
                    return itemResultClass.getField("SUCCESS").get(null);
                } catch (NoSuchFieldException e) {
                    return itemResultClass.getField("CONSUME").get(null);
                }
            } else if ("CONSUME".equals(resultName)) {
                return itemResultClass.getField("CONSUME").get(null);
            } else if ("PASS".equals(resultName)) {
                return itemResultClass.getField("PASS_TO_DEFAULT_BLOCK_INTERACTION").get(null);
            } else if ("FAIL".equals(resultName)) {
                return itemResultClass.getField("FAIL").get(null);
            }

            return itemResultClass.getField("PASS_TO_DEFAULT_BLOCK_INTERACTION").get(null);
        } catch (Throwable t) {
            // In 1.21.2+ / 26.3+, ItemInteractionResult doesn't exist, so return interactionResult directly
            return interactionResult;
        }
    }

    /**
     * Converts ItemInteractionResult -> InteractionResult on 1.20.5 - 1.21.1.
     */
    public static Object toInteractionResult(Object itemInteractionResult) {
        if (itemInteractionResult == null) return null;

        // Support InteractionResultHolder.getResult()
        try {
            for (Method m : itemInteractionResult.getClass().getMethods()) {
                if ("getResult".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(itemInteractionResult);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> interResultClass = Class.forName("net.minecraft.world.InteractionResult");
            String resultName = itemInteractionResult.toString();

            if ("SUCCESS".equals(resultName)) {
                return interResultClass.getField("SUCCESS").get(null);
            } else if ("CONSUME".equals(resultName) || "CONSUME_PARTIAL".equals(resultName)) {
                return interResultClass.getField("CONSUME").get(null);
            } else if ("PASS_TO_DEFAULT_BLOCK_INTERACTION".equals(resultName) || "PASS".equals(resultName)) {
                return interResultClass.getField("PASS").get(null);
            } else if ("FAIL".equals(resultName)) {
                return interResultClass.getField("FAIL").get(null);
            }

            return interResultClass.getField("PASS").get(null);
        } catch (Throwable t) {
            return itemInteractionResult;
        }
    }
}
