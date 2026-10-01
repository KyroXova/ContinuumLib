package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.builder.ContinuumJarBuilder;
import com.kyroxova.continuumlib.cir.CirCompilationUnit;
import com.kyroxova.continuumlib.cir.CirRegisteredEntry;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.verification.ResolutionReport;
import com.kyroxova.continuumlib.verification.TargetCompilationVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

public class BuildScapeReleaseTest {

    private static final List<TargetSpec> POST_1182_TARGETS = List.of(
            TargetSpec.of("1.19.2", "forge"),
            TargetSpec.of("1.20.1", "forge"),
            TargetSpec.of("1.20.4", "neoforge"),
            TargetSpec.of("1.20.6", "neoforge"),
            TargetSpec.of("1.21.1", "neoforge"),
            TargetSpec.of("26.3", "neoforge"),
            TargetSpec.of("1.19.2", "fabric"),
            TargetSpec.of("1.20.1", "fabric"),
            TargetSpec.of("1.21.1", "fabric"),
            TargetSpec.of("1.21.1", "quilt"),
            TargetSpec.of("1.21.1", "paper"),
            TargetSpec.of("1.21.1", "spigot")
    );

    private static final String BUILDSCAPE_SOURCE = """
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

            public class BuildScapeReleaseRegistries {
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

                // 1. Blocks & Machine Subclass
                public static final RegistryObject<Block> COPPER_GRATE =
                        BLOCKS.register("copper_grate", () -> new Block(BlockBehaviour.Properties.of(Material.STONE).strength(2.0F)));
                public static final RegistryObject<MyMachineBlock> MACHINE =
                        BLOCKS.register("machine", () -> new MyMachineBlock(BlockBehaviour.Properties.of(Material.STONE).strength(3.5F)));

                // 2. BlockItem & Standard Item
                public static final RegistryObject<Item> COPPER_GRATE_ITEM =
                        ITEMS.register("copper_grate", () -> new BlockItem(COPPER_GRATE.get(), new Item.Properties()));
                public static final RegistryObject<Item> CHISEL =
                        ITEMS.register("chisel", () -> new Item(new Item.Properties().stacksTo(1).durability(256)));

                // 3. Block Entity
                public static final RegistryObject<BlockEntityType<ExampleBlockEntity>> MACHINE_BE =
                        BLOCK_ENTITIES.register("machine_be", () -> BlockEntityType.Builder.of(ExampleBlockEntity::new, MACHINE.get(), COPPER_GRATE.get()).build(null));

                // 4. Entity Type
                public static final RegistryObject<EntityType<FallingIcicleEntity>> FALLING_ICICLE =
                        ENTITIES.register("falling_icicle", () -> EntityType.Builder.<FallingIcicleEntity>of(FallingIcicleEntity::new, MobCategory.MISC)
                                .sized(0.98F, 0.98F)
                                .clientTrackingRange(10)
                                .updateInterval(20)
                                .build("falling_icicle"));

                // 5. Sound Events
                public static final RegistryObject<SoundEvent> COPPER_GRATE_BREAK =
                        registerSoundEvent("block.copper_grate.break");
                public static final RegistryObject<SoundEvent> COPPER_GRATE_PLACE =
                        COPPER_GRATE_BREAK;

                // 6. Particles
                public static final RegistryObject<SimpleParticleType> GLOW_SPARKLE =
                        PARTICLES.register("glow_sparkle", () -> new SimpleParticleType(false));

                // 7. Menus
                public static final RegistryObject<MenuType<BuildersWorkbenchMenu>> WORKBENCH_MENU =
                        MENUS.register("builders_workbench", () -> IForgeMenuType.create(BuildersWorkbenchMenu::new));

                // 8. Fluids
                public static final RegistryObject<CustomFluid> EXPERIENCE_STILL =
                        FLUIDS.register("experience_still", () -> new CustomFluid(PROPERTIES));

                // 9. Recipe Serializers
                public static final RegistryObject<SimpleRecipeSerializer<ConfettiConfigureRecipe>> CONFETTI_RECIPE =
                        RECIPES.register("confetti_configure", () -> new SimpleRecipeSerializer<>(ConfettiConfigureRecipe::new));
                public static final RegistryObject<RecipeSerializer<ShapedDurabilityRecipe>> SHAPED_RECIPE =
                        RECIPES.register("shaped_durability", () -> ShapedDurabilityRecipe.SERIALIZER);

                // 10. Creative Tab
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

    @Test
    @DisplayName("Verify BuildScape Semantic Resolution and Target Compilation for all post-1.18.2 targets")
    void testBuildScapeSemanticResolutionAndCompilation() {
        TargetSpec baseSpec = TargetSpec.of("1.18.2", "forge");

        ContinuumPipeline pipeline = new ContinuumPipeline(baseSpec, POST_1182_TARGETS);
        Map<TargetSpec, String> emittedSources = new LinkedHashMap<>();
        ResolutionReport report = pipeline.processSource(BUILDSCAPE_SOURCE, emittedSources);

        // Verify CIR extraction
        CirCompilationUnit cir = pipeline.getLastAnalyzedCir();
        assertNotNull(cir);
        assertEquals("com.kingodogo.buildscape", cir.getPackageName());
        assertTrue(cir.getRegisteredEntries().size() >= 13);

        for (CirRegisteredEntry entry : cir.getRegisteredEntries()) {
            assertFalse(entry.getImplementationType().contains("RegistryObject"));
            assertFalse(entry.getImplementationType().contains("DeferredBlock"));
            assertFalse(entry.getImplementationType().contains("DeferredRegister"));
        }

        // Verify Target Code Emission & Javac Compilation across all 12 targets
        for (TargetSpec target : POST_1182_TARGETS) {
            String emitted = emittedSources.get(target);
            assertNotNull(emitted, "Emitted source must be present for " + target);

            if (target.getLoader() == LoaderType.FORGE) {
                assertTrue(emitted.contains("DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)"));
                assertTrue(emitted.contains("RegistryObject<Block> COPPER_GRATE = BLOCKS.register("));
            } else if (target.getLoader() == LoaderType.NEOFORGE) {
                assertTrue(emitted.contains("DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID)"));
                assertTrue(emitted.contains("DeferredBlock<Block> COPPER_GRATE = BLOCKS.registerBlock(\"copper_grate\", Block::new"));
                if (target.getVersion().isAtLeast(MCVersion.of("26.0"))) {
                    assertTrue(emitted.contains("Identifier.fromNamespaceAndPath(MOD_ID, \"block_copper_grate_break\")"));
                }
            } else if (target.getLoader() == LoaderType.FABRIC || target.getLoader() == LoaderType.QUILT) {
                assertTrue(emitted.contains("Registry.register("));
                assertTrue(emitted.contains("FabricBlockEntityTypeBuilder.create("));
            } else if (target.getLoader() == LoaderType.PAPER || target.getLoader() == LoaderType.SPIGOT) {
                assertTrue(emitted.contains("public class BuildScapeReleaseRegistries extends JavaPlugin implements Listener"));
                assertTrue(emitted.contains("NamespacedKey COPPER_GRATE_KEY = new NamespacedKey(MOD_ID, \"copper_grate\")"));
            }

            Boolean compiled = report.getTargetCompilationStatus().get(target);
            assertNotNull(compiled, "Target compilation status must be present for " + target);
            assertTrue(compiled, "Target Java compilation must succeed for " + target);
        }
    }

    @Test
    @DisplayName("Verify BuildScape Release Packaging Pipeline: Loader-Specific JARs and Universal Bundle for all post-1.18.2 targets")
    void testBuildScapeReleasePackagingPipeline() throws IOException {
        TargetSpec baseSpec = TargetSpec.of("1.18.2", "forge");
        File buildscapeRefResources = new File("ref/buildscape/1.18.2/src/main/resources");
        assertTrue(buildscapeRefResources.exists(), "BuildScape 1.18.2 resources must exist");

        // 1. Gather reference resources from ref/buildscape/1.18.2
        Map<String, byte[]> inputFiles = new LinkedHashMap<>();
        Path rootPath = buildscapeRefResources.toPath();
        try (var stream = Files.walk(rootPath)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relativePath = rootPath.relativize(path).toString().replace('\\', '/');
                try {
                    inputFiles.put(relativePath, Files.readAllBytes(path));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        // 2. Compile real, valid Java bytecode for custom blocks and registries using TargetCompilationVerifier
        TargetCompilationVerifier verifier = new TargetCompilationVerifier();

        // 2a. Real custom block classes with rich property and shape logics
        String stringLightSource = """
                package com.kingodogo.buildscape.block;

