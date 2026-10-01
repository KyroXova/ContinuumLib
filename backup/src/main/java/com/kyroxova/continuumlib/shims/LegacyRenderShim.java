package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Universal Polyfill Shim for legacy OpenGL 2.1 fixed-function rendering pipeline,
 * Tessellator, WorldRenderer, BufferBuilder, and GlStateManager.
 *
 * Translates legacy immediate-mode drawing, matrix stacks, and GL calls to modern
 * PoseStack and VertexConsumer / MultiBufferSource pipelines (1.15+ / 1.17+ / 1.20+ / 26.3+),
 * and provides fallback execution for custom TileEntity and Item renderers.
 */
public final class LegacyRenderShim {

    private static final Logger LOGGER = Logger.getLogger(LegacyRenderShim.class.getName());

    public static final LegacyRenderShim instance = new LegacyRenderShim();

    // Reflection cache for modern RenderSystem / PoseStack / VertexConsumer
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    // Thread-local rendering context
    private static final ThreadLocal<RenderContext> CONTEXT = ThreadLocal.withInitial(RenderContext::new);

    public LegacyRenderShim() {}

    public static LegacyRenderShim getInstance() {
        return instance;
    }

    public static LegacyRenderShim getInstance(Object ignored) {
        return instance;
    }

    public LegacyRenderShim getBuilder() {
        return this;
    }

    public LegacyRenderShim getWorldRenderer() {
        return this;
    }

    public static Object getBuffer(Object ignored) {
        return instance;
    }

    // =========================================================================
    // 1. ThreadLocal Render Context Management
    // =========================================================================

    public static class RenderContext {
        public Object poseStack;
        public Object bufferSource;
        public Object vertexConsumer;
        public int combinedLight = 0x00F000F0;
        public int combinedOverlay = 0x000A0000;

        // Current color (RGBA 0.0 - 1.0)
        public float r = 1.0f;
        public float g = 1.0f;
        public float b = 1.0f;
        public float a = 1.0f;

        // Current normal
        public float nx = 0.0f;
        public float ny = 1.0f;
        public float nz = 0.0f;

        // Translation offset for Tessellator
        public double offsetX = 0.0;
        public double offsetY = 0.0;
        public double offsetZ = 0.0;

        // Matrix stack
        public final Deque<float[]> matrixStack = new ArrayDeque<>();
        public float[] currentMatrix = createIdentityMatrix();

        // Tessellator vertex accumulator
        public int drawMode = 7; // GL_QUADS = 7
        public boolean isDrawing = false;
        public final List<AccumulatedVertex> vertices = new ArrayList<>();

        // Pending vertex attributes for BufferBuilder style calls
        public double pendingX;
        public double pendingY;
        public double pendingZ;
        public double pendingU;
        public double pendingV;
        public float pendingR = 1.0f;
        public float pendingG = 1.0f;
        public float pendingB = 1.0f;
        public float pendingA = 1.0f;
        public float pendingNx = 0.0f;
        public float pendingNy = 1.0f;
        public float pendingNz = 0.0f;
        public int pendingLight = 0x00F000F0;
        public int pendingOverlay = 0x000A0000;
        public boolean hasPendingPos = false;
        public Object guiGraphics;
        public Object boundTexture;

        public RenderContext() {
            matrixStack.push(createIdentityMatrix());
        }

        public void reset() {
            guiGraphics = null;
            boundTexture = null;
            poseStack = null;
            bufferSource = null;
            vertexConsumer = null;
            combinedLight = 0x00F000F0;
            combinedOverlay = 0x000A0000;
            r = 1.0f;
            g = 1.0f;
            b = 1.0f;
            a = 1.0f;
            nx = 0.0f;
            ny = 1.0f;
            nz = 0.0f;
            offsetX = 0.0;
            offsetY = 0.0;
            offsetZ = 0.0;
            matrixStack.clear();
            currentMatrix = createIdentityMatrix();
            matrixStack.push(createIdentityMatrix());
            isDrawing = false;
            vertices.clear();
            hasPendingPos = false;
        }
    }

    public static class AccumulatedVertex {
        public float x, y, z;
        public float u, v;
        public float r, g, b, a;
        public float nx, ny, nz;
        public int light;
        public int overlay;

