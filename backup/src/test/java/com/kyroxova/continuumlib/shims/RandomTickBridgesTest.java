package com.kyroxova.continuumlib.shims;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class RandomTickBridgesTest {

    @Test
    public void testRandomShimDelegation() {
        Random rand = new Random(12345L);
        int val = rand.nextInt(100);

        Random wrapped = RandomShim.toLegacyRandom(rand);
        assertNotNull(wrapped);

        Object source = RandomShim.toRandomSource(rand);
        assertNotNull(source);
    }

    @Test
    public void testRandomTickSyntheticBridgesInjection() {
        // Synthesize a legacy Block class with animateTick and randomTick
        ClassNode blockNode = new ClassNode();
        blockNode.version = Opcodes.V17;
        blockNode.access = Opcodes.ACC_PUBLIC;
        blockNode.name = "com/example/MyCustomCropBlock";
        blockNode.superName = "net/minecraft/world/level/block/Block";

        // animateTick(BlockState, Level, BlockPos, Random)
        MethodNode animateMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "animateTick",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V",
                null,
                null
        );
        animateMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        blockNode.methods.add(animateMethod);

        // randomTick(BlockState, ServerLevel, BlockPos, Random)
        MethodNode randomTickMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "randomTick",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V",
                null,
                null
        );
        randomTickMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        blockNode.methods.add(randomTickMethod);

        // tick(BlockState, ServerLevel, BlockPos, Random)
        MethodNode tickMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "tick",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/Random;)V",
                null,
                null
        );
        tickMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        blockNode.methods.add(tickMethod);

        ClassWriter cw = new ClassWriter(0);
        blockNode.accept(cw);
        byte[] legacyBytecode = cw.toByteArray();

        // Transform targeting 1.20.1 Forge
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.1", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        byte[] transformed = transformer.transform("com/example/MyCustomCropBlock", legacyBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasModernAnimateTick = false;
        boolean hasModernRandomTick = false;
        boolean hasModernTick = false;

        for (MethodNode m : resultNode.methods) {
            if ("animateTick".equals(m.name) && m.desc.contains("RandomSource")) {
                hasModernAnimateTick = true;
            }
            if ("randomTick".equals(m.name) && m.desc.contains("RandomSource")) {
                hasModernRandomTick = true;
            }
            if ("tick".equals(m.name) && m.desc.contains("RandomSource")) {
                hasModernTick = true;
            }
        }

        assertTrue(hasModernAnimateTick, "Block must have synthetic animateTick(..., RandomSource) bridge on 1.19+");
        assertTrue(hasModernRandomTick, "Block must have synthetic randomTick(..., RandomSource) bridge on 1.19+");
        assertTrue(hasModernTick, "Block must have synthetic tick(..., RandomSource) bridge on 1.19+");
    }
}
