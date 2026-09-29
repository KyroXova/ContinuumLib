package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Fluids, Fluid Attributes, and Entity Attributes across 1.7.9 -> 26.3+.
 */
public final class FluidAndAttributesRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_2 = MCVersion.of("1.19.2");
        MCVersion v1_20_4 = MCVersion.of("1.20.4");
        MCVersion v1_20_5 = MCVersion.of("1.20.5");

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

        // 4. EntityAttributeCreationEvent vs Fabric FabricDefaultAttributeRegistry
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/event/entity/EntityAttributeCreationEvent", "put",
                "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;)V",
                "com/kyroxova/continuumlib/shims/AttributeModifierShim", "putDefaultAttributes",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, LoaderType.FABRIC,
                "EntityAttributeCreationEvent.put -> AttributeModifierShim.putDefaultAttributes (Fabric)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/fabricmc/fabric/api/object/builder/v1/entity/FabricDefaultAttributeRegistry", "register",
                "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;",
                "com/kyroxova/continuumlib/shims/AttributeModifierShim", "registerDefaultAttributes",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FORGE,
                "FabricDefaultAttributeRegistry.register -> AttributeModifierShim.registerDefaultAttributes (Forge)"
        ));

        // 5. LivingEntity.getAttribute Attribute vs Holder<Attribute> lookup
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/LivingEntity", "getAttribute",
                "(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;",
                "com/kyroxova/continuumlib/shims/AttributeModifierShim", "getAttribute",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_5, null, null,
                "LivingEntity.getAttribute(Attribute) -> AttributeModifierShim.getAttribute (1.20.5+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/LivingEntity", "getAttribute",
                "(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;",
                "com/kyroxova/continuumlib/shims/AttributeModifierShim", "getAttribute",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_4, null,
                "LivingEntity.getAttribute(Holder) -> AttributeModifierShim.getAttribute (<= 1.20.4)"
        ));

        // 6. AttributeModifier modern constructor polyfill on <= 1.20.4
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/ai/attributes/AttributeModifier", "<init>",
                "(Lnet/minecraft/resources/ResourceLocation;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V",
                "com/kyroxova/continuumlib/shims/AttributeModifierShim", "createModifier",
                "(Ljava/lang/Object;DLjava/lang/Object;)Ljava/lang/Object;",
                null, v1_20_4, null,
                "AttributeModifier(ResourceLocation, double, Operation) polyfill for <= 1.20.4"
        ));

        // 7. Level.getFluidState polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/Level", "getFluidState",
                "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
                "com/kyroxova/continuumlib/shims/FluidShim", "getFluidState",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "Level.getFluidState -> FluidShim.getFluidState"
        ));

        // 8. FluidState.isSource polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/material/FluidState", "isSource",
                "()Z",
                "com/kyroxova/continuumlib/shims/FluidShim", "isSource",
                "(Ljava/lang/Object;)Z",
                null, null, null,
                "FluidState.isSource -> FluidShim.isSource"
        ));
    }
}
