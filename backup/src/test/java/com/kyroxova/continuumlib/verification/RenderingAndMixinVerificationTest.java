package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.shims.LegacyRenderShim;
import com.kyroxova.continuumlib.transformer.ContinuumMixinPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Verification Suite for Rendering Shims & Mixin Compatibility Engine:
 * Tests LegacyRenderShim OpenGL 2.1 fixed-function emulation, Tessellator vertex compilation,
 * matrix transformations, GlStateManager state changes, ThreadLocal context isolation,
 * and ContinuumMixinPlugin dynamic proxy generation, annotation remapping, and ClassNode transformation.
 */
public class RenderingAndMixinVerificationTest {

    // =========================================================================
    // Tier 1: Isolation Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 1: Isolation Tests")
    class Tier1Isolation {

        @Test
        @DisplayName("1.1 LegacyRenderShim instance retrieval, Tessellator emulation, and vertex accumulation")
        public void testTessellatorEmulation() {
            LegacyRenderShim shim = LegacyRenderShim.getInstance();
            assertNotNull(shim);
            assertSame(shim, LegacyRenderShim.getInstance(null));
            assertSame(shim, shim.getBuilder());
            assertSame(shim, shim.getWorldRenderer());
            assertSame(shim, LegacyRenderShim.getBuffer(null));

            // Start drawing quads (mode 7)
            shim.startDrawingQuads();

            // Set color, normal, translation
            LegacyRenderShim.setColorRGBA(shim, 255, 128, 64, 255);
            LegacyRenderShim.setNormal(shim, 0.0f, 1.0f, 0.0f);
            LegacyRenderShim.setTranslation(shim, 10.0, 20.0, 30.0);

            // Add 4 vertices for a quad
            LegacyRenderShim.addVertexWithUV(shim, 0.0, 0.0, 0.0, 0.0, 0.0);
            LegacyRenderShim.addVertexWithUV(shim, 1.0, 0.0, 0.0, 1.0, 0.0);
            LegacyRenderShim.addVertexWithUV(shim, 1.0, 1.0, 0.0, 1.0, 1.0);
            LegacyRenderShim.addVertexWithUV(shim, 0.0, 1.0, 0.0, 0.0, 1.0);

            assertEquals(4, LegacyRenderShim.getAccumulatedVertexCount());

            // Draw quad
            int vertexCount = LegacyRenderShim.draw(shim);
            assertEquals(4, vertexCount);
            assertEquals(0, LegacyRenderShim.getAccumulatedVertexCount());
        }

        @Test
        @DisplayName("1.2 BufferBuilder style fluent API: pos, tex, color, normal, endVertex")
        public void testBufferBuilderFluentApi() {
            LegacyRenderShim shim = LegacyRenderShim.getInstance();
            shim.startDrawing(7);

            // Fluent vertex building
            LegacyRenderShim.pos(shim, 5.0, 10.0, 15.0)
                    .tex(shim, 0.25, 0.75)
                    .color(shim, 1.0f, 0.0f, 0.0f, 1.0f)
                    .normal(shim, 1.0f, 0.0f, 0.0f);
            LegacyRenderShim.endVertex(shim);

            assertEquals(1, LegacyRenderShim.getAccumulatedVertexCount());

            LegacyRenderShim.drawVoid(shim);
            assertEquals(0, LegacyRenderShim.getAccumulatedVertexCount());
        }

        @Test
        @DisplayName("1.3 Matrix Stack and 4x4 affine transformations: push, translate, scale, rotate, pop")
        public void testMatrixStackTransformations() {
            LegacyRenderShim.releaseContext();

            // Push initial identity
            LegacyRenderShim.pushMatrix();

            // Translate by (10, 0, 0)
            LegacyRenderShim.translate(10.0f, 0.0f, 0.0f);
            float[] currentMatrix = LegacyRenderShim.getCurrentMatrix();
            float[] translatedPoint = LegacyRenderShim.transformPoint(currentMatrix, 0.0f, 0.0f, 0.0f);
            assertEquals(10.0f, translatedPoint[0], 0.001f);
            assertEquals(0.0f, translatedPoint[1], 0.001f);
            assertEquals(0.0f, translatedPoint[2], 0.001f);

            // Scale by 2.0
            LegacyRenderShim.scale(2.0f, 2.0f, 2.0f);
            currentMatrix = LegacyRenderShim.getCurrentMatrix();
            float[] scaledPoint = LegacyRenderShim.transformPoint(currentMatrix, 5.0f, 0.0f, 0.0f);
            assertEquals(20.0f, scaledPoint[0], 0.001f); // (10 + 2*5) = 20

            // Rotate 90 degrees around Z axis
            LegacyRenderShim.rotate(90.0f, 0.0f, 0.0f, 1.0f);

            // Pop matrix back
            LegacyRenderShim.popMatrix();
            currentMatrix = LegacyRenderShim.getCurrentMatrix();
            float[] restoredPoint = LegacyRenderShim.transformPoint(currentMatrix, 5.0f, 3.0f, 1.0f);
            assertEquals(5.0f, restoredPoint[0], 0.001f);
            assertEquals(3.0f, restoredPoint[1], 0.001f);
            assertEquals(1.0f, restoredPoint[2], 0.001f);
        }

