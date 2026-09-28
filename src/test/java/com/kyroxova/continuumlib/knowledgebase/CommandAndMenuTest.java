package com.kyroxova.continuumlib.knowledgebase;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CommandAndMenuTest {

    @Test
    public void testCommandAndMenuTransformations() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.20.4", "neoforge");

        List<TransformationRule> rules = kb.getApplicableRules(base, target);

        // 1. Verify SoundEvent rule
        boolean hasSoundRule = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/sounds/SoundEvent") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/SoundEventShim")
        );
        assertTrue(hasSoundRule, "Expected SoundEvent constructor polyfill");

        // 2. Verify MenuType rule
        boolean hasMenuRule = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraftforge/common/extensions/IForgeMenuType") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/MenuTypeShim")
        );
        assertTrue(hasMenuRule, "Expected IForgeMenuType.create polyfill");

        // 3. Test Bytecode Transformation
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/SoundAndMenuTestClass";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "init", "()V", null, null);

        // Call: new SoundEvent(location)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "net/minecraft/sounds/SoundEvent",
                "<init>",
                "(Lnet/minecraft/resources/ResourceLocation;)V",
                false));

        // Call: IForgeMenuType.create(factory)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraftforge/common/extensions/IForgeMenuType",
                "create",
                "(Lnet/minecraftforge/network/IContainerFactory;)Lnet/minecraft/world/inventory/MenuType;",
                false));

        mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] originalBytes = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);
        byte[] transformed = transformer.transform("com/example/SoundAndMenuTestClass", originalBytes);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        MethodNode resultMethod = result.methods.get(0);
        boolean soundPolyfilled = false;
        boolean menuPolyfilled = false;

        for (var insn : resultMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/SoundEventShim") && minsn.name.equals("create")) {
                    soundPolyfilled = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/MenuTypeShim") && minsn.name.equals("createMenuType")) {
                    menuPolyfilled = true;
                }
            }
        }

        assertTrue(soundPolyfilled, "SoundEvent should be polyfilled to SoundEventShim");
        assertTrue(menuPolyfilled, "IForgeMenuType should be polyfilled to MenuTypeShim");
    }
}
