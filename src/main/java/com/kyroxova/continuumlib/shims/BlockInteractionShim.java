package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Block interactions in Minecraft 1.20.5+ and 1.21+.
 * Converts legacy InteractionResult to modern ItemInteractionResult.
 */
public final class BlockInteractionShim {

    private static final Logger LOGGER = Logger.getLogger(BlockInteractionShim.class.getName());

    private BlockInteractionShim() {}

    /**
     * Converts InteractionResult -> ItemInteractionResult on 1.20.5+.
     */
    public static Object toItemInteractionResult(Object interactionResult) {
        if (interactionResult == null) return null;

        try {
            Class<?> itemResultClass = Class.forName("net.minecraft.world.ItemInteractionResult");
            String resultName = interactionResult.toString();

            // SUCCESS -> SUCCESS_AND_END_CONSUME or CONSUME
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
            LOGGER.fine("[BlockInteractionShim] Error translating interaction result: " + t.getMessage());
            return interactionResult;
        }
    }
}