        @Test
        @DisplayName("1.4 GlStateManager emulation: blend, depth, cull, texture, color4f")
        public void testGlStateManagerEmulation() {
            // Invoking GL calls without throwing
            assertDoesNotThrow(() -> {
                LegacyRenderShim.enableBlend();
                LegacyRenderShim.disableBlend();
                LegacyRenderShim.enableTexture();
                LegacyRenderShim.disableTexture();
                LegacyRenderShim.enableDepth();
                LegacyRenderShim.disableDepth();
                LegacyRenderShim.enableCull();
                LegacyRenderShim.disableCull();
                LegacyRenderShim.enableLighting();
                LegacyRenderShim.disableLighting();

                LegacyRenderShim.blendFunc(1, 0);
                LegacyRenderShim.depthMask(true);
                LegacyRenderShim.depthFunc(515);
                LegacyRenderShim.glCullFace(1029);
                LegacyRenderShim.bindTexture(0);

                LegacyRenderShim.glColor4f(0.5f, 0.8f, 0.2f, 1.0f);
                LegacyRenderShim.glColor4ub((byte) 255, (byte) 128, (byte) 0, (byte) 255);
            });
        }

        @Test
        @DisplayName("1.5 RenderContext binding and BlockEntity / Item render bridges")
        public void testContextBindingAndRenderBridges() {
            Object mockPoseStack = new Object();
            Object mockBufferSource = new Object();

            LegacyRenderShim.bindContext(mockPoseStack, mockBufferSource, 0x00F000F0, 0x000A0000);
            assertSame(mockPoseStack, LegacyRenderShim.getBoundPoseStack());
            assertSame(mockBufferSource, LegacyRenderShim.getBoundBufferSource());
            assertEquals(0x00F000F0, LegacyRenderShim.getBoundLight());
            assertEquals(0x000A0000, LegacyRenderShim.getBoundOverlay());

            // Release context
            LegacyRenderShim.releaseContext();
            assertNull(LegacyRenderShim.getBoundPoseStack());
            assertNull(LegacyRenderShim.getBoundBufferSource());

            // Render bridges invoke without throwing when given null or mock targets
            assertDoesNotThrow(() -> {
                LegacyRenderShim.renderBlockEntity(null, new Object(), 1.0f, mockPoseStack, mockBufferSource, 0, 0);
                LegacyRenderShim.renderTileEntityFast(null, new Object(), 0.0, 0.0, 0.0, 1.0f, -1, mockPoseStack, mockBufferSource);
                LegacyRenderShim.renderItem(null, new Object(), null, mockPoseStack, mockBufferSource, 0, 0);
            });
        }
    }

    // =========================================================================
    // Tier 2: ContinuumMixinPlugin & Remapping Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 2: ContinuumMixinPlugin & Bytecode Remapping Tests")
    class Tier2MixinRemapping {

        @Test
        @DisplayName("2.1 ContinuumMixinPlugin dynamic proxy creation and lifecycle methods")
        public void testMixinPluginDynamicProxy() throws Throwable {
            ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
            TargetSpec baseSpec = new TargetSpec(MCVersion.of("1.18.2"), LoaderType.FORGE, "mojmap", 0);
            TargetSpec targetSpec = new TargetSpec(MCVersion.of("1.20.1"), LoaderType.NEOFORGE, "mojmap", 0);

            ContinuumMixinPlugin plugin = new ContinuumMixinPlugin(kb, baseSpec, targetSpec);
            assertEquals(baseSpec, plugin.getBaseSpec());
            assertEquals(targetSpec, plugin.getTargetSpec());
            assertNotNull(plugin.getTransformer());
            assertNotNull(plugin.getKnowledgeBase());

            // Lifecycle hooks
            plugin.onLoad("com.example.mod.mixin");
            assertNull(plugin.getRefMapperConfig());
            assertTrue(plugin.shouldApplyMixin("net.minecraft.world.level.block.Block", "com.example.mod.mixin.BlockMixin"));
            assertTrue(plugin.getMixins().isEmpty());

            Set<String> myTargets = new HashSet<>(List.of("net/minecraft/world/level/block/Block"));
            Set<String> otherTargets = new HashSet<>();
            plugin.acceptTargets(myTargets, otherTargets);

            // Invoke handler directly
            Method onLoadMethod = ContinuumMixinPlugin.class.getMethod("onLoad", String.class);
            Object result = plugin.invoke(null, onLoadMethod, new Object[]{"com.example.mod.mixin"});
            assertNull(result);

            // Proxy factory
            Object proxy = ContinuumMixinPlugin.createProxy(getClass().getClassLoader(), kb, baseSpec, targetSpec);
            assertNotNull(proxy);
            assertTrue(proxy instanceof InvocationHandler);
        }

