package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Extended Container Menus (MenuType).
 * Bridges Forge IForgeMenuType.create(...) across NeoForge IMenuTypeExtension and Fabric ExtendedScreenHandlerType.
 */
public final class MenuTypeShim {

    private static final Logger LOGGER = Logger.getLogger(MenuTypeShim.class.getName());

    private MenuTypeShim() {}

    public static Object createMenuType(Object factory) {
        if (factory == null) return null;

        try {
            // 1. Try NeoForge IMenuTypeExtension.create(...)
            Class<?> neoClass = Class.forName("net.neoforged.neoforge.common.extensions.IMenuTypeExtension");
            Method createMethod = neoClass.getMethod("create", Class.forName("net.neoforged.neoforge.network.IContainerFactory"));
            return createMethod.invoke(null, factory);
        } catch (Throwable t1) {
            try {
                // 2. Try Forge IForgeMenuType.create(...)
                Class<?> forgeClass = Class.forName("net.minecraftforge.common.extensions.IForgeMenuType");
                Method createMethod = forgeClass.getMethod("create", Class.forName("net.minecraftforge.network.IContainerFactory"));
                return createMethod.invoke(null, factory);
            } catch (Throwable t2) {
                try {
                    // 3. Try standard MenuType constructor
                    Class<?> menuTypeClass = Class.forName("net.minecraft.world.inventory.MenuType");
                    return menuTypeClass.getConstructor(Class.forName("net.minecraft.world.inventory.MenuType$MenuSupplier")).newInstance(factory);
                } catch (Throwable t3) {
                    LOGGER.fine("[MenuTypeShim] Could not instantiate MenuType: " + t3.getMessage());
                    return null;
                }
            }
        }
    }
}
