package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synthetic Cross-Loader Event Dispatcher.
 * Allows mods written with Forge event mechanisms (@SubscribeEvent, FMLCommonSetupEvent, BuildCreativeModeTabContentsEvent, etc.)
 * to be dispatched from Fabric callbacks and across version epochs without modifying the consumer mod's source code.
 */
public final class SyntheticEventDispatcher {

    private static final Logger LOGGER = Logger.getLogger(SyntheticEventDispatcher.class.getName());

    private static final Map<Class<?>, List<EventListenerWrapper>> LISTENERS = new ConcurrentHashMap<>();

    private SyntheticEventDispatcher() {}

    /**
     * Registers an object containing @SubscribeEvent methods or consumer listeners.
     */
    public static void register(Object target) {
        if (target == null) return;
        Class<?> clazz = target.getClass();

        for (Method method : clazz.getMethods()) {
            if (isEventSubscriber(method) && method.getParameterCount() == 1) {
                Class<?> eventType = method.getParameterTypes()[0];
                LISTENERS.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                        .add(new EventListenerWrapper(target, method));
                LOGGER.fine(String.format("[SyntheticEventDispatcher] Registered listener: %s#%s for %s",
                        clazz.getSimpleName(), method.getName(), eventType.getSimpleName()));
            }
        }
    }

    private static boolean isEventSubscriber(Method method) {
        for (var annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if ("SubscribeEvent".equals(name) || "EventListener".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Posts an event to all registered listeners matching the event class or its superclasses.
     * Automatically coordinates tab populating if the event is a BuildCreativeModeTabContentsEvent.
     */
    public static void post(Object event) {
        if (event == null) return;
        Class<?> eventClass = event.getClass();

        // Check if event is a creative tab contents event
        String className = eventClass.getSimpleName();
        if (className.contains("BuildCreativeModeTabContents") || className.contains("BuildContents")) {
            CreativeTabShim.handleBuildContents(event);
        }

        for (Map.Entry<Class<?>, List<EventListenerWrapper>> entry : LISTENERS.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                for (EventListenerWrapper listener : entry.getValue()) {
                    try {
                        listener.method().invoke(listener.target(), event);
                    } catch (Throwable t) {
                        LOGGER.log(Level.SEVERE, "[SyntheticEventDispatcher] Error dispatching event " + eventClass.getName(), t);
                    }
                }
            }
        }
    }

    /**
     * Dispatches creative tab content events directly to registered tab items.
     */
    public static void dispatchTabContents(Object event) {
        CreativeTabShim.handleBuildContents(event);
    }

    private record EventListenerWrapper(Object target, Method method) {}
}
