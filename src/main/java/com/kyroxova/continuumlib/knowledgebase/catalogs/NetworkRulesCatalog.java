package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Networking and Packet Communication.
 * Bridges Forge SimpleChannel across NeoForge and Fabric.
 */
public final class NetworkRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_2 = MCVersion.of("1.20.2");

        // 1. NetworkRegistry.newSimpleChannel -> NetworkShim.createSimpleChannel
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/NetworkRegistry", "newSimpleChannel",
                "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;Ljava/util/function/Predicate;Ljava/util/function/Predicate;)Lnet/minecraftforge/network/simple/SimpleChannel;",
                "com/kyroxova/continuumlib/shims/NetworkShim", "createSimpleChannel",
                "(Ljava/lang/Object;Ljava/util/function/Supplier;Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/lang/Object;",
                v1_20_2, null, null,
                "NetworkRegistry.newSimpleChannel -> NetworkShim.createSimpleChannel"
        ));
    }
}
