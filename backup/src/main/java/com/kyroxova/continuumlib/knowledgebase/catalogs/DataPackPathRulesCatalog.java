package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Datapack and Resource Location path plural/singular transformations (1.20.6 <-> 1.21+).
 */
public final class DataPackPathRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_21_0 = MCVersion.of("1.21");

        // 1. ResourceLocation getPath() -> getNormalizedPath on 1.21+
        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "getPath",
                "()Ljava/lang/String;",
                "com/kyroxova/continuumlib/shims/ResourceLocationShim", "getNormalizedPath",
                "(Ljava/lang/Object;)Ljava/lang/String;",
                v1_21_0, null, null,
                "ResourceLocation.getPath() -> ResourceLocationShim.getNormalizedPath (1.21+)"
        ));

        // 2. ResourceLocation getPath() -> getDenormalizedPath for targets <= 1.20.6
        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "getPath",
                "()Ljava/lang/String;",
                "com/kyroxova/continuumlib/shims/ResourceLocationShim", "getDenormalizedPath",
                "(Ljava/lang/Object;)Ljava/lang/String;",
                null, MCVersion.of("1.20.6"), null,
                "ResourceLocation.getPath() -> ResourceLocationShim.getDenormalizedPath (<= 1.20.6)"
        ));
    }
}
