package com.kyroxova.continuumlib.shims;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;
import com.kyroxova.continuumlib.transformer.ContinuumMixinPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegacyRenderShim and ContinuumMixinPlugin Verification Tests")
public class LegacyRenderAndMixinPluginTest {

    private ApiKnowledgeBase kb;
    private TargetSpec baseSpec;
    private TargetSpec targetSpec;

    @BeforeEach
    public void setup() {
        LegacyRenderShim.releaseContext();
        kb = ApiKnowledgeBase.createDefault();
        baseSpec = new TargetSpec(MCVersion.of("1.16.5"), LoaderType.FORGE, "mojmap", 0);
        targetSpec = new TargetSpec(MCVersion.of("1.20.1"), LoaderType.NEOFORGE, "mojmap", 0);
    }

    @AfterEach
    public void tearDown() {
        LegacyRenderShim.releaseContext();
    }

    // =========================================================================
    // Tier 1: LegacyRenderShim Isolation & Matrix Transformation Tests
    // =========================================================================

    @Test
    @DisplayName("1.1 Matrix stack push, pop, translate, scale, and rotate transformations")
    public void testMatrixStackOperations() {
        LegacyRenderShim.loadIdentity();
        float[] identity = LegacyRenderShim.getCurrentMatrix();
        assertEquals(1.0f, identity[0], 1e-5f);
        assertEquals(1.0f, identity[5], 1e-5f);
        assertEquals(1.0f, identity[10], 1e-5f);
        assertEquals(1.0f, identity[15], 1e-5f);

        // Push matrix and translate
        LegacyRenderShim.pushMatrix();
        LegacyRenderShim.translate(2.0f, 3.0f, 4.0f);
        float[] translatedPoint = LegacyRenderShim.transformPoint(LegacyRenderShim.getCurrentMatrix(), 0f, 0f, 0f);
        assertEquals(2.0f, translatedPoint[0], 1e-4f);
        assertEquals(3.0f, translatedPoint[1], 1e-4f);
        assertEquals(4.0f, translatedPoint[2], 1e-4f);

        // Scale
        LegacyRenderShim.scale(2.0f, 2.0f, 2.0f);
        float[] scaledPoint = LegacyRenderShim.transformPoint(LegacyRenderShim.getCurrentMatrix(), 1f, 1f, 1f);
        // (1*2 + 2 = 4, 1*2 + 3 = 5, 1*2 + 4 = 6)
        assertEquals(4.0f, scaledPoint[0], 1e-4f);
        assertEquals(5.0f, scaledPoint[1], 1e-4f);
        assertEquals(6.0f, scaledPoint[2], 1e-4f);

        // Pop matrix restores identity
        LegacyRenderShim.popMatrix();
        float[] restored = LegacyRenderShim.transformPoint(LegacyRenderShim.getCurrentMatrix(), 1f, 1f, 1f);
        assertEquals(1.0f, restored[0], 1e-4f);
        assertEquals(1.0f, restored[1], 1e-4f);
        assertEquals(1.0f, restored[2], 1e-4f);
    }

    @Test
    @DisplayName("1.2 Rotation and normal vector transformation")
    public void testRotationAndNormals() {
        LegacyRenderShim.loadIdentity();
        // Rotate 90 degrees around Z axis: (1, 0, 0) -> (0, 1, 0)
        LegacyRenderShim.rotate(90.0f, 0.0f, 0.0f, 1.0f);
        float[] rotatedNorm = LegacyRenderShim.transformNormal(LegacyRenderShim.getCurrentMatrix(), 1.0f, 0.0f, 0.0f);

        assertEquals(0.0f, rotatedNorm[0], 1e-4f);
        assertEquals(1.0f, rotatedNorm[1], 1e-4f);
        assertEquals(0.0f, rotatedNorm[2], 1e-4f);
    }

