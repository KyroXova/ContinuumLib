package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Fluids and Fluid Attributes (1.18.2 <-> 1.19.2+ / 1.20+ / NeoForge).
 */
public final class FluidAndAttributesRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_2 = MCVersion.of("1.19.2");
        MCVersion v1_20_4 = MCVersion.of("1.20.4");

        // 1. FluidAttributes.builder -> FluidShim.createFluidAttributes
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fluids/FluidAttributes", "builder",
                "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraftforge/fluids/FluidAttributes$Builder;",
                "com/kyroxova/continuumlib/shims/FluidShim", "createFluidAttributes",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_2, null, null,
                "FluidAttributes.builder -> FluidShim.createFluidAttributes"
        ));

        // 2. FluidAttributes property query polyfills
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fluids/FluidAttributes", "getDensity",
                "()I",
                "com/kyroxova/continuumlib/shims/FluidShim", "getDensity",
                "(Ljava/lang/Object;)I",
                v1_19_2, null, null,
                "FluidAttributes.getDensity -> FluidShim.getDensity"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fluids/FluidAttributes", "getViscosity",
                "()I",
                "com/kyroxova/continuumlib/shims/FluidShim", "getViscosity",
                "(Ljava/lang/Object;)I",
                v1_19_2, null, null,
                "FluidAttributes.getViscosity -> FluidShim.getViscosity"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fluids/FluidAttributes", "getTemperature",
                "()I",
                "com/kyroxova/continuumlib/shims/FluidShim", "getTemperature",
                "(Ljava/lang/Object;)I",
                v1_19_2, null, null,
                "FluidAttributes.getTemperature -> FluidShim.getTemperature"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/fluids/FluidAttributes", "isGaseous",
                "()Z",
                "com/kyroxova/continuumlib/shims/FluidShim", "isGaseous",
                "(Ljava/lang/Object;)Z",
                v1_19_2, null, null,
                "FluidAttributes.isGaseous -> FluidShim.isGaseous"
        ));

        // 3. Forge FluidType -> NeoForge FluidType class redirect
        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/fluids/FluidType",
                "net/neoforged/neoforge/fluids/FluidType",
                v1_20_4, null, LoaderType.NEOFORGE,
                "FluidType -> NeoForge FluidType"
        ));
    }
}
