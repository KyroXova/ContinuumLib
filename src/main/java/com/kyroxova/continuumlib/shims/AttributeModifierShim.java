package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Entity AttributeModifier.
 * In Minecraft 1.20.5+, AttributeModifier(UUID, String, double, Operation) was replaced with
 * AttributeModifier(ResourceLocation, double, Operation).
 * Operation enum names also changed from ADDITION/MULTIPLY_* to ADD_VALUE/ADD_MULTIPLIED_*.
 */
public final class AttributeModifierShim {

    private static final Logger LOGGER = Logger.getLogger(AttributeModifierShim.class.getName());

    private AttributeModifierShim() {}

    /**
     * Bridges legacy AttributeModifier instantiation to modern 1.20.5+ or legacy constructor.
     */
    public static Object createModifier(UUID id, String name, double amount, Object operation) {
        try {
            Class<?> modifierClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier");

            // 1. Check if modern 1.20.5+ constructor exists: (ResourceLocation, double, Operation)
            try {
                Class<?> resLocClass = Class.forName("net.minecraft.resources.ResourceLocation");
                Class<?> opClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");

                Constructor<?> modernCtor = modifierClass.getConstructor(resLocClass, double.class, opClass);

                // Build ResourceLocation from name
                String safeName = (name == null || name.trim().isEmpty()) ? "modifier_" + (id != null ? id.toString() : "default") : name;
                safeName = safeName.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
                if (safeName.startsWith("_")) safeName = "m" + safeName;

                Object resLoc;
                try {
                    // 1.21+ ResourceLocation.parse(String)
                    Method parseMethod = resLocClass.getMethod("parse", String.class);
                    resLoc = parseMethod.invoke(null, "continuum:" + safeName);
                } catch (NoSuchMethodException e) {
                    // 1.20.5 ResourceLocation.fromNamespaceAndPath(String, String) or new ResourceLocation
                    try {
                        Method fromMethod = resLocClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                        resLoc = fromMethod.invoke(null, "continuum", safeName);
                    } catch (NoSuchMethodException e2) {
                        Constructor<?> resCtor = resLocClass.getConstructor(String.class, String.class);
                        resLoc = resCtor.newInstance("continuum", safeName);
                    }
                }

                // Map Operation enum by ordinal
                Object modernOp = operation;
                if (operation instanceof Enum<?> enumVal) {
                    Object[] opConstants = opClass.getEnumConstants();
                    int ord = Math.min(enumVal.ordinal(), opConstants.length - 1);
                    modernOp = opConstants[ord];
                }

                return modernCtor.newInstance(resLoc, amount, modernOp);
            } catch (NoSuchMethodException e) {
                // 2. Legacy constructor: (UUID, String, double, Operation)
                Class<?> opClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
                Constructor<?> legacyCtor = modifierClass.getConstructor(UUID.class, String.class, double.class, opClass);
                return legacyCtor.newInstance(id, name, amount, operation);
            }
        } catch (Throwable t) {
            LOGGER.fine("[AttributeModifierShim] Error constructing AttributeModifier: " + t.getMessage());
            return null;
        }
    }
}