        @Test
        @DisplayName("2.2 Remapping @Mixin target classes and @Inject method descriptors")
        public void testMixinClassNodeRemapping() {
            ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
            TargetSpec baseSpec = new TargetSpec(MCVersion.of("1.18.2"), LoaderType.FORGE, "mojmap", 0);
            TargetSpec legacySpec = new TargetSpec(MCVersion.of("1.16.5"), LoaderType.FORGE, "mojmap", 0);
            ContinuumMixinPlugin plugin = new ContinuumMixinPlugin(kb, baseSpec, legacySpec);

            // Construct synthetic Mixin ClassNode targeting Level (which redirects to World on <= 1.16.5)
            ClassNode mixinNode = new ClassNode();
            mixinNode.name = "com/example/mod/mixin/LevelMixin";

            // Add @Mixin(Level.class) annotation
            AnnotationNode mixinAnno = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
            mixinAnno.values = new ArrayList<>(List.of("value", new ArrayList<>(List.of("net/minecraft/world/level/Level"))));
            mixinNode.invisibleAnnotations = new ArrayList<>(List.of(mixinAnno));

            // Add an @Inject method targeting Level.getBlockEntity
            MethodNode methodNode = new MethodNode(0, "onGetBlockEntity", "(Lnet/minecraft/core/BlockPos;)V", null, null);
            AnnotationNode injectAnno = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
            injectAnno.values = new ArrayList<>(List.of(
                    "method", new ArrayList<>(List.of("getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
            ));
            methodNode.visibleAnnotations = new ArrayList<>(List.of(injectAnno));
            mixinNode.methods = new ArrayList<>(List.of(methodNode));

            // Apply remapping
            boolean modified = plugin.remapMixinClassNode(mixinNode);
            assertTrue(modified, "Mixin class node should be modified by remapping rules");

            // Verify @Mixin target class remapped from Level to World
            @SuppressWarnings("unchecked")
            List<Object> targets = (List<Object>) mixinAnno.values.get(1);
            assertEquals("net/minecraft/world/World", targets.get(0));

            // Verify method target in @Inject remapped BlockEntity -> TileEntity, BlockPos -> util/math/BlockPos
            @SuppressWarnings("unchecked")
            List<Object> methodTargets = (List<Object>) injectAnno.values.get(1);
            String remappedMethodTarget = (String) methodTargets.get(0);
            assertTrue(remappedMethodTarget.contains("TileEntity") || remappedMethodTarget.contains("getTileEntity"),
                    "Method target descriptor should remap BlockEntity to TileEntity: " + remappedMethodTarget);
        }

        @Test
        @DisplayName("2.3 remapMethodTarget utility function across version boundaries")
        public void testRemapMethodTarget() {
            ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
            TargetSpec baseSpec = new TargetSpec(MCVersion.of("1.18.2"), LoaderType.FORGE, "mojmap", 0);
            TargetSpec legacySpec = new TargetSpec(MCVersion.of("1.16.5"), LoaderType.FORGE, "mojmap", 0);
            ContinuumMixinPlugin plugin = new ContinuumMixinPlugin(kb, baseSpec, legacySpec);

            String target = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)V";
            String remapped = plugin.remapMethodTarget(target);

            assertTrue(remapped.contains("net/minecraft/world/World"));
            assertNotNull(remapped);
        }
    }

    // =========================================================================
    // Tier 3: Concurrency & Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Tier 3: Concurrency & Stress Tests")
    class Tier3Concurrency {

        @Test
        @DisplayName("3.1 Highly concurrent multi-threaded Tessellator rendering and matrix isolation")
        public void testConcurrentTessellatorRendering() throws Exception {
            final int threadCount = 16;
            final int iterationsPerThread = 2000;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCounter = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                pool.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        LegacyRenderShim shim = LegacyRenderShim.getInstance();

                        for (int i = 0; i < iterationsPerThread; i++) {
                            // 1. Matrix operations
                            LegacyRenderShim.pushMatrix();
                            LegacyRenderShim.translate(threadId, i, 0);
                            LegacyRenderShim.scale(1.5f, 1.5f, 1.5f);
                            LegacyRenderShim.popMatrix();

                            // 2. Vertex drawing
                            shim.startDrawingQuads();
                            LegacyRenderShim.setColorRGBA(shim, 255, 255, 255, 255);
                            LegacyRenderShim.addVertexWithUV(shim, 0, 0, 0, 0, 0);
                            LegacyRenderShim.addVertexWithUV(shim, 1, 0, 0, 1, 0);
                            LegacyRenderShim.addVertexWithUV(shim, 1, 1, 0, 1, 1);
                            LegacyRenderShim.addVertexWithUV(shim, 0, 1, 0, 0, 1);

                            int drawn = LegacyRenderShim.draw(shim);
                            assertEquals(4, drawn);

                            successCounter.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail("Exception in rendering thread " + threadId + ": " + e.getMessage());
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(threadCount * iterationsPerThread, successCounter.get());
        }
    }
}
