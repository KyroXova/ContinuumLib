package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.cir.CirCompilationUnit;
import com.kyroxova.continuumlib.cir.CirRegisteredEntry;
import com.kyroxova.continuumlib.verification.ResolutionReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UniversalMultiPlatformResolutionTest {

    @Test
    @DisplayName("Verify Universal Multi-Platform Resolution from Forge 1.18.2 to Forge, NeoForge, Fabric, Quilt, Paper, and Spigot")
    void testUniversalMultiPlatformResolution() {
        TargetSpec baseSpec = TargetSpec.of("1.18.2", "forge");
        List<TargetSpec> targets = List.of(
                TargetSpec.of("1.19.2", "forge"),
                TargetSpec.of("1.21.1", "neoforge"),
                TargetSpec.of("26.3", "neoforge"),
                TargetSpec.of("1.21.1", "fabric"),
                TargetSpec.of("1.21.1", "quilt"),
                TargetSpec.of("1.21.1", "paper"),
                TargetSpec.of("1.21.1", "spigot")
        );

        String complexModSource = """
                package com.kingodogo.buildscape;

                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.material.Material;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.BlockItem;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.item.CreativeModeTab;
                import net.minecraft.world.level.block.entity.BlockEntityType;
                import net.minecraft.world.entity.EntityType;
                import net.minecraft.world.entity.MobCategory;
                import net.minecraft.sounds.SoundEvent;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.core.particles.SimpleParticleType;
                import net.minecraft.core.particles.ParticleType;
                import net.minecraft.world.inventory.MenuType;
                import net.minecraftforge.common.extensions.IForgeMenuType;
                import net.minecraft.world.level.material.Fluid;
                import net.minecraft.world.item.crafting.RecipeSerializer;
                import net.minecraft.world.item.crafting.SimpleRecipeSerializer;
                import net.minecraftforge.registries.DeferredRegister;
                import net.minecraftforge.registries.ForgeRegistries;
                import net.minecraftforge.registries.RegistryObject;

                public class ModMultiPlatformRegistries {
                    public static final String MOD_ID = "buildscape";
                    public static final Object PROPERTIES = null;

                    public static final DeferredRegister<Block> BLOCKS =
                            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
                    public static final DeferredRegister<Item> ITEMS =
                            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
                    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
                            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, MOD_ID);
                    public static final DeferredRegister<EntityType<?>> ENTITIES =
                            DeferredRegister.create(ForgeRegistries.ENTITIES, MOD_ID);
                    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
                            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
                    public static final DeferredRegister<ParticleType<?>> PARTICLES =
                            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);
                    public static final DeferredRegister<MenuType<?>> MENUS =
                            DeferredRegister.create(ForgeRegistries.CONTAINERS, MOD_ID);
                    public static final DeferredRegister<Fluid> FLUIDS =
                            DeferredRegister.create(ForgeRegistries.FLUIDS, MOD_ID);
                    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
                            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID);

                    // 1. Block & Custom Subclass Block
                    public static final RegistryObject<Block> COPPER_GRATE =
                            BLOCKS.register("copper_grate", () -> new Block(BlockBehaviour.Properties.of(Material.STONE).strength(2.0F)));
                    public static final RegistryObject<MyMachineBlock> MACHINE =
                            BLOCKS.register("machine", () -> new MyMachineBlock(BlockBehaviour.Properties.of(Material.STONE).strength(3.5F)));

                    // 2. BlockItem & Normal Item
                    public static final RegistryObject<Item> COPPER_GRATE_ITEM =
                            ITEMS.register("copper_grate", () -> new BlockItem(COPPER_GRATE.get(), new Item.Properties()));
                    public static final RegistryObject<Item> CHISEL =
                            ITEMS.register("chisel", () -> new Item(new Item.Properties().stacksTo(1).durability(256)));

                    // 3. Multi-Block BlockEntity
                    public static final RegistryObject<BlockEntityType<ExampleBlockEntity>> MACHINE_BE =
                            BLOCK_ENTITIES.register("machine_be", () -> BlockEntityType.Builder.of(ExampleBlockEntity::new, MACHINE.get(), COPPER_GRATE.get()).build(null));

                    // 4. Entity with Builder Chaining
                    public static final RegistryObject<EntityType<FallingIcicleEntity>> FALLING_ICICLE =
                            ENTITIES.register("falling_icicle", () -> EntityType.Builder.<FallingIcicleEntity>of(FallingIcicleEntity::new, MobCategory.MISC)
                                    .sized(0.98F, 0.98F)
                                    .clientTrackingRange(10)
                                    .updateInterval(20)
                                    .build("falling_icicle"));

                    // 5. Sound Event with Helper & Alias
                    public static final RegistryObject<SoundEvent> COPPER_GRATE_BREAK =
                            registerSoundEvent("block.copper_grate.break");
                    public static final RegistryObject<SoundEvent> COPPER_GRATE_PLACE =
                            COPPER_GRATE_BREAK;

                    // 6. Particle
                    public static final RegistryObject<SimpleParticleType> GLOW_SPARKLE =
                            PARTICLES.register("glow_sparkle", () -> new SimpleParticleType(false));

                    // 7. MenuType
                    public static final RegistryObject<MenuType<BuildersWorkbenchMenu>> WORKBENCH_MENU =
                            MENUS.register("builders_workbench", () -> IForgeMenuType.create(BuildersWorkbenchMenu::new));

                    // 8. Fluid
                    public static final RegistryObject<CustomFluid> EXPERIENCE_STILL =
                            FLUIDS.register("experience_still", () -> new CustomFluid(PROPERTIES));

                    // 9. Recipe Serializers
                    public static final RegistryObject<SimpleRecipeSerializer<ConfettiConfigureRecipe>> CONFETTI_RECIPE =
                            RECIPES.register("confetti_configure", () -> new SimpleRecipeSerializer<>(ConfettiConfigureRecipe::new));
                    public static final RegistryObject<RecipeSerializer<ShapedDurabilityRecipe>> SHAPED_RECIPE =
                            RECIPES.register("shaped_durability", () -> ShapedDurabilityRecipe.SERIALIZER);

                    // 10. Creative Mode Tab
                    public static final CreativeModeTab MOD_TAB = new CreativeModeTab(MOD_ID) {
                        @Override
                        public ItemStack makeIcon() {
                            return new ItemStack(COPPER_GRATE_ITEM.get());
                        }
                    };

                    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
                        return SOUND_EVENTS.register(name.replace('.', '_'), () -> new SoundEvent(new ResourceLocation(MOD_ID, name.replace('.', '_'))));
                    }
                }
                """;

        ContinuumPipeline pipeline = new ContinuumPipeline(baseSpec, targets);
        Map<TargetSpec, String> emittedSources = new LinkedHashMap<>();
        ResolutionReport report = pipeline.processSource(complexModSource, emittedSources);

        // Verify CIR extraction
        CirCompilationUnit cir = pipeline.getLastAnalyzedCir();
        assertNotNull(cir);
        assertEquals("com.kingodogo.buildscape", cir.getPackageName());
        assertTrue(cir.getRegisteredEntries().size() >= 13);

        // Verify CIR strictly does NOT contain loader types
        for (CirRegisteredEntry entry : cir.getRegisteredEntries()) {
            assertFalse(entry.getImplementationType().contains("RegistryObject"));
            assertFalse(entry.getImplementationType().contains("DeferredBlock"));
            assertFalse(entry.getImplementationType().contains("DeferredRegister"));
        }

        // 1. Verify Fabric & Quilt Emitted Target Code
        String fabric1211 = emittedSources.get(TargetSpec.of("1.21.1", "fabric"));
        assertNotNull(fabric1211);
        assertTrue(fabric1211.contains("Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(MOD_ID, \"copper_grate\")"));
        assertTrue(fabric1211.contains("Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MOD_ID, \"chisel\")"));
        assertTrue(fabric1211.contains("FabricBlockEntityTypeBuilder.create(ExampleBlockEntity::new, MACHINE, COPPER_GRATE)"));
        assertTrue(fabric1211.contains("ExtendedScreenHandlerType<>(BuildersWorkbenchMenu::new)"));
        assertTrue(fabric1211.contains("SoundEvent COPPER_GRATE_PLACE = COPPER_GRATE_BREAK"));
        assertTrue(fabric1211.contains("FabricItemGroup.builder()"));

        String quilt1211 = emittedSources.get(TargetSpec.of("1.21.1", "quilt"));
        assertNotNull(quilt1211);
        assertTrue(quilt1211.contains("Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(MOD_ID, \"copper_grate\")"));
        assertTrue(quilt1211.contains("FabricItemGroup.builder()"));

        // 2. Verify Paper & Spigot Plugin Emitted Target Code
        String paper1211 = emittedSources.get(TargetSpec.of("1.21.1", "paper"));
        assertNotNull(paper1211);
        assertTrue(paper1211.contains("public class ModMultiPlatformRegistries extends JavaPlugin implements Listener"));
        assertTrue(paper1211.contains("NamespacedKey COPPER_GRATE_KEY = new NamespacedKey(MOD_ID, \"copper_grate\")"));
        assertTrue(paper1211.contains("NamespacedKey CHISEL_KEY = new NamespacedKey(MOD_ID, \"chisel\")"));
        assertTrue(paper1211.contains("NamespacedKey COPPER_GRATE_PLACE_KEY = COPPER_GRATE_BREAK_KEY"));
        assertTrue(paper1211.contains("NamespacedKey MOD_TAB_KEY = new NamespacedKey(MOD_ID, \"buildscape\")"));
        assertTrue(paper1211.contains("public void onEnable()"));
        assertTrue(paper1211.contains("public void onDisable()"));

        String spigot1211 = emittedSources.get(TargetSpec.of("1.21.1", "spigot"));
        assertNotNull(spigot1211);
        assertTrue(spigot1211.contains("public class ModMultiPlatformRegistries extends JavaPlugin implements Listener"));
        assertTrue(spigot1211.contains("NamespacedKey COPPER_GRATE_KEY = new NamespacedKey(MOD_ID, \"copper_grate\")"));

        // 3. Verify NeoForge Emitted Target Code
        String neo1211 = emittedSources.get(TargetSpec.of("1.21.1", "neoforge"));
        assertNotNull(neo1211);
        assertTrue(neo1211.contains("DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID)"));
        assertTrue(neo1211.contains("DeferredBlock<Block> COPPER_GRATE = BLOCKS.registerBlock(\"copper_grate\", Block::new"));
        assertTrue(neo1211.contains("CreativeModeTab.builder()"));

        // 4. Verify javac Target Compilation across all 7 platforms
        Boolean forgeCompiled = report.getTargetCompilationStatus().get(TargetSpec.of("1.19.2", "forge"));
        Boolean neo1211Compiled = report.getTargetCompilationStatus().get(TargetSpec.of("1.21.1", "neoforge"));
        Boolean neo263Compiled = report.getTargetCompilationStatus().get(TargetSpec.of("26.3", "neoforge"));
        Boolean fabricCompiled = report.getTargetCompilationStatus().get(TargetSpec.of("1.21.1", "fabric"));
        Boolean quiltCompiled = report.getTargetCompilationStatus().get(TargetSpec.of("1.21.1", "quilt"));
        Boolean paperCompiled = report.getTargetCompilationStatus().get(TargetSpec.of("1.21.1", "paper"));
        Boolean spigotCompiled = report.getTargetCompilationStatus().get(TargetSpec.of("1.21.1", "spigot"));

        assertNotNull(forgeCompiled);
        assertTrue(forgeCompiled, "Forge 1.19.2 compilation must succeed");

        assertNotNull(neo1211Compiled);
        assertTrue(neo1211Compiled, "NeoForge 1.21.1 compilation must succeed");

        assertNotNull(neo263Compiled);
        assertTrue(neo263Compiled, "NeoForge 26.3 compilation must succeed");

        assertNotNull(fabricCompiled);
        assertTrue(fabricCompiled, "Fabric 1.21.1 compilation must succeed");

        assertNotNull(quiltCompiled);
        assertTrue(quiltCompiled, "Quilt 1.21.1 compilation must succeed");

        assertNotNull(paperCompiled);
        assertTrue(paperCompiled, "Paper 1.21.1 compilation must succeed");

        assertNotNull(spigotCompiled);
        assertTrue(spigotCompiled, "Spigot 1.21.1 compilation must succeed");

        // 5. Verify Resolution Report formatting
        String reportOutput = report.generateReportString();
        assertTrue(reportOutput.contains("Target: Forge 1.19.2"));
        assertTrue(reportOutput.contains("Target: NeoForge 1.21.1"));
        assertTrue(reportOutput.contains("Target: NeoForge 26.3"));
        assertTrue(reportOutput.contains("Target: Fabric 1.21.1"));
        assertTrue(reportOutput.contains("Target: Quilt 1.21.1"));
        assertTrue(reportOutput.contains("Target: Paper 1.21.1"));
        assertTrue(reportOutput.contains("Target: Spigot 1.21.1"));
    }
}