    @Test
    @DisplayName("1.3 State management & color methods null-safety")
    public void testStateAndColor() {
        assertDoesNotThrow(() -> {
            LegacyRenderShim.color(0.5f, 0.6f, 0.7f, 0.8f);
            LegacyRenderShim.color(255, 128, 64, 32);
            LegacyRenderShim.glColor4ub((byte) 255, (byte) 128, (byte) 64, (byte) 32);
            LegacyRenderShim.enableBlend();
            LegacyRenderShim.disableBlend();
            LegacyRenderShim.enableTexture();
            LegacyRenderShim.disableTexture();
            LegacyRenderShim.enableDepth();
            LegacyRenderShim.disableDepth();
            LegacyRenderShim.enableCull();
            LegacyRenderShim.disableCull();
            LegacyRenderShim.blendFunc(1, 0);
            LegacyRenderShim.depthMask(true);
            LegacyRenderShim.depthFunc(515);
            LegacyRenderShim.glLineWidth(2.0f);
            LegacyRenderShim.glEnable(3042); // GL_BLEND
            LegacyRenderShim.glDisable(3042);
        });
    }

    @Test
    @DisplayName("1.4 Tessellator vertex accumulation and draw to mock VertexConsumer")
    public void testTessellatorAccumulationAndDraw() {
        MockVertexConsumer mockConsumer = new MockVertexConsumer();
        LegacyRenderShim.bindVertexConsumer(mockConsumer);

        LegacyRenderShim.startDrawingQuads(null);
        LegacyRenderShim.setColorRGBA(null, 255, 0, 0, 255);
        LegacyRenderShim.setNormal(null, 0f, 1f, 0f);
        LegacyRenderShim.addVertexWithUV(null, 0.0, 0.0, 0.0, 0.0, 0.0);
        LegacyRenderShim.addVertexWithUV(null, 1.0, 0.0, 0.0, 1.0, 0.0);
        LegacyRenderShim.addVertexWithUV(null, 1.0, 1.0, 0.0, 1.0, 1.0);
        LegacyRenderShim.addVertexWithUV(null, 0.0, 1.0, 0.0, 0.0, 1.0);

        assertEquals(4, LegacyRenderShim.getAccumulatedVertexCount());

        int count = LegacyRenderShim.draw(null);
        assertEquals(4, count);
        assertEquals(4, mockConsumer.vertices.size());
        assertEquals(0, LegacyRenderShim.getAccumulatedVertexCount());

        // Verify vertex values
        MockVertex v0 = mockConsumer.vertices.get(0);
        assertEquals(0.0f, v0.x, 1e-4f);
        assertEquals(0.0f, v0.y, 1e-4f);
        assertEquals(255, v0.r);
        assertEquals(0, v0.g);
        assertEquals(0, v0.b);
        assertEquals(255, v0.a);
        assertEquals(0.0f, v0.u, 1e-4f);
        assertEquals(0.0f, v0.v, 1e-4f);

        MockVertex v1 = mockConsumer.vertices.get(1);
        assertEquals(1.0f, v1.x, 1e-4f);
        assertEquals(1.0f, v1.u, 1e-4f);
    }

    @Test
    @DisplayName("1.5 Fallback BlockEntity and Item renderer dispatch")
    public void testRendererFallbacks() {
        MockBlockEntityRenderer ber = new MockBlockEntityRenderer();
        MockBlockEntity be = new MockBlockEntity();

        assertDoesNotThrow(() -> {
            LegacyRenderShim.renderBlockEntity(ber, be, 1.0f, null, null, 0, 0);
        });
        assertTrue(ber.rendered);

        MockItemRenderer ir = new MockItemRenderer();
        Object itemStack = new Object();
        assertDoesNotThrow(() -> {
            LegacyRenderShim.renderItem(ir, itemStack, null, null, null, 0, 0);
        });
        assertTrue(ir.rendered);
    }

    // =========================================================================
    // Tier 2: ContinuumMixinPlugin Remapping & Dynamic Reflection Tests
    // =========================================================================

    @Test
    @DisplayName("2.1 ContinuumMixinPlugin dynamic proxy creation and lifecycle dispatch")
    public void testMixinPluginProxy() {
        ContinuumMixinPlugin plugin = new ContinuumMixinPlugin(kb, baseSpec, targetSpec);

        assertDoesNotThrow(() -> {
            plugin.onLoad("com.example.mod.mixin");
            assertNull(plugin.getRefMapperConfig());
            assertTrue(plugin.shouldApplyMixin("net.minecraft.world.level.Level", "com.example.mod.mixin.WorldMixin"));
            assertTrue(plugin.getMixins().isEmpty());
        });

        // Test acceptTargets remapping
        Set<String> myTargets = new HashSet<>(Arrays.asList("net.minecraft.world.World", "net.minecraft.client.renderer.ItemBlockRenderTypes"));
        Set<String> otherTargets = new HashSet<>(Collections.singletonList("net.minecraft.tileentity.TileEntity"));

        plugin.acceptTargets(myTargets, otherTargets);

        assertTrue(myTargets.contains("net.minecraft.world.level.Level"));
        assertFalse(myTargets.contains("net.minecraft.world.World"));
        assertTrue(otherTargets.contains("net.minecraft.world.level.block.entity.BlockEntity"));
        assertFalse(otherTargets.contains("net.minecraft.tileentity.TileEntity"));
    }

