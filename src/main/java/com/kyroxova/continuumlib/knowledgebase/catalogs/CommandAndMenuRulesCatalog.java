package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Commands, Menus, Containers, and SoundEvents.
 */
public final class CommandAndMenuRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_19_3 = MCVersion.of("1.19.3");
        MCVersion v1_20_4 = MCVersion.of("1.20.4");

        // 1. SoundEvent constructor polyfill (1.19.3+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/sounds/SoundEvent", "<init>",
                "(Lnet/minecraft/resources/ResourceLocation;)V",
                "com/kyroxova/continuumlib/shims/SoundEventShim", "create",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_19_3, null, null,
                "SoundEvent(ResourceLocation) -> SoundEventShim.create"
        ));

        // 2. IForgeMenuType.create -> MenuTypeShim.createMenuType
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/common/extensions/IForgeMenuType", "create",
                "(Lnet/minecraftforge/network/IContainerFactory;)Lnet/minecraft/world/inventory/MenuType;",
                "com/kyroxova/continuumlib/shims/MenuTypeShim", "createMenuType",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_4, null, LoaderType.NEOFORGE,
                "IForgeMenuType.create -> MenuTypeShim.createMenuType"
        ));

        // 3. RegisterCommandsEvent NeoForge redirect
        kb.registerRule(new ClassRedirectRule(
                "net/minecraftforge/event/RegisterCommandsEvent",
                "net/neoforged/neoforge/event/RegisterCommandsEvent",
                v1_20_4, null, LoaderType.NEOFORGE,
                "RegisterCommandsEvent -> NeoForge RegisterCommandsEvent"
        ));
    }
}
