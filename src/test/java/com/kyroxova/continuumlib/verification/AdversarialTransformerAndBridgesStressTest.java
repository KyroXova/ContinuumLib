package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.shims.BlockEntityShim;
import com.kyroxova.continuumlib.shims.RecipeShim;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial Challenger Stress Suite:
 * Rigorously attacks and stress-tests:
 * 1. Constructor stripping stack neutrality under nested, separated, super(), and edge bytecode.
 * 2. BlockEntity recursion guard under deep recursion, nested entities, exceptions, and high concurrency.
 * 3. Dynamic recipe bridges, method descriptor matching, proxy delegation, and idempotence.
 */
public class AdversarialTransformerAndBridgesStressTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // =========================================================================
    // 1. Constructor Stripping & Stack Neutrality Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("1. Constructor Stripping & Stack Neutrality")
    class ConstructorStrippingAdversarial {

        @Test
        @DisplayName("1.1 Stack neutrality: Constructor stripping removes NEW and DUP while preserving argument stack evaluation")
        public void testConstructorStrippingRemovesNewDup() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode callerNode = new ClassNode();
            callerNode.version = Opcodes.V17;
            callerNode.access = Opcodes.ACC_PUBLIC;
            callerNode.name = "com/example/ConstructorCaller";
            callerNode.superName = "java/lang/Object";

            // Method building: new ResourceLocation("outer_mod", "outer_path")
            MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "create", "()Ljava/lang/Object;", null, null);
            InsnList il = m.instructions;
            il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/resources/ResourceLocation"));
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode("outer_mod"));
            il.add(new LdcInsnNode("outer_path"));
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "net/minecraft/resources/ResourceLocation",
                    "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
            ));
            il.add(new InsnNode(Opcodes.ARETURN));
            callerNode.methods.add(m);

            ClassWriter cw = new ClassWriter(0);
            callerNode.accept(cw);
            byte[] transformed = transformer.transform(callerNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode res = new ClassNode();
            cr.accept(res, 0);

            MethodNode transformedM = res.methods.stream()
                    .filter(mn -> "create".equals(mn.name))
                    .findFirst()
                    .orElseThrow();

            boolean hasNew = false;
            boolean hasDup = false;
            boolean hasStaticFactory = false;

            for (AbstractInsnNode insn : transformedM.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.NEW && insn instanceof TypeInsnNode tn && tn.desc.contains("ResourceLocation")) {
                    hasNew = true;
                }
                if (insn.getOpcode() == Opcodes.DUP) {
                    hasDup = true;
                }
                if (insn.getOpcode() == Opcodes.INVOKESTATIC && insn instanceof MethodInsnNode min) {
                    if (min.owner.contains("ResourceLocation") && min.name.contains("fromNamespaceAndPath")) {
                        hasStaticFactory = true;
                    }
                }
            }

            assertFalse(hasNew, "NEW opcode for polyfilled constructor must be eliminated");
            assertFalse(hasDup, "DUP opcode for polyfilled constructor must be eliminated");
            assertTrue(hasStaticFactory, "Constructor invocation must be replaced by static factory method");
        }

        @Test
        @DisplayName("1.2 Constructor stripping preserves subclass super() constructor in multiple overloaded constructors")
        public void testMultipleConstructorsPreserveSuper() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode subClass = new ClassNode();
            subClass.version = Opcodes.V17;
            subClass.access = Opcodes.ACC_PUBLIC;
            subClass.name = "com/example/OverloadedSubclass";
            subClass.superName = "net/minecraft/resources/ResourceLocation";

            // Constructor 1: <init>(String) -> super("mod", str)
            MethodNode init1 = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
            init1.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            init1.instructions.add(new LdcInsnNode("mod"));
            init1.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            init1.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "net/minecraft/resources/ResourceLocation",
                    "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
            ));
            init1.instructions.add(new InsnNode(Opcodes.RETURN));
            subClass.methods.add(init1);

            // Constructor 2: <init>() -> this("default")
            MethodNode init2 = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init2.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            init2.instructions.add(new LdcInsnNode("default"));
            init2.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    subClass.name,
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
            ));
            init2.instructions.add(new InsnNode(Opcodes.RETURN));
            subClass.methods.add(init2);

            ClassWriter cw = new ClassWriter(0);
            subClass.accept(cw);
            byte[] transformed = transformer.transform(subClass.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resNode = new ClassNode();
            cr.accept(resNode, 0);

            // Ensure neither constructor was converted to static shim
            for (MethodNode mn : resNode.methods) {
                if ("<init>".equals(mn.name)) {
                    boolean hasInvokeSpecial = false;
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                            hasInvokeSpecial = true;
                        }
                        assertNotEquals(Opcodes.INVOKESTATIC, insn.getOpcode(),
                                "Constructor must never have INVOKESTATIC shim call in place of super() or this()");
                    }
                    assertTrue(hasInvokeSpecial, "Constructor must retain its INVOKESPECIAL");
                }
            }
        }

        @Test
        @DisplayName("1.3 Multiple sequential instantiations in the same method are independently stripped")
        public void testSequentialInstantiations() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode node = new ClassNode();
            node.version = Opcodes.V17;
            node.access = Opcodes.ACC_PUBLIC;
            node.name = "com/example/SequentialInstantiator";
            node.superName = "java/lang/Object";

            MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "createTwo", "()V", null, null);
            InsnList il = m.instructions;

            // First: new ResourceLocation("a", "1")
            il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/resources/ResourceLocation"));
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode("a"));
            il.add(new LdcInsnNode("1"));
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "net/minecraft/resources/ResourceLocation",
                    "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
            ));
            il.add(new VarInsnNode(Opcodes.ASTORE, 0));

            // Second: new ResourceLocation("b", "2")
            il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/resources/ResourceLocation"));
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode("b"));
            il.add(new LdcInsnNode("2"));
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "net/minecraft/resources/ResourceLocation",
                    "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
            ));
            il.add(new VarInsnNode(Opcodes.ASTORE, 1));
            il.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(m);

            ClassWriter cw = new ClassWriter(0);
            node.accept(cw);
            byte[] transformed = transformer.transform(node.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode res = new ClassNode();
            cr.accept(res, 0);

            MethodNode resM = res.methods.stream().filter(mn -> "createTwo".equals(mn.name)).findFirst().orElseThrow();

            int newCount = 0;
            int dupCount = 0;
            int staticCount = 0;

            for (AbstractInsnNode insn : resM.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.NEW) newCount++;
                if (insn.getOpcode() == Opcodes.DUP) dupCount++;
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) staticCount++;
            }

            assertEquals(0, newCount, "All NEW opcodes for polyfilled constructors must be stripped");
            assertEquals(0, dupCount, "All DUP opcodes for polyfilled constructors must be stripped");
            assertEquals(2, staticCount, "Both constructor calls must be converted to INVOKESTATIC");
        }
    }

    // =========================================================================
    // 2. BlockEntity Recursion Guard Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("2. BlockEntity Recursion Guard Stress Tests")
    class BlockEntityRecursionGuardStress {

        @Test
        @DisplayName("2.1 High Concurrency Stress: 16 threads performing 8,000 saves simultaneously")
        public void testConcurrentSavingStress() throws Exception {
            int threadCount = 16;
            int iterationsPerThread = 500;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    latch.await();
                    int successfulSaves = 0;
                    for (int i = 0; i < iterationsPerThread; i++) {
                        Object entity = new StressBlockEntity("Entity_" + threadId + "_" + i);
                        Object tag = new Object();
                        assertFalse(BlockEntityShim.isSaving(entity));
                        BlockEntityShim.save(entity, tag);
                        assertFalse(BlockEntityShim.isSaving(entity));
                        successfulSaves++;
                    }
                    return successfulSaves;
                }));
            }

            latch.countDown();
            int totalSaves = 0;
            for (Future<Integer> f : futures) {
                totalSaves += f.get(10, TimeUnit.SECONDS);
            }
            pool.shutdown();

            assertEquals(threadCount * iterationsPerThread, totalSaves);
        }

        @Test
        @DisplayName("2.2 Nested BlockEntities: Saving parent BE triggers saving child BE")
        public void testNestedBlockEntitySaving() {
            AtomicInteger parentSaveCount = new AtomicInteger(0);
            AtomicInteger childSaveCount = new AtomicInteger(0);

            Object child = new Object() {
                public void saveAdditional(Object tag) {
                    childSaveCount.incrementAndGet();
                }
            };

            Object parent = new Object() {
                public void saveAdditional(Object tag) {
                    parentSaveCount.incrementAndGet();
                    // Parent saves child during its own save
                    BlockEntityShim.save(child, tag);
                }
            };

            Object tag = new Object();
            BlockEntityShim.save(parent, tag);

            assertEquals(1, parentSaveCount.get(), "Parent must be saved once");
            assertEquals(1, childSaveCount.get(), "Child must be saved once during parent save");
            assertFalse(BlockEntityShim.isSaving(parent), "Parent saving state must be cleared");
            assertFalse(BlockEntityShim.isSaving(child), "Child saving state must be cleared");
        }

        @Test
        @DisplayName("2.3 Exception during save clears isSaving state cleanly (no state leakage)")
        public void testExceptionClearsSavingState() {
            Object faultBlockEntity = new Object() {
                public void saveAdditional(Object tag) {
                    throw new RuntimeException("Simulated IO failure in saveAdditional");
                }
            };

            assertDoesNotThrow(() -> {
                BlockEntityShim.save(faultBlockEntity, new Object());
            }, "BlockEntityShim.save must catch exceptions and not rethrow");

            assertFalse(BlockEntityShim.isSaving(faultBlockEntity),
                    "Saving flag must be cleaned up in finally block even if save throws exception");
        }

        @Test
        @DisplayName("2.4 Mutual recursion: Entity A calls save(B), Entity B calls save(A)")
        public void testMutualRecursionGuard() {
            AtomicInteger aCount = new AtomicInteger(0);
            AtomicInteger bCount = new AtomicInteger(0);

            class MutualEntity {
                MutualEntity partner;
                AtomicInteger counter;
                MutualEntity(AtomicInteger counter) { this.counter = counter; }

                public void saveAdditional(Object tag) {
                    counter.incrementAndGet();
                    if (partner != null) {
                        BlockEntityShim.save(partner, tag);
                    }
                }
            }

            MutualEntity a = new MutualEntity(aCount);
            MutualEntity b = new MutualEntity(bCount);
            a.partner = b;
            b.partner = a;

            assertDoesNotThrow(() -> {
                BlockEntityShim.save(a, new Object());
            }, "Mutual recursion between two block entities must terminate cleanly without stack overflow");

            assertEquals(1, aCount.get(), "Entity A should only be saved once");
            assertEquals(1, bCount.get(), "Entity B should only be saved once");
            assertFalse(BlockEntityShim.isSaving(a));
            assertFalse(BlockEntityShim.isSaving(b));
        }
    }

    // =========================================================================
    // 3. Dynamic Recipe Bridges & Method Descriptor Matching Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("3. Dynamic Recipe Bridges & Descriptors")
    class DynamicRecipeBridgesStress {

        @Test
        @DisplayName("3.1 Dynamic recipe bridges generation across legacy signatures (Container and CraftingContainer)")
        public void testDynamicRecipeBridgesInjection() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode recipeClass = new ClassNode();
            recipeClass.version = Opcodes.V17;
            recipeClass.access = Opcodes.ACC_PUBLIC;
            recipeClass.name = "com/example/FullRecipe";
            recipeClass.superName = "java/lang/Object";

            // assemble(Container, RegistryAccess)
            MethodNode assemble = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "assemble",
                    "(Lnet/minecraft/world/Container;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            assemble.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            assemble.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeClass.methods.add(assemble);

            // matches(Container, Level)
            MethodNode matches = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "matches",
                    "(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Z",
                    null,
                    null
            );
            matches.instructions.add(new InsnNode(Opcodes.ICONST_1));
            matches.instructions.add(new InsnNode(Opcodes.IRETURN));
            recipeClass.methods.add(matches);

            // getResultItem(RegistryAccess)
            MethodNode getResult = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "getResultItem",
                    "(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            getResult.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            getResult.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeClass.methods.add(getResult);

            // getRemainingItems(Container)
            MethodNode getRemaining = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "getRemainingItems",
                    "(Lnet/minecraft/world/Container;)Lnet/minecraft/core/NonNullList;",
                    null,
                    null
            );
            getRemaining.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            getRemaining.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeClass.methods.add(getRemaining);

            ClassWriter cw = new ClassWriter(0);
            recipeClass.accept(cw);
            byte[] transformed = transformer.transform(recipeClass.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode res = new ClassNode();
            cr.accept(res, 0);

            // Verify all modern synthetic bridges were injected
            boolean hasModernAssembleCrafting = res.methods.stream().anyMatch(m ->
                    "assemble".equals(m.name) && m.desc.contains("CraftingInput") && m.desc.contains("HolderLookup$Provider"));
            boolean hasModernAssembleRecipe = res.methods.stream().anyMatch(m ->
                    "assemble".equals(m.name) && m.desc.contains("RecipeInput") && m.desc.contains("HolderLookup$Provider"));
            boolean hasModernMatchesCrafting = res.methods.stream().anyMatch(m ->
                    "matches".equals(m.name) && m.desc.contains("CraftingInput"));
            boolean hasModernMatchesRecipe = res.methods.stream().anyMatch(m ->
                    "matches".equals(m.name) && m.desc.contains("RecipeInput"));
            boolean hasModernGetResult = res.methods.stream().anyMatch(m ->
                    "getResultItem".equals(m.name) && m.desc.contains("HolderLookup$Provider"));
            boolean hasModernGetRemaining = res.methods.stream().anyMatch(m ->
                    "getRemainingItems".equals(m.name) && m.desc.contains("CraftingInput"));

            assertTrue(hasModernAssembleCrafting, "Modern assemble(CraftingInput, HolderLookup.Provider) bridge must be present");
            assertTrue(hasModernAssembleRecipe, "Modern assemble(RecipeInput, HolderLookup.Provider) bridge must be present");
            assertTrue(hasModernMatchesCrafting, "Modern matches(CraftingInput, Level) bridge must be present");
            assertTrue(hasModernMatchesRecipe, "Modern matches(RecipeInput, Level) bridge must be present");
            assertTrue(hasModernGetResult, "Modern getResultItem(HolderLookup.Provider) bridge must be present");
            assertTrue(hasModernGetRemaining, "Modern getRemainingItems(CraftingInput) bridge must be present");
        }

        @Test
        @DisplayName("3.2 Recipe Provider buildRecipes(RecipeOutput) -> buildCraftingRecipes(Consumer) bridge")
        public void testRecipeProviderBridge() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode providerNode = new ClassNode();
            providerNode.version = Opcodes.V17;
            providerNode.access = Opcodes.ACC_PUBLIC;
            providerNode.name = "com/example/MyRecipeProvider";
            providerNode.superName = "java/lang/Object";

            MethodNode buildMethod = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "buildCraftingRecipes",
                    "(Ljava/util/function/Consumer;)V",
                    null,
                    null
            );
            buildMethod.instructions.add(new InsnNode(Opcodes.RETURN));
            providerNode.methods.add(buildMethod);

            ClassWriter cw = new ClassWriter(0);
            providerNode.accept(cw);
            byte[] transformed = transformer.transform(providerNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode res = new ClassNode();
            cr.accept(res, 0);

            boolean hasBuildRecipes = res.methods.stream().anyMatch(m ->
                    "buildRecipes".equals(m.name) && m.desc.contains("RecipeOutput"));
            assertTrue(hasBuildRecipes, "buildRecipes(RecipeOutput) bridge must be generated");
        }

        @Test
        @DisplayName("3.3 Transformer does not inject duplicate bridges if class already contains modern methods")
        public void testBridgeIdempotenceWhenModernMethodExists() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode recipeClass = new ClassNode();
            recipeClass.version = Opcodes.V17;
            recipeClass.access = Opcodes.ACC_PUBLIC;
            recipeClass.name = "com/example/AlreadyModernRecipe";
            recipeClass.superName = "java/lang/Object";

            // Legacy assemble(Container)
            MethodNode legacyAssemble = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "assemble",
                    "(Lnet/minecraft/world/Container;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            legacyAssemble.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            legacyAssemble.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeClass.methods.add(legacyAssemble);

            // Modern assemble(CraftingInput, HolderLookup.Provider) already explicitly defined
            MethodNode modernAssemble = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "assemble",
                    "(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            modernAssemble.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            modernAssemble.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeClass.methods.add(modernAssemble);

            ClassWriter cw = new ClassWriter(0);
            recipeClass.accept(cw);
            byte[] transformed = transformer.transform(recipeClass.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode res = new ClassNode();
            cr.accept(res, 0);

            long modernCraftingAssembleCount = res.methods.stream()
                    .filter(m -> "assemble".equals(m.name) && m.desc.contains("CraftingInput"))
                    .count();

            assertEquals(1, modernCraftingAssembleCount,
                    "Must NOT inject duplicate assemble(CraftingInput, Provider) bridge when one already exists");
        }

        @Test
        @DisplayName("3.4 RecipeShim wrapOutput adapts RecipeOutput into Consumer cleanly")
        public void testRecipeOutputAdaptation() {
            AtomicBoolean accepted = new AtomicBoolean(false);

            Consumer<Object> consumer = finishedRecipe -> {
                accepted.set(true);
            };

            Consumer<Object> adapted = RecipeShim.wrapOutput(consumer);
            assertNotNull(adapted);
            adapted.accept("dummyRecipe");
            assertTrue(accepted.get(), "Adapted consumer must forward to original consumer");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static class StressBlockEntity {
        private final String id;
        public StressBlockEntity(String id) { this.id = id; }
        public void saveAdditional(Object tag) {
            // Simulate normal save logic
        }
    }
}
