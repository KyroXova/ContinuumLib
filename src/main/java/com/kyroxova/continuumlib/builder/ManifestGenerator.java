package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Automatically synthesizes valid loader manifests for target builds and universal bundles.
 * Supports Forge (1.7.9 through 26.3+), NeoForge, Fabric, and Quilt.
 */
public final class ManifestGenerator {

    private ManifestGenerator() {}

    /**
     * Generates the primary manifest content for the given target specification.
     */
    public static String generateManifest(BootstrapperConfig config, TargetSpec target) {
        if (target.getLoader() == LoaderType.NEOFORGE) {
            return generateNeoForgeToml(config, target);
        } else if (target.getLoader() == LoaderType.QUILT) {
            return generateQuiltJson(config, target);
        } else if (target.getLoader() == LoaderType.FABRIC) {
            return generateFabricJson(config, target);
        } else {
            // Forge: Check if legacy pre-flattening (<= 1.12.2)
            if (target.getVersion().isBefore(MCVersion.V1_13)) {
                return generateMcModInfo(config, target);
            }
            return generateForgeToml(config, target);
        }
    }

    /**
     * Returns the relative JAR entry path for the target manifest.
     */
    public static String getManifestPath(TargetSpec target) {
        if (target.getLoader() == LoaderType.NEOFORGE) {
            return "META-INF/neoforge.mods.toml";
        } else if (target.getLoader() == LoaderType.QUILT) {
            return "quilt.mod.json";
        } else if (target.getLoader() == LoaderType.FABRIC) {
            return "fabric.mod.json";
        } else {
            if (target.getVersion().isBefore(MCVersion.V1_13)) {
                return "mcmod.info";
            }
            return "META-INF/mods.toml";
        }
    }

    /**
     * Generates all required multi-loader manifests and service descriptors for a Universal Bootstrap Bundle.
     */
    public static Map<String, String> generateUniversalManifests(BootstrapperConfig config, MCVersion version) {
        Map<String, String> manifests = new LinkedHashMap<>();
        TargetSpec forgeSpec = new TargetSpec(version, LoaderType.FORGE, "mojmap", 0);
        TargetSpec neoSpec = new TargetSpec(version, LoaderType.NEOFORGE, "mojmap", 0);
        TargetSpec fabricSpec = new TargetSpec(version, LoaderType.FABRIC, "mojmap", 0);
        TargetSpec quiltSpec = new TargetSpec(version, LoaderType.QUILT, "mojmap", 0);

        manifests.put("META-INF/mods.toml", generateForgeToml(config, forgeSpec));
        manifests.put("META-INF/neoforge.mods.toml", generateNeoForgeToml(config, neoSpec));
        manifests.put("fabric.mod.json", generateFabricUniversalJson(config, version));
        manifests.put("quilt.mod.json", generateQuiltUniversalJson(config, version));
        manifests.put("mcmod.info", generateMcModInfo(config, forgeSpec));
        manifests.put("META-INF/services/cpw.mods.modlauncher.serviceapi.ITransformationService",
                "com.kyroxova.bootstrapper.hooks.ModLauncherPluginHook\n");

        return manifests;
    }

    public static String generateForgeToml(BootstrapperConfig config, TargetSpec target) {
        return "modLoader=\"javafml\"\n" +
                "loaderVersion=\"[40,)\"\n" +
                "license=\"All Rights Reserved\"\n" +
                "[[mods]]\n" +
                "modId=\"" + config.getModId() + "\"\n" +
                "version=\"" + target.getVersion().getRaw() + "\"\n" +
                "displayName=\"" + config.getModId() + "\"\n";
    }

    public static String generateNeoForgeToml(BootstrapperConfig config, TargetSpec target) {
        return "modLoader=\"javafml\"\n" +
                "loaderVersion=\"[20.4,)\"\n" +
                "license=\"All Rights Reserved\"\n" +
                "[[mods]]\n" +
                "modId=\"" + config.getModId() + "\"\n" +
                "version=\"" + target.getVersion().getRaw() + "\"\n" +
                "displayName=\"" + config.getModId() + "\"\n";
    }

