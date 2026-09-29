package com.kyroxova.continuumlib.knowledgebase.catalogs;

import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.FieldRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;

/**
 * Universal Rules for Blocks, Properties, BlockEntities, and Entities across 1.7.9 -> 26.3+.
 */
public final class BlockAndEntityRulesCatalog {

    public static void register(ApiKnowledgeBase kb) {
        MCVersion v1_13_0 = MCVersion.of("1.13");
        MCVersion v1_14_0 = MCVersion.of("1.14");
        MCVersion v1_19_0 = MCVersion.of("1.19");
        MCVersion v1_20_0 = MCVersion.of("1.20");
        MCVersion v1_20_5 = MCVersion.of("1.20.5");

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

        // 2. Block.use(...) Invocations (1.20.5+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "use",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
                "com/kyroxova/continuumlib/shims/BlockInteractionShim", "useBlock",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_5, null, null,
                "Block.use -> BlockInteractionShim.useBlock (1.20.5+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/state/BlockBehaviour", "use",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
                "com/kyroxova/continuumlib/shims/BlockInteractionShim", "useBlock",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_20_5, null, null,
                "BlockBehaviour.use -> BlockInteractionShim.useBlock (1.20.5+)"
        ));

        // 3. BlockEntity save/load Additional (1.20.5+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/entity/BlockEntity", "saveAdditional",
                "(Lnet/minecraft/nbt/CompoundTag;)V",
                "com/kyroxova/continuumlib/shims/BlockEntityShim", "save",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_5, null, null,
                "BlockEntity.saveAdditional(CompoundTag) -> BlockEntityShim.save (1.20.5+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/entity/BlockEntity", "load",
                "(Lnet/minecraft/nbt/CompoundTag;)V",
                "com/kyroxova/continuumlib/shims/BlockEntityShim", "load",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_5, null, null,
                "BlockEntity.load(CompoundTag) -> BlockEntityShim.load (1.20.5+)"
        ));

        // 4. RandomSource vs Random tick invocation polyfills (1.19+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "animateTick",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V",
                "com/kyroxova/continuumlib/shims/RandomShim", "animateTick",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_19_0, null, null,
                "Block.animateTick(..., Random) -> RandomShim.animateTick (1.19+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/level/block/Block", "randomTick",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V",
                "com/kyroxova/continuumlib/shims/RandomShim", "randomTick",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_19_0, null, null,
                "Block.randomTick(..., Random) -> RandomShim.randomTick (1.19+)"
        ));

        // 5. AttributeModifier Operation Enums (1.20.5+)
        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation", "ADDITION",
                "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;",
                "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation", "ADD_VALUE",
                "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;",
                v1_20_5, null, null,
                "AttributeModifier$Operation.ADDITION -> ADD_VALUE (1.20.5+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation", "MULTIPLY_BASE",
                "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;",
                "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation", "ADD_MULTIPLIED_BASE",
                "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;",
                v1_20_5, null, null,
                "AttributeModifier$Operation.MULTIPLY_BASE -> ADD_MULTIPLIED_BASE (1.20.5+)"
        ));

        kb.registerRule(new FieldRedirectRule(
                "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation", "MULTIPLY_TOTAL",
                "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;",
                "net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation", "ADD_MULTIPLIED_TOTAL",
                "Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;",
                v1_20_5, null, null,
                "AttributeModifier$Operation.MULTIPLY_TOTAL -> ADD_MULTIPLIED_TOTAL (1.20.5+)"
        ));

        // 6. AttributeModifier Constructor Polyfill (1.20.5+)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/ai/attributes/AttributeModifier", "<init>",
                "(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V",
                "com/kyroxova/continuumlib/shims/AttributeModifierShim", "createModifier",
                "(Ljava/util/UUID;Ljava/lang/String;DLjava/lang/Object;)Ljava/lang/Object;",
                v1_20_5, null, null,
                "AttributeModifier(UUID, name, amount, operation) -> AttributeModifierShim.createModifier (1.20.5+)"
        ));

        // 7. Legacy TileEntity -> BlockEntity class redirect (1.14+)
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/tileentity/TileEntity",
                "net/minecraft/world/level/block/entity/BlockEntity",
                v1_14_0, null, null,
                "TileEntity -> BlockEntity (1.14+)"
        ));

        // 8. Entity getLevel() method redirect for 1.20+
        kb.registerRule(new MethodRedirectRule(
                "net/minecraft/world/entity/Entity", "getLevel",
                "()Lnet/minecraft/world/level/Level;",
                "net/minecraft/world/entity/Entity", "level",
                "()Lnet/minecraft/world/level/Level;",
                -1,
                v1_20_0, null, null,
                "Entity.getLevel() -> Entity.level() (1.20+)"
        ));

        // 9. Entity level() reverse redirect for <= 1.19.4
        kb.registerRule(new MethodRedirectRule(
                "net/minecraft/world/entity/Entity", "level",
                "()Lnet/minecraft/world/level/Level;",
                "net/minecraft/world/entity/Entity", "getLevel",
                "()Lnet/minecraft/world/level/Level;",
                -1,
                null, MCVersion.of("1.19.4"), null,
                "Entity.level() -> Entity.getLevel() (<= 1.19.4)"
        ));

        // 10. Entity onGround() vs isOnGround()
        kb.registerRule(new MethodRedirectRule(
                "net/minecraft/world/entity/Entity", "isOnGround",
                "()Z",
                "net/minecraft/world/entity/Entity", "onGround",
                "()Z",
                -1,
                v1_20_0, null, null,
                "Entity.isOnGround() -> Entity.onGround() (1.20+)"
        ));

        kb.registerRule(new MethodRedirectRule(
                "net/minecraft/world/entity/Entity", "onGround",
                "()Z",
                "net/minecraft/world/entity/Entity", "isOnGround",
                "()Z",
                -1,
                null, MCVersion.of("1.19.4"), null,
                "Entity.onGround() -> Entity.isOnGround() (<= 1.19.4)"
        ));

        // 11. Entity.getCommandSenderWorld() -> level() (1.20+)
        kb.registerRule(new MethodRedirectRule(
                "net/minecraft/world/entity/Entity", "getCommandSenderWorld",
                "()Lnet/minecraft/world/level/Level;",
                "net/minecraft/world/entity/Entity", "level",
                "()Lnet/minecraft/world/level/Level;",
                -1,
                v1_20_0, null, null,
                "Entity.getCommandSenderWorld() -> Entity.level() (1.20+)"
        ));

        // 12. LivingEntity attribute queries
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/LivingEntity", "getAttributeValue",
                "(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D",
                "com/kyroxova/continuumlib/shims/LivingEntityShim", "getAttributeValue",
                "(Ljava/lang/Object;Ljava/lang/Object;)D",
                null, null, null,
                "LivingEntity.getAttributeValue -> LivingEntityShim.getAttributeValue"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/LivingEntity", "getAttribute",
                "(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;",
                "com/kyroxova/continuumlib/shims/LivingEntityShim", "getAttribute",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "LivingEntity.getAttribute -> LivingEntityShim.getAttribute"
        ));

        // 13. LivingEntity equipment slot queries
        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
                "com/kyroxova/continuumlib/shims/LivingEntityShim", "getItemBySlot",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                null, null, null,
                "LivingEntity.getItemBySlot -> LivingEntityShim.getItemBySlot"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/world/entity/LivingEntity", "setItemSlot",
                "(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V",
                "com/kyroxova/continuumlib/shims/LivingEntityShim", "setItemSlot",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                null, null, null,
                "LivingEntity.setItemSlot -> LivingEntityShim.setItemSlot"
        ));

        MCVersion v1_17_0 = MCVersion.of("1.17");
        MCVersion v1_16_5 = MCVersion.of("1.16.5");

        // 14. Synced Entity Data Class Redirects
        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/network/datasync/DataParameter",
                "net/minecraft/network/syncher/EntityDataAccessor",
                v1_17_0, null, null,
                "DataParameter -> EntityDataAccessor (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/network/syncher/EntityDataAccessor",
                "net/minecraft/network/datasync/DataParameter",
                null, v1_16_5, null,
                "EntityDataAccessor -> DataParameter (<= 1.16.5)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/network/datasync/DataSerializers",
                "net/minecraft/network/syncher/EntityDataSerializers",
                v1_17_0, null, null,
                "DataSerializers -> EntityDataSerializers (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/network/syncher/EntityDataSerializers",
                "net/minecraft/network/datasync/DataSerializers",
                null, v1_16_5, null,
                "EntityDataSerializers -> DataSerializers (<= 1.16.5)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/network/datasync/EntityDataManager",
                "net/minecraft/network/syncher/SynchedEntityData",
                v1_17_0, null, null,
                "EntityDataManager -> SynchedEntityData (1.17+)"
        ));

        kb.registerRule(new ClassRedirectRule(
                "net/minecraft/network/syncher/SynchedEntityData",
                "net/minecraft/network/datasync/EntityDataManager",
                null, v1_16_5, null,
                "SynchedEntityData -> EntityDataManager (<= 1.16.5)"
        ));

        // 15. Synced Entity Data Method Polyfills
        kb.registerRule(new PolyfillRule(
                "net/minecraft/network/datasync/EntityDataManager", "createKey",
                "(Ljava/lang/Class;Lnet/minecraft/network/datasync/DataSerializer;)Lnet/minecraft/network/datasync/DataParameter;",
                "com/kyroxova/continuumlib/shims/EntityDataShim", "defineId",
                "(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;",
                v1_17_0, null, null,
                "EntityDataManager.createKey -> EntityDataShim.defineId (1.17+)"
        ));

        kb.registerRule(new PolyfillRule(
                "net/minecraft/network/syncher/SynchedEntityData", "defineId",
                "(Ljava/lang/Class;Lnet/minecraft/network/syncher/EntityDataSerializer;)Lnet/minecraft/network/syncher/EntityDataAccessor;",
                "com/kyroxova/continuumlib/shims/EntityDataShim", "createKey",
                "(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;",
                null, v1_16_5, null,
                "SynchedEntityData.defineId -> EntityDataShim.createKey (<= 1.16.5)"
        ));

        // 16. SynchedEntityData.define polyfill for 1.20.5+ / 26.3+ (builder adaptation)
        kb.registerRule(new PolyfillRule(
                "net/minecraft/network/syncher/SynchedEntityData", "define",
                "(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V",
                "com/kyroxova/continuumlib/shims/EntityDataShim", "define",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                v1_20_5, null, null,
                "SynchedEntityData.define -> EntityDataShim.define (1.20.5+)"
        ));
    }
}
