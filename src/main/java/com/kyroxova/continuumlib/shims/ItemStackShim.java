package com.kyroxova.continuumlib.shims;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for ItemStack NBT and Modern Data Components.
 *
 * Provides bidirectional translation across all Minecraft versions (1.7.9 -> 26.3+):
 * 1. Legacy to Modern (1.20.5+):
 *    Translates ItemStack.getTag(), getOrCreateTag(), setTag(), hasTag(), removeTagKey()
 *    to DataComponents.CUSTOM_DATA (net.minecraft.world.item.component.CustomData).
 * 2. Modern to Legacy (pre-1.20.5):
 *    Translates modern ItemStack.get(DataComponentType), set(...), has(...), remove(...)
 *    to root CompoundTag and dedicated sub-compounds on legacy versions.
 */
public final class ItemStackShim {

    private static final Logger LOGGER = Logger.getLogger(ItemStackShim.class.getName());

    // Subcompound key used to store arbitrary data components on pre-1.20.5 item tags
    private static final String CONTINUUM_COMPONENTS_KEY = "ContinuumComponents";

    private ItemStackShim() {}

    // =========================================================================
    // Legacy NBT API -> Modern Data Components (1.20.5+)
    // =========================================================================

    /**
     * Intercepts: ItemStack.getTag()
     * On <= 1.20.4: invokes underlying getTag().
     * On 1.20.5+: extracts root tag from DataComponents.CUSTOM_DATA.
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
     * On <= 1.20.4: invokes underlying getOrCreateTag().
     * On 1.20.5+: retrieves CUSTOM_DATA or initializes a new CompoundTag and binds it.
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
            Object emptyTag = createEmptyCompoundTag();
            if (emptyTag != null) {
                setCustomDataNbt(itemStack, emptyTag);
                return emptyTag;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Intercepts: ItemStack.setTag(CompoundTag)
     * On <= 1.20.4: invokes underlying setTag(CompoundTag).
     * On 1.20.5+: binds CompoundTag into DataComponents.CUSTOM_DATA.
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

    /**
     * Intercepts: ItemStack.hasTag()
     * On <= 1.20.4: invokes underlying hasTag().
     * On 1.20.5+: checks presence of DataComponents.CUSTOM_DATA.
     */
    public static boolean hasTag(Object itemStack) {
        if (itemStack == null) return false;
        try {
            // 1. Try legacy ItemStack.hasTag() (<= 1.20.4)
            Method hasTagMethod = itemStack.getClass().getMethod("hasTag");
            return (boolean) hasTagMethod.invoke(itemStack);
        } catch (NoSuchMethodException e) {
            // 2. Modern 1.20.5+ Data Components
            return getCustomDataNbt(itemStack) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Intercepts: ItemStack.removeTagKey(String)
     * On <= 1.20.4: invokes underlying removeTagKey(key).
     * On 1.20.5+: removes key from DataComponents.CUSTOM_DATA.
     */
    public static void removeTagKey(Object itemStack, String key) {
        if (itemStack == null || key == null) return;
        try {
            Method removeKeyMethod = itemStack.getClass().getMethod("removeTagKey", String.class);
            removeKeyMethod.invoke(itemStack, key);
        } catch (NoSuchMethodException e) {
            Object nbt = getCustomDataNbt(itemStack);
            if (nbt != null) {
                try {
                    Method removeMethod = nbt.getClass().getMethod("remove", String.class);
                    removeMethod.invoke(nbt, key);
                    setCustomDataNbt(itemStack, nbt);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // Modern Data Components API -> Pre-1.20.5 NBT Translation
    // =========================================================================

    /**
     * Intercepts: ItemStack.get(DataComponentType)
     * On 1.20.5+: invokes modern stack.get(componentType).
     * On <= 1.20.4: reads corresponding value from root CompoundTag.
     */
    public static Object get(Object itemStack, Object componentType) {
        if (itemStack == null || componentType == null) return null;

        // 1. Try modern ItemStack.get(DataComponentType) (1.20.5+)
        try {
            for (Method m : itemStack.getClass().getMethods()) {
                if ("get".equals(m.getName()) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType.isInstance(componentType)) {
                        return m.invoke(itemStack, componentType);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Pre-1.20.5 NBT fallback
        Object tag = getTag(itemStack);
        if (tag == null) return null;

        String key = getComponentKey(componentType);

        // Check CUSTOM_DATA
        if (isCustomDataComponent(key)) {
            return wrapCustomData(tag);
        }

        // Check DAMAGE
        if ("damage".equals(key) || "minecraft:damage".equals(key)) {
            try {
                Method getInt = tag.getClass().getMethod("getInt", String.class);
                return getInt.invoke(tag, "Damage");
            } catch (Throwable ignored) {}
        }

        // Check CUSTOM_NAME
        if ("custom_name".equals(key) || "minecraft:custom_name".equals(key)) {
            try {
                Method getCompound = tag.getClass().getMethod("getCompound", String.class);
                Object displayTag = getCompound.invoke(tag, "display");
                if (displayTag != null) {
                    Method getString = displayTag.getClass().getMethod("getString", String.class);
                    return getString.invoke(displayTag, "Name");
                }
            } catch (Throwable ignored) {}
        }

        // Check LORE
        if ("lore".equals(key) || "minecraft:lore".equals(key)) {
            try {
                Method getCompound = tag.getClass().getMethod("getCompound", String.class);
                Object displayTag = getCompound.invoke(tag, "display");
                if (displayTag != null) {
                    Method getList = displayTag.getClass().getMethod("getList", String.class, int.class);
                    return getList.invoke(displayTag, "Lore", 8);
                }
            } catch (Throwable ignored) {}
        }

        // General component lookup in ContinuumComponents sub-compound
        try {
            Method getCompound = tag.getClass().getMethod("getCompound", String.class);
            Object compTag = getCompound.invoke(tag, CONTINUUM_COMPONENTS_KEY);
            if (compTag != null) {
                Method getMethod = compTag.getClass().getMethod("get", String.class);
                return getMethod.invoke(compTag, key);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /**
     * Intercepts: ItemStack.set(DataComponentType, Object)
     * On 1.20.5+: invokes modern stack.set(componentType, value).
     * On <= 1.20.4: writes corresponding value to root CompoundTag.
     */
    public static Object set(Object itemStack, Object componentType, Object value) {
        if (itemStack == null || componentType == null) return null;

        // 1. Try modern ItemStack.set(DataComponentType, Object) (1.20.5+)
        try {
            for (Method m : itemStack.getClass().getMethods()) {
                if ("set".equals(m.getName()) && m.getParameterCount() == 2) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType.isInstance(componentType)) {
                        return m.invoke(itemStack, componentType, value);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Pre-1.20.5 NBT fallback
        Object tag = getOrCreateTag(itemStack);
        if (tag == null) return value;

        String key = getComponentKey(componentType);

        // Set CUSTOM_DATA
        if (isCustomDataComponent(key)) {
            if (value != null) {
                Object extractedTag = extractTagFromCustomData(value);
                setTag(itemStack, extractedTag != null ? extractedTag : value);
            }
            return value;
        }

        // Set DAMAGE
        if (("damage".equals(key) || "minecraft:damage".equals(key)) && value instanceof Number num) {
            try {
                Method putInt = tag.getClass().getMethod("putInt", String.class, int.class);
                putInt.invoke(tag, "Damage", num.intValue());
            } catch (Throwable ignored) {}
            return value;
        }

        // Set CUSTOM_NAME
        if ("custom_name".equals(key) || "minecraft:custom_name".equals(key)) {
            try {
                Method getCompound = tag.getClass().getMethod("getCompound", String.class);
                Object displayTag = getCompound.invoke(tag, "display");
                if (displayTag == null) {
                    displayTag = createEmptyCompoundTag();
                    Method put = tag.getClass().getMethod("put", String.class, Class.forName("net.minecraft.nbt.Tag"));
                    put.invoke(tag, "display", displayTag);
                }
                Method putString = displayTag.getClass().getMethod("putString", String.class, String.class);
                putString.invoke(displayTag, "Name", String.valueOf(value));
            } catch (Throwable ignored) {}
            return value;
        }

        // General component store in ContinuumComponents sub-compound
        try {
            Method getCompound = tag.getClass().getMethod("getCompound", String.class);
            Object compTag = getCompound.invoke(tag, CONTINUUM_COMPONENTS_KEY);
            if (compTag == null) {
                compTag = createEmptyCompoundTag();
                Method put = tag.getClass().getMethod("put", String.class, Class.forName("net.minecraft.nbt.Tag"));
                put.invoke(tag, CONTINUUM_COMPONENTS_KEY, compTag);
            }
            if (value != null) {
                // Store string representation or tag
                Method putString = compTag.getClass().getMethod("putString", String.class, String.class);
                putString.invoke(compTag, key, String.valueOf(value));
            }
        } catch (Throwable ignored) {}

        return value;
    }

    /**
     * Intercepts: ItemStack.has(DataComponentType)
     */
    public static boolean has(Object itemStack, Object componentType) {
        if (itemStack == null || componentType == null) return false;

        // 1. Try modern ItemStack.has(DataComponentType)
        try {
            for (Method m : itemStack.getClass().getMethods()) {
                if ("has".equals(m.getName()) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType.isInstance(componentType)) {
                        return (boolean) m.invoke(itemStack, componentType);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return get(itemStack, componentType) != null;
    }

    /**
     * Intercepts: ItemStack.remove(DataComponentType)
     */
    public static Object remove(Object itemStack, Object componentType) {
        if (itemStack == null || componentType == null) return null;

        // 1. Try modern ItemStack.remove(DataComponentType)
        try {
            for (Method m : itemStack.getClass().getMethods()) {
                if ("remove".equals(m.getName()) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType.isInstance(componentType)) {
                        return m.invoke(itemStack, componentType);
                    }
                }
            }
        } catch (Throwable ignored) {}

        Object previous = get(itemStack, componentType);
        Object tag = getTag(itemStack);
        if (tag != null) {
            String key = getComponentKey(componentType);
            try {
                Method getCompound = tag.getClass().getMethod("getCompound", String.class);
                Object compTag = getCompound.invoke(tag, CONTINUUM_COMPONENTS_KEY);
                if (compTag != null) {
                    Method removeMethod = compTag.getClass().getMethod("remove", String.class);
                    removeMethod.invoke(compTag, key);
                }
            } catch (Throwable ignored) {}
        }
        return previous;
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static String getComponentKey(Object componentType) {
        if (componentType == null) return "";
        try {
            Method getKey = componentType.getClass().getMethod("getKey");
            Object keyObj = getKey.invoke(componentType);
            if (keyObj != null) return keyObj.toString();
        } catch (Throwable ignored) {}
        return componentType.toString().toLowerCase();
    }

    private static boolean isCustomDataComponent(String key) {
        return key.contains("custom_data");
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

    private static Object extractTagFromCustomData(Object customData) {
        try {
            Method copyTag = customData.getClass().getMethod("copyTag");
            return copyTag.invoke(customData);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object wrapCustomData(Object compoundTag) {
        try {
            Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
            Method ofMethod = customDataClass.getMethod("of", Class.forName("net.minecraft.nbt.CompoundTag"));
            return ofMethod.invoke(null, compoundTag);
        } catch (Throwable ignored) {
            return compoundTag;
        }
    }

    private static Object createEmptyCompoundTag() {
        try {
            return Class.forName("net.minecraft.nbt.CompoundTag").getConstructor().newInstance();
        } catch (Throwable t) {
            return null;
        }
    }
}
