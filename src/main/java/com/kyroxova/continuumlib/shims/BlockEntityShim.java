package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for BlockEntity save/load methods across Minecraft versions.
 * In <= 1.20.4: saveAdditional(CompoundTag) and load(CompoundTag).
 * In >= 1.20.5: saveAdditional(CompoundTag, HolderLookup.Provider) and loadAdditional(CompoundTag, HolderLookup.Provider).
 */
public final class BlockEntityShim {

    private static final Logger LOGGER = Logger.getLogger(BlockEntityShim.class.getName());

    private static final ThreadLocal<Set<Object>> SAVING = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Set<Object>> LOADING = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new WeakHashMap<>()));

    private BlockEntityShim() {}

    public static void pushSave(Object blockEntity) {
        if (blockEntity != null) {
            SAVING.get().add(blockEntity);
        }
    }

    public static void popSave(Object blockEntity) {
        if (blockEntity != null) {
            SAVING.get().remove(blockEntity);
        }
    }

    public static boolean isSaving(Object blockEntity) {
        return blockEntity != null && SAVING.get().contains(blockEntity);
    }

    public static void pushLoad(Object blockEntity) {
        if (blockEntity != null) {
            LOADING.get().add(blockEntity);
        }
    }

    public static void popLoad(Object blockEntity) {
        if (blockEntity != null) {
            LOADING.get().remove(blockEntity);
        }
    }

    public static boolean isLoading(Object blockEntity) {
        return blockEntity != null && LOADING.get().contains(blockEntity);
    }

    /**
     * Intercepts and bridges BlockEntity.saveAdditional(CompoundTag) on modern runtimes.
     */
    public static void save(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;
        if (isSaving(blockEntity)) {
            return;
        }

        pushSave(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();
            // Attempt 1: Modern 1.20.5+ saveAdditional(CompoundTag, HolderLookup.Provider)
            try {
                Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                Class<?> providerClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
                Method modernSave = beClass.getMethod("saveAdditional", tagClass, providerClass);
                modernSave.setAccessible(true);
                Object provider = resolveLookupProvider(blockEntity);
                modernSave.invoke(blockEntity, compoundTag, provider);
                return;
            } catch (Throwable ignored) {}

            // Attempt 2: Legacy <= 1.20.4 saveAdditional(CompoundTag)
            try {
                Method legacySave = null;
                try {
                    Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                    legacySave = beClass.getMethod("saveAdditional", tagClass);
                } catch (Throwable t) {
                    for (Method m : beClass.getMethods()) {
                        if ("saveAdditional".equals(m.getName()) && m.getParameterCount() == 1) {
                            legacySave = m;
                            break;
                        }
                    }
                }
                if (legacySave != null) {
                    legacySave.setAccessible(true);
                    legacySave.invoke(blockEntity, compoundTag);
                    return;
                }
            } catch (Throwable ignored) {}

            // Attempt 3: 1.7.10 / 1.12.2 writeToNBT(NBTTagCompound)
            try {
                Method oldSave = null;
                try {
                    Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
                    oldSave = beClass.getMethod("writeToNBT", nbtClass);
                } catch (Throwable t) {
                    for (Method m : beClass.getMethods()) {
                        if ("writeToNBT".equals(m.getName()) && m.getParameterCount() == 1) {
                            oldSave = m;
                            break;
                        }
                    }
                }
                if (oldSave != null) {
                    oldSave.setAccessible(true);
                    oldSave.invoke(blockEntity, compoundTag);
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in saveAdditional polyfill: " + t.getMessage());
        } finally {
            popSave(blockEntity);
        }
    }

    /**
     * Intercepts and bridges BlockEntity.load(CompoundTag) on modern runtimes.
     */
    public static void load(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;
        if (isLoading(blockEntity)) {
            return;
        }

        pushLoad(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();
            // Attempt 1: Modern 1.20.5+ loadAdditional(CompoundTag, HolderLookup.Provider)
            try {
                Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                Class<?> providerClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
                Method modernLoad = beClass.getMethod("loadAdditional", tagClass, providerClass);
                modernLoad.setAccessible(true);
                Object provider = resolveLookupProvider(blockEntity);
                modernLoad.invoke(blockEntity, compoundTag, provider);
                return;
            } catch (Throwable ignored) {}

            // Attempt 2: Legacy <= 1.20.4 load(CompoundTag)
            try {
                Method legacyLoad = null;
                try {
                    Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                    legacyLoad = beClass.getMethod("load", tagClass);
                } catch (Throwable t) {
                    for (Method m : beClass.getMethods()) {
                        if ("load".equals(m.getName()) && m.getParameterCount() == 1) {
                            legacyLoad = m;
                            break;
                        }
                    }
                }
                if (legacyLoad != null) {
                    legacyLoad.setAccessible(true);
                    legacyLoad.invoke(blockEntity, compoundTag);
                    return;
                }
            } catch (Throwable ignored) {}

            // Attempt 3: 1.7.10 / 1.12.2 readFromNBT(NBTTagCompound)
            try {
                Method oldLoad = null;
                try {
                    Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
                    oldLoad = beClass.getMethod("readFromNBT", nbtClass);
                } catch (Throwable t) {
                    for (Method m : beClass.getMethods()) {
                        if ("readFromNBT".equals(m.getName()) && m.getParameterCount() == 1) {
                            oldLoad = m;
                            break;
                        }
                    }
                }
                if (oldLoad != null) {
                    oldLoad.setAccessible(true);
                    oldLoad.invoke(blockEntity, compoundTag);
                }
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in load polyfill: " + t.getMessage());
        } finally {
            popLoad(blockEntity);
        }
    }

    /**
     * Resolves HolderLookup.Provider from the BlockEntity's Level or RegistryAccess.
     */
    private static Object resolveLookupProvider(Object blockEntity) {
        try {
            Method getLevel = blockEntity.getClass().getMethod("getLevel");
            Object level = getLevel.invoke(blockEntity);
            if (level != null) {
                Method regAccess = level.getClass().getMethod("registryAccess");
                return regAccess.invoke(level);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
