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

    @Test
    public void testEnchantmentHelperAndLivingEntityTransformations() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        ClassNode callerClass = new ClassNode();
        callerClass.version = Opcodes.V17;
        callerClass.access = Opcodes.ACC_PUBLIC;
        callerClass.name = "com/example/TestEntityCaller";
        callerClass.superName = "java/lang/Object";

        MethodNode testMethod = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "invokeAll",
                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ai/attributes/Attribute;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/effect/MobEffect;)V",
                null,
                null
        );
        InsnList il = testMethod.instructions;

        // 1. EnchantmentHelper.getEnchantmentLevel(Enchantment, ItemStack)
        il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // Enchantment
        il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // ItemStack
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/world/item/enchantment/EnchantmentHelper", "getEnchantmentLevel", "(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I", false));
        il.add(new InsnNode(Opcodes.POP));

        // 2. EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)
        il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // Enchantment
        il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // ItemStack
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/world/item/enchantment/EnchantmentHelper", "getItemEnchantmentLevel", "(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I", false));
        il.add(new InsnNode(Opcodes.POP));

        // 3. player.getAttributeValue(Attribute)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // player (subclass of LivingEntity)
        il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // Attribute
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "getAttributeValue", "(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D", false));
        il.add(new InsnNode(Opcodes.POP2));

        // 4. player.getAttribute(Attribute)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // player
        il.add(new VarInsnNode(Opcodes.ALOAD, 3)); // Attribute
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "getAttribute", "(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;", false));
        il.add(new InsnNode(Opcodes.POP));

        // 5. player.getItemBySlot(EquipmentSlot)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // player
        il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // EquipmentSlot
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "getItemBySlot", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false));
        il.add(new InsnNode(Opcodes.POP));

        // 6. player.setItemSlot(EquipmentSlot, ItemStack)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // player
        il.add(new VarInsnNode(Opcodes.ALOAD, 4)); // EquipmentSlot
        il.add(new VarInsnNode(Opcodes.ALOAD, 2)); // ItemStack
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "setItemSlot", "(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V", false));

        // 7. player.hasEffect(MobEffect)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // player
        il.add(new VarInsnNode(Opcodes.ALOAD, 5)); // MobEffect
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "hasEffect", "(Lnet/minecraft/world/effect/MobEffect;)Z", false));
        il.add(new InsnNode(Opcodes.POP));

        // 8. player.getEffect(MobEffect)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // player
        il.add(new VarInsnNode(Opcodes.ALOAD, 5)); // MobEffect
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "getEffect", "(Lnet/minecraft/world/effect/MobEffect;)Lnet/minecraft/world/effect/MobEffectInstance;", false));
        il.add(new InsnNode(Opcodes.POP));

        il.add(new InsnNode(Opcodes.RETURN));
        callerClass.methods.add(testMethod);

        ClassWriter cw = new ClassWriter(0);
        callerClass.accept(cw);
        byte[] transformed = transformer.transform("com/example/TestEntityCaller", cw.toByteArray());
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        MethodNode resMethod = result.methods.stream().filter(m -> "invokeAll".equals(m.name)).findFirst().orElseThrow();

        // Verify transformed instructions:
        boolean hasEnchantLevel = false;
        boolean hasItemEnchantLevel = false;
        boolean hasAttrVal = false;
        boolean hasAttr = false;
        boolean hasItemBySlot = false;
        boolean hasSetItemSlot = false;
        boolean hasEffect = false;
        boolean hasGetEffect = false;

        for (AbstractInsnNode insn : resMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.owner.contains("EnchantmentShim")) {
                    if ("getEnchantmentLevel".equals(minsn.name)) hasEnchantLevel = true;
                    if ("getItemEnchantmentLevel".equals(minsn.name)) hasItemEnchantLevel = true;
                    assertEquals(Opcodes.INVOKESTATIC, minsn.getOpcode());
                }
                if (minsn.owner.contains("LivingEntityShim") || minsn.owner.contains("AttributeModifierShim")) {
                    if ("getAttributeValue".equals(minsn.name)) hasAttrVal = true;
                    if ("getAttribute".equals(minsn.name)) hasAttr = true;
                    if ("getItemBySlot".equals(minsn.name)) hasItemBySlot = true;
                    if ("setItemSlot".equals(minsn.name)) hasSetItemSlot = true;
                    assertEquals(Opcodes.INVOKESTATIC, minsn.getOpcode());
                }
                if (minsn.owner.contains("MobEffectShim")) {
                    if ("hasEffect".equals(minsn.name)) hasEffect = true;
                    if ("getEffect".equals(minsn.name)) hasGetEffect = true;
                    assertEquals(Opcodes.INVOKESTATIC, minsn.getOpcode());
                }
            }
        }

        assertTrue(hasEnchantLevel, "EnchantmentHelper.getEnchantmentLevel -> EnchantmentShim");
        assertTrue(hasItemEnchantLevel, "EnchantmentHelper.getItemEnchantmentLevel -> EnchantmentShim");
        assertTrue(hasAttrVal, "Player.getAttributeValue -> LivingEntityShim");
        assertTrue(hasAttr, "Player.getAttribute -> LivingEntityShim");
        assertTrue(hasItemBySlot, "Player.getItemBySlot -> LivingEntityShim");
        assertTrue(hasSetItemSlot, "Player.setItemSlot -> LivingEntityShim");
        assertTrue(hasEffect, "Player.hasEffect -> MobEffectShim");
        assertTrue(hasGetEffect, "Player.getEffect -> MobEffectShim");
    }

    @Test
    public void test26_3RecipeAssembleBridge() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target26_3 = TargetSpec.of("26.3", "neoforge");
        TargetSpec target121 = TargetSpec.of("1.21.1", "neoforge");

        ContinuumBytecodeTransformer transformer26_3 = new ContinuumBytecodeTransformer(kb, base, target26_3);
        ContinuumBytecodeTransformer transformer121 = new ContinuumBytecodeTransformer(kb, base, target121);

        // 1. Legacy recipe class (pre-1.20.5 with Container)
        ClassNode legacyRecipe = new ClassNode();
        legacyRecipe.version = Opcodes.V17;
        legacyRecipe.access = Opcodes.ACC_PUBLIC;
        legacyRecipe.name = "com/example/MyLegacyRecipe";
        legacyRecipe.superName = "java/lang/Object";

        MethodNode legacyAssemble = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "assemble",
                "(Lnet/minecraft/world/Container;)Lnet/minecraft/world/item/ItemStack;",
                null,
                null
        );
        legacyAssemble.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        legacyAssemble.instructions.add(new InsnNode(Opcodes.ARETURN));
        legacyRecipe.methods.add(legacyAssemble);

        ClassWriter cw = new ClassWriter(0);
        legacyRecipe.accept(cw);
        byte[] recipeBytes = cw.toByteArray();

        // Transform for 26.3
        byte[] transformed26_3 = transformer26_3.transform("com/example/MyLegacyRecipe", recipeBytes);
        ClassReader cr26_3 = new ClassReader(transformed26_3);
        ClassNode res26_3 = new ClassNode();
        cr26_3.accept(res26_3, 0);

        boolean has1ArgRecipeInputAssemble = false;
        boolean has1ArgCraftingInputAssemble = false;
        boolean has2ArgAssemble = false;

        for (MethodNode m : res26_3.methods) {
            if ("assemble".equals(m.name)) {
                if ("(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;".equals(m.desc)) {
                    has1ArgRecipeInputAssemble = true;
                }
                if ("(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;".equals(m.desc)) {
                    has1ArgCraftingInputAssemble = true;
                }
                if (m.desc.contains("HolderLookup$Provider")) {
                    has2ArgAssemble = true;
                }
            }
        }

        assertTrue(has1ArgRecipeInputAssemble, "26.3+ target must inject 1-arg assemble(RecipeInput)");
        assertTrue(has1ArgCraftingInputAssemble, "26.3+ target must inject 1-arg assemble(CraftingInput)");
        assertFalse(has2ArgAssemble, "26.3+ target must NOT inject 2-arg assemble with HolderLookup.Provider");

        // Transform for 1.21.1
        byte[] transformed121 = transformer121.transform("com/example/MyLegacyRecipe", recipeBytes);
        ClassReader cr121 = new ClassReader(transformed121);
        ClassNode res121 = new ClassNode();
        cr121.accept(res121, 0);

        boolean has121CraftingInputAssemble = false;
        for (MethodNode m : res121.methods) {
            if ("assemble".equals(m.name) && "(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;".equals(m.desc)) {
                has121CraftingInputAssemble = true;
            }
        }
        assertTrue(has121CraftingInputAssemble, "1.21.1 target must inject 2-arg assemble(CraftingInput, HolderLookup.Provider)");
    }

    @Test
    public void testWave5ExplosionSoundAndAdvancementTransformations() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec targetModern = TargetSpec.of("26.3", "neoforge");
        TargetSpec targetLegacy = TargetSpec.of("1.16.5", "forge");
        TargetSpec target119 = TargetSpec.of("1.19.2", "forge");
        TargetSpec target1201 = TargetSpec.of("1.20.1", "forge");

        ContinuumBytecodeTransformer transformerModern = new ContinuumBytecodeTransformer(kb, base, targetModern);
        ContinuumBytecodeTransformer transformerLegacy = new ContinuumBytecodeTransformer(kb, base, targetLegacy);
        ContinuumBytecodeTransformer transformer119 = new ContinuumBytecodeTransformer(kb, base, target119);
        ContinuumBytecodeTransformer transformer1201 = new ContinuumBytecodeTransformer(kb, base, target1201);

        // 1. Test Modern Target (>= 1.20 and 26.3+)
        ClassNode callerModern = new ClassNode();
        callerModern.version = Opcodes.V17;
        callerModern.access = Opcodes.ACC_PUBLIC;
        callerModern.name = "com/example/TestWave5ModernCaller";
        callerModern.superName = "java/lang/Object";

        MethodNode m1 = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "testModern", "()V", null, null);
        InsnList il1 = m1.instructions;

        // Explosion$BlockInteraction.BREAK -> Level$ExplosionInteraction.BLOCK
        il1.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/Explosion$BlockInteraction", "BREAK", "Lnet/minecraft/world/level/Explosion$BlockInteraction;"));
        il1.add(new InsnNode(Opcodes.POP));

        // Level.explode returning Explosion followed by POP -> returns void, POP removed
        il1.add(new InsnNode(Opcodes.ACONST_NULL)); // Level
        il1.add(new InsnNode(Opcodes.ACONST_NULL)); // Entity
        il1.add(new InsnNode(Opcodes.DCONST_0));     // x
        il1.add(new InsnNode(Opcodes.DCONST_0));     // y
        il1.add(new InsnNode(Opcodes.DCONST_0));     // z
        il1.add(new InsnNode(Opcodes.FCONST_1));     // power
        il1.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/Explosion$BlockInteraction", "BREAK", "Lnet/minecraft/world/level/Explosion$BlockInteraction;"));
        il1.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "explode", "(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Explosion$BlockInteraction;)Lnet/minecraft/world/level/Explosion;", false));
        il1.add(new InsnNode(Opcodes.POP));

        // Level.explode returning Explosion followed by ASTORE -> returns void, ACONST_NULL inserted before ASTORE
        il1.add(new InsnNode(Opcodes.ACONST_NULL)); // Level
        il1.add(new InsnNode(Opcodes.ACONST_NULL)); // Entity
        il1.add(new InsnNode(Opcodes.DCONST_0));     // x
        il1.add(new InsnNode(Opcodes.DCONST_0));     // y
        il1.add(new InsnNode(Opcodes.DCONST_0));     // z
        il1.add(new InsnNode(Opcodes.FCONST_1));     // power
        il1.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/Explosion$BlockInteraction", "BREAK", "Lnet/minecraft/world/level/Explosion$BlockInteraction;"));
        il1.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "explode", "(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Explosion$BlockInteraction;)Lnet/minecraft/world/level/Explosion;", false));
        il1.add(new VarInsnNode(Opcodes.ASTORE, 0));

        // SoundCategory.PLAYERS -> SoundSource.PLAYERS
        il1.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/util/SoundCategory", "PLAYERS", "Lnet/minecraft/util/SoundCategory;"));
        il1.add(new InsnNode(Opcodes.POP));

        // Advancement$Builder.build(ResourceLocation) returning Advancement -> returns AdvancementHolder + .value()
        il1.add(new InsnNode(Opcodes.ACONST_NULL)); // Builder
        il1.add(new InsnNode(Opcodes.ACONST_NULL)); // ResourceLocation
        il1.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/advancements/Advancement$Builder", "build", "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/advancements/Advancement;", false));
        il1.add(new InsnNode(Opcodes.POP));

        // CriteriaTriggers relocation (26.3+)
        il1.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/advancements/CriteriaTriggers", "LOCATION", "Lnet/minecraft/advancements/critereon/LocationTrigger;"));
        il1.add(new InsnNode(Opcodes.POP));

        il1.add(new InsnNode(Opcodes.RETURN));
        callerModern.methods.add(m1);

        ClassWriter cwMod = new ClassWriter(0);
        callerModern.accept(cwMod);
        byte[] transformedMod = transformerModern.transform("com/example/TestWave5ModernCaller", cwMod.toByteArray());
        assertNotNull(transformedMod);

        ClassReader crMod = new ClassReader(transformedMod);
        ClassNode resMod = new ClassNode();
        crMod.accept(resMod, 0);

        MethodNode resM1 = resMod.methods.stream().filter(m -> "testModern".equals(m.name)).findFirst().orElseThrow();

        boolean foundExplosionBlockEnum = false;
        boolean foundExplodeVoid = false;
        boolean foundAconstNullBeforeAstore = false;
        boolean foundSoundSourceEnum = false;
        boolean foundAdvancementHolderValue = false;
        boolean foundTriggersRelocation = false;

        for (AbstractInsnNode insn : resM1.instructions.toArray()) {
            if (insn instanceof FieldInsnNode finsn) {
                if ("BLOCK".equals(finsn.name) && "net/minecraft/world/level/Level$ExplosionInteraction".equals(finsn.owner)) {
                    foundExplosionBlockEnum = true;
                }
                if ("PLAYERS".equals(finsn.name) && "net/minecraft/sounds/SoundSource".equals(finsn.owner)) {
                    foundSoundSourceEnum = true;
                }
                if ("net/minecraft/advancements/triggers/CriteriaTriggers".equals(finsn.owner)) {
                    foundTriggersRelocation = true;
                }
            } else if (insn instanceof MethodInsnNode minsn) {
                if ("explode".equals(minsn.name) && minsn.desc.endsWith(")V")) {
                    foundExplodeVoid = true;
                }
                if ("value".equals(minsn.name) && "net/minecraft/advancements/AdvancementHolder".equals(minsn.owner)) {
                    foundAdvancementHolderValue = true;
                }
            } else if (insn instanceof VarInsnNode vinsn && vinsn.getOpcode() == Opcodes.ASTORE) {
                AbstractInsnNode prev = vinsn.getPrevious();
                while (prev != null && prev.getOpcode() < 0) prev = prev.getPrevious();
                if (prev != null && prev.getOpcode() == Opcodes.ACONST_NULL) {
                    foundAconstNullBeforeAstore = true;
                }
            }
        }

        assertTrue(foundExplosionBlockEnum, "Explosion$BlockInteraction.BREAK -> Level$ExplosionInteraction.BLOCK");
        assertTrue(foundExplodeVoid, "Level.explode -> void return type");
        assertTrue(foundAconstNullBeforeAstore, "ACONST_NULL inserted when explode return value is stored to preserve stack neutrality");
        assertTrue(foundSoundSourceEnum, "SoundCategory.PLAYERS -> SoundSource.PLAYERS");
        assertTrue(foundAdvancementHolderValue, "Advancement$Builder.build -> build AdvancementHolder + .value()");
        assertTrue(foundTriggersRelocation, "CriteriaTriggers -> net/minecraft/advancements/triggers/CriteriaTriggers on 26.3+");

        // 2. Test Legacy Target (<= 1.16.5 SoundSource -> SoundCategory)
        ClassNode callerLegacy = new ClassNode();
        callerLegacy.version = Opcodes.V17;
        callerLegacy.access = Opcodes.ACC_PUBLIC;
        callerLegacy.name = "com/example/TestWave5LegacyCaller";
        callerLegacy.superName = "java/lang/Object";

        MethodNode m2 = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "testLegacy", "()V", null, null);
        InsnList il2 = m2.instructions;
        il2.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/sounds/SoundSource", "PLAYERS", "Lnet/minecraft/sounds/SoundSource;"));
        il2.add(new InsnNode(Opcodes.POP));
        il2.add(new InsnNode(Opcodes.RETURN));
        callerLegacy.methods.add(m2);

        ClassWriter cwLeg = new ClassWriter(0);
        callerLegacy.accept(cwLeg);
        byte[] transformedLeg = transformerLegacy.transform("com/example/TestWave5LegacyCaller", cwLeg.toByteArray());
        ClassReader crLeg = new ClassReader(transformedLeg);
        ClassNode resLeg = new ClassNode();
        crLeg.accept(resLeg, 0);

        MethodNode resM2 = resLeg.methods.stream().filter(m -> "testLegacy".equals(m.name)).findFirst().orElseThrow();
        boolean foundSoundCategoryEnum = false;
        for (AbstractInsnNode insn : resM2.instructions.toArray()) {
            if (insn instanceof FieldInsnNode finsn) {
                if ("PLAYERS".equals(finsn.name) && "net/minecraft/util/SoundCategory".equals(finsn.owner)) {
                    foundSoundCategoryEnum = true;
                }
            }
        }
        assertTrue(foundSoundCategoryEnum, "SoundSource.PLAYERS -> SoundCategory.PLAYERS on <= 1.16.5");

        // 3. Test 1.19.2 Target (ExplosionInteraction -> BlockInteraction and modern explode returning void -> returns Explosion + POP)
        ClassNode caller119 = new ClassNode();
        caller119.version = Opcodes.V17;
        caller119.access = Opcodes.ACC_PUBLIC;
        caller119.name = "com/example/TestWave5119Caller";
        caller119.superName = "java/lang/Object";

        MethodNode m3 = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test119", "()V", null, null);
        InsnList il3 = m3.instructions;
        il3.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/Level$ExplosionInteraction", "BLOCK", "Lnet/minecraft/world/level/Level$ExplosionInteraction;"));
        il3.add(new InsnNode(Opcodes.POP));

        il3.add(new InsnNode(Opcodes.ACONST_NULL)); // Level
        il3.add(new InsnNode(Opcodes.ACONST_NULL)); // Entity
        il3.add(new InsnNode(Opcodes.DCONST_0));     // x
        il3.add(new InsnNode(Opcodes.DCONST_0));     // y
        il3.add(new InsnNode(Opcodes.DCONST_0));     // z
        il3.add(new InsnNode(Opcodes.FCONST_1));     // power
        il3.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/Level$ExplosionInteraction", "BLOCK", "Lnet/minecraft/world/level/Level$ExplosionInteraction;"));
        il3.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "explode", "(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V", false));

        il3.add(new InsnNode(Opcodes.RETURN));
        caller119.methods.add(m3);

        ClassWriter cw119 = new ClassWriter(0);
        caller119.accept(cw119);
        byte[] transformed119 = transformer119.transform("com/example/TestWave5119Caller", cw119.toByteArray());
        ClassReader cr119 = new ClassReader(transformed119);
        ClassNode res119 = new ClassNode();
        cr119.accept(res119, 0);

        MethodNode resM3 = res119.methods.stream().filter(m -> "test119".equals(m.name)).findFirst().orElseThrow();
        boolean foundBlockInteractionBreak = false;
        boolean foundExplodeReturningExplosion = false;
        boolean foundInsertedPop = false;

        for (AbstractInsnNode insn : resM3.instructions.toArray()) {
            if (insn instanceof FieldInsnNode finsn) {
                if ("BREAK".equals(finsn.name) && "net/minecraft/world/level/Explosion$BlockInteraction".equals(finsn.owner)) {
                    foundBlockInteractionBreak = true;
                }
            } else if (insn instanceof MethodInsnNode minsn) {
                if ("explode".equals(minsn.name) && minsn.desc.endsWith("Lnet/minecraft/world/level/Explosion;")) {
                    foundExplodeReturningExplosion = true;
                    AbstractInsnNode next = minsn.getNext();
                    while (next != null && next.getOpcode() < 0) next = next.getNext();
                    if (next != null && next.getOpcode() == Opcodes.POP) {
                        foundInsertedPop = true;
                    }
                }
            }
        }
        assertTrue(foundBlockInteractionBreak, "Level$ExplosionInteraction.BLOCK -> Explosion$BlockInteraction.BREAK on <= 1.19.2");
        assertTrue(foundExplodeReturningExplosion, "Level.explode -> Explosion return type on <= 1.19.2");
        assertTrue(foundInsertedPop, "POP inserted after Level.explode returning Explosion to preserve stack neutrality");

        // 4. Test 1.20.1 Target (AdvancementHolder.value() removed, AdvancementHolder.id() -> getId())
        ClassNode caller1201 = new ClassNode();
        caller1201.version = Opcodes.V17;
        caller1201.access = Opcodes.ACC_PUBLIC;
        caller1201.name = "com/example/TestWave51201Caller";
        caller1201.superName = "java/lang/Object";

        MethodNode m4 = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test1201", "()V", null, null);
        InsnList il4 = m4.instructions;
        il4.add(new InsnNode(Opcodes.ACONST_NULL)); // AdvancementHolder
        il4.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/advancements/AdvancementHolder", "value", "()Lnet/minecraft/advancements/Advancement;", false));
        il4.add(new InsnNode(Opcodes.POP));

        il4.add(new InsnNode(Opcodes.ACONST_NULL)); // AdvancementHolder
        il4.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/advancements/AdvancementHolder", "id", "()Lnet/minecraft/resources/ResourceLocation;", false));
        il4.add(new InsnNode(Opcodes.POP));

        il4.add(new InsnNode(Opcodes.RETURN));
        caller1201.methods.add(m4);

        ClassWriter cw1201 = new ClassWriter(0);
        caller1201.accept(cw1201);
        byte[] transformed1201 = transformer1201.transform("com/example/TestWave51201Caller", cw1201.toByteArray());
        ClassReader cr1201 = new ClassReader(transformed1201);
        ClassNode res1201 = new ClassNode();
        cr1201.accept(res1201, 0);

        MethodNode resM4 = res1201.methods.stream().filter(m -> "test1201".equals(m.name)).findFirst().orElseThrow();
        boolean foundValueCall = false;
        boolean foundGetIdCall = false;

        for (AbstractInsnNode insn : resM4.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if ("value".equals(minsn.name)) foundValueCall = true;
                if ("getId".equals(minsn.name)) foundGetIdCall = true;
            }
        }
        assertFalse(foundValueCall, "AdvancementHolder.value() must be removed on <= 1.20.1");
        assertTrue(foundGetIdCall, "AdvancementHolder.id() must be rewritten to getId() on <= 1.20.1");
    }

    @Test
    public void testEntitySyncedDataSyntheticBridge() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target1205 = TargetSpec.of("1.21.1", "neoforge");

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target1205);

        ClassNode entityNode = new ClassNode();
        entityNode.version = Opcodes.V17;
        entityNode.access = Opcodes.ACC_PUBLIC;
        entityNode.name = "com/example/MyCustomEntity";
        entityNode.superName = "net/minecraft/world/entity/Entity";

        MethodNode legacyDefine = new MethodNode(
                Opcodes.ACC_PROTECTED,
                "defineSynchedData",
                "()V",
                null,
                null
        );
        legacyDefine.instructions.add(new InsnNode(Opcodes.RETURN));
        entityNode.methods.add(legacyDefine);

        ClassWriter cw = new ClassWriter(0);
        entityNode.accept(cw);
        byte[] transformed = transformer.transform("com/example/MyCustomEntity", cw.toByteArray());
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode res = new ClassNode();
        cr.accept(res, 0);

        MethodNode injectedBridge = null;
        for (MethodNode m : res.methods) {
            if ("defineSynchedData".equals(m.name) && "(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V".equals(m.desc)) {
                injectedBridge = m;
                break;
            }
        }

        assertNotNull(injectedBridge, "defineSynchedData(SynchedEntityData$Builder) bridge must be injected for Entity on >= 1.20.5");
        assertTrue((injectedBridge.access & Opcodes.ACC_SYNTHETIC) != 0, "Bridge method must be synthetic");

        boolean hasSuperCall = false;
        boolean hasPushBuilder = false;
        boolean hasLegacyDefineCall = false;
        boolean hasPopBuilder = false;

        for (AbstractInsnNode insn : injectedBridge.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.getOpcode() == Opcodes.INVOKESPECIAL && "net/minecraft/world/entity/Entity".equals(minsn.owner) && "defineSynchedData".equals(minsn.name)) {
                    hasSuperCall = true;
                }
                if (minsn.owner.contains("EntityDataShim") && "pushBuilder".equals(minsn.name)) {
                    hasPushBuilder = true;
                }
                if (minsn.getOpcode() == Opcodes.INVOKEVIRTUAL && "com/example/MyCustomEntity".equals(minsn.owner) && "defineSynchedData".equals(minsn.name) && "()V".equals(minsn.desc)) {
                    hasLegacyDefineCall = true;
                }
                if (minsn.owner.contains("EntityDataShim") && "popBuilder".equals(minsn.name)) {
                    hasPopBuilder = true;
                }
            }
        }

        assertTrue(hasSuperCall, "Bridge must call super.defineSynchedData(builder)");
        assertTrue(hasPushBuilder, "Bridge must call EntityDataShim.pushBuilder(builder)");
        assertTrue(hasLegacyDefineCall, "Bridge must call this.defineSynchedData()");
        assertTrue(hasPopBuilder, "Bridge must call EntityDataShim.popBuilder()");
    }
}
