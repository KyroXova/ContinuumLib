package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for BlockEntity save/load methods across Minecraft versions.
 * In <= 1.20.4: saveAdditional(CompoundTag) and load(CompoundTag).
 * In >= 1.20.5: saveAdditional(CompoundTag, HolderLookup.Provider) and loadAdditional(CompoundTag, HolderLookup.Provider).
 */
public final class BlockEntityShim {

    private static final Logger LOGGER = Logger.getLogger(BlockEntityShim.class.getName());

    private BlockEntityShim() {}

    /**
     * Intercepts and bridges BlockEntity.saveAdditional(CompoundTag) on modern runtimes.
     */
    public static void save(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;

        try {
            Class<?> beClass = blockEntity.getClass();
            // Attempt 1: Modern 1.20.5+ saveAdditional(CompoundTag, HolderLookup.Provider)
            try {
                Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                Class<?> providerClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
                Method modernSave = beClass.getMethod("saveAdditional", tagClass, providerClass);
                Object provider = resolveLookupProvider(blockEntity);
                modernSave.invoke(blockEntity, compoundTag, provider);
                return;
            } catch (NoSuchMethodException ignored) {}

            // Attempt 2: Legacy <= 1.20.4 saveAdditional(CompoundTag)
            try {
                Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                Method legacySave = beClass.getMethod("saveAdditional", tagClass);
                legacySave.invoke(blockEntity, compoundTag);
                return;
            } catch (NoSuchMethodException ignored) {}

            // Attempt 3: 1.7.10 / 1.12.2 writeToNBT(NBTTagCompound)
            try {
                Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
                Method oldSave = beClass.getMethod("writeToNBT", nbtClass);
                oldSave.invoke(blockEntity, compoundTag);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in saveAdditional polyfill: " + t.getMessage());
        }
    }

    /**
     * Intercepts and bridges BlockEntity.load(CompoundTag) on modern runtimes.
     */
    public static void load(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;

        try {
            Class<?> beClass = blockEntity.getClass();
            // Attempt 1: Modern 1.20.5+ loadAdditional(CompoundTag, HolderLookup.Provider)
            try {
                Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                Class<?> providerClass = Class.forName("net.minecraft.core.HolderLookup$Provider");
                Method modernLoad = beClass.getMethod("loadAdditional", tagClass, providerClass);
                Object provider = resolveLookupProvider(blockEntity);
                modernLoad.invoke(blockEntity, compoundTag, provider);
                return;
            } catch (NoSuchMethodException ignored) {}

            // Attempt 2: Legacy <= 1.20.4 load(CompoundTag)
            try {
                Class<?> tagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                Method legacyLoad = beClass.getMethod("load", tagClass);
                legacyLoad.invoke(blockEntity, compoundTag);
                return;
            } catch (NoSuchMethodException ignored) {}

            // Attempt 3: 1.7.10 / 1.12.2 readFromNBT(NBTTagCompound)
            try {
                Class<?> nbtClass = Class.forName("net.minecraft.nbt.NBTTagCompound");
                Method oldLoad = beClass.getMethod("readFromNBT", nbtClass);
                oldLoad.invoke(blockEntity, compoundTag);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in load polyfill: " + t.getMessage());
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
