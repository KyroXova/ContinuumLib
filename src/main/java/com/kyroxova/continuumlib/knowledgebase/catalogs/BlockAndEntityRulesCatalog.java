package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Blocks, Properties, BlockEntities, and Entities.
 */
public final class BlockAndEntityRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_20_0 = MCVersion.of("1.20");

        // 1. Material Removal Polyfills (1.20+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/state/BlockBehaviour$Properties", "of",
                "(Lnet/minecraft/world/level/material/Material;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;",
                "com/kyroxova/continuumlib/shims/BlockPropertiesShim", "ofLegacyMaterial",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_0, null, null,
                "Properties.of(Material) -> BlockPropertiesShim.ofLegacyMaterial"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/state/BlockBehaviour$Properties", "of",
                "(Lnet/minecraft/world/level/material/Material;Lnet/minecraft/world/level/material/MaterialColor;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;",
                "com/kyroxova/continuumlib/shims/BlockPropertiesShim", "ofLegacyMaterialAndColor",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_0, null, null,
                "Properties.of(Material, MaterialColor) -> BlockPropertiesShim.ofLegacyMaterialAndColor"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/state/BlockBehaviour$Properties", "of",
                "(Lnet/minecraft/world/level/material/Material;Ljava/util/function/Function;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;",
                "com/kyroxova/continuumlib/shims/BlockPropertiesShim", "ofLegacyMaterialAndFunction",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_0, null, null,
                "Properties.of(Material, Function) -> BlockPropertiesShim.ofLegacyMaterialAndFunction"
        ));
    }
}
