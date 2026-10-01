package com.kyroxova.continuumlib.transformer;

import com.kyroxova.bootstrapper.ContinuumBootstrapper;
import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.EnvironmentDetector;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.knowledgebase.rules.ClassRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.MethodRedirectRule;
import com.kyroxova.continuumlib.knowledgebase.rules.PolyfillRule;
import com.kyroxova.continuumlib.knowledgebase.rules.TransformationRule;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SpongePowered Mixin configuration plugin and bytecode adapter for ContinuumLib.
 *
 * Implements compatibility with Mixin's {@code IMixinConfigPlugin} dynamically
 * using reflection and InvocationHandler to avoid strict compile-time dependency
 * if the Mixin library is optional in certain environments.
 *
 * Intercepts Mixin configuration, remapping {@code @Mixin} targets, {@code @Inject} /
 * {@code @Redirect} descriptors, and injected bytecode using {@link ApiKnowledgeBase}.
 */
public class ContinuumMixinPlugin implements InvocationHandler {

    private static final Logger LOGGER = Logger.getLogger(ContinuumMixinPlugin.class.getName());

    public static final String MIXIN_CONFIG_PLUGIN_INTERFACE = "org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin";
    public static final String MIXIN_INFO_INTERFACE = "org.spongepowered.asm.mixin.extensibility.IMixinInfo";

    private final ApiKnowledgeBase knowledgeBase;
    private final TargetSpec baseSpec;
    private final TargetSpec targetSpec;
    private final ContinuumBytecodeTransformer transformer;
    private final Map<String, String> classRedirects = new HashMap<>();
    private final List<MethodRedirectRule> methodRedirects = new ArrayList<>();
    private final List<PolyfillRule> polyfillRules = new ArrayList<>();

    private String mixinPackage;
    private boolean initialized = false;

    public ContinuumMixinPlugin() {
        ContinuumBootstrapper bootstrapper = ContinuumBootstrapper.getInstance();
        BootstrapperConfig config = bootstrapper.getConfig();
        EnvironmentDetector.EnvironmentInfo currentEnv = bootstrapper.getCurrentEnv();

        this.baseSpec = config != null ? config.getBaseSpec() : new TargetSpec(com.kyroxova.bootstrapper.environment.MCVersion.of("1.16.5"), LoaderType.FORGE, "mojmap", 0);
        this.targetSpec = currentEnv != null
                ? new TargetSpec(currentEnv.version(), currentEnv.loader(), "mojmap", 0)
                : new TargetSpec(com.kyroxova.bootstrapper.environment.MCVersion.of("1.20.1"), LoaderType.NEOFORGE, "mojmap", 0);

        this.knowledgeBase = ApiKnowledgeBase.createDefault();
        this.transformer = new ContinuumBytecodeTransformer(this.knowledgeBase, this.baseSpec, this.targetSpec);
        initRules();
    }

    public ContinuumMixinPlugin(ApiKnowledgeBase knowledgeBase, TargetSpec baseSpec, TargetSpec targetSpec) {
        this.knowledgeBase = knowledgeBase != null ? knowledgeBase : ApiKnowledgeBase.createDefault();
        this.baseSpec = baseSpec != null ? baseSpec : new TargetSpec(com.kyroxova.bootstrapper.environment.MCVersion.of("1.16.5"), LoaderType.FORGE, "mojmap", 0);
        this.targetSpec = targetSpec != null ? targetSpec : new TargetSpec(com.kyroxova.bootstrapper.environment.MCVersion.of("1.20.1"), LoaderType.NEOFORGE, "mojmap", 0);
        this.transformer = new ContinuumBytecodeTransformer(this.knowledgeBase, this.baseSpec, this.targetSpec);
        initRules();
    }

    private void initRules() {
        classRedirects.putAll(transformer.getClassRedirects());
        methodRedirects.addAll(transformer.getMethodRedirects());
        polyfillRules.addAll(transformer.getPolyfillRules());
        initialized = true;
    }

    // =========================================================================
    // 1. Dynamic Factory & InvocationHandler Adapter
    // =========================================================================

    /**
     * Creates a dynamic proxy implementing IMixinConfigPlugin if present on the class loader.
     */
    public static Object createProxy(ClassLoader loader) {
        return createProxy(loader, null, null, null);
    }

