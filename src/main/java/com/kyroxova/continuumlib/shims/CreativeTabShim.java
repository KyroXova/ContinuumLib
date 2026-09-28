package com.kyroxova.continuumlib.shims;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bridges Creative Mode Tab registrations between legacy (.tab() on Item.Properties)
 * and modern 1.20+ (BuildCreativeModeTabContentsEvent / CreativeModeTab.builder()).
 */
public final class CreativeTabShim {

    private static final Logger LOGGER = Logger.getLogger(CreativeTabShim.class.getName());

    // Holds tab item associations for modern tab content events
    private static final Map<Object, Set<Object>> TAB_ITEMS = new ConcurrentHashMap<>();

    private CreativeTabShim() {}

    /**
     * Intercepts: Item$Properties.tab(CreativeModeTab tab)
     * In 1.20+, .tab() does not exist on Item.Properties. This shim receives the properties object,
     * notes the tab association, and returns the properties object seamlessly.
     */
    public static Object tab(Object properties, Object creativeTab) {
        if (properties == null) return null;
        if (creativeTab != null) {
            TAB_ITEMS.computeIfAbsent(creativeTab, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(properties);
        }
        return properties;
    }

    public static Set<Object> getItemsForTab(Object tab) {
        return TAB_ITEMS.getOrDefault(tab, Collections.emptySet());
    }
}
