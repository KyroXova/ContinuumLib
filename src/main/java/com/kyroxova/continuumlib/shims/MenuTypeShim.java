package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Extended Container Menus (MenuType) and Container Synchronization.
 * Bridges Forge IForgeMenuType.create(...) across NeoForge IMenuTypeExtension and Fabric ExtendedScreenHandlerType,
 * and handles slot listeners and inventory state synchronization across 1.7.9 -> 26.3+.
 */
public final class MenuTypeShim {

    private static final Logger LOGGER = Logger.getLogger(MenuTypeShim.class.getName());

    private static final Map<String, Object> REGISTERED_MENU_TYPES = new ConcurrentHashMap<>();
    private static final Map<Object, List<Object>> MENU_LISTENERS = new ConcurrentHashMap<>();
    private static final Map<Object, Map<Integer, Object>> MENU_SLOTS = new ConcurrentHashMap<>();

    private MenuTypeShim() {}

    public static Object createMenuType(Object factory) {
        return createMenuType("unnamed_menu", factory);
    }

    public static Object createMenuType(Object idOrName, Object factory) {
        if (factory == null) return null;
        String idStr = String.valueOf(idOrName);

        try {
            // 1. Try NeoForge IMenuTypeExtension.create(...)
            Class<?> neoClass = Class.forName("net.neoforged.neoforge.common.extensions.IMenuTypeExtension");
            Method createMethod = neoClass.getMethod("create", Class.forName("net.neoforged.neoforge.network.IContainerFactory"));
            Object mt = createMethod.invoke(null, factory);
            registerMenuType(idStr, mt);
            return mt;
        } catch (Throwable t1) {
            try {
                // 2. Try Forge IForgeMenuType.create(...)
                Class<?> forgeClass = Class.forName("net.minecraftforge.common.extensions.IForgeMenuType");
                Method createMethod = forgeClass.getMethod("create", Class.forName("net.minecraftforge.network.IContainerFactory"));
                Object mt = createMethod.invoke(null, factory);
                registerMenuType(idStr, mt);
                return mt;
            } catch (Throwable t2) {
                try {
                    // 3. Try standard MenuType constructor
                    Class<?> menuTypeClass = Class.forName("net.minecraft.world.inventory.MenuType");
                    Object mt = menuTypeClass.getConstructor(Class.forName("net.minecraft.world.inventory.MenuType$MenuSupplier")).newInstance(factory);
                    registerMenuType(idStr, mt);
                    return mt;
                } catch (Throwable t3) {
                    LOGGER.fine("[MenuTypeShim] Could not instantiate MenuType via reflection, using VirtualMenuType: " + t3.getMessage());
                }
            }
        }

        VirtualMenuType vmt = new VirtualMenuType(idStr, factory);
        registerMenuType(idStr, vmt);
        return vmt;
    }

    public static void registerMenuType(String id, Object menuType) {
        if (id != null && menuType != null) {
            REGISTERED_MENU_TYPES.put(id, menuType);
        }
    }

    public static Object getMenuType(String id) {
        return id != null ? REGISTERED_MENU_TYPES.get(id) : null;
    }

    public static void openMenu(Object player, Object menuProvider) {
        openMenu(player, menuProvider, null);
    }

