package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.shims.BlockInteractionShim;
import com.kyroxova.continuumlib.shims.ItemStackShim;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SyntheticMethodsAndAttributesTest {

    @Test
    public void testBlockAndBlockEntitySyntheticBridges() {
        // 1. Synthesize mock Block and BlockEntity classes
        ClassNode blockNode = new ClassNode();
        blockNode.version = Opcodes.V17;
        blockNode.access = Opcodes.ACC_PUBLIC;
        blockNode.name = "com/example/MyCustomBlock";
        blockNode.superName = "net/minecraft/world/level/block/Block";

        MethodNode useMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "use",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
                null,
                null
        );
        useMethod.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        useMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
        blockNode.methods.add(useMethod);

        ClassWriter cwBlock = new ClassWriter(0);
        blockNode.accept(cwBlock);
        byte[] blockBytecode = cwBlock.toByteArray();

        // 2. Synthesize BlockEntity class
        ClassNode beNode = new ClassNode();
        beNode.version = Opcodes.V17;
        beNode.access = Opcodes.ACC_PUBLIC;
        beNode.name = "com/example/MyCustomBlockEntity";
        beNode.superName = "net/minecraft/world/level/block/entity/BlockEntity";

        MethodNode saveMethod = new MethodNode(Opcodes.ACC_PUBLIC, "saveAdditional", "(Lnet/minecraft/nbt/CompoundTag;)V", null, null);
        saveMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        beNode.methods.add(saveMethod);

        MethodNode loadMethod = new MethodNode(Opcodes.ACC_PUBLIC, "load", "(Lnet/minecraft/nbt/CompoundTag;)V", null, null);
        loadMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        beNode.methods.add(loadMethod);

        ClassWriter cwBE = new ClassWriter(0);
        beNode.accept(cwBE);
        byte[] beBytecode = cwBE.toByteArray();

        // 3. Transform for 1.20.6 NeoForge target
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.6", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        byte[] transformedBlock = transformer.transform("com/example/MyCustomBlock", blockBytecode);
        byte[] transformedBE = transformer.transform("com/example/MyCustomBlockEntity", beBytecode);

        // Verify Block has synthetic useItemOn and useWithoutItem
        ClassReader crBlock = new ClassReader(transformedBlock);
        ClassNode resultBlock = new ClassNode();
        crBlock.accept(resultBlock, 0);

        boolean hasUseItemOn = false;
        boolean hasUseWithoutItem = false;
        for (MethodNode m : resultBlock.methods) {
            if ("useItemOn".equals(m.name)) hasUseItemOn = true;
            if ("useWithoutItem".equals(m.name)) hasUseWithoutItem = true;
        }
        assertTrue(hasUseItemOn, "Block must have synthetic useItemOn injected for 1.20.6+");
        assertTrue(hasUseWithoutItem, "Block must have synthetic useWithoutItem injected for 1.20.6+");

        // Verify BlockEntity has synthetic saveAdditional and loadAdditional
        ClassReader crBE = new ClassReader(transformedBE);
        ClassNode resultBE = new ClassNode();
        crBE.accept(resultBE, 0);

        boolean hasSaveAdditional = false;
        boolean hasLoadAdditional = false;
        for (MethodNode m : resultBE.methods) {
            if ("saveAdditional".equals(m.name) && "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V".equals(m.desc)) {
                hasSaveAdditional = true;
            }
            if ("loadAdditional".equals(m.name) && "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V".equals(m.desc)) {
                hasLoadAdditional = true;
            }
        }
        assertTrue(hasSaveAdditional, "BlockEntity must have modern saveAdditional injected for 1.20.6+");
        assertTrue(hasLoadAdditional, "BlockEntity must have modern loadAdditional injected for 1.20.6+");
    }

    @Test
    public void testEntityLevelAndAttributeModifierTransformation() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/EntityHandler";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle", "(Lnet/minecraft/world/entity/Entity;)V", null, null);
        // Entity.level field access:
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/entity/Entity", "level", "Lnet/minecraft/world/level/Level;"));
        mn.instructions.add(new InsnNode(Opcodes.POP));

        // AttributeModifier constructor:
        // new AttributeModifier(UUID, String, double, Operation)
        mn.instructions.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/world/entity/ai/attributes/AttributeModifier"));
        mn.instructions.add(new InsnNode(Opcodes.DUP));
        mn.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); // UUID
        mn.instructions.add(new LdcInsnNode("generic.speed")); // name
        mn.instructions.add(new InsnNode(Opcodes.DCONST_0)); // amount
        mn.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); // operation
        mn.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "net/minecraft/world/entity/ai/attributes/AttributeModifier",
                "<init>",
                "(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V",
                false
        ));
        mn.instructions.add(new InsnNode(Opcodes.POP));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] original = cw.toByteArray();

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        byte[] transformed = transformer.transform("com/example/EntityHandler", original);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        MethodNode transformedMethod = resultNode.methods.get(0);
        boolean levelGetterCalled = false;
        boolean attributeModifierShimCalled = false;

        for (AbstractInsnNode insn : transformedMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if ("level".equals(minsn.name) && "()Lnet/minecraft/world/level/Level;".equals(minsn.desc)) {
                    levelGetterCalled = true;
                }
                if ("com/kyroxova/continuumlib/shims/AttributeModifierShim".equals(minsn.owner)
                        && "createModifier".equals(minsn.name)) {
                    attributeModifierShimCalled = true;
                }
            }
        }

        assertTrue(levelGetterCalled, "Entity.level field access must be rewritten to level() method call on 1.20+");
        assertTrue(attributeModifierShimCalled, "AttributeModifier constructor must be rewritten to AttributeModifierShim on 1.20.5+");
    }

    @Test
    public void testBlockInteractionShim() {
        // Test fallback behavior of BlockInteractionShim when vanilla classes are absent
        Object converted = BlockInteractionShim.toItemInteractionResult("PASS");
        assertNotNull(converted);
    }

    @Test
    public void testWorldAndLevelBytecodeTransformations() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/WorldHandler";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", null, null);
        // 1. GETFIELD isRemote:
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/level/Level", "isRemote", "Z"));
        mn.instructions.add(new InsnNode(Opcodes.POP));

        // 2. INVOKEVIRTUAL getTileEntity:
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "getTileEntity", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", false));
        mn.instructions.add(new InsnNode(Opcodes.POP));

        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] original = cw.toByteArray();

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec target118 = TargetSpec.of("1.18.2", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, TargetSpec.of("1.16.5", "forge"), target118);

        byte[] transformed = transformer.transform("com/example/WorldHandler", original);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        MethodNode resultMethod = resultNode.methods.get(0);
        boolean isClientSideCalled = false;
        boolean getBlockEntityCalled = false;

        for (AbstractInsnNode insn : resultMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if ("isClientSide".equals(minsn.name) && "()Z".equals(minsn.desc)) {
                    isClientSideCalled = true;
                }
                if ("getBlockEntity".equals(minsn.name)) {
                    getBlockEntityCalled = true;
                }
            }
        }

        assertTrue(isClientSideCalled, "isRemote field read must be rewritten to isClientSide() method on 1.17+");
        assertTrue(getBlockEntityCalled, "getTileEntity method must be rewritten to getBlockEntity on 1.17+");
    }

    @Test
    public void testKeyMappingBytecodeTransformations() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/KeyHandler";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handle", "(Lnet/minecraft/client/KeyMapping;)V", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/KeyMapping", "isKeyDown", "()Z", false));
        mn.instructions.add(new InsnNode(Opcodes.POP));

        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/KeyMapping", "isPressed", "()Z", false));
        mn.instructions.add(new InsnNode(Opcodes.POP));

        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] original = cw.toByteArray();

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec target118 = TargetSpec.of("1.18.2", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, TargetSpec.of("1.16.5", "forge"), target118);

        byte[] transformed = transformer.transform("com/example/KeyHandler", original);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        MethodNode resultMethod = resultNode.methods.get(0);
        boolean isDownCalled = false;
        boolean consumeClickCalled = false;

        for (AbstractInsnNode insn : resultMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if ("isDown".equals(minsn.name)) isDownCalled = true;
                if ("consumeClick".equals(minsn.name)) consumeClickCalled = true;
            }
        }

        assertTrue(isDownCalled, "isKeyDown must be rewritten to isDown on 1.17+");
        assertTrue(consumeClickCalled, "isPressed must be rewritten to consumeClick on 1.17+");
    }

    @Test
    public void testParticleDispatchBytecodeTransformations() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/ParticleHandler";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "spawn",
                "(Lnet/minecraft/world/level/Level;Ljava/lang/Object;DDDDDD)V", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        mn.instructions.add(new VarInsnNode(Opcodes.DLOAD, 2));
        mn.instructions.add(new VarInsnNode(Opcodes.DLOAD, 4));
        mn.instructions.add(new VarInsnNode(Opcodes.DLOAD, 6));
        mn.instructions.add(new VarInsnNode(Opcodes.DLOAD, 8));
        mn.instructions.add(new VarInsnNode(Opcodes.DLOAD, 10));
        mn.instructions.add(new VarInsnNode(Opcodes.DLOAD, 12));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "addParticle",
                "(Ljava/lang/Object;DDDDDD)V", false));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] original = cw.toByteArray();

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec target118 = TargetSpec.of("1.18.2", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, TargetSpec.of("1.16.5", "forge"), target118);

        byte[] transformed = transformer.transform("com/example/ParticleHandler", original);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        MethodNode resultMethod = resultNode.methods.get(0);
        boolean particleShimCalled = false;

        for (AbstractInsnNode insn : resultMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if ("com/kyroxova/continuumlib/shims/ParticleShim".equals(minsn.owner) && "addParticle".equals(minsn.name)) {
                    particleShimCalled = true;
                    assertEquals(Opcodes.INVOKESTATIC, minsn.getOpcode());
                }
            }
        }

        assertTrue(particleShimCalled, "Level.addParticle must be redirected to ParticleShim.addParticle");
    }

    @Test
    public void testVoxelShapeSyntheticBridges() {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/CustomShapeBlock";
        cn.superName = "net/minecraft/world/level/block/Block";

        MethodNode legacyBB = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getBoundingBox",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;",
                null,
                null
        );
        legacyBB.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        legacyBB.instructions.add(new InsnNode(Opcodes.ARETURN));
        cn.methods.add(legacyBB);

        MethodNode legacyColBB = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getCollisionBoundingBox",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;",
                null,
                null
        );
        legacyColBB.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        legacyColBB.instructions.add(new InsnNode(Opcodes.ARETURN));
        cn.methods.add(legacyColBB);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] original = cw.toByteArray();

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec target118 = TargetSpec.of("1.18.2", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, TargetSpec.of("1.12.2", "forge"), target118);

        byte[] transformed = transformer.transform("com/example/CustomShapeBlock", original);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasGetShape = false;
        boolean hasGetCollisionShape = false;

        for (MethodNode m : resultNode.methods) {
            if ("getShape".equals(m.name)) hasGetShape = true;
            if ("getCollisionShape".equals(m.name)) hasGetCollisionShape = true;
        }

        assertTrue(hasGetShape, "Block defining getBoundingBox must have synthetic getShape injected for 1.13+");
        assertTrue(hasGetCollisionShape, "Block defining getCollisionBoundingBox must have synthetic getCollisionShape injected for 1.13+");
    }

    @Test
    public void test26_3EvolutionaryShiftBridges() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec target26_3 = TargetSpec.of("26.3", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, TargetSpec.of("1.20.1", "forge"), target26_3);

        // 1. Test BlockBehaviour bridges
        ClassNode blockNode = new ClassNode();
        blockNode.version = Opcodes.V17;
        blockNode.access = Opcodes.ACC_PUBLIC;
        blockNode.name = "com/example/My263Block";
        blockNode.superName = "net/minecraft/world/level/block/Block";

        MethodNode legacyClone = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getCloneItemStack",
                "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;",
                null,
                null
        );
        legacyClone.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        legacyClone.instructions.add(new InsnNode(Opcodes.ARETURN));
        blockNode.methods.add(legacyClone);

        MethodNode legacyNeighbor = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "neighborChanged",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
                null,
                null
        );
        legacyNeighbor.instructions.add(new InsnNode(Opcodes.RETURN));
        blockNode.methods.add(legacyNeighbor);

        MethodNode legacyEntityInside = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "entityInside",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
                null,
                null
        );
        legacyEntityInside.instructions.add(new InsnNode(Opcodes.RETURN));
        blockNode.methods.add(legacyEntityInside);

        ClassWriter cwBlock = new ClassWriter(0);
        blockNode.accept(cwBlock);
        byte[] transformedBlock = transformer.transform("com/example/My263Block", cwBlock.toByteArray());
        assertNotNull(transformedBlock);

        ClassReader crBlock = new ClassReader(transformedBlock);
        ClassNode resultBlock = new ClassNode();
        crBlock.accept(resultBlock, 0);

        boolean hasModernClone = false;
        boolean hasModernNeighbor = false;
        boolean hasModernEntityInside = false;

        for (MethodNode m : resultBlock.methods) {
            if ("getCloneItemStack".equals(m.name) && "(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/item/ItemStack;".equals(m.desc)) {
                hasModernClone = true;
            }
            if ("neighborChanged".equals(m.name) && "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V".equals(m.desc)) {
                hasModernNeighbor = true;
            }
            if ("entityInside".equals(m.name) && "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V".equals(m.desc)) {
                hasModernEntityInside = true;
            }
        }

        assertTrue(hasModernClone, "4-arg getCloneItemStack must be injected for 26.3+");
        assertTrue(hasModernNeighbor, "6-arg neighborChanged with Orientation must be injected for 26.3+");
        assertTrue(hasModernEntityInside, "6-arg entityInside with InsideBlockEffectApplier must be injected for 26.3+");

        // 2. Test Item bridges
        ClassNode itemNode = new ClassNode();
        itemNode.version = Opcodes.V17;
        itemNode.access = Opcodes.ACC_PUBLIC;
        itemNode.name = "com/example/My263Item";
        itemNode.superName = "net/minecraft/world/item/Item";

        MethodNode legacyTick = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "inventoryTick",
                "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
                null,
                null
        );
        legacyTick.instructions.add(new InsnNode(Opcodes.RETURN));
        itemNode.methods.add(legacyTick);

        MethodNode legacyDuration = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getUseDuration",
                "(Lnet/minecraft/world/item/ItemStack;)I",
                null,
                null
        );
        legacyDuration.instructions.add(new InsnNode(Opcodes.ICONST_1));
        legacyDuration.instructions.add(new InsnNode(Opcodes.IRETURN));
        itemNode.methods.add(legacyDuration);

        MethodNode legacyUse = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "use",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
                null,
                null
        );
        legacyUse.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        legacyUse.instructions.add(new InsnNode(Opcodes.ARETURN));
        itemNode.methods.add(legacyUse);

        ClassWriter cwItem = new ClassWriter(0);
        itemNode.accept(cwItem);
        byte[] transformedItem = transformer.transform("com/example/My263Item", cwItem.toByteArray());
        assertNotNull(transformedItem);

        ClassReader crItem = new ClassReader(transformedItem);
        ClassNode resultItem = new ClassNode();
        crItem.accept(resultItem, 0);

        boolean hasModernTick = false;
        boolean hasModernDuration = false;
        boolean hasModernUse = false;

        for (MethodNode m : resultItem.methods) {
            if ("inventoryTick".equals(m.name) && "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V".equals(m.desc)) {
                hasModernTick = true;
            }
            if ("getUseDuration".equals(m.name) && "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I".equals(m.desc)) {
                hasModernDuration = true;
            }
            if ("use".equals(m.name) && "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;".equals(m.desc)) {
                hasModernUse = true;
            }
        }

        assertTrue(hasModernTick, "4-arg inventoryTick must be injected for 26.3+");
        assertTrue(hasModernDuration, "2-arg getUseDuration must be injected for 26.3+");
        assertTrue(hasModernUse, "use returning InteractionResult must be injected for 26.3+");
    }
}
