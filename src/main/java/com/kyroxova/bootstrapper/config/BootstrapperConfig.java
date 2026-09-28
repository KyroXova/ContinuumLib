package com.kyroxova.bootstrapper.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Manages loading and validation of ContinuumLib configuration.
 * Reads:
 * 1. data/continuumlib/config.json
 * 2. data/continuumlib/version/ContinuumLib.json
 * 3. data/continuumlib/loader/ContinuumLib.json
 */
public final class BootstrapperConfig {

    private static final Logger LOGGER = Logger.getLogger(BootstrapperConfig.class.getName());

    public static final String DEFAULT_CONFIG_PATH = "data/continuumlib/config.json";
    public static final String DEFAULT_VERSION_PATH = "data/continuumlib/version/ContinuumLib.json";
    public static final String DEFAULT_LOADER_PATH = "data/continuumlib/loader/ContinuumLib.json";

    private final String modId;
    private final String mode; // hybrid, runtime, build
    private final TargetSpec baseSpec;
    private final List<TargetSpec> targetSpecs;
    private final String jarNamingFormat;
    private final String destinationPath;

    public BootstrapperConfig(String modId, String mode, TargetSpec baseSpec, List<TargetSpec> targetSpecs,
                              String jarNamingFormat, String destinationPath) {
        this.modId = Objects.requireNonNull(modId, "modId cannot be null");
        this.mode = mode == null ? "hybrid" : mode.toLowerCase();
        this.baseSpec = Objects.requireNonNull(baseSpec, "baseSpec cannot be null");
        this.targetSpecs = Collections.unmodifiableList(new ArrayList<>(targetSpecs));
        this.jarNamingFormat = jarNamingFormat == null ? "%modid%-%loader%-%version%.jar" : jarNamingFormat;
        this.destinationPath = destinationPath == null ? "build/libs/%loader%/" : destinationPath;
    }

    /**
     * Loads the configuration from the active ClassLoader resources or returns safe defaults.
     */
    public static BootstrapperConfig loadFromClasspath(ClassLoader classLoader) {
        ClassLoader cl = classLoader != null ? classLoader : BootstrapperConfig.class.getClassLoader();
        InputStream configStream = cl.getResourceAsStream(DEFAULT_CONFIG_PATH);

        if (configStream == null) {
            LOGGER.warning("[ContinuumConfig] " + DEFAULT_CONFIG_PATH + " not found on classpath. Using fallback defaults (1.18.2 Forge).");
            return createDefaultFallback();
        }

        try (InputStreamReader reader = new InputStreamReader(configStream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            String modId = root.has("modid") ? root.get("modid").getAsString() : "examplemod";
            String mode = root.has("mode") ? root.get("mode").getAsString() : "hybrid";

            // Parse Base
            JsonObject baseObj = root.has("base") ? root.getAsJsonObject("base") : new JsonObject();
            String baseVersion = baseObj.has("version") ? baseObj.get("version").getAsString() : "1.18.2";
            String baseLoader = baseObj.has("loader") ? baseObj.get("loader").getAsString() : "forge";
            String baseMappings = baseObj.has("mappings") ? baseObj.get("mappings").getAsString() : "mojmap";
            int baseJava = baseObj.has("java_target") ? baseObj.get("java_target").getAsInt() : 17;
            TargetSpec baseSpec = new TargetSpec(MCVersion.of(baseVersion), LoaderType.fromString(baseLoader), baseMappings, baseJava);

            // Parse Output
            String namingFormat = "%modid%-%loader%-%version%.jar";
            String destPath = "build/libs/%loader%/";
            if (root.has("output")) {
                JsonObject outObj = root.getAsJsonObject("output");
                if (outObj.has("jar_naming_format")) namingFormat = outObj.get("jar_naming_format").getAsString();
                if (outObj.has("destination_path")) destPath = outObj.get("destination_path").getAsString();
            }

            // Parse Targets
            List<String> targetVersions = loadVersionsMatrix(cl, root);
            List<String> targetLoaders = loadLoadersMatrix(cl, root);

            List<TargetSpec> targets = new ArrayList<>();
            for (String v : targetVersions) {
                for (String l : targetLoaders) {
                    targets.add(TargetSpec.of(v, l));
                }
            }

            // Custom specific targets
            if (root.has("targets") && root.getAsJsonObject("targets").has("custom_targets")) {
                JsonArray customArr = root.getAsJsonObject("targets").getAsJsonArray("custom_targets");
                for (JsonElement el : customArr) {
                    if (el.isJsonObject()) {
                        JsonObject cObj = el.getAsJsonObject();
                        String cv = cObj.get("version").getAsString();
                        String clName = cObj.get("loader").getAsString();
                        String cm = cObj.has("mappings") ? cObj.get("mappings").getAsString() : "mojmap";
                        int cj = cObj.has("java_target") ? cObj.get("java_target").getAsInt() : 0;
                        targets.add(new TargetSpec(MCVersion.of(cv), LoaderType.fromString(clName), cm, cj));
                    }
                }
            }

            return new BootstrapperConfig(modId, mode, baseSpec, targets, namingFormat, destPath);
        } catch (Exception e) {
            LOGGER.severe("[ContinuumConfig] Failed to parse " + DEFAULT_CONFIG_PATH + ": " + e.getMessage());
            return createDefaultFallback();
        }
    }

    private static List<String> loadVersionsMatrix(ClassLoader cl, JsonObject root) {
        String path = DEFAULT_VERSION_PATH;
        if (root.has("targets") && root.getAsJsonObject("targets").has("versions_file")) {
            path = root.getAsJsonObject("targets").get("versions_file").getAsString();
        }

        try (InputStream stream = cl.getResourceAsStream(path)) {
            if (stream != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("supported_versions")) {
                    List<String> result = new ArrayList<>();
                    for (JsonElement elem : obj.getAsJsonArray("supported_versions")) {
                        result.add(elem.getAsString());
                    }
                    return result;
                }
            }
        } catch (Exception ignored) {}
        return List.of("1.18.2", "1.19.2", "1.20.1", "1.20.4");
    }

