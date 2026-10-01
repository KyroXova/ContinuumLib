package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.cir.*;
import com.kyroxova.continuumlib.verification.ResolutionReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ContinuumVerticalSlicePipelineTest {

    private static final String BASE_FORGE_1_18_2_SOURCE = """
            package com.example.infinityxyzmod;

            import net.minecraft.world.level.block.Block;
            import net.minecraft.world.level.block.state.BlockBehaviour;
            import net.minecraft.world.level.material.Material;
            import net.minecraft.world.item.Item;
            import net.minecraft.world.item.BlockItem;
            import net.minecraft.world.level.block.entity.BlockEntityType;
            import net.minecraftforge.registries.DeferredRegister;
            import net.minecraftforge.registries.ForgeRegistries;
            import net.minecraftforge.registries.RegistryObject;

            public class ModRegistry {
                public static final String MOD_ID = "infinityxyzmod";

                public static final DeferredRegister<Block> BLOCKS =
                        DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);

                public static final DeferredRegister<Item> ITEMS =
                        DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

                public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
                        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, MOD_ID);

                public static final RegistryObject<Block> EXAMPLE_BLOCK =
                        BLOCKS.register("example_block",
                                () -> new Block(
                                        BlockBehaviour.Properties
                                                .of(Material.STONE)
                                                .strength(2.0F)
                                )
                        );

                public static final RegistryObject<MyMachineBlock> MACHINE_BLOCK =
                        BLOCKS.register("machine_block",
                                () -> new MyMachineBlock(
                                        BlockBehaviour.Properties
                                                .of(Material.STONE)
                                                .strength(3.5F)
                                )
                        );

                public static final RegistryObject<Item> EXAMPLE_ITEM =
                        ITEMS.register("example_item",
                                () -> new Item(new Item.Properties())
                        );

                public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM =
                        ITEMS.register("example_block",
                                () -> new BlockItem(
                                        EXAMPLE_BLOCK.get(),
                                        new Item.Properties()
                                )
                        );

                public static final RegistryObject<BlockEntityType<ExampleBlockEntity>> MACHINE_BE =
                        BLOCK_ENTITIES.register("machine_be",
                                () -> BlockEntityType.Builder.of(
                                        ExampleBlockEntity::new,
                                        MACHINE_BLOCK.get()
                                ).build(null)
                        );
            }
            """;

    @Test
    @DisplayName("Verify Complete Vertical Slice: Forge 1.18.2 Base -> CIR -> Forge 1.19.2 / NeoForge 1.21.1 / NeoForge 26.3 -> Emission & Compilation")
    void testCompleteVerticalSlicePipeline() {
        TargetSpec baseSpec = TargetSpec.of("1.18.2", "forge");
        List<TargetSpec> targets = List.of(
                TargetSpec.of("1.19.2", "forge"),
                TargetSpec.of("1.21.1", "neoforge"),
                TargetSpec.of("26.3", "neoforge")
        );

        ContinuumPipeline pipeline = new ContinuumPipeline(baseSpec, targets);
        Map<TargetSpec, String> generatedSources = new LinkedHashMap<>();

        ResolutionReport report = pipeline.processSource(BASE_FORGE_1_18_2_SOURCE, generatedSources);

        // 1. Verify all targets produced generated source code
        assertEquals(3, generatedSources.size());
        assertNotNull(generatedSources.get(TargetSpec.of("1.19.2", "forge")));
        assertNotNull(generatedSources.get(TargetSpec.of("1.21.1", "neoforge")));
        assertNotNull(generatedSources.get(TargetSpec.of("26.3", "neoforge")));

        String forge119Source = generatedSources.get(TargetSpec.of("1.19.2", "forge"));
        String neoforge121Source = generatedSources.get(TargetSpec.of("1.21.1", "neoforge"));
        String neoforge263Source = generatedSources.get(TargetSpec.of("26.3", "neoforge"));

        // 2. Verify Forge 1.19.2 Native Source Semantics
        assertTrue(forge119Source.contains("DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)"));
        assertTrue(forge119Source.contains("RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register(\"example_block\", () -> new Block(BlockBehaviour.Properties.of(Material.STONE).strength(2.0F)))"));
        assertTrue(forge119Source.contains("RegistryObject<MyMachineBlock> MACHINE_BLOCK"));

        // 3. Verify NeoForge 1.21.1 Native Source Semantics
        assertTrue(neoforge121Source.contains("DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID)"));
        assertTrue(neoforge121Source.contains("DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerBlock(\"example_block\", Block::new, BlockBehaviour.Properties.of().strength(2.0F))"));
        assertTrue(neoforge121Source.contains("DeferredBlock<MyMachineBlock> MACHINE_BLOCK = BLOCKS.registerBlock(\"machine_block\", MyMachineBlock::new, BlockBehaviour.Properties.of().strength(3.5F))"));
        assertTrue(neoforge121Source.contains("DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem(\"example_item\", new Item.Properties())"));
        assertTrue(neoforge121Source.contains("DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(\"example_block\", EXAMPLE_BLOCK)"));
        assertTrue(neoforge121Source.contains("DeferredHolder<BlockEntityType<?>, BlockEntityType<ExampleBlockEntity>> MACHINE_BE"));
        assertFalse(neoforge121Source.contains("Material.STONE"), "NeoForge 1.21.1 must not contain Material.STONE");

        // 4. Verify NeoForge 26.3 Native Source Semantics
        assertTrue(neoforge263Source.contains("DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID)"));
        assertTrue(neoforge263Source.contains("DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerBlock(\"example_block\", Block::new, BlockBehaviour.Properties.of().strength(2.0F))"));
        assertTrue(neoforge263Source.contains("import net.minecraft.resources.Identifier;"), "NeoForge 26.3 should import Identifier");

        // 5. Verify Target Compilation Status (must be PASSED for all targets)
        for (TargetSpec target : targets) {
            Boolean compiled = report.getTargetCompilationStatus().get(target);
            assertNotNull(compiled, "Target " + target + " should have compilation status");
            assertTrue(compiled, "Target " + target + " compilation must pass!");
        }

        // 6. Verify Resolution Report Output
        String reportString = report.generateReportString();
        assertNotNull(reportString);
        System.out.println("=== RESOLUTION REPORT OUTPUT ===");
        System.out.println(reportString);
        System.out.println("================================");

        assertTrue(reportString.contains("Base:\nForge 1.18.2"));
        assertTrue(reportString.contains("Target: Forge 1.19.2"));
        assertTrue(reportString.contains("Target: NeoForge 1.21.1"));
        assertTrue(reportString.contains("Target: NeoForge 26.3"));
        assertTrue(reportString.contains("COMPILATION PASSED"));
    }

    @Test
    @DisplayName("Verify Configurable Mod Name via BootstrapperConfig (no hardcoded mod names)")
    void testConfigurableModNameViaConfigFile() {
        String customModId = "quantumtech";
        com.kyroxova.bootstrapper.config.BootstrapperConfig config = new com.kyroxova.bootstrapper.config.BootstrapperConfig(
                customModId,
                "hybrid",
                TargetSpec.of("1.18.2", "forge"),
                List.of(TargetSpec.of("1.21.1", "neoforge")),
                "%modid%-%loader%-%version%.jar",
                "build/libs/%loader%/"
        );

        String customSource = """
                package com.example.quantumtech;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.material.Material;
                import net.minecraftforge.registries.DeferredRegister;
                import net.minecraftforge.registries.ForgeRegistries;
                import net.minecraftforge.registries.RegistryObject;

                public class QuantumRegistries {
                    public static final String MOD_ID = "quantumtech";
                    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
                    public static final RegistryObject<Block> GENERATOR = BLOCKS.register("generator",
                            () -> new Block(BlockBehaviour.Properties.of(Material.STONE).strength(4.0F)));
                }
                """;

        ContinuumPipeline pipeline = new ContinuumPipeline(config);
        Map<TargetSpec, String> outputSources = new LinkedHashMap<>();
        ResolutionReport report = pipeline.processSource(customSource, outputSources);

        String emittedNeoForge = outputSources.get(TargetSpec.of("1.21.1", "neoforge"));
        assertNotNull(emittedNeoForge);
        assertTrue(emittedNeoForge.contains("DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID)"));
        assertTrue(emittedNeoForge.contains("DeferredBlock<Block> GENERATOR = BLOCKS.registerBlock(\"generator\", Block::new, BlockBehaviour.Properties.of().strength(4.0F))"));

        Boolean compiled = report.getTargetCompilationStatus().get(TargetSpec.of("1.21.1", "neoforge"));
        assertNotNull(compiled);
        assertTrue(compiled);
    }
}
