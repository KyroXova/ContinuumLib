package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class WorldShim {

    private static final Logger LOGGER = Logger.getLogger(WorldShim.class.getName());

    private WorldShim() {}

    public static Object getBlockEntity(Object level, Object pos) {
        if (level == null || pos == null) return null;
        try {
            for (Method m : level.getClass().getMethods()) {
                if (("getBlockEntity".equals(m.getName()) || "getTileEntity".equals(m.getName())) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(level, pos);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[WorldShim] Error getting block entity: " + t.getMessage());
        }
        return null;
    }

    public static Object getTileEntity(Object world, Object pos) {
        return getBlockEntity(world, pos);
    }

    public static Object getBlockState(Object level, Object pos) {
        if (level == null || pos == null) return null;
        try {
            for (Method m : level.getClass().getMethods()) {
                if (("getBlockState".equals(m.getName()) || "getBlock".equals(m.getName())) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(level, pos);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[WorldShim] Error getting block state: " + t.getMessage());
        }
        return null;
    }

    public static boolean isClientSide(Object level) {
        if (level == null) return false;
        try {
            for (Method m : level.getClass().getMethods()) {
                if (("isClientSide".equals(m.getName()) || "isRemote".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(level);
                    if (res instanceof Boolean b) return b;
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Field f : level.getClass().getFields()) {
                if ("isClientSide".equals(f.getName()) || "isRemote".equals(f.getName())) {
                    try {
                        f.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = f.get(level);
                    if (res instanceof Boolean b) return b;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isRemote(Object world) {
        return isClientSide(world);
    }

    public static Object getDimensionKey(Object level) {
        if (level == null) return null;
        try {
            for (Method m : level.getClass().getMethods()) {
                if (("dimension".equals(m.getName()) || "getDimensionKey".equals(m.getName()) || "dimensionKey".equals(m.getName())) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(level);
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[WorldShim] Error getting dimension key: " + t.getMessage());
        }
        return null;
    }

    public static Object getOverworld(Object server) {
        if (server == null) return null;
        try {
            for (Method m : server.getClass().getMethods()) {
                if ("overworld".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(server);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> slClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Field overworldField = slClass.getField("OVERWORLD");
            Object overworldKey = overworldField.get(null);
            for (Method m : server.getClass().getMethods()) {
                if ("getLevel".equals(m.getName()) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(server, overworldKey);
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Method m : server.getClass().getMethods()) {
                if ("getWorld".equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    return m.invoke(server, 0);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object getDimension(Object server, Object keyOrLocation) {
        if (server == null || keyOrLocation == null) return null;
        try {
            for (Method m : server.getClass().getMethods()) {
                if (("getLevel".equals(m.getName()) || "getWorld".equals(m.getName())) && m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Object res = m.invoke(server, keyOrLocation);
                    if (res != null) return res;
                }
            }
        } catch (Throwable ignored) {}

        try {
            String targetStr = keyOrLocation.toString();
            for (Method m : server.getClass().getMethods()) {
                if ("getAllLevels".equals(m.getName()) && m.getParameterCount() == 0) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {}
                    Iterable<?> levels = (Iterable<?>) m.invoke(server);
                    for (Object lvl : levels) {
                        Object dim = getDimensionKey(lvl);
                        if (dim != null && dim.toString().contains(targetStr)) {
                            return lvl;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
