package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirmer verification test suite for Milestone 2:
 * Synthetic Bridge Methods & Bytecode Injections (R2) in ContinuumBytecodeTransformer.
 */
public class Milestone2BytecodeTransformerVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    @Test
    @DisplayName("1. Static field-to-method polyfill rewriting (DamageSource.GENERIC -> DamageSourceShim.generic())")
    public void testStaticFieldToMethodPolyfillRewriting() {
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.4", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        ClassNode callerNode = new ClassNode();
        callerNode.version = Opcodes.V17;
        callerNode.access = Opcodes.ACC_PUBLIC;
        callerNode.name = "com/example/DamageCaller";
        callerNode.superName = "java/lang/Object";

        MethodNode testMethod = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getGenericDamage",
                "()Ljava/lang/Object;",
                null,
                null
        );
        testMethod.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "net/minecraft/world/damagesource/DamageSource",
                "GENERIC",
                "Lnet/minecraft/world/damagesource/DamageSource;"
        ));
        testMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
        callerNode.methods.add(testMethod);

        ClassWriter cw = new ClassWriter(0);
        callerNode.accept(cw);
        byte[] transformed = transformer.transform(callerNode.name, cw.toByteArray());
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        MethodNode m = result.methods.stream()
                .filter(mn -> "getGenericDamage".equals(mn.name))
                .findFirst()
                .orElseThrow();

        boolean hasFieldAccess = false;
        boolean hasShimMethodCall = false;

        for (Iterator<AbstractInsnNode> it = m.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insn = it.next();
            if (insn.getOpcode() == Opcodes.GETSTATIC) {
                FieldInsnNode fn = (FieldInsnNode) insn;
                if ("GENERIC".equals(fn.name)) {
                    hasFieldAccess = true;
                }
            }
            if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                MethodInsnNode mn = (MethodInsnNode) insn;
                if (mn.owner.contains("DamageSourceShim") && "generic".equalsIgnoreCase(mn.name)) {
                    hasShimMethodCall = true;
                }
            }
        }

        assertFalse(hasFieldAccess, "GETSTATIC DamageSource.GENERIC must be rewritten");
        assertTrue(hasShimMethodCall, "Must invoke DamageSourceShim.generic() or equivalent polyfill");
    }

    @Test
    @DisplayName("2. Version-dependent useItemOn return type: ItemInteractionResult (1.20.6/1.21.1) vs InteractionResult (1.21.2+ / 26.3+)")
    public void testVersionDependentUseItemOnReturnType() {
        TargetSpec base = TargetSpec.of("1.18.2", "forge");

        // Synthesize a Block class with legacy use()
        ClassNode blockNode = new ClassNode();
        blockNode.version = Opcodes.V17;
        blockNode.access = Opcodes.ACC_PUBLIC;
        blockNode.name = "com/example/CustomInteractiveBlock";
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

        ClassWriter cw = new ClassWriter(0);
        blockNode.accept(cw);
        byte[] legacyBytes = cw.toByteArray();

        // 1.20.6 / 1.21.1 should inject useItemOn returning ItemInteractionResult
        TargetSpec target1206 = TargetSpec.of("1.20.6", "neoforge");
        ContinuumBytecodeTransformer transformer1206 = new ContinuumBytecodeTransformer(kb, base, target1206);
        byte[] transformed1206 = transformer1206.transform(blockNode.name, legacyBytes);
        assertNotNull(transformed1206);

        ClassReader cr1206 = new ClassReader(transformed1206);
        ClassNode node1206 = new ClassNode();
        cr1206.accept(node1206, 0);

        boolean hasItemInteractionResultBridge = node1206.methods.stream().anyMatch(m ->
                "useItemOn".equals(m.name) && m.desc.contains("ItemInteractionResult"));
        assertTrue(hasItemInteractionResultBridge, "1.20.6 target must generate useItemOn returning ItemInteractionResult");

        // 1.21.2+ / 26.3+ should inject useItemOn returning InteractionResult
        TargetSpec target263 = TargetSpec.of("26.3", "neoforge");
        ContinuumBytecodeTransformer transformer263 = new ContinuumBytecodeTransformer(kb, base, target263);
        byte[] transformed263 = transformer263.transform(blockNode.name, legacyBytes);
        assertNotNull(transformed263);

        ClassReader cr263 = new ClassReader(transformed263);
        ClassNode node263 = new ClassNode();
        cr263.accept(node263, 0);

        boolean hasInteractionResultBridge = node263.methods.stream().anyMatch(m ->
                "useItemOn".equals(m.name) && m.desc.endsWith(")Lnet/minecraft/world/InteractionResult;"));
        assertTrue(hasInteractionResultBridge, "26.3+ target must generate useItemOn returning InteractionResult");
    }

    @Test
    @DisplayName("3. RecipeProvider synthetic bridge: buildCraftingRecipes -> buildRecipes(RecipeOutput) on >= 1.20.5")
    public void testRecipeProviderSyntheticBridge() {
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.6", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        ClassNode providerNode = new ClassNode();
        providerNode.version = Opcodes.V17;
        providerNode.access = Opcodes.ACC_PUBLIC;
        providerNode.name = "com/example/MyRecipeProvider";
        providerNode.superName = "net/minecraft/data/recipes/RecipeProvider";

        MethodNode buildMethod = new MethodNode(
                Opcodes.ACC_PROTECTED,
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
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        boolean hasBuildRecipes = result.methods.stream().anyMatch(m ->
                "buildRecipes".equals(m.name) && m.desc.contains("RecipeOutput"));
        assertTrue(hasBuildRecipes, "RecipeProvider must have synthetic buildRecipes(RecipeOutput) on 1.20.5+");
    }

    @Test
    @DisplayName("4. General constructor polyfill stripping (NEW and DUP opcodes removed)")
    public void testConstructorPolyfillStripping() {
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        ClassNode callerNode = new ClassNode();
        callerNode.version = Opcodes.V17;
        callerNode.access = Opcodes.ACC_PUBLIC;
        callerNode.name = "com/example/ResourceCaller";
        callerNode.superName = "java/lang/Object";

        MethodNode testMethod = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "createLocation",
                "()Ljava/lang/Object;",
                null,
                null
        );
        testMethod.instructions.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/resources/ResourceLocation"));
        testMethod.instructions.add(new InsnNode(Opcodes.DUP));
        testMethod.instructions.add(new LdcInsnNode("mymod"));
        testMethod.instructions.add(new LdcInsnNode("myitem"));
        testMethod.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "net/minecraft/resources/ResourceLocation",
                "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false
        ));
        testMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
        callerNode.methods.add(testMethod);

        ClassWriter cw = new ClassWriter(0);
        callerNode.accept(cw);
        byte[] transformed = transformer.transform(callerNode.name, cw.toByteArray());
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        MethodNode m = result.methods.stream()
                .filter(mn -> "createLocation".equals(mn.name))
                .findFirst()
                .orElseThrow();

        boolean hasNew = false;
        boolean hasDup = false;
        boolean hasStaticPolyfill = false;

        for (Iterator<AbstractInsnNode> it = m.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insn = it.next();
            if (insn.getOpcode() == Opcodes.NEW) {
                TypeInsnNode tn = (TypeInsnNode) insn;
                if ("net/minecraft/resources/ResourceLocation".equals(tn.desc)) {
                    hasNew = true;
                }
            }
            if (insn.getOpcode() == Opcodes.DUP) {
                hasDup = true;
            }
            if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                MethodInsnNode min = (MethodInsnNode) insn;
                if (min.owner.contains("ResourceLocation") && min.name.contains("fromNamespaceAndPath")) {
                    hasStaticPolyfill = true;
                }
            }
        }

        assertFalse(hasNew, "NEW opcode for polyfilled constructor must be eliminated");
        assertFalse(hasDup, "DUP opcode for polyfilled constructor must be eliminated");
        assertTrue(hasStaticPolyfill, "Constructor invocation must be replaced by static factory method");
    }

    @Test
    @DisplayName("5. Clean execution of transformed classes via custom ClassLoader without NoSuchMethodError, VerifyError, or AbstractMethodError")
    public void testCleanClassLoaderExecution() throws Exception {
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.4", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        ClassNode executableNode = new ClassNode();
        executableNode.version = Opcodes.V17;
        executableNode.access = Opcodes.ACC_PUBLIC;
        executableNode.name = "com/example/ExecutableClass";
        executableNode.superName = "java/lang/Object";

        // Default constructor
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        executableNode.methods.add(init);

        // Compute method returning integer
        MethodNode compute = new MethodNode(Opcodes.ACC_PUBLIC, "compute", "()I", null, null);
        compute.instructions.add(new InsnNode(Opcodes.ICONST_5));
        compute.instructions.add(new InsnNode(Opcodes.IRETURN));
        executableNode.methods.add(compute);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        executableNode.accept(cw);
        byte[] transformed = transformer.transform(executableNode.name, cw.toByteArray());
        assertNotNull(transformed);

        // Load into dynamic ClassLoader
        ByteArrayClassLoader loader = new ByteArrayClassLoader(getClass().getClassLoader());
        Class<?> clazz = loader.defineClass("com.example.ExecutableClass", transformed);
        assertNotNull(clazz);

        Object instance = clazz.getDeclaredConstructor().newInstance();
        Method method = clazz.getMethod("compute");
        Object result = method.invoke(instance);
        assertEquals(5, result, "Method compute must return 5 without linkage or verification error");
    }

    private static class ByteArrayClassLoader extends ClassLoader {
        public ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}
