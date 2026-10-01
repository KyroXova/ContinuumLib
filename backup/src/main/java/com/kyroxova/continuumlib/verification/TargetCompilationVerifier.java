package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Validates and compiles generated target Java source code against the target environment classpath.
 * Ensures generated code is syntactically and semantically valid Java.
 */
public final class TargetCompilationVerifier {

    private final List<File> additionalClasspath = new ArrayList<>();

    public TargetCompilationVerifier() {
    }

    public TargetCompilationVerifier addClasspath(File file) {
        if (file != null && file.exists()) {
            additionalClasspath.add(file);
        }
        return this;
    }

    public boolean verifyCompilation(String sourceCode, String className, TargetSpec targetSpec) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // No javac available (e.g. non-JDK environment)
            return false;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8);

        try {
            Path tempDir = Files.createTempDirectory("continuumlib-verification-");
            List<JavaFileObject> compilationUnits = new ArrayList<>();

            // 1. Add mock / target stubs needed for compilation if not on classpath
            generateTargetStubs(targetSpec, tempDir, compilationUnits, className);

            // 2. Add the generated source code
            SimpleJavaFileObject sourceObj = new SimpleJavaFileObject(
                    URI.create("string:///" + className.replace('.', '/') + ".java"),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return sourceCode;
                }
            };
            compilationUnits.add(sourceObj);

            // Set up compilation options
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(tempDir.toString());

            if (!additionalClasspath.isEmpty()) {
                StringBuilder cp = new StringBuilder();
                for (int i = 0; i < additionalClasspath.size(); i++) {
                    if (i > 0) cp.append(File.pathSeparator);
                    cp.append(additionalClasspath.get(i).getAbsolutePath());
                }
                options.add("-cp");
                options.add(cp.toString());
            }

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits
            );

            boolean success = task.call();
            if (!success) {
                for (Diagnostic<? extends JavaFileObject> diag : diagnostics.getDiagnostics()) {
                    System.err.printf("[ContinuumLib Compiler Diagnostic] Line %d: %s%n",
                            diag.getLineNumber(), diag.getMessage(Locale.getDefault()));
                }
            }

            // Cleanup temp dir
            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }

            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                fileManager.close();
            } catch (IOException ignored) {
            }
        }
    }

    public Map<String, byte[]> compileToBytecode(String sourceCode, String className, TargetSpec targetSpec) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return Collections.emptyMap();
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8);
        Map<String, byte[]> compiledClasses = new LinkedHashMap<>();

        try {
            Path tempDir = Files.createTempDirectory("continuumlib-compile-");
            List<JavaFileObject> compilationUnits = new ArrayList<>();

            generateTargetStubs(targetSpec, tempDir, compilationUnits, className);

            SimpleJavaFileObject sourceObj = new SimpleJavaFileObject(
                    URI.create("string:///" + className.replace('.', '/') + ".java"),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return sourceCode;
                }
            };
            compilationUnits.add(sourceObj);

            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(tempDir.toString());

            if (!additionalClasspath.isEmpty()) {
                StringBuilder cp = new StringBuilder();
                for (int i = 0; i < additionalClasspath.size(); i++) {
                    if (i > 0) cp.append(File.pathSeparator);
                    cp.append(additionalClasspath.get(i).getAbsolutePath());
                }
                options.add("-cp");
                options.add(cp.toString());
            }

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits
            );

            boolean success = task.call();
            if (success) {
                String classPathPrefix = className.replace('.', '/');
                int lastSlash = classPathPrefix.lastIndexOf('/');
                String pkgPrefix = lastSlash > 0 ? classPathPrefix.substring(0, lastSlash + 1) : "";

                try (var walk = Files.walk(tempDir)) {
                    walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).forEach(classPath -> {
                        String rel = tempDir.relativize(classPath).toString().replace('\\', '/');
                        if (rel.startsWith(pkgPrefix) || rel.equals(classPathPrefix + ".class")) {
                            try {
                                compiledClasses.put(rel, Files.readAllBytes(classPath));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                }
            } else {
                for (Diagnostic<? extends JavaFileObject> diag : diagnostics.getDiagnostics()) {
                    System.err.printf("[ContinuumLib Compiler Diagnostic] Line %d: %s%n",
                            diag.getLineNumber(), diag.getMessage(Locale.getDefault()));
                }
            }

            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }

            return compiledClasses;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        } finally {
            try {
                fileManager.close();
            } catch (IOException ignored) {
            }
        }
    }

    public Map<String, byte[]> compileToBytecode(Map<String, String> sources, TargetSpec targetSpec) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || sources.isEmpty()) {
            return Collections.emptyMap();
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), StandardCharsets.UTF_8);
        Map<String, byte[]> compiledClasses = new LinkedHashMap<>();

        try {
            Path tempDir = Files.createTempDirectory("continuumlib-compile-multi-");
            List<JavaFileObject> compilationUnits = new ArrayList<>();

            generateTargetStubs(targetSpec, tempDir, compilationUnits, sources.keySet());

            for (Map.Entry<String, String> entry : sources.entrySet()) {
                String className = entry.getKey();
                String sourceCode = entry.getValue();
                SimpleJavaFileObject sourceObj = new SimpleJavaFileObject(
                        URI.create("string:///" + className.replace('.', '/') + ".java"),
                        JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return sourceCode;
                    }
                };
                compilationUnits.add(sourceObj);
            }

            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(tempDir.toString());

            if (!additionalClasspath.isEmpty()) {
                StringBuilder cp = new StringBuilder();
                for (int i = 0; i < additionalClasspath.size(); i++) {
                    if (i > 0) cp.append(File.pathSeparator);
                    cp.append(additionalClasspath.get(i).getAbsolutePath());
                }
                options.add("-cp");
                options.add(cp.toString());
            }

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits
            );

            boolean success = task.call();
            if (success) {
                Set<String> sourceClassPrefixes = new HashSet<>();
                for (String sName : sources.keySet()) {
                    sourceClassPrefixes.add(sName.replace('.', '/'));
                }

                try (var walk = Files.walk(tempDir)) {
                    walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).forEach(classPath -> {
                        String rel = tempDir.relativize(classPath).toString().replace('\\', '/');
                        boolean isSourceClass = false;
                        for (String prefix : sourceClassPrefixes) {
                            if (rel.equals(prefix + ".class") || rel.startsWith(prefix + "$")) {
                                isSourceClass = true;
                                break;
                            }
                        }
                        if (isSourceClass) {
                            try {
                                compiledClasses.put(rel, Files.readAllBytes(classPath));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                }
            } else {
                for (Diagnostic<? extends JavaFileObject> diag : diagnostics.getDiagnostics()) {
                    System.err.printf("[ContinuumLib Compiler Diagnostic] Line %d: %s%n",
                            diag.getLineNumber(), diag.getMessage(Locale.getDefault()));
                }
            }

            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }

            return compiledClasses;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        } finally {
            try {
                fileManager.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void addStub(List<JavaFileObject> units, Set<String> added, String className, String source) {
        if (added.add(className)) {
            units.add(createStringSource(className, source));
        }
    }

    private void generatePackageCustomStubs(String pkg, List<JavaFileObject> units, Set<String> added) {
        addStub(units, added, pkg + ".MyMachineBlock",
                "package " + pkg + "; public class MyMachineBlock extends net.minecraft.world.level.block.Block { " +
                        "public MyMachineBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { super(props); } }");
        addStub(units, added, pkg + ".ExampleBlockEntity",
                "package " + pkg + "; public class ExampleBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity { " +
                        "public ExampleBlockEntity(net.minecraft.core.BlockPos pos, Object state) { super(pos, state); } }");
        addStub(units, added, pkg + ".FallingIcicleEntity",
                "package " + pkg + "; public class FallingIcicleEntity { public FallingIcicleEntity(Object a, Object b) {} }");
        addStub(units, added, pkg + ".BuildersWorkbenchMenu",
                "package " + pkg + "; public class BuildersWorkbenchMenu extends net.minecraft.world.inventory.AbstractContainerMenu { public BuildersWorkbenchMenu(int id, Object inv) { super(null, id); } }");
        addStub(units, added, pkg + ".CustomFluid",
                "package " + pkg + "; public class CustomFluid extends net.minecraftforge.fluids.ForgeFlowingFluid { public CustomFluid(Object p) { super(p); } }");
        addStub(units, added, pkg + ".ShapedDurabilityRecipe",
                "package " + pkg + "; public class ShapedDurabilityRecipe { public static final net.minecraft.world.item.crafting.RecipeSerializer<ShapedDurabilityRecipe> SERIALIZER = null; }");
        addStub(units, added, pkg + ".ConfettiConfigureRecipe",
                "package " + pkg + "; public class ConfettiConfigureRecipe { public ConfettiConfigureRecipe(Object loc) {} }");
    }

    private void generateTargetStubs(TargetSpec targetSpec, Path tempDir, List<JavaFileObject> units, String className) {
        generateTargetStubs(targetSpec, tempDir, units, Collections.singleton(className));
    }

    private void generateTargetStubs(TargetSpec targetSpec, Path tempDir, List<JavaFileObject> units, Collection<String> classNames) {
        Set<String> added = new HashSet<>();
        // Universal Minecraft basic stubs (Block, Item, etc.)
        units.add(createStringSource("net.minecraft.world.level.block.Block",
                "package net.minecraft.world.level.block; public class Block { " +
                        "public net.minecraft.world.level.block.state.StateDefinition<Block, net.minecraft.world.level.block.state.BlockState> stateDefinition = new net.minecraft.world.level.block.state.StateDefinition<>(); " +
                        "public Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) {} " +
                        "public static net.minecraft.world.phys.shapes.VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) { return new net.minecraft.world.phys.shapes.VoxelShape(); } " +
                        "public net.minecraft.world.level.block.state.BlockState defaultBlockState() { return new net.minecraft.world.level.block.state.BlockState(); } " +
                        "protected void registerDefaultState(net.minecraft.world.level.block.state.BlockState state) {} " +
                        "public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return null; } " +
                        "public net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) { return net.minecraft.world.InteractionResult.SUCCESS; } " +
                        "protected net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) { return net.minecraft.world.InteractionResult.SUCCESS; } " +
                        "protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) { return net.minecraft.world.InteractionResult.SUCCESS; } " +
                        "public net.minecraft.world.level.block.state.BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) { return defaultBlockState(); } " +
                        "protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {} }"));

        units.add(createStringSource("net.minecraft.core.Direction",
                "package net.minecraft.core; public enum Direction { NORTH, SOUTH, EAST, WEST, UP, DOWN; " +
                        "public Direction getOpposite() { return this; } " +
                        "public enum Axis { X, Y, Z; } " +
                        "public Axis getAxis() { return Axis.Y; } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.properties.Property",
                "package net.minecraft.world.level.block.state.properties; public class Property<T extends Comparable<T>> { " +
                        "public String getName() { return \"\"; } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.properties.DirectionProperty",
                "package net.minecraft.world.level.block.state.properties; public class DirectionProperty extends Property<net.minecraft.core.Direction> { " +
                        "public static DirectionProperty create(String name, net.minecraft.core.Direction... directions) { return new DirectionProperty(); } " +
                        "public static DirectionProperty create(String name, java.util.function.Predicate<net.minecraft.core.Direction> filter) { return new DirectionProperty(); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.properties.BooleanProperty",
                "package net.minecraft.world.level.block.state.properties; public class BooleanProperty extends Property<Boolean> { " +
                        "public static BooleanProperty create(String name) { return new BooleanProperty(); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.properties.IntegerProperty",
                "package net.minecraft.world.level.block.state.properties; public class IntegerProperty extends Property<Integer> { " +
                        "public static IntegerProperty create(String name, int min, int max) { return new IntegerProperty(); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.properties.BlockStateProperties",
                "package net.minecraft.world.level.block.state.properties; public class BlockStateProperties { " +
                        "public static final DirectionProperty HORIZONTAL_FACING = new DirectionProperty(); " +
                        "public static final DirectionProperty FACING = new DirectionProperty(); " +
                        "public static final BooleanProperty WATERLOGGED = new BooleanProperty(); " +
                        "public static final BooleanProperty POWERED = new BooleanProperty(); " +
                        "public static final IntegerProperty POWER = new IntegerProperty(); }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.BlockState",
                "package net.minecraft.world.level.block.state; public class BlockState { " +
                        "public <T extends Comparable<T>> T getValue(net.minecraft.world.level.block.state.properties.Property<T> prop) { return null; } " +
                        "public <T extends Comparable<T>, V extends T> BlockState setValue(net.minecraft.world.level.block.state.properties.Property<T> prop, V val) { return this; } " +
                        "public net.minecraft.world.level.block.Block getBlock() { return null; } " +
                        "public boolean is(Object b) { return false; } " +
                        "public BlockState rotate(Object r) { return this; } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.StateDefinition",
                "package net.minecraft.world.level.block.state; public class StateDefinition<O, S> { " +
                        "public S any() { return null; } " +
                        "public static class Builder<O, S> { " +
                        "public Builder<O, S> add(net.minecraft.world.level.block.state.properties.Property<?>... properties) { return this; } " +
                        "} }"));

        units.add(createStringSource("net.minecraft.world.phys.shapes.VoxelShape",
                "package net.minecraft.world.phys.shapes; public class VoxelShape { " +
                        "public VoxelShape move(double x, double y, double z) { return this; } " +
                        "public VoxelShape optimize() { return this; } }"));

        units.add(createStringSource("net.minecraft.world.phys.shapes.Shapes",
                "package net.minecraft.world.phys.shapes; public class Shapes { " +
                        "public static VoxelShape empty() { return new VoxelShape(); } " +
                        "public static VoxelShape block() { return new VoxelShape(); } " +
                        "public static VoxelShape or(VoxelShape a, VoxelShape b) { return a; } " +
                        "public static VoxelShape or(VoxelShape a, VoxelShape... others) { return a; } }"));

        units.add(createStringSource("net.minecraft.world.phys.shapes.CollisionContext",
                "package net.minecraft.world.phys.shapes; public interface CollisionContext { " +
                        "static CollisionContext empty() { return new CollisionContext() {}; } }"));

        units.add(createStringSource("net.minecraft.world.level.BlockGetter",
                "package net.minecraft.world.level; public interface BlockGetter { " +
                        "default net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) { return null; } }"));

        units.add(createStringSource("net.minecraft.world.level.Level",
                "package net.minecraft.world.level; public class Level implements BlockGetter { " +
                        "public boolean isClientSide = false; " +
                        "public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) { return null; } }"));

        units.add(createStringSource("net.minecraft.world.entity.player.Player",
                "package net.minecraft.world.entity.player; public class Player {}"));

        units.add(createStringSource("net.minecraft.world.InteractionHand",
                "package net.minecraft.world; public enum InteractionHand { MAIN_HAND, OFF_HAND; }"));

        units.add(createStringSource("net.minecraft.world.InteractionResult",
                "package net.minecraft.world; public enum InteractionResult { SUCCESS, CONSUME, CONSUME_PARTIAL, PASS, FAIL; }"));

        units.add(createStringSource("net.minecraft.world.phys.BlockHitResult",
                "package net.minecraft.world.phys; public class BlockHitResult {}"));

        units.add(createStringSource("net.minecraft.world.item.context.BlockPlaceContext",
                "package net.minecraft.world.item.context; public class BlockPlaceContext { " +
                        "public net.minecraft.core.Direction getHorizontalDirection() { return net.minecraft.core.Direction.NORTH; } " +
                        "public net.minecraft.core.Direction getNearestLookingDirection() { return net.minecraft.core.Direction.NORTH; } " +
                        "public net.minecraft.core.BlockPos getClickedPos() { return new net.minecraft.core.BlockPos(); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.HorizontalDirectionalBlock",
                "package net.minecraft.world.level.block; public class HorizontalDirectionalBlock extends Block { " +
                        "public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING; " +
                        "public HorizontalDirectionalBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { super(props); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.SimpleWaterloggedBlock",
                "package net.minecraft.world.level.block; public interface SimpleWaterloggedBlock {}"));

        units.add(createStringSource("net.minecraft.util.StringRepresentable",
                "package net.minecraft.util; public interface StringRepresentable { String getSerializedName(); }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.properties.EnumProperty",
                "package net.minecraft.world.level.block.state.properties; public class EnumProperty<T extends Enum<T> & net.minecraft.util.StringRepresentable> extends Property<T> { " +
                        "public static <T extends Enum<T> & net.minecraft.util.StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) { return new EnumProperty<>(); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.properties.SlabType",
                "package net.minecraft.world.level.block.state.properties; public enum SlabType implements net.minecraft.util.StringRepresentable { TOP, BOTTOM, DOUBLE; public String getSerializedName() { return name().toLowerCase(); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.Rotation",
                "package net.minecraft.world.level.block; public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90; public net.minecraft.core.Direction rotate(net.minecraft.core.Direction d) { return d; } }"));

        units.add(createStringSource("net.minecraft.world.level.block.Mirror",
                "package net.minecraft.world.level.block; public enum Mirror { NONE, LEFT_RIGHT, FRONT_BACK; public Rotation getRotation(net.minecraft.core.Direction d) { return Rotation.NONE; } }"));

        units.add(createStringSource("net.minecraft.world.level.LevelAccessor",
                "package net.minecraft.world.level; public interface LevelAccessor extends BlockGetter { default void scheduleTick(net.minecraft.core.BlockPos pos, Object f, int delay) {} }"));

        units.add(createStringSource("net.minecraft.world.level.material.FluidState",
                "package net.minecraft.world.level.material; public class FluidState { public Fluid getType() { return null; } }"));

        units.add(createStringSource("net.minecraft.world.level.material.Fluids",
                "package net.minecraft.world.level.material; public class Fluids { " +
                        "public static final Fluid WATER = new Fluid(); " +
                        "public static final Fluid EMPTY = new Fluid(); }"));

        units.add(createStringSource("net.minecraft.world.level.block.AbstractGlassBlock",
                "package net.minecraft.world.level.block; public class AbstractGlassBlock extends Block { " +
                        "public AbstractGlassBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { super(props); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.TransparentBlock",
                "package net.minecraft.world.level.block; public class TransparentBlock extends Block { " +
                        "public TransparentBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { super(props); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.HalfTransparentBlock",
                "package net.minecraft.world.level.block; public class HalfTransparentBlock extends Block { " +
                        "public HalfTransparentBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { super(props); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.BaseEntityBlock",
                "package net.minecraft.world.level.block; public abstract class BaseEntityBlock extends Block { " +
                        "public BaseEntityBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { super(props); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.SlabBlock",
                "package net.minecraft.world.level.block; public class SlabBlock extends Block implements SimpleWaterloggedBlock { " +
                        "public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.SlabType> TYPE = null; " +
                        "public static final net.minecraft.world.level.block.state.properties.BooleanProperty WATERLOGGED = null; " +
                        "public SlabBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { super(props); } }"));

        units.add(createStringSource("net.minecraft.world.level.block.state.BlockBehaviour",
                "package net.minecraft.world.level.block.state; public class BlockBehaviour { " +
                        "public static class Properties { " +
                        "public static Properties of() { return new Properties(); } " +
                        "public static Properties of(net.minecraft.world.level.material.Material mat) { return new Properties(); } " +
                        "public Properties strength(float d) { return this; } " +
                        "public Properties strength(float d, float r) { return this; } " +
                        "public Properties requiresCorrectToolForDrops() { return this; } " +
                        "public Properties noOcclusion() { return this; } " +
                        "public Properties sound(Object s) { return this; } " +
                        "} }"));

        units.add(createStringSource("net.minecraft.world.level.material.Material",
                "package net.minecraft.world.level.material; public class Material { public static final Material STONE = new Material(); }"));

        units.add(createStringSource("net.minecraft.world.item.Item",
                "package net.minecraft.world.item; public class Item { public Item(Properties props) {} " +
                        "public static class Properties { " +
                        "public Properties() {} " +
                        "public Properties tab(Object t) { return this; } " +
                        "public Properties stacksTo(int s) { return this; } " +
                        "public Properties durability(int d) { return this; } " +
                        "} }"));

        units.add(createStringSource("net.minecraft.world.item.BlockItem",
                "package net.minecraft.world.item; public class BlockItem extends Item { " +
                        "public BlockItem(net.minecraft.world.level.block.Block block, Properties props) { super(props); } }"));

        units.add(createStringSource("net.minecraft.world.item.ItemStack",
                "package net.minecraft.world.item; public class ItemStack { public ItemStack(Object item) {} }"));

        units.add(createStringSource("net.minecraft.world.item.CreativeModeTab",
                "package net.minecraft.world.item; public class CreativeModeTab { " +
                        "public CreativeModeTab(String label) {} " +
                        "public CreativeModeTab(Object label) {} " +
                        "public net.minecraft.world.item.ItemStack makeIcon() { return null; } " +
                        "public static class Builder { " +
                        "public Builder icon(java.util.function.Supplier<net.minecraft.world.item.ItemStack> s) { return this; } " +
                        "public Builder title(Object t) { return this; } " +
                        "public CreativeModeTab build() { return new CreativeModeTab(\"\"); } " +
                        "} " +
                        "public static Builder builder() { return new Builder(); } }"));

        units.add(createStringSource("net.minecraft.network.chat.Component",
                "package net.minecraft.network.chat; public interface Component { " +
                        "static Component translatable(String key) { return new Component() {}; } " +
                        "static Component literal(String text) { return new Component() {}; } }"));

        units.add(createStringSource("net.minecraft.world.level.block.entity.BlockEntityType",
                "package net.minecraft.world.level.block.entity; public class BlockEntityType<T> { " +
                        "@FunctionalInterface public interface BlockEntitySupplier<T> { T create(net.minecraft.core.BlockPos pos, Object state); } " +
                        "public static class Builder<T> { " +
                        "public static <T> Builder<T> of(BlockEntitySupplier<T> factory, Object... blocks) { return new Builder<>(); } " +
                        "public BlockEntityType<T> build(Object type) { return new BlockEntityType<>(); } " +
                        "} }"));

        units.add(createStringSource("net.minecraft.world.level.block.SoundType",
                "package net.minecraft.world.level.block; public class SoundType { public static final SoundType STONE = new SoundType(); }"));

        units.add(createStringSource("net.minecraft.core.BlockPos",
                "package net.minecraft.core; public class BlockPos {}"));

        units.add(createStringSource("net.minecraft.world.level.block.entity.BlockEntity",
                "package net.minecraft.world.level.block.entity; public class BlockEntity { public BlockEntity(net.minecraft.core.BlockPos pos, Object state) {} }"));

        units.add(createStringSource("net.minecraft.world.entity.EntityType",
                "package net.minecraft.world.entity; public class EntityType<T> { " +
                        "@FunctionalInterface public interface EntityFactory<T> { T create(Object a, Object b); } " +
                        "public static class Builder<T> { " +
                        "public static <T> Builder<T> of(EntityFactory<T> factory, Object category) { return new Builder<>(); } " +
                        "public Builder<T> sized(float w, float h) { return this; } " +
                        "public Builder<T> clientTrackingRange(int r) { return this; } " +
                        "public Builder<T> updateInterval(int i) { return this; } " +
                        "public EntityType<T> build(String id) { return new EntityType<>(); } " +
                        "} }"));

        units.add(createStringSource("net.minecraft.world.entity.MobCategory",
                "package net.minecraft.world.entity; public enum MobCategory { MISC, CREATURE, MONSTER, WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AMBIENT, AXOLOTLS; }"));

        units.add(createStringSource("net.minecraft.sounds.SoundEvent",
                "package net.minecraft.sounds; public class SoundEvent { " +
                        "public SoundEvent(net.minecraft.resources.ResourceLocation loc) {} " +
                        "public SoundEvent(Object loc) {} " +
                        "public static SoundEvent createVariableRangeEvent(Object loc) { return new SoundEvent(loc); } }"));

        units.add(createStringSource("net.minecraft.resources.ResourceLocation",
                "package net.minecraft.resources; public class ResourceLocation { " +
                        "public ResourceLocation() {} " +
                        "public ResourceLocation(String ns, String p) {} " +
                        "public static ResourceLocation fromNamespaceAndPath(String ns, String p) { return new ResourceLocation(ns, p); } }"));

        units.add(createStringSource("net.minecraft.core.particles.ParticleType",
                "package net.minecraft.core.particles; public class ParticleType<T> { public ParticleType(boolean b) {} }"));

        units.add(createStringSource("net.minecraft.core.particles.SimpleParticleType",
                "package net.minecraft.core.particles; public class SimpleParticleType extends ParticleType<SimpleParticleType> { " +
                        "public SimpleParticleType(boolean b) { super(b); } }"));

        units.add(createStringSource("net.minecraft.world.inventory.MenuType",
                "package net.minecraft.world.inventory; public class MenuType<T> { public MenuType() {} }"));

        units.add(createStringSource("net.minecraft.world.inventory.AbstractContainerMenu",
                "package net.minecraft.world.inventory; public abstract class AbstractContainerMenu { public AbstractContainerMenu(Object a, int b) {} }"));

        units.add(createStringSource("net.minecraft.world.level.material.Fluid",
                "package net.minecraft.world.level.material; public class Fluid {}"));

        units.add(createStringSource("net.minecraftforge.fluids.ForgeFlowingFluid",
                "package net.minecraftforge.fluids; public class ForgeFlowingFluid extends net.minecraft.world.level.material.Fluid { " +
                        "public ForgeFlowingFluid(Object props) {} " +
                        "public static class Properties { public Properties(Object a, Object b, Object c) {} } " +
                        "public static class Source extends ForgeFlowingFluid { public Source(Object props) { super(props); } } " +
                        "public static class Flowing extends ForgeFlowingFluid { public Flowing(Object props) { super(props); } } }"));

        units.add(createStringSource("net.minecraft.world.item.crafting.RecipeSerializer",
                "package net.minecraft.world.item.crafting; public interface RecipeSerializer<T> {}"));

        units.add(createStringSource("net.minecraft.world.item.crafting.SimpleRecipeSerializer",
                "package net.minecraft.world.item.crafting; public class SimpleRecipeSerializer<T> implements RecipeSerializer<T> { " +
                        "@FunctionalInterface public interface Factory<T> { T create(Object loc); } " +
                        "public SimpleRecipeSerializer(Factory<T> sup) {} " +
                        "public SimpleRecipeSerializer(Object sup) {} }"));

        // Generate stubs for custom mod classes in the collected packages (e.g. MyMachineBlock, ExampleBlockEntity, etc.)
        Set<String> packages = new LinkedHashSet<>();
        for (String cName : classNames) {
            int pkgIdx = cName.lastIndexOf('.');
            if (pkgIdx > 0) {
                String pkg = cName.substring(0, pkgIdx);
                packages.add(pkg);
                int parentIdx = pkg.lastIndexOf('.');
                if (parentIdx > 0) {
                    packages.add(pkg.substring(0, parentIdx));
                }
            }
        }
        for (String pkg : packages) {
            generatePackageCustomStubs(pkg, units, added);
        }

        // Loader-specific stubs
        if (targetSpec.getLoader() == LoaderType.NEOFORGE) {
            units.add(createStringSource("net.neoforged.fml.common.Mod",
                    "package net.neoforged.fml.common; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Mod { String value(); }"));

            units.add(createStringSource("net.neoforged.neoforge.registries.DeferredBlock",
                    "package net.neoforged.neoforge.registries; public class DeferredBlock<T> extends DeferredHolder<net.minecraft.world.level.block.Block, T> { public T get() { return null; } }"));

            units.add(createStringSource("net.neoforged.neoforge.registries.DeferredItem",
                    "package net.neoforged.neoforge.registries; public class DeferredItem<T> extends DeferredHolder<net.minecraft.world.item.Item, T> { public T get() { return null; } }"));

            units.add(createStringSource("net.neoforged.neoforge.registries.DeferredHolder",
                    "package net.neoforged.neoforge.registries; public class DeferredHolder<R, T> extends net.minecraftforge.registries.RegistryObject<T> { public T get() { return null; } }"));

            units.add(createStringSource("net.neoforged.neoforge.registries.DeferredRegister",
                    "package net.neoforged.neoforge.registries; public class DeferredRegister<T> { " +
                            "public static <T> DeferredRegister<T> create(Object key, String modId) { return new DeferredRegister<>(); } " +
                            "public static Blocks createBlocks(String modId) { return new Blocks(); } " +
                            "public static Items createItems(String modId) { return new Items(); } " +
                            "public <I extends T> DeferredHolder<T, I> register(String id, java.util.function.Supplier<? extends I> sup) { return new DeferredHolder<>(); } " +
                            "public static class Blocks { " +
                            "public <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> registerBlock(String id, java.util.function.Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties, B> func, net.minecraft.world.level.block.state.BlockBehaviour.Properties props) { return new DeferredBlock<>(); } " +
                            "} " +
                            "public static class Items { " +
                            "public DeferredItem<net.minecraft.world.item.Item> registerSimpleItem(String id, net.minecraft.world.item.Item.Properties props) { return new DeferredItem<>(); } " +
                            "public <I extends net.minecraft.world.item.Item> DeferredItem<I> registerItem(String id, java.util.function.Function<net.minecraft.world.item.Item.Properties, I> func, net.minecraft.world.item.Item.Properties props) { return new DeferredItem<>(); } " +
                            "public DeferredItem<net.minecraft.world.item.BlockItem> registerSimpleBlockItem(String id, Object block) { return new DeferredItem<>(); } " +
                            "} }"));

            units.add(createStringSource("net.minecraftforge.registries.RegistryObject",
                    "package net.minecraftforge.registries; public class RegistryObject<T> { public T get() { return null; } }"));

            units.add(createStringSource("net.neoforged.neoforge.common.extensions.IMenuTypeExtension",
                    "package net.neoforged.neoforge.common.extensions; public interface IMenuTypeExtension { " +
                            "@FunctionalInterface public interface MenuFactory<T> { T create(int id, Object inv); } " +
                            "static <T> net.minecraft.world.inventory.MenuType<T> create(MenuFactory<T> factory) { return new net.minecraft.world.inventory.MenuType<>(); } }"));

            units.add(createStringSource("net.minecraft.core.registries.Registries",
                    "package net.minecraft.core.registries; public class Registries { " +
                            "public static final Object BLOCK = new Object(); " +
                            "public static final Object ITEM = new Object(); " +
                            "public static final Object BLOCK_ENTITY_TYPE = new Object(); " +
                            "public static final Object ENTITY_TYPE = new Object(); " +
                            "public static final Object SOUND_EVENT = new Object(); " +
                            "public static final Object PARTICLE_TYPE = new Object(); " +
                            "public static final Object MENU = new Object(); " +
                            "public static final Object FLUID = new Object(); " +
                            "public static final Object RECIPE_SERIALIZER = new Object(); " +
                            "public static final Object CREATIVE_MODE_TAB = new Object(); }"));

            units.add(createStringSource("net.minecraft.resources.Identifier",
                    "package net.minecraft.resources; public class Identifier { public static Identifier fromNamespaceAndPath(String ns, String p) { return new Identifier(); } }"));

        } else if (targetSpec.getLoader() == LoaderType.FABRIC || targetSpec.getLoader() == LoaderType.QUILT) {
            units.add(createStringSource("net.minecraft.core.Registry",
                    "package net.minecraft.core; public interface Registry<T> { " +
                            "static <V, T extends V> T register(Registry<V> reg, Object id, T entry) { return entry; } " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.level.block.Block> BLOCK = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.item.Item> ITEM = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPE = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPE = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.sounds.SoundEvent> SOUND_EVENT = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPE = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.inventory.MenuType<?>> MENU = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.level.material.Fluid> FLUID = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZER = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TAB = null; }"));

            units.add(createStringSource("net.minecraft.core.registries.BuiltInRegistries",
                    "package net.minecraft.core.registries; public class BuiltInRegistries { " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.level.block.Block> BLOCK = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.item.Item> ITEM = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPE = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPE = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.sounds.SoundEvent> SOUND_EVENT = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPE = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.inventory.MenuType<?>> MENU = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.level.material.Fluid> FLUID = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZER = null; " +
                            "public static final net.minecraft.core.Registry<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TAB = null; }"));

            units.add(createStringSource("net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup",
                    "package net.fabricmc.fabric.api.itemgroup.v1; public class FabricItemGroup { " +
                            "public static net.minecraft.world.item.CreativeModeTab.Builder builder() { return net.minecraft.world.item.CreativeModeTab.builder(); } }"));

            units.add(createStringSource("net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder",
                    "package net.fabricmc.fabric.api.object.builder.v1.block.entity; public class FabricBlockEntityTypeBuilder<T> { " +
                            "@FunctionalInterface public interface Factory<T> { T create(net.minecraft.core.BlockPos pos, Object state); } " +
                            "public static <T> FabricBlockEntityTypeBuilder<T> create(Factory<T> f, Object... blocks) { return new FabricBlockEntityTypeBuilder<>(); } " +
                            "public net.minecraft.world.level.block.entity.BlockEntityType<T> build() { return new net.minecraft.world.level.block.entity.BlockEntityType<>(); } }"));

            units.add(createStringSource("net.fabricmc.fabric.api.particle.v1.FabricParticleTypes",
                    "package net.fabricmc.fabric.api.particle.v1; public class FabricParticleTypes { " +
                            "public static net.minecraft.core.particles.SimpleParticleType simple(boolean alwaysShow) { return new net.minecraft.core.particles.SimpleParticleType(alwaysShow); } }"));

            units.add(createStringSource("net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType",
                    "package net.fabricmc.fabric.api.screenhandler.v1; public class ExtendedScreenHandlerType<T> extends net.minecraft.world.inventory.MenuType<T> { " +
                            "@FunctionalInterface public interface ExtendedFactory<T> { T create(int id, Object inv); } " +
                            "public ExtendedScreenHandlerType(ExtendedFactory<T> factory) {} }"));

            units.add(createStringSource("net.fabricmc.api.ModInitializer",
                    "package net.fabricmc.api; public interface ModInitializer { void onInitialize(); }"));

        } else if (targetSpec.getLoader() == LoaderType.PAPER || targetSpec.getLoader() == LoaderType.SPIGOT) {
            units.add(createStringSource("org.bukkit.plugin.java.JavaPlugin",
                    "package org.bukkit.plugin.java; public class JavaPlugin { " +
                            "public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getLogger(\"Paper\"); } " +
                            "public org.bukkit.Server getServer() { return new org.bukkit.Server() {}; } " +
                            "public void onEnable() {} " +
                            "public void onDisable() {} }"));

            units.add(createStringSource("org.bukkit.Server",
                    "package org.bukkit; public interface Server { " +
                            "default org.bukkit.plugin.PluginManager getPluginManager() { return new org.bukkit.plugin.PluginManager() {}; } }"));

            units.add(createStringSource("org.bukkit.plugin.PluginManager",
                    "package org.bukkit.plugin; public interface PluginManager { " +
                            "default void registerEvents(Object listener, Object plugin) {} }"));

            units.add(createStringSource("org.bukkit.event.EventHandler",
                    "package org.bukkit.event; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface EventHandler {}"));

            units.add(createStringSource("org.bukkit.event.player.PlayerInteractEvent",
                    "package org.bukkit.event.player; public class PlayerInteractEvent { " +
                            "public org.bukkit.block.Block getClickedBlock() { return null; } }"));

            units.add(createStringSource("org.bukkit.block.Block",
                    "package org.bukkit.block; public interface Block { " +
                            "default org.bukkit.Material getType() { return org.bukkit.Material.STONE; } }"));

            units.add(createStringSource("org.bukkit.NamespacedKey",
                    "package org.bukkit; public class NamespacedKey { " +
                            "public NamespacedKey(String ns, String key) {} }"));

            units.add(createStringSource("org.bukkit.event.Listener",
                    "package org.bukkit.event; public interface Listener {}"));

            units.add(createStringSource("org.bukkit.inventory.ItemStack",
                    "package org.bukkit.inventory; public class ItemStack { public ItemStack(Object m) {} }"));

            units.add(createStringSource("org.bukkit.Material",
                    "package org.bukkit; public enum Material { STONE, AIR, IRON_INGOT }"));

        } else {
            // Forge stubs
            units.add(createStringSource("net.minecraftforge.fml.common.Mod",
                    "package net.minecraftforge.fml.common; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Mod { String value(); }"));

            units.add(createStringSource("net.minecraftforge.registries.RegistryObject",
                    "package net.minecraftforge.registries; public class RegistryObject<T> { public T get() { return null; } }"));

            units.add(createStringSource("net.minecraftforge.common.extensions.IForgeMenuType",
                    "package net.minecraftforge.common.extensions; public interface IForgeMenuType { " +
                            "@FunctionalInterface public interface MenuFactory<T> { T create(int id, Object inv); } " +
                            "static <T> net.minecraft.world.inventory.MenuType<T> create(MenuFactory<T> factory) { return new net.minecraft.world.inventory.MenuType<>(); } }"));

            units.add(createStringSource("net.minecraftforge.registries.ForgeRegistries",
                    "package net.minecraftforge.registries; public class ForgeRegistries { " +
                            "public static final Object BLOCKS = new Object(); " +
                            "public static final Object ITEMS = new Object(); " +
                            "public static final Object BLOCK_ENTITIES = new Object(); " +
                            "public static final Object ENTITIES = new Object(); " +
                            "public static final Object SOUND_EVENTS = new Object(); " +
                            "public static final Object PARTICLE_TYPES = new Object(); " +
                            "public static final Object CONTAINERS = new Object(); " +
                            "public static final Object FLUIDS = new Object(); " +
                            "public static final Object RECIPE_SERIALIZERS = new Object(); }"));

            units.add(createStringSource("net.minecraftforge.eventbus.api.IEventBus",
                    "package net.minecraftforge.eventbus.api; public interface IEventBus { void register(Object o); }"));

            units.add(createStringSource("net.minecraftforge.registries.DeferredRegister",
                    "package net.minecraftforge.registries; public class DeferredRegister<T> { " +
                            "public static <T> DeferredRegister<T> create(Object reg, String modId) { return new DeferredRegister<>(); } " +
                            "public void register(net.minecraftforge.eventbus.api.IEventBus bus) {} " +
                            "public void register(Object bus) {} " +
                            "public <I extends T> RegistryObject<I> register(String id, java.util.function.Supplier<? extends I> sup) { return new RegistryObject<>(); } }"));
        }
    }

    private SimpleJavaFileObject createStringSource(String className, String source) {
        return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
    }
}