                import net.minecraft.core.BlockPos;
                import net.minecraft.core.Direction;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.InteractionResult;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.item.context.BlockPlaceContext;
                import net.minecraft.world.level.BlockGetter;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.level.block.AbstractGlassBlock;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.SimpleWaterloggedBlock;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.level.block.state.StateDefinition;
                import net.minecraft.world.level.block.state.properties.BlockStateProperties;
                import net.minecraft.world.level.block.state.properties.BooleanProperty;
                import net.minecraft.world.level.block.state.properties.DirectionProperty;
                import net.minecraft.world.phys.BlockHitResult;
                import net.minecraft.world.phys.shapes.CollisionContext;
                import net.minecraft.world.phys.shapes.VoxelShape;

                public class StringLightBlock extends AbstractGlassBlock implements SimpleWaterloggedBlock {
                    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
                    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
                    public static final BooleanProperty LIT = BlockStateProperties.POWERED;

                    private static final VoxelShape SHAPE_NORTH = Block.box(0, 12, 0, 16, 15, 2);
                    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 12, 14, 16, 15, 16);
                    private static final VoxelShape SHAPE_EAST = Block.box(14, 12, 0, 16, 15, 16);
                    private static final VoxelShape SHAPE_WEST = Block.box(0, 12, 0, 2, 15, 16);

