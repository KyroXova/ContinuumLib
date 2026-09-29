package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Automates the packaging and generation of multi-version / multi-loader JAR files.
 * Supports:
 * 1. Cleanly separated, loader-specific JARs: build/libs/%loader%/%modid%-%loader%-%version%.jar
 * 2. Universal in-memory multi-loader bootstrap bundles: build/libs/universal/%modid%-universal-%version%.jar
 */
public final class ContinuumJarBuilder {

    private static final Logger LOGGER = Logger.getLogger(ContinuumJarBuilder.class.getName());

    private static final String[] SHIM_CLASS_NAMES = {
            "AttributeModifierShim", "BlockInteractionShim", "BlockPropertiesShim", "ButtonShim",
            "CapabilityShim", "ComponentShim", "CreativeTabShim", "DamageSourceShim", "EnchantmentShim",
            "FluidShim", "GuiComponentShim", "ItemStackShim", "KeyMappingShim", "MathShim", "MenuTypeShim",
            "NetworkShim", "ParticleShim", "RandomShim", "RecipeShim", "RecordItemShim", "ReflectionHelperShim",
            "RegistryShim", "RenderTypeShim", "ResourceLocationJsonAdapter", "ResourceLocationShim",
            "ScreenRenderingShim", "SoundEventShim", "SyntheticEventDispatcher", "TagShim", "VoxelShapeShim", "WorldShim"
    };

    private static final String[] BOOTSTRAPPER_CORE_CLASSES = {
            "com/kyroxova/bootstrapper/ContinuumBootstrapper.class",
            "com/kyroxova/bootstrapper/config/BootstrapperConfig.class",
            "com/kyroxova/bootstrapper/config/TargetSpec.class",
            "com/kyroxova/bootstrapper/environment/EnvironmentDetector.class",
            "com/kyroxova/bootstrapper/environment/EnvironmentDetector$EnvironmentInfo.class",
            "com/kyroxova/bootstrapper/environment/LoaderType.class",
            "com/kyroxova/bootstrapper/environment/MCVersion.class",
            "com/kyroxova/bootstrapper/hooks/ContinuumTransformerHook.class",
            "com/kyroxova/bootstrapper/hooks/FabricPreLaunchHook.class",
            "com/kyroxova/bootstrapper/hooks/ModLauncherPluginHook.class",
            "com/kyroxova/bootstrapper/hooks/LegacyCoreModHook.class"
    };

    private final BootstrapperConfig config;
    private final ApiKnowledgeBase knowledgeBase;
    private boolean embedShims = true;

