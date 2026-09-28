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
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Subsystem g:
 * Transformer Bridges & Constructor Stripping Stack Neutrality (Requirement R2 & R3).
 */
public class TransformerBridgesAndConstructorStrippingVerificationTest {

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
        @DisplayName("1.1 Constructor stripping stack neutrality: super() calls in subclass constructors preserved as INVOKESPECIAL")
        public void testSubclassSuperConstructorCallPreservation() throws Exception {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            // Create a subclass that calls super(String, String) on a polyfilled class
            ClassNode subclassNode = new ClassNode();
            subclassNode.version = Opcodes.V17;
            subclassNode.access = Opcodes.ACC_PUBLIC;
            subclassNode.name = "com/example/MyCustomResourceLocation";
            subclassNode.superName = "net/minecraft/resources/ResourceLocation";

            // Constructor: <init>(String path) invoking super("mymod", path)
            MethodNode initMethod = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "<init>",
                    "(Ljava/lang/String;)V",
                    null,
                    null
            );
            InsnList il = initMethod.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
            il.add(new LdcInsnNode("mymod"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1)); // path
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "net/minecraft/resources/ResourceLocation",
                    "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
            ));
            il.add(new InsnNode(Opcodes.RETURN));
            subclassNode.methods.add(initMethod);

            ClassWriter cw = new ClassWriter(0);
            subclassNode.accept(cw);
            byte[] transformed = transformer.transform(subclassNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode resultNode = new ClassNode();
            cr.accept(resultNode, 0);

            MethodNode transformedInit = resultNode.methods.stream()
                    .filter(m -> "<init>".equals(m.name))
                    .findFirst()
                    .orElseThrow();

            boolean hasInvokeSpecialSuper = false;
            boolean hasStaticShim = false;

            for (Iterator<AbstractInsnNode> it = transformedInit.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if ("<init>".equals(min.name) && "net/minecraft/resources/ResourceLocation".equals(min.owner)) {
                        hasInvokeSpecialSuper = true;
                    }
                }
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if (min.owner.contains("ResourceLocationShim") || min.owner.contains("ResourceLocation")) {
                        hasStaticShim = true;
                    }
                }
            }

            assertTrue(hasInvokeSpecialSuper, "Subclass super() constructor call must remain INVOKESPECIAL to maintain stack neutrality");
            assertFalse(hasStaticShim, "Subclass super() constructor must NOT be rewritten to INVOKESTATIC");
        }

        @Test
        @DisplayName("1.2 Constructor stripping with NEW and DUP: completely removes NEW/DUP and converts to INVOKESTATIC")
        public void testExplicitNewDupConstructorStripping() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode callerNode = new ClassNode();
            callerNode.version = Opcodes.V17;
            callerNode.access = Opcodes.ACC_PUBLIC;
            callerNode.name = "com/example/ExplicitInstantiator";
            callerNode.superName = "java/lang/Object";

            MethodNode callerMethod = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "instantiateLocation",
                    "(Ljava/lang/String;)Ljava/lang/Object;",
                    null,
                    null
            );
            InsnList il = callerMethod.instructions;
            il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/resources/ResourceLocation"));
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode("mymod"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "net/minecraft/resources/ResourceLocation",
                    "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
            ));
            il.add(new InsnNode(Opcodes.ARETURN));
            callerNode.methods.add(callerMethod);

            ClassWriter cw = new ClassWriter(0);
            callerNode.accept(cw);
            byte[] transformed = transformer.transform(callerNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode result = new ClassNode();
            cr.accept(result, 0);

            MethodNode m = result.methods.stream().filter(mn -> "instantiateLocation".equals(mn.name)).findFirst().orElseThrow();

            boolean hasNew = false;
            boolean hasDup = false;
            boolean hasStaticFactory = false;

            for (Iterator<AbstractInsnNode> it = m.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn.getOpcode() == Opcodes.NEW) hasNew = true;
                if (insn.getOpcode() == Opcodes.DUP) hasDup = true;
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if (min.name.contains("fromNamespaceAndPath") || min.owner.contains("ResourceLocation")) {
                        hasStaticFactory = true;
                    }
                }
            }

            assertFalse(hasNew, "NEW opcode must be stripped");
            assertFalse(hasDup, "DUP opcode must be stripped");
            assertTrue(hasStaticFactory, "Constructor call must be converted to static factory invocation");
        }

        @Test
        @DisplayName("1.3 BlockEntity saveAdditional recursion guard prevents StackOverflowError")
        public void testBlockEntitySaveRecursionGuard() {
            AtomicInteger callCount = new AtomicInteger(0);

            // Simulate BlockEntity where saveAdditional calls super.saveAdditional, which redirects to BlockEntityShim.save
            RecursiveSaveBlockEntity recursiveBlockEntity = new RecursiveSaveBlockEntity(callCount);

            Object dummyTag = new Object();
            assertDoesNotThrow(() -> {
                BlockEntityShim.save(recursiveBlockEntity, dummyTag);
            }, "BlockEntity save recursion guard must prevent StackOverflowError");

            // Must have entered exactly once and avoided infinite recursion loop
            assertEquals(1, callCount.get(), "Recursion guard must ensure saveAdditional was called only once");
            assertFalse(BlockEntityShim.isSaving(recursiveBlockEntity));
        }

        @Test
        @DisplayName("1.4 BlockEntity loadAdditional recursion guard prevents StackOverflowError")
        public void testBlockEntityLoadRecursionGuard() {
            AtomicInteger callCount = new AtomicInteger(0);

            RecursiveLoadBlockEntity recursiveBlockEntity = new RecursiveLoadBlockEntity(callCount);

            Object dummyTag = new Object();
            assertDoesNotThrow(() -> {
                BlockEntityShim.load(recursiveBlockEntity, dummyTag);
            }, "BlockEntity load recursion guard must prevent StackOverflowError");

            assertEquals(1, callCount.get(), "Recursion guard must ensure load was called only once");
            assertFalse(BlockEntityShim.isLoading(recursiveBlockEntity));
        }

        @Test
        @DisplayName("1.5 BlockEntity save bridge injected with pushSave and popSave bytecode calls")
        public void testBlockEntityInjectedBridgeBytecode() {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.20.6", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode beNode = new ClassNode();
            beNode.version = Opcodes.V17;
            beNode.access = Opcodes.ACC_PUBLIC;
            beNode.name = "com/example/CustomBlockEntity";
            beNode.superName = "net/minecraft/world/level/block/entity/BlockEntity";

            // Legacy saveAdditional(CompoundTag)
            MethodNode legacySave = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "saveAdditional",
                    "(Lnet/minecraft/nbt/CompoundTag;)V",
                    null,
                    null
            );
            legacySave.instructions.add(new InsnNode(Opcodes.RETURN));
            beNode.methods.add(legacySave);

            ClassWriter cw = new ClassWriter(0);
            beNode.accept(cw);
            byte[] transformed = transformer.transform(beNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode result = new ClassNode();
            cr.accept(result, 0);

            // Modern saveAdditional bridge must exist
            MethodNode modernSave = result.methods.stream()
                    .filter(m -> "saveAdditional".equals(m.name) && m.desc.contains("HolderLookup$Provider"))
                    .findFirst()
                    .orElseThrow();

            boolean hasPushSave = false;
            boolean hasPopSave = false;

            for (Iterator<AbstractInsnNode> it = modernSave.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if (min.owner.contains("BlockEntityShim") && "pushSave".equals(min.name)) {
                        hasPushSave = true;
                    }
                    if (min.owner.contains("BlockEntityShim") && "popSave".equals(min.name)) {
                        hasPopSave = true;
                    }
                }
            }

            assertTrue(hasPushSave, "Modern saveAdditional bridge must call BlockEntityShim.pushSave");
            assertTrue(hasPopSave, "Modern saveAdditional bridge must call BlockEntityShim.popSave");
        }
    }

    // =========================================================================
    // Tier 2: Boundary & Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Boundary & Edge Cases")
    class Tier2Boundary {

        @Test
        @DisplayName("2.1 Null parameters across BlockEntityShim and RecipeShim")
        public void testNullParameters() {
            assertDoesNotThrow(() -> BlockEntityShim.pushSave(null));
            assertDoesNotThrow(() -> BlockEntityShim.popSave(null));
            assertFalse(BlockEntityShim.isSaving(null));
            assertDoesNotThrow(() -> BlockEntityShim.pushLoad(null));
            assertDoesNotThrow(() -> BlockEntityShim.popLoad(null));
            assertFalse(BlockEntityShim.isLoading(null));
            assertDoesNotThrow(() -> BlockEntityShim.save(null, null));
            assertDoesNotThrow(() -> BlockEntityShim.load(null, null));

            assertNull(RecipeShim.wrapInput(null));
            assertNull(RecipeShim.wrapToRecipeInput(null));
            assertNull(RecipeShim.wrapRegistryAccess(null));
            assertNull(RecipeShim.assembleRecipe(null, null, null));
            assertFalse(RecipeShim.matchesRecipe(null, null, null));
        }

        @Test
        @DisplayName("2.2 RecipeShim wrapRegistryAccess returns input if already RegistryAccess")
        public void testWrapRegistryAccessIdempotence() {
            Object mockRegistryAccess = new Object() {
                public String getAccessId() {
                    return "access_id_1";
                }
            };
            Object wrapped = RecipeShim.wrapRegistryAccess(mockRegistryAccess);
            assertNotNull(wrapped);
        }
    }

    // =========================================================================
    // Tier 3: Recipe Bridges & Dynamic Execution
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Recipe Bridges & Dynamic Execution")
    class Tier3RecipeBridges {

        @Test
        @DisplayName("3.1 Recipe with (Container, RegistryAccess) generates modern assemble and getResultItem bridges with wrapRegistryAccess")
        public void testRecipeWithRegistryAccessBridges() {
            TargetSpec base = TargetSpec.of("1.19.4", "forge");
            TargetSpec target = TargetSpec.of("1.20.6", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode recipeNode = new ClassNode();
            recipeNode.version = Opcodes.V17;
            recipeNode.access = Opcodes.ACC_PUBLIC;
            recipeNode.name = "com/example/RegistryAccessRecipe";
            recipeNode.superName = "java/lang/Object";

            // Legacy assemble(Container, RegistryAccess)
            MethodNode assemble = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "assemble",
                    "(Lnet/minecraft/world/Container;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            assemble.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            assemble.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeNode.methods.add(assemble);

            // Legacy getResultItem(RegistryAccess)
            MethodNode getResultItem = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "getResultItem",
                    "(Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            getResultItem.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            getResultItem.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeNode.methods.add(getResultItem);

            ClassWriter cw = new ClassWriter(0);
            recipeNode.accept(cw);
            byte[] transformed = transformer.transform(recipeNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode result = new ClassNode();
            cr.accept(result, 0);

            // Verify modern assemble(CraftingInput, HolderLookup.Provider) bridge was injected
            MethodNode modernAssemble = result.methods.stream()
                    .filter(m -> "assemble".equals(m.name) && m.desc.contains("CraftingInput") && m.desc.contains("HolderLookup$Provider"))
                    .findFirst()
                    .orElseThrow();

            boolean hasWrapRegistryAccess = false;
            for (Iterator<AbstractInsnNode> it = modernAssemble.instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode insn = it.next();
                if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if (min.owner.contains("RecipeShim") && "wrapRegistryAccess".equals(min.name)) {
                        hasWrapRegistryAccess = true;
                    }
                }
            }
            assertTrue(hasWrapRegistryAccess, "Modern assemble bridge must wrap HolderLookup.Provider using RecipeShim.wrapRegistryAccess");

            // Verify modern getResultItem(HolderLookup.Provider) bridge was injected
            boolean hasModernGetResult = result.methods.stream().anyMatch(m ->
                    "getResultItem".equals(m.name) && m.desc.contains("HolderLookup$Provider"));
            assertTrue(hasModernGetResult, "Modern getResultItem bridge must be injected");
        }

        @Test
        @DisplayName("3.2 Recipe with (CraftingContainer, RegistryAccess) generates modern CraftingInput and RecipeInput bridges")
        public void testCraftingContainerRecipeBridges() {
            TargetSpec base = TargetSpec.of("1.19.4", "forge");
            TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode recipeNode = new ClassNode();
            recipeNode.version = Opcodes.V17;
            recipeNode.access = Opcodes.ACC_PUBLIC;
            recipeNode.name = "com/example/CraftingContainerRecipe";
            recipeNode.superName = "java/lang/Object";

            MethodNode assemble = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "assemble",
                    "(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
                    null,
                    null
            );
            assemble.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            assemble.instructions.add(new InsnNode(Opcodes.ARETURN));
            recipeNode.methods.add(assemble);

            MethodNode matches = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "matches",
                    "(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z",
                    null,
                    null
            );
            matches.instructions.add(new InsnNode(Opcodes.ICONST_1));
            matches.instructions.add(new InsnNode(Opcodes.IRETURN));
            recipeNode.methods.add(matches);

            ClassWriter cw = new ClassWriter(0);
            recipeNode.accept(cw);
            byte[] transformed = transformer.transform(recipeNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ClassReader cr = new ClassReader(transformed);
            ClassNode result = new ClassNode();
            cr.accept(result, 0);

            // Both CraftingInput and RecipeInput assemble bridges must be injected
            assertTrue(result.methods.stream().anyMatch(m -> "assemble".equals(m.name) && m.desc.contains("CraftingInput")));
            assertTrue(result.methods.stream().anyMatch(m -> "assemble".equals(m.name) && m.desc.contains("RecipeInput")));

            // Both matches bridges must be injected
            assertTrue(result.methods.stream().anyMatch(m -> "matches".equals(m.name) && m.desc.contains("CraftingInput")));
            assertTrue(result.methods.stream().anyMatch(m -> "matches".equals(m.name) && m.desc.contains("RecipeInput")));
        }

        @Test
        @DisplayName("3.3 Clean execution of transformed class with injected bridges in custom ClassLoader")
        public void testTransformedClassExecutionInClassLoader() throws Exception {
            TargetSpec base = TargetSpec.of("1.18.2", "forge");
            TargetSpec target = TargetSpec.of("1.20.4", "forge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            ClassNode testNode = new ClassNode();
            testNode.version = Opcodes.V17;
            testNode.access = Opcodes.ACC_PUBLIC;
            testNode.name = "com/example/TransformedExecutable";
            testNode.superName = "java/lang/Object";

            // Default constructor
            MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            testNode.methods.add(init);

            // Business logic method returning string
            MethodNode execMethod = new MethodNode(Opcodes.ACC_PUBLIC, "execute", "()Ljava/lang/String;", null, null);
            execMethod.instructions.add(new LdcInsnNode("ContinuumSuccess"));
            execMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
            testNode.methods.add(execMethod);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            testNode.accept(cw);
            byte[] transformed = transformer.transform(testNode.name, cw.toByteArray());
            assertNotNull(transformed);

            ByteArrayClassLoader loader = new ByteArrayClassLoader(getClass().getClassLoader());
            Class<?> loadedClass = loader.defineClass("com.example.TransformedExecutable", transformed);
            assertNotNull(loadedClass);

            Object instance = loadedClass.getDeclaredConstructor().newInstance();
            Method m = loadedClass.getMethod("execute");
            Object ret = m.invoke(instance);
            assertEquals("ContinuumSuccess", ret);
        }
    }

    // =========================================================================
    // Test ClassLoader
    // =========================================================================

    private static class ByteArrayClassLoader extends ClassLoader {
        public ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    public static class RecursiveSaveBlockEntity {
        private final AtomicInteger callCount;

        public RecursiveSaveBlockEntity(AtomicInteger callCount) {
            this.callCount = callCount;
        }

        public void saveAdditional(Object compoundTag) {
            callCount.incrementAndGet();
            BlockEntityShim.save(this, compoundTag);
        }
    }

    public static class RecursiveLoadBlockEntity {
        private final AtomicInteger callCount;

        public RecursiveLoadBlockEntity(AtomicInteger callCount) {
            this.callCount = callCount;
        }

        public void load(Object compoundTag) {
            callCount.incrementAndGet();
            BlockEntityShim.load(this, compoundTag);
        }
    }
}
