package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Text and Chat Components.
 * Bridges TranslatableComponent and TextComponent constructors to Component.translatable / literal.
 */
public final class TextAndChatRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_0 = MCVersion.of("1.19");

        // 1. new TranslatableComponent(String)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/network/chat/TranslatableComponent", "<init>",
                "(Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/ComponentShim", "translatable",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                v1_19_0, null, null,
                "new TranslatableComponent(key) -> ComponentShim.translatable"
        ));

        // 2. new TextComponent(String)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/network/chat/TextComponent", "<init>",
                "(Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/ComponentShim", "literal",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                v1_19_0, null, null,
                "new TextComponent(text) -> ComponentShim.literal"
        ));
    }
}
