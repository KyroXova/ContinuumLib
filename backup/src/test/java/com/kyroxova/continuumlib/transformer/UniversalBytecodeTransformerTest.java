package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

public class UniversalBytecodeTransformerTest {

    @Test
    public void testUniversalInstructionTransformations() {
        // 1. Synthesize a mock class with reflection, ItemStack NBT, font draw, and TranslatableComponent
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/UniversalTestClass";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "executeAll", "()V", null, null);

        // A. Call: ObfuscationReflectionHelper.setPrivateValue(clazz, instance, value, fieldName)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraftforge/fml/util/ObfuscationReflectionHelper",
                "setPrivateValue",
                "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",
                false));

        // B. Call: ItemStack.getTag()
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack",
                "getTag",
                "()Lnet/minecraft/nbt/CompoundTag;",
                false));

        // C. Call: Font.draw(poseStack, text, x, y, color)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/gui/Font",
                "draw",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
                false));

        // D. Call: new ResourceLocation(namespace, path)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "net/minecraft/resources/ResourceLocation",
                "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false));

        mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        // 2. Set up Transformer targeting 1.20.5+ NeoForge
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.5", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        // 3. Transform bytecode
        byte[] transformed = transformer.transform("com/example/UniversalTestClass", originalBytecode);
        assertNotNull(transformed);

        // 4. Verify all instructions were rewritten
        ClassReader cr = new ClassReader(transformed);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        MethodNode resultMethod = result.methods.get(0);
        boolean reflectionPolyfilled = false;
        boolean nbtPolyfilled = false;
        boolean fontDrawPolyfilled = false;
        boolean resourceLocationPolyfilled = false;

        for (var insn : resultMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/ReflectionHelperShim") && minsn.name.equals("setPrivateValue")) {
                    reflectionPolyfilled = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/ItemStackShim") && minsn.name.equals("getTag")) {
                    nbtPolyfilled = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/ScreenRenderingShim") && minsn.name.equals("drawString")) {
                    fontDrawPolyfilled = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/ResourceLocationShim") && minsn.name.equals("create")) {
                    resourceLocationPolyfilled = true;
                }
            }
        }

        assertTrue(reflectionPolyfilled, "ObfuscationReflectionHelper should be polyfilled");
        assertTrue(nbtPolyfilled, "ItemStack.getTag should be polyfilled");
        assertTrue(fontDrawPolyfilled, "Font.draw should be polyfilled");
        assertTrue(resourceLocationPolyfilled, "ResourceLocation should be polyfilled");
    }
}
