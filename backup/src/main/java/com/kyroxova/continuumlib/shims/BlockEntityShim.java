package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for BlockEntity save/load methods across Minecraft versions.
 * Supports bidirectional conversions between:
 * - 1.7.9 / 1.12.2: readFromNBT(NBTTagCompound) / writeToNBT(NBTTagCompound)
 * - 1.18.2: load(CompoundTag) / saveAdditional(CompoundTag)
 * - 1.20.5 / 26.3+: loadAdditional(CompoundTag, HolderLookup.Provider) / saveAdditional(CompoundTag, HolderLookup.Provider)
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
        bridgeSave(blockEntity, compoundTag);
    }

    /**
     * Intercepts and bridges BlockEntity.load(CompoundTag) on modern runtimes.
     */
    public static void load(Object blockEntity, Object compoundTag) {
        bridgeLoad(blockEntity, compoundTag);
    }

    /**
     * Bridge method for 1.7.9 readFromNBT(NBTTagCompound).
     * Dispatches to modern loadAdditional(tag, provider), load(tag), or readFromNBT.
     */
    public static void bridgeReadFromNBT(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;
        if (isLoading(blockEntity)) return;

        pushLoad(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();

            // 1. Attempt Modern 1.20.5+ loadAdditional(tag, provider)
            for (Method m : beClass.getMethods()) {
                if ("loadAdditional".equals(m.getName()) && m.getParameterCount() == 2 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object provider = resolveLookupProvider(blockEntity);
                    m.invoke(blockEntity, compoundTag, provider);
                    return;
                }
            }

            // 2. Attempt 1.18.2 load(tag)
            for (Method m : beClass.getMethods()) {
                if ("load".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 3. Attempt 1.18.2 loadAdditional(tag) (1 parameter)
            for (Method m : beClass.getMethods()) {
                if ("loadAdditional".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 4. Attempt 1.7.9 readFromNBT(tag)
            for (Method m : beClass.getMethods()) {
                if ("readFromNBT".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in bridgeReadFromNBT: " + t.getMessage());
        } finally {
            popLoad(blockEntity);
        }
    }

    /**
     * Bridge method for 1.7.9 writeToNBT(NBTTagCompound).
     * Dispatches to modern saveAdditional(tag, provider), saveAdditional(tag), or writeToNBT.
     */
    public static void bridgeWriteToNBT(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;
        if (isSaving(blockEntity)) return;

        pushSave(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();

            // 1. Attempt Modern 1.20.5+ saveAdditional(tag, provider)
            for (Method m : beClass.getMethods()) {
                if ("saveAdditional".equals(m.getName()) && m.getParameterCount() == 2 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object provider = resolveLookupProvider(blockEntity);
                    m.invoke(blockEntity, compoundTag, provider);
                    return;
                }
            }

            // 2. Attempt 1.18.2 saveAdditional(tag)
            for (Method m : beClass.getMethods()) {
                if ("saveAdditional".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 3. Attempt 1.7.9 writeToNBT(tag)
            for (Method m : beClass.getMethods()) {
                if ("writeToNBT".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in bridgeWriteToNBT: " + t.getMessage());
        } finally {
            popSave(blockEntity);
        }
    }

    /**
     * Bridge method for 1.18.2 load(CompoundTag).
     */
    public static void bridgeLoad(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;
        if (isLoading(blockEntity)) return;

        pushLoad(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();

            // 1. Attempt loadAdditional(tag, provider)
            for (Method m : beClass.getMethods()) {
                if ("loadAdditional".equals(m.getName()) && m.getParameterCount() == 2 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object provider = resolveLookupProvider(blockEntity);
                    m.invoke(blockEntity, compoundTag, provider);
                    return;
                }
            }

            // 2. Attempt readFromNBT(tag)
            for (Method m : beClass.getMethods()) {
                if ("readFromNBT".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 3. Attempt load(tag)
            for (Method m : beClass.getMethods()) {
                if ("load".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in bridgeLoad: " + t.getMessage());
        } finally {
            popLoad(blockEntity);
        }
    }

    /**
     * Bridge method for 1.18.2 saveAdditional(CompoundTag).
     */
    public static void bridgeSave(Object blockEntity, Object compoundTag) {
        if (blockEntity == null || compoundTag == null) return;
        if (isSaving(blockEntity)) return;

        pushSave(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();

            // 1. Attempt saveAdditional(tag, provider)
            for (Method m : beClass.getMethods()) {
                if ("saveAdditional".equals(m.getName()) && m.getParameterCount() == 2 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object provider = resolveLookupProvider(blockEntity);
                    m.invoke(blockEntity, compoundTag, provider);
                    return;
                }
            }

            // 2. Attempt writeToNBT(tag)
            for (Method m : beClass.getMethods()) {
                if ("writeToNBT".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 3. Attempt saveAdditional(tag)
            for (Method m : beClass.getMethods()) {
                if ("saveAdditional".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in bridgeSave: " + t.getMessage());
        } finally {
            popSave(blockEntity);
        }
    }

    /**
     * Bridge method for 1.20.5+ loadAdditional(CompoundTag, HolderLookup.Provider).
     */
    public static void bridgeLoadAdditional(Object blockEntity, Object compoundTag, Object provider) {
        if (blockEntity == null || compoundTag == null) return;
        if (isLoading(blockEntity)) return;

        pushLoad(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();

            // 1. Attempt load(tag)
            for (Method m : beClass.getMethods()) {
                if ("load".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 2. Attempt readFromNBT(tag)
            for (Method m : beClass.getMethods()) {
                if ("readFromNBT".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 3. Attempt loadAdditional(tag, provider) (if defined directly on super or mod class)
            for (Method m : beClass.getMethods()) {
                if ("loadAdditional".equals(m.getName()) && m.getParameterCount() == 2 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag, provider);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in bridgeLoadAdditional: " + t.getMessage());
        } finally {
            popLoad(blockEntity);
        }
    }

    /**
     * Bridge method for 1.20.5+ saveAdditional(CompoundTag, HolderLookup.Provider).
     */
    public static void bridgeSaveAdditional(Object blockEntity, Object compoundTag, Object provider) {
        if (blockEntity == null || compoundTag == null) return;
        if (isSaving(blockEntity)) return;

        pushSave(blockEntity);
        try {
            Class<?> beClass = blockEntity.getClass();

            // 1. Attempt saveAdditional(tag) (1 parameter)
            for (Method m : beClass.getMethods()) {
                if ("saveAdditional".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 2. Attempt writeToNBT(tag)
            for (Method m : beClass.getMethods()) {
                if ("writeToNBT".equals(m.getName()) && m.getParameterCount() == 1 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag);
                    return;
                }
            }

            // 3. Attempt saveAdditional(tag, provider) (if defined directly on super or mod class)
            for (Method m : beClass.getMethods()) {
                if ("saveAdditional".equals(m.getName()) && m.getParameterCount() == 2 && !m.isSynthetic()) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(blockEntity, compoundTag, provider);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[BlockEntityShim] Error in bridgeSaveAdditional: " + t.getMessage());
        } finally {
            popSave(blockEntity);
        }
    }

    /**
     * Resolves HolderLookup.Provider from the BlockEntity's Level or RegistryAccess.
     */
    public static Object resolveLookupProvider(Object blockEntity) {
        if (blockEntity == null) return null;
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
