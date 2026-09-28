package com.kyroxova.continuumlib.shims;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Universal Recipe Shim.
 * Bridges legacy Recipe methods (matches, assemble, getRemainingItems, getResultItem)
 * between 1.18.2 (Container/CraftingContainer) and modern 1.20.5+ / 1.21+ (RecipeInput/CraftingInput),
 * and adapts buildCraftingRecipes(Consumer<FinishedRecipe>) to buildRecipes(RecipeOutput).
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

    /**
     * Wraps a legacy Container into a modern RecipeInput / CraftingInput proxy.
     */
    public static Object wrapToRecipeInput(Object legacyContainer) {
        if (legacyContainer == null) return null;

        try {
            Class<?> recipeInputClass = null;
            try {
                recipeInputClass = Class.forName("net.minecraft.world.item.crafting.CraftingInput");
            } catch (ClassNotFoundException e) {
                try {
                    recipeInputClass = Class.forName("net.minecraft.world.item.crafting.RecipeInput");
                } catch (ClassNotFoundException ignored) {}
            }

            if (recipeInputClass == null) return legacyContainer;

            Class<?> finalClass = recipeInputClass;
            return Proxy.newProxyInstance(
                    RecipeShim.class.getClassLoader(),
                    new Class<?>[]{finalClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("size".equals(name) || "getItemCount".equals(name)) {
                            Method sizeMethod = legacyContainer.getClass().getMethod("getContainerSize");
                            return sizeMethod.invoke(legacyContainer);
                        }
                        if ("getItem".equals(name) && args != null && args.length == 1) {
                            Method getItemMethod = legacyContainer.getClass().getMethod("getItem", int.class);
                            return getItemMethod.invoke(legacyContainer, args[0]);
                        }
                        if ("isEmpty".equals(name)) {
                            Method emptyMethod = legacyContainer.getClass().getMethod("isEmpty");
                            return emptyMethod.invoke(legacyContainer);
                        }
                        if ("width".equals(name)) {
                            try {
                                Method w = legacyContainer.getClass().getMethod("getWidth");
                                return w.invoke(legacyContainer);
                            } catch (NoSuchMethodException e) {
                                return 3;
                            }
                        }
                        if ("height".equals(name)) {
                            try {
                                Method h = legacyContainer.getClass().getMethod("getHeight");
                                return h.invoke(legacyContainer);
                            } catch (NoSuchMethodException e) {
                                return 3;
                            }
                        }
                        return null;
                    }
            );
        } catch (Throwable t) {
            LOGGER.fine("[RecipeShim] Error wrapping legacy container: " + t.getMessage());
            return legacyContainer;
        }
    }

    /**
     * Adapts or wraps a modern RecipeOutput into a legacy Consumer<FinishedRecipe> / Consumer<Object>.
     */
    @SuppressWarnings("unchecked")
    public static Consumer<Object> wrapOutput(Object recipeOutput) {
        if (recipeOutput == null) return r -> {};
        if (recipeOutput instanceof Consumer) return (Consumer<Object>) recipeOutput;

        return finishedRecipe -> {
            if (finishedRecipe == null) return;
            try {
                // Call recipeOutput.accept(...) via reflection
                for (Method m : recipeOutput.getClass().getMethods()) {
                    if ("accept".equals(m.getName())) {
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {}
                        if (m.getParameterCount() == 3) {
                            // accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement)
                            Object id = resolveRecipeId(finishedRecipe);
                            m.invoke(recipeOutput, id, finishedRecipe, null);
                            return;
                        } else if (m.getParameterCount() == 2) {
                            // accept(ResourceLocation id, Recipe<?> recipe)
                            Object id = resolveRecipeId(finishedRecipe);
                            m.invoke(recipeOutput, id, finishedRecipe);
                            return;
                        } else if (m.getParameterCount() == 1) {
                            // accept(FinishedRecipe recipe)
                            m.invoke(recipeOutput, finishedRecipe);
                            return;
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.fine("[RecipeShim] Error in wrapOutput accept: " + t.getMessage());
            }
        };
    }

    /**
     * Universal alias for wrapOutput: adapts RecipeOutput into Consumer<FinishedRecipe> / Consumer<Object>.
     */
    public static Consumer<Object> adaptRecipeOutput(Object recipeOutput) {
        return wrapOutput(recipeOutput);
    }

    /**
     * Wraps a legacy Consumer into a modern RecipeOutput proxy (1.20.5+ / 1.21+).
     */
    @SuppressWarnings("unchecked")
    public static Object adaptToRecipeOutput(Object consumerOrOutput) {
        if (consumerOrOutput == null) return null;
        if (!(consumerOrOutput instanceof Consumer<?> consumer)) {
            return consumerOrOutput;
        }

        try {
            Class<?> recipeOutputClass = Class.forName("net.minecraft.data.recipes.RecipeOutput");
            return Proxy.newProxyInstance(
                    RecipeShim.class.getClassLoader(),
                    new Class<?>[]{recipeOutputClass},
                    (proxy, method, args) -> {
                        if ("accept".equals(method.getName()) && args != null) {
                            if (args.length >= 2 && args[1] != null) {
                                ((Consumer<Object>) consumer).accept(args[1]);
                            } else if (args.length == 1 && args[0] != null) {
                                ((Consumer<Object>) consumer).accept(args[0]);
                            }
                        }
                        return null;
                    }
            );
        } catch (Throwable t) {
            return consumerOrOutput;
        }
    }

    private static Object resolveRecipeId(Object finishedRecipe) {
        if (finishedRecipe == null) return null;
        try {
            Method getId = finishedRecipe.getClass().getMethod("getId");
            try {
                getId.setAccessible(true);
            } catch (Throwable ignored) {}
            return getId.invoke(finishedRecipe);
        } catch (Throwable ignored) {}

        try {
            Method idMethod = finishedRecipe.getClass().getMethod("id");
            try {
                idMethod.setAccessible(true);
            } catch (Throwable ignored) {}
            return idMethod.invoke(finishedRecipe);
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Dispatches assemble(...) call safely across legacy and modern signatures.
     */
    public static Object assembleRecipe(Object recipe, Object container, Object provider) {
        if (recipe == null) return null;
        try {
            Class<?> recipeClass = recipe.getClass();
            // Try modern assemble(CraftingInput, HolderLookup.Provider) or (RecipeInput, ...)
            for (Method m : recipeClass.getMethods()) {
                if ("assemble".equals(m.getName()) && m.getParameterCount() == 2) {
                    Object input = wrapToRecipeInput(container);
                    return m.invoke(recipe, input, provider);
                }
            }
            // Try legacy assemble(Container)
            for (Method m : recipeClass.getMethods()) {
                if ("assemble".equals(m.getName()) && m.getParameterCount() == 1) {
                    Object legacy = (container instanceof Proxy) ? container : wrapInput(container);
                    return m.invoke(recipe, legacy);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[RecipeShim] Error dispatching assembleRecipe: " + t.getMessage());
        }
        return null;
    }

    /**
     * Dispatches matches(...) call safely across legacy and modern signatures.
     */
    public static boolean matchesRecipe(Object recipe, Object container, Object level) {
        if (recipe == null) return false;
        try {
            Class<?> recipeClass = recipe.getClass();
            for (Method m : recipeClass.getMethods()) {
                if ("matches".equals(m.getName()) && m.getParameterCount() == 2) {
                    Class<?> firstParam = m.getParameterTypes()[0];
                    if (firstParam.getName().contains("Container")) {
                        Object legacy = (container instanceof Proxy) ? container : wrapInput(container);
                        return (boolean) m.invoke(recipe, legacy, level);
                    } else {
                        Object input = wrapToRecipeInput(container);
                        return (boolean) m.invoke(recipe, input, level);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[RecipeShim] Error dispatching matchesRecipe: " + t.getMessage());
        }
        return false;
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
