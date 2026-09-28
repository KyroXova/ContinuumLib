package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.*;

/**
 * Universal Registry and Identifier Transformation Rules across 1.7.9 -> 26.3+.
 */
public final class RegistryRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_3 = MCVersion.of("1.19.3");
        MCVersion v1_20_4 = MCVersion.of("1.20.4");
        MCVersion v1_20_5 = MCVersion.of("1.20.5");

        // 1. ForgeRegistries.BLOCK_ENTITIES -> BLOCK_ENTITY_TYPES (1.19.3+)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraftforge/registries/ForgeRegistries", "BLOCK_ENTITIES",
                "Lnet/minecraftforge/registries/IForgeRegistry;",
                "net/minecraftforge/registries/ForgeRegistries", "BLOCK_ENTITY_TYPES",
                "Lnet/minecraftforge/registries/IForgeRegistry;",
                v1_19_3, null, LoaderType.FORGE,
                "ForgeRegistries.BLOCK_ENTITIES renamed to BLOCK_ENTITY_TYPES in 1.19.3+"
        ));

        // 2. NeoForge DeferredRegister / DeferredHolder / DeferredBlock (1.20.4+)
        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/registries/RegistryObject",
                "net/neoforged/neoforge/registries/DeferredHolder",
                v1_20_4, null, LoaderType.NEOFORGE,
                "RegistryObject -> DeferredHolder"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/registries/DeferredRegister",
                "net/neoforged/neoforge/registries/DeferredRegister",
                v1_20_4, null, LoaderType.NEOFORGE,
                "DeferredRegister -> NeoForge DeferredRegister"
        ));

        // 3. ResourceLocation constructor changes in 1.20.5+ / 1.21+ / 26.x
        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/ResourceLocationShim", "create",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_20_5, null, null,
                "ResourceLocation(namespace, path) constructor polyfill for 1.20.5+ / 1.21+ / 26.x"
        ));

        // 4. Fabric Registry Bridges
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/registries/DeferredRegister", "register",
                "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;",
                "com/kyroxova/continuumlib/shims/RegistryShim", "registerFabric",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "Bridge Forge DeferredRegister to Fabric Registry"
        ));
    }
}
