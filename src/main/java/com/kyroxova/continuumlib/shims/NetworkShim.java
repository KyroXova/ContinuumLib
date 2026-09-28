package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Networking Shim.
 * Bridges Forge SimpleChannel, NeoForge PayloadRegistrar / CustomPacketPayload,
 * and Fabric ServerPlayNetworking / ClientPlayNetworking packet protocols.
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
        String version = (networkProtocolVersion != null) ? networkProtocolVersion.get() : "1";
        ChannelDescriptor desc = new ChannelDescriptor(channelId, version);
        REGISTERED_CHANNELS.put(channelId, desc);
        LOGGER.info("[NetworkShim] Registered network channel: " + channelId);
        return desc;
    }

    /**
     * Overload for simpler channel creation.
     */
    public static Object createSimpleChannel(Object name) {
        return createSimpleChannel(name, () -> "1", v -> true, v -> true);
    }

    /**
     * Bridges channel.registerMessage(...)
     */
    public static <MSG> void registerMessage(Object channel, int index, Class<MSG> messageType,
                                             BiConsumer<MSG, Object> encoder,
                                             Function<Object, MSG> decoder,
                                             BiConsumer<MSG, Supplier<Object>> messageConsumer) {
        if (channel instanceof ChannelDescriptor desc) {
            desc.register(index, messageType, encoder, decoder, messageConsumer);
            LOGGER.fine(String.format("[NetworkShim] Registered packet ID %d (%s) on %s", index, messageType.getSimpleName(), desc.id));
        }
    }

    /**
     * Bridges channel.send(...)
     */
    public static void send(Object channel, Object packetTarget, Object message) {
        if (message == null) return;
        LOGGER.fine("[NetworkShim] Dispatching packet " + message.getClass().getSimpleName() + " to " + packetTarget);

        // 1. NeoForge PacketDistributor check
        try {
            Class<?> distributorClass = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            Method sendMethod = distributorClass.getMethod("sendToPlayer", Class.forName("net.minecraft.server.level.ServerPlayer"), Object.class);
            sendMethod.invoke(null, packetTarget, message);
            return;
        } catch (Throwable ignored) {}

        // 2. Fabric ServerPlayNetworking check
        try {
            Class<?> fabricNetworking = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
            for (Method m : fabricNetworking.getMethods()) {
                if ("send".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.invoke(null, packetTarget, message);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Bridges channel.sendToServer(message)
     */
    public static void sendToServer(Object channel, Object message) {
        if (message == null) return;
        LOGGER.fine("[NetworkShim] Dispatching packet to server: " + message.getClass().getSimpleName());

        // 1. NeoForge PacketDistributor.sendToServer
        try {
            Class<?> distributorClass = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            Method sendMethod = distributorClass.getMethod("sendToServer", Object.class);
            sendMethod.invoke(null, message);
            return;
        } catch (Throwable ignored) {}

        // 2. Fabric ClientPlayNetworking.send
        try {
            Class<?> clientNetworking = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            for (Method m : clientNetworking.getMethods()) {
                if ("send".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(null, message);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Bridges channel.sendTo(message, serverPlayer)
     */
    public static void sendToPlayer(Object channel, Object message, Object serverPlayer) {
        send(channel, serverPlayer, message);
    }

    public static ChannelDescriptor getChannel(String id) {
        return REGISTERED_CHANNELS.get(id);
    }

    public static class ChannelDescriptor {
        public final String id;
        public final String version;
        public final Map<Class<?>, PacketHandlerEntry<?>> handlers = new ConcurrentHashMap<>();

        public ChannelDescriptor(String id, String version) {
            this.id = id;
            this.version = version;
        }

        public <MSG> void register(int index, Class<MSG> messageType,
                                   BiConsumer<MSG, Object> encoder,
                                   Function<Object, MSG> decoder,
                                   BiConsumer<MSG, Supplier<Object>> messageConsumer) {
            handlers.put(messageType, new PacketHandlerEntry<>(index, messageType, encoder, decoder, messageConsumer));
        }

        public <MSG> void registerMessage(int index, Class<MSG> messageType,
                                          BiConsumer<MSG, Object> encoder,
                                          Function<Object, MSG> decoder,
                                          BiConsumer<MSG, Supplier<Object>> messageConsumer) {
            register(index, messageType, encoder, decoder, messageConsumer);
        }

        public void send(Object target, Object message) {
            NetworkShim.send(this, target, message);
        }

        public void sendToServer(Object message) {
            NetworkShim.sendToServer(this, message);
        }

        public void sendTo(Object message, Object player) {
            NetworkShim.sendToPlayer(this, message, player);
        }
    }

    public static class PacketHandlerEntry<MSG> {
        public final int index;
        public final Class<MSG> type;
        public final BiConsumer<MSG, Object> encoder;
        public final Function<Object, MSG> decoder;
        public final BiConsumer<MSG, Supplier<Object>> consumer;

        public PacketHandlerEntry(int index, Class<MSG> type,
                                  BiConsumer<MSG, Object> encoder,
                                  Function<Object, MSG> decoder,
                                  BiConsumer<MSG, Supplier<Object>> consumer) {
            this.index = index;
            this.type = type;
            this.encoder = encoder;
            this.decoder = decoder;
            this.consumer = consumer;
        }
    }
}
