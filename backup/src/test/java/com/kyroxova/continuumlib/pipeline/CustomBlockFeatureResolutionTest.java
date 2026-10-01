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

public class CustomBlockFeatureResolutionTest {

    @Test
    @DisplayName("Verify Full Custom Block Feature Resolution from Forge 1.18.2 across all 7 target platforms")
    void testCustomBlockFeatureResolution() {
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

        String customBlockSource = """
                package com.kingodogo.buildscape.block;

                import net.minecraft.core.BlockPos;
                import net.minecraft.core.Direction;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.world.InteractionResult;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.item.context.BlockPlaceContext;
                import net.minecraft.world.level.BlockGetter;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.SimpleWaterloggedBlock;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import net.minecraft.world.level.block.state.StateDefinition;
                import net.minecraft.world.level.block.state.properties.BlockStateProperties;
                import net.minecraft.world.level.block.state.properties.BooleanProperty;
                import net.minecraft.world.level.block.state.properties.DirectionProperty;
                import net.minecraft.world.level.block.state.properties.IntegerProperty;
                import net.minecraft.world.phys.BlockHitResult;
                import net.minecraft.world.phys.shapes.CollisionContext;
                import net.minecraft.world.phys.shapes.Shapes;
                import net.minecraft.world.phys.shapes.VoxelShape;

                public class CustomApparatusBlock extends Block implements SimpleWaterloggedBlock {
                    public static final String MOD_ID = "buildscape";
                    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
                    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
                    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

                    private static final VoxelShape SHAPE_NORTH = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 8.0D);
                    private static final VoxelShape SHAPE_SOUTH = Block.box(0.0D, 0.0D, 8.0D, 16.0D, 8.0D, 16.0D);
                    private static final VoxelShape SHAPE_WEST = Block.box(0.0D, 0.0D, 0.0D, 8.0D, 8.0D, 16.0D);
                    private static final VoxelShape SHAPE_EAST = Block.box(8.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

                    public CustomApparatusBlock(BlockBehaviour.Properties properties) {
                        super(properties);
                        this.registerDefaultState(this.stateDefinition.any()
                                .setValue(FACING, Direction.NORTH)
                                .setValue(WATERLOGGED, false)
                                .setValue(POWER, 0));
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
                        builder.add(FACING, WATERLOGGED, POWER);
                    }

                    @Override
                    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
                        return InteractionResult.SUCCESS;
                    }
                }
                """;

        ContinuumPipeline pipeline = new ContinuumPipeline(baseSpec, targets);
        Map<TargetSpec, String> emittedSources = new LinkedHashMap<>();
        ResolutionReport report = pipeline.processSource(customBlockSource, emittedSources);

        CirCompilationUnit cir = pipeline.getLastAnalyzedCir();
        assertNotNull(cir);
        assertEquals("CustomApparatusBlock", cir.getPrimaryClassName());
        assertEquals("Block", cir.getSuperClass());
        assertTrue(cir.getImplementedInterfaces().contains("SimpleWaterloggedBlock"));

        // 1. Verify Forge 1.19.2
        String forgeSource = emittedSources.get(TargetSpec.of("1.19.2", "forge"));
        assertNotNull(forgeSource);
        assertTrue(forgeSource.contains("public class CustomApparatusBlock extends Block implements SimpleWaterloggedBlock"));
        assertTrue(forgeSource.contains("switch"));

        // 2. Verify NeoForge 1.21.1 and 26.3 (method adapted to useWithoutItem)
        String neoSource = emittedSources.get(TargetSpec.of("1.21.1", "neoforge"));
        assertNotNull(neoSource);
        assertTrue(neoSource.contains("public class CustomApparatusBlock extends Block implements SimpleWaterloggedBlock"));
        assertTrue(neoSource.contains("useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)"));

        String neo263Source = emittedSources.get(TargetSpec.of("26.3", "neoforge"));
        assertNotNull(neo263Source);
        assertTrue(neo263Source.contains("useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)"));

        // 3. Verify Fabric 1.21.1 and Quilt 1.21.1
        String fabricSource = emittedSources.get(TargetSpec.of("1.21.1", "fabric"));
        assertNotNull(fabricSource);
        assertTrue(fabricSource.contains("public class CustomApparatusBlock extends Block implements SimpleWaterloggedBlock"));
        assertTrue(fabricSource.contains("useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)"));

        String quiltSource = emittedSources.get(TargetSpec.of("1.21.1", "quilt"));
        assertNotNull(quiltSource);
        assertTrue(quiltSource.contains("public class CustomApparatusBlock extends Block implements SimpleWaterloggedBlock"));

        // 4. Verify Paper and Spigot 1.21.1 (JavaPlugin with PlayerInteractEvent)
        String paperSource = emittedSources.get(TargetSpec.of("1.21.1", "paper"));
        assertNotNull(paperSource);
        assertTrue(paperSource.contains("public class CustomApparatusBlock extends JavaPlugin implements Listener"));
        assertTrue(paperSource.contains("@EventHandler"));
        assertTrue(paperSource.contains("public void onPlayerInteract(PlayerInteractEvent event)"));
        assertTrue(paperSource.contains("public void onEnable()"));

        String spigotSource = emittedSources.get(TargetSpec.of("1.21.1", "spigot"));
        assertNotNull(spigotSource);
        assertTrue(spigotSource.contains("public class CustomApparatusBlock extends JavaPlugin implements Listener"));
        assertTrue(spigotSource.contains("@EventHandler"));

        // 5. In-memory javac compilation verification across all 7 targets
        for (TargetSpec target : targets) {
            Boolean compiled = report.getTargetCompilationStatus().get(target);
            assertNotNull(compiled, "Target " + target.getLoader() + ":" + target.getVersion() + " should have compilation status");
            assertTrue(compiled, "Target " + target.getLoader() + ":" + target.getVersion() + " failed to compile:\n" + emittedSources.get(target));
        }
    }
}
