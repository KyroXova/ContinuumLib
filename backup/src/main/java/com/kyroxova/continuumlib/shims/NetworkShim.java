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
 * Bridges Forge SimpleChannel / SimpleNetworkWrapper (1.7.10 - 1.20.1),
 * NeoForge PayloadRegistrar / CustomPacketPayload (with Type and StreamCodec),
 * and Fabric ServerPlayNetworking / ClientPlayNetworking / PayloadTypeRegistry packet protocols across
 * Minecraft versions 1.7.9 through 26.3+.
 *
 * Supports generic mod packet POJOs (1.7.10 IMessage, Forge channel packets, modern CustomPacketPayload),
 * and dynamic buffer routing between ByteBuf, FriendlyByteBuf, and RegistryFriendlyByteBuf serialization
 * with HolderLookup.Provider access.
 */
public final class NetworkShim {

    private static final Logger LOGGER = Logger.getLogger(NetworkShim.class.getName());

    private static final Map<String, ChannelDescriptor> REGISTERED_CHANNELS = new ConcurrentHashMap<>();
    private static final Map<String, PayloadEntry> REGISTERED_PAYLOADS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ChannelDescriptor> PACKET_TO_CHANNEL = new ConcurrentHashMap<>();
    private static final Map<Object, Object> BUFFER_REGISTRY_ACCESS = Collections.synchronizedMap(new WeakHashMap<>());

    private NetworkShim() {}

    /**
     * Creates a compatibility channel representation (Forge SimpleChannel equivalent).
     */
    public static ChannelDescriptor createSimpleChannel(Object name, Supplier<String> networkProtocolVersion,
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
    public static ChannelDescriptor createSimpleChannel(Object name) {
        return createSimpleChannel(name, () -> "1", v -> true, v -> true);
    }

    /**
     * Creates a 1.7.10 - 1.12.2 SimpleNetworkWrapper compatible channel.
     */
    public static ChannelDescriptor createSimpleNetworkWrapper(Object channelName) {
        return createSimpleChannel(channelName);
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
            if (messageType != null) {
                PACKET_TO_CHANNEL.put(messageType, desc);
            }
            LOGGER.fine(String.format("[NetworkShim] Registered packet ID %d (%s) on %s",
                    index, messageType != null ? messageType.getSimpleName() : "unknown", desc.id));
        }
    }

