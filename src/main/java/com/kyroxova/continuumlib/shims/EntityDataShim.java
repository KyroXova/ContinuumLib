package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Minecraft Entity Synced Data across 1.7.9 -> 26.3+.
 * Seamlessly bridges:
 * 1. DataParameter (<= 1.16.5) vs EntityDataAccessor (>= 1.17 / 26.3+)
 * 2. DataSerializers (<= 1.16.5) vs EntityDataSerializers (>= 1.17 / 26.3+)
 * 3. EntityDataManager.createKey vs SynchedEntityData.defineId
 * 4. Entity.defineSynchedData() (<= 1.20.4) vs Entity.defineSynchedData(SynchedEntityData.Builder) (>= 1.20.5 / 26.3+)
 *    via ThreadLocal Builder capturing and adaptive dispatch.
 */
public final class EntityDataShim {

    private static final Logger LOGGER = Logger.getLogger(EntityDataShim.class.getName());

    private static final ThreadLocal<java.util.Deque<Object>> BUILDER_STACK = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private EntityDataShim() {}

    /**
     * Pushes the active SynchedEntityData.Builder on the current thread's stack.
     * Invoked by bytecode transformer in injected defineSynchedData bridges.
     */
    public static void pushBuilder(Object builder) {
        if (builder != null) {
            BUILDER_STACK.get().push(builder);
        }
    }

    /**
     * Pops and returns the active SynchedEntityData.Builder from the current thread's stack.
     * Invoked by bytecode transformer in injected defineSynchedData bridges.
     */
    public static Object popBuilder() {
        java.util.Deque<Object> stack = BUILDER_STACK.get();
        if (stack.isEmpty()) return null;
        Object val = stack.pop();
        if (stack.isEmpty()) {
            BUILDER_STACK.remove();
        }
        return val;
    }

    /**
     * Sets the active SynchedEntityData.Builder on the current thread.
     */
    public static void setCurrentBuilder(Object builder) {
        if (builder != null) {
            pushBuilder(builder);
        } else {
            popBuilder();
        }
    }

    /**
     * Alias for setCurrentBuilder / pushBuilder.
     */
    public static void captureBuilder(Object builder) {
        pushBuilder(builder);
    }

    /**
     * Clears the active builder on the current thread.
     */
    public static void clearCurrentBuilder() {
        popBuilder();
    }

    /**
     * Alias for clearCurrentBuilder / popBuilder.
     */
    public static void releaseBuilder() {
        popBuilder();
    }

