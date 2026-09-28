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

import static org.junit.jupiter.api.Assertions.*;

public class ScreenAndLootBridgesTest {

    @Test
    public void testScreenGuiGraphicsSyntheticBridges() {
        // Synthesize a legacy 1.18.2 Screen class
        ClassNode screenNode = new ClassNode();
        screenNode.version = Opcodes.V17;
        screenNode.access = Opcodes.ACC_PUBLIC;
        screenNode.name = "com/example/MyCustomScreen";
        screenNode.superName = "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen";

        // render(PoseStack, int, int, float)
        MethodNode renderMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "render",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V",
                null,
                null
        );
        renderMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        screenNode.methods.add(renderMethod);

        // renderBg(PoseStack, float, int, int)
        MethodNode renderBgMethod = new MethodNode(
                Opcodes.ACC_PROTECTED,
                "renderBg",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;FII)V",
                null,
                null
        );
        renderBgMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        screenNode.methods.add(renderBgMethod);

        // renderLabels(PoseStack, int, int)
        MethodNode renderLabelsMethod = new MethodNode(
                Opcodes.ACC_PROTECTED,
                "renderLabels",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;II)V",
                null,
                null
        );
        renderLabelsMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        screenNode.methods.add(renderLabelsMethod);

        ClassWriter cw = new ClassWriter(0);
        screenNode.accept(cw);
        byte[] legacyBytecode = cw.toByteArray();

        // Transform targeting 1.20.1 Forge
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.1", "forge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        byte[] transformed = transformer.transform("com/example/MyCustomScreen", legacyBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasModernRender = false;
        boolean hasModernRenderBg = false;
        boolean hasModernRenderLabels = false;

        for (MethodNode m : resultNode.methods) {
            if ("render".equals(m.name) && m.desc.contains("GuiGraphics")) {
                hasModernRender = true;
            }
            if ("renderBg".equals(m.name) && m.desc.contains("GuiGraphics")) {
                hasModernRenderBg = true;
            }
            if ("renderLabels".equals(m.name) && m.desc.contains("GuiGraphics")) {
                hasModernRenderLabels = true;
            }
        }

        assertTrue(hasModernRender, "Screen must have synthetic render(GuiGraphics, int, int, float) bridge on 1.20+");
        assertTrue(hasModernRenderBg, "Screen must have synthetic renderBg(GuiGraphics, float, int, int) bridge on 1.20+");
        assertTrue(hasModernRenderLabels, "Screen must have synthetic renderLabels(GuiGraphics, int, int) bridge on 1.20+");
    }

    @Test
    public void testLootParamsSyntheticBridge() {
        // Synthesize a Block class with getDrops(BlockState, LootContext.Builder)
        ClassNode blockNode = new ClassNode();
        blockNode.version = Opcodes.V17;
        blockNode.access = Opcodes.ACC_PUBLIC;
        blockNode.name = "com/example/MyDropBlock";
        blockNode.superName = "net/minecraft/world/level/block/Block";

        MethodNode dropsMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getDrops",
                "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootContext$Builder;)Ljava/util/List;",
                null,
                null
        );
        dropsMethod.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        dropsMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
        blockNode.methods.add(dropsMethod);

        ClassWriter cw = new ClassWriter(0);
        blockNode.accept(cw);
        byte[] legacyBytecode = cw.toByteArray();

        // Transform targeting 1.20.4 NeoForge
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.4", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        byte[] transformed = transformer.transform("com/example/MyDropBlock", legacyBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasModernGetDrops = false;
        for (MethodNode m : resultNode.methods) {
            if ("getDrops".equals(m.name) && m.desc.contains("LootParams$Builder")) {
                hasModernGetDrops = true;
            }
        }

        assertTrue(hasModernGetDrops, "Block must have synthetic getDrops(..., LootParams.Builder) bridge on 1.20+");
    }
}
