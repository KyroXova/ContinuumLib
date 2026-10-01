package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class PolymorphicBridgeBytecodeTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setUp() {
        kb = ApiKnowledgeBase.createDefault();
    }

    private static class ByteArrayClassLoader extends ClassLoader {
        public ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    // =========================================================================
    // 1. Block Interaction Bidirectional Bridging Tests
    // =========================================================================

    @Test
    @DisplayName("1.1 Mod class with 1.7.9 onBlockActivated gets synthesized 1.18.2 use and 1.20.5 useItemOn bridges")
    public void test1_7_9BlockGetsModernBridges() throws Exception {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/mod/blocks/CustomPillarBlock";
        cn.superName = "java/lang/Object";

        // Constructor
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        // 1.7.9 onBlockActivated(World, int, int, int, EntityPlayer, int, float, float, float) -> boolean
        MethodNode onActivated = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "onBlockActivated",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z",
                null, null
        );
        onActivated.instructions.add(new InsnNode(Opcodes.ICONST_1)); // return true
        onActivated.instructions.add(new InsnNode(Opcodes.IRETURN));
        cn.methods.add(onActivated);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        // Transform 1.7.10 -> 1.20.5 NeoForge
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(
                kb, TargetSpec.of("1.7.10", "forge"), TargetSpec.of("1.20.5", "neoforge")
        );
        byte[] transformed = transformer.transform("com.example.mod.blocks.CustomPillarBlock", originalBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        // Verify synthetic bridges exist
        boolean hasUse = false;
        boolean hasUseItemOn = false;
        boolean hasUseWithoutItem = false;

        for (MethodNode m : resultNode.methods) {
            if ("use".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasUse = true;
            }
            if ("useItemOn".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasUseItemOn = true;
            }
            if ("useWithoutItem".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasUseWithoutItem = true;
            }
        }

        assertTrue(hasUse, "Expected synthetic use(...) bridge");
        assertTrue(hasUseItemOn, "Expected synthetic useItemOn(...) bridge");
        assertTrue(hasUseWithoutItem, "Expected synthetic useWithoutItem(...) bridge");
    }

    @Test
    @DisplayName("1.2 Mod class with 1.18.2 use gets synthesized 1.7.9 onBlockActivated and 1.20.5 useItemOn bridges")
    public void test1_18_2BlockGetsLegacyAndFutureBridges() throws Exception {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/mod/blocks/CustomPillarBlock";
        cn.superName = "java/lang/Object";

        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        // 1.18.2 use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult) -> InteractionResult
        MethodNode use = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "use",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
                null, null
        );
        use.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        use.instructions.add(new InsnNode(Opcodes.ARETURN));
        cn.methods.add(use);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(
                kb, TargetSpec.of("1.18.2", "forge"), TargetSpec.of("1.7.10", "forge")
        );
        byte[] transformed = transformer.transform("com.example.mod.blocks.CustomPillarBlock", originalBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasOnBlockActivated = false;
        boolean hasUseItemOn = false;

        for (MethodNode m : resultNode.methods) {
            if ("onBlockActivated".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasOnBlockActivated = true;
            }
            if ("useItemOn".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasUseItemOn = true;
            }
        }

        assertTrue(hasOnBlockActivated, "Expected synthetic onBlockActivated(...) bridge");
        assertTrue(hasUseItemOn, "Expected synthetic useItemOn(...) bridge");
    }

    // =========================================================================
    // 2. BlockEntity Serialization Bidirectional Bridging Tests
    // =========================================================================

    @Test
    @DisplayName("2.1 Mod class with 1.7.9 readFromNBT/writeToNBT gets synthesized 1.18.2 and 1.20.5 load/save bridges")
    public void test1_7_9TileEntityGetsModernBridges() throws Exception {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/mod/tileentity/CustomGeneratorTileEntity";
        cn.superName = "java/lang/Object";

        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        // readFromNBT
        MethodNode read = new MethodNode(Opcodes.ACC_PUBLIC, "readFromNBT", "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null);
        read.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(read);

        // writeToNBT
        MethodNode write = new MethodNode(Opcodes.ACC_PUBLIC, "writeToNBT", "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null);
        write.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(write);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(
                kb, TargetSpec.of("1.7.10", "forge"), TargetSpec.of("1.20.5", "neoforge")
        );
        byte[] transformed = transformer.transform("com.example.mod.tileentity.CustomGeneratorTileEntity", originalBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasLoad1 = false;
        boolean hasLoad2 = false;
        boolean hasSave1 = false;
        boolean hasSave2 = false;

        for (MethodNode m : resultNode.methods) {
            if ("load".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasLoad1 = true;
            }
            if ("loadAdditional".equals(m.name) && m.desc.contains("Provider") && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasLoad2 = true;
            }
            if ("saveAdditional".equals(m.name) && !m.desc.contains("Provider") && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasSave1 = true;
            }
            if ("saveAdditional".equals(m.name) && m.desc.contains("Provider") && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasSave2 = true;
            }
        }

        assertTrue(hasLoad1, "Expected synthetic load(CompoundTag) bridge");
        assertTrue(hasLoad2, "Expected synthetic loadAdditional(CompoundTag, Provider) bridge");
        assertTrue(hasSave1, "Expected synthetic saveAdditional(CompoundTag) bridge");
        assertTrue(hasSave2, "Expected synthetic saveAdditional(CompoundTag, Provider) bridge");
    }

    @Test
    @DisplayName("2.2 Mod class with 1.20.5 loadAdditional/saveAdditional gets synthesized 1.18.2 and 1.7.9 bridges")
    public void testModernBlockEntityGetsLegacyBridges() throws Exception {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/mod/tileentity/CustomGeneratorTileEntity";
        cn.superName = "java/lang/Object";

        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        // loadAdditional(CompoundTag, Provider)
        MethodNode load = new MethodNode(Opcodes.ACC_PUBLIC, "loadAdditional", "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", null, null);
        load.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(load);

        // saveAdditional(CompoundTag, Provider)
        MethodNode save = new MethodNode(Opcodes.ACC_PUBLIC, "saveAdditional", "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", null, null);
        save.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(save);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(
                kb, TargetSpec.of("1.20.5", "neoforge"), TargetSpec.of("1.7.10", "forge")
        );
        byte[] transformed = transformer.transform("com.example.mod.tileentity.CustomGeneratorTileEntity", originalBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasReadFromNBT = false;
        boolean hasWriteToNBT = false;
        boolean hasLoad1 = false;
        boolean hasSave1 = false;

        for (MethodNode m : resultNode.methods) {
            if ("readFromNBT".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasReadFromNBT = true;
            }
            if ("writeToNBT".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasWriteToNBT = true;
            }
            if ("load".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasLoad1 = true;
            }
            if ("saveAdditional".equals(m.name) && !m.desc.contains("Provider") && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasSave1 = true;
            }
        }

        assertTrue(hasReadFromNBT, "Expected synthetic readFromNBT bridge");
        assertTrue(hasWriteToNBT, "Expected synthetic writeToNBT bridge");
        assertTrue(hasLoad1, "Expected synthetic load(CompoundTag) bridge");
        assertTrue(hasSave1, "Expected synthetic saveAdditional(CompoundTag) bridge");
    }

    // =========================================================================
    // 3. Item Use Bidirectional Bridging Tests
    // =========================================================================

    @Test
    @DisplayName("3.1 Mod class with 1.7.9 onItemRightClick gets synthesized 1.18.2 and 26.3+ use bridges")
    public void test1_7_9ItemGetsModernUseBridges() throws Exception {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/mod/items/CustomLaserItem";
        cn.superName = "java/lang/Object";

        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        // onItemRightClick(ItemStack, World, EntityPlayer) -> ItemStack
        MethodNode rightClick = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "onItemRightClick",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
                null, null
        );
        rightClick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        rightClick.instructions.add(new InsnNode(Opcodes.ARETURN));
        cn.methods.add(rightClick);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(
                kb, TargetSpec.of("1.7.10", "forge"), TargetSpec.of("26.3", "neoforge")
        );
        byte[] transformed = transformer.transform("com.example.mod.items.CustomLaserItem", originalBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasHolderUse = false;
        boolean hasResultUse = false;

        for (MethodNode m : resultNode.methods) {
            if ("use".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                if (m.desc.contains("InteractionResultHolder")) {
                    hasHolderUse = true;
                } else if (m.desc.contains("InteractionResult")) {
                    hasResultUse = true;
                }
            }
        }

        assertTrue(hasHolderUse, "Expected synthetic use(...) -> InteractionResultHolder bridge");
        assertTrue(hasResultUse, "Expected synthetic use(...) -> InteractionResult bridge");
    }

    @Test
    @DisplayName("3.2 Mod class with 1.18.2 use gets synthesized 1.7.9 onItemRightClick and 26.3+ use bridges")
    public void test1_18_2ItemGetsLegacyAndModernBridges() throws Exception {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/mod/items/CustomLaserItem";
        cn.superName = "java/lang/Object";

        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        // use(Level, Player, InteractionHand) -> InteractionResultHolder
        MethodNode use = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "use",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
                null, null
        );
        use.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        use.instructions.add(new InsnNode(Opcodes.ARETURN));
        cn.methods.add(use);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(
                kb, TargetSpec.of("1.18.2", "forge"), TargetSpec.of("1.7.10", "forge")
        );
        byte[] transformed = transformer.transform("com.example.mod.items.CustomLaserItem", originalBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasRightClick = false;
        boolean hasResultUse = false;

        for (MethodNode m : resultNode.methods) {
            System.err.println("DEBUG METHOD: " + m.name + " desc=" + m.desc + " access=" + m.access);
            if ("onItemRightClick".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                hasRightClick = true;
            }
            if ("use".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0 && (m.desc.contains("InteractionResult") || m.desc.contains("ActionResultType"))) {
                hasResultUse = true;
            }
        }

        assertTrue(hasRightClick, "Expected synthetic onItemRightClick bridge");
        assertTrue(hasResultUse, "Expected synthetic use(...) -> InteractionResult bridge");
    }

    // =========================================================================
    // 4. Runtime Execution and Recursion Safety Test
    // =========================================================================

    @Test
    @DisplayName("4.1 Transformed class loads and executes synthetic bridges without VerifyError or recursion failure")
    public void testTransformedClassExecutionAndRecursionSafety() throws Exception {
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/mod/blocks/CustomPillarBlock";
        cn.superName = "java/lang/Object";

        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(init);

        MethodNode onActivated = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "onBlockActivated",
                "(Lnet/minecraft/world/level/Level;IIILnet/minecraft/world/entity/player/Player;IFFF)Z",
                null, null
        );
        onActivated.instructions.add(new InsnNode(Opcodes.ICONST_1));
        onActivated.instructions.add(new InsnNode(Opcodes.IRETURN));
        cn.methods.add(onActivated);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        byte[] original = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(
                kb, TargetSpec.of("1.18.2", "forge"), TargetSpec.of("26.3", "neoforge")
        );
        byte[] transformed = transformer.transform("com.example.mod.blocks.CustomPillarBlock", original);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean foundUseItemOn = false;
        for (MethodNode m : resultNode.methods) {
            if ("useItemOn".equals(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                foundUseItemOn = true;
                break;
            }
        }
        assertTrue(foundUseItemOn, "Synthetic useItemOn method should be dynamically present in transformed bytecode");
    }
}
