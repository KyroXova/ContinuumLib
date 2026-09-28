package com.kyroxova.continuumlib.shims;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

/**
 * Universal Recipe Shim.
 * Bridges legacy Recipe methods (matches, assemble, getRemainingItems, getResultItem)
 * between 1.18.2 (Container/CraftingContainer) and modern 1.20.5+ / 1.21+ (RecipeInput/CraftingInput).
 */
public final class RecipeShim {

    private static final Logger LOGGER = Logger.getLogger(RecipeShim.class.getName());

    private RecipeShim() {}

    /**
     * Wraps a modern RecipeInput or CraftingInput into a legacy Container interface proxy.
     */
    public static Object wrapInput(Object modernInput) {
        if (modernInput == null) return null;

        try {
            Class<?> containerClass = Class.forName("net.minecraft.world.Container");
            Class<?> craftingContainerClass = null;
            try {
                craftingContainerClass = Class.forName("net.minecraft.world.inventory.CraftingContainer");
            } catch (ClassNotFoundException ignored) {}

            Class<?>[] interfaces = craftingContainerClass != null && craftingContainerClass.isInterface()
                    ? new Class<?>[]{craftingContainerClass, containerClass}
                    : new Class<?>[]{containerClass};

            return Proxy.newProxyInstance(
                    RecipeShim.class.getClassLoader(),
                    interfaces,
                    new RecipeInputInvocationHandler(modernInput)
            );
        } catch (Throwable t) {
            LOGGER.fine("[RecipeShim] Error wrapping modern recipe input: " + t.getMessage());
            return modernInput;
        }
    }

    private static class RecipeInputInvocationHandler implements InvocationHandler {
        private final Object target;

        public RecipeInputInvocationHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            // 1. getContainerSize() / size()
            if ("getContainerSize".equals(name) || "size".equals(name)) {
                try {
                    Method sizeMethod = target.getClass().getMethod("size");
                    return sizeMethod.invoke(target);
                } catch (NoSuchMethodException e) {
                    return 0;
                }
            }

            // 2. getItem(int)
            if ("getItem".equals(name) && args != null && args.length == 1 && args[0] instanceof Integer) {
                try {
                    Method getItemMethod = target.getClass().getMethod("getItem", int.class);
                    return getItemMethod.invoke(target, args[0]);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            }

            // 3. getWidth()
            if ("getWidth".equals(name)) {
                try {
                    Method widthMethod = target.getClass().getMethod("width");
                    return widthMethod.invoke(target);
                } catch (NoSuchMethodException e) {
                    return 3;
                }
            }

            // 4. getHeight()
            if ("getHeight".equals(name)) {
                try {
                    Method heightMethod = target.getClass().getMethod("height");
                    return heightMethod.invoke(target);
                } catch (NoSuchMethodException e) {
                    return 3;
                }
            }

            // 5. isEmpty()
            if ("isEmpty".equals(name)) {
                try {
                    Method isEmptyMethod = target.getClass().getMethod("isEmpty");
                    return isEmptyMethod.invoke(target);
                } catch (NoSuchMethodException ignored) {}
                return false;
            }

            // 6. toString, hashCode, equals
            if ("toString".equals(name)) {
                return "RecipeInputContainerWrapper[" + target.toString() + "]";
            }
            if ("hashCode".equals(name)) {
                return target.hashCode();
            }
            if ("equals".equals(name) && args != null && args.length == 1) {
                return target.equals(args[0]);
            }

            // Default fallback for unused Container methods (clearContent, setItem, etc.)
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class) return null;
            if (returnType == boolean.class) return false;
            if (returnType == int.class) return 0;
            return null;
        }
    }
}
