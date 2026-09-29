package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Polyfill for CommandSource / CommandSourceStack operations across Minecraft 1.7.9 -> 26.3+.
 * Seamlessly bridges:
 * 1. CommandSourceStack.sendSuccess(Component, boolean) (<= 1.19.4)
 *    vs CommandSourceStack.sendSuccess(Supplier<Component>, boolean) (>= 1.20.0 / 26.3+)
 * 2. Legacy CommandSource / ICommandSender sendMessage fallbacks.
 */
public final class CommandShim {

    private static final Logger LOGGER = Logger.getLogger(CommandShim.class.getName());

    private CommandShim() {}

    /**
     * Bridges sendSuccess across versions.
     * Seamlessly handles both Component and Supplier<Component> inputs and targets:
     * - On >= 1.20.0 / 26.3+: invokes sendSuccess(Supplier<Component>, boolean)
     * - On <= 1.19.4: invokes sendSuccess(Component, boolean)
     * - Fallbacks: sendSuccess(Component) or sendMessage(Component)
     */
    public static void sendSuccess(Object source, Object componentOrSupplier, boolean allowLogging) {
        if (source == null || componentOrSupplier == null) return;

        Supplier<?> supplier = (componentOrSupplier instanceof Supplier)
                ? (Supplier<?>) componentOrSupplier
                : () -> componentOrSupplier;

        Object rawComponent = (componentOrSupplier instanceof Supplier)
                ? ((Supplier<?>) componentOrSupplier).get()
                : componentOrSupplier;

        // 1. Scan sendSuccess methods on source (2 parameters)
        for (Method m : getAllMethods(source.getClass())) {
            if ("sendSuccess".equals(m.getName()) && m.getParameterCount() == 2) {
                try {
                    m.setAccessible(true);
                    Class<?> firstParam = m.getParameterTypes()[0];

                    if (Supplier.class.isAssignableFrom(firstParam)) {
                        // Modern 1.20+ / 26.3+: Supplier<Component>
                        m.invoke(source, supplier, allowLogging);
                        return;
                    } else {
                        // Intermediate <= 1.19.4: Component
                        m.invoke(source, rawComponent, allowLogging);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. Legacy fallback: sendSuccess(Component) or sendMessage(Component)
        for (Method m : getAllMethods(source.getClass())) {
            if ("sendSuccess".equals(m.getName()) && m.getParameterCount() == 1) {
                try {
                    m.setAccessible(true);
                    m.invoke(source, rawComponent);
                    return;
                } catch (Throwable ignored) {}
            } else if ("sendMessage".equals(m.getName()) && m.getParameterCount() == 1) {
                try {
                    m.setAccessible(true);
                    m.invoke(source, rawComponent);
                    return;
                } catch (Throwable ignored) {}
            }
        }

        LOGGER.fine("[CommandShim] Unable to invoke sendSuccess on: " + source.getClass().getName());
    }

    /**
     * Bridges sendFailure across versions.
     */
    public static void sendFailure(Object source, Object componentOrSupplier) {
        if (source == null || componentOrSupplier == null) return;

        Object rawComponent = (componentOrSupplier instanceof Supplier)
                ? ((Supplier<?>) componentOrSupplier).get()
                : componentOrSupplier;

        for (Method m : getAllMethods(source.getClass())) {
            if ("sendFailure".equals(m.getName()) && m.getParameterCount() == 1) {
                try {
                    m.setAccessible(true);
                    m.invoke(source, rawComponent);
                    return;
                } catch (Throwable ignored) {}
            }
        }

        // Legacy fallback
        sendSuccess(source, rawComponent, false);
    }

    private static Method[] getAllMethods(Class<?> clazz) {
        Method[] publicMethods = clazz.getMethods();
        Method[] declaredMethods = clazz.getDeclaredMethods();
        Method[] all = new Method[publicMethods.length + declaredMethods.length];
        System.arraycopy(publicMethods, 0, all, 0, publicMethods.length);
        System.arraycopy(declaredMethods, 0, all, publicMethods.length, declaredMethods.length);
        return all;
    }
}
