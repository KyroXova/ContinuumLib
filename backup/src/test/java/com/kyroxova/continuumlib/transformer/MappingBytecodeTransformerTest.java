package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.mappings.MappingFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.*;

public class MappingBytecodeTransformerTest {

    @Test
    @DisplayName("Verify bytecode transformation remapping from MOJMAP to INTERMEDIARY")
    public void testMojmapToIntermediaryTransformation() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();

        TargetSpec baseSpec = new TargetSpec(MCVersion.V1_18_2, LoaderType.FORGE, "mojmap", 17);
        TargetSpec targetSpec = new TargetSpec(MCVersion.V1_18_2, LoaderType.FABRIC, "intermediary", 17);

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, baseSpec, targetSpec);
        assertEquals(MappingFormat.MOJMAP, transformer.getBaseMappingFormat());
        assertEquals(MappingFormat.INTERMEDIARY, transformer.getTargetMappingFormat());

        // Generate synthetic class:
        // public class com/example/TestItem extends net/minecraft/world/item/Item {
        //     public net/minecraft/world/item/ItemStack stackField;
        //     public net/minecraft/world/item/Item getMyItem(net/minecraft/world/item/ItemStack stack) {
        //         stack.setCount(1);
        //         net/minecraft/world/item/ItemStack empty = net/minecraft/world/item/ItemStack.EMPTY;
        //         return stack.getItem();
        //     }
        // }
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/TestItem";
        cn.superName = "net/minecraft/world/item/Item";

        FieldNode fn = new FieldNode(Opcodes.ACC_PUBLIC, "stackField", "Lnet/minecraft/world/item/ItemStack;", null, null);
        cn.fields.add(fn);

        MethodNode mn = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getMyItem",
                "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/Item;",
                null,
                null
        );

        InsnList insns = mn.instructions;
        // stack.setCount(1);
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new InsnNode(Opcodes.ICONST_1));
        insns.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack",
                "setCount",
                "(I)V",
                false
        ));
        // net/minecraft/world/item/ItemStack.EMPTY
        insns.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "net/minecraft/world/item/ItemStack",
                "EMPTY",
                "Lnet/minecraft/world/item/ItemStack;"
        ));
        insns.add(new VarInsnNode(Opcodes.ASTORE, 2));
        // return stack.getItem();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack",
                "getItem",
                "()Lnet/minecraft/world/item/Item;",
                false
        ));
        insns.add(new InsnNode(Opcodes.ARETURN));

        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        byte[] originalBytecode = cw.toByteArray();

        // Transform bytecode
        byte[] transformedBytecode = transformer.transform("com.example.TestItem", originalBytecode);
        assertNotNull(transformedBytecode);

        // Read and verify
        ClassReader cr = new ClassReader(transformedBytecode);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        // 1. Verify superName remapped to Intermediary
        assertEquals("net/minecraft/class_1792", result.superName);

        // 2. Verify field descriptor remapped
        FieldNode resField = result.fields.get(0);
        assertEquals("Lnet/minecraft/class_1799;", resField.desc);

        // 3. Verify method descriptor remapped
        MethodNode resMethod = result.methods.get(0);
        assertEquals("(Lnet/minecraft/class_1799;)Lnet/minecraft/class_1792;", resMethod.desc);

        // 4. Verify instructions remapped
        boolean foundSetCount = false;
        boolean foundEmptyField = false;
        boolean foundGetItem = false;

        for (AbstractInsnNode insn : resMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if ("method_7939".equals(minsn.name)) { // setCount
                    assertEquals("net/minecraft/class_1799", minsn.owner);
                    assertEquals("(I)V", minsn.desc);
                    foundSetCount = true;
                } else if ("method_7909".equals(minsn.name)) { // getItem
                    assertEquals("net/minecraft/class_1799", minsn.owner);
                    assertEquals("()Lnet/minecraft/class_1792;", minsn.desc);
                    foundGetItem = true;
                }
            } else if (insn instanceof FieldInsnNode finsn) {
                if ("field_8037".equals(finsn.name)) { // EMPTY
                    assertEquals("net/minecraft/class_1799", finsn.owner);
                    assertEquals("Lnet/minecraft/class_1799;", finsn.desc);
                    foundEmptyField = true;
                }
            }
        }

        assertTrue(foundSetCount, "Expected setCount to be translated to method_7939");
        assertTrue(foundEmptyField, "Expected EMPTY to be translated to field_8037");
        assertTrue(foundGetItem, "Expected getItem to be translated to method_7909");
    }

    @Test
    @DisplayName("Verify bytecode transformation remapping from MOJMAP to SRG")
    public void testMojmapToSrgTransformation() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();

        TargetSpec baseSpec = new TargetSpec(MCVersion.V1_18_2, LoaderType.FORGE, "mojmap", 17);
        TargetSpec targetSpec = new TargetSpec(MCVersion.V1_18_2, LoaderType.FORGE, "srg", 17);

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, baseSpec, targetSpec);

        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/TestSrg";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "check", "(Lnet/minecraft/world/item/ItemStack;)V", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        mn.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/item/ItemStack",
                "getItem",
                "()Lnet/minecraft/world/item/Item;",
                false
        ));
        mn.instructions.add(new InsnNode(Opcodes.POP));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        byte[] original = cw.toByteArray();

        byte[] transformed = transformer.transform("com.example.TestSrg", original);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode res = new ClassNode();
        cr.accept(res, 0);

        MethodNode resMethod = res.methods.get(0);
        boolean foundSrgMethod = false;
        for (AbstractInsnNode insn : resMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if ("func_77973_b".equals(minsn.name)) {
                    foundSrgMethod = true;
                }
            }
        }
        assertTrue(foundSrgMethod, "Expected getItem to be translated to func_77973_b");
    }
}
