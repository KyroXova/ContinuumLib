package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Universal Polyfill for Button widget instantiation.
 * In 1.19.3+, the legacy new Button(x, y, width, height, title, onPress) constructor
 * was replaced with Button.builder(title, onPress).bounds(x, y, width, height).build().
 */
public final class ButtonShim {

    private static final Logger LOGGER = Logger.getLogger(ButtonShim.class.getName());

    private ButtonShim() {}

    public static Object create(int x, int y, int width, int height, Object title, Object onPress) {
        try {
            Class<?> buttonClass = Class.forName("net.minecraft.client.gui.components.Button");

            // Attempt 1: Modern 1.19.3+ Button.builder(...)
            try {
                Method builderMethod = buttonClass.getMethod("builder",
                        Class.forName("net.minecraft.network.chat.Component"),
                        Class.forName("net.minecraft.client.gui.components.Button$OnPress"));
                Object builder = builderMethod.invoke(null, title, onPress);

                Method boundsMethod = builder.getClass().getMethod("bounds", int.class, int.class, int.class, int.class);
                boundsMethod.invoke(builder, x, y, width, height);

                Method buildMethod = builder.getClass().getMethod("build");
                return buildMethod.invoke(builder);
            } catch (NoSuchMethodException ignored) {}

            // Attempt 2: Legacy Constructor (<= 1.19.2)
            try {
                var ctor = buttonClass.getConstructor(int.class, int.class, int.class, int.class,
                        Class.forName("net.minecraft.network.chat.Component"),
                        Class.forName("net.minecraft.client.gui.components.Button$OnPress"));
                return ctor.newInstance(x, y, width, height, title, onPress);
            } catch (NoSuchMethodException ignored) {}

        } catch (Throwable t) {
            LOGGER.fine("[ButtonShim] Error instantiating Button: " + t.getMessage());
        }
        return null;
    }
}
