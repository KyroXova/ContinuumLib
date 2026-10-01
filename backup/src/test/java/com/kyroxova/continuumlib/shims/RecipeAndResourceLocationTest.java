package com.kyroxova.continuumlib.shims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

public class RecipeAndResourceLocationTest {

    @Test
    public void testRecipeSyntheticBridgesInjection() {
        // Synthesize a legacy 1.18.2 custom recipe class
        ClassNode recipeNode = new ClassNode();
        recipeNode.version = Opcodes.V17;
        recipeNode.access = Opcodes.ACC_PUBLIC;
        recipeNode.name = "com/example/MyCustomRecipe";
        recipeNode.superName = "net/minecraft/world/item/crafting/CustomRecipe";

        // matches(CraftingContainer, Level)
        MethodNode matchesMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "matches",
                "(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z",
                null,
                null
        );
        matchesMethod.instructions.add(new InsnNode(Opcodes.ICONST_1));
        matchesMethod.instructions.add(new InsnNode(Opcodes.IRETURN));
        recipeNode.methods.add(matchesMethod);

        // assemble(CraftingContainer)
        MethodNode assembleMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "assemble",
                "(Lnet/minecraft/world/inventory/CraftingContainer;)Lnet/minecraft/world/item/ItemStack;",
                null,
                null
        );
        assembleMethod.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        assembleMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
        recipeNode.methods.add(assembleMethod);

        // getResultItem()
        MethodNode resultMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getResultItem",
                "()Lnet/minecraft/world/item/ItemStack;",
                null,
                null
        );
        resultMethod.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        resultMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
        recipeNode.methods.add(resultMethod);

        // getRemainingItems(CraftingContainer)
        MethodNode remainingMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getRemainingItems",
                "(Lnet/minecraft/world/inventory/CraftingContainer;)Lnet/minecraft/core/NonNullList;",
                null,
                null
        );
        remainingMethod.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        remainingMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
        recipeNode.methods.add(remainingMethod);

        ClassWriter cw = new ClassWriter(0);
        recipeNode.accept(cw);
        byte[] legacyBytecode = cw.toByteArray();

        // Transform targeting 1.21.1 NeoForge
        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        TargetSpec base = TargetSpec.of("1.18.2", "forge");
        TargetSpec target = TargetSpec.of("1.21.1", "neoforge");
        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

        byte[] transformed = transformer.transform("com/example/MyCustomRecipe", legacyBytecode);
        assertNotNull(transformed);

        ClassReader cr = new ClassReader(transformed);
        ClassNode resultNode = new ClassNode();
        cr.accept(resultNode, 0);

        boolean hasModernAssembleCrafting = false;
        boolean hasModernAssembleRecipe = false;
        boolean hasModernMatchesCrafting = false;
        boolean hasModernMatchesRecipe = false;
        boolean hasModernGetResult = false;
        boolean hasModernGetRemaining = false;

        for (MethodNode m : resultNode.methods) {
            if ("assemble".equals(m.name)) {
                if (m.desc.contains("CraftingInput") && m.desc.contains("HolderLookup$Provider")) hasModernAssembleCrafting = true;
                if (m.desc.contains("RecipeInput") && m.desc.contains("HolderLookup$Provider")) hasModernAssembleRecipe = true;
            }
            if ("matches".equals(m.name)) {
                if (m.desc.contains("CraftingInput")) hasModernMatchesCrafting = true;
                if (m.desc.contains("RecipeInput")) hasModernMatchesRecipe = true;
            }
            if ("getResultItem".equals(m.name) && m.desc.contains("HolderLookup$Provider")) {
                hasModernGetResult = true;
            }
            if ("getRemainingItems".equals(m.name) && m.desc.contains("CraftingInput")) {
                hasModernGetRemaining = true;
            }
        }

        assertTrue(hasModernAssembleCrafting, "Recipe must have assemble(CraftingInput, HolderLookup.Provider) bridge");
        assertTrue(hasModernAssembleRecipe, "Recipe must have assemble(RecipeInput, HolderLookup.Provider) bridge");
        assertTrue(hasModernMatchesCrafting, "Recipe must have matches(CraftingInput, Level) bridge");
        assertTrue(hasModernMatchesRecipe, "Recipe must have matches(RecipeInput, Level) bridge");
        assertTrue(hasModernGetResult, "Recipe must have getResultItem(HolderLookup.Provider) bridge");
        assertTrue(hasModernGetRemaining, "Recipe must have getRemainingItems(CraftingInput) bridge");
    }

    @Test
    public void testResourceLocationNormalizationAndJson() {
        // Test normalization for 1.21+ singular paths
        assertEquals("recipe/my_recipe.json", ResourceLocationShim.normalizePath("recipes/my_recipe.json"));
        assertEquals("loot_table/chests/simple.json", ResourceLocationShim.normalizePath("loot_tables/chests/simple.json"));
        assertEquals("tags/block/mineable/pickaxe.json", ResourceLocationShim.normalizePath("tags/blocks/mineable/pickaxe.json"));
        assertEquals("tags/item/coals.json", ResourceLocationShim.normalizePath("tags/items/coals.json"));

        // Test denormalization for <= 1.20.6 plural paths
        assertEquals("recipes/my_recipe.json", ResourceLocationShim.denormalizePath("recipe/my_recipe.json"));
        assertEquals("loot_tables/chests/simple.json", ResourceLocationShim.denormalizePath("loot_table/chests/simple.json"));
        assertEquals("tags/blocks/mineable/pickaxe.json", ResourceLocationShim.denormalizePath("tags/block/mineable/pickaxe.json"));

        // Test JSON serialization adapter
        Gson gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(String.class, new ResourceLocationJsonAdapter())
                .create();

        String serialized = gson.toJson("buildscape:recipes/pillar_recipe");
        assertTrue(serialized.contains("buildscape:recipes/pillar_recipe"));
    }
}
