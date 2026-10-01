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

        MCVersion v1_17_0 = MCVersion.of("1.17");
        MCVersion v1_16_5 = MCVersion.of("1.16.5");

        // 4. broadcastChanges <-> detectAndSendChanges
        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule(
                "net/minecraft/world/inventory/AbstractContainerMenu", "detectAndSendChanges",
                "()V",
                "net/minecraft/world/inventory/AbstractContainerMenu", "broadcastChanges",
                "()V",
                -1,
                v1_17_0, null, null,
                "detectAndSendChanges -> broadcastChanges (1.17+)"
        ));

        kb.registerRule(new com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule(
                "net/minecraft/world/inventory/AbstractContainerMenu", "broadcastChanges",
                "()V",
                "net/minecraft/world/inventory/AbstractContainerMenu", "detectAndSendChanges",
                "()V",
                -1,
                null, v1_16_5, null,
                "broadcastChanges -> detectAndSendChanges (<= 1.16.5)"
        ));

        // 5. NetworkHooks.openScreen polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraftforge/network/NetworkHooks", "openScreen",
                "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)V",
                "com/kyroxova/continuumlib/shims/MenuTypeShim", "openMenu",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, null,
                "NetworkHooks.openScreen -> MenuTypeShim.openMenu"
        ));

        // 6. CommandSource <-> CommandSourceStack Class Redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/command/CommandSource",
                "net/minecraft/commands/CommandSourceStack",
                v1_17_0, null, null,
                "CommandSource -> CommandSourceStack (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/commands/CommandSourceStack",
                "net/minecraft/command/CommandSource",
                null, v1_16_5, null,
                "CommandSourceStack -> CommandSource (<= 1.16.5)"
        ));

        MCVersion v1_20_0 = MCVersion.of("1.20");
        MCVersion v1_19_4 = MCVersion.of("1.19.4");

        // 7. CommandSourceStack.sendSuccess polyfills (Component vs Supplier<Component>)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/commands/CommandSourceStack", "sendSuccess",
                "(Lnet/minecraft/network/chat/Component;Z)V",
                "com/kyroxova/continuumlib/shims/CommandShim", "sendSuccess",
                "(Ljava/lang/Object;Ljava/lang/Object;Z)V",
                v1_20_0, null, null,
                "CommandSourceStack.sendSuccess(Component, boolean) -> CommandShim.sendSuccess (1.20+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/commands/CommandSourceStack", "sendSuccess",
                "(Ljava/util/function/Supplier;Z)V",
                "com/kyroxova/continuumlib/shims/CommandShim", "sendSuccess",
                "(Ljava/lang/Object;Ljava/lang/Object;Z)V",
                null, v1_19_4, null,
                "CommandSourceStack.sendSuccess(Supplier, boolean) -> CommandShim.sendSuccess (<= 1.19.4)"
        ));

        // 8. CommandSourceStack.sendFailure polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/commands/CommandSourceStack", "sendFailure",
                "(Lnet/minecraft/network/chat/Component;)V",
                "com/kyroxova/continuumlib/shims/CommandShim", "sendFailure",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, null,
                "CommandSourceStack.sendFailure -> CommandShim.sendFailure"
        ));
    }
}
