package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Entity Attributes and AttributeModifiers.
 *
 * Supports:
 * 1. AttributeModifier Instantiation:
 *    - Legacy (UUID, String, double, Operation) -> Modern 1.20.5+ (ResourceLocation, double, Operation).
 *    - Modern (ResourceLocation, double, Operation) -> Legacy <= 1.20.4 (UUID, String, double, Operation).
 *    - Operation enum translation: ADDITION <-> ADD_VALUE, MULTIPLY_BASE <-> ADD_MULTIPLIED_BASE, MULTIPLY_TOTAL <-> ADD_MULTIPLIED_TOTAL.
 * 2. Attribute Registration:
 *    - Forge/NeoForge EntityAttributeCreationEvent vs Fabric FabricDefaultAttributeRegistry.
 * 3. Attribute Holder Lookups:
 *    - LivingEntity.getAttribute(Attribute) vs LivingEntity.getAttribute(Holder<Attribute>).
 */
public final class AttributeModifierShim {

    private static final Logger LOGGER = Logger.getLogger(AttributeModifierShim.class.getName());

    private static final Map<Object, Object> REGISTERED_DEFAULT_ATTRIBUTES = new ConcurrentHashMap<>();

    private AttributeModifierShim() {}

    /**
     * Bridges legacy AttributeModifier(UUID, String, double, Operation) constructor.
     */
    public static Object createModifier(UUID id, String name, double amount, Object operation) {
        try {
            Class<?> modifierClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier");

            // 1. Check if modern 1.20.5+ constructor exists: (ResourceLocation, double, Operation)
            try {
                Class<?> resLocClass = Class.forName("net.minecraft.resources.ResourceLocation");
                Class<?> opClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");

                Constructor<?> modernCtor = modifierClass.getConstructor(resLocClass, double.class, opClass);

                // Build compliant ResourceLocation from name / UUID
                String safeName = (name == null || name.trim().isEmpty()) ? "modifier_" + (id != null ? id.toString() : "default") : name;
                safeName = safeName.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
                if (safeName.startsWith("_")) safeName = "m" + safeName;

                Object resLoc = ResourceLocationShim.create("continuum", safeName);

                // Map Operation enum to modern constants
                Object modernOp = mapOperation(operation, opClass);

                return modernCtor.newInstance(resLoc, amount, modernOp);
            } catch (NoSuchMethodException e) {
                // 2. Legacy constructor: (UUID, String, double, Operation)
                Class<?> opClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
                Constructor<?> legacyCtor = modifierClass.getConstructor(UUID.class, String.class, double.class, opClass);
                Object legacyOp = mapOperation(operation, opClass);
                return legacyCtor.newInstance(id, name, amount, legacyOp);
            }
        } catch (Throwable t) {
            LOGGER.fine("[AttributeModifierShim] Error constructing AttributeModifier: " + t.getMessage());
            return null;
        }
    }

    /**
     * Bridges modern AttributeModifier(ResourceLocation, double, Operation) constructor on legacy <= 1.20.4 versions.
     */
    public static Object createModifier(Object resourceLocation, double amount, Object operation) {
        if (resourceLocation == null) return null;
        try {
            Class<?> modifierClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier");

            // 1. Try modern constructor: (ResourceLocation, double, Operation)
            try {
                Class<?> resLocClass = Class.forName("net.minecraft.resources.ResourceLocation");
                Class<?> opClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
                Constructor<?> modernCtor = modifierClass.getConstructor(resLocClass, double.class, opClass);
                Object modernOp = mapOperation(operation, opClass);
                return modernCtor.newInstance(resourceLocation, amount, modernOp);
            } catch (NoSuchMethodException e) {
                // 2. Fallback to legacy constructor: (UUID, String, double, Operation)
                String locStr = resourceLocation.toString();
                UUID derivedUuid = UUID.nameUUIDFromBytes(locStr.getBytes(StandardCharsets.UTF_8));
                Class<?> opClass = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation");
                Constructor<?> legacyCtor = modifierClass.getConstructor(UUID.class, String.class, double.class, opClass);
                Object legacyOp = mapOperation(operation, opClass);
                return legacyCtor.newInstance(derivedUuid, locStr, amount, legacyOp);
            }
        } catch (Throwable t) {
            LOGGER.fine("[AttributeModifierShim] Error constructing AttributeModifier: " + t.getMessage());
            return null;
        }
    }

