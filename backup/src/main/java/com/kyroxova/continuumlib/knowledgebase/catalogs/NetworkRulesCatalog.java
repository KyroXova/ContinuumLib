package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Networking and Packet Communication.
 * Bridges Forge SimpleChannel across NeoForge (1.20.2+ / 1.20.4+ / 1.21+ / 26.3+) and Fabric.
 */
public final class NetworkRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_2 = MCVersion.of("1.20.2");
        MCVersion v1_20_5 = MCVersion.of("1.20.5");

        // 1. NetworkRegistry.newSimpleChannel -> NetworkShim.createSimpleChannel (4-param)
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/NetworkRegistry", "newSimpleChannel",
                "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;Ljava/util/function/Predicate;Ljava/util/function/Predicate;)Lnet/minecraftforge/network/simple/SimpleChannel;",
                "com/kyroxova/continuumlib/shims/NetworkShim", "createSimpleChannel",
                "(Ljava/lang/Object;Ljava/util/function/Supplier;Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/lang/Object;",
                v1_20_2, null, null,
                "NetworkRegistry.newSimpleChannel -> NetworkShim.createSimpleChannel"
        ));

        // NetworkRegistry.newSimpleChannel single param overload
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/NetworkRegistry", "newSimpleChannel",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraftforge/network/simple/SimpleChannel;",
                "com/kyroxova/continuumlib/shims/NetworkShim", "createSimpleChannel",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_2, null, null,
                "NetworkRegistry.newSimpleChannel(ResourceLocation) -> NetworkShim.createSimpleChannel"
        ));

        // 2. SimpleChannel.registerMessage -> NetworkShim.registerMessage
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/simple/SimpleChannel", "registerMessage",
                "(ILjava/lang/Class;Ljava/util/function/BiConsumer;Ljava/util/function/Function;Ljava/util/function/BiConsumer;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "registerMessage",
                "(Ljava/lang/Object;ILjava/lang/Class;Ljava/util/function/BiConsumer;Ljava/util/function/Function;Ljava/util/function/BiConsumer;)V",
                v1_20_2, null, null,
                "SimpleChannel.registerMessage -> NetworkShim.registerMessage"
        ));

        // 3. SimpleChannel.send -> NetworkShim.send
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/simple/SimpleChannel", "send",
                "(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "send",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_2, null, null,
                "SimpleChannel.send -> NetworkShim.send"
        ));

        // 4. SimpleChannel.sendToServer -> NetworkShim.sendToServer
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/simple/SimpleChannel", "sendToServer",
                "(Ljava/lang/Object;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "sendToServer",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_2, null, null,
                "SimpleChannel.sendToServer -> NetworkShim.sendToServer"
        ));

        // 5. SimpleChannel.sendTo -> NetworkShim.sendToPlayer
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/simple/SimpleChannel", "sendTo",
                "(Ljava/lang/Object;Lnet/minecraft/network/Connection;Lnet/minecraftforge/network/NetworkDirection;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "sendToPlayer",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_2, null, null,
                "SimpleChannel.sendTo -> NetworkShim.sendToPlayer"
        ));

        // 6. NeoForge PayloadRegistrar routing polyfills for cross-loader compatibility
        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/network/registration/PayloadRegistrar", "playToClient",
                null,
                "com/kyroxova/continuumlib/shims/NetworkShim", "registerPayloadToClient",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiConsumer;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "NeoForge PayloadRegistrar.playToClient -> NetworkShim.registerPayloadToClient"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/network/registration/PayloadRegistrar", "playToServer",
                null,
                "com/kyroxova/continuumlib/shims/NetworkShim", "registerPayloadToServer",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiConsumer;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "NeoForge PayloadRegistrar.playToServer -> NetworkShim.registerPayloadToServer"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/network/registration/PayloadRegistrar", "playBidirectional",
                null,
                "com/kyroxova/continuumlib/shims/NetworkShim", "registerPayloadBidirectional",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiConsumer;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "NeoForge PayloadRegistrar.playBidirectional -> NetworkShim.registerPayloadBidirectional"
        ));

        // 7. PacketDistributor routing polyfills
        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/network/PacketDistributor", "sendToPlayer",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "sendToPlayer",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, LoaderType.FABRIC,
                "PacketDistributor.sendToPlayer -> NetworkShim.sendToPlayer (Fabric)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/network/PacketDistributor", "sendToServer",
                "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "sendToServer",
                "(Ljava/lang/Object;)V",
                null, null, LoaderType.FABRIC,
                "PacketDistributor.sendToServer -> NetworkShim.sendToServer (Fabric)"
        ));

        // 8. Fabric ServerPlayNetworking / ClientPlayNetworking polyfills for Forge/NeoForge
        kb.registerRule(new PolyfillRule(
                "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking", "send",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "sendToPlayer",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, LoaderType.FORGE,
                "Fabric ServerPlayNetworking.send -> NetworkShim.sendToPlayer (Forge)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking", "send",
                "(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
                "com/kyroxova/continuumlib/shims/NetworkShim", "sendToServer",
                "(Ljava/lang/Object;)V",
                null, null, LoaderType.FORGE,
                "Fabric ClientPlayNetworking.send -> NetworkShim.sendToServer (Forge)"
        ));
    }
}