                    public StringLightBlock(BlockBehaviour.Properties properties) {
                        super(properties);
                        this.registerDefaultState(this.stateDefinition.any()
                                .setValue(FACING, Direction.NORTH)
                                .setValue(WATERLOGGED, false)
                                .setValue(LIT, false));
                    }

                    @Override
                    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                        Direction facing = state.getValue(FACING);
                        return switch (facing) {
                            case NORTH -> SHAPE_NORTH;
                            case SOUTH -> SHAPE_SOUTH;
                            case EAST -> SHAPE_EAST;
                            default -> SHAPE_WEST;
                        };
                    }

                    @Override
                    public BlockState getStateForPlacement(BlockPlaceContext context) {
                        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
                    }

                    @Override
                    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                        builder.add(FACING, WATERLOGGED, LIT);
                    }

                    @Override
                    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
                        return InteractionResult.SUCCESS;
                    }
                }
                """;

        String shelfSource = """
                package com.kingodogo.buildscape.block;

                import net.minecraft.core.BlockPos;
                import net.minecraft.core.Direction;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.InteractionResult;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.item.context.BlockPlaceContext;
                import net.minecraft.world.level.BlockGetter;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.level.block.BaseEntityBlock;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.SimpleWaterloggedBlock;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.level.block.state.StateDefinition;
                import net.minecraft.world.level.block.state.properties.BlockStateProperties;
                import net.minecraft.world.level.block.state.properties.BooleanProperty;
                import net.minecraft.world.level.block.state.properties.DirectionProperty;
                import net.minecraft.world.phys.BlockHitResult;
                import net.minecraft.world.phys.shapes.CollisionContext;
                import net.minecraft.world.phys.shapes.Shapes;
                import net.minecraft.world.phys.shapes.VoxelShape;

                public class ShelfBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
                    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
                    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
                    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

                    private static final VoxelShape SHAPE_NORTH = Shapes.or(
                            Block.box(0, 12, 11, 16, 16, 13),
                            Block.box(0, 0, 13, 16, 16, 16)
                    );
                    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
                            Block.box(0, 12, 3, 16, 16, 5),
                            Block.box(0, 0, 0, 16, 16, 3)
                    );
                    private static final VoxelShape SHAPE_WEST = Shapes.or(
                            Block.box(11, 12, 0, 13, 16, 16),
                            Block.box(13, 0, 0, 16, 16, 16)
                    );
                    private static final VoxelShape SHAPE_EAST = Shapes.or(
                            Block.box(3, 12, 0, 5, 16, 16),
                            Block.box(0, 0, 0, 3, 16, 16)
                    );

                    public ShelfBlock(BlockBehaviour.Properties properties) {
                        super(properties);
                        this.registerDefaultState(this.stateDefinition.any()
                                .setValue(FACING, Direction.NORTH)
                                .setValue(WATERLOGGED, false)
                                .setValue(POWERED, false));
                    }

                    @Override
                    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                        Direction facing = state.getValue(FACING);
                        return switch (facing) {
                            case SOUTH -> SHAPE_SOUTH;
                            case WEST -> SHAPE_WEST;
                            case EAST -> SHAPE_EAST;
                            default -> SHAPE_NORTH;
                        };
                    }

                    @Override
                    public BlockState getStateForPlacement(BlockPlaceContext context) {
                        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
                    }

                    @Override
                    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                        builder.add(FACING, WATERLOGGED, POWERED);
                    }

                    @Override
                    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
                        return InteractionResult.SUCCESS;
                    }
                }
                """;

        String ornamentSource = """
                package com.kingodogo.buildscape.block;

                import net.minecraft.core.BlockPos;
                import net.minecraft.core.Direction;
                import net.minecraft.world.item.context.BlockPlaceContext;
                import net.minecraft.world.level.BlockGetter;
                import net.minecraft.world.level.block.AbstractGlassBlock;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.SimpleWaterloggedBlock;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.level.block.state.StateDefinition;
                import net.minecraft.world.level.block.state.properties.BlockStateProperties;
                import net.minecraft.world.level.block.state.properties.BooleanProperty;
                import net.minecraft.world.level.block.state.properties.DirectionProperty;
                import net.minecraft.world.phys.shapes.CollisionContext;
                import net.minecraft.world.phys.shapes.VoxelShape;

                public class OrnamentBlock extends AbstractGlassBlock implements SimpleWaterloggedBlock {
                    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
                    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

                    private static final VoxelShape SHAPE_NORTH = Block.box(5, 4, 10, 11, 12, 16);
                    private static final VoxelShape SHAPE_SOUTH = Block.box(5, 4, 0, 11, 12, 6);
                    private static final VoxelShape SHAPE_EAST = Block.box(0, 4, 5, 6, 12, 11);
                    private static final VoxelShape SHAPE_WEST = Block.box(10, 4, 5, 16, 12, 11);

                    public OrnamentBlock(BlockBehaviour.Properties properties) {
                        super(properties);
                        this.registerDefaultState(this.stateDefinition.any()
                                .setValue(FACING, Direction.NORTH)
                                .setValue(WATERLOGGED, false));
                    }

                    @Override
                    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                        Direction facing = state.getValue(FACING);
                        return switch (facing) {
                            case SOUTH -> SHAPE_SOUTH;
                            case EAST -> SHAPE_EAST;
                            case WEST -> SHAPE_WEST;
                            default -> SHAPE_NORTH;
                        };
                    }

                    @Override
                    public BlockState getStateForPlacement(BlockPlaceContext context) {
                        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
                    }

                    @Override
                    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                        builder.add(FACING, WATERLOGGED);
                    }
                }
                """;

        String verticalSlabSource = """
                package com.kingodogo.buildscape.block;

                import net.minecraft.core.BlockPos;
                import net.minecraft.core.Direction;
                import net.minecraft.world.item.context.BlockPlaceContext;
                import net.minecraft.world.level.BlockGetter;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.SlabBlock;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.level.block.state.StateDefinition;
                import net.minecraft.world.level.block.state.properties.BlockStateProperties;
                import net.minecraft.world.level.block.state.properties.DirectionProperty;
                import net.minecraft.world.phys.shapes.CollisionContext;
                import net.minecraft.world.phys.shapes.VoxelShape;

                public class VerticalSlabBlock extends SlabBlock {
                    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

                    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, 8);
                    private static final VoxelShape SOUTH = Block.box(0, 0, 8, 16, 16, 16);
                    private static final VoxelShape WEST = Block.box(0, 0, 0, 8, 16, 16);
                    private static final VoxelShape EAST = Block.box(8, 0, 0, 16, 16, 16);

                    public VerticalSlabBlock(BlockBehaviour.Properties properties) {
                        super(properties);
                        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
                    }

                    @Override
                    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                        Direction facing = state.getValue(FACING);
                        return switch (facing) {
                            case SOUTH -> SOUTH;
                            case WEST -> WEST;
                            case EAST -> EAST;
                            default -> NORTH;
                        };
                    }

                    @Override
                    public BlockState getStateForPlacement(BlockPlaceContext context) {
                        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
                    }

                    @Override
                    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                        super.createBlockStateDefinition(builder);
                        builder.add(FACING);
                    }
                }
                """;

        String grateSource = """
                package com.kingodogo.buildscape.block;

                import net.minecraft.world.level.block.HalfTransparentBlock;
                import net.minecraft.world.level.block.SimpleWaterloggedBlock;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.level.block.state.StateDefinition;
                import net.minecraft.world.level.block.state.properties.BlockStateProperties;
                import net.minecraft.world.level.block.state.properties.BooleanProperty;

                public class WaterloggableGrateBlock extends HalfTransparentBlock implements SimpleWaterloggedBlock {
                    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

                    public WaterloggableGrateBlock(BlockBehaviour.Properties properties) {
                        super(properties);
                        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
                    }

                    @Override
                    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                        builder.add(WATERLOGGED);
                    }
                }
                """;

        // 2b. Release Registries with dynamic registration of all BuildScape blocks and items
        String registriesSource = """
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

                public class BuildScapeReleaseRegistries {
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

                    // Core sample entries
                    public static final RegistryObject<Block> COPPER_GRATE =
                            BLOCKS.register("copper_grate", () -> new com.kingodogo.buildscape.block.WaterloggableGrateBlock(BlockBehaviour.Properties.of()));
                    public static final RegistryObject<MyMachineBlock> MACHINE =
                            BLOCKS.register("machine", () -> new MyMachineBlock(BlockBehaviour.Properties.of()));
                    public static final RegistryObject<Item> COPPER_GRATE_ITEM =
                            ITEMS.register("copper_grate", () -> new BlockItem(COPPER_GRATE.get(), new Item.Properties()));
                    public static final RegistryObject<Item> CHISEL =
                            ITEMS.register("chisel", () -> new Item(new Item.Properties()));
                    public static final RegistryObject<BlockEntityType<ExampleBlockEntity>> MACHINE_BE =
                            BLOCK_ENTITIES.register("machine_be", () -> BlockEntityType.Builder.of(ExampleBlockEntity::new, MACHINE.get(), COPPER_GRATE.get()).build(null));
                    public static final RegistryObject<EntityType<FallingIcicleEntity>> FALLING_ICICLE =
                            ENTITIES.register("falling_icicle", () -> EntityType.Builder.<FallingIcicleEntity>of(FallingIcicleEntity::new, MobCategory.MISC)
                                    .sized(0.98F, 0.98F).build("falling_icicle"));
                    public static final RegistryObject<SoundEvent> COPPER_GRATE_BREAK =
                            SOUND_EVENTS.register("block_copper_grate_break", () -> new SoundEvent(new ResourceLocation(MOD_ID, "block_copper_grate_break")));
                    public static final RegistryObject<SoundEvent> COPPER_GRATE_PLACE = COPPER_GRATE_BREAK;
                    public static final RegistryObject<SimpleParticleType> GLOW_SPARKLE =
                            PARTICLES.register("glow_sparkle", () -> new SimpleParticleType(false));
                    public static final RegistryObject<MenuType<BuildersWorkbenchMenu>> WORKBENCH_MENU =
                            MENUS.register("builders_workbench", () -> IForgeMenuType.create(BuildersWorkbenchMenu::new));
                    public static final RegistryObject<CustomFluid> EXPERIENCE_STILL =
                            FLUIDS.register("experience_still", () -> new CustomFluid(PROPERTIES));

                    // Recipe Serializers for all custom recipe types in BuildScape
                    public static final RegistryObject<SimpleRecipeSerializer<ConfettiConfigureRecipe>> CONFETTI_RECIPE =
                            RECIPES.register("confetti_configure", () -> new SimpleRecipeSerializer<>(ConfettiConfigureRecipe::new));
                    public static final RegistryObject<RecipeSerializer<ShapedDurabilityRecipe>> SHAPED_RECIPE =
                            RECIPES.register("shaped_durability", () -> ShapedDurabilityRecipe.SERIALIZER);
                    public static final RegistryObject<SimpleRecipeSerializer<ConfettiConfigureRecipe>> FIREWORK_STAR_RECIPE =
                            RECIPES.register("custom_firework_star", () -> new SimpleRecipeSerializer<>(ConfettiConfigureRecipe::new));
                    public static final RegistryObject<SimpleRecipeSerializer<ConfettiConfigureRecipe>> INFINITE_PHOENIX_RECIPE =
                            RECIPES.register("infinite_phoenix_firework_star", () -> new SimpleRecipeSerializer<>(ConfettiConfigureRecipe::new));
                    public static final RegistryObject<SimpleRecipeSerializer<ConfettiConfigureRecipe>> CLEAR_SHULKER_RECIPE =
                            RECIPES.register("clear_shulker_filters", () -> new SimpleRecipeSerializer<>(ConfettiConfigureRecipe::new));
                    public static final RegistryObject<SimpleRecipeSerializer<ConfettiConfigureRecipe>> SHAPELESS_RECIPE =
                            RECIPES.register("shapeless_durability", () -> new SimpleRecipeSerializer<>(ConfettiConfigureRecipe::new));

                    public static final CreativeModeTab MOD_TAB = new CreativeModeTab(MOD_ID) {
                        @Override
                        public ItemStack makeIcon() {
                            return new ItemStack(COPPER_GRATE_ITEM.get());
                        }
                    };

                    public static void init(Object bus) {
                        System.out.println("[BuildScape] Initializing BuildScape registries on event bus: " + bus);
                        int blockCount = 0;
                        int itemCount = 0;

                        // Register dynamically discovered BuildScape blocks
                        java.io.InputStream inBlocks = BuildScapeReleaseRegistries.class.getResourceAsStream("/META-INF/buildscape_blocks.txt");
                        if (inBlocks == null) {
                            inBlocks = BuildScapeReleaseRegistries.class.getClassLoader().getResourceAsStream("META-INF/buildscape_blocks.txt");
                        }
                        if (inBlocks != null) {
                            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(inBlocks, java.nio.charset.StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = r.readLine()) != null) {
                                    line = line.trim();
                                    if (!line.isEmpty()) {
                                        final String id = line;
                                        BLOCKS.register(id, () -> createBlock(id));
                                        ITEMS.register(id, () -> new BlockItem(new Block(BlockBehaviour.Properties.of()), new Item.Properties()));
                                        blockCount++;
                                    }
                                }
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }

                        // Register dynamically discovered BuildScape items
                        java.io.InputStream inItems = BuildScapeReleaseRegistries.class.getResourceAsStream("/META-INF/buildscape_items.txt");
                        if (inItems == null) {
                            inItems = BuildScapeReleaseRegistries.class.getClassLoader().getResourceAsStream("META-INF/buildscape_items.txt");
                        }
                        if (inItems != null) {
                            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(inItems, java.nio.charset.StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = r.readLine()) != null) {
                                    line = line.trim();
                                    if (!line.isEmpty()) {
                                        final String id = line;
                                        ITEMS.register(id, () -> new Item(new Item.Properties()));
                                        itemCount++;
                                    }
                                }
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }

                        System.out.println("[BuildScape] Registered " + blockCount + " blocks and " + itemCount + " items!");

                        if (bus != null) {
                            try {
                                BLOCKS.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                ITEMS.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                RECIPES.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                BLOCK_ENTITIES.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                ENTITIES.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                SOUND_EVENTS.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                PARTICLES.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                MENUS.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                FLUIDS.register((net.minecraftforge.eventbus.api.IEventBus) bus);
                                System.out.println("[BuildScape] All DeferredRegisters registered to event bus successfully!");
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    }

                    public static Block createBlock(String id) {
                        BlockBehaviour.Properties props = BlockBehaviour.Properties.of();
                        if (id.contains("string_light")) {
                            return new com.kingodogo.buildscape.block.StringLightBlock(props);
                        } else if (id.contains("shelf")) {
                            return new com.kingodogo.buildscape.block.ShelfBlock(props);
                        } else if (id.contains("ornament")) {
                            return new com.kingodogo.buildscape.block.OrnamentBlock(props);
                        } else if (id.contains("vertical_slab")) {
                            return new com.kingodogo.buildscape.block.VerticalSlabBlock(props);
                        } else if (id.contains("grate")) {
                            return new com.kingodogo.buildscape.block.WaterloggableGrateBlock(props);
                        }
                        return new Block(props);
                    }
                }
                """;

        String buildScapeMainSource = """
                package com.kingodogo.buildscape;

                import net.minecraftforge.fml.common.Mod;

                @Mod("buildscape")
                public class BuildScape {
                    public static final String MODID = "buildscape";

                    public BuildScape(net.minecraftforge.eventbus.api.IEventBus bus) {
                        BuildScapeReleaseRegistries.init(bus);
                    }
                }
                """;

        Map<String, String> sourcesToCompile = new LinkedHashMap<>();
        sourcesToCompile.put("com.kingodogo.buildscape.block.StringLightBlock", stringLightSource);
        sourcesToCompile.put("com.kingodogo.buildscape.block.ShelfBlock", shelfSource);
        sourcesToCompile.put("com.kingodogo.buildscape.block.OrnamentBlock", ornamentSource);
        sourcesToCompile.put("com.kingodogo.buildscape.block.VerticalSlabBlock", verticalSlabSource);
        sourcesToCompile.put("com.kingodogo.buildscape.block.WaterloggableGrateBlock", grateSource);
        sourcesToCompile.put("com.kingodogo.buildscape.BuildScapeReleaseRegistries", registriesSource);
        sourcesToCompile.put("com.kingodogo.buildscape.BuildScape", buildScapeMainSource);

        Map<String, byte[]> allCompiled = verifier.compileToBytecode(sourcesToCompile, baseSpec);
        assertFalse(allCompiled.isEmpty(), "BuildScape release classes compilation must succeed and produce valid bytecode");
        inputFiles.putAll(allCompiled);

        // Ensure META-INF block/item lists are explicitly included in release packaging
        File blocksListFile = new File("ref/buildscape/1.18.2/src/main/resources/META-INF/buildscape_blocks.txt");
        if (blocksListFile.exists()) {
            inputFiles.put("META-INF/buildscape_blocks.txt", Files.readAllBytes(blocksListFile.toPath()));
        }
        File itemsListFile = new File("ref/buildscape/1.18.2/src/main/resources/META-INF/buildscape_items.txt");
        if (itemsListFile.exists()) {
            inputFiles.put("META-INF/buildscape_items.txt", Files.readAllBytes(itemsListFile.toPath()));
        }

        // 3. Configure BootstrapperConfig for BuildScape loaded from ref directory
        File releaseOutputDir = new File("build/releases/buildscape");
        File buildscapeConfigFile = new File("ref/buildscape/continuumlib.json");
        BootstrapperConfig config = buildscapeConfigFile.exists()
                ? BootstrapperConfig.loadFromFile(buildscapeConfigFile)
                : new BootstrapperConfig("buildscape", "build", baseSpec, POST_1182_TARGETS, "%modid%-%loader%-%version%.jar", "build/releases/buildscape/%loader%/");

        ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
        ContinuumJarBuilder builder = new ContinuumJarBuilder(config, kb);

        // 4. Build all target release JARs and the Universal multi-loader bundle
        List<File> generatedJars = builder.buildAll(inputFiles, releaseOutputDir, true);
        assertEquals(POST_1182_TARGETS.size() + 1, generatedJars.size(),
                "Must generate 12 loader-specific release JARs and 1 universal bundle");

        // 5. Deep validation of all generated release JARs
        for (File jarFile : generatedJars) {
            assertTrue(jarFile.exists(), "Generated JAR must exist: " + jarFile.getAbsolutePath());
            assertTrue(jarFile.length() > 0, "Generated JAR must have non-zero size: " + jarFile.getName());

            String parentDirName = jarFile.getParentFile().getName();
            try (JarFile jar = new JarFile(jarFile)) {
                // Must have standard Manifest
                assertNotNull(jar.getManifest(), "JAR must contain MANIFEST.MF");

                // Must NOT contain split-package platform internal classes (avoids JPMS ResolutionException)
                assertNull(jar.getJarEntry("net/minecraft/network/chat/Component.class"),
                        "JAR must not contain net.minecraft classes: " + jarFile.getName());
                assertNull(jar.getJarEntry("net/minecraft/world/level/block/Block.class"),
                        "JAR must not contain net.minecraft classes: " + jarFile.getName());

                // Must NOT contain orphaned composter blockstate override when mixins are absent
                assertNull(jar.getJarEntry("assets/minecraft/blockstates/composter.json"),
                        "JAR must not contain orphaned composter blockstate without mixins: " + jarFile.getName());

                // Verify valid compiled class bytecode can be parsed by ASM
                JarEntry mainClassEntry = jar.getJarEntry("com/kingodogo/buildscape/BuildScape.class");
                assertNotNull(mainClassEntry, "BuildScape.class must exist in " + jarFile.getName());
                byte[] classBytes = jar.getInputStream(mainClassEntry).readAllBytes();
                ClassReader cr = new ClassReader(classBytes);
                assertEquals("com/kingodogo/buildscape/BuildScape", cr.getClassName());

                if ("universal".equals(parentDirName)) {
                    // Universal Multi-Loader Bundle Validations
                    assertEquals("buildscape-universal-1.18.2.jar", jarFile.getName());
                    assertNotNull(jar.getJarEntry("META-INF/mods.toml"), "Universal must contain Forge manifest");
                    assertNotNull(jar.getJarEntry("META-INF/neoforge.mods.toml"), "Universal must contain NeoForge manifest");
                    assertNotNull(jar.getJarEntry("fabric.mod.json"), "Universal must contain Fabric manifest");
                    assertNotNull(jar.getJarEntry("quilt.mod.json"), "Universal must contain Quilt manifest");
                    assertNotNull(jar.getJarEntry("plugin.yml"), "Universal must contain Paper/Spigot manifest");
                    assertNotNull(jar.getJarEntry("META-INF/services/cpw.mods.modlauncher.serviceapi.ITransformationService"));
                    assertNotNull(jar.getJarEntry("com/kyroxova/bootstrapper/ContinuumBootstrapper.class"));
                    assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));

                    // Verify NeoForge manifest loaderVersion=[1,)
                    String neoToml = new String(jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(neoToml.contains("loaderVersion=\"[1,)\""), "Universal NeoForge manifest must use loaderVersion=[1,)");
                } else if ("forge".equals(parentDirName)) {
                    // Forge Target Validations
                    assertNotNull(jar.getJarEntry("META-INF/mods.toml"), "Forge JAR must contain mods.toml");
                    assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));
                    assertNotNull(jar.getJarEntry("buildscape.png"), "Forge JAR must contain original assets");
                } else if ("neoforge".equals(parentDirName)) {
                    // NeoForge Target Validations
                    assertNotNull(jar.getJarEntry("META-INF/neoforge.mods.toml"), "NeoForge JAR must contain neoforge.mods.toml");
                    assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));

                    // Verify NeoForge loaderVersion=[1,) for javafml
                    String neoToml = new String(jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(neoToml.contains("loaderVersion=\"[1,)\""), "NeoForge manifest must specify loaderVersion=[1,) for javafml provider compatibility");

                    if (jarFile.getName().contains("1.21.1") || jarFile.getName().contains("26.3")) {
                        assertNotNull(jar.getJarEntry("data/buildscape/recipe/golden_jar_from_pattern.json"),
                                "1.21+ NeoForge JAR must have normalized singular recipe path");
                        if (jar.getJarEntry("assets/minecraft/atlases/blocks.json") != null) {
                            String atlasJson = new String(jar.getInputStream(jar.getJarEntry("assets/minecraft/atlases/blocks.json")).readAllBytes(), StandardCharsets.UTF_8);
                            assertFalse(atlasJson.contains(":textures/"), "Atlas JSON must not contain :textures/ prefix on modern targets");
                        }
                        if (jar.getJarEntry("data/buildscape/worldgen/placed_feature/mangrove_checked.json") != null) {
                            String wfJson = new String(jar.getInputStream(jar.getJarEntry("data/buildscape/worldgen/placed_feature/mangrove_checked.json")).readAllBytes(), StandardCharsets.UTF_8);
                            assertTrue(wfJson.contains("\"feature\": \"minecraft:mangrove\""), "Worldgen feature must map to minecraft:mangrove on modern targets");
                            assertTrue(wfJson.contains("\"id\": \"minecraft:mangrove_propagule\""), "Worldgen block state must map to id on modern targets");
                            assertFalse(wfJson.contains("\"Name\":"), "Modern worldgen must not use legacy Name key");
                        }
                        if (jar.getJarEntry("data/minecraft/tags/block/dirt.json") != null) {
                            String tagJson = new String(jar.getInputStream(jar.getJarEntry("data/minecraft/tags/block/dirt.json")).readAllBytes(), StandardCharsets.UTF_8);
                            assertTrue(tagJson.contains("\"required\":false") || tagJson.contains("\"required\": false"),
                                    "Tag entries must be marked optional (required: false)");
                        }

                        // Verify recipe normalization (id instead of item, ingredients string/array)
                        String recipeJson = new String(jar.getInputStream(jar.getJarEntry("data/buildscape/recipe/golden_jar_from_pattern.json")).readAllBytes(), StandardCharsets.UTF_8);
                        assertTrue(recipeJson.contains("\"id\":\"buildscape:golden_jar\"") || recipeJson.contains("\"id\": \"buildscape:golden_jar\""),
                                "Recipe result must use id instead of item");
                        assertNull(jar.getJarEntry("data/minecraft/recipe/snow.json"),
                                "Orphaned vanilla recipe must not exist without mixins");

                        // Verify advancement normalization (icon id instead of item)
                        if (jar.getJarEntry("data/buildscape/advancement/a_full_buildscape_cube.json") != null) {
                            String advJson = new String(jar.getInputStream(jar.getJarEntry("data/buildscape/advancement/a_full_buildscape_cube.json")).readAllBytes(), StandardCharsets.UTF_8);
                            assertTrue(advJson.contains("\"id\":\"buildscape:builders_workbench\"") || advJson.contains("\"id\": \"buildscape:builders_workbench\""),
                                    "Advancement icon must use id instead of item");
                        }
                    }
                } else if ("fabric".equals(parentDirName)) {
                    // Fabric Target Validations
                    assertNotNull(jar.getJarEntry("fabric.mod.json"), "Fabric JAR must contain fabric.mod.json");
                    assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));
                    if (jarFile.getName().contains("1.21.1")) {
                        assertNotNull(jar.getJarEntry("data/buildscape/recipe/golden_jar_from_pattern.json"),
                                "1.21+ Fabric JAR must have normalized singular recipe path");
                    }
                } else if ("quilt".equals(parentDirName)) {
                    // Quilt Target Validations
                    assertNotNull(jar.getJarEntry("quilt.mod.json"), "Quilt JAR must contain quilt.mod.json");
                    assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));
                    if (jarFile.getName().contains("1.21.1")) {
                        assertNotNull(jar.getJarEntry("data/buildscape/recipe/golden_jar_from_pattern.json"),
                                "1.21+ Quilt JAR must have normalized singular recipe path");
                    }
                } else if ("paper".equals(parentDirName) || "spigot".equals(parentDirName)) {
                    // Paper / Spigot Plugin Target Validations
                    assertNotNull(jar.getJarEntry("plugin.yml"), "Paper/Spigot JAR must contain plugin.yml");
                    assertNotNull(jar.getJarEntry("com/kyroxova/continuumlib/shims/BlockInteractionShim.class"));
                }
            }
        }

        // Copy generated NeoForge 26.3 release jar to PrismLauncher instance if available
        File prismModsDir = new File("D:/PrismLauncher/instances/26.1 - Copy/minecraft/mods");
        File neo26Jar = new File(releaseOutputDir, "neoforge/buildscape-neoforge-26.3.jar");
        if (prismModsDir.exists() && neo26Jar.exists()) {
            try {
                Files.copy(neo26Jar.toPath(), new File(prismModsDir, neo26Jar.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[BuildScapeReleaseTest] Successfully deployed " + neo26Jar.getName() + " to PrismLauncher mods directory.");
            } catch (Throwable t) {
                System.out.println("[BuildScapeReleaseTest] Notice: Could not overwrite " + neo26Jar.getName() + " (game instance is likely running): " + t.getMessage());
            }
        }
    }
}
