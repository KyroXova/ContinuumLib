package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.cir.CirCompilationUnit;
import com.kyroxova.continuumlib.verification.ResolutionReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BiDirectionalVersionResolutionTest {

    @Test
    @DisplayName("Verify Base = Forge 1.17.1 resolving forward & cross-loader to Forge 1.18.2, NeoForge 1.21.1/26.3, Fabric 1.17.1/1.21.1, Paper 1.21.1")
    void testForgeBaseToModernAndCrossLoader() {
        TargetSpec baseSpec = TargetSpec.of("1.17.1", "forge");
        List<TargetSpec> targets = List.of(
                TargetSpec.of("1.18.2", "forge"),
                TargetSpec.of("1.21.1", "neoforge"),
                TargetSpec.of("26.3", "neoforge"),
                TargetSpec.of("1.17.1", "fabric"),
                TargetSpec.of("1.21.1", "fabric"),
                TargetSpec.of("1.21.1", "paper")
        );

        String forge117Source = """
                package com.kyroxova.testmod;

                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.material.Material;
                import net.minecraft.world.item.Item;
                import net.minecraftforge.registries.DeferredRegister;
                import net.minecraftforge.registries.ForgeRegistries;
                import net.minecraftforge.registries.RegistryObject;

                public class TestModRegistries {
                    public static final String MOD_ID = "testmod";

                    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
                    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

                    public static final RegistryObject<Block> TEST_BLOCK = BLOCKS.register("test_block",
                            () -> new Block(BlockBehaviour.Properties.of(Material.STONE).strength(2.0F)));

                    public static final RegistryObject<Item> TEST_ITEM = ITEMS.register("test_item",
                            () -> new Item(new Item.Properties().stacksTo(64)));
                }
                """;

        ContinuumPipeline pipeline = new ContinuumPipeline(baseSpec, targets);
        Map<TargetSpec, String> emittedSources = new LinkedHashMap<>();
        ResolutionReport report = pipeline.processSource(forge117Source, emittedSources);

        CirCompilationUnit cir = pipeline.getLastAnalyzedCir();
        assertNotNull(cir);
        assertEquals("TestModRegistries", cir.getPrimaryClassName());
        assertEquals(2, cir.getRegistries().size());
        assertEquals(2, cir.getRegisteredEntries().size());

        // 1. Verify Forge 1.18.2: retains Material.STONE, DeferredRegister, RegistryObject
        String forge118Source = emittedSources.get(TargetSpec.of("1.18.2", "forge"));
        assertNotNull(forge118Source);
        assertTrue(forge118Source.contains("DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);"));
        assertTrue(forge118Source.contains("RegistryObject<Block> TEST_BLOCK = BLOCKS.register(\"test_block\""));
        assertTrue(forge118Source.contains("Material.STONE"));

        // 2. Verify NeoForge 1.21.1: converts to DeferredRegister.createBlocks, DeferredBlock, strips Material
        String neo121Source = emittedSources.get(TargetSpec.of("1.21.1", "neoforge"));
        assertNotNull(neo121Source);
        assertTrue(neo121Source.contains("DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);"));
        assertTrue(neo121Source.contains("DeferredBlock<Block> TEST_BLOCK = BLOCKS.registerBlock(\"test_block\""));
        assertTrue(neo121Source.contains("DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);"));
        assertTrue(neo121Source.contains("DeferredItem<Item> TEST_ITEM = ITEMS.registerSimpleItem(\"test_item\""));
        assertFalse(neo121Source.contains("Material.STONE"));
        assertTrue(neo121Source.contains("BlockBehaviour.Properties.of().strength(2.0F)"));

        // 3. Verify NeoForge 26.3
        String neo263Source = emittedSources.get(TargetSpec.of("26.3", "neoforge"));
        assertNotNull(neo263Source);
        assertTrue(neo263Source.contains("DeferredBlock<Block> TEST_BLOCK = BLOCKS.registerBlock(\"test_block\""));

        // 4. Verify Fabric 1.17.1: pre-1.19.3 uses Registry.BLOCK and retains Material.STONE
        String fabric117Source = emittedSources.get(TargetSpec.of("1.17.1", "fabric"));
        assertNotNull(fabric117Source);
        assertTrue(fabric117Source.contains("Registry.register(Registry.BLOCK, new ResourceLocation(MOD_ID, \"test_block\")"));
        assertTrue(fabric117Source.contains("Registry.register(Registry.ITEM, new ResourceLocation(MOD_ID, \"test_item\")"));
        assertTrue(fabric117Source.contains("Material.STONE"));

        // 5. Verify Fabric 1.21.1: modern uses BuiltInRegistries.BLOCK and strips Material
        String fabric121Source = emittedSources.get(TargetSpec.of("1.21.1", "fabric"));
        assertNotNull(fabric121Source);
        assertTrue(fabric121Source.contains("Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(MOD_ID, \"test_block\")"));
        assertTrue(fabric121Source.contains("Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, \"test_item\")"));
        assertFalse(fabric121Source.contains("Material.STONE"));

        // 6. Verify Paper 1.21.1
        String paperSource = emittedSources.get(TargetSpec.of("1.21.1", "paper"));
        assertNotNull(paperSource);
        assertTrue(paperSource.contains("NamespacedKey TEST_BLOCK_KEY = new NamespacedKey(MOD_ID, \"test_block\");"));
        assertTrue(paperSource.contains("NamespacedKey TEST_ITEM_KEY = new NamespacedKey(MOD_ID, \"test_item\");"));

        // 7. Verify in-memory javac compilation across all targets
        for (TargetSpec target : targets) {
            Boolean compiled = report.getTargetCompilationStatus().get(target);
            assertNotNull(compiled, "Target " + target.getLoader() + ":" + target.getVersion() + " should have compilation status");
            assertTrue(compiled, "Target " + target.getLoader() + ":" + target.getVersion() + " failed to compile:\n" + emittedSources.get(target));
        }
    }

    @Test
    @DisplayName("Verify Base = NeoForge 1.21.1 resolving backward & cross-loader to Forge 1.17.1/1.18.2, Fabric 1.17.1/1.21.1, Paper 1.21.1")
    void testNeoForgeBaseToLegacyForgeAndFabric() {
        TargetSpec baseSpec = TargetSpec.of("1.21.1", "neoforge");
        List<TargetSpec> targets = List.of(
                TargetSpec.of("1.17.1", "forge"),
                TargetSpec.of("1.18.2", "forge"),
                TargetSpec.of("1.17.1", "fabric"),
                TargetSpec.of("1.21.1", "fabric"),
                TargetSpec.of("1.21.1", "paper")
        );

        String neo121Source = """
                package com.kyroxova.neomod;

                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.item.Item;
                import net.neoforged.neoforge.registries.DeferredRegister;
                import net.neoforged.neoforge.registries.DeferredBlock;
                import net.neoforged.neoforge.registries.DeferredItem;

                public class TestNeoRegistries {
                    public static final String MOD_ID = "neomod";

                    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
                    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

                    public static final DeferredBlock<Block> NEO_BLOCK = BLOCKS.registerBlock("neo_block",
                            () -> new Block(BlockBehaviour.Properties.of().strength(3.0F)));

                    public static final DeferredItem<Item> NEO_ITEM = ITEMS.registerSimpleItem("neo_item",
                            new Item.Properties().stacksTo(16));
                }
                """;

        ContinuumPipeline pipeline = new ContinuumPipeline(baseSpec, targets);
        Map<TargetSpec, String> emittedSources = new LinkedHashMap<>();
        ResolutionReport report = pipeline.processSource(neo121Source, emittedSources);

        CirCompilationUnit cir = pipeline.getLastAnalyzedCir();
        assertNotNull(cir);
        assertEquals("TestNeoRegistries", cir.getPrimaryClassName());
        assertEquals(2, cir.getRegistries().size());
        assertEquals(2, cir.getRegisteredEntries().size());

        // 1. Verify Forge 1.17.1: backport synthesizes Material.STONE and DeferredRegister<T>
        String forge117Source = emittedSources.get(TargetSpec.of("1.17.1", "forge"));
        assertNotNull(forge117Source);
        assertTrue(forge117Source.contains("import net.minecraft.world.level.material.Material;"));
        assertTrue(forge117Source.contains("DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);"));
        assertTrue(forge117Source.contains("RegistryObject<Block> NEO_BLOCK = BLOCKS.register(\"neo_block\""));
        assertTrue(forge117Source.contains("Material.STONE"));
        assertTrue(forge117Source.contains("strength(3.0F)"));

        // 2. Verify Forge 1.18.2: backport synthesizes Material.STONE and DeferredRegister<T>
        String forge118Source = emittedSources.get(TargetSpec.of("1.18.2", "forge"));
        assertNotNull(forge118Source);
        assertTrue(forge118Source.contains("import net.minecraft.world.level.material.Material;"));
        assertTrue(forge118Source.contains("DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);"));
        assertTrue(forge118Source.contains("Material.STONE"));

        // 3. Verify Fabric 1.17.1: backport synthesizes Material.STONE and uses Registry.BLOCK
        String fabric117Source = emittedSources.get(TargetSpec.of("1.17.1", "fabric"));
        assertNotNull(fabric117Source);
        assertTrue(fabric117Source.contains("import net.minecraft.world.level.material.Material;"));
        assertTrue(fabric117Source.contains("Registry.register(Registry.BLOCK, new ResourceLocation(MOD_ID, \"neo_block\")"));
        assertTrue(fabric117Source.contains("Material.STONE"));

        // 4. Verify Fabric 1.21.1: uses BuiltInRegistries.BLOCK and no Material
        String fabric121Source = emittedSources.get(TargetSpec.of("1.21.1", "fabric"));
        assertNotNull(fabric121Source);
        assertTrue(fabric121Source.contains("Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(MOD_ID, \"neo_block\")"));
        assertFalse(fabric121Source.contains("Material.STONE"));

        // 5. Verify Paper 1.21.1
        String paperSource = emittedSources.get(TargetSpec.of("1.21.1", "paper"));
        assertNotNull(paperSource);
        assertTrue(paperSource.contains("NamespacedKey NEO_BLOCK_KEY = new NamespacedKey(MOD_ID, \"neo_block\");"));

        // 6. In-memory javac compilation verification across all targets
        for (TargetSpec target : targets) {
            Boolean compiled = report.getTargetCompilationStatus().get(target);
            assertNotNull(compiled, "Target " + target.getLoader() + ":" + target.getVersion() + " should have compilation status");
            assertTrue(compiled, "Target " + target.getLoader() + ":" + target.getVersion() + " failed to compile:\n" + emittedSources.get(target));
        }
    }
}
