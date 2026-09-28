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

public class MasterCatalogsEngineTest {

    @Test
    public void testMasterCatalogsCoverageAndTransformations() {
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.21.0", "neoforge");

        List<TransformationRule> rules = kb.getApplicableRules(base, target);

        // 1. Verify DamageSource polyfill
        boolean hasDamageSourcePolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/world/damagesource/DamageSource") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/DamageSourceShim")
        );
        assertTrue(hasDamageSourcePolyfill, "Expected DamageSource polyfill");

        // 2. Verify EnchantmentHelper polyfill
        boolean hasEnchantmentPolyfill = rules.stream().anyMatch(r ->
                r instanceof PolyfillRule pr &&
                pr.getSourceOwner().equals("net/minecraft/world/item/enchantment/EnchantmentHelper") &&
                pr.getShimOwner().equals("com/kyroxova/continuumlib/shims/EnchantmentShim")
        );
        assertTrue(hasEnchantmentPolyfill, "Expected EnchantmentHelper polyfill");

        // 3. Verify LootContext.Builder redirect
        boolean hasLootParamsRedirect = rules.stream().anyMatch(r ->
                r instanceof ClassRedirectRule cr &&
                cr.getSourceInternalName().equals("net/minecraft/world/level/storage/loot/LootContext$Builder") &&
                cr.getTargetInternalName().equals("net/minecraft/world/level/storage/loot/LootParams$Builder")
        );
        assertTrue(hasLootParamsRedirect, "Expected LootContext.Builder -> LootParams.Builder redirect");

        // 4. Test Bytecode Transformation on a mock class with DamageSource, Enchantments, and Tags
        ClassNode cn = new ClassNode();
        cn.version = Opcodes.V17;
        cn.access = Opcodes.ACC_PUBLIC;
        cn.name = "com/example/MasterEngineTestClass";
        cn.superName = "java/lang/Object";

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "executeAll", "()V", null, null);

        // Call: DamageSource.playerAttack(player)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/world/damagesource/DamageSource",
                "playerAttack",
                "(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/damagesource/DamageSource;",
                false));

        // Call: EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/world/item/enchantment/EnchantmentHelper",
                "getItemEnchantmentLevel",
                "(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I",
                false));

        // Call: BlockTags.create(resourceLocation)
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "net/minecraft/tags/BlockTags",
                "create",
                "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;",
                false));

        mn.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        byte[] originalBytes = cw.toByteArray();

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);
        byte[] transformed = transformer.transform("com/example/MasterEngineTestClass", originalBytes);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode result = new ClassNode();
        cr.accept(result, 0);

        MethodNode resultMethod = result.methods.get(0);
        boolean damageSourcePolyfilled = false;
        boolean enchantmentPolyfilled = false;
        boolean tagPolyfilled = false;

        for (var insn : resultMethod.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/DamageSourceShim") && minsn.name.equals("playerAttack")) {
                    damageSourcePolyfilled = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/EnchantmentShim") && minsn.name.equals("getItemEnchantmentLevel")) {
                    enchantmentPolyfilled = true;
                }
                if (minsn.owner.equals("com/kyroxova/continuumlib/shims/TagShim") && minsn.name.equals("createBlockTag")) {
                    tagPolyfilled = true;
                }
            }
        }

        assertTrue(damageSourcePolyfilled, "DamageSource.playerAttack should be polyfilled");
        assertTrue(enchantmentPolyfilled, "EnchantmentHelper.getItemEnchantmentLevel should be polyfilled");
        assertTrue(tagPolyfilled, "BlockTags.create should be polyfilled");
    }
}
