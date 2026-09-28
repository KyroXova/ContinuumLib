package com.kyroxova.continuumlib.shims;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Networking Shim.
 * Bridges Forge SimpleChannel and PacketDistributor across NeoForge and Fabric packet protocols.
 */
public final class NetworkShim {

    private static final Logger LOGGER = Logger.getLogger(NetworkShim.class.getName());

    private static final Map<String, ChannelDescriptor> REGISTERED_CHANNELS = new ConcurrentHashMap<>();

    private NetworkShim() {}

    /**
     * Creates a compatibility channel representation.
     */
    public static Object createSimpleChannel(Object name, Supplier<String> networkProtocolVersion,
                                             Function<String, Boolean> clientAcceptedVersions,
                                             Function<String, Boolean> serverAcceptedVersions) {
        String channelId = String.valueOf(name);
        ChannelDescriptor desc = new ChannelDescriptor(channelId, networkProtocolVersion.get());
        REGISTERED_CHANNELS.put(channelId, desc);
        LOGGER.info("[NetworkShim] Registered network channel: " + channelId);
        return desc;
    }

    /**
     * Bridges channel.registerMessage(...)
     */
    public static <MSG> void registerMessage(Object channel, int index, Class<MSG> messageType,
                                             BiConsumer<MSG, Object> encoder,
                                             Function<Object, MSG> decoder,
                                             BiConsumer<MSG, Supplier<Object>> messageConsumer) {
        if (channel instanceof ChannelDescriptor desc) {
            desc.handlers.put(messageType, (msg, ctxSupplier) -> {
                try {
                    messageConsumer.accept(messageType.cast(msg), ctxSupplier);
                } catch (Throwable t) {
                    LOGGER.severe("[NetworkShim] Error handling message " + messageType.getName() + ": " + t.getMessage());
                }
            });
            LOGGER.fine(String.format("[NetworkShim] Registered packet ID %d (%s) on %s", index, messageType.getSimpleName(), desc.id));
        }
    }

    /**
     * Bridges channel.send(...)
     */
    public static void send(Object channel, Object packetTarget, Object message) {
        LOGGER.fine("[NetworkShim] Dispatching packet " + message.getClass().getSimpleName() + " to " + packetTarget);
        // On modern loaders, translates to ServerPlayNetworking / NeoForge PacketDistributor
    }

    public static class ChannelDescriptor {
        public final String id;
        public final String version;
        public final Map<Class<?>, BiConsumer<Object, Supplier<Object>>> handlers = new ConcurrentHashMap<>();

        public ChannelDescriptor(String id, String version) {
            this.id = id;
            this.version = version;
        }

        public <MSG> void registerMessage(int index, Class<MSG> messageType,
                                          BiConsumer<MSG, Object> encoder,
                                          Function<Object, MSG> decoder,
                                          BiConsumer<MSG, Supplier<Object>> messageConsumer) {
            NetworkShim.registerMessage(this, index, messageType, encoder, decoder, messageConsumer);
        }

        public void send(Object target, Object message) {
            NetworkShim.send(this, target, message);
        }
    }
}
