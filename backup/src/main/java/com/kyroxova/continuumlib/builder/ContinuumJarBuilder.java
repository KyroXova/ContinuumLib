package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.MCVersion;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;

import com.google.gson.*;

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
            "AdvancementShim", "AttributeModifierShim", "BlockEntityShim", "BlockInteractionShim", "BlockPropertiesShim", "ButtonShim",
            "CapabilityShim", "ClientRendererShim", "ColorHandlerShim", "CommandShim", "ComponentShim", "CreativeTabShim", "DamageSourceShim", "DataComponentShim", "EnchantmentShim",
            "EntityDataShim", "ExplosionShim", "FluidShim", "GuiComponentShim", "ItemInteractionShim", "ItemStackShim", "KeyMappingShim", "LegacyGameRegistryShim", "LegacyMetadataShim", "LivingEntityShim", "MathShim", "MenuTypeShim",
            "MobEffectShim", "NetworkShim", "ParticleShim", "RandomShim", "RecipeShim", "RecordItemShim", "ReflectionHelperShim",
            "RegistryShim", "RenderTypeShim", "ResourceLocationJsonAdapter", "ResourceLocationShim", "SavedDataShim",
            "ScreenRenderingShim", "SoundEventShim", "SoundPlaybackShim", "SyntheticEventDispatcher", "TagShim", "VoxelShapeShim", "WorldShim"
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

    private static final Set<String> ALL_LOADER_MANIFEST_PATHS = Set.of(
            "META-INF/mods.toml",
            "META-INF/neoforge.mods.toml",
            "fabric.mod.json",
            "quilt.mod.json",
            "plugin.yml",
            "mcmod.info"
    );

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

        boolean hasMixins = inputFiles.keySet().stream().anyMatch(k -> k.contains("/mixin/") && k.endsWith(".class"));

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
            Set<String> writtenEntries = new HashSet<>();
            writtenEntries.add("META-INF/MANIFEST.MF");

            // 1. Process and write input files
            for (Map.Entry<String, byte[]> entry : inputFiles.entrySet()) {
                String entryName = entry.getKey();
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entryName)) {
                    continue;
                }
                if (ALL_LOADER_MANIFEST_PATHS.contains(entryName) ||
                        ALL_LOADER_MANIFEST_PATHS.contains(entryName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (entryName.startsWith("net/minecraft/") ||
                        entryName.startsWith("net/minecraftforge/") ||
                        entryName.startsWith("net/neoforged/")) {
                    continue;
                }
                if (!hasMixins) {
                    if (entryName.endsWith(".mixins.json") ||
                            entryName.equals("assets/minecraft/blockstates/composter.json") ||
                            entryName.equals("data/minecraft/loot_tables/blocks/composter.json") ||
                            entryName.equals("data/minecraft/recipes/snow.json") ||
                            entryName.equals("data/minecraft/recipe/snow.json")) {
                        continue;
                    }
                }
                byte[] content = entry.getValue();

                // Normalize plural vs singular datapack paths
                entryName = DataPackResourcePathNormalizer.normalizePath(entryName, target.getVersion());

                // Normalize atlas sprite paths for 1.19.3+ (strip leading textures/ in resource definitions)
                if (target.getVersion().isAtLeast(MCVersion.of("1.19.3")) &&
                        entryName.startsWith("assets/") && entryName.contains("/atlases/") && entryName.endsWith(".json")) {
                    String jsonStr = new String(content, StandardCharsets.UTF_8);
                    if (jsonStr.contains(":textures/")) {
                        content = jsonStr.replace(":textures/", ":").getBytes(StandardCharsets.UTF_8);
                    }
                }

                // Normalize tag entries: mark values as optional (required: false) to prevent TagLoader
                // from crashing vanilla or mod tags when external/backported mod blocks are missing
                if (entryName.startsWith("data/") && entryName.contains("/tags/") && entryName.endsWith(".json")) {
                    content = normalizeTagJson(content);
                }

                // Normalize worldgen features and block predicates for modern targets
                if (entryName.startsWith("data/") && entryName.contains("/worldgen/") && entryName.endsWith(".json")) {
                    content = normalizeWorldGenJson(content, target.getVersion());
                }

                // Normalize recipe definitions for modern targets (1.20.5+ result format, 1.21.2+ ingredients)
                if (entryName.startsWith("data/") && (entryName.contains("/recipes/") || entryName.contains("/recipe/")) && entryName.endsWith(".json")) {
                    content = normalizeRecipeJson(content, target.getVersion());
                }

                // Normalize advancement definitions for modern targets (1.20.5+ icon and reward format)
                if (entryName.startsWith("data/") && (entryName.contains("/advancements/") || entryName.contains("/advancement/")) && entryName.endsWith(".json")) {
                    content = normalizeAdvancementJson(content, target.getVersion());
                }

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

        boolean hasMixins = inputFiles.keySet().stream().anyMatch(k -> k.contains("/mixin/") && k.endsWith(".class"));

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
            Set<String> writtenEntries = new HashSet<>();
            writtenEntries.add("META-INF/MANIFEST.MF");

            // 1. Write original base input files (untransformed mod bytecode & resources)
            for (Map.Entry<String, byte[]> entry : inputFiles.entrySet()) {
                String entryName = entry.getKey();
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entryName)) {
                    continue;
                }
                if (ALL_LOADER_MANIFEST_PATHS.contains(entryName) ||
                        ALL_LOADER_MANIFEST_PATHS.contains(entryName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (entryName.startsWith("net/minecraft/") ||
                        entryName.startsWith("net/minecraftforge/") ||
                        entryName.startsWith("net/neoforged/")) {
                    continue;
                }
                if (!hasMixins) {
                    if (entryName.endsWith(".mixins.json") ||
                            entryName.equals("assets/minecraft/blockstates/composter.json") ||
                            entryName.equals("data/minecraft/loot_tables/blocks/composter.json") ||
                            entryName.equals("data/minecraft/recipes/snow.json") ||
                            entryName.equals("data/minecraft/recipe/snow.json")) {
                        continue;
                    }
                }
                byte[] content = entry.getValue();
                if (entryName.startsWith("data/") && entryName.contains("/tags/") && entryName.endsWith(".json")) {
                    content = normalizeTagJson(content);
                }
                writeEntry(jos, entryName, content, writtenEntries);
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

            // 4. Ensure continuumlib configuration is present in JAR
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

    private static byte[] normalizeTagJson(byte[] content) {
        try {
            String jsonStr = new String(content, StandardCharsets.UTF_8);
            JsonElement rootElement = JsonParser.parseString(jsonStr);
            if (!rootElement.isJsonObject()) {
                return content;
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has("values") || !root.get("values").isJsonArray()) {
                return content;
            }
            JsonArray originalValues = root.getAsJsonArray("values");
            JsonArray newValues = new JsonArray();
            for (JsonElement elem : originalValues) {
                if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                    JsonObject entryObj = new JsonObject();
                    entryObj.addProperty("id", elem.getAsString());
                    entryObj.addProperty("required", false);
                    newValues.add(entryObj);
                } else if (elem.isJsonObject()) {
                    JsonObject obj = elem.getAsJsonObject();
                    if (!obj.has("required")) {
                        obj.addProperty("required", false);
                    }
                    newValues.add(obj);
                } else {
                    newValues.add(elem);
                }
            }
            root.add("values", newValues);
            return root.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return content;
        }
    }

    private static byte[] normalizeWorldGenJson(byte[] content, MCVersion targetVersion) {
        try {
            String jsonStr = new String(content, StandardCharsets.UTF_8);
            if (targetVersion.isAtLeast(MCVersion.of("1.19"))) {
                jsonStr = jsonStr.replace("\"buildscape:mangrove\"", "\"minecraft:mangrove\"")
                                 .replace("\"buildscape:tall_mangrove\"", "\"minecraft:tall_mangrove\"")
                                 .replace("\"buildscape:mangrove_propagule\"", "\"minecraft:mangrove_propagule\"");
            }
            if (targetVersion.isAtLeast(MCVersion.of("1.20.5"))) {
                jsonStr = jsonStr.replaceAll("\"Name\"\\s*:", "\"id\":")
                                 .replaceAll("\"Properties\"\\s*:", "\"properties\":");
            }
            return jsonStr.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return content;
        }
    }

    private static byte[] normalizeRecipeJson(byte[] content, MCVersion targetVersion) {
        if (!targetVersion.isAtLeast(MCVersion.of("1.20.5")) && targetVersion.getMajor() < 26) {
            return content;
        }
        try {
            String jsonStr = new String(content, StandardCharsets.UTF_8);
            JsonElement rootElement = JsonParser.parseString(jsonStr);
            if (!rootElement.isJsonObject()) {
                return content;
            }
            JsonObject root = rootElement.getAsJsonObject();

            // 1. Result normalization (1.20.5+ / 26.x): "result": { "item": "..." } -> "result": { "id": "..." }
            if (root.has("result")) {
                JsonElement resElem = root.get("result");
                if (resElem.isJsonObject()) {
                    JsonObject resObj = resElem.getAsJsonObject();
                    if (resObj.has("item")) {
                        String itemId = resObj.get("item").getAsString();
                        resObj.remove("item");
                        resObj.addProperty("id", itemId);
                    }
                }
            }

            // 2. Ingredient normalization for 1.21.2+ / 26.x:
            // Ingredient codec accepts String ("item" or "#tag") or Array of Strings, not { "item": ... }
            if (targetVersion.isAtLeast(MCVersion.of("1.21.2")) || targetVersion.getMajor() >= 26) {
                if (root.has("ingredients") && root.get("ingredients").isJsonArray()) {
                    JsonArray origIngs = root.getAsJsonArray("ingredients");
                    JsonArray newIngs = new JsonArray();
                    for (JsonElement ing : origIngs) {
                        newIngs.add(normalizeIngredientElement(ing));
                    }
                    root.add("ingredients", newIngs);
                }
                if (root.has("key") && root.get("key").isJsonObject()) {
                    JsonObject keyObj = root.getAsJsonObject("key");
                    JsonObject newKeyObj = new JsonObject();
                    for (Map.Entry<String, JsonElement> kEntry : keyObj.entrySet()) {
                        newKeyObj.add(kEntry.getKey(), normalizeIngredientElement(kEntry.getValue()));
                    }
                    root.add("key", newKeyObj);
                }
            }

            return root.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return content;
        }
    }

    private static JsonElement normalizeIngredientElement(JsonElement elem) {
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("item")) {
                return new JsonPrimitive(obj.get("item").getAsString());
            } else if (obj.has("tag")) {
                return new JsonPrimitive("#" + obj.get("tag").getAsString());
            }
            return obj;
        } else if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            JsonArray newArr = new JsonArray();
            for (JsonElement child : arr) {
                newArr.add(normalizeIngredientElement(child));
            }
            return newArr;
        }
        return elem;
    }

    private static byte[] normalizeAdvancementJson(byte[] content, MCVersion targetVersion) {
        if (!targetVersion.isAtLeast(MCVersion.of("1.20.5")) && targetVersion.getMajor() < 26) {
            return content;
        }
        try {
            String jsonStr = new String(content, StandardCharsets.UTF_8);
            JsonElement rootElement = JsonParser.parseString(jsonStr);
            if (!rootElement.isJsonObject()) {
                return content;
            }
            JsonObject root = rootElement.getAsJsonObject();

            // 1. Icon normalization: "display": { "icon": { "item": "..." } } -> "icon": { "id": "..." }
            if (root.has("display") && root.get("display").isJsonObject()) {
                JsonObject display = root.getAsJsonObject("display");
                if (display.has("icon") && display.get("icon").isJsonObject()) {
                    JsonObject icon = display.getAsJsonObject("icon");
                    if (icon.has("item")) {
                        String itemId = icon.get("item").getAsString();
                        icon.remove("item");
                        icon.addProperty("id", itemId);
                    }
                }
            }

            // 2. Rewards normalization: "rewards": { "items": [ { "item": "..." } ] } -> { "id": "..." }
            if (root.has("rewards") && root.get("rewards").isJsonObject()) {
                JsonObject rewards = root.getAsJsonObject("rewards");
                if (rewards.has("items") && rewards.get("items").isJsonArray()) {
                    JsonArray itemsArr = rewards.getAsJsonArray("items");
                    for (JsonElement itemElem : itemsArr) {
                        if (itemElem.isJsonObject()) {
                            JsonObject itemObj = itemElem.getAsJsonObject();
                            if (itemObj.has("item")) {
                                String itemId = itemObj.get("item").getAsString();
                                itemObj.remove("item");
                                itemObj.addProperty("id", itemId);
                            }
                        }
                    }
                }
            }

            return root.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return content;
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