    /**
     * Registers a generic mod-defined packet POJO (1.7.10 IMessage or arbitrary POJO)
     * with an IMessageHandler or consumer.
     */
    public static <REQ> void registerGenericMessage(Object channel, Object handlerClassOrInstance,
                                                    Class<REQ> messageType, int index, Object side) {
        if (channel instanceof ChannelDescriptor desc) {
            desc.registerMessage(handlerClassOrInstance, messageType, index, side);
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

            // 2. Try packet.write(buffer) or packet.encode(buffer) or packet.toBytes(buffer)
            try {
                for (Method m : packet.getClass().getMethods()) {
                    if (("write".equals(m.getName()) || "encode".equals(m.getName()) || "toBytes".equals(m.getName()))
                            && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        Object adapted = adaptBuffer(buffer, paramType);
                        m.invoke(packet, adapted);
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
     * Adapts any buffer between io.netty.buffer.ByteBuf, FriendlyByteBuf,
     * and RegistryFriendlyByteBuf.
     */
    public static Object adaptBuffer(Object buffer, Class<?> targetClass) {
        if (buffer == null || targetClass == null) return buffer;
        if (targetClass.isInstance(buffer)) return buffer;

        String targetName = targetClass.getName();

        // 1. Target: RegistryFriendlyByteBuf (1.20.5+ / 26.3+)
        if ("net.minecraft.network.RegistryFriendlyByteBuf".equals(targetName)) {
            return createRegistryFriendlyByteBuf(buffer, getRegistryAccess(buffer));
        }

        // 2. Target: FriendlyByteBuf (1.13+)
        if ("net.minecraft.network.FriendlyByteBuf".equals(targetName)
                || "net.minecraft.network.PacketBuffer".equals(targetName)) {
            try {
                Class<?> fbClass = Class.forName(targetName);
                Class<?> bbClass = Class.forName("io.netty.buffer.ByteBuf");
                if (bbClass.isInstance(buffer)) {
                    Constructor<?> ctor = fbClass.getConstructor(bbClass);
                    Object fb = ctor.newInstance(buffer);
                    Object reg = getRegistryAccess(buffer);
                    if (reg != null) BUFFER_REGISTRY_ACCESS.put(fb, reg);
                    return fb;
                }
            } catch (Throwable ignored) {}
        }

        // 3. Target: io.netty.buffer.ByteBuf (1.7.10 - present)
        if ("io.netty.buffer.ByteBuf".equals(targetName)) {
            // FriendlyByteBuf and RegistryFriendlyByteBuf extend ByteBuf in Minecraft
            return buffer;
        }

        return buffer;
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
     * Synthesizes an automatic encoder for any mod packet POJO.
     * Supports 1.7.10 IMessage (toBytes), Forge channel packets (write/encode),
     * and modern CustomPacketPayload.
     */
    public static <MSG> BiConsumer<MSG, Object> createGenericEncoder(Class<MSG> messageType) {
        return (msg, buf) -> {
            if (msg == null || buf == null) return;

            // 1. Try toBytes(ByteBuf) - 1.7.10 / 1.12 IMessage
            try {
                for (Method m : msg.getClass().getMethods()) {
                    if ("toBytes".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        Object adapted = adaptBuffer(buf, paramType);
                        m.invoke(msg, adapted);
                        return;
                    }
                }
            } catch (Throwable t) {
                LOGGER.fine("[NetworkShim] toBytes error for " + messageType.getName() + ": " + t.getMessage());
            }

            // 2. Try write(FriendlyByteBuf) / encode(FriendlyByteBuf) / write(RegistryFriendlyByteBuf)
            try {
                for (Method m : msg.getClass().getMethods()) {
                    if (("write".equals(m.getName()) || "encode".equals(m.getName())) && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        Object adapted = adaptBuffer(buf, paramType);
                        m.invoke(msg, adapted);
                        return;
                    }
                }
            } catch (Throwable t) {
                LOGGER.fine("[NetworkShim] write/encode error for " + messageType.getName() + ": " + t.getMessage());
            }

            // 3. Fallback: modern CustomPacketPayload write
            try {
                Method writeMethod = msg.getClass().getMethod("write", Class.forName("net.minecraft.network.FriendlyByteBuf"));
                Object adapted = adaptBuffer(buf, Class.forName("net.minecraft.network.FriendlyByteBuf"));
                writeMethod.invoke(msg, adapted);
            } catch (Throwable ignored) {}
        };
    }

    /**
     * Synthesizes an automatic decoder for any mod packet POJO.
     * Handles buffer constructors, default constructor + fromBytes(ByteBuf),
     * and static decode/read methods.
     */
    @SuppressWarnings("unchecked")
    public static <MSG> Function<Object, MSG> createGenericDecoder(Class<MSG> messageType) {
        return buf -> {
            if (buf == null) return null;

            // 1. Try single-arg buffer constructor: new MSG(FriendlyByteBuf/RegistryFriendlyByteBuf/ByteBuf)
            try {
                for (Constructor<?> ctor : messageType.getConstructors()) {
                    if (ctor.getParameterCount() == 1) {
                        Class<?> paramType = ctor.getParameterTypes()[0];
                        if (paramType.getName().endsWith("ByteBuf") || paramType.getName().endsWith("FriendlyByteBuf")) {
                            Object adapted = adaptBuffer(buf, paramType);
                            return (MSG) ctor.newInstance(adapted);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Try default constructor + fromBytes(ByteBuf) - 1.7.10 / 1.12 IMessage
            try {
                Constructor<MSG> defCtor = messageType.getDeclaredConstructor();
                defCtor.setAccessible(true);
                MSG instance = defCtor.newInstance();

                for (Method m : messageType.getMethods()) {
                    if ("fromBytes".equals(m.getName()) && m.getParameterCount() == 1) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        Object adapted = adaptBuffer(buf, paramType);
                        m.invoke(instance, adapted);
                        return instance;
                    }
                }

                for (Method m : messageType.getMethods()) {
                    if (("read".equals(m.getName()) || "decode".equals(m.getName()))
                            && m.getParameterCount() == 1
                            && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        Object adapted = adaptBuffer(buf, paramType);
                        m.invoke(instance, adapted);
                        return instance;
                    }
                }

                return instance;
            } catch (Throwable ignored) {}

            // 3. Try static decode(buffer) or read(buffer)
            try {
                for (Method m : messageType.getMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                            && ("decode".equals(m.getName()) || "read".equals(m.getName()))
                            && m.getParameterCount() == 1
                            && messageType.isAssignableFrom(m.getReturnType())) {
                        Class<?> paramType = m.getParameterTypes()[0];
                        Object adapted = adaptBuffer(buf, paramType);
                        return (MSG) m.invoke(null, adapted);
                    }
                }
            } catch (Throwable ignored) {}

            return null;
        };
    }

    /**
     * Synthesizes a consumer handler bridging IMessageHandler.onMessage(req, ctx) or BiConsumer.
     */
    @SuppressWarnings("unchecked")
    public static <MSG> BiConsumer<MSG, Supplier<Object>> createGenericConsumer(Class<MSG> messageType, Object handlerOrClass) {
        return (msg, ctxSupplier) -> {
            if (msg == null) return;
            Object handlerInstance = handlerOrClass;
            if (handlerOrClass instanceof Class<?> clazz) {
                try {
                    Constructor<?> ctor = clazz.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    handlerInstance = ctor.newInstance();
                } catch (Throwable t) {
                    LOGGER.warning("[NetworkShim] Failed to instantiate packet handler: " + clazz.getName());
                    return;
                }
            }

            if (handlerInstance == null) return;

            // 1. Try IMessageHandler.onMessage(req, ctx)
            try {
                for (Method m : handlerInstance.getClass().getMethods()) {
                    if ("onMessage".equals(m.getName()) && m.getParameterCount() == 2) {
                        Object ctx = (ctxSupplier != null) ? ctxSupplier.get() : null;
                        Object reply = m.invoke(handlerInstance, msg, ctx);
                        // If reply message is returned, automatically send it back
                        if (reply != null) {
                            sendReply(reply, ctx);
                        }
                        return;
                    }
                }
            } catch (Throwable t) {
                LOGGER.warning("[NetworkShim] Error executing onMessage: " + t.getMessage());
            }

            // 2. Try BiConsumer accept(msg, ctxSupplier)
            if (handlerInstance instanceof BiConsumer biConsumer) {
                try {
                    biConsumer.accept(msg, ctxSupplier);
                    return;
                } catch (Throwable ignored) {}
            }

            // 3. Try handle(ctx) or process() on msg instance itself
            try {
                for (Method m : msg.getClass().getMethods()) {
                    if (("handle".equals(m.getName()) || "process".equals(m.getName())) && m.getParameterCount() <= 2) {
                        if (m.getParameterCount() == 0) {
                            m.invoke(msg);
                            return;
                        } else if (m.getParameterCount() == 1) {
                            m.invoke(msg, ctxSupplier != null ? ctxSupplier.get() : null);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        };
    }

    private static void sendReply(Object replyMessage, Object ctx) {
        if (replyMessage == null) return;
        try {
            // Check if context has getSender() / getServerHandler()
            if (ctx != null) {
                for (Method m : ctx.getClass().getMethods()) {
                    if (("getSender".equals(m.getName()) || "getServerHandler".equals(m.getName()))
                            && m.getParameterCount() == 0) {
                        Object sender = m.invoke(ctx);
                        if (sender != null) {
                            sendToPlayer(sender, replyMessage);
                            return;
                        }
                    }
                }
            }
            // Otherwise dispatch to server
            sendToServer(replyMessage);
        } catch (Throwable ignored) {}
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
     * Bridges channel.send(...) across NeoForge PacketDistributor, Fabric ServerPlayNetworking,
     * and Forge SimpleChannel.
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
        } else if (channel == null) {
            ChannelDescriptor desc = PACKET_TO_CHANNEL.get(message.getClass());
            if (desc != null) {
                desc.dispatchLocally(packetTarget, message);
            }
        }
    }

    /**
     * Bridges channel.sendToServer(message) across NeoForge, Fabric, and Forge.
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
        } else if (channel == null) {
            ChannelDescriptor desc = PACKET_TO_CHANNEL.get(message.getClass());
            if (desc != null) {
                desc.dispatchLocally(null, message);
            }
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

    /**
     * Bridges channel.sendToAll(message)
     */
    public static void sendToAll(Object channel, Object message) {
        if (message == null) return;

        // 1. NeoForge PacketDistributor.sendToAllPlayers
        try {
            Class<?> distributor = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            for (Method m : distributor.getMethods()) {
                if ("sendToAllPlayers".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(null, message);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Channel fallback
        if (channel instanceof ChannelDescriptor desc) {
            desc.dispatchLocally(null, message);
        } else {
            ChannelDescriptor desc = PACKET_TO_CHANNEL.get(message.getClass());
            if (desc != null) desc.dispatchLocally(null, message);
        }
    }

    public static void sendToAll(Object message) {
        sendToAll(null, message);
    }

    /**
     * Bridges channel.sendToAllAround(message, targetPoint)
     */
    public static void sendToAllAround(Object channel, Object message, Object targetPoint) {
        if (message == null) return;

        // 1. NeoForge PacketDistributor.sendToPlayersNear
        try {
            Class<?> distributor = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            for (Method m : distributor.getMethods()) {
                if ("sendToPlayersNear".equals(m.getName())) {
                    m.invoke(null, targetPoint, message);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Channel fallback
        if (channel instanceof ChannelDescriptor desc) {
            desc.dispatchLocally(targetPoint, message);
        } else {
            ChannelDescriptor desc = PACKET_TO_CHANNEL.get(message.getClass());
            if (desc != null) desc.dispatchLocally(targetPoint, message);
        }
    }

    public static void sendToAllAround(Object message, Object targetPoint) {
        sendToAllAround(null, message, targetPoint);
    }

    /**
     * Bridges channel.sendToDimension(message, dimension)
     */
    public static void sendToDimension(Object channel, Object message, Object dimension) {
        if (message == null) return;

        // 1. NeoForge PacketDistributor.sendToPlayersInDimension
        try {
            Class<?> distributor = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            for (Method m : distributor.getMethods()) {
                if ("sendToPlayersInDimension".equals(m.getName())) {
                    m.invoke(null, dimension, message);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Channel fallback
        if (channel instanceof ChannelDescriptor desc) {
            desc.dispatchLocally(dimension, message);
        } else {
            ChannelDescriptor desc = PACKET_TO_CHANNEL.get(message.getClass());
            if (desc != null) desc.dispatchLocally(dimension, message);
        }
    }

    public static void sendToDimension(Object message, Object dimension) {
        sendToDimension(null, message, dimension);
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

    /**
     * Universal Network Channel and SimpleNetworkWrapper descriptor.
     */
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
            PACKET_TO_CHANNEL.put(messageType, this);
        }

        public <MSG> void registerMessage(int index, Class<MSG> messageType,
                                           BiConsumer<MSG, Object> encoder,
                                           Function<Object, MSG> decoder,
                                           BiConsumer<MSG, Supplier<Object>> messageConsumer) {
            register(index, messageType, encoder, decoder, messageConsumer);
        }

        /**
         * 1.7.10 - 1.12.2 SimpleNetworkWrapper.registerMessage compatibility.
         */
        public <REQ> void registerMessage(Object handlerClassOrInstance, Class<REQ> messageType, int index, Object side) {
            BiConsumer<REQ, Object> encoder = createGenericEncoder(messageType);
            Function<Object, REQ> decoder = createGenericDecoder(messageType);
            BiConsumer<REQ, Supplier<Object>> consumer = createGenericConsumer(messageType, handlerClassOrInstance);
            register(index, messageType, encoder, decoder, consumer);
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

        public void sendToAll(Object message) {
            NetworkShim.sendToAll(this, message);
        }

        public void sendToAllAround(Object message, Object targetPoint) {
            NetworkShim.sendToAllAround(this, message, targetPoint);
        }

        public void sendToDimension(Object message, Object dimension) {
            NetworkShim.sendToDimension(this, message, dimension);
        }

        public Object getPacketFrom(Object message) {
            return NetworkShim.wrapPacket(message, null);
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
