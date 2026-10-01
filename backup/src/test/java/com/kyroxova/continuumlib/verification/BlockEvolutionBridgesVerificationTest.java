package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Wave 3 Subsystem:
 * 26.3+ BlockBehaviour Evolution Synthetic Bridges (Requirement R2 & R3).
 * Bridges:
 * 1. getCloneItemStack(LevelReader, BlockPos, BlockState, boolean) -> ItemStack
 * 2. neighborChanged(BlockState, Level, BlockPos, Block, Orientation, boolean) -> void
 * 3. entityInside(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier, boolean) -> void
 */
public class BlockEvolutionBridgesVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 BlockBehaviour getCloneItemStack modern synthetic bridge injection")
        public void testGetCloneItemStackBridge() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/MyCustomBlock";
            cn.superName = "java/lang/Object";

            // Default constructor
            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy getCloneItemStack: (BlockGetter, BlockPos, BlockState) -> ItemStack
            MethodNode legacyClone = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "getCloneItemStack",
                    "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            legacyClone.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            legacyClone.instructions.add(new InsnNode(Opcodes.ARETURN));
            cn.methods.add(legacyClone);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.MyCustomBlock", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resultNode = new ClassNode();
            cr.accept(resultNode, 0);

            boolean hasModernCloneBridge = false;
            for (MethodNode m : resultNode.methods) {
                if ("getCloneItemStack".equals(m.name)
                        && "(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/item/ItemStack;".equals(m.desc)) {
                    hasModernCloneBridge = true;
                    assertTrue((m.access & Opcodes.ACC_SYNTHETIC) != 0, "Bridge method should be marked synthetic");
                }
            }

            assertTrue(hasModernCloneBridge, "Modern 26.3 getCloneItemStack bridge should be injected");
        }

        @Test
        @DisplayName("1.2 BlockBehaviour neighborChanged modern synthetic bridge injection with Orientation parameter")
        public void testNeighborChangedBridge() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/MyNeighborBlock";
            cn.superName = "java/lang/Object";

            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy neighborChanged
            MethodNode legacyNeighbor = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "neighborChanged",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
                    null,
                    null
            );
            legacyNeighbor.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(legacyNeighbor);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.MyNeighborBlock", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resultNode = new ClassNode();
            cr.accept(resultNode, 0);

            boolean hasModernNeighborBridge = false;
            for (MethodNode m : resultNode.methods) {
                if ("neighborChanged".equals(m.name)
                        && "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V".equals(m.desc)) {
                    hasModernNeighborBridge = true;
                    assertTrue((m.access & Opcodes.ACC_SYNTHETIC) != 0, "Bridge method should be marked synthetic");
                }
            }

            assertTrue(hasModernNeighborBridge, "Modern 26.3 neighborChanged bridge should be injected");
        }

        @Test
        @DisplayName("1.3 BlockBehaviour entityInside modern synthetic bridge injection with InsideBlockEffectApplier")
        public void testEntityInsideBridge() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/MyEntityInsideBlock";
            cn.superName = "java/lang/Object";

            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy entityInside
            MethodNode legacyEntityInside = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "entityInside",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
                    null,
                    null
            );
            legacyEntityInside.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(legacyEntityInside);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.MyEntityInsideBlock", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resultNode = new ClassNode();
            cr.accept(resultNode, 0);

            boolean hasModernEntityInsideBridge = false;
            for (MethodNode m : resultNode.methods) {
                if ("entityInside".equals(m.name)
                        && "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V".equals(m.desc)) {
                    hasModernEntityInsideBridge = true;
                    assertTrue((m.access & Opcodes.ACC_SYNTHETIC) != 0, "Bridge method should be marked synthetic");
                }
            }

            assertTrue(hasModernEntityInsideBridge, "Modern 26.3 entityInside bridge should be injected");
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Idempotency: Classes already containing modern 26.3 methods do not receive duplicate bridges")
        public void testIdempotency() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/AlreadyModernBlock";
            cn.superName = "java/lang/Object";

            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy + Already Modern neighborChanged
            MethodNode legacy = new MethodNode(Opcodes.ACC_PUBLIC, "neighborChanged",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
                    null, null);
            legacy.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(legacy);

            MethodNode modern = new MethodNode(Opcodes.ACC_PUBLIC, "neighborChanged",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V",
                    null, null);
            modern.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(modern);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.AlreadyModernBlock", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resNode = new ClassNode();
            cr.accept(resNode, 0);

            long modernCount = resNode.methods.stream()
                    .filter(m -> "neighborChanged".equals(m.name) && m.desc.contains("Orientation"))
                    .count();

            assertEquals(1, modernCount, "Should not duplicate modern method");
        }

        @Test
        @DisplayName("2.2 Version gating: Targets below 26.3 do not inject 26.3 bridges")
        public void testVersionGating() throws Exception {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.20.4", "forge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/LegacyTargetBlock";
            cn.superName = "java/lang/Object";

            MethodNode legacy = new MethodNode(Opcodes.ACC_PUBLIC, "neighborChanged",
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
                    null, null);
            legacy.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(legacy);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.LegacyTargetBlock", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resNode = new ClassNode();
            cr.accept(resNode, 0);

            boolean has26_3Bridge = resNode.methods.stream()
                    .anyMatch(m -> "neighborChanged".equals(m.name) && m.desc.contains("Orientation"));

            assertFalse(has26_3Bridge, "Target 1.20.4 should not inject 26.3 Orientation bridge");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent bytecode transformation and bridge injection across worker threads")
        public void testConcurrentBlockBridgeTransformations() throws Exception {
            int threadCount = 16;
            int iterationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            ClassNode cn = new ClassNode();
                            cn.version = Opcodes.V17;
                            cn.access = Opcodes.ACC_PUBLIC;
                            cn.name = "com/example/ConcurrentBlock_" + threadId + "_" + i;
                            cn.superName = "java/lang/Object";

                            MethodNode legacy = new MethodNode(
                                    Opcodes.ACC_PUBLIC,
                                    "entityInside",
                                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
                                    null,
                                    null
                            );
                            legacy.instructions.add(new InsnNode(Opcodes.RETURN));
                            cn.methods.add(legacy);

                            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                            cn.accept(cw);
                            byte[] inBytes = cw.toByteArray();

                            byte[] outBytes = transformer.transform(cn.name.replace('/', '.'), inBytes);
                            if (outBytes != null) {
                                ClassReader cr = new ClassReader(outBytes);
                                ClassNode res = new ClassNode();
                                cr.accept(res, 0);

                                boolean hasBridge = res.methods.stream()
                                        .anyMatch(m -> "entityInside".equals(m.name) && m.desc.contains("InsideBlockEffectApplier"));
                                if (hasBridge) {
                                    successCount.incrementAndGet();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(15, TimeUnit.SECONDS), "Concurrent block transformations timed out");
            executor.shutdown();

            assertEquals(threadCount * iterationsPerThread, successCount.get());
        }
    }
}