        public AccumulatedVertex(float x, float y, float z, float u, float v,
                                 float r, float g, float b, float a,
                                 float nx, float ny, float nz, int light, int overlay) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.light = light;
            this.overlay = overlay;
        }
    }

    public static void bindContext(Object poseStack, Object bufferSource, int combinedLight, int combinedOverlay) {
        RenderContext ctx = CONTEXT.get();
        ctx.poseStack = poseStack;
        ctx.bufferSource = bufferSource;
        ctx.combinedLight = combinedLight;
        ctx.combinedOverlay = combinedOverlay;
        ctx.pendingLight = combinedLight;
        ctx.pendingOverlay = combinedOverlay;
    }

    public static void pushContext(Object poseStack, Object bufferSource, int combinedLight, int combinedOverlay) {
        bindContext(poseStack, bufferSource, combinedLight, combinedOverlay);
    }

    public static void bindVertexConsumer(Object vertexConsumer) {
        CONTEXT.get().vertexConsumer = vertexConsumer;
    }

    public static void releaseContext() {
        CONTEXT.get().reset();
    }

    public static void popContext() {
        releaseContext();
    }

    public static RenderContext getContext() {
        return CONTEXT.get();
    }

    public static Object getBoundPoseStack() {
        return CONTEXT.get().poseStack;
    }

    public static Object getBoundBufferSource() {
        return CONTEXT.get().bufferSource;
    }

    public static Object getBoundVertexConsumer() {
        return CONTEXT.get().vertexConsumer;
    }

    public static int getBoundLight() {
        return CONTEXT.get().combinedLight;
    }

    public static int getBoundOverlay() {
        return CONTEXT.get().combinedOverlay;
    }

    public static float[] getCurrentMatrix() {
        return Arrays.copyOf(CONTEXT.get().currentMatrix, 16);
    }

    public static int getAccumulatedVertexCount() {
        return CONTEXT.get().vertices.size();
    }

    // =========================================================================
    // 2. Matrix Stack Operations (GL11 / GlStateManager / PoseStack)
    // =========================================================================

    public static void glPushMatrix() {
        pushMatrix();
    }

    public static void pushMatrix() {
        RenderContext ctx = CONTEXT.get();
        float[] copy = Arrays.copyOf(ctx.currentMatrix, 16);
        ctx.matrixStack.push(copy);

        if (ctx.poseStack != null) {
            invokePoseStackMethod(ctx.poseStack, "pushPose", "push");
        }
    }

    public static void glPopMatrix() {
        popMatrix();
    }

    public static void popMatrix() {
        RenderContext ctx = CONTEXT.get();
        if (!ctx.matrixStack.isEmpty()) {
            ctx.currentMatrix = ctx.matrixStack.pop();
        }

        if (ctx.poseStack != null) {
            invokePoseStackMethod(ctx.poseStack, "popPose", "pop");
        }
    }

    public static void loadIdentity() {
        RenderContext ctx = CONTEXT.get();
        ctx.currentMatrix = createIdentityMatrix();
    }

    public static void glTranslatef(float x, float y, float z) {
        translate(x, y, z);
    }

    public static void glTranslated(double x, double y, double z) {
        translate(x, y, z);
    }

    public static void translate(double x, double y, double z) {
        translate((float) x, (float) y, (float) z);
    }

    public static void translate(float x, float y, float z) {
        RenderContext ctx = CONTEXT.get();
        multiplyTranslation(ctx.currentMatrix, x, y, z);

        if (ctx.poseStack != null) {
            invokePoseStackTranslate(ctx.poseStack, x, y, z);
        }
    }

    public static void glScalef(float x, float y, float z) {
        scale(x, y, z);
    }

    public static void glScaled(double x, double y, double z) {
        scale(x, y, z);
    }

    public static void scale(double x, double y, double z) {
        scale((float) x, (float) y, (float) z);
    }

    public static void scale(float x, float y, float z) {
        RenderContext ctx = CONTEXT.get();
        multiplyScale(ctx.currentMatrix, x, y, z);

        if (ctx.poseStack != null) {
            invokePoseStackScale(ctx.poseStack, x, y, z);
        }
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        rotate(angle, x, y, z);
    }

    public static void glRotated(double angle, double x, double y, double z) {
        rotate((float) angle, (float) x, (float) y, (float) z);
    }

    public static void rotate(double angle, double x, double y, double z) {
        rotate((float) angle, (float) x, (float) y, (float) z);
    }

    public static void rotate(float angleDegrees, float x, float y, float z) {
        RenderContext ctx = CONTEXT.get();
        multiplyRotation(ctx.currentMatrix, angleDegrees, x, y, z);

        if (ctx.poseStack != null) {
            invokePoseStackRotate(ctx.poseStack, angleDegrees, x, y, z);
        }
    }

    // =========================================================================
    // 3. State & Color Operations (GL11 / GlStateManager / RenderSystem)
    // =========================================================================

    public static void glColor4f(float r, float g, float b, float a) {
        color(r, g, b, a);
    }

    public static void glColor3f(float r, float g, float b) {
        color(r, g, b, 1.0f);
    }

    public static void glColor4ub(byte r, byte g, byte b, byte a) {
        color((r & 0xFF) / 255.0f, (g & 0xFF) / 255.0f, (b & 0xFF) / 255.0f, (a & 0xFF) / 255.0f);
    }

    public static void color(float r, float g, float b, float a) {
        RenderContext ctx = CONTEXT.get();
        ctx.r = r;
        ctx.g = g;
        ctx.b = b;
        ctx.a = a;
        ctx.pendingR = r;
        ctx.pendingG = g;
        ctx.pendingB = b;
        ctx.pendingA = a;

        invokeRenderSystemColor(r, g, b, a);
    }

    public static void color(float r, float g, float b) {
        color(r, g, b, 1.0f);
    }

    public static void color(int r, int g, int b, int a) {
        color(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
    }

    public static void color4f(float r, float g, float b, float a) {
        color(r, g, b, a);
    }

    public static void color4ub(int r, int g, int b, int a) {
        color(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
    }

    public static void glEnable(int cap) {
        switch (cap) {
            case 3042: // GL_BLEND
                enableBlend();
                break;
            case 3553: // GL_TEXTURE_2D
                enableTexture();
                break;
            case 2929: // GL_DEPTH_TEST
                enableDepth();
                break;
            case 2884: // GL_CULL_FACE
                enableCull();
                break;
            case 2896: // GL_LIGHTING
                enableLighting();
                break;
            default:
                break;
        }
    }

    public static void glDisable(int cap) {
        switch (cap) {
            case 3042: // GL_BLEND
                disableBlend();
                break;
            case 3553: // GL_TEXTURE_2D
                disableTexture();
                break;
            case 2929: // GL_DEPTH_TEST
                disableDepth();
                break;
            case 2884: // GL_CULL_FACE
                disableCull();
                break;
            case 2896: // GL_LIGHTING
                disableLighting();
                break;
            default:
                break;
        }
    }

    public static void enableBlend() {
        invokeRenderSystemVoid("enableBlend");
    }

    public static void disableBlend() {
        invokeRenderSystemVoid("disableBlend");
    }

    public static void enableTexture() {
        invokeRenderSystemVoid("enableTexture");
    }

    public static void disableTexture() {
        invokeRenderSystemVoid("disableTexture");
    }

    public static void enableDepth() {
        invokeRenderSystemVoid("enableDepthTest");
    }

    public static void disableDepth() {
        invokeRenderSystemVoid("disableDepthTest");
    }

    public static void enableCull() {
        invokeRenderSystemVoid("enableCull");
    }

    public static void disableCull() {
        invokeRenderSystemVoid("disableCull");
    }

    public static void enableLighting() {
        // Lighting is handled via shader uniforms in modern MC
    }

    public static void disableLighting() {
        // Lighting is handled via shader uniforms in modern MC
    }

    public static void glBlendFunc(int sfactor, int dfactor) {
        blendFunc(sfactor, dfactor);
    }

    public static void blendFunc(int sfactor, int dfactor) {
        try {
            Class<?> rs = getRenderSystemClass();
            if (rs != null) {
                Method m = rs.getMethod("blendFunc", int.class, int.class);
                m.invoke(null, sfactor, dfactor);
            }
        } catch (Throwable ignored) {}
    }

    public static void glDepthMask(boolean flag) {
        depthMask(flag);
    }

    public static void depthMask(boolean flag) {
        try {
            Class<?> rs = getRenderSystemClass();
            if (rs != null) {
                Method m = rs.getMethod("depthMask", boolean.class);
                m.invoke(null, flag);
            }
        } catch (Throwable ignored) {}
    }

    public static void glDepthFunc(int func) {
        depthFunc(func);
    }

    public static void depthFunc(int func) {
        try {
            Class<?> rs = getRenderSystemClass();
            if (rs != null) {
                Method m = rs.getMethod("depthFunc", int.class);
                m.invoke(null, func);
            }
        } catch (Throwable ignored) {}
    }

    public static void glCullFace(int mode) {
        // Mode mapping if needed
    }

    public static void cullFace(int mode) {
        glCullFace(mode);
    }

    public static void glLineWidth(float width) {
        try {
            Class<?> rs = getRenderSystemClass();
            if (rs != null) {
                Method m = rs.getMethod("lineWidth", float.class);
                m.invoke(null, width);
            }
        } catch (Throwable ignored) {}
    }

    public static void glBindTexture(int target, int texture) {
        bindTexture(texture);
    }

    public static void bindTexture(int texture) {
        try {
            Class<?> rs = getRenderSystemClass();
            if (rs != null) {
                Method m = rs.getMethod("setShaderTexture", int.class, int.class);
                m.invoke(null, 0, texture);
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // 4. Tessellator & BufferBuilder Emulation
    // =========================================================================

    public static void startDrawingQuads(Object tessellator) {
        startDrawing(tessellator, 7);
    }

    public void startDrawingQuads() {
        startDrawing(7);
    }

    public static void startDrawing(Object tessellator, int mode) {
        instance.startDrawing(mode);
    }

    public void startDrawing(int mode) {
        RenderContext ctx = CONTEXT.get();
        ctx.drawMode = mode;
        ctx.isDrawing = true;
        ctx.vertices.clear();
        ctx.hasPendingPos = false;
    }

    public static void begin(Object tessellator, int mode, Object format) {
        instance.begin(mode, format);
    }

    public void begin(int mode, Object format) {
        startDrawing(mode);
    }

    public static void addVertexWithUV(Object tessellator, double x, double y, double z, double u, double v) {
        instance.addVertexWithUV(x, y, z, u, v);
    }

    public void addVertexWithUV(double x, double y, double z, double u, double v) {
        RenderContext ctx = CONTEXT.get();
        float[] pos = transformPoint(ctx.currentMatrix,
                (float) (x + ctx.offsetX),
                (float) (y + ctx.offsetY),
                (float) (z + ctx.offsetZ));

        float[] norm = transformNormal(ctx.currentMatrix, ctx.nx, ctx.ny, ctx.nz);

        ctx.vertices.add(new AccumulatedVertex(
                pos[0], pos[1], pos[2],
                (float) u, (float) v,
                ctx.r, ctx.g, ctx.b, ctx.a,
                norm[0], norm[1], norm[2],
                ctx.combinedLight, ctx.combinedOverlay
        ));
    }

    public static void addVertex(Object tessellator, double x, double y, double z) {
        instance.addVertex(x, y, z);
    }

    public void addVertex(double x, double y, double z) {
        addVertexWithUV(x, y, z, 0.0, 0.0);
    }

    public static void setColorRGBA(Object tessellator, int r, int g, int b, int a) {
        color(r, g, b, a);
    }

    public static void setColorRGBA_F(Object tessellator, float r, float g, float b, float a) {
        color(r, g, b, a);
    }

    public static void setColorOpaque(Object tessellator, int r, int g, int b) {
        color(r, g, b, 255);
    }

    public static void setColorOpaque_F(Object tessellator, float r, float g, float b) {
        color(r, g, b, 1.0f);
    }

    public static void setNormal(Object tessellator, float x, float y, float z) {
        RenderContext ctx = CONTEXT.get();
        ctx.nx = x;
        ctx.ny = y;
        ctx.nz = z;
        ctx.pendingNx = x;
        ctx.pendingNy = y;
        ctx.pendingNz = z;
    }

    public static void setTranslation(Object tessellator, double x, double y, double z) {
        RenderContext ctx = CONTEXT.get();
        ctx.offsetX = x;
        ctx.offsetY = y;
        ctx.offsetZ = z;
    }

    public static void setBrightness(Object tessellator, int brightness) {
        RenderContext ctx = CONTEXT.get();
        ctx.combinedLight = brightness;
        ctx.pendingLight = brightness;
    }

    // Fluent BufferBuilder API (pos, tex, color, normal, endVertex)
    public static LegacyRenderShim pos(Object buffer, double x, double y, double z) {
        RenderContext ctx = CONTEXT.get();
        ctx.pendingX = x;
        ctx.pendingY = y;
        ctx.pendingZ = z;
        ctx.hasPendingPos = true;
        return instance;
    }

    public static LegacyRenderShim tex(Object buffer, double u, double v) {
        RenderContext ctx = CONTEXT.get();
        ctx.pendingU = u;
        ctx.pendingV = v;
        return instance;
    }

    public static LegacyRenderShim color(Object buffer, int r, int g, int b, int a) {
        RenderContext ctx = CONTEXT.get();
        ctx.pendingR = r / 255.0f;
        ctx.pendingG = g / 255.0f;
        ctx.pendingB = b / 255.0f;
        ctx.pendingA = a / 255.0f;
        return instance;
    }

    public static LegacyRenderShim color(Object buffer, float r, float g, float b, float a) {
        RenderContext ctx = CONTEXT.get();
        ctx.pendingR = r;
        ctx.pendingG = g;
        ctx.pendingB = b;
        ctx.pendingA = a;
        return instance;
    }

    public static LegacyRenderShim normal(Object buffer, float x, float y, float z) {
        RenderContext ctx = CONTEXT.get();
        ctx.pendingNx = x;
        ctx.pendingNy = y;
        ctx.pendingNz = z;
        return instance;
    }

    public static LegacyRenderShim lightmap(Object buffer, int sky, int block) {
        RenderContext ctx = CONTEXT.get();
        ctx.pendingLight = (sky << 16) | (block & 0xFFFF);
        return instance;
    }

    public static LegacyRenderShim overlay(Object buffer, int u, int v) {
        RenderContext ctx = CONTEXT.get();
        ctx.pendingOverlay = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
        return instance;
    }

    public static void endVertex(Object buffer) {
        RenderContext ctx = CONTEXT.get();
        if (ctx.hasPendingPos) {
            float[] pos = transformPoint(ctx.currentMatrix,
                    (float) (ctx.pendingX + ctx.offsetX),
                    (float) (ctx.pendingY + ctx.offsetY),
                    (float) (ctx.pendingZ + ctx.offsetZ));

            float[] norm = transformNormal(ctx.currentMatrix, ctx.pendingNx, ctx.pendingNy, ctx.pendingNz);

            ctx.vertices.add(new AccumulatedVertex(
                    pos[0], pos[1], pos[2],
                    (float) ctx.pendingU, (float) ctx.pendingV,
                    ctx.pendingR, ctx.pendingG, ctx.pendingB, ctx.pendingA,
                    norm[0], norm[1], norm[2],
                    ctx.pendingLight, ctx.pendingOverlay
            ));
            ctx.hasPendingPos = false;
        }
    }

    public static int draw(Object tessellator) {
        return instance.draw();
    }

    public static void drawVoid(Object tessellator) {
        instance.draw();
    }

    public int draw() {
        RenderContext ctx = CONTEXT.get();
        if (!ctx.isDrawing || ctx.vertices.isEmpty()) {
            ctx.isDrawing = false;
            ctx.vertices.clear();
            return 0;
        }

        int count = ctx.vertices.size();

        // 1. Direct VertexConsumer routing if active
        Object consumer = ctx.vertexConsumer;
        if (consumer == null && ctx.bufferSource != null) {
            consumer = resolveVertexConsumerFromSource(ctx.bufferSource);
        }

        if (consumer != null) {
            emitToVertexConsumer(consumer, ctx.vertices);
        } else {
            // 2. Standalone modern Tessellator invocation
            emitToModernTessellator(ctx.vertices, ctx.drawMode);
        }

        ctx.isDrawing = false;
        ctx.vertices.clear();
        return count;
    }

    private static Object resolveVertexConsumerFromSource(Object bufferSource) {
        if (bufferSource == null) return null;
        try {
            // Try bufferSource.getBuffer(RenderType)
            Class<?> renderTypeClass = Class.forName("net.minecraft.client.renderer.RenderType");
            Method solidMethod = renderTypeClass.getMethod("solid");
            Object solidType = solidMethod.invoke(null);

            for (Method m : bufferSource.getClass().getMethods()) {
                if ("getBuffer".equals(m.getName()) && m.getParameterCount() == 1) {
                    return m.invoke(bufferSource, solidType);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void emitToVertexConsumer(Object consumer, List<AccumulatedVertex> vertices) {
        if (consumer == null || vertices == null || vertices.isEmpty()) return;

        Class<?> clazz = consumer.getClass();
        try {
            // Check for modern 1.21+ / NeoForge addVertex / setColor / setUv API
            Method addVertexMethod = findMethod(clazz, "addVertex", float.class, float.class, float.class);
            Method setColorMethod = findMethod(clazz, "setColor", int.class, int.class, int.class, int.class);
            Method setUvMethod = findMethod(clazz, "setUv", float.class, float.class);
            Method setOverlayMethod = findMethod(clazz, "setOverlay", int.class);
            Method setLightMethod = findMethod(clazz, "setLight", int.class);
            Method setNormalMethod = findMethod(clazz, "setNormal", float.class, float.class, float.class);

            if (addVertexMethod != null && setColorMethod != null && setUvMethod != null) {
                for (AccumulatedVertex v : vertices) {
                    addVertexMethod.invoke(consumer, v.x, v.y, v.z);
                    setColorMethod.invoke(consumer, (int) (v.r * 255), (int) (v.g * 255), (int) (v.b * 255), (int) (v.a * 255));
                    setUvMethod.invoke(consumer, v.u, v.v);
                    if (setOverlayMethod != null) setOverlayMethod.invoke(consumer, v.overlay);
                    if (setLightMethod != null) setLightMethod.invoke(consumer, v.light);
                    if (setNormalMethod != null) setNormalMethod.invoke(consumer, v.nx, v.ny, v.nz);
                }
                return;
            }

            // Standard 1.15 - 1.20 VertexConsumer pipeline
            Method vertexMethod = findMethod(clazz, "vertex", double.class, double.class, double.class);
            if (vertexMethod == null) {
                vertexMethod = findMethod(clazz, "vertex", float.class, float.class, float.class);
            }
            Method colorMethod = findMethod(clazz, "color", int.class, int.class, int.class, int.class);
            Method uvMethod = findMethod(clazz, "uv", float.class, float.class);
            Method overlayMethod = findMethod(clazz, "overlayCoords", int.class);
            Method uv2Method = findMethod(clazz, "uv2", int.class);
            Method normalMethod = findMethod(clazz, "normal", float.class, float.class, float.class);
            Method endVertexMethod = findMethod(clazz, "endVertex");

            for (AccumulatedVertex v : vertices) {
                if (vertexMethod != null) {
                    if (vertexMethod.getParameterTypes()[0] == double.class) {
                        vertexMethod.invoke(consumer, (double) v.x, (double) v.y, (double) v.z);
                    } else {
                        vertexMethod.invoke(consumer, v.x, v.y, v.z);
                    }
                }
                if (colorMethod != null) {
                    colorMethod.invoke(consumer, (int) (v.r * 255), (int) (v.g * 255), (int) (v.b * 255), (int) (v.a * 255));
                }
                if (uvMethod != null) {
                    uvMethod.invoke(consumer, v.u, v.v);
                }
                if (overlayMethod != null) {
                    overlayMethod.invoke(consumer, v.overlay);
                }
                if (uv2Method != null) {
                    uv2Method.invoke(consumer, v.light);
                }
                if (normalMethod != null) {
                    normalMethod.invoke(consumer, v.nx, v.ny, v.nz);
                }
                if (endVertexMethod != null) {
                    endVertexMethod.invoke(consumer);
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[LegacyRenderShim] Error emitting vertices to VertexConsumer", t);
        }
    }

    private static void emitToModernTessellator(List<AccumulatedVertex> vertices, int drawMode) {
        if (vertices == null || vertices.isEmpty()) return;
        try {
            Class<?> tessClass = Class.forName("net.minecraft.client.renderer.Tessellator");
            Method getInst = tessClass.getMethod("getInstance");
            Object modernTess = getInst.invoke(null);
            if (modernTess == null) return;

            // Try 1.15 - 1.20 getBuilder()
            Method getBuilder = findMethod(tessClass, "getBuilder");
            if (getBuilder != null) {
                Object builder = getBuilder.invoke(modernTess);
                if (builder != null) {
                    emitToVertexConsumer(builder, vertices);
                    Method endMethod = findMethod(builder.getClass(), "end");
                    if (endMethod != null) {
                        Object buffer = endMethod.invoke(builder);
                        Class<?> uploaderClass = Class.forName("com.mojang.blaze3d.vertex.BufferUploader");
                        Method drawMethod = findMethod(uploaderClass, "drawWithShader", buffer.getClass());
                        if (drawMethod != null) {
                            drawMethod.invoke(null, buffer);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Graceful fallback for test or headless environments
        }
    }

    // =========================================================================
    // 5. Fallback Custom TileEntity & Item Renderers
    // =========================================================================

    /**
     * Fallback dispatcher bridging legacy BlockEntity / TileEntity renderers to modern
     * BlockEntityRenderer.render(T, float, PoseStack, MultiBufferSource, int, int).
     */
    public static void renderBlockEntity(Object renderer, Object blockEntity, float partialTicks,
                                         Object poseStack, Object bufferSource,
                                         int combinedLight, int combinedOverlay) {
        if (renderer == null || blockEntity == null) return;

        bindContext(poseStack, bufferSource, combinedLight, combinedOverlay);
        pushMatrix();

        try {
            // 1. Try modern BlockEntityRenderer.render(T, float, PoseStack, MultiBufferSource, int, int)
            for (Method m : renderer.getClass().getMethods()) {
                if ("render".equals(m.getName()) && m.getParameterCount() == 6) {
                    m.invoke(renderer, blockEntity, partialTicks, poseStack, bufferSource, combinedLight, combinedOverlay);
                    return;
                }
            }

            // Extract coordinates from block entity if available
            double x = 0.0, y = 0.0, z = 0.0;
            try {
                Method getPos = blockEntity.getClass().getMethod("getBlockPos");
                Object pos = getPos.invoke(blockEntity);
                if (pos != null) {
                    x = (double) (int) pos.getClass().getMethod("getX").invoke(pos);
                    y = (double) (int) pos.getClass().getMethod("getY").invoke(pos);
                    z = (double) (int) pos.getClass().getMethod("getZ").invoke(pos);
                }
            } catch (Throwable ignored) {
                try {
                    x = blockEntity.getClass().getField("xCoord").getInt(blockEntity);
                    y = blockEntity.getClass().getField("yCoord").getInt(blockEntity);
                    z = blockEntity.getClass().getField("zCoord").getInt(blockEntity);
                } catch (Throwable ignored2) {}
            }

            // 2. Try legacy 1.8-1.12 TileEntitySpecialRenderer.render(T, double, double, double, float, int)
            for (Method m : renderer.getClass().getMethods()) {
                if ("render".equals(m.getName()) && m.getParameterCount() == 6
                        && m.getParameterTypes()[1] == double.class) {
                    m.invoke(renderer, blockEntity, x, y, z, partialTicks, -1);
                    return;
                }
            }

            // 3. Try legacy 1.7.10 renderTileEntityAt(TileEntity, double, double, double, float)
            for (Method m : renderer.getClass().getMethods()) {
                if ("renderTileEntityAt".equals(m.getName()) && m.getParameterCount() == 5) {
                    m.invoke(renderer, blockEntity, x, y, z, partialTicks);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[LegacyRenderShim] Error dispatching custom BlockEntity renderer", t);
        } finally {
            popMatrix();
            releaseContext();
        }
    }

    /**
     * Fallback dispatcher for Forge legacy FastTESR / renderTileEntityFast.
     */
    public static void renderTileEntityFast(Object renderer, Object tileEntity, double x, double y, double z,
                                            float partialTicks, int destroyStage, Object poseStack, Object bufferSource) {
        if (renderer == null || tileEntity == null) return;

        bindContext(poseStack, bufferSource, 0x00F000F0, 0x000A0000);
        pushMatrix();

        try {
            for (Method m : renderer.getClass().getMethods()) {
                if ("renderTileEntityFast".equals(m.getName())) {
                    if (m.getParameterCount() == 7) {
                        m.invoke(renderer, tileEntity, x, y, z, partialTicks, destroyStage, bufferSource);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[LegacyRenderShim] Error dispatching FastTESR", t);
        } finally {
            popMatrix();
            releaseContext();
        }
    }

    /**
     * Fallback dispatcher bridging custom Item renderers (IItemRenderer, TileEntityItemStackRenderer,
     * BlockEntityWithoutLevelRenderer) across all Minecraft versions.
     */
    public static void renderItem(Object renderer, Object itemStack, Object displayContext,
                                  Object poseStack, Object bufferSource,
                                  int combinedLight, int combinedOverlay) {
        if (renderer == null || itemStack == null) return;

        bindContext(poseStack, bufferSource, combinedLight, combinedOverlay);
        pushMatrix();

        try {
            // 1. Modern 1.19.4+ BlockEntityWithoutLevelRenderer.renderByItem(ItemStack, ItemDisplayContext, PoseStack, MultiBufferSource, int, int)
            for (Method m : renderer.getClass().getMethods()) {
                if ("renderByItem".equals(m.getName()) && m.getParameterCount() == 6) {
                    m.invoke(renderer, itemStack, displayContext, poseStack, bufferSource, combinedLight, combinedOverlay);
                    return;
                }
            }

            // 2. 1.15 - 1.19.2 renderByItem(ItemStack, ItemTransforms.TransformType / PoseStack, PoseStack, MultiBufferSource, int, int)
            for (Method m : renderer.getClass().getMethods()) {
                if ("renderByItem".equals(m.getName()) && m.getParameterCount() == 5) {
                    m.invoke(renderer, itemStack, poseStack, bufferSource, combinedLight, combinedOverlay);
                    return;
                }
            }

            // 3. 1.8 - 1.14 TileEntityItemStackRenderer.renderByItem(ItemStack)
            for (Method m : renderer.getClass().getMethods()) {
                if ("renderByItem".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(renderer, itemStack);
                    return;
                }
            }

            // 4. 1.7.10 IItemRenderer.renderItem(ItemRenderType, ItemStack, Object...)
            for (Method m : renderer.getClass().getMethods()) {
                if ("renderItem".equals(m.getName()) && m.getParameterCount() == 3) {
                    m.invoke(renderer, displayContext, itemStack, new Object[0]);
                    return;
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[LegacyRenderShim] Error dispatching custom Item renderer", t);
        } finally {
            popMatrix();
            releaseContext();
        }
    }

    public static void renderItem(Object renderer, Object itemStack, Object transformType, boolean leftHand,
                                  Object poseStack, Object bufferSource, int combinedLight, int combinedOverlay, Object fallback) {
        renderItem(renderer, itemStack, transformType, poseStack, bufferSource, combinedLight, combinedOverlay);
    }

    // =========================================================================
    // 6. Matrix & Math Helpers (4x4 Matrix Mathematics)
    // =========================================================================

    public static float[] createIdentityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    public static void multiplyTranslation(float[] m, float tx, float ty, float tz) {
        // Translation in column-major: column 3 is offset by tx * col0 + ty * col1 + tz * col2
        m[12] += m[0] * tx + m[4] * ty + m[8] * tz;
        m[13] += m[1] * tx + m[5] * ty + m[9] * tz;
        m[14] += m[2] * tx + m[6] * ty + m[10] * tz;
        m[15] += m[3] * tx + m[7] * ty + m[11] * tz;
    }

    public static void multiplyScale(float[] m, float sx, float sy, float sz) {
        m[0] *= sx; m[1] *= sx; m[2] *= sx; m[3] *= sx;
        m[4] *= sy; m[5] *= sy; m[6] *= sy; m[7] *= sy;
        m[8] *= sz; m[9] *= sz; m[10] *= sz; m[11] *= sz;
    }

    public static void multiplyRotation(float[] m, float angleDegrees, float x, float y, float z) {
        float lenSq = x * x + y * y + z * z;
        if (lenSq < 1.0e-6f) return;
        float invLen = 1.0f / (float) Math.sqrt(lenSq);
        x *= invLen;
        y *= invLen;
        z *= invLen;

        float rad = (float) Math.toRadians(angleDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float oneMinusCos = 1.0f - cos;

        float[] r = new float[]{
                cos + x * x * oneMinusCos,        y * x * oneMinusCos + z * sin,    z * x * oneMinusCos - y * sin,    0f,
                x * y * oneMinusCos - z * sin,    cos + y * y * oneMinusCos,        z * y * oneMinusCos + x * sin,    0f,
                x * z * oneMinusCos + y * sin,    y * z * oneMinusCos - x * sin,    cos + z * z * oneMinusCos,        0f,
                0f,                               0f,                               0f,                               1f
        };

        float[] temp = Arrays.copyOf(m, 16);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m[i * 4 + j] =
                        temp[j] * r[i * 4] +
                        temp[4 + j] * r[i * 4 + 1] +
                        temp[8 + j] * r[i * 4 + 2] +
                        temp[12 + j] * r[i * 4 + 3];
            }
        }
    }

    public static float[] transformPoint(float[] m, float px, float py, float pz) {
        float x = m[0] * px + m[4] * py + m[8] * pz + m[12];
        float y = m[1] * px + m[5] * py + m[9] * pz + m[13];
        float z = m[2] * px + m[6] * py + m[10] * pz + m[14];
        float w = m[3] * px + m[7] * py + m[11] * pz + m[15];
        if (w != 1.0f && w != 0.0f) {
            float invW = 1.0f / w;
            x *= invW;
            y *= invW;
            z *= invW;
        }
        return new float[]{x, y, z};
    }

    public static float[] transformNormal(float[] m, float nx, float ny, float nz) {
        float x = m[0] * nx + m[4] * ny + m[8] * nz;
        float y = m[1] * nx + m[5] * ny + m[9] * nz;
        float z = m[2] * nx + m[6] * ny + m[10] * nz;
        float lenSq = x * x + y * y + z * z;
        if (lenSq > 1.0e-6f) {
            float invLen = 1.0f / (float) Math.sqrt(lenSq);
            x *= invLen;
            y *= invLen;
            z *= invLen;
        }
        return new float[]{x, y, z};
    }

    // =========================================================================
    // 7. Reflection Invocations for Modern Systems
    // =========================================================================

    private static Class<?> getRenderSystemClass() {
        try {
            return Class.forName("com.mojang.blaze3d.systems.RenderSystem");
        } catch (Throwable ignored) {
            try {
                return Class.forName("net.minecraft.client.renderer.RenderSystem");
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private static void invokeRenderSystemVoid(String methodName) {
        try {
            Class<?> rs = getRenderSystemClass();
            if (rs != null) {
                Method m = rs.getMethod(methodName);
                m.invoke(null);
            }
        } catch (Throwable ignored) {}
    }

    private static void invokeRenderSystemColor(float r, float g, float b, float a) {
        try {
            Class<?> rs = getRenderSystemClass();
            if (rs != null) {
                Method m = findMethod(rs, "setShaderColor", float.class, float.class, float.class, float.class);
                if (m == null) {
                    m = findMethod(rs, "color4f", float.class, float.class, float.class, float.class);
                }
                if (m != null) {
                    m.invoke(null, r, g, b, a);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void invokePoseStackMethod(Object poseStack, String... methodNames) {
        if (poseStack == null) return;
        for (String name : methodNames) {
            Method m = findMethod(poseStack.getClass(), name);
            if (m != null) {
                try {
                    m.invoke(poseStack);
                    return;
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void invokePoseStackTranslate(Object poseStack, float x, float y, float z) {
        if (poseStack == null) return;
        Method m = findMethod(poseStack.getClass(), "translate", double.class, double.class, double.class);
        if (m != null) {
            try {
                m.invoke(poseStack, (double) x, (double) y, (double) z);
                return;
            } catch (Throwable ignored) {}
        }
        m = findMethod(poseStack.getClass(), "translate", float.class, float.class, float.class);
        if (m != null) {
            try {
                m.invoke(poseStack, x, y, z);
            } catch (Throwable ignored) {}
        }
    }

    private static void invokePoseStackScale(Object poseStack, float x, float y, float z) {
        if (poseStack == null) return;
        Method m = findMethod(poseStack.getClass(), "scale", float.class, float.class, float.class);
        if (m != null) {
            try {
                m.invoke(poseStack, x, y, z);
            } catch (Throwable ignored) {}
        }
    }

    private static void invokePoseStackRotate(Object poseStack, float angle, float x, float y, float z) {
        if (poseStack == null) return;
        // PoseStack rotation in modern MC uses Quaternionf / Axis.
        // Internal matrix already tracks exact transformed vertex positions; PoseStack rotation is best-effort.
        try {
            Method m = findMethod(poseStack.getClass(), "mulPose");
            if (m != null) {
                Class<?> paramType = m.getParameterTypes()[0];
                Method rotDegrees = findMethod(paramType, "rotationDegrees", float.class);
                if (rotDegrees != null) {
                    // Try to invoke rotation if static
                    // (otherwise vertex transformation handles it)
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        String key = clazz.getName() + "#" + name + Arrays.toString(paramTypes);
        return METHOD_CACHE.computeIfAbsent(key, k -> {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    if (paramTypes.length == 0 && m.getParameterCount() == 0) {
                        try { m.setAccessible(true); } catch (Throwable ignored) {}
                        return m;
                    }
                    if (m.getParameterCount() == paramTypes.length) {
                        Class<?>[] pts = m.getParameterTypes();
                        boolean match = true;
                        for (int i = 0; i < pts.length; i++) {
                            if (!pts[i].isAssignableFrom(paramTypes[i]) && pts[i] != paramTypes[i]) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            try { m.setAccessible(true); } catch (Throwable ignored) {}
                            return m;
                        }
                    }
                }
            }
            return null;
        });
    }

    public static void bindScreenContext(Object graphicsOrPose) {
        if (graphicsOrPose == null) return;
        RenderContext ctx = CONTEXT.get();
        if (graphicsOrPose.getClass().getName().contains("GuiGraphics")) {
            ctx.guiGraphics = graphicsOrPose;
            try {
                Method poseMethod = graphicsOrPose.getClass().getMethod("pose");
                ctx.poseStack = poseMethod.invoke(graphicsOrPose);
            } catch (Throwable ignored) {
                ctx.poseStack = graphicsOrPose;
            }
        } else {
            ctx.poseStack = graphicsOrPose;
            ctx.guiGraphics = null;
        }
    }

    public static Object getCurrentScreenContext() {
        RenderContext ctx = CONTEXT.get();
        return (ctx.guiGraphics != null) ? ctx.guiGraphics : ctx.poseStack;
    }

    public static void releaseScreenContext() {
        RenderContext ctx = CONTEXT.get();
        if (ctx.isDrawing) {
            instance.draw();
        }
        ctx.guiGraphics = null;
        ctx.boundTexture = null;
    }

    public static void bindTexture(Object textureLocation) {
        if (textureLocation == null) return;
        RenderContext ctx = CONTEXT.get();
        ctx.boundTexture = textureLocation;

        try {
            Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
            Method setShaderTex = findMethod(renderSystem, "setShaderTexture", int.class, textureLocation.getClass());
            if (setShaderTex != null) {
                setShaderTex.invoke(null, 0, textureLocation);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInst = mcClass.getMethod("getInstance");
            Object mc = getInst.invoke(null);
            Method getTexMgr = mc.getClass().getMethod("getTextureManager");
            Object texMgr = getTexMgr.invoke(mc);
            Method bindMethod = texMgr.getClass().getMethod("bindTexture", textureLocation.getClass());
            bindMethod.invoke(texMgr, textureLocation);
        } catch (Throwable ignored) {}
    }

    public static void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        drawTexturedModalRect(null, x, y, u, v, width, height);
    }

    public static void drawTexturedModalRect(Object screenOrGraphics, int x, int y, int u, int v, int width, int height) {
        RenderContext ctx = CONTEXT.get();
        Object graphics = (screenOrGraphics != null && screenOrGraphics.getClass().getName().contains("GuiGraphics"))
                ? screenOrGraphics : ctx.guiGraphics;

        // 1. Try GuiGraphics.blit(boundTexture, x, y, u, v, width, height)
        if (graphics != null && ctx.boundTexture != null) {
            try {
                for (Method m : graphics.getClass().getMethods()) {
                    if ("blit".equals(m.getName()) && m.getParameterCount() >= 7) {
                        m.invoke(graphics, ctx.boundTexture, x, y, (float) u, (float) v, width, height, 256, 256);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
            try {
                for (Method m : graphics.getClass().getMethods()) {
                    if ("blit".equals(m.getName()) && m.getParameterCount() == 6) {
                        m.invoke(graphics, ctx.boundTexture, x, y, u, v, width, height);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Direct Tessellator quad drawing
        float f = 0.00390625F; // 1 / 256
        float f1 = 0.00390625F;
        instance.startDrawingQuads();
        instance.addVertexWithUV((double)(x + 0), (double)(y + height), 0.0D, (double)((float)(u + 0) * f), (double)((float)(v + height) * f1));
        instance.addVertexWithUV((double)(x + width), (double)(y + height), 0.0D, (double)((float)(u + width) * f), (double)((float)(v + height) * f1));
        instance.addVertexWithUV((double)(x + width), (double)(y + 0), 0.0D, (double)((float)(u + width) * f), (double)((float)(v + 0) * f1));
        instance.addVertexWithUV((double)(x + 0), (double)(y + 0), 0.0D, (double)((float)(u + 0) * f), (double)((float)(v + 0) * f1));
        instance.draw();
    }
}
