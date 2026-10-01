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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Wave 3 Subsystem:
 * 26.3+ Item Evolution Synthetic Bridges (Requirement R2 & R3).
 * Bridges:
 * 1. inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot) -> void
 * 2. getUseDuration(ItemStack, LivingEntity) -> int
 * 3. use(Level, Player, InteractionHand) -> InteractionResult
 */
public class ItemEvolutionBridgesVerificationTest {

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
        @DisplayName("1.1 Item inventoryTick modern synthetic bridge injection with EquipmentSlot")
        public void testInventoryTickBridge() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/MyCustomItem";
            cn.superName = "java/lang/Object";

            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy inventoryTick: (ItemStack, Level, Entity, int, boolean) -> void
            MethodNode legacyTick = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "inventoryTick",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
                    null,
                    null
            );
            legacyTick.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(legacyTick);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.MyCustomItem", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resultNode = new ClassNode();
            cr.accept(resultNode, 0);

            boolean hasModernTickBridge = false;
            for (MethodNode m : resultNode.methods) {
                if ("inventoryTick".equals(m.name)
                        && "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V".equals(m.desc)) {
                    hasModernTickBridge = true;
                    assertTrue((m.access & Opcodes.ACC_SYNTHETIC) != 0, "Bridge method should be marked synthetic");
                }
            }

            assertTrue(hasModernTickBridge, "Modern 26.3 inventoryTick bridge should be injected");
        }

        @Test
        @DisplayName("1.2 Item getUseDuration modern synthetic bridge injection with LivingEntity")
        public void testGetUseDurationBridge() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/MyFoodItem";
            cn.superName = "java/lang/Object";

            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy getUseDuration: (ItemStack) -> int
            MethodNode legacyDuration = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "getUseDuration",
                    "(Lnet/minecraft/world/item/ItemStack;)I",
                    null,
                    null
            );
            legacyDuration.instructions.add(new InsnNode(Opcodes.ICONST_5));
            legacyDuration.instructions.add(new InsnNode(Opcodes.IRETURN));
            cn.methods.add(legacyDuration);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.MyFoodItem", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resultNode = new ClassNode();
            cr.accept(resultNode, 0);

            boolean hasModernDurationBridge = false;
            for (MethodNode m : resultNode.methods) {
                if ("getUseDuration".equals(m.name)
                        && "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I".equals(m.desc)) {
                    hasModernDurationBridge = true;
                    assertTrue((m.access & Opcodes.ACC_SYNTHETIC) != 0, "Bridge method should be marked synthetic");
                }
            }

            assertTrue(hasModernDurationBridge, "Modern 26.3 getUseDuration bridge should be injected");
        }

        @Test
        @DisplayName("1.3 Item use modern synthetic bridge returning InteractionResult instead of InteractionResultHolder")
        public void testItemUseBridge() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/MyInteractiveItem";
            cn.superName = "java/lang/Object";

            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy use: (Level, Player, InteractionHand) -> InteractionResultHolder
            MethodNode legacyUse = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "use",
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
                    null,
                    null
            );
            legacyUse.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            legacyUse.instructions.add(new InsnNode(Opcodes.ARETURN));
            cn.methods.add(legacyUse);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.MyInteractiveItem", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resultNode = new ClassNode();
            cr.accept(resultNode, 0);

            boolean hasModernUseBridge = false;
            for (MethodNode m : resultNode.methods) {
                if ("use".equals(m.name)
                        && "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;".equals(m.desc)) {
                    hasModernUseBridge = true;
                    assertTrue((m.access & Opcodes.ACC_SYNTHETIC) != 0, "Bridge method should be marked synthetic");
                }
            }

            assertTrue(hasModernUseBridge, "Modern 26.3 use returning InteractionResult bridge should be injected");
        }
    }

    // =========================================================================
    // Tier 2: Rules Catalog & Transformation Delta Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Rules Catalog & Transformation Delta Tests")
    class Tier2RulesCatalog {

        @Test
        @DisplayName("2.1 Idempotency: Classes already containing modern 26.3 Item methods do not receive duplicates")
        public void testIdempotency() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/AlreadyModernItem";
            cn.superName = "java/lang/Object";

            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(init);

            // Legacy + Already Modern getUseDuration
            MethodNode legacy = new MethodNode(Opcodes.ACC_PUBLIC, "getUseDuration",
                    "(Lnet/minecraft/world/item/ItemStack;)I", null, null);
            legacy.instructions.add(new InsnNode(Opcodes.ICONST_1));
            legacy.instructions.add(new InsnNode(Opcodes.IRETURN));
            cn.methods.add(legacy);

            MethodNode modern = new MethodNode(Opcodes.ACC_PUBLIC, "getUseDuration",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I", null, null);
            modern.instructions.add(new InsnNode(Opcodes.ICONST_2));
            modern.instructions.add(new InsnNode(Opcodes.IRETURN));
            cn.methods.add(modern);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.AlreadyModernItem", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resNode = new ClassNode();
            cr.accept(resNode, 0);

            long durationMethods = resNode.methods.stream()
                    .filter(m -> "getUseDuration".equals(m.name) && m.desc.contains("LivingEntity"))
                    .count();

            assertEquals(1, durationMethods, "Should not duplicate modern getUseDuration bridge");
        }

        @Test
        @DisplayName("2.2 Version gating: Targets below 26.3 do not inject 26.3 Item bridges")
        public void testVersionGating() throws Exception {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.20.4", "forge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode cn = new ClassNode();
            cn.version = Opcodes.V17;
            cn.access = Opcodes.ACC_PUBLIC;
            cn.name = "com/example/LegacyTargetItem";
            cn.superName = "java/lang/Object";

            MethodNode legacy = new MethodNode(Opcodes.ACC_PUBLIC, "getUseDuration",
                    "(Lnet/minecraft/world/item/ItemStack;)I", null, null);
            legacy.instructions.add(new InsnNode(Opcodes.ICONST_1));
            legacy.instructions.add(new InsnNode(Opcodes.IRETURN));
            cn.methods.add(legacy);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            byte[] transformed = transformer.transform("com.example.LegacyTargetItem", bytecode);
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resNode = new ClassNode();
            cr.accept(resNode, 0);

            boolean has26_3Bridge = resNode.methods.stream()
                    .anyMatch(m -> "getUseDuration".equals(m.name) && m.desc.contains("LivingEntity"));

            assertFalse(has26_3Bridge, "Target 1.20.4 should not inject 26.3 LivingEntity bridge");
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent bytecode transformation and Item bridge injection")
        public void testConcurrentItemBridgeTransformations() throws Exception {
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
                            cn.name = "com/example/ConcurrentItem_" + threadId + "_" + i;
                            cn.superName = "java/lang/Object";

                            MethodNode legacy = new MethodNode(
                                    Opcodes.ACC_PUBLIC,
                                    "use",
                                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
                                    null,
                                    null
                            );
                            legacy.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                            legacy.instructions.add(new InsnNode(Opcodes.ARETURN));
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
                                        .anyMatch(m -> "use".equals(m.name) && "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;".equals(m.desc));
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

            assertTrue(latch.await(15, TimeUnit.SECONDS), "Concurrent item transformations timed out");
            executor.shutdown();

            assertEquals(threadCount * iterationsPerThread, successCount.get());
        }
    }
}
