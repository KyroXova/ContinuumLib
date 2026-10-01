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
        MCVersion v1_21_0 = MCVersion.of("1.21");

        // 1. Registry -> BuiltInRegistries field redirects (1.19.3+)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "BLOCK", null,
                "net/minecraft/core/registries/BuiltInRegistries", "BLOCK", null,
                v1_19_3, null, null,
                "Registry.BLOCK -> BuiltInRegistries.BLOCK (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "ITEM", null,
                "net/minecraft/core/registries/BuiltInRegistries", "ITEM", null,
                v1_19_3, null, null,
                "Registry.ITEM -> BuiltInRegistries.ITEM (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "FLUID", null,
                "net/minecraft/core/registries/BuiltInRegistries", "FLUID", null,
                v1_19_3, null, null,
                "Registry.FLUID -> BuiltInRegistries.FLUID (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "ENTITY_TYPE", null,
                "net/minecraft/core/registries/BuiltInRegistries", "ENTITY_TYPE", null,
                v1_19_3, null, null,
                "Registry.ENTITY_TYPE -> BuiltInRegistries.ENTITY_TYPE (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "BLOCK_ENTITY_TYPE", null,
                "net/minecraft/core/registries/BuiltInRegistries", "BLOCK_ENTITY_TYPE", null,
                v1_19_3, null, null,
                "Registry.BLOCK_ENTITY_TYPE -> BuiltInRegistries.BLOCK_ENTITY_TYPE (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "SOUND_EVENT", null,
                "net/minecraft/core/registries/BuiltInRegistries", "SOUND_EVENT", null,
                v1_19_3, null, null,
                "Registry.SOUND_EVENT -> BuiltInRegistries.SOUND_EVENT (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "MOB_EFFECT", null,
                "net/minecraft/core/registries/BuiltInRegistries", "MOB_EFFECT", null,
                v1_19_3, null, null,
                "Registry.MOB_EFFECT -> BuiltInRegistries.MOB_EFFECT (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "ENCHANTMENT", null,
                "net/minecraft/core/registries/BuiltInRegistries", "ENCHANTMENT", null,
                v1_19_3, null, null,
                "Registry.ENCHANTMENT -> BuiltInRegistries.ENCHANTMENT (1.19.3+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/core/Registry", "ATTRIBUTE", null,
                "net/minecraft/core/registries/BuiltInRegistries", "ATTRIBUTE", null,
                v1_19_3, null, null,
                "Registry.ATTRIBUTE -> BuiltInRegistries.ATTRIBUTE (1.19.3+)"
        ));

        // 2. ForgeRegistries redirects for NeoForge are handled uniformly by ForgeRegistriesShim


        // 3. ForgeRegistries.BLOCK_ENTITIES -> BLOCK_ENTITY_TYPES (1.19.3+)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraftforge/registries/ForgeRegistries", "BLOCK_ENTITIES",
                "Lnet/minecraftforge/registries/IForgeRegistry;",
                "net/minecraftforge/registries/ForgeRegistries", "BLOCK_ENTITY_TYPES",
                "Lnet/minecraftforge/registries/IForgeRegistry;",
                v1_19_3, null, LoaderType.FORGE,
                "ForgeRegistries.BLOCK_ENTITIES renamed to BLOCK_ENTITY_TYPES in 1.19.3+"
        ));

        // 4. NeoForge DeferredRegister / DeferredHolder (1.20.4+)
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

        // 5. Legacy RegistryDelegate redirects
        MCVersion v1_20_0 = MCVersion.of("1.20");
        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/registries/RegistryDelegate",
                "net/minecraftforge/registries/RegistryObject",
                v1_19_3, null, LoaderType.FORGE,
                "RegistryDelegate -> RegistryObject (Forge)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/registries/RegistryDelegate",
                "net/neoforged/neoforge/registries/DeferredHolder",
                v1_20_0, null, LoaderType.NEOFORGE,
                "RegistryDelegate -> DeferredHolder (NeoForge)"
        ));

        // 6. ResourceLocation constructor polyfills for 1.20.5 - 1.20.6
        MCVersion v1_20_6 = MCVersion.of("1.20.6");
        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/ResourceLocationShim", "create",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                v1_20_5, v1_20_6, null,
                "ResourceLocation(namespace, path) constructor polyfill for 1.20.5 - 1.20.6"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "<init>",
                "(Ljava/lang/String;)V",
                "com/kyroxova/continuumlib/shims/ResourceLocationShim", "parse",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                v1_20_5, v1_20_6, null,
                "ResourceLocation(location) constructor polyfill for 1.20.5 - 1.20.6"
        ));

        // 7. ResourceLocation parse(String) polyfill for <= 1.20.4 targets
        kb.registerRule(new PolyfillRule(
                "net/minecraft/resources/ResourceLocation", "parse",
                "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
                "com/kyroxova/continuumlib/shims/ResourceLocationShim", "parse",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                null, MCVersion.of("1.20.4"), null,
                "ResourceLocation.parse(String) fallback for <= 1.20.4"
        ));

        // 8. Fabric Registry Bridges
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/registries/DeferredRegister", "register",
                "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;",
                "com/kyroxova/continuumlib/shims/RegistryShim", "registerFabric",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "Bridge Forge DeferredRegister to Fabric Registry"
        ));

        // 9. Holder lookup polyfills across versions
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/registries/RegistryObject", "get",
                "()Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/RegistryShim", "getValue",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "RegistryObject.get() -> RegistryShim.getValue"
        ));

        kb.registerRule(new PolyfillRule(
                "net/neoforged/neoforge/registries/DeferredHolder", "get",
                "()Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/RegistryShim", "getValue",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, LoaderType.FABRIC,
                "DeferredHolder.get() -> RegistryShim.getValue"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/core/Holder", "value",
                "()Ljava/lang/Object;",
                "com/kyroxova/continuumlib/shims/RegistryShim", "getValue",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_19_3, null,
                "Holder.value() -> RegistryShim.getValue (pre-1.19.3)"
        ));
    }
}
