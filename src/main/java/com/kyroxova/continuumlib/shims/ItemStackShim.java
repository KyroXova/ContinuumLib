package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for ItemStack NBT and Data Components.
 * In Minecraft 1.20.5+, direct NBT methods on ItemStack (getTag, getOrCreateTag, setTag)
 * were removed in favor of the Data Component system (DataComponents.CUSTOM_DATA).
 * This shim bridges legacy NBT calls to Data Components on 1.20.5+ and 26.x.
 */
public final class ItemStackShim {

    private static final Logger LOGGER = Logger.getLogger(ItemStackShim.class.getName());

    private ItemStackShim() {}

    /**
     * Intercepts: ItemStack.getTag()
     */
    public static Object getTag(Object itemStack) {
        if (itemStack == null) return null;
        try {
            // 1. Try legacy ItemStack.getTag() (<= 1.20.4)
            Method getTagMethod = itemStack.getClass().getMethod("getTag");
            return getTagMethod.invoke(itemStack);
        } catch (NoSuchMethodException e) {
            // 2. Modern 1.20.5+ Data Components
            return getCustomDataNbt(itemStack);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Intercepts: ItemStack.getOrCreateTag()
     */
    public static Object getOrCreateTag(Object itemStack) {
        if (itemStack == null) return null;
        try {
            // 1. Try legacy ItemStack.getOrCreateTag() (<= 1.20.4)
            Method getOrCreateMethod = itemStack.getClass().getMethod("getOrCreateTag");
            return getOrCreateMethod.invoke(itemStack);
        } catch (NoSuchMethodException e) {
            // 2. Modern 1.20.5+ Data Components
            Object nbt = getCustomDataNbt(itemStack);
            if (nbt != null) return nbt;
            return createEmptyCompoundTag();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Intercepts: ItemStack.setTag(CompoundTag)
     */
    public static void setTag(Object itemStack, Object compoundTag) {
        if (itemStack == null) return;
        try {
            // 1. Try legacy ItemStack.setTag(CompoundTag) (<= 1.20.4)
            Method setTagMethod = itemStack.getClass().getMethod("setTag", Class.forName("net.minecraft.nbt.CompoundTag"));
            setTagMethod.invoke(itemStack, compoundTag);
        } catch (NoSuchMethodException e) {
            // 2. Modern 1.20.5+ Data Components
            setCustomDataNbt(itemStack, compoundTag);
        } catch (Throwable ignored) {}
    }

    private static Object getCustomDataNbt(Object itemStack) {
        try {
            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object customDataKey = dataComponentsClass.getField("CUSTOM_DATA").get(null);
            Method getMethod = itemStack.getClass().getMethod("get", customDataKey.getClass().getInterfaces()[0]);
            Object customData = getMethod.invoke(itemStack, customDataKey);
            if (customData != null) {
                Method copyTagMethod = customData.getClass().getMethod("copyTag");
                return copyTagMethod.invoke(customData);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void setCustomDataNbt(Object itemStack, Object compoundTag) {
        try {
            Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
            Method ofMethod = customDataClass.getMethod("of", Class.forName("net.minecraft.nbt.CompoundTag"));
            Object customData = ofMethod.invoke(null, compoundTag);

            Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            Object customDataKey = dataComponentsClass.getField("CUSTOM_DATA").get(null);
            Method setMethod = itemStack.getClass().getMethod("set", customDataKey.getClass().getInterfaces()[0], Object.class);
            setMethod.invoke(itemStack, customDataKey, customData);
        } catch (Throwable ignored) {}
    }

    private static Object createEmptyCompoundTag() {
        try {
            return Class.forName("net.minecraft.nbt.CompoundTag").getConstructor().newInstance();
        } catch (Throwable t) {
            return null;
        }
    }
}
