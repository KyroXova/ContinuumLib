package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Item Interaction results across 1.7.9 -> 26.3+.
 * Adapts InteractionResultHolder<ItemStack> (< 1.21.2) <-> InteractionResult (1.21.2+)
 * for Item.use, useOn, and finishUsingItem across Forge, NeoForge, and Fabric.
 */
public final class ItemInteractionShim {

    private static final Logger LOGGER = Logger.getLogger(ItemInteractionShim.class.getName());

    private ItemInteractionShim() {}

    /**
     * Creates a success result holder across versions.
     */
    public static Object success(Object itemStack) {
        return createResultHolder("success", "SUCCESS", itemStack);
    }

    /**
     * Creates a consume result holder across versions.
     */
    public static Object consume(Object itemStack) {
        return createResultHolder("consume", "CONSUME", itemStack);
    }

    /**
     * Creates a pass result holder across versions.
     */
    public static Object pass(Object itemStack) {
        return createResultHolder("pass", "PASS", itemStack);
    }

    /**
     * Creates a fail result holder across versions.
     */
    public static Object fail(Object itemStack) {
        return createResultHolder("fail", "FAIL", itemStack);
    }

    /**
     * Creates a sidedSuccess result holder across versions.
     */
    public static Object sidedSuccess(Object itemStack, boolean isClientSide) {
        try {
            Class<?> holderClass = Class.forName("net.minecraft.world.InteractionResultHolder");
            for (Method m : holderClass.getMethods()) {
                if ("sidedSuccess".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(null, itemStack, isClientSide);
                }
            }
        } catch (Throwable ignored) {}

        String resultType = isClientSide ? "SUCCESS" : "CONSUME";
        return new VirtualInteractionResultHolder(resultType, itemStack);
    }

    /**
     * Adapts an InteractionResultHolder to an InteractionResult (for 1.21.2+ consumers).
     */
    public static Object toInteractionResult(Object resultHolder) {
        if (resultHolder == null) return resolveInteractionResultEnum("PASS");

        // 1. If it's already an InteractionResult enum, return it
        Class<?> irClass = getInteractionResultClass();
        if (irClass != null && irClass.isInstance(resultHolder)) {
            return resultHolder;
        }

        // 2. If it has getResult(), call it
        try {
            for (Method m : resultHolder.getClass().getMethods()) {
                if ("getResult".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(resultHolder);
                }
            }
        } catch (Throwable ignored) {}

        // 3. If it's VirtualInteractionResultHolder
        if (resultHolder instanceof VirtualInteractionResultHolder vrh) {
            return resolveInteractionResultEnum(vrh.getResult());
        }

        return resolveInteractionResultEnum("PASS");
    }

    /**
     * Adapts an InteractionResult + ItemStack to an InteractionResultHolder (for <= 1.21.1 consumers).
     */
    public static Object toResultHolder(Object interactionResult, Object itemStack) {
        if (interactionResult == null) return pass(itemStack);

        String resultName = (interactionResult instanceof Enum<?> e) ? e.name() : String.valueOf(interactionResult);
        return switch (resultName.toUpperCase()) {
            case "SUCCESS" -> success(itemStack);
            case "CONSUME", "CONSUME_PARTIAL" -> consume(itemStack);
            case "FAIL" -> fail(itemStack);
            default -> pass(itemStack);
        };
    }

    /**
     * Extracts the result status from either an InteractionResultHolder, InteractionResult, or Virtual holder.
     */
    public static Object getResult(Object resultOrHolder) {
        if (resultOrHolder == null) return "PASS";

        try {
            for (Method m : resultOrHolder.getClass().getMethods()) {
                if ("getResult".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(resultOrHolder);
                }
            }
        } catch (Throwable ignored) {}

        if (resultOrHolder instanceof VirtualInteractionResultHolder vrh) {
            return vrh.getResult();
        }

        if (resultOrHolder instanceof Enum<?> e) {
            return e.name();
        }

        return resultOrHolder;
    }

    /**
     * Extracts the item stack payload from a result holder.
     */
    public static Object getObject(Object resultOrHolder) {
        if (resultOrHolder == null) return null;

        try {
            for (Method m : resultOrHolder.getClass().getMethods()) {
                if ("getObject".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(resultOrHolder);
                }
            }
        } catch (Throwable ignored) {}

        if (resultOrHolder instanceof VirtualInteractionResultHolder vrh) {
            return vrh.getObject();
        }

        return null;
    }

    private static Object createResultHolder(String methodName, String enumName, Object itemStack) {
        try {
            // 1. Modern InteractionResultHolder (1.17 - 1.21.1)
            Class<?> holderClass = Class.forName("net.minecraft.world.InteractionResultHolder");
            for (Method m : holderClass.getMethods()) {
                if (methodName.equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(null, itemStack);
                }
            }
        } catch (Throwable ignored) {}

        try {
            // 2. Legacy ActionResult (<= 1.16.5)
            Class<?> arClass = Class.forName("net.minecraft.util.ActionResult");
            for (Method m : arClass.getMethods()) {
                if (methodName.equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(null, itemStack);
                }
            }
        } catch (Throwable ignored) {}

        return new VirtualInteractionResultHolder(enumName, itemStack);
    }

    private static Class<?> getInteractionResultClass() {
        try {
            return Class.forName("net.minecraft.world.InteractionResult");
        } catch (Throwable ignored) {}
        try {
            return Class.forName("net.minecraft.util.ActionResultType");
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object resolveInteractionResultEnum(String name) {
        Class<?> irClass = getInteractionResultClass();
        if (irClass != null && irClass.isEnum()) {
            for (Object constant : irClass.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
        }
        return name;
    }

    /**
     * Fallback standalone VirtualInteractionResultHolder for non-MC environments and unit tests.
     */
    public static class VirtualInteractionResultHolder {
        private final String result;
        private final Object object;

        public VirtualInteractionResultHolder(String result, Object object) {
            this.result = result;
            this.object = object;
        }

        public String getResult() {
            return result;
        }

        public Object getObject() {
            return object;
        }

        @Override
        public String toString() {
            return "VirtualInteractionResultHolder[" + result + ", " + object + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VirtualInteractionResultHolder that)) return false;
            return Objects.equals(result, that.result) && Objects.equals(object, that.object);
        }

        @Override
        public int hashCode() {
            return Objects.hash(result, object);
        }
    }
}