    /**
     * Returns the currently active SynchedEntityData.Builder, if any.
     */
    public static Object getCurrentBuilder() {
        java.util.Deque<Object> stack = BUILDER_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Defines a synced entity data accessor across all versions.
     * Maps to SynchedEntityData.defineId (1.17+) or EntityDataManager.createKey (<= 1.16.5).
     */
    public static Object defineId(Class<?> entityClass, Object serializer) {
        if (entityClass == null) return null;

        // 1. Try modern SynchedEntityData.defineId(Class, EntityDataSerializer) (1.17+ / 26.3+)
        try {
            Class<?> synchedClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            for (Method m : synchedClass.getMethods()) {
                if ("defineId".equals(m.getName()) && m.getParameterCount() == 2 && Modifier.isStatic(m.getModifiers())) {
                    return m.invoke(null, entityClass, serializer);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy EntityDataManager.createKey(Class, DataSerializer) (<= 1.16.5)
        try {
            Class<?> managerClass = Class.forName("net.minecraft.network.datasync.EntityDataManager");
            for (Method m : managerClass.getMethods()) {
                if ("createKey".equals(m.getName()) && m.getParameterCount() == 2 && Modifier.isStatic(m.getModifiers())) {
                    return m.invoke(null, entityClass, serializer);
                }
            }
        } catch (Throwable ignored) {}

        // 3. Fallback: Synthetic accessor for tests or environments without MC classes loaded
        return new SyntheticAccessor(entityClass, serializer);
    }

    /**
     * Alias for defineId (matches <= 1.16.5 naming).
     */
    public static Object createKey(Class<?> entityClass, Object serializer) {
        return defineId(entityClass, serializer);
    }

    /**
     * Defines / registers a synced data parameter.
     * Automatically routes to:
     * - The captured SynchedEntityData.Builder (if within 1.20.5+ / 26.3+ defineSynchedData)
     * - The SynchedEntityData / EntityDataManager instance directly (<= 1.20.4)
     * - Or resolves the entityData property from an Entity target.
     */
    public static void define(Object target, Object accessor, Object defaultValue) {
        // 1. Check if a builder was captured on the current thread (1.20.5+ / 26.3+)
        Object builder = getCurrentBuilder();
        if (builder != null) {
            if (invokeDefine(builder, accessor, defaultValue)) {
                return;
            }
        }

        if (target == null) return;

        // 2. Try directly on target (SynchedEntityData, EntityDataManager, or Builder)
        if (invokeDefine(target, accessor, defaultValue)) {
            return;
        }

        // 3. If target is an Entity, resolve its entityData / getEntityData()
        Object entityData = resolveEntityData(target);
        if (entityData != null) {
            invokeDefine(entityData, accessor, defaultValue);
        }
    }

    /**
     * Retrieves the synced value for the given accessor from the entity or data manager.
     */
    public static Object get(Object target, Object accessor) {
        if (target == null || accessor == null) return null;

        Object entityData = resolveEntityData(target);
        Object receiver = entityData != null ? entityData : target;

        for (Method m : getAllMethods(receiver.getClass())) {
            if ("get".equals(m.getName()) && m.getParameterCount() == 1) {
                try {
                    m.setAccessible(true);
                    return m.invoke(receiver, accessor);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /**
     * Sets the synced value for the given accessor on the entity or data manager.
     */
    public static void set(Object target, Object accessor, Object value) {
        if (target == null || accessor == null) return;

        Object entityData = resolveEntityData(target);
        Object receiver = entityData != null ? entityData : target;

        for (Method m : getAllMethods(receiver.getClass())) {
            if ("set".equals(m.getName()) && m.getParameterCount() == 2) {
                try {
                    m.setAccessible(true);
                    m.invoke(receiver, accessor, value);
                    return;
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Helper to resolve a standard serializer by name (e.g. "BYTE", "INT", "STRING", "BOOLEAN").
     */
    public static Object getSerializer(String name) {
        if (name == null) return null;

        // 1. Try modern EntityDataSerializers
        try {
            Class<?> clazz = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
            Field f = clazz.getField(name);
            return f.get(null);
        } catch (Throwable ignored) {}

        // 2. Try legacy DataSerializers
        try {
            Class<?> clazz = Class.forName("net.minecraft.network.datasync.DataSerializers");
            Field f = clazz.getField(name);
            return f.get(null);
        } catch (Throwable ignored) {}

        return "Serializer[" + name + "]";
    }

    private static boolean invokeDefine(Object receiver, Object accessor, Object defaultValue) {
        if (receiver == null) return false;
        Class<?> clazz = receiver.getClass();

        // Check define(accessor, value) (1.17+ / 26.3+)
        for (Method m : getAllMethods(clazz)) {
            if (("define".equals(m.getName()) || "register".equals(m.getName())) && m.getParameterCount() == 2) {
                try {
                    m.setAccessible(true);
                    m.invoke(receiver, accessor, defaultValue);
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static Object resolveEntityData(Object target) {
        if (target == null) return null;
        Class<?> clazz = target.getClass();

        // 1. Method: getEntityData()
        for (Method m : getAllMethods(clazz)) {
            if (("getEntityData".equals(m.getName()) || "getDataWatcher".equals(m.getName())) && m.getParameterCount() == 0) {
                try {
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (Throwable ignored) {}
            }
        }

        // 2. Field: entityData or dataWatcher
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if ("entityData".equals(f.getName()) || "dataWatcher".equals(f.getName())) {
                    try {
                        f.setAccessible(true);
                        return f.get(target);
                    } catch (Throwable ignored) {}
                }
            }
        }

        return null;
    }

    private static Method[] getAllMethods(Class<?> clazz) {
        Method[] publicMethods = clazz.getMethods();
        Method[] declaredMethods = clazz.getDeclaredMethods();
        Method[] all = new Method[publicMethods.length + declaredMethods.length];
        System.arraycopy(publicMethods, 0, all, 0, publicMethods.length);
        System.arraycopy(declaredMethods, 0, all, publicMethods.length, declaredMethods.length);
        return all;
    }

    /**
     * Synthetic accessor representing an EntityDataAccessor / DataParameter.
     */
    public static final class SyntheticAccessor {
        private final Class<?> entityClass;
        private final Object serializer;

        public SyntheticAccessor(Class<?> entityClass, Object serializer) {
            this.entityClass = entityClass;
            this.serializer = serializer;
        }

        public Class<?> getEntityClass() {
            return entityClass;
        }

        public Object getSerializer() {
            return serializer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SyntheticAccessor)) return false;
            SyntheticAccessor that = (SyntheticAccessor) o;
            return Objects.equals(entityClass, that.entityClass) && Objects.equals(serializer, that.serializer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityClass, serializer);
        }

        @Override
        public String toString() {
            return "SyntheticAccessor[" + (entityClass != null ? entityClass.getSimpleName() : "null") + ", " + serializer + "]";
        }
    }
}
