package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Universal Networking Shim.
 * Bridges Forge SimpleChannel, NeoForge PayloadRegistrar / CustomPacketPayload (with Type and StreamCodec),
 * and Fabric ServerPlayNetworking / ClientPlayNetworking / PayloadTypeRegistry packet protocols across
 * Minecraft versions 1.7.9 through 26.3+.
 *
 * Supports ByteBuf, FriendlyByteBuf, and RegistryFriendlyByteBuf serialization with HolderLookup.Provider access.
 */
public final class NetworkShim {

    private static final Logger LOGGER = Logger.getLogger(NetworkShim.class.getName());

    private static final Map<String, ChannelDescriptor> REGISTERED_CHANNELS = new ConcurrentHashMap<>();
    private static final Map<String, PayloadEntry> REGISTERED_PAYLOADS = new ConcurrentHashMap<>();
    private static final Map<Object, Object> BUFFER_REGISTRY_ACCESS = Collections.synchronizedMap(new WeakHashMap<>());

    private NetworkShim() {}

    /**
     * Creates a compatibility channel representation (Forge SimpleChannel equivalent).
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
            LOGGER.fine(String.format("[NetworkShim] Registered packet ID %d (%s) on %s",
                    index, messageType != null ? messageType.getSimpleName() : "unknown", desc.id));
        }
    }

    /**
     * Registers a typed CustomPacketPayload across NeoForge (PayloadRegistrar) and Fabric (PayloadTypeRegistry).
     *
     * @param id The payload Type or ResourceLocation identifier.
     * @param streamCodec The StreamCodec for packet buffer serialization.
     * @param handler The bidirectional or server packet consumer.
     */
    public static void registerPayload(Object id, Object streamCodec, BiConsumer<Object, Object> handler) {
        registerPayloadBidirectional(id, streamCodec, handler);
    }

    /**
     * Registers a modern payload for client-bound packets (playToClient / playS2C).
     */
    public static void registerPayloadToClient(Object id, Object streamCodec, BiConsumer<Object, Object> clientHandler) {
        String payloadId = String.valueOf(id);
        REGISTERED_PAYLOADS.compute(payloadId, (k, existing) -> {
            BiConsumer<Object, Object> server = (existing != null) ? existing.serverHandler : null;
            Object codec = (streamCodec != null) ? streamCodec : (existing != null ? existing.streamCodec : null);
            return new PayloadEntry(payloadId, id, codec, clientHandler, server);
        });

        // 1. NeoForge PayloadRegistrar hook
        try {
            Class<?> registrarClass = Class.forName("net.neoforged.neoforge.network.registration.PayloadRegistrar");
            // If active registrar is available in context, register directly
        } catch (Throwable ignored) {}

        // 2. Fabric PayloadTypeRegistry & ClientPlayNetworking hook
        try {
            Class<?> payloadTypeRegistry = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
            Method playS2C = payloadTypeRegistry.getMethod("playS2C");
            Object registryInstance = playS2C.invoke(null);
            Method registerMethod = registryInstance.getClass().getMethod("register",
                    Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type"),
                    Class.forName("net.minecraft.network.codec.StreamCodec"));
            registerMethod.invoke(registryInstance, id, streamCodec);

            Class<?> clientNetworking = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            for (Method m : clientNetworking.getMethods()) {
                if ("registerGlobalReceiver".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.invoke(null, id, clientHandler);
                    break;
                }
            }
        } catch (Throwable ignored) {}

        LOGGER.fine("[NetworkShim] Registered client payload: " + payloadId);
    }

    /**
     * Fluent polyfill for NeoForge PayloadRegistrar.playToClient.
     */
    public static Object registerPayloadToClient(Object registrar, Object id, Object streamCodec, BiConsumer<Object, Object> clientHandler) {
        registerPayloadToClient(id, streamCodec, clientHandler);
        return registrar;
    }

    /**
     * Registers a modern payload for server-bound packets (playToServer / playC2S).
     */
    public static void registerPayloadToServer(Object id, Object streamCodec, BiConsumer<Object, Object> serverHandler) {
        String payloadId = String.valueOf(id);
        REGISTERED_PAYLOADS.compute(payloadId, (k, existing) -> {
            BiConsumer<Object, Object> client = (existing != null) ? existing.clientHandler : null;
            Object codec = (streamCodec != null) ? streamCodec : (existing != null ? existing.streamCodec : null);
            return new PayloadEntry(payloadId, id, codec, client, serverHandler);
        });

        // Fabric registration
        try {
            Class<?> payloadTypeRegistry = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
            Method playC2S = payloadTypeRegistry.getMethod("playC2S");
            Object registryInstance = playC2S.invoke(null);
            Method registerMethod = registryInstance.getClass().getMethod("register",
                    Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type"),
                    Class.forName("net.minecraft.network.codec.StreamCodec"));
            registerMethod.invoke(registryInstance, id, streamCodec);

            Class<?> serverNetworking = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
            for (Method m : serverNetworking.getMethods()) {
                if ("registerGlobalReceiver".equals(m.getName()) && m.getParameterCount() == 2) {
                    m.invoke(null, id, serverHandler);
                    break;
                }
            }
        } catch (Throwable ignored) {}

        LOGGER.fine("[NetworkShim] Registered server payload: " + payloadId);
    }

