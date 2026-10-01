package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Advancement and AdvancementHolder across 1.12 -> 26.3+.
 * In 1.20.2+, AdvancementHolder was introduced as a record containing (ResourceLocation id, Advancement value),
 * replacing direct getId() on Advancement.
 */
public final class AdvancementShim {

    private static final Logger LOGGER = Logger.getLogger(AdvancementShim.class.getName());

    private AdvancementShim() {}

    /**
     * Virtual representation of an AdvancementHolder for pre-1.20.2 environments.
     */
    public record VirtualAdvancementHolder(Object id, Object value) {
        public Object getId() {
            return id;
        }

        public Object getAdvancement() {
            return value;
        }
    }

    /**
     * Unwraps an AdvancementHolder into an Advancement, or returns the advancement itself.
     */
    public static Object unwrap(Object advancementOrHolder) {
        return unwrapHolder(advancementOrHolder);
    }

    public static Object unwrapHolder(Object advancementOrHolder) {
        if (advancementOrHolder == null) return null;
        try {
            for (Method m : advancementOrHolder.getClass().getMethods()) {
                if (("value".equals(m.getName()) || "getAdvancement".equals(m.getName())) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object res = m.invoke(advancementOrHolder);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}
        return advancementOrHolder;
    }

    /**
     * Extracts the ResourceLocation id from an Advancement or AdvancementHolder.
     */
    public static Object getId(Object advancementOrHolder) {
        if (advancementOrHolder == null) return null;

        // 1. Try AdvancementHolder.id() / getId()
        try {
            for (Method m : advancementOrHolder.getClass().getMethods()) {
                if (("id".equals(m.getName()) || "getId".equals(m.getName())) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object res = m.invoke(advancementOrHolder);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try on unwrapped advancement
        Object unwrapped = unwrapHolder(advancementOrHolder);
        if (unwrapped != null && unwrapped != advancementOrHolder) {
            try {
                for (Method m : unwrapped.getClass().getMethods()) {
                    if (("id".equals(m.getName()) || "getId".equals(m.getName())) && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        Object res = m.invoke(unwrapped);
                        if (res != null) return res;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    /**
     * Wraps an id and Advancement into an AdvancementHolder (or VirtualAdvancementHolder).
     */
    public static Object wrapHolder(Object id, Object advancement) {
        if (id == null || advancement == null) return null;
        try {
            Class<?> holderClass = Class.forName("net.minecraft.advancements.AdvancementHolder");
            for (Constructor<?> ctor : holderClass.getConstructors()) {
                if (ctor.getParameterCount() == 2) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(id, advancement);
                }
            }
        } catch (Throwable ignored) {}

        return new VirtualAdvancementHolder(id, advancement);
    }

    /**
     * Builds an advancement using an Advancement.Builder across versions.
     */
    public static Object build(Object builder, Object id) {
        if (builder == null) return null;

        // 1. Try builder.build(id)
        try {
            for (Method m : builder.getClass().getMethods()) {
                if ("build".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    return m.invoke(builder, id);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try builder.build() without id, then wrap
        try {
            for (Method m : builder.getClass().getMethods()) {
                if ("build".equals(m.getName()) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    Object adv = m.invoke(builder);
                    if (adv != null) {
                        return (id != null) ? wrapHolder(id, adv) : adv;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
