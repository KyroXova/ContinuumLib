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

        MCVersion v1_19_3 = MCVersion.of("1.19.3");

        // 2. SoundEvent.<init>(ResourceLocation) -> SoundEventShim.create (1.19.3+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/sounds/SoundEvent", "<init>",
                "(Lnet/minecraft/resources/ResourceLocation;)V",
                "com/kyroxova/continuumlib/shims/SoundEventShim", "create",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_3, null, null,
                "SoundEvent.<init>(ResourceLocation) -> SoundEventShim.create (1.19.3+)"
        ));

        // 3. SoundEvent.createVariableRangeEvent(ResourceLocation) -> SoundEventShim.create (<= 1.19.2)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/sounds/SoundEvent", "createVariableRangeEvent",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/sounds/SoundEvent;",
                "com/kyroxova/continuumlib/shims/SoundEventShim", "createVariableRangeEvent",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, MCVersion.of("1.19.2"), null,
                "SoundEvent.createVariableRangeEvent -> SoundEventShim.createVariableRangeEvent (<= 1.19.2)"
        ));

        // 4. SoundEvent.createFixedRangeEvent(ResourceLocation, float) -> SoundEventShim.createFixedRangeEvent (<= 1.19.2)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/sounds/SoundEvent", "createFixedRangeEvent",
                "(Lnet/minecraft/resources/ResourceLocation;F)Lnet/minecraft/sounds/SoundEvent;",
                "com/kyroxova/continuumlib/shims/SoundEventShim", "createFixedRangeEvent",
                "(Ljava/lang/Object;F)Ljava/lang/Object;",
                null, MCVersion.of("1.19.2"), null,
                "SoundEvent.createFixedRangeEvent -> SoundEventShim.createFixedRangeEvent (<= 1.19.2)"
        ));

        // 5. SoundEvent.getLocation() -> SoundEvent.location() (26.3+)
        MCVersion v26_3 = MCVersion.of("26.3");
        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule(
                "net/minecraft/sounds/SoundEvent", "getLocation",
                "()Lnet/minecraft/resources/ResourceLocation;",
                "net/minecraft/sounds/SoundEvent", "location",
                "()Lnet/minecraft/resources/Identifier;",
                -1,
                v26_3, null, null,
                "SoundEvent.getLocation() -> SoundEvent.location() (26.3+)"
        ));
    }
}
