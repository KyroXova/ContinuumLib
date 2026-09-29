package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Persistent SavedData across 1.7.9 -> 26.3+.
 * Bridges legacy WorldSavedData (<= 1.12.2), intermediate SavedData(CompoundTag) (1.14 - 1.20.4),
 * and modern SavedData(HolderLookup.Provider) (1.20.5+ / 26.3+).
 */
public final class SavedDataShim {

    private static final Logger LOGGER = Logger.getLogger(SavedDataShim.class.getName());

    private static final Map<String, Object> LOCAL_SAVED_DATA = new ConcurrentHashMap<>();

    private SavedDataShim() {}

    /**
     * Resolves DimensionDataStorage from a ServerLevel, ServerWorld, or uses the dataStorage directly.
     */
    public static Object getDataStorage(Object levelOrStorage) {
        if (levelOrStorage == null) return null;
        try {
            // Check if levelOrStorage has getDataStorage()
            for (Method m : levelOrStorage.getClass().getMethods()) {
                if (("getDataStorage".equals(m.getName()) || "getMapStorage".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(levelOrStorage);
                }
            }
        } catch (Throwable ignored) {}
        return levelOrStorage;
    }

    public static Object getOrCreate(Object levelOrStorage, String id, Supplier<?> supplier) {
        return getOrCreate(levelOrStorage, id, (Object) supplier);
    }

    /**
     * Retrieves or creates a SavedData instance across all versions.
     */
    @SuppressWarnings("unchecked")
    public static Object getOrCreate(Object levelOrStorage, String id, Object factoryOrSupplier) {
        if (id == null) return null;
        Object storage = getDataStorage(levelOrStorage);

        if (storage != null) {
            // 1. Try modern computeIfAbsent(SavedData.Factory, String) (1.20.5+)
            try {
                for (Method m : storage.getClass().getMethods()) {
                    if ("computeIfAbsent".equals(m.getName()) && m.getParameterCount() == 2) {
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {}
                        Object factory = adaptToSavedDataFactory(factoryOrSupplier);
                        return m.invoke(storage, factory, id);
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Try intermediate computeIfAbsent(Function, Supplier, String) (1.14 - 1.20.4)
            try {
                for (Method m : storage.getClass().getMethods()) {
                    if ("computeIfAbsent".equals(m.getName()) && m.getParameterCount() == 3) {
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {}
                        Function<Object, Object> deserializer = (factoryOrSupplier instanceof Function<?, ?> fn)
                                ? (Function<Object, Object>) fn : (tag -> instantiate(factoryOrSupplier));
                        Supplier<Object> supplier = (factoryOrSupplier instanceof Supplier<?> sup)
                                ? (Supplier<Object>) sup : (() -> instantiate(factoryOrSupplier));
                        return m.invoke(storage, deserializer, supplier, id);
                    }
                }
            } catch (Throwable ignored) {}

            // 3. Try legacy getOrLoadData(Class, String) (<= 1.12.2)
            try {
                for (Method m : storage.getClass().getMethods()) {
                    if ("getOrLoadData".equals(m.getName()) && m.getParameterCount() == 2) {
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {}
                        Class<?> clazz = (factoryOrSupplier instanceof Class<?> c) ? c : factoryOrSupplier.getClass();
                        Object loaded = m.invoke(storage, clazz, id);
                        if (loaded != null) return loaded;

                        Object created = instantiate(factoryOrSupplier);
                        Method setData = storage.getClass().getMethod("setData", String.class, loaded != null ? loaded.getClass() : created.getClass());
                        setData.invoke(storage, id, created);
                        return created;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Fallback in-memory storage for non-MC environments and unit tests
        return LOCAL_SAVED_DATA.computeIfAbsent(id, k -> instantiate(factoryOrSupplier));
    }

    /**
     * Marks a SavedData instance as dirty across versions.
     */
    public static void setDirty(Object savedData) {
        if (savedData == null) return;
        try {
            for (Method m : savedData.getClass().getMethods()) {
                if (("setDirty".equals(m.getName()) || "markDirty".equals(m.getName()))) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    if (m.getParameterCount() == 0) {
                        m.invoke(savedData);
                        return;
                    } else if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
                        m.invoke(savedData, true);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[SavedDataShim] Error marking saved data dirty: " + t.getMessage());
        }

        if (savedData instanceof VirtualSavedData vsd) {
            vsd.setDirty(true);
        }
    }

    /**
     * Saves data to CompoundTag across versions (supports both 1.20.4- and 1.20.5+ signatures).
     */
    public static Object save(Object savedData, Object compoundTag, Object provider) {
        if (savedData == null || compoundTag == null) return compoundTag;
        try {
            // 1. Try modern save(CompoundTag, HolderLookup.Provider) (1.20.5+)
            for (Method m : savedData.getClass().getMethods()) {
                if ("save".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(savedData, compoundTag, provider);
                }
            }
        } catch (Throwable ignored) {}

        try {
            // 2. Try intermediate save(CompoundTag) (1.14 - 1.20.4)
            for (Method m : savedData.getClass().getMethods()) {
                if ("save".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(savedData, compoundTag);
                }
            }
        } catch (Throwable ignored) {}

        try {
            // 3. Try legacy writeToNBT(NBTTagCompound) (<= 1.12.2)
            for (Method m : savedData.getClass().getMethods()) {
                if ("writeToNBT".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(savedData, compoundTag);
                }
            }
        } catch (Throwable ignored) {}

        return compoundTag;
    }

    /**
     * Loads data from CompoundTag across versions.
     */
    public static Object load(Object savedData, Object compoundTag, Object provider) {
        if (savedData == null || compoundTag == null) return savedData;
        try {
            for (Method m : savedData.getClass().getMethods()) {
                if ("load".equals(m.getName()) && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(savedData, compoundTag, provider);
                    return savedData;
                } else if ("load".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(savedData, compoundTag);
                    return savedData;
                } else if ("readFromNBT".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    m.invoke(savedData, compoundTag);
                    return savedData;
                }
            }
        } catch (Throwable ignored) {}

        return savedData;
    }

    private static Object adaptToSavedDataFactory(Object factoryOrSupplier) {
        try {
            Class<?> factoryClass = Class.forName("net.minecraft.world.level.saveddata.SavedData$Factory");
            for (Constructor<?> c : factoryClass.getConstructors()) {
                if (c.getParameterCount() == 3) {
                    Supplier<Object> constructor = (factoryOrSupplier instanceof Supplier<?> sup)
                            ? (Supplier<Object>) sup : (() -> instantiate(factoryOrSupplier));
                    BiFunction<Object, Object, Object> deserializer = (factoryOrSupplier instanceof BiFunction<?, ?, ?> bf)
                            ? (BiFunction<Object, Object, Object>) bf : ((tag, prov) -> instantiate(factoryOrSupplier));
                    return c.newInstance(constructor, deserializer, null);
                }
            }
        } catch (Throwable ignored) {}
        return factoryOrSupplier;
    }

    private static Object instantiate(Object factoryOrSupplier) {
        if (factoryOrSupplier == null) return new VirtualSavedData();
        if (factoryOrSupplier instanceof Supplier<?> sup) {
            return sup.get();
        }
        if (factoryOrSupplier instanceof Class<?> clazz) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable ignored) {}
        }
        return factoryOrSupplier;
    }

    public static Object getLocalData(String id) {
        return id != null ? LOCAL_SAVED_DATA.get(id) : null;
    }

    public static void setLocalData(String id, Object data) {
        if (id != null && data != null) {
            LOCAL_SAVED_DATA.put(id, data);
        }
    }

    public static void clearLocalData() {
        LOCAL_SAVED_DATA.clear();
    }

    /**
     * Fallback standalone VirtualSavedData.
     */
    public static class VirtualSavedData {
        private String id;
        private boolean dirty;
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        public VirtualSavedData() {
            this.dirty = false;
        }

        public VirtualSavedData(String id) {
            this();
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public void setDirty() {
            this.dirty = true;
        }

        public void markDirty() {
            this.dirty = true;
        }

        public void put(String key, Object value) {
            data.put(key, value);
            this.dirty = true;
        }

        public Object get(String key) {
            return data.get(key);
        }

        public Map<String, Object> getData() {
            return data;
        }
    }
}