    /**
     * Creates a dynamic proxy implementing IMixinConfigPlugin with custom knowledge base and target specs.
     */
    public static Object createProxy(ClassLoader loader, ApiKnowledgeBase kb, TargetSpec baseSpec, TargetSpec targetSpec) {
        try {
            Class<?> pluginClass = Class.forName(MIXIN_CONFIG_PLUGIN_INTERFACE, true, loader);
            ContinuumMixinPlugin handler = new ContinuumMixinPlugin(kb, baseSpec, targetSpec);
            return Proxy.newProxyInstance(loader, new Class<?>[]{pluginClass}, handler);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.FINE, "[ContinuumMixinPlugin] IMixinConfigPlugin not found on classpath, returning standalone handler");
            return new ContinuumMixinPlugin(kb, baseSpec, targetSpec);
        }
    }

    /**
     * Dynamically generates an adapter class that implements IMixinConfigPlugin using ASM.
     */
    public static Class<?> getOrGeneratePluginClass(ClassLoader loader) {
        String adapterClassName = "com.kyroxova.continuumlib.transformer.ContinuumMixinPlugin$GeneratedAdapter";
        try {
            return Class.forName(adapterClassName, true, loader);
        } catch (ClassNotFoundException ignored) {}

        try {
            Class<?> iface = Class.forName(MIXIN_CONFIG_PLUGIN_INTERFACE, true, loader);
            byte[] bytecode = generateAdapterBytecode(adapterClassName.replace('.', '/'), iface.getName().replace('.', '/'));

            Method defineMethod = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            defineMethod.setAccessible(true);
            return (Class<?>) defineMethod.invoke(loader, adapterClassName, bytecode, 0, bytecode.length);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[ContinuumMixinPlugin] Unable to generate dynamic adapter class: " + t.getMessage());
            return ContinuumMixinPlugin.class;
        }
    }

    private static byte[] generateAdapterBytecode(String internalName, String ifaceInternalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null,
                "com/kyroxova/continuumlib/transformer/ContinuumMixinPlugin",
                new String[]{ifaceInternalName});

        // Default constructor
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/kyroxova/continuumlib/transformer/ContinuumMixinPlugin", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        int paramCount = method.getParameterCount();

        if ("onLoad".equals(name) && paramCount == 1) {
            onLoad((String) args[0]);
            return null;
        }
        if ("getRefMapperConfig".equals(name) && paramCount == 0) {
            return getRefMapperConfig();
        }
        if ("shouldApplyMixin".equals(name) && paramCount == 2) {
            return shouldApplyMixin((String) args[0], (String) args[1]);
        }
        if ("acceptTargets".equals(name) && paramCount == 2) {
            acceptTargets((Set<String>) args[0], (Set<String>) args[1]);
            return null;
        }
        if ("getMixins".equals(name) && paramCount == 0) {
            return getMixins();
        }
        if ("preApply".equals(name) && paramCount == 4) {
            preApply((String) args[0], (ClassNode) args[1], (String) args[2], args[3]);
            return null;
        }
        if ("postApply".equals(name) && paramCount == 4) {
            postApply((String) args[0], (ClassNode) args[1], (String) args[2], args[3]);
            return null;
        }
        if ("toString".equals(name)) {
            return "ContinuumMixinPluginAdapter@" + Integer.toHexString(hashCode());
        }
        if ("hashCode".equals(name)) {
            return hashCode();
        }
        if ("equals".equals(name) && paramCount == 1) {
            return proxy == args[0];
        }

        return null;
    }

    // =========================================================================
    // 2. SpongePowered IMixinConfigPlugin Implementation
    // =========================================================================

    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;
        LOGGER.info("[ContinuumMixinPlugin] Initialized mixin package: " + mixinPackage);
        if (!initialized) {
            initRules();
        }
    }

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        remapTargetSet(myTargets);
        remapTargetSet(otherTargets);
    }

    private void remapTargetSet(Set<String> targets) {
        if (targets == null || targets.isEmpty() || classRedirects.isEmpty()) return;

        List<String> toAdd = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        for (String target : targets) {
            String slashName = target.replace('.', '/');
            if (classRedirects.containsKey(slashName)) {
                toRemove.add(target);
                toAdd.add(classRedirects.get(slashName).replace('/', '.'));
            }
        }

        targets.removeAll(toRemove);
        targets.addAll(toAdd);
    }

    public List<String> getMixins() {
        return Collections.emptyList();
    }

    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, Object mixinInfo) {
        LOGGER.fine("[ContinuumMixinPlugin] preApply for " + mixinClassName + " -> " + targetClassName);

        // 1. Remap the mixin class itself if accessible from mixinInfo
        if (mixinInfo != null) {
            try {
                Method getClassNodeMethod = mixinInfo.getClass().getMethod("getClassNode", int.class);
                ClassNode mixinNode = (ClassNode) getClassNodeMethod.invoke(mixinInfo, 0);
                if (mixinNode != null) {
                    remapMixinClassNode(mixinNode);
                }
            } catch (Throwable ignored) {}
        }

        // 2. Transform the target class before mixin merge if needed
        if (targetClass != null) {
            transformer.transformClassNode(targetClass);
        }
    }

    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, Object mixinInfo) {
        LOGGER.fine("[ContinuumMixinPlugin] postApply for " + mixinClassName + " -> " + targetClassName);

        // After mixin merge, newly injected instructions from the mixin must be transformed
        // to redirect any legacy OpenGL 2.1 / Tessellator calls to LegacyRenderShim.
        if (targetClass != null) {
            transformer.transformClassNode(targetClass);
        }
    }

    // =========================================================================
    // 3. Mixin Bytecode & Annotation Remapping
    // =========================================================================

    /**
     * Remaps annotations and bytecode inside a Mixin class node before Mixin descriptor verification.
     */
    public boolean remapMixinClassNode(ClassNode mixinNode) {
        if (mixinNode == null) return false;
        boolean modified = false;

        // 1. Remap @Mixin annotations on class
        modified |= remapMixinAnnotations(mixinNode.visibleAnnotations);
        modified |= remapMixinAnnotations(mixinNode.invisibleAnnotations);

        // 2. Remap injection annotations on all methods (@Inject, @Redirect, @ModifyArg, etc.)
        if (mixinNode.methods != null) {
            for (MethodNode method : mixinNode.methods) {
                modified |= remapMethodAnnotations(method.visibleAnnotations);
                modified |= remapMethodAnnotations(method.invisibleAnnotations);
            }
        }

        // 3. Remap method bytecode and instructions inside the mixin class
        modified |= transformer.transformClassNode(mixinNode);

        return modified;
    }

    private boolean remapMixinAnnotations(List<AnnotationNode> annotations) {
        if (annotations == null || annotations.isEmpty()) return false;
        boolean modified = false;

        for (AnnotationNode an : annotations) {
            if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(an.desc)) {
                if (an.values != null) {
                    for (int i = 0; i < an.values.size(); i += 2) {
                        String key = (String) an.values.get(i);
                        Object val = an.values.get(i + 1);

                        if ("value".equals(key) && val instanceof List<?> list) {
                            List<Object> remappedList = new ArrayList<>();
                            for (Object item : list) {
                                if (item instanceof Type type && type.getSort() == Type.OBJECT) {
                                    String remapped = classRedirects.get(type.getInternalName());
                                    if (remapped != null) {
                                        remappedList.add(Type.getObjectType(remapped));
                                        modified = true;
                                    } else {
                                        remappedList.add(item);
                                    }
                                } else if (item instanceof String str) {
                                    String slash = str.replace('.', '/');
                                    String remapped = classRedirects.get(slash);
                                    if (remapped != null) {
                                        String result = str.contains("/") ? remapped.replace('.', '/') : remapped.replace('/', '.');
                                        remappedList.add(result);
                                        modified = true;
                                    } else {
                                        remappedList.add(item);
                                    }
                                } else {
                                    remappedList.add(item);
                                }
                            }
                            an.values.set(i + 1, remappedList);
                        } else if ("targets".equals(key) && val instanceof List<?> list) {
                            List<Object> remappedList = new ArrayList<>();
                            for (Object item : list) {
                                if (item instanceof String str) {
                                    String slash = str.replace('.', '/');
                                    String remapped = classRedirects.get(slash);
                                    if (remapped != null) {
                                        String result = str.contains("/") ? remapped.replace('.', '/') : remapped.replace('/', '.');
                                        remappedList.add(result);
                                        modified = true;
                                    } else {
                                        remappedList.add(item);
                                    }
                                } else if (item instanceof Type type && type.getSort() == Type.OBJECT) {
                                    String remapped = classRedirects.get(type.getInternalName());
                                    if (remapped != null) {
                                        remappedList.add(Type.getObjectType(remapped));
                                        modified = true;
                                    } else {
                                        remappedList.add(item);
                                    }
                                } else {
                                    remappedList.add(item);
                                }
                            }
                            an.values.set(i + 1, remappedList);
                        }
                    }
                }
            }
        }

        return modified;
    }

    private boolean remapMethodAnnotations(List<AnnotationNode> annotations) {
        if (annotations == null || annotations.isEmpty()) return false;
        boolean modified = false;

        for (AnnotationNode an : annotations) {
            if (an.values == null) continue;

            for (int i = 0; i < an.values.size(); i += 2) {
                String key = (String) an.values.get(i);
                Object val = an.values.get(i + 1);

                if ("method".equals(key)) {
                    if (val instanceof String str) {
                        String remapped = remapMethodTarget(str);
                        if (!remapped.equals(str)) {
                            an.values.set(i + 1, remapped);
                            modified = true;
                        }
                    } else if (val instanceof List<?> list) {
                        List<Object> remappedList = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof String str) {
                                String remapped = remapMethodTarget(str);
                                remappedList.add(remapped);
                                if (!remapped.equals(str)) modified = true;
                            } else {
                                remappedList.add(item);
                            }
                        }
                        an.values.set(i + 1, remappedList);
                    }
                } else if ("target".equals(key)) {
                    if (val instanceof String str) {
                        String remapped = remapMethodTarget(str);
                        if (!remapped.equals(str)) {
                            an.values.set(i + 1, remapped);
                            modified = true;
                        }
                    }
                }
            }
        }

        return modified;
    }

    /**
     * Remaps a mixin method target specification (e.g. "render(Lnet/minecraft/world/World;)V").
     */
    public String remapMethodTarget(String target) {
        if (target == null || target.isEmpty()) return target;

        int parenOpen = target.indexOf('(');
        if (parenOpen >= 0) {
            String prefix = target.substring(0, parenOpen);
            String desc = target.substring(parenOpen);

            // Remap descriptor types
            String remappedDesc = transformer.remapDescriptorString(desc);

            // Check if method name needs redirection
            String remappedName = prefix;
            for (MethodRedirectRule mr : methodRedirects) {
                if (prefix.endsWith(mr.getSourceName())) {
                    if (mr.getSourceDesc() == null || mr.getSourceDesc().equals(desc)) {
                        remappedName = prefix.substring(0, prefix.length() - mr.getSourceName().length()) + mr.getTargetName();
                        break;
                    }
                }
            }

            // Version-dependent legacy method renames
            if (targetSpec != null && !targetSpec.getVersion().isAtLeast(com.kyroxova.bootstrapper.environment.MCVersion.of("1.17"))) {
                if (remappedName.equals("getBlockEntity")) {
                    remappedName = "getTileEntity";
                }
            }

            return remappedName + remappedDesc;
        }

        // Target without descriptor: check name mapping
        for (MethodRedirectRule mr : methodRedirects) {
            if (target.endsWith(mr.getSourceName())) {
                return target.substring(0, target.length() - mr.getSourceName().length()) + mr.getTargetName();
            }
        }

        if (targetSpec != null && !targetSpec.getVersion().isAtLeast(com.kyroxova.bootstrapper.environment.MCVersion.of("1.17"))) {
            if (target.equals("getBlockEntity")) {
                return "getTileEntity";
            }
        }

        return target;
    }

    public ApiKnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    public TargetSpec getBaseSpec() {
        return baseSpec;
    }

    public TargetSpec getTargetSpec() {
        return targetSpec;
    }

    public ContinuumBytecodeTransformer getTransformer() {
        return transformer;
    }
}
