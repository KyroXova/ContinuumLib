package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Reflection and Distribution Markers.
 * Bridges ObfuscationReflectionHelper and Dist markers.
 */
public final class ReflectionAndDistRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_4 = MCVersion.of("1.20.4");

        // 1. ObfuscationReflectionHelper polyfills
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/util/ObfuscationReflectionHelper", "setPrivateValue",
                "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/ReflectionHelperShim", "setPrivateValue",
                "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",
                null, null, null,
                "ObfuscationReflectionHelper.setPrivateValue -> ReflectionHelperShim.setPrivateValue"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fml/util/ObfuscationReflectionHelper", "getPrivateValue",
                "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/ReflectionHelperShim", "getPrivateValue",
                "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                null, null, null,
                "ObfuscationReflectionHelper.getPrivateValue -> ReflectionHelperShim.getPrivateValue"
        ));

        // 2. Dist markers for NeoForge
        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/api/distmarker/Dist",
                "net/neoforged/api/distmarker/Dist",
                v1_20_4, null, LoaderType.NEOFORGE,
                "Dist -> NeoForge Dist"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/api/distmarker/OnlyIn",
                "net/neoforged/api/distmarker/OnlyIn",
                v1_20_4, null, LoaderType.NEOFORGE,
                "@OnlyIn -> NeoForge @OnlyIn"
        ));
    }
}