    @Test
    @DisplayName("2.2 Remap Mixin method target descriptors and class references")
    public void testRemapMethodTarget() {
        ContinuumMixinPlugin plugin = new ContinuumMixinPlugin(kb, baseSpec, targetSpec);

        String rawTarget = "render(Lnet/minecraft/world/World;F)V";
        String remapped = plugin.remapMethodTarget(rawTarget);
        assertEquals("render(Lnet/minecraft/world/level/Level;F)V", remapped);

        String blockTarget = "onBlockActivated(Lnet/minecraft/world/World;Lnet/minecraft/tileentity/TileEntity;)V";
        String remappedBlock = plugin.remapMethodTarget(blockTarget);
        assertEquals("onBlockActivated(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", remappedBlock);
    }

    @Test
    @DisplayName("2.3 Remap ClassNode @Mixin and @Inject annotations")
    public void testRemapMixinClassNode() {
        ContinuumMixinPlugin plugin = new ContinuumMixinPlugin(kb, baseSpec, targetSpec);

        ClassNode mixinNode = new ClassNode();
        mixinNode.name = "com/example/mod/mixin/WorldMixin";
        mixinNode.superName = "java/lang/Object";

        // Add @Mixin(value = { World.class }, targets = { "net.minecraft.world.World" })
        AnnotationNode mixinAnn = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        List<Object> values = new ArrayList<>();
        values.add("value");
        values.add(new ArrayList<>(Collections.singletonList(Type.getObjectType("net/minecraft/world/World"))));
        values.add("targets");
        values.add(new ArrayList<>(Collections.singletonList("net.minecraft.world.World")));
        mixinAnn.values = values;
        mixinNode.visibleAnnotations = new ArrayList<>(Collections.singletonList(mixinAnn));

        // Add method with @Inject(method = "render(Lnet/minecraft/world/World;)V")
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "onRender", "(Lnet/minecraft/world/World;)V", null, null);
        AnnotationNode injectAnn = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        injectAnn.values = new ArrayList<>(Arrays.asList("method", "render(Lnet/minecraft/world/World;)V"));
        method.visibleAnnotations = new ArrayList<>(Collections.singletonList(injectAnn));

