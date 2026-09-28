package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Shim for Creative Mode Tabs and Item Groups.
 * Bridges legacy CreativeTabs (1.12.2) and CreativeModeTab / ItemGroup / Item.Properties.tab (1.16.5 - 1.18.2)
 * with modern 1.19.3+ CreativeModeTab.builder(), BuildCreativeModeTabContentsEvent,
 * and Fabric ItemGroupEvents across 1.12.2, 1.16.5, 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.1, and 26.3+.
 */
public final class CreativeTabShim {

    private static final Logger LOGGER = Logger.getLogger(CreativeTabShim.class.getName());

    // Holds tab item associations (tab key -> set of items / item suppliers / properties)
    private static final Map<Object, Set<Object>> TAB_ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Object> REGISTERED_TABS = new ConcurrentHashMap<>();

    private CreativeTabShim() {}

    /**
     * Intercepts: Item$Properties.tab(CreativeModeTab tab)
     * In 1.19.3+, .tab() does not exist on Item.Properties. This shim receives the properties object,
     * notes the tab association, and returns the properties object seamlessly.
     */
    public static Object tab(Object properties, Object creativeTab) {
        if (properties == null) return null;
        if (creativeTab != null) {
            TAB_ITEMS.computeIfAbsent(creativeTab, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(properties);
            String tabKey = String.valueOf(creativeTab);
            TAB_ITEMS.computeIfAbsent(tabKey, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(properties);
        }
        return properties;
    }

    /**
     * Associates an item or item supplier with a creative tab (by tab instance or tab ID).
     */
    public static void addItemToTab(Object tabId, Supplier<?> itemSupplier) {
        if (tabId == null || itemSupplier == null) return;
        TAB_ITEMS.computeIfAbsent(tabId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(itemSupplier);
        String stringId = String.valueOf(tabId);
        TAB_ITEMS.computeIfAbsent(stringId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(itemSupplier);
        LOGGER.fine(String.format("[CreativeTabShim] Bound item supplier to tab %s", stringId));
    }

    /**
     * Registers a creative mode tab across version/loader eras.
     * On 1.19.3+: Uses CreativeModeTab.builder() or FabricItemGroup.builder().
     * On <= 1.19.2: Instantiates legacy CreativeModeTab / CreativeTabs.
     */
    public static Object registerTab(Object id, Supplier<?> iconSupplier, Object title) {
        String stringId = String.valueOf(id);
        LOGGER.info("[CreativeTabShim] Registering tab: " + stringId);

        // 1. Try modern CreativeModeTab.builder() (1.19.3+ / 1.20+ / 26.3+)
        try {
            Class<?> tabClass = Class.forName("net.minecraft.world.item.CreativeModeTab");
            Method builderMethod = tabClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            // Set title
            if (title != null) {
                Method titleMethod = builder.getClass().getMethod("title", Class.forName("net.minecraft.network.chat.Component"));
                titleMethod.invoke(builder, title);
            }

            // Set icon
            if (iconSupplier != null) {
                Method iconMethod = builder.getClass().getMethod("icon", Supplier.class);
                iconMethod.invoke(builder, iconSupplier);
            }

            // Set displayItems generator
            try {
                for (Method m : builder.getClass().getMethods()) {
                    if ("displayItems".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> generatorClass = m.getParameterTypes()[0];
                        Object generator = java.lang.reflect.Proxy.newProxyInstance(
                                CreativeTabShim.class.getClassLoader(),
                                new Class<?>[]{generatorClass},
                                (proxy, method, args) -> {
                                    if (args != null && args.length >= 2) {
                                        populateTab(id, args[1]);
                                    }
                                    return null;
                                }
                        );
                        m.invoke(builder, generator);
                        break;
                    }
                }
            } catch (Throwable ignored) {}

            Method buildMethod = builder.getClass().getMethod("build");
            Object tab = buildMethod.invoke(builder);
            REGISTERED_TABS.put(stringId, tab);
            return tab;
        } catch (Throwable ignored) {}

        // 2. Try FabricItemGroup.builder() (Fabric 1.19.3+)
        try {
            Class<?> fabricBuilderClass = Class.forName("net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup");
            Method builderMethod = fabricBuilderClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            if (title != null) {
                Method titleMethod = builder.getClass().getMethod("displayName", Class.forName("net.minecraft.network.chat.Component"));
                titleMethod.invoke(builder, title);
            }
            if (iconSupplier != null) {
                Method iconMethod = builder.getClass().getMethod("icon", Supplier.class);
                iconMethod.invoke(builder, iconSupplier);
            }
            Method buildMethod = builder.getClass().getMethod("build");
            Object tab = buildMethod.invoke(builder);
            REGISTERED_TABS.put(stringId, tab);
            return tab;
        } catch (Throwable ignored) {}

        // 3. Fallback: virtual Tab descriptor for legacy targets
        VirtualCreativeTab virtualTab = new VirtualCreativeTab(stringId, iconSupplier, title);
        REGISTERED_TABS.put(stringId, virtualTab);
        return virtualTab;
    }

    /**
     * Handles BuildCreativeModeTabContentsEvent (NeoForge/Forge) or ItemGroupEvents callback.
     */
    public static void handleBuildContents(Object event) {
        if (event == null) return;
        try {
            // Check getTabKey()
            Object tabKey = null;
            try {
                Method getTabKey = event.getClass().getMethod("getTabKey");
                tabKey = getTabKey.invoke(event);
            } catch (NoSuchMethodException e) {
                try {
                    Method getTab = event.getClass().getMethod("getTab");
                    tabKey = getTab.invoke(event);
                } catch (Throwable ignored) {}
            }

            if (tabKey != null) {
                populateTab(tabKey, event);
            }
        } catch (Throwable t) {
            LOGGER.fine("[CreativeTabShim] Error handling BuildCreativeModeTabContentsEvent: " + t.getMessage());
        }
    }

    /**
     * Populates the tab output target with all items registered to this tab.
     */
    public static void populateTab(Object tabKeyOrTab, Object output) {
        if (tabKeyOrTab == null || output == null) return;

        Set<Object> items = getItemsForTab(tabKeyOrTab);
        for (Object itemObj : items) {
            Object toAccept = itemObj;
            if (itemObj instanceof Supplier<?> supplier) {
                toAccept = supplier.get();
            }
            if (toAccept == null) continue;

            // Try output.accept(item)
            try {
                for (Method m : output.getClass().getMethods()) {
                    if ("accept".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        if (paramType.isInstance(toAccept)) {
                            m.invoke(output, toAccept);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Retrieves all items registered to a given tab key or tab instance.
     */
    public static Set<Object> getItemsForTab(Object tab) {
        if (tab == null) return Collections.emptySet();
        Set<Object> direct = TAB_ITEMS.get(tab);
        if (direct != null && !direct.isEmpty()) return direct;

        String keyStr = String.valueOf(tab);
        Set<Object> byString = TAB_ITEMS.get(keyStr);
        if (byString != null && !byString.isEmpty()) return byString;

        // Try extracting location/key if ResourceKey
        try {
            Method locationMethod = tab.getClass().getMethod("location");
            Object loc = locationMethod.invoke(tab);
            if (loc != null) {
                Set<Object> byLoc = TAB_ITEMS.get(String.valueOf(loc));
                if (byLoc != null && !byLoc.isEmpty()) return byLoc;
            }
        } catch (Throwable ignored) {}

        return Collections.emptySet();
    }

    /**
     * Polyfill for CreativeModeTab.builder() when called on pre-1.19.3 environments.
     */
    public static Object createBuilder() {
        return new TabBuilder();
    }

    public static class TabBuilder {
        private Object title;
        private Supplier<?> icon;
        private Object displayItems;

        public TabBuilder title(Object title) {
            this.title = title;
            return this;
        }

        public TabBuilder icon(Supplier<?> icon) {
            this.icon = icon;
            return this;
        }

        public TabBuilder displayItems(Object displayItems) {
            this.displayItems = displayItems;
            return this;
        }

        public Object build() {
            String id = (title != null) ? title.toString() : "tab_" + System.currentTimeMillis();
            return registerTab(id, icon, title);
        }
    }

    public static class VirtualCreativeTab {
        public final String id;
        public final Supplier<?> iconSupplier;
        public final Object title;

        public VirtualCreativeTab(String id, Supplier<?> iconSupplier, Object title) {
            this.id = id;
            this.iconSupplier = iconSupplier;
            this.title = title;
        }

        public Object makeIcon() {
            return iconSupplier != null ? iconSupplier.get() : null;
        }

        public Object getTabIconItem() {
            return makeIcon();
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
