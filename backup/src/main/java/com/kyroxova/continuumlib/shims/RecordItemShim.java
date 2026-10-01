package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Polyfill for RecordItem (Music Discs).
 * Bridges legacy 1.18.2 new RecordItem(int, Supplier<SoundEvent>, Properties)
 * across 1.19.4+ (int, SoundEvent, Properties, int length) and 1.21+ (int, ResourceKey<JukeboxSong>, Properties).
 */
public final class RecordItemShim {

    private static final Logger LOGGER = Logger.getLogger(RecordItemShim.class.getName());

    private RecordItemShim() {}

    public static Object create(int analogOutput, Object soundSupplierOrEvent, Object properties) {
        try {
            Class<?> recordClass = Class.forName("net.minecraft.world.item.RecordItem");
            Object sound = (soundSupplierOrEvent instanceof Supplier<?> s) ? s.get() : soundSupplierOrEvent;

            // Attempt 1: 1.18.2 Constructor (int, Supplier, Properties)
            for (Constructor<?> ctor : recordClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 3 && params[0] == int.class && params[1] == Supplier.class) {
                    return ctor.newInstance(analogOutput, (Supplier<?>) () -> sound, properties);
                }
            }

            // Attempt 2: 1.19.4 / 1.20 Constructor (int, SoundEvent, Properties, int lengthInSeconds)
            for (Constructor<?> ctor : recordClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 4 && params[0] == int.class) {
                    return ctor.newInstance(analogOutput, sound, properties, 120);
                }
            }

            // Attempt 3: Modern 1.21+ Constructor (int, ResourceKey<JukeboxSong>, Properties)
            for (Constructor<?> ctor : recordClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 3 && params[0] == int.class) {
                    return ctor.newInstance(analogOutput, null, properties);
                }
            }

        } catch (Throwable t) {
            LOGGER.fine("[RecordItemShim] Error creating RecordItem: " + t.getMessage());
        }
        return null;
    }
}
