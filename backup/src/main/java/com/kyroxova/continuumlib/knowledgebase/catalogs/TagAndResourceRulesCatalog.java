package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for BlockTags, ItemTags, and TagKey binding.
 */
public final class TagAndResourceRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_18_2 = MCVersion.of("1.18.2");

        // 1. BlockTags.create(ResourceLocation)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/tags/BlockTags", "create",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;",
                "com/kyroxova/continuumlib/shims/TagShim", "createBlockTag",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_18_2, null, null,
                "BlockTags.create -> TagShim.createBlockTag"
        ));

        // 2. ItemTags.create(ResourceLocation)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/tags/ItemTags", "create",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;",
                "com/kyroxova/continuumlib/shims/TagShim", "createItemTag",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_18_2, null, null,
                "ItemTags.create -> TagShim.createItemTag"
        ));

        // 3. FluidTags.create(ResourceLocation)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/tags/FluidTags", "create",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;",
                "com/kyroxova/continuumlib/shims/TagShim", "createFluidTag",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_18_2, null, null,
                "FluidTags.create -> TagShim.createFluidTag"
        ));

        // 4. EntityTypeTags.create(ResourceLocation)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/tags/EntityTypeTags", "create",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;",
                "com/kyroxova/continuumlib/shims/TagShim", "createEntityTypeTag",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_18_2, null, null,
                "EntityTypeTags.create -> TagShim.createEntityTypeTag"
        ));

        MCVersion v1_21 = MCVersion.of("1.21");

        // 5. ResourceLocation.<init>(String, String) -> fromNamespaceAndPath (1.21+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
                v1_21, null, null,
                "ResourceLocation.<init>(String, String) -> ResourceLocation.fromNamespaceAndPath(String, String)"
        ));

        // 6. ResourceLocation.<init>(String) -> parse (1.21+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "<init>",
                "(Ljava/lang/String;)V",
                "net/minecraft/resources/ResourceLocation", "parse",
                "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
                v1_21, null, null,
                "ResourceLocation.<init>(String) -> ResourceLocation.parse(String)"
        ));

        // 7. ResourceLocation -> Identifier (26.3+)
        MCVersion v26_3 = MCVersion.of("26.3");
        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule(
                "net/minecraft/resources/ResourceLocation",
                "net/minecraft/resources/Identifier",
                v26_3, null, null,
                "ResourceLocation -> Identifier (26.3+)"
        ));

        // 8. SoundEvent.<init>(ResourceLocation) -> createVariableRangeEvent (1.19.3+)
        MCVersion v1_19_3 = MCVersion.of("1.19.3");
        kb.registerRule(new PolyfillRule(
                "net/minecraft/sounds/SoundEvent", "<init>",
                "(Lnet/minecraft/resources/ResourceLocation;)V",
                "net/minecraft/sounds/SoundEvent", "createVariableRangeEvent",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/sounds/SoundEvent;",
                v1_19_3, null, null,
                "SoundEvent.<init>(ResourceLocation) -> SoundEvent.createVariableRangeEvent(ResourceLocation)"
        ));
        kb.registerRule(new PolyfillRule(
                "net/minecraft/sounds/SoundEvent", "<init>",
                "(Ljava/lang/Object;)V",
                "net/minecraft/sounds/SoundEvent", "createVariableRangeEvent",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/sounds/SoundEvent;",
                v1_19_3, null, null,
                "SoundEvent.<init>(Object) -> SoundEvent.createVariableRangeEvent(ResourceLocation)"
        ));
        kb.registerRule(new PolyfillRule(
                "net/minecraft/sounds/SoundEvent", "<init>",
                "(Lnet/minecraft/resources/Identifier;)V",
                "net/minecraft/sounds/SoundEvent", "createVariableRangeEvent",
                "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/sounds/SoundEvent;",
                v1_19_3, null, null,
                "SoundEvent.<init>(Identifier) -> SoundEvent.createVariableRangeEvent(Identifier)"
        ));
    }
}
