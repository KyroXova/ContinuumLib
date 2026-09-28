package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Sounds, Music Discs, and Record Items.
 */
public final class SoundAndMusicRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_4 = MCVersion.of("1.19.4");

        // 1. RecordItem constructor polyfill (1.19.4+ / 1.20+ / 1.21+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/item/RecordItem", "<init>",
                "(ILjava/util/function/Supplier;Lnet/minecraft/world/item/Item$Properties;)V",
                "com/kyroxova/continuumlib/shims/RecordItemShim", "create",
                "(ILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_4, null, null,
                "RecordItem constructor polyfill"
        ));
    }
}
