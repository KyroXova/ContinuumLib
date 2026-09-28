package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Fluids and Fluid Attributes.
 */
public final class FluidAndAttributesRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_2 = MCVersion.of("1.19.2");

        // 1. FluidAttributes.builder -> FluidShim.createFluidAttributes
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fluids/FluidAttributes", "builder",
                "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraftforge/fluids/FluidAttributes$Builder;",
                "com/kyroxova/continuumlib/shims/FluidShim", "createFluidAttributes",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_2, null, null,
                "FluidAttributes.builder -> FluidShim.createFluidAttributes"
        ));
    }
}
