package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

public final class WorldAndDimensionRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_17_0 = MCVersion.of("1.17");
        MCVersion v1_16_5 = MCVersion.of("1.16.5");

        // 1. World <-> Level class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/World",
                "net/minecraft/world/level/Level",
                v1_17_0, null, null,
                "World -> Level (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/level/Level",
                "net/minecraft/world/World",
                null, v1_16_5, null,
                "Level -> World (<= 1.16.5)"
        ));

        // 2. ServerWorld <-> ServerLevel class redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/server/ServerWorld",
                "net/minecraft/server/level/ServerLevel",
                v1_17_0, null, null,
                "ServerWorld -> ServerLevel (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/server/level/ServerLevel",
                "net/minecraft/world/server/ServerWorld",
                null, v1_16_5, null,
                "ServerLevel -> ServerWorld (<= 1.16.5)"
        ));

        // 3. getBlockEntity <-> getTileEntity polyfill rules
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "getTileEntity",
                "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                "com/kyroxova/continuumlib/shims/WorldShim", "getBlockEntity",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_17_0, null, null,
                "World.getTileEntity -> WorldShim.getBlockEntity (1.17+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/Level", "getBlockEntity",
                "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                "com/kyroxova/continuumlib/shims/WorldShim", "getBlockEntity",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_16_5, null,
                "Level.getBlockEntity -> WorldShim.getBlockEntity (<= 1.16.5)"
        ));

        // 4. isClientSide <-> isRemote polyfill rules
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/World", "isRemote",
                "()Z",
                "com/kyroxova/continuumlib/shims/WorldShim", "isClientSide",
                "(Ljava/lang/Object;)Z",
                v1_17_0, null, null,
                "World.isRemote -> WorldShim.isClientSide (1.17+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/Level", "isClientSide",
                "()Z",
                "com/kyroxova/continuumlib/shims/WorldShim", "isClientSide",
                "(Ljava/lang/Object;)Z",
                null, v1_16_5, null,
                "Level.isClientSide -> WorldShim.isClientSide (<= 1.16.5)"
        ));

        // 5. MinecraftServer.overworld polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/server/MinecraftServer", "overworld",
                "()Lnet/minecraft/server/level/ServerLevel;",
                "com/kyroxova/continuumlib/shims/WorldShim", "getOverworld",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_16_5, null,
                "MinecraftServer.overworld -> WorldShim.getOverworld (<= 1.16.5)"
        ));

        // 6. WorldSavedData <-> SavedData class redirects
        MCVersion v1_14_0 = MCVersion.of("1.14");
        MCVersion v1_12_2 = MCVersion.of("1.12.2");

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/storage/WorldSavedData",
                "net/minecraft/world/level/saveddata/SavedData",
                v1_14_0, null, null,
                "WorldSavedData -> SavedData (1.14+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/world/level/saveddata/SavedData",
                "net/minecraft/world/storage/WorldSavedData",
                null, v1_12_2, null,
                "SavedData -> WorldSavedData (<= 1.12.2)"
        ));

        // 7. DimensionDataStorage.computeIfAbsent polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/storage/DimensionDataStorage", "computeIfAbsent",
                null,
                "com/kyroxova/continuumlib/shims/SavedDataShim", "getOrCreate",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "DimensionDataStorage.computeIfAbsent -> SavedDataShim.getOrCreate"
        ));

        // 8. SavedData.setDirty polyfill
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/saveddata/SavedData", "setDirty",
                "()V",
                "com/kyroxova/continuumlib/shims/SavedDataShim", "setDirty",
                "(Ljava/lang/Object;)V",
                null, null, null,
                "SavedData.setDirty -> SavedDataShim.setDirty"
        ));
    }
}