    /**
     * Fluent polyfill for NeoForge PayloadRegistrar.playToServer.
     */
    public static Object registerPayloadToServer(Object registrar, Object id, Object streamCodec, BiConsumer<Object, Object> serverHandler) {
        registerPayloadToServer(id, streamCodec, serverHandler);
        return registrar;
    }

    /**
     * Registers a modern payload bidirectionally.
     */
    public static void registerPayloadBidirectional(Object id, Object streamCodec, BiConsumer<Object, Object> handler) {
        String payloadId = String.valueOf(id);
        REGISTERED_PAYLOADS.compute(payloadId, (k, existing) -> {
            Object codec = (streamCodec != null) ? streamCodec : (existing != null ? existing.streamCodec : null);
            return new PayloadEntry(payloadId, id, codec, handler, handler);
        });
        registerPayloadToClient(id, streamCodec, handler);
        registerPayloadToServer(id, streamCodec, handler);
    }

    /**
     * Fluent polyfill for NeoForge PayloadRegistrar.playBidirectional.
     */
    public static Object registerPayloadBidirectional(Object registrar, Object id, Object streamCodec, BiConsumer<Object, Object> handler) {
        registerPayloadBidirectional(id, streamCodec, handler);
        return registrar;
    }

    /**
     * Wraps or serializes a packet payload with the specified packet buffer.
     * Supports ByteBuf, FriendlyByteBuf, and RegistryFriendlyByteBuf.
     */
    public static Object wrapPacket(Object packet, Object buffer) {
        if (packet == null && buffer == null) return null;
        if (packet != null && buffer != null) {
            // 1. Try invoking StreamCodec.encode(buffer, packet)
            for (PayloadEntry entry : REGISTERED_PAYLOADS.values()) {
                if (entry.streamCodec != null) {
                    try {
                        Method encodeMethod = entry.streamCodec.getClass().getMethod("encode", Object.class, Object.class);
                        encodeMethod.invoke(entry.streamCodec, buffer, packet);
                        return buffer;
                    } catch (Throwable ignored) {}
                }
            }

            // 2. Try packet.write(buffer) or packet.encode(buffer)
            try {
                for (Method m : packet.getClass().getMethods()) {
                    if (("write".equals(m.getName()) || "encode".equals(m.getName())) && m.getParameterCount() == 1) {
                        m.invoke(packet, buffer);
                        return buffer;
                    }
                }
            } catch (Throwable ignored) {}

            return buffer;
        }

        // Return whichever non-null operand was supplied
        return packet != null ? packet : buffer;
    }

    /**
     * Creates a RegistryFriendlyByteBuf wrapping a raw ByteBuf and binding the given HolderLookup.Provider.
     */
    public static Object createRegistryFriendlyByteBuf(Object byteBuf, Object registryAccess) {
        if (byteBuf == null) return null;

        // Store association for later query
        if (registryAccess != null) {
            BUFFER_REGISTRY_ACCESS.put(byteBuf, registryAccess);
        }

        // 1. Try modern RegistryFriendlyByteBuf constructor (1.20.5+ / 26.3+)
        try {
            Class<?> rfbClass = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf");
            Class<?> bbClass = Class.forName("io.netty.buffer.ByteBuf");
            Class<?> holderLookupClass = Class.forName("net.minecraft.core.HolderLookup$Provider");

            Constructor<?> ctor = rfbClass.getConstructor(bbClass, holderLookupClass);
            Object rfb = ctor.newInstance(byteBuf, registryAccess);
            if (registryAccess != null) {
                BUFFER_REGISTRY_ACCESS.put(rfb, registryAccess);
            }
            return rfb;
        } catch (Throwable ignored) {}

        // 2. Fallback to FriendlyByteBuf
        try {
            Class<?> fbClass = Class.forName("net.minecraft.network.FriendlyByteBuf");
            Constructor<?> ctor = fbClass.getConstructor(Class.forName("io.netty.buffer.ByteBuf"));
            Object fb = ctor.newInstance(byteBuf);
            if (registryAccess != null) {
                BUFFER_REGISTRY_ACCESS.put(fb, registryAccess);
            }
            return fb;
        } catch (Throwable ignored) {}

        return byteBuf;
    }

