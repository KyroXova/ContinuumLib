package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.FieldRedirectRule;

/**
 * Universal Rules for Mod Lifecycle and Events.
 * Bridges Forge/NeoForge event buses and annotations.
 */
public final class LifecycleAndEventRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_4 = MCVersion.of("1.20.4");

        // 1. MinecraftForge.EVENT_BUS -> NeoForge.EVENT_BUS (NeoForge)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraftforge/common/MinecraftForge", "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;",
                "net/neoforged/neoforge/common/NeoForge", "EVENT_BUS",
                "Lnet/neoforged/bus/api/IEventBus;",
                v1_20_4, null, LoaderType.NEOFORGE,
                "MinecraftForge.EVENT_BUS -> NeoForge.EVENT_BUS"
        ));

        // 2. Class redirects for NeoForge
        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/fml/common/Mod",
                "net/neoforged/fml/common/Mod",
                v1_20_4, null, LoaderType.NEOFORGE,
                "@Mod -> NeoForge @Mod"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/eventbus/api/SubscribeEvent",
                "net/neoforged/bus/api/SubscribeEvent",
                v1_20_4, null, LoaderType.NEOFORGE,
                "@SubscribeEvent -> NeoForge @SubscribeEvent"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/eventbus/api/IEventBus",
                "net/neoforged/bus/api/IEventBus",
                v1_20_4, null, LoaderType.NEOFORGE,
                "IEventBus -> NeoForge IEventBus"
        ));
    }
}
