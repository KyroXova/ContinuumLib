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
}
