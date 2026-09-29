package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.shims.*;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-World Mod Simulation Verification Test Suite:
 * Simulating Buildscape's PillarBlock and PillarBlockEntity patterns:
 * 1. Unobtrusive hitbox resolution: Custom 8x8 VoxelShape collision box is strictly preserved
 *    without being overridden or altered by ContinuumLib.
 * 2. Waterlog state: FluidState query via FluidShim.isSource / FluidShim.getFluidState.
 * 3. Persistent NBT / components / attachments via CapabilityShim & DataComponentShim.
 * 4. Capability queries for block entities.
 * 5. Dynamic mob rendering & spawn egg interaction via ItemInteractionShim, LivingEntityShim, and MobEffectShim.
 * 6. Full ASM class generation, bytecode transformation across 1.20.4 -> 26.3+, and dynamic ClassLoader execution.
 */
public class RealWorldModSimulationVerificationTest {

    private ApiKnowledgeBase kb;

    @BeforeEach
    public void setup() {
        this.kb = ApiKnowledgeBase.createDefault();
    }

    // Helper Dynamic ClassLoader
    private static class SimulationClassLoader extends ClassLoader {
        public SimulationClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    // =========================================================================
    // Tier 1: Isolation Tests (Mod Logic Simulation)
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 Unobtrusive hitbox resolution: 8x8 column VoxelShape remains strictly preserved")
        public void testUnobtrusivePillarHitbox() {
            // Buildscape PillarBlock uses an 8x8 centered column (minX=4, maxX=12, minY=0, maxY=16, minZ=4, maxZ=12)
            Object pillarShape = VoxelShapeShim.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);
            assertNotNull(pillarShape);

            VoxelShapeShim.VirtualAABB aabb = VoxelShapeShim.toAABB(pillarShape);
            assertEquals(4.0, aabb.minX, 0.0001, "Pillar minX must be 4.0");
            assertEquals(0.0, aabb.minY, 0.0001, "Pillar minY must be 0.0");
            assertEquals(4.0, aabb.minZ, 0.0001, "Pillar minZ must be 4.0");
            assertEquals(12.0, aabb.maxX, 0.0001, "Pillar maxX must be 12.0");
            assertEquals(16.0, aabb.maxY, 0.0001, "Pillar maxY must be 16.0");
            assertEquals(12.0, aabb.maxZ, 0.0001, "Pillar maxZ must be 12.0");

            // Verify that this is distinct from a standard full cube (0,0,0 -> 16,16,16)
            VoxelShapeShim.VirtualVoxelShape fullCube = VoxelShapeShim.block();
            VoxelShapeShim.VirtualAABB fullAABB = VoxelShapeShim.toAABB(fullCube);
            assertNotEquals(fullAABB.minX, aabb.minX, "Custom hitbox must not be altered to full cube minX");
            assertNotEquals(fullAABB.maxX, aabb.maxX, "Custom hitbox must not be altered to full cube maxX");
        }

        @Test
        @DisplayName("1.2 Waterlog state: Query fluid state and source block via FluidShim")
        public void testPillarWaterlogState() {
            // Mock Level with waterlogged block pos
            Object mockLevel = new Object() {
                public Object getFluidState(Object pos) {
                    if ("waterlogged_pos".equals(pos)) {
                        return new Object() {
                            public boolean isSource() { return true; }
                        };
                    } else {
                        return new Object() {
                            public int getAmount() { return 0; }
                        };
                    }
                }
            };

            Object waterloggedState = FluidShim.getFluidState(mockLevel, "waterlogged_pos");
            assertTrue(FluidShim.isSource(waterloggedState), "Waterlogged pillar must report true for isSource");

            Object dryState = FluidShim.getFluidState(mockLevel, "dry_pos");
            assertFalse(FluidShim.isSource(dryState), "Dry pillar must report false for isSource");
        }

        @Test
        @DisplayName("1.3 Persistent NBT / data attachments on PillarBlockEntity")
        public void testPillarBlockEntityPersistence() {
            Object pillarBe = new Object();
            String dataKey = "buildscape:pillar_state";

            Map<String, Object> state = new HashMap<>();
            state.put("height", 3);
            state.put("material", "marble");
            state.put("waterlogged", true);

            CapabilityShim.setDataAttachment(pillarBe, dataKey, state);

            assertTrue(CapabilityShim.hasDataAttachment(pillarBe, dataKey));
            Object retrieved = CapabilityShim.getDataAttachment(pillarBe, dataKey);
            assertEquals(state, retrieved);

            // Mutation test
            @SuppressWarnings("unchecked")
            Map<String, Object> castState = (Map<String, Object>) retrieved;
            castState.put("height", 4);
            assertEquals(4, ((Map<?, ?>) CapabilityShim.getDataAttachment(pillarBe, dataKey)).get("height"));

            // Removal
            CapabilityShim.removeDataAttachment(pillarBe, dataKey);
            assertFalse(CapabilityShim.hasDataAttachment(pillarBe, dataKey));
        }