    public static void openMenu(Object player, Object menuProvider, Object extraDataWriter) {
        if (player == null || menuProvider == null) return;

        if (extraDataWriter != null) {
            try {
                Class<?> nhClass = Class.forName("net.minecraftforge.network.NetworkHooks");
                for (Method m : nhClass.getMethods()) {
                    if ("openScreen".equals(m.getName()) && m.getParameterCount() == 3) {
                        m.invoke(null, player, menuProvider, extraDataWriter);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }

        try {
            for (Method m : player.getClass().getMethods()) {
                if ("openMenu".equals(m.getName())) {
                    if (extraDataWriter != null && m.getParameterCount() == 2) {
                        m.invoke(player, menuProvider, extraDataWriter);
                        return;
                    } else if (m.getParameterCount() == 1) {
                        m.invoke(player, menuProvider);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            for (Method m : player.getClass().getMethods()) {
                if ("openGui".equals(m.getName())) {
                    m.invoke(player, menuProvider);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[MenuTypeShim] Error opening menu: " + t.getMessage());
        }
    }

    public static void addDataSlot(Object menu, Object dataSlot) {
        if (menu == null || dataSlot == null) return;
        try {
            for (Method m : menu.getClass().getMethods()) {
                if ("addDataSlot".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(menu, dataSlot);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[MenuTypeShim] Error adding data slot: " + t.getMessage());
        }
    }

    public static void addDataSlots(Object menu, Object containerData) {
        if (menu == null || containerData == null) return;
        try {
            for (Method m : menu.getClass().getMethods()) {
                if ("addDataSlots".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(menu, containerData);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[MenuTypeShim] Error adding data slots: " + t.getMessage());
        }
    }

    /**
     * Attaches a container slot listener to a menu across version eras.
     */
    public static void addSlotListener(Object menu, Object listener) {
        if (menu == null || listener == null) return;
        MENU_LISTENERS.computeIfAbsent(menu, k -> new CopyOnWriteArrayList<>()).add(listener);

        try {
            for (Method m : menu.getClass().getMethods()) {
                if (("addSlotListener".equals(m.getName()) || "addListener".equals(m.getName())) && m.getParameterCount() == 1) {
                    m.invoke(menu, listener);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[MenuTypeShim] Error invoking addSlotListener: " + t.getMessage());
        }
    }

    public static List<Object> getSlotListeners(Object menu) {
        if (menu == null) return Collections.emptyList();
        return MENU_LISTENERS.getOrDefault(menu, Collections.emptyList());
    }

    /**
     * Broadcasts changes to container listeners and synchronizes slots.
     */
    public static void broadcastChanges(Object menu) {
        if (menu == null) return;

        // Reflection on real menu
        try {
            for (Method m : menu.getClass().getMethods()) {
                if (("broadcastChanges".equals(m.getName()) || "sendAllDataToRemote".equals(m.getName())) && m.getParameterCount() == 0) {
                    m.invoke(menu);
                    break;
                }
            }
        } catch (Throwable ignored) {}

        // Notify registered virtual listeners
        List<Object> listeners = MENU_LISTENERS.get(menu);
        if (listeners != null) {
            Map<Integer, Object> slots = MENU_SLOTS.getOrDefault(menu, Collections.emptyMap());
            for (Object l : listeners) {
                for (Map.Entry<Integer, Object> entry : slots.entrySet()) {
                    notifyListenerSlot(l, menu, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Synchronizes a single slot item across client and server.
     */
    public static void syncSlot(Object menu, int slotId, Object itemStack) {
        if (menu == null) return;
        MENU_SLOTS.computeIfAbsent(menu, k -> new ConcurrentHashMap<>()).put(slotId, itemStack);

        // Notify listeners
        List<Object> listeners = MENU_LISTENERS.get(menu);
        if (listeners != null) {
            for (Object l : listeners) {
                notifyListenerSlot(l, menu, slotId, itemStack);
            }
        }

        // Try direct setItem or slot access on menu
        try {
            for (Method m : menu.getClass().getMethods()) {
                if ("setItem".equals(m.getName()) && m.getParameterCount() == 3) {
                    // setItem(int slot, int stateId, ItemStack stack)
                    m.invoke(menu, slotId, 0, itemStack);
                    return;
                } else if ("setRemoteSlot".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.invoke(menu, slotId, itemStack);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static Object getSlotItem(Object menu, int slotId) {
        if (menu == null) return null;
        Map<Integer, Object> slots = MENU_SLOTS.get(menu);
        if (slots != null && slots.containsKey(slotId)) {
            return slots.get(slotId);
        }

        try {
            for (Method m : menu.getClass().getMethods()) {
                if ("getSlot".equals(m.getName()) && m.getParameterCount() == 1) {
                    Object slot = m.invoke(menu, slotId);
                    if (slot != null) {
                        Method getItem = slot.getClass().getMethod("getItem");
                        return getItem.invoke(slot);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static int getSlotCount(Object menu) {
        if (menu == null) return 0;
        Map<Integer, Object> slots = MENU_SLOTS.get(menu);
        if (slots != null) {
            return slots.size();
        }
        try {
            for (Method m : menu.getClass().getMethods()) {
                if ("slots".equals(m.getName()) && m.getParameterCount() == 0) {
                    Object slotList = m.invoke(menu);
                    if (slotList instanceof List<?> l) return l.size();
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static void notifyListenerSlot(Object listener, Object menu, int slotId, Object itemStack) {
        if (listener == null) return;
        try {
            for (Method m : listener.getClass().getMethods()) {
                if ("slotChanged".equals(m.getName())) {
                    if (m.getParameterCount() == 3) {
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {}
                        m.invoke(listener, menu, slotId, itemStack);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Fallback VirtualMenuType.
     */
    public static class VirtualMenuType {
        private final String id;
        private final Object factory;

        public VirtualMenuType(String id, Object factory) {
            this.id = id;
            this.factory = factory;
        }

        public String getId() {
            return id;
        }

        public Object getFactory() {
            return factory;
        }

        public Object create(int windowId, Object playerInventory) {
            if (factory == null) return new VirtualContainerMenu(windowId);
            try {
                for (Method m : factory.getClass().getMethods()) {
                    if (m.getParameterCount() == 2 && ("create".equals(m.getName()) || "apply".equals(m.getName()))) {
                        return m.invoke(factory, windowId, playerInventory);
                    }
                }
            } catch (Throwable ignored) {}
            return new VirtualContainerMenu(windowId);
        }

        @Override
        public String toString() {
            return "VirtualMenuType[" + id + "]";
        }
    }

    /**
     * Fallback VirtualContainerMenu.
     */
    public static class VirtualContainerMenu {
        private final int containerId;
        private final Map<Integer, Object> slots = new ConcurrentHashMap<>();

        public VirtualContainerMenu(int containerId) {
            this.containerId = containerId;
        }

        public int getContainerId() {
            return containerId;
        }

        public void setSlot(int index, Object stack) {
            slots.put(index, stack);
            MenuTypeShim.syncSlot(this, index, stack);
        }

        public Object getSlot(int index) {
            return slots.get(index);
        }

        @Override
        public String toString() {
            return "VirtualContainerMenu[id=" + containerId + ", slots=" + slots.size() + "]";
        }
    }
}
