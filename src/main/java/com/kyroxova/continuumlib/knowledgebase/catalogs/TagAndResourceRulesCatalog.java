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
    }
}