        @Test
        @DisplayName("1.4 Dynamic spawn egg interaction & spawned entity query")
        public void testSpawnEggInteractionAndEntityQuery() {
            Object spawnEggStack = "buildscape:pillar_golem_spawn_egg";
            Object successResult = ItemInteractionShim.sidedSuccess(spawnEggStack, true);

            assertEquals("SUCCESS", ItemInteractionShim.getResult(successResult));
            assertEquals(spawnEggStack, ItemInteractionShim.getObject(successResult));

            // Simulating spawned entity
            Object spawnedGolem = new Object() {
                public double getAttributeValue(Object attr) {
                    if ("minecraft:generic.max_health".equals(attr)) return 100.0;
                    return 20.0;
                }

                public boolean hasEffect(Object effect) {
                    return "minecraft:strength".equals(effect);
                }

                public Object getItemBySlot(Object slot) {
                    if ("HEAD".equals(String.valueOf(slot))) return "buildscape:carved_pillar_cap";
                    return null;
                }
            };

            assertEquals(100.0, LivingEntityShim.getAttributeValue(spawnedGolem, "minecraft:generic.max_health"), 0.001);
            assertTrue(MobEffectShim.hasEffect(spawnedGolem, "minecraft:strength"));
            assertEquals("buildscape:carved_pillar_cap", LivingEntityShim.getItemBySlot(spawnedGolem, "HEAD"));
        }
    }

    // =========================================================================
    // Tier 2: Bytecode Transformation & ClassLoader Execution
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: Bytecode Transformation & ClassLoader Execution")
    class Tier2BytecodeExecution {

        @Test
        @DisplayName("2.1 Transform & execute Buildscape PillarBlock and PillarBlockEntity in custom ClassLoader")
        public void testBuildscapePillarBytecodeExecution() throws Exception {
            TargetSpec base = TargetSpec.of("1.20.4", "forge");
            TargetSpec target = TargetSpec.of("26.3", "neoforge");
            ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(kb, base, target);

            // 1. Generate PillarBlock ASM class
            ClassNode blockNode = new ClassNode();
            blockNode.version = Opcodes.V17;
            blockNode.access = Opcodes.ACC_PUBLIC;
            blockNode.name = "com/buildscape/block/PillarBlock";
            blockNode.superName = "java/lang/Object";

            // Default constructor
            MethodNode blockInit = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            blockInit.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            blockInit.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
            blockInit.instructions.add(new InsnNode(Opcodes.RETURN));
            blockNode.methods.add(blockInit);

            // getShape method returning VoxelShapeShim.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0)
            MethodNode getShapeMethod = new MethodNode(Opcodes.ACC_PUBLIC, "getPillarShape", "()Ljava/lang/Object;", null, null);
            getShapeMethod.instructions.add(new LdcInsnNode(4.0));
            getShapeMethod.instructions.add(new LdcInsnNode(0.0));
            getShapeMethod.instructions.add(new LdcInsnNode(4.0));
            getShapeMethod.instructions.add(new LdcInsnNode(12.0));
            getShapeMethod.instructions.add(new LdcInsnNode(16.0));
            getShapeMethod.instructions.add(new LdcInsnNode(12.0));
            getShapeMethod.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/VoxelShapeShim",
                    "box",
                    "(DDDDDD)Ljava/lang/Object;",
                    false
            ));
            getShapeMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
            blockNode.methods.add(getShapeMethod);

            // checkWaterlogged method calling FluidShim.isSource(FluidShim.getFluidState(level, pos))
            MethodNode checkWaterlogged = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "checkWaterlogged",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                    null,
                    null
            );
            checkWaterlogged.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            checkWaterlogged.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            checkWaterlogged.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/FluidShim",
                    "getFluidState",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    false
            ));
            checkWaterlogged.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/FluidShim",
                    "isSource",
                    "(Ljava/lang/Object;)Z",
                    false
            ));
            checkWaterlogged.instructions.add(new InsnNode(Opcodes.IRETURN));
            blockNode.methods.add(checkWaterlogged);

            // interactWithSpawnEgg method calling ItemInteractionShim.success(itemStack)
            MethodNode interactMethod = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "interactWithSpawnEgg",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    null,
                    null
            );
            interactMethod.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            interactMethod.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/ItemInteractionShim",
                    "success",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false
            ));
            interactMethod.instructions.add(new InsnNode(Opcodes.ARETURN));
            blockNode.methods.add(interactMethod);

            ClassWriter blockCw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            blockNode.accept(blockCw);
            byte[] blockBytes = blockCw.toByteArray();

            // Transform PillarBlock bytecode
            byte[] transformedBlockBytes = transformer.transform("com.buildscape.block.PillarBlock", blockBytes);
            assertNotNull(transformedBlockBytes);

            // 2. Generate PillarBlockEntity ASM class
            ClassNode beNode = new ClassNode();
            beNode.version = Opcodes.V17;
            beNode.access = Opcodes.ACC_PUBLIC;
            beNode.name = "com/buildscape/block/entity/PillarBlockEntity";
            beNode.superName = "java/lang/Object";

            // Default constructor
            MethodNode beInit = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            beInit.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            beInit.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
            beInit.instructions.add(new InsnNode(Opcodes.RETURN));
            beNode.methods.add(beInit);

            // getEntityHealth query calling LivingEntityShim.getAttributeValue(entity, attr)
            MethodNode getEntityHealth = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "getEntityHealth",
                    "(Ljava/lang/Object;Ljava/lang/Object;)D",
                    null,
                    null
            );
            getEntityHealth.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            getEntityHealth.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            getEntityHealth.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/kyroxova/continuumlib/shims/LivingEntityShim",
                    "getAttributeValue",
                    "(Ljava/lang/Object;Ljava/lang/Object;)D",
                    false
            ));
            getEntityHealth.instructions.add(new InsnNode(Opcodes.DRETURN));
            beNode.methods.add(getEntityHealth);

            ClassWriter beCw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            beNode.accept(beCw);
            byte[] beBytes = beCw.toByteArray();

            byte[] transformedBeBytes = transformer.transform("com.buildscape.block.entity.PillarBlockEntity", beBytes);
            assertNotNull(transformedBeBytes);

            // 3. Load into custom ClassLoader
            SimulationClassLoader loader = new SimulationClassLoader(getClass().getClassLoader());
            Class<?> pillarBlockClass = loader.defineClass("com.buildscape.block.PillarBlock", transformedBlockBytes);
            Class<?> pillarBeClass = loader.defineClass("com.buildscape.block.entity.PillarBlockEntity", transformedBeBytes);

            assertNotNull(pillarBlockClass);
            assertNotNull(pillarBeClass);

            // 4. Instantiate and invoke methods via reflection
            Object pillarBlock = pillarBlockClass.getDeclaredConstructor().newInstance();
            Method getShape = pillarBlockClass.getMethod("getPillarShape");
            Object shapeResult = getShape.invoke(pillarBlock);

            assertNotNull(shapeResult);
            VoxelShapeShim.VirtualAABB aabb = VoxelShapeShim.toAABB(shapeResult);
            assertEquals(4.0, aabb.minX, 0.0001);
            assertEquals(12.0, aabb.maxX, 0.0001);
            assertEquals(16.0, aabb.maxY, 0.0001);

            // Invoke checkWaterlogged
            Object mockLevel = new Object() {
                public Object getFluidState(Object pos) {
                    return new Object() {
                        public boolean isSource() { return true; }
                    };
                }
            };
            Method checkWaterloggedM = pillarBlockClass.getMethod("checkWaterlogged", Object.class, Object.class);
            Object isWaterlogged = checkWaterloggedM.invoke(pillarBlock, mockLevel, "pos_1");
            assertEquals(Boolean.TRUE, isWaterlogged);

            // Invoke interactWithSpawnEgg
            Method interactM = pillarBlockClass.getMethod("interactWithSpawnEgg", Object.class);
            Object resultHolder = interactM.invoke(pillarBlock, "buildscape:golem_egg");
            assertEquals("SUCCESS", ItemInteractionShim.getResult(resultHolder));

            // Invoke PillarBlockEntity getEntityHealth
            Object pillarBe = pillarBeClass.getDeclaredConstructor().newInstance();
            Method getHealthM = pillarBeClass.getMethod("getEntityHealth", Object.class, Object.class);

            Object mockEntity = new Object() {
                public double getAttributeValue(Object attr) {
                    return 50.0;
                }
            };
            Object healthVal = getHealthM.invoke(pillarBe, mockEntity, "max_health");
            assertEquals(50.0, ((Double) healthVal).doubleValue(), 0.001);
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent simulation of PillarBlock placements, hitbox checks, and NBT attachments")
        public void testConcurrentPillarSimulation() throws InterruptedException {
            int threadCount = 16;
            int opsPerThread = 250;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger verifiedSimulations = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            // 1. Hitbox resolution
                            Object shape = VoxelShapeShim.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);
                            VoxelShapeShim.VirtualAABB aabb = VoxelShapeShim.toAABB(shape);
                            if (aabb.minX != 4.0 || aabb.maxX != 12.0) {
                                continue;
                            }

                            // 2. Waterlog check
                            Object state = new Object() {
                                public boolean isSource() { return true; }
                            };
                            if (!FluidShim.isSource(state)) {
                                continue;
                            }

                            // 3. Attachment / NBT storage
                            Object be = new Object();
                            String key = "be_" + threadId + "_" + i;
                            CapabilityShim.setDataAttachment(be, key, i);
                            Object stored = CapabilityShim.getDataAttachment(be, key);
                            if (!Integer.valueOf(i).equals(stored)) {
                                continue;
                            }

                            // 4. Item interaction
                            Object holder = ItemInteractionShim.sidedSuccess("pillar_item", true);
                            if (!"SUCCESS".equals(ItemInteractionShim.getResult(holder))) {
                                continue;
                            }

                            verifiedSimulations.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent simulation timed out");
            executor.shutdown();

            assertEquals(threadCount * opsPerThread, verifiedSimulations.get());
        }
    }
}