        // Injected instructions with legacy GL11 call
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPushMatrix", "()V", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V", false));

        mixinNode.methods = new ArrayList<>(Collections.singletonList(method));

        boolean modified = plugin.remapMixinClassNode(mixinNode);
        assertTrue(modified);

        // Verify @Mixin annotation was remapped
        List<Object> remappedTypes = (List<Object>) mixinAnn.values.get(1);
        assertEquals(Type.getObjectType("net/minecraft/world/level/Level"), remappedTypes.get(0));

        List<Object> remappedTargets = (List<Object>) mixinAnn.values.get(3);
        assertEquals("net.minecraft.world.level.Level", remappedTargets.get(0));

        // Verify @Inject method descriptor was remapped
        assertEquals("render(Lnet/minecraft/world/level/Level;)V", injectAnn.values.get(1));

        // Verify GL11 calls inside mixin method were redirected to LegacyRenderShim
        MethodInsnNode minsn0 = (MethodInsnNode) method.instructions.get(0);
        assertEquals("com/kyroxova/continuumlib/shims/LegacyRenderShim", minsn0.owner);
        assertEquals("pushMatrix", minsn0.name);
    }

    @Test
    @DisplayName("2.4 Mixin preApply and postApply transform target class instructions")
    public void testPreApplyAndPostApply() {
        ContinuumMixinPlugin plugin = new ContinuumMixinPlugin(kb, baseSpec, targetSpec);

        ClassNode targetClass = new ClassNode();
        targetClass.name = "net/minecraft/client/gui/screens/TitleScreen";
        targetClass.superName = "java/lang/Object";

        MethodNode renderMethod = new MethodNode(Opcodes.ACC_PUBLIC, "render", "()V", null, null);
        renderMethod.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/client/renderer/Tessellator", "getInstance", "()Lnet/minecraft/client/renderer/Tessellator;", false));
        targetClass.methods = new ArrayList<>(Collections.singletonList(renderMethod));

        plugin.postApply("net.minecraft.client.gui.screens.TitleScreen", targetClass, "com.example.TitleScreenMixin", null);

        MethodInsnNode insn = (MethodInsnNode) renderMethod.instructions.get(0);
        assertEquals("com/kyroxova/continuumlib/shims/LegacyRenderShim", insn.owner);
        assertEquals("getInstance", insn.name);
    }

    // =========================================================================
    // Tier 3: ClientRenderingAndGuiCatalog Rules Presence Tests
    // =========================================================================

    @Test
    @DisplayName("3.1 GL11 and Tessellator redirect rules registered in ApiKnowledgeBase")
    public void testCatalogRulesRegistered() {
        List<TransformationRule> rules = kb.getApplicableRules(baseSpec, targetSpec);

        boolean hasGL11Push = false;
        boolean hasGL11Translate = false;
        boolean hasTessellatorGetInstance = false;
        boolean hasTessellatorDraw = false;

        for (TransformationRule r : rules) {
            if (r instanceof PolyfillRule pr) {
                if ("org/lwjgl/opengl/GL11".equals(pr.getSourceOwner()) && "glPushMatrix".equals(pr.getSourceName())) {
                    hasGL11Push = true;
                }
                if ("org/lwjgl/opengl/GL11".equals(pr.getSourceOwner()) && "glTranslatef".equals(pr.getSourceName())) {
                    hasGL11Translate = true;
                }
                if ("net/minecraft/client/renderer/Tessellator".equals(pr.getSourceOwner()) && "getInstance".equals(pr.getSourceName())) {
                    hasTessellatorGetInstance = true;
                }
                if ("net/minecraft/client/renderer/Tessellator".equals(pr.getSourceOwner()) && "draw".equals(pr.getSourceName())) {
                    hasTessellatorDraw = true;
                }
            }
        }

        assertTrue(hasGL11Push, "GL11.glPushMatrix rule should be active");
        assertTrue(hasGL11Translate, "GL11.glTranslatef rule should be active");
        assertTrue(hasTessellatorGetInstance, "Tessellator.getInstance rule should be active");
        assertTrue(hasTessellatorDraw, "Tessellator.draw rule should be active");
    }

    // =========================================================================
    // Helper Mock Classes for Testing
    // =========================================================================

    public static class MockVertex {
        public float x, y, z;
        public int r, g, b, a;
        public float u, v;

        public MockVertex(float x, float y, float z, int r, int g, int b, int a, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.u = u;
            this.v = v;
        }
    }

    public static class MockVertexConsumer {
        public final List<MockVertex> vertices = new ArrayList<>();
        private float curX, curY, curZ;
        private int curR = 255, curG = 255, curB = 255, curA = 255;
        private float curU, curV;

        public MockVertexConsumer vertex(double x, double y, double z) {
            this.curX = (float) x;
            this.curY = (float) y;
            this.curZ = (float) z;
            return this;
        }

        public MockVertexConsumer color(int r, int g, int b, int a) {
            this.curR = r;
            this.curG = g;
            this.curB = b;
            this.curA = a;
            return this;
        }

        public MockVertexConsumer uv(float u, float v) {
            this.curU = u;
            this.curV = v;
            return this;
        }

        public MockVertexConsumer overlayCoords(int overlay) {
            return this;
        }

        public MockVertexConsumer uv2(int light) {
            return this;
        }

        public MockVertexConsumer normal(float nx, float ny, float nz) {
            return this;
        }

        public void endVertex() {
            vertices.add(new MockVertex(curX, curY, curZ, curR, curG, curB, curA, curU, curV));
        }
    }

    public static class MockBlockEntity {
        public int xCoord = 10;
        public int yCoord = 20;
        public int zCoord = 30;
    }

    public static class MockBlockEntityRenderer {
        public boolean rendered = false;

        public void renderTileEntityAt(Object be, double x, double y, double z, float pt) {
            this.rendered = true;
        }
    }

    public static class MockItemRenderer {
        public boolean rendered = false;

        public void renderByItem(Object itemStack) {
            this.rendered = true;
        }
    }
}
