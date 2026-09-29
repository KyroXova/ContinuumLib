package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Universal Polyfill for MobEffect / PotionEffect queries across 1.7.9 -> 26.3+.
 * Dynamically bridges MobEffect instances and modern Holder<MobEffect> (1.20.5+ / 1.21+),
 * as well as legacy Potion/PotionEffect methods (<= 1.12.2).
 */
public final class MobEffectShim {

    private static final Logger LOGGER = Logger.getLogger(MobEffectShim.class.getName());

    private MobEffectShim() {}

    /**
     * Checks if a LivingEntity has the specified mob effect or effect holder.
     */
    public static boolean hasEffect(Object livingEntity, Object mobEffectOrHolder) {
        if (livingEntity == null || mobEffectOrHolder == null) return false;

        Object directEffect = unwrapHolder(mobEffectOrHolder);

        // 1. Try modern livingEntity.hasEffect(Holder<MobEffect>) or (MobEffect)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("hasEffect".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Object res = null;
                        try {
                            res = m.invoke(livingEntity, mobEffectOrHolder);
                            if (res instanceof Boolean b && b) return true;
                        } catch (Throwable ignored) {}

                        if (directEffect != null && directEffect != mobEffectOrHolder) {
                            try {
                                Object res2 = m.invoke(livingEntity, directEffect);
                                if (res2 instanceof Boolean b && b) return true;
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy livingEntity.isPotionActive(Potion) (<= 1.12.2)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("isPotionActive".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Object target = directEffect != null ? directEffect : mobEffectOrHolder;
                        Object res = m.invoke(livingEntity, target);
                        if (res instanceof Boolean b && b) return true;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fallback: inspect getActiveEffects() / getActivePotionEffects()
        try {
            Collection<?> effects = getActiveEffects(livingEntity);
            if (effects != null) {
                for (Object inst : effects) {
                    if (inst != null && matchesEffectInstance(inst, mobEffectOrHolder, directEffect)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * Retrieves the MobEffectInstance from a LivingEntity.
     */
    public static Object getEffect(Object livingEntity, Object mobEffectOrHolder) {
        if (livingEntity == null || mobEffectOrHolder == null) return null;

        Object directEffect = unwrapHolder(mobEffectOrHolder);

        // 1. Try modern livingEntity.getEffect(Holder<MobEffect>) or (MobEffect)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getEffect".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Object res = null;
                        try {
                            res = m.invoke(livingEntity, mobEffectOrHolder);
                            if (res != null) return res;
                        } catch (Throwable ignored) {}

                        if (directEffect != null && directEffect != mobEffectOrHolder) {
                            try {
                                Object res2 = m.invoke(livingEntity, directEffect);
                                if (res2 != null) return res2;
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy livingEntity.getActivePotionEffect(Potion)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getActivePotionEffect".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Object target = directEffect != null ? directEffect : mobEffectOrHolder;
                        Object res = m.invoke(livingEntity, target);
                        if (res != null) return res;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fallback: query active effects collection
        try {
            Collection<?> effects = getActiveEffects(livingEntity);
            if (effects != null) {
                for (Object inst : effects) {
                    if (inst != null && matchesEffectInstance(inst, mobEffectOrHolder, directEffect)) {
                        return inst;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Adds a MobEffectInstance to a LivingEntity.
     */
    public static boolean addEffect(Object livingEntity, Object mobEffectInstance) {
        if (livingEntity == null || mobEffectInstance == null) return false;

        // 1. Try livingEntity.addEffect(MobEffectInstance)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("addEffect".equals(m.getName())) {
                    try {
                        m.setAccessible(true);
                        if (m.getParameterCount() == 1) {
                            Object res = m.invoke(livingEntity, mobEffectInstance);
                            if (res instanceof Boolean b) return b;
                            return true;
                        } else if (m.getParameterCount() == 2) {
                            Object res = m.invoke(livingEntity, mobEffectInstance, null);
                            if (res instanceof Boolean b) return b;
                            return true;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy livingEntity.addPotionEffect(PotionEffect)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("addPotionEffect".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        m.invoke(livingEntity, mobEffectInstance);
                        return true;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * Removes a MobEffect or Holder<MobEffect> from a LivingEntity.
     */
    public static boolean removeEffect(Object livingEntity, Object mobEffectOrHolder) {
        if (livingEntity == null || mobEffectOrHolder == null) return false;

        Object directEffect = unwrapHolder(mobEffectOrHolder);

        // 1. Try modern livingEntity.removeEffect(Holder<MobEffect>) or (MobEffect)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("removeEffect".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        try {
                            Object res = m.invoke(livingEntity, mobEffectOrHolder);
                            if (res instanceof Boolean b && b) return true;
                        } catch (Throwable ignored) {}

                        if (directEffect != null && directEffect != mobEffectOrHolder) {
                            try {
                                Object res2 = m.invoke(livingEntity, directEffect);
                                if (res2 instanceof Boolean b && b) return true;
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy livingEntity.removePotionEffect(Potion)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("removePotionEffect".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Object target = directEffect != null ? directEffect : mobEffectOrHolder;
                        m.invoke(livingEntity, target);
                        return true;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static Collection<?> getActiveEffects(Object livingEntity) {
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if (("getActiveEffects".equals(m.getName()) || "getActivePotionEffects".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(livingEntity);
                    if (res instanceof Collection<?> c) return c;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean matchesEffectInstance(Object inst, Object holder, Object direct) {
        try {
            for (Method m : getAllMethods(inst.getClass())) {
                if (("getEffect".equals(m.getName()) || "getPotion".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object effect = m.invoke(inst);
                    if (Objects.equals(effect, holder) || Objects.equals(effect, direct)) return true;
                    if (effect != null && direct != null && Objects.equals(unwrapHolder(effect), direct)) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Object unwrapHolder(Object holderOrObject) {
        if (holderOrObject == null) return null;
        if (holderOrObject instanceof String || holderOrObject instanceof Number || holderOrObject instanceof Boolean) {
            return holderOrObject;
        }
        if (!isHolderClass(holderOrObject.getClass())) {
            return holderOrObject;
        }
        try {
            for (Method m : holderOrObject.getClass().getMethods()) {
                if ("value".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(holderOrObject);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return holderOrObject;
    }

    private static boolean isHolderClass(Class<?> clazz) {
        if (clazz == null) return false;
        if (clazz.getName().contains("Holder")) return true;
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.getName().contains("Holder")) return true;
        }
        return false;
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
