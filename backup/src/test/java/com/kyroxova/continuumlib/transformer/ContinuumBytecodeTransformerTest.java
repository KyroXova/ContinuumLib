package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

public class ContinuumBytecodeTransformerTest {

    @Test
    public void testBytecodeTransformationForNeoForgeAndMaterialPolyfill() {
        // 1. Synthesize a mock 1.18.2 Forge class: com/example/TestModBlocks
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/TestModBlocks";
        cn.superName = "java/lang/Object";

        // Field: public static RegistryObject<Block> MY_BLOCK
        FieldNode fn = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "MY_BLOCK",
                "Lnet/minecraftforge/registries/RegistryObject;",
                null, null);
        cn.fields.add(fn);

        // Method: public static void init()
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "init", "()V", null, null);

        // Call: BlockBehaviour.Properties.of(Material.WOOD)
        mn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/material/Material", "WOOD", "Lnet/minecraft/world/level/material/Material;"));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/world/level/block/state/BlockBehaviour$Properties",
                "of",
                "(Lnet/minecraft/world/level/material/Material;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;",
                false));

        // Call: itemProps.tab(BUILDSCAPE_TAB)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/Item$Properties",
                "tab",
                "(Lnet/minecraft/world/item/CreativeModeTab;)Lnet/minecraft/world/item/Item$Properties;",
                false));

        mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        // 2. Set up Transformer: 1.18.2 Forge -> 1.20.4 NeoForge
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.4", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        // 3. Transform bytecode
        byte[] transformed = transformer.transform("com/example/TestModBlocks", originalBytecode);
        assertNotNull(transformed);
        assertNotEquals(originalBytecode.length, transformed.length);

        // 4. Inspect transformed class
        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        // Verify Field redirected: RegistryObject -> DeferredHolder
        FieldNode transformedField = resultNode.fields.get(0);
        assertEquals("Lnet/neoforged/neoforge/registries/DeferredHolder;", transformedField.desc);

        // Verify Method call polyfilled: Properties.of(Material) -> BlockPropertiesShim.ofLegacyMaterial
        MethodNode transformedMethod = resultNode.methods.get(0);
        boolean polyfillInvoked = false;
        boolean tabPolyfillInvoked = false;

        for (var insn : transformedMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/BlockPropertiesShim")
                        && minsn.name.equals("ofLegacyMaterial")) {
                    polyfillInvoked = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/CreativeTabShim")
                        && minsn.name.equals("tab")) {
                    tabPolyfillInvoked = true;
                }
            }
        }

        assertTrue(polyfillInvoked, "Expected BlockPropertiesShim.ofLegacyMaterial invocation");
        assertTrue(tabPolyfillInvoked, "Expected CreativeTabShim.tab invocation");
    }
}
