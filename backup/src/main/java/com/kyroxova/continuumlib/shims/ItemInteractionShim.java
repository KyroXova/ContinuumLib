package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Item Interaction results across 1.7.9 -> 26.3+.
 * Supports bidirectional conversions between:
 * - 1.7.9: onItemRightClick(ItemStack, World, EntityPlayer) -> ItemStack
 * - 1.18.2: use(Level, Player, InteractionHand) -> InteractionResultHolder<ItemStack>
 * - 26.3+: use(Level, Player, InteractionHand) -> InteractionResult
 */
public final class ItemInteractionShim {

    private static final Logger LOGGER = Logger.getLogger(ItemInteractionShim.class.getName());

    private static final ThreadLocal<Set<Object>> ITEM_USING = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new WeakHashMap<>()));

    private ItemInteractionShim() {}

    public static boolean pushItemUse(Object item) {
        if (item == null) return false;
        return ITEM_USING.get().add(item);
    }

    public static void popItemUse(Object item) {
        if (item != null) {
            ITEM_USING.get().remove(item);
        }
    }

    public static boolean isItemUseActive(Object item) {
        return item != null && ITEM_USING.get().contains(item);
    }

    /**
     * Bridges 1.7.9 onItemRightClick(ItemStack, World, EntityPlayer) -> ItemStack
     * to modern use(...) returning InteractionResultHolder or InteractionResult.
     */
    public static Object bridgeOnItemRightClick(Object item, Object itemStack, Object world, Object player) {
        if (item == null) return itemStack;
        if (isItemUseActive(item)) return itemStack;

        pushItemUse(item);
        try {
            Class<?> clazz = item.getClass();
            Object hand = resolveMainHand();

            // 1. Attempt use(Level, Player, InteractionHand) (3 params)
            for (Method m : clazz.getMethods()) {
                if ("use".equals(m.getName()) && m.getParameterCount() == 3 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(item, world, player, hand);
                    if (res != null) {
                        Object obj = getObject(res);
                        if (obj != null) return obj;
                    }
                    return itemStack;
                }
            }

            // 2. Attempt direct onItemRightClick
            for (Method m : clazz.getMethods()) {
                if ("onItemRightClick".equals(m.getName()) && m.getParameterCount() == 3 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(item, itemStack, world, player);
                    if (res != null) return res;
                    return itemStack;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ItemInteractionShim] Error in bridgeOnItemRightClick: " + t.getMessage());
        } finally {
            popItemUse(item);
        }
        return itemStack;
    }

    /**
     * Bridges 1.18.2 use(Level, Player, InteractionHand) -> InteractionResultHolder
     * to 1.7.9 onItemRightClick or 26.3+ use returning InteractionResult.
     */
    public static Object bridgeUseToHolder(Object item, Object level, Object player, Object hand) {
        if (item == null) return pass(null);
        if (isItemUseActive(item)) return pass(null);

        pushItemUse(item);
        try {
            Class<?> clazz = item.getClass();
            Object heldItem = resolveHeldItem(player);

            // 1. Attempt onItemRightClick(ItemStack, World, Player)
            for (Method m : clazz.getMethods()) {
                if ("onItemRightClick".equals(m.getName()) && m.getParameterCount() == 3 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object newStack = m.invoke(item, heldItem, level, player);
                    return success(newStack != null ? newStack : heldItem);
                }
            }

            // 2. Attempt 26.3+ use returning InteractionResult
            for (Method m : clazz.getMethods()) {
                if ("use".equals(m.getName()) && m.getParameterCount() == 3 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(item, level, player, hand);
                    if (res != null) {
                        return toResultHolder(res, heldItem);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ItemInteractionShim] Error in bridgeUseToHolder: " + t.getMessage());
        } finally {
            popItemUse(item);
        }
        return pass(resolveHeldItem(player));
    }

    /**
     * Bridges 26.3+ use(Level, Player, InteractionHand) -> InteractionResult
     * to 1.18.2 use returning InteractionResultHolder or 1.7.9 onItemRightClick.
     */
    public static Object bridgeUseToResult(Object item, Object level, Object player, Object hand) {
        if (item == null) return resolveInteractionResultEnum("PASS");
        if (isItemUseActive(item)) return resolveInteractionResultEnum("PASS");

        pushItemUse(item);
        try {
            Class<?> clazz = item.getClass();
            Object heldItem = resolveHeldItem(player);

            // 1. Attempt 1.18.2 use returning InteractionResultHolder
            for (Method m : clazz.getMethods()) {
                if ("use".equals(m.getName()) && m.getParameterCount() == 3 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(item, level, player, hand);
                    if (res != null) {
                        return toInteractionResult(res);
                    }
                }
            }

            // 2. Attempt onItemRightClick(ItemStack, World, Player)
            for (Method m : clazz.getMethods()) {
                if ("onItemRightClick".equals(m.getName()) && m.getParameterCount() == 3 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(item, heldItem, level, player);
                    return resolveInteractionResultEnum("SUCCESS");
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ItemInteractionShim] Error in bridgeUseToResult: " + t.getMessage());
        } finally {
            popItemUse(item);
        }
        return resolveInteractionResultEnum("PASS");
    }

    public static Object resolveHeldItem(Object player) {
        return BlockInteractionShim.resolveHeldItem(player);
    }

    public static Object resolveMainHand() {
        return BlockInteractionShim.resolveMainHand();
    }

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

    public static Object resolveInteractionResultEnum(String name) {
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