    private static List<String> loadLoadersMatrix(ClassLoader cl, JsonObject root) {
        String path = DEFAULT_LOADER_PATH;
        if (root.has("targets") && root.getAsJsonObject("targets").has("loaders_file")) {
            path = root.getAsJsonObject("targets").get("loaders_file").getAsString();
        }

        try (InputStream stream = cl.getResourceAsStream(path)) {
            if (stream != null) {
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("supported_loaders")) {
                    List<String> result = new ArrayList<>();
                    for (JsonElement elem : obj.getAsJsonArray("supported_loaders")) {
                        result.add(elem.getAsString());
                    }
                    return result;
                }
            }
        } catch (Exception ignored) {}
        return List.of("forge", "neoforge", "fabric");
    }

    public static BootstrapperConfig createDefaultFallback() {
        TargetSpec base = new TargetSpec(MCVersion.V1_18_2, LoaderType.FORGE, "mojmap", 17);
        List<TargetSpec> targets = List.of(
                TargetSpec.of("1.18.2", "forge"),
                TargetSpec.of("1.19.2", "forge"),
                TargetSpec.of("1.20.1", "forge"),
                TargetSpec.of("1.20.4", "neoforge"),
                TargetSpec.of("1.20.1", "fabric")
        );
        return new BootstrapperConfig("modid", "hybrid", base, targets, "%modid%-%loader%-%version%.jar", "build/libs/%loader%/");
    }

    public String getModId() {
        return modId;
    }

    public String getMode() {
        return mode;
    }

    public TargetSpec getBaseSpec() {
        return baseSpec;
    }

    public List<TargetSpec> getTargetSpecs() {
        return targetSpecs;
    }

    public String getJarNamingFormat() {
        return jarNamingFormat;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public String formatJarName(String version, String loader) {
        return jarNamingFormat
                .replace("%modid%", modId)
                .replace("%loader%", loader.toLowerCase())
                .replace("%version%", version);
    }

    public String formatDestinationDir(String loader) {
        return destinationPath.replace("%loader%", loader.toLowerCase());
    }
}