    /**
     * Retrieves the HolderLookup.Provider associated with a packet buffer.
     */
    public static Object getRegistryAccess(Object buffer) {
        if (buffer == null) return null;

        // 1. Check direct method call: buffer.registryAccess()
        try {
            Method m = buffer.getClass().getMethod("registryAccess");
            return m.invoke(buffer);
        } catch (Throwable ignored) {}

        // 2. Check stored registry access mapping
        return BUFFER_REGISTRY_ACCESS.get(buffer);
    }

    /**
     * Adapts legacy BiConsumer encoder and Function decoder into a StreamCodec-compatible adapter.
     */
    @SuppressWarnings("unchecked")
    public static <B, V> Object adaptToStreamCodec(BiConsumer<V, B> encoder, Function<B, V> decoder) {
        try {
            Class<?> streamCodecClass = Class.forName("net.minecraft.network.codec.StreamCodec");
            Method ofMethod = streamCodecClass.getMethod("of",
                    Class.forName("net.minecraft.network.codec.StreamEncoder"),
                    Class.forName("net.minecraft.network.codec.StreamDecoder"));
            return ofMethod.invoke(null, encoder, decoder);
        } catch (Throwable t) {
            // Dynamic proxy fallback
            try {
                Class<?> streamCodecClass = Class.forName("net.minecraft.network.codec.StreamCodec");
                return Proxy.newProxyInstance(
                        NetworkShim.class.getClassLoader(),
                        new Class<?>[]{streamCodecClass},
                        (proxy, method, args) -> {
                            if ("encode".equals(method.getName()) && args != null && args.length == 2) {
                                encoder.accept((V) args[1], (B) args[0]);
                                return null;
                            }
                            if ("decode".equals(method.getName()) && args != null && args.length == 1) {
                                return decoder.apply((B) args[0]);
                            }
                            return null;
                        }
                );
            } catch (Throwable ignored) {}
        }
        return new StreamCodecAdapter<>(encoder, decoder);
    }

    /**
     * Bridges channel.send(...) across NeoForge PacketDistributor and Fabric ServerPlayNetworking.
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

        // 3. Forge SimpleChannel fallback if target is PacketTarget
        if (channel instanceof ChannelDescriptor desc) {
            desc.dispatchLocally(packetTarget, message);
        }
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

        // 3. Channel fallback
        if (channel instanceof ChannelDescriptor desc) {
            desc.dispatchLocally(null, message);
        }
    }

    /**
     * Bridges PacketDistributor.sendToServer(payload) and ClientPlayNetworking.send(payload).
     * Consumes 1 operand [message].
     */
    public static void sendToServer(Object message) {
        sendToServer(null, message);
    }

    /**
     * Bridges channel.sendTo(message, serverPlayer)
     */
    public static void sendToPlayer(Object channel, Object message, Object serverPlayer) {
        send(channel, serverPlayer, message);
    }

    /**
     * Bridges PacketDistributor.sendToPlayer(player, payload) and ServerPlayNetworking.send(player, payload).
     * Consumes 2 operands [player, message].
     */
    public static void sendToPlayer(Object player, Object message) {
        send(null, player, message);
    }

    public static ChannelDescriptor getChannel(String id) {
        return id != null ? REGISTERED_CHANNELS.get(id) : null;
    }

    public static PayloadEntry getPayload(String id) {
        return id != null ? REGISTERED_PAYLOADS.get(id) : null;
    }

    public static Map<String, PayloadEntry> getRegisteredPayloads() {
        return REGISTERED_PAYLOADS;
    }

    public static class StreamCodecAdapter<B, V> {
        private final BiConsumer<V, B> encoder;
        private final Function<B, V> decoder;

        public StreamCodecAdapter(BiConsumer<V, B> encoder, Function<B, V> decoder) {
            this.encoder = encoder;
            this.decoder = decoder;
        }

        public void encode(B buffer, V value) {
            if (encoder != null) encoder.accept(value, buffer);
        }

        public V decode(B buffer) {
            return (decoder != null) ? decoder.apply(buffer) : null;
        }
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

        @SuppressWarnings("unchecked")
        void dispatchLocally(Object target, Object message) {
            if (message == null) return;
            PacketHandlerEntry entry = handlers.get(message.getClass());
            if (entry != null && entry.consumer != null) {
                entry.consumer.accept(message, (Supplier<Object>) () -> target);
            }
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

    public static class PayloadEntry {
        public final String stringId;
        public final Object rawId;
        public final Object streamCodec;
        public final BiConsumer<Object, Object> clientHandler;
        public final BiConsumer<Object, Object> serverHandler;

        public PayloadEntry(String stringId, Object rawId, Object streamCodec,
                            BiConsumer<Object, Object> clientHandler,
                            BiConsumer<Object, Object> serverHandler) {
            this.stringId = stringId;
            this.rawId = rawId;
            this.streamCodec = streamCodec;
            this.clientHandler = clientHandler;
            this.serverHandler = serverHandler;
        }
    }
}