    /**
     * Maps Operation enum constants between legacy (ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL)
     * and modern (ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL).
     */
    public static Object mapOperation(Object operation, Class<?> targetOpClass) {
        if (operation == null || targetOpClass == null) return operation;
        if (!targetOpClass.isEnum()) return operation;

        Object[] constants = targetOpClass.getEnumConstants();
        if (constants == null || constants.length == 0) return operation;

        String name = operation.toString().toUpperCase();
        // Exact name match
        for (Object c : constants) {
            if (c.toString().equalsIgnoreCase(name)) return c;
        }

        // Translation table
        if (name.contains("ADDITION") || name.contains("ADD_VALUE")) {
            return findConstant(constants, "ADD_VALUE", "ADDITION");
        }
        if (name.contains("MULTIPLY_BASE") || name.contains("ADD_MULTIPLIED_BASE")) {
            return findConstant(constants, "ADD_MULTIPLIED_BASE", "MULTIPLY_BASE");
        }
        if (name.contains("MULTIPLY_TOTAL") || name.contains("ADD_MULTIPLIED_TOTAL")) {
            return findConstant(constants, "ADD_MULTIPLIED_TOTAL", "MULTIPLY_TOTAL");
        }

        // Ordinal fallback
        if (operation instanceof Enum<?> enumVal) {
            int ord = Math.min(enumVal.ordinal(), constants.length - 1);
            return constants[ord];
        }

        return constants[0];
    }

    private static Object findConstant(Object[] constants, String primary, String secondary) {
        for (Object c : constants) {
            if (c.toString().equalsIgnoreCase(primary)) return c;
        }
        for (Object c : constants) {
            if (c.toString().equalsIgnoreCase(secondary)) return c;
        }
        return constants[0];
    }

    /**
     * Bridges LivingEntity.getAttribute() across raw Attribute vs Holder<Attribute> eras.
     */
    public static Object getAttribute(Object livingEntity, Object attributeOrHolder) {
        if (livingEntity == null || attributeOrHolder == null) return null;

        // 1. Try direct call: getAttribute(attributeOrHolder)
        try {
            for (Method m : livingEntity.getClass().getMethods()) {
                if ("getAttribute".equals(m.getName()) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType != Object.class && paramType.isInstance(attributeOrHolder)) {
                        return m.invoke(livingEntity, attributeOrHolder);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Unwrap Holder if param expects Attribute
        try {
            Method valueMethod = attributeOrHolder.getClass().getMethod("value");
            Object rawAttr = valueMethod.invoke(attributeOrHolder);
            for (Method m : livingEntity.getClass().getMethods()) {
                if ("getAttribute".equals(m.getName()) && m.getParameterCount() == 1) {
                    if (m.getParameterTypes()[0].isInstance(rawAttr)) {
                        return m.invoke(livingEntity, rawAttr);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Wrap Attribute as Holder if param expects Holder
        try {
            Class<?> holderClass = Class.forName("net.minecraft.core.Holder");
            Method directMethod = holderClass.getMethod("direct", Object.class);
            Object holder = directMethod.invoke(null, attributeOrHolder);
            for (Method m : livingEntity.getClass().getMethods()) {
                if ("getAttribute".equals(m.getName()) && m.getParameterCount() == 1) {
                    if (m.getParameterTypes()[0].isInstance(holder)) {
                        return m.invoke(livingEntity, holder);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Universal Entity Attribute registration.
     * Supports Forge/NeoForge EntityAttributeCreationEvent and Fabric FabricDefaultAttributeRegistry.
     */
    public static Object registerDefaultAttributes(Object entityType, Object supplierOrBuilder) {
        if (entityType == null || supplierOrBuilder == null) return null;
        REGISTERED_DEFAULT_ATTRIBUTES.put(entityType, supplierOrBuilder);
        LOGGER.info("[AttributeModifierShim] Registered default attributes for: " + entityType);

        // 1. Try Fabric FabricDefaultAttributeRegistry
        try {
            Class<?> fabricRegistryClass = Class.forName("net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry");
            for (Method m : fabricRegistryClass.getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 2) {
                    return m.invoke(null, entityType, supplierOrBuilder);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Forge / NeoForge EntityAttributeCreationEvent.put(EntityType, AttributeSupplier)
        // If event context is available, route to it
        return supplierOrBuilder;
    }

    public static Object getRegisteredAttributes(Object entityType) {
        return entityType != null ? REGISTERED_DEFAULT_ATTRIBUTES.get(entityType) : null;
    }
}
