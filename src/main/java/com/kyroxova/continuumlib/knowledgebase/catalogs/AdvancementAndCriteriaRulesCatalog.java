package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Advancements, Criteria Triggers, and Rewards.
 * Bridges:
 * 1. Advancement (<= 1.20.1) vs AdvancementHolder (>= 1.20.2).
 * 2. Advancement.getId() vs AdvancementHolder.id().
 * 3. AdvancementHolder.value() -> Advancement.
 * 4. 26.3+ Identifier evolution for Advancements.
 */
public final class AdvancementAndCriteriaRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_2 = MCVersion.of("1.20.2");
        MCVersion v1_20_1 = MCVersion.of("1.20.1");

        // 1. AdvancementHolder -> Advancement fallback on <= 1.20.1
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/advancements/AdvancementHolder",
                "net/minecraft/advancements/Advancement",
                null, v1_20_1, null,
                "AdvancementHolder -> Advancement (<= 1.20.1)"
        ));

        // 2. Advancement.getId() polyfill for modern targets where AdvancementHolder is expected or id signature shifts
        kb.registerRule(new PolyfillRule(
                "net/minecraft/advancements/Advancement", "getId",
                "()Lnet/minecraft/resources/ResourceLocation;",
                "com/kyroxova/continuumlib/shims/AdvancementShim", "getId",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_2, null, null,
                "Advancement.getId() -> AdvancementShim.getId (1.20.2+)"
        ));

        // 3. AdvancementHolder.id() polyfill for pre-1.20.2 targets
        kb.registerRule(new PolyfillRule(
                "net/minecraft/advancements/AdvancementHolder", "id",
                "()Lnet/minecraft/resources/ResourceLocation;",
                "com/kyroxova/continuumlib/shims/AdvancementShim", "getId",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_1, null,
                "AdvancementHolder.id() -> AdvancementShim.getId (<= 1.20.1)"
        ));

        // 4. AdvancementHolder.value() polyfill for pre-1.20.2 targets
        kb.registerRule(new PolyfillRule(
                "net/minecraft/advancements/AdvancementHolder", "value",
                "()Lnet/minecraft/advancements/Advancement;",
                "com/kyroxova/continuumlib/shims/AdvancementShim", "getAdvancement",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_1, null,
                "AdvancementHolder.value() -> AdvancementShim.getAdvancement (<= 1.20.1)"
        ));

        // 5. Universal AdvancementShim.createHolder polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/advancements/AdvancementHolder", "<init>",
                "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/advancements/Advancement;)V",
                "com/kyroxova/continuumlib/shims/AdvancementShim", "createHolder",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_1, null,
                "AdvancementHolder.<init> -> AdvancementShim.createHolder (<= 1.20.1)"
        ));

        // 6. CriteriaTriggers package relocation (26.3+)
        MCVersion v26_3 = MCVersion.of("26.3");
        MCVersion v26_2 = MCVersion.of("26.2");

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/advancements/CriteriaTriggers",
                "net/minecraft/advancements/triggers/CriteriaTriggers",
                v26_3, null, null,
                "CriteriaTriggers -> triggers/CriteriaTriggers (26.3+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/advancements/triggers/CriteriaTriggers",
                "net/minecraft/advancements/CriteriaTriggers",
                null, v26_2, null,
                "triggers/CriteriaTriggers -> CriteriaTriggers (<= 26.2)"
        ));
    }
}
