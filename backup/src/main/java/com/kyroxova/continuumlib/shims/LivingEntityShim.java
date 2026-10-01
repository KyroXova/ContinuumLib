package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for LivingEntity operations across 1.7.9 -> 26.3+.
 * Bridges dynamic Attribute/Holder<Attribute> queries, equipment slot getters and setters.
 */
public final class LivingEntityShim {

    private static final Logger LOGGER = Logger.getLogger(LivingEntityShim.class.getName());

    private LivingEntityShim() {}

    /**
     * Retrieves the numerical value of an attribute on a LivingEntity.
     * Supports both Attribute objects and modern Holder<Attribute> (1.20.5+ / 1.21+).
     */
    public static double getAttributeValue(Object livingEntity, Object attributeOrHolder) {
        if (livingEntity == null || attributeOrHolder == null) return 0.0;

        Object directAttribute = unwrapHolder(attributeOrHolder);

        // 1. Try modern livingEntity.getAttributeValue(Holder<Attribute>) or (Attribute)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getAttributeValue".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Class<?> paramType = m.getParameterTypes()[0];
                        boolean isHolderParam = paramType.getSimpleName().contains("Holder");

                        if (isHolderParam) {
                            try {
                                Object res = m.invoke(livingEntity, attributeOrHolder);
                                if (res instanceof Number n && n.doubleValue() != 0.0) return n.doubleValue();
                                if (res instanceof Number n) {
                                    if (directAttribute != null && directAttribute != attributeOrHolder) {
                                        try {
                                            Object res2 = m.invoke(livingEntity, directAttribute);
                                            if (res2 instanceof Number n2 && n2.doubleValue() != 0.0) return n2.doubleValue();
                                        } catch (Throwable ignored) {}
                                    }
                                    return n.doubleValue();
                                }
                            } catch (Throwable ignored) {
                                if (directAttribute != null && directAttribute != attributeOrHolder) {
                                    try {
                                        Object res2 = m.invoke(livingEntity, directAttribute);
                                        if (res2 instanceof Number n2) return n2.doubleValue();
                                    } catch (Throwable ignored2) {}
                                }
                            }
                        } else {
                            if (directAttribute != null && directAttribute != attributeOrHolder) {
                                try {
                                    Object res = m.invoke(livingEntity, directAttribute);
                                    if (res instanceof Number n && n.doubleValue() != 0.0) return n.doubleValue();
                                    if (res instanceof Number n) return n.doubleValue();
                                } catch (Throwable ignored) {}
                            }
                            try {
                                Object res = m.invoke(livingEntity, attributeOrHolder);
                                if (res instanceof Number n) return n.doubleValue();
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try livingEntity.getAttribute(attribute/holder).getValue()
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getAttribute".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Class<?> paramType = m.getParameterTypes()[0];
                        boolean isHolderParam = paramType.getSimpleName().contains("Holder");
                        Object firstArg = isHolderParam ? attributeOrHolder : (directAttribute != null ? directAttribute : attributeOrHolder);
                        Object secondArg = (firstArg == attributeOrHolder) ? directAttribute : attributeOrHolder;

                        Object instance = null;
                        try {
                            instance = m.invoke(livingEntity, firstArg);
                        } catch (Throwable ignored) {}
                        if (instance == null && secondArg != null) {
                            try {
                                instance = m.invoke(livingEntity, secondArg);
                            } catch (Throwable ignored) {}
                        }
                        if (instance != null) {
                            for (Method valMethod : getAllMethods(instance.getClass())) {
                                if ("getValue".equals(valMethod.getName()) && valMethod.getParameterCount() == 0) {
                                    valMethod.setAccessible(true);
                                    Object val = valMethod.invoke(instance);
                                    if (val instanceof Number n) return n.doubleValue();
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return 0.0;
    }

    /**
     * Retrieves an AttributeInstance from a LivingEntity across versions.
     */
    public static Object getAttribute(Object livingEntity, Object attributeOrHolder) {
        if (livingEntity == null || attributeOrHolder == null) return null;
        Object directAttribute = unwrapHolder(attributeOrHolder);

        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getAttribute".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Class<?> paramType = m.getParameterTypes()[0];
                        boolean isHolderParam = paramType.getSimpleName().contains("Holder");

                        if (isHolderParam) {
                            try {
                                Object res = m.invoke(livingEntity, attributeOrHolder);
                                if (res != null) return res;
                            } catch (Throwable ignored) {}
                            if (directAttribute != null && directAttribute != attributeOrHolder) {
                                try {
                                    Object res = m.invoke(livingEntity, directAttribute);
                                    if (res != null) return res;
                                } catch (Throwable ignored) {}
                            }
                        } else {
                            if (directAttribute != null && directAttribute != attributeOrHolder) {
                                try {
                                    Object res = m.invoke(livingEntity, directAttribute);
                                    if (res != null) return res;
                                } catch (Throwable ignored) {}
                            }
                            try {
                                Object res = m.invoke(livingEntity, attributeOrHolder);
                                if (res != null) return res;
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Retrieves the ItemStack equipped in an EquipmentSlot.
     */
    public static Object getItemBySlot(Object livingEntity, Object equipmentSlot) {
        if (livingEntity == null || equipmentSlot == null) return null;

        // 1. Try modern livingEntity.getItemBySlot(EquipmentSlot)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getItemBySlot".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(livingEntity, equipmentSlot);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try intermediate livingEntity.getItemStackFromSlot(EquipmentSlot)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getItemStackFromSlot".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(livingEntity, equipmentSlot);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try legacy livingEntity.getEquipmentInSlot(int)
        try {
            int slotIndex = resolveSlotIndex(equipmentSlot);
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("getEquipmentInSlot".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(livingEntity, slotIndex);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Sets an ItemStack into an EquipmentSlot on a LivingEntity.
     */
    public static void setItemSlot(Object livingEntity, Object equipmentSlot, Object itemStack) {
        if (livingEntity == null || equipmentSlot == null) return;

        // 1. Try modern livingEntity.setItemSlot(EquipmentSlot, ItemStack)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("setItemSlot".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                        m.invoke(livingEntity, equipmentSlot, itemStack);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try intermediate livingEntity.setItemStackToSlot(EquipmentSlot, ItemStack)
        try {
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("setItemStackToSlot".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                        m.invoke(livingEntity, equipmentSlot, itemStack);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 3. Try legacy livingEntity.setCurrentItemOrArmor(int, ItemStack)
        try {
            int slotIndex = resolveSlotIndex(equipmentSlot);
            for (Method m : getAllMethods(livingEntity.getClass())) {
                if ("setCurrentItemOrArmor".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                        m.invoke(livingEntity, slotIndex, itemStack);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int resolveSlotIndex(Object equipmentSlot) {
        if (equipmentSlot instanceof Number n) return n.intValue();
        if (equipmentSlot instanceof Enum<?> e) {
            String name = e.name().toUpperCase();
            return switch (name) {
                case "MAINHAND" -> 0;
                case "FEET" -> 1;
                case "LEGS" -> 2;
                case "CHEST" -> 3;
                case "HEAD" -> 4;
                case "OFFHAND" -> 5;
                case "BODY" -> 6;
                case "SADDLE" -> 7;
                default -> e.ordinal();
            };
        }
        return 0;
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
