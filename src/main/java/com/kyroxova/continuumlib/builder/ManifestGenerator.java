package com.kyroxova.continuumlib.builder;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.LoaderType;

/**
 * Automatically synthesizes valid loader manifests for target builds.
 */
public final class ManifestGenerator {

    private ManifestGenerator() {}

    public static String generateManifest(BootstrapperConfig config, TargetSpec target) {
        if (target.getLoader() == LoaderType.NEOFORGE) {
            return generateNeoForgeToml(config, target);
        } else if (target.getLoader() == LoaderType.FABRIC || target.getLoader() == LoaderType.QUILT) {
            return generateFabricJson(config, target);
        } else {
            return generateForgeToml(config, target);
        }
    }

    public static String getManifestPath(TargetSpec target) {
        if (target.getLoader() == LoaderType.NEOFORGE) {
            return "META-INF/neoforge.mods.toml";
        } else if (target.getLoader() == LoaderType.FABRIC || target.getLoader() == LoaderType.QUILT) {
            return "fabric.mod.json";
        } else {
            return "META-INF/mods.toml";
        }
    }

    private static String generateForgeToml(BootstrapperConfig config, TargetSpec target) {
        return "modLoader=\"javafml\"\n" +
                "loaderVersion=\"[40,)\"\n" +
                "license=\"All Rights Reserved\"\n" +
                "[[mods]]\n" +
                "modId=\"" + config.getModId() + "\"\n" +
                "version=\"" + target.getVersion().getRaw() + "\"\n" +
                "displayName=\"" + config.getModId() + "\"\n";
    }

    private static String generateNeoForgeToml(BootstrapperConfig config, TargetSpec target) {
        return "modLoader=\"javafml\"\n" +
                "loaderVersion=\"[20.4,)\"\n" +
                "license=\"All Rights Reserved\"\n" +
                "[[mods]]\n" +
                "modId=\"" + config.getModId() + "\"\n" +
                "version=\"" + target.getVersion().getRaw() + "\"\n" +
                "displayName=\"" + config.getModId() + "\"\n";
    }

    private static String generateFabricJson(BootstrapperConfig config, TargetSpec target) {
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
}
