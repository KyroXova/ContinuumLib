package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExhaustiveEngineTest {

    @Test
    public void testExhaustiveCatalogsAndBytecodeTransformations() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.4", "neoforge");

        List<TransformationRule> rules = kb.getApplicableRules(base, target);

        // 1. Verify Vector3f redirect
        boolean hasVectorRedirect = rules.stream().anyMatch(r ->
                r instanceof ClassRedirectRule cr &&
                cr.getSourceInternalName().equals("com/mojang/math/Vector3f") &&
                cr.getTargetInternalName().equals("org/joml/Vector3f")
        );
        assertTrue(hasVectorRedirect, "Expected Vector3f -> org.joml.Vector3f rule");

        // 2. Verify ItemTransforms.TransformType redirect
        boolean hasTransformTypeRedirect = rules.stream().anyMatch(r ->
                r instanceof ClassRedirectRule cr &&
                cr.getSourceInternalName().equals("net/minecraft/client/renderer/block/model/ItemTransforms$TransformType") &&
                cr.getTargetInternalName().equals("net/minecraft/world/item/ItemDisplayContext")
        );
        assertTrue(hasTransformTypeRedirect, "Expected ItemTransforms.TransformType -> ItemDisplayContext rule");

        // 3. Verify RenderType shim
        boolean hasRenderTypeShim = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/client/renderer/ItemBlockRenderTypes") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/RenderTypeShim")
        );
        assertTrue(hasRenderTypeShim, "Expected RenderTypeShim rule");

        // 4. Test Bytecode Transformation on a mock class with RenderType, Button, and Vector3f
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/ExhaustiveTestClass";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "renderAll", "()V", null, null);

        // Call: ItemBlockRenderTypes.setRenderLayer(block, renderType)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/client/renderer/ItemBlockRenderTypes",
                "setRenderLayer",
                "(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/client/renderer/RenderType;)V",
                false));

        // Call: GuiComponent.fill(poseStack, minX, minY, maxX, maxY, color)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/client/gui/GuiComponent",
                "fill",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V",
                false));

        mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] originalBytes = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);
        byte[] transformed = transformer.transform("com/example/ExhaustiveTestClass", originalBytes);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        MethodNode resultMethod = result.methods.get(0);
        boolean renderTypePolyfilled = false;
        boolean guiComponentPolyfilled = false;

        for (var insn : resultMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/RenderTypeShim") && minsn.name.equals("setRenderLayer")) {
                    renderTypePolyfilled = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/GuiComponentShim") && minsn.name.equals("fill")) {
                    guiComponentPolyfilled = true;
                }
            }
        }

        assertTrue(renderTypePolyfilled, "ItemBlockRenderTypes.setRenderLayer should be polyfilled");
        assertTrue(guiComponentPolyfilled, "GuiComponent.fill should be polyfilled");
    }
}