    public ContinuumJarBuilder(BootstrapperConfig config, ApiKnowledgeBase knowledgeBase) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
    }

    public boolean isEmbedShims() {
        return embedShims;
    }

    public ContinuumJarBuilder setEmbedShims(boolean embedShims) {
        this.embedShims = embedShims;
        return this;
    }

    /**
     * Builds a specific target JAR from an existing JAR file.
     */
    public File buildTargetJar(TargetSpec target, File inputJarFile, File outputDirectoryRoot) throws IOException {
        Map<String, byte[]> inputFiles = readJarEntries(inputJarFile);
        return buildTargetJar(target, inputFiles, outputDirectoryRoot);
    }

    /**
     * Builds a specific target JAR from compiled input classes and resources.
     * Output path format: build/libs/%loader%/%modid%-%loader%-%version%.jar
     *
     * @param target the target specification (e.g. 1.20.4 NeoForge)
     * @param inputFiles map of entry name -> raw byte content (compiled classes & resources)
     * @param outputDirectoryRoot base output directory (e.g. "build/libs")
     * @return File handle to the generated JAR
     */
    public File buildTargetJar(TargetSpec target, Map<String, byte[]> inputFiles, File outputDirectoryRoot) throws IOException {
        File targetDir = new File(outputDirectoryRoot, target.getLoader().getId());
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create target directory: " + targetDir.getAbsolutePath());
        }

        String jarName = config.formatJarName(target.getVersion().getRaw(), target.getLoader().getId());
        File outputJar = new File(targetDir, jarName);

        LOGGER.info(String.format("[ContinuumJarBuilder] Building %s for Target: %s", jarName, target));

        ContinuumBytecodeTransformer transformer = new ContinuumBytecodeTransformer(knowledgeBase, config.getBaseSpec(), target);

        Manifest manifest = createStandardManifest(target.getVersion().getRaw());

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
            Set<String> writtenEntries = new HashSet<>();
            writtenEntries.add("META-INF/MANIFEST.MF");

            // 1. Process and write input files
            for (Map.Entry<String, byte[]> entry : inputFiles.entrySet()) {
                String entryName = entry.getKey();
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entryName)) {
                    continue;
                }
                byte[] content = entry.getValue();

                // Normalize plural vs singular datapack paths
                entryName = DataPackResourcePathNormalizer.normalizePath(entryName, target.getVersion());

                if (entryName.endsWith(".class")) {
                    String className = entryName.substring(0, entryName.length() - 6);
                    content = transformer.transform(className, content);
                }

                writeEntry(jos, entryName, content, writtenEntries);
            }

            // 2. Synthesize loader manifest if not present
            String manifestPath = ManifestGenerator.getManifestPath(target);
            if (!writtenEntries.contains(manifestPath)) {
                String manifestContent = ManifestGenerator.generateManifest(config, target);
                writeEntry(jos, manifestPath, manifestContent.getBytes(StandardCharsets.UTF_8), writtenEntries);
            }

            // 3. Embed polyfill shims for standalone execution
            if (embedShims) {
                Map<String, byte[]> shims = loadRuntimeShims();
                for (Map.Entry<String, byte[]> shim : shims.entrySet()) {
                    writeEntry(jos, shim.getKey(), shim.getValue(), writtenEntries);
                }
            }
        }

        LOGGER.info(String.format("[ContinuumJarBuilder] Successfully generated %s (%d bytes)",
                outputJar.getAbsolutePath(), outputJar.length()));
        return outputJar;
    }

    /**
     * Builds a Universal In-Memory Multi-Loader Bootstrap Bundle from an existing JAR file.
     * Output path format: build/libs/universal/%modid%-universal-%version%.jar
     */
    public File buildUniversalBootstrapBundle(File inputJarFile, File outputDirectoryRoot) throws IOException {
        Map<String, byte[]> inputFiles = readJarEntries(inputJarFile);
        return buildUniversalBootstrapBundle(inputFiles, outputDirectoryRoot);
    }

    /**
     * Builds a Universal In-Memory Multi-Loader Bootstrap Bundle.
     * Output path format: build/libs/universal/%modid%-universal-%version%.jar
     *
     * @param inputFiles map of entry name -> raw byte content (compiled base classes & resources)
     * @param outputDirectoryRoot base output directory (e.g. "build/libs")
     * @return File handle to the generated universal JAR
     */
    public File buildUniversalBootstrapBundle(Map<String, byte[]> inputFiles, File outputDirectoryRoot) throws IOException {
        File universalDir = new File(outputDirectoryRoot, "universal");
        if (!universalDir.exists() && !universalDir.mkdirs()) {
            throw new IOException("Failed to create universal directory: " + universalDir.getAbsolutePath());
        }

        MCVersion baseVersion = config.getBaseSpec().getVersion();
        String jarName = config.formatJarName(baseVersion.getRaw(), "universal");
        File outputJar = new File(universalDir, jarName);

        LOGGER.info(String.format("[ContinuumJarBuilder] Packaging Universal Bootstrap Bundle: %s", jarName));

        Manifest manifest = createUniversalManifest(baseVersion.getRaw());

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
            Set<String> writtenEntries = new HashSet<>();
            writtenEntries.add("META-INF/MANIFEST.MF");

            // 1. Write original base input files (untransformed mod bytecode & resources)
            for (Map.Entry<String, byte[]> entry : inputFiles.entrySet()) {
                String entryName = entry.getKey();
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entryName)) {
                    continue;
                }
                writeEntry(jos, entryName, entry.getValue(), writtenEntries);
            }

            // 2. Write multi-loader manifests and service descriptors
            Map<String, String> multiManifests = ManifestGenerator.generateUniversalManifests(config, baseVersion);
            for (Map.Entry<String, String> m : multiManifests.entrySet()) {
                writeEntry(jos, m.getKey(), m.getValue().getBytes(StandardCharsets.UTF_8), writtenEntries);
            }

            // 3. Write bootstrapper runtime engine (bootstrapper, transformer, knowledgebase, shims)
            Map<String, byte[]> runtime = loadUniversalBootstrapperRuntime();
            for (Map.Entry<String, byte[]> rt : runtime.entrySet()) {
                writeEntry(jos, rt.getKey(), rt.getValue(), writtenEntries);
            }

            // 4. Ensure continuum configuration is present in JAR
            ensureConfigResources(jos, writtenEntries);
        }

        LOGGER.info(String.format("[ContinuumJarBuilder] Successfully generated Universal Bundle %s (%d bytes)",
                outputJar.getAbsolutePath(), outputJar.length()));
        return outputJar;
    }

    /**
     * Builds all target JARs configured in BootstrapperConfig, plus universal bundle if requested.
     */
    public List<File> buildAll(File inputJarFile, File outputDirectoryRoot, boolean includeUniversal) throws IOException {
        Map<String, byte[]> inputFiles = readJarEntries(inputJarFile);
        return buildAll(inputFiles, outputDirectoryRoot, includeUniversal);
    }

    /**
     * Builds all target JARs configured in BootstrapperConfig, plus universal bundle if requested.
     */
    public List<File> buildAll(Map<String, byte[]> inputFiles, File outputDirectoryRoot, boolean includeUniversal) throws IOException {
        List<File> generated = new ArrayList<>();

        for (TargetSpec target : config.getTargetSpecs()) {
            File jar = buildTargetJar(target, inputFiles, outputDirectoryRoot);
            generated.add(jar);
        }

        if (includeUniversal) {
            File universalJar = buildUniversalBootstrapBundle(inputFiles, outputDirectoryRoot);
            generated.add(universalJar);
        }

        return generated;
    }

    private void writeEntry(JarOutputStream jos, String entryName, byte[] content, Set<String> writtenEntries) throws IOException {
        if (content == null || writtenEntries.contains(entryName)) {
            return;
        }
        writtenEntries.add(entryName);
        jos.putNextEntry(new JarEntry(entryName));
        jos.write(content);
        jos.closeEntry();
    }

    private Manifest createStandardManifest(String version) {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(new Attributes.Name("Implementation-Title"), config.getModId());
        attrs.put(new Attributes.Name("Implementation-Version"), version);
        attrs.put(new Attributes.Name("Created-By"), "ContinuumLib Multi-JAR Builder");
        return manifest;
    }

    private Manifest createUniversalManifest(String version) {
        Manifest manifest = createStandardManifest(version);
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(new Attributes.Name("FMLCorePlugin"), "com.kyroxova.bootstrapper.hooks.LegacyCoreModHook");
        attrs.put(new Attributes.Name("FMLCorePluginContainsFMLMod"), "true");
        attrs.put(new Attributes.Name("Main-Class"), "com.kyroxova.bootstrapper.ContinuumBootstrapper");
        return manifest;
    }

    private void ensureConfigResources(JarOutputStream jos, Set<String> writtenEntries) throws IOException {
        String[] configPaths = {
                BootstrapperConfig.DEFAULT_CONFIG_PATH,
                BootstrapperConfig.DEFAULT_VERSION_PATH,
                BootstrapperConfig.DEFAULT_LOADER_PATH
        };

        ClassLoader cl = getClass().getClassLoader();
        for (String path : configPaths) {
            if (!writtenEntries.contains(path)) {
                try (InputStream is = cl.getResourceAsStream(path)) {
                    if (is != null) {
                        writeEntry(jos, path, is.readAllBytes(), writtenEntries);
                    }
                }
            }
        }
    }

    public static Map<String, byte[]> readJarEntries(File jarFile) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jf = new JarFile(jarFile)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry entry = en.nextElement();
                if (entry.isDirectory()) continue;
                try (InputStream is = jf.getInputStream(entry)) {
                    entries.put(entry.getName(), is.readAllBytes());
                }
            }
        }
        return entries;
    }

    /**
     * Loads polyfill shim classes for embedding in target JARs.
     */
    public Map<String, byte[]> loadRuntimeShims() {
        Map<String, byte[]> shims = scanPackageFromCodeSource("com/kyroxova/continuumlib/shims/");

        // Fallback or supplementary loading via ClassLoader
        ClassLoader cl = getClass().getClassLoader();
        for (String name : SHIM_CLASS_NAMES) {
            String path = "com/kyroxova/continuumlib/shims/" + name + ".class";
            if (!shims.containsKey(path)) {
                byte[] bytes = loadResourceBytes(cl, path);
                if (bytes != null) {
                    shims.put(path, bytes);
                }
            }
        }

        // Also include normalizer and fundamental types if needed
        String normalizerPath = "com/kyroxova/continuumlib/builder/DataPackResourcePathNormalizer.class";
        if (!shims.containsKey(normalizerPath)) {
            byte[] bytes = loadResourceBytes(cl, normalizerPath);
            if (bytes != null) shims.put(normalizerPath, bytes);
        }

        return shims;
    }

    /**
     * Loads all bootstrapper, transformer, knowledgebase, and shim classes for the universal bundle.
     */
    public Map<String, byte[]> loadUniversalBootstrapperRuntime() {
        Map<String, byte[]> runtime = new HashMap<>();

        // 1. Scan from CodeSource
        runtime.putAll(scanPackageFromCodeSource("com/kyroxova/bootstrapper/"));
        runtime.putAll(scanPackageFromCodeSource("com/kyroxova/continuumlib/knowledgebase/"));
        runtime.putAll(scanPackageFromCodeSource("com/kyroxova/continuumlib/transformer/"));
        runtime.putAll(scanPackageFromCodeSource("com/kyroxova/continuumlib/shims/"));

        // 2. Ensure critical core bootstrapper classes via ClassLoader
        ClassLoader cl = getClass().getClassLoader();
        for (String path : BOOTSTRAPPER_CORE_CLASSES) {
            if (!runtime.containsKey(path)) {
                byte[] bytes = loadResourceBytes(cl, path);
                if (bytes != null) {
                    runtime.put(path, bytes);
                }
            }
        }

        for (String name : SHIM_CLASS_NAMES) {
            String path = "com/kyroxova/continuumlib/shims/" + name + ".class";
            if (!runtime.containsKey(path)) {
                byte[] bytes = loadResourceBytes(cl, path);
                if (bytes != null) runtime.put(path, bytes);
            }
        }

        String normalizerPath = "com/kyroxova/continuumlib/builder/DataPackResourcePathNormalizer.class";
        if (!runtime.containsKey(normalizerPath)) {
            byte[] bytes = loadResourceBytes(cl, normalizerPath);
            if (bytes != null) runtime.put(normalizerPath, bytes);
        }

        return runtime;
    }

    private static Map<String, byte[]> scanPackageFromCodeSource(String packagePrefix) {
        Map<String, byte[]> result = new HashMap<>();
        try {
            CodeSource cs = ContinuumJarBuilder.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                File loc = new File(cs.getLocation().toURI());
                if (loc.isDirectory()) {
                    scanDirRecursive(loc, loc, packagePrefix, result);
                } else if (loc.isFile() && loc.getName().endsWith(".jar")) {
                    try (JarFile jf = new JarFile(loc)) {
                        Enumeration<JarEntry> en = jf.entries();
                        while (en.hasMoreElements()) {
                            JarEntry entry = en.nextElement();
                            if (!entry.isDirectory() && entry.getName().startsWith(packagePrefix)) {
                                try (InputStream is = jf.getInputStream(entry)) {
                                    result.put(entry.getName(), is.readAllBytes());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.fine("[ContinuumJarBuilder] CodeSource scan notice: " + t.getMessage());
        }
        return result;
    }

    private static void scanDirRecursive(File root, File current, String prefix, Map<String, byte[]> out) {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirRecursive(root, f, prefix, out);
            } else if (f.isFile() && f.getName().endsWith(".class")) {
                String rel = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                if (rel.startsWith(prefix)) {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        out.put(rel, fis.readAllBytes());
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    private static byte[] loadResourceBytes(ClassLoader cl, String resourcePath) {
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException ignored) {}
        return null;
    }
}