    public static String generateFabricJson(BootstrapperConfig config, TargetSpec target) {
        return "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"id\": \"" + config.getModId() + "\",\n" +
                "  \"version\": \"" + target.getVersion().getRaw() + "\",\n" +
                "  \"name\": \"" + config.getModId() + "\",\n" +
                "  \"environment\": \"*\",\n" +
                "  \"depends\": {\n" +
                "    \"fabricloader\": \">=0.14.0\",\n" +
                "    \"minecraft\": \"~" + target.getVersion().getRaw() + "\"\n" +
                "  }\n" +
                "}\n";
    }

    public static String generateFabricUniversalJson(BootstrapperConfig config, MCVersion version) {
        return "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"id\": \"" + config.getModId() + "\",\n" +
                "  \"version\": \"" + version.getRaw() + "\",\n" +
                "  \"name\": \"" + config.getModId() + "\",\n" +
                "  \"environment\": \"*\",\n" +
                "  \"entrypoints\": {\n" +
                "    \"preLaunch\": [\n" +
                "      \"com.kyroxova.bootstrapper.hooks.FabricPreLaunchHook\"\n" +
                "    ]\n" +
                "  },\n" +
                "  \"depends\": {\n" +
                "    \"fabricloader\": \">=0.14.0\",\n" +
                "    \"minecraft\": \"*\"\n" +
                "  }\n" +
                "}\n";
    }

    public static String generateQuiltJson(BootstrapperConfig config, TargetSpec target) {
        return "{\n" +
                "  \"schema_version\": 1,\n" +
                "  \"quilt_loader\": {\n" +
                "    \"group\": \"com.kyroxova\",\n" +
                "    \"id\": \"" + config.getModId() + "\",\n" +
                "    \"version\": \"" + target.getVersion().getRaw() + "\",\n" +
                "    \"metadata\": {\n" +
                "      \"name\": \"" + config.getModId() + "\"\n" +
                "    },\n" +
                "    \"intermediate_mappings\": \"net.fabricmc:intermediary\",\n" +
                "    \"depends\": [\n" +
                "      {\n" +
                "        \"id\": \"quilt_loader\",\n" +
                "        \"versions\": \">=0.19.0\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"minecraft\",\n" +
                "        \"versions\": \">=" + target.getVersion().getRaw() + "\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}\n";
    }

    public static String generateQuiltUniversalJson(BootstrapperConfig config, MCVersion version) {
        return "{\n" +
                "  \"schema_version\": 1,\n" +
                "  \"quilt_loader\": {\n" +
                "    \"group\": \"com.kyroxova\",\n" +
                "    \"id\": \"" + config.getModId() + "\",\n" +
                "    \"version\": \"" + version.getRaw() + "\",\n" +
                "    \"metadata\": {\n" +
                "      \"name\": \"" + config.getModId() + "\"\n" +
                "    },\n" +
                "    \"intermediate_mappings\": \"net.fabricmc:intermediary\",\n" +
                "    \"entrypoints\": {\n" +
                "      \"pre_launch\": [\n" +
                "        \"com.kyroxova.bootstrapper.hooks.FabricPreLaunchHook\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"depends\": [\n" +
                "      {\n" +
                "        \"id\": \"quilt_loader\",\n" +
                "        \"versions\": \">=0.19.0\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"minecraft\",\n" +
                "        \"versions\": \"*\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}\n";
    }

    public static String generateMcModInfo(BootstrapperConfig config, TargetSpec target) {
        return "[\n" +
                "  {\n" +
                "    \"modid\": \"" + config.getModId() + "\",\n" +
                "    \"name\": \"" + config.getModId() + "\",\n" +
                "    \"description\": \"Automated cross-version compatibility mod built with ContinuumLib\",\n" +
                "    \"version\": \"" + target.getVersion().getRaw() + "\",\n" +
                "    \"mcversion\": \"" + target.getVersion().getRaw() + "\",\n" +
                "    \"url\": \"\",\n" +
                "    \"updateUrl\": \"\",\n" +
                "    \"authorList\": [],\n" +
                "    \"credits\": \"\",\n" +
                "    \"logoFile\": \"\",\n" +
                "    \"screenshots\": [],\n" +
                "    \"dependencies\": []\n" +
                "  }\n" +
                "]\n";
    }
}
