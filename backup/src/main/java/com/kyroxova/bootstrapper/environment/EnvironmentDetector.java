package com.kyroxova.bootstrapper.environment;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Probes the running JVM environment to detect the active Minecraft version and Mod Loader.
 * Designed with defensive reflection and safe fallbacks for runtime and testing environments.
 */
public final class EnvironmentDetector {

    private static final Logger LOGGER = Logger.getLogger(EnvironmentDetector.class.getName());

    private static EnvironmentInfo cachedInfo = null;

    private EnvironmentDetector() {}

    public static synchronized EnvironmentInfo getEnvironment() {
        if (cachedInfo != null) {
            return cachedInfo;
        }

        LoaderType detectedLoader = detectLoader();
        MCVersion detectedVersion = detectMinecraftVersion();

        cachedInfo = new EnvironmentInfo(detectedLoader, detectedVersion);
        LOGGER.info(String.format("[ContinuumBootstrapper] Detected Environment: Loader=%s, Version=%s",
                detectedLoader.getDisplayName(), detectedVersion));

        return cachedInfo;
    }

    /**
     * Injects a mock/overridden environment for testing or build-time target simulation.
     */
    public static synchronized void setMockEnvironment(LoaderType loader, MCVersion version) {
        cachedInfo = new EnvironmentInfo(loader, version);
    }

    public static synchronized void reset() {
        cachedInfo = null;
    }

    private static LoaderType detectLoader() {
        // 1. Check NeoForge (NeoForged FML)
        if (isClassPresent("net.neoforged.fml.loading.FMLLoader") || isClassPresent("net.neoforged.neoforge.common.NeoForge")) {
            return LoaderType.NEOFORGE;
        }

        // 2. Check Quilt
        if (isClassPresent("org.quiltmc.loader.api.QuiltLoader")) {
            return LoaderType.QUILT;
        }

        // 3. Check Fabric
        if (isClassPresent("net.fabricmc.loader.api.FabricLoader")) {
            return LoaderType.FABRIC;
        }

        // 4. Check Modern Forge (1.13+)
        if (isClassPresent("net.minecraftforge.fml.loading.FMLLoader") || isClassPresent("net.minecraftforge.common.MinecraftForge")) {
            return LoaderType.FORGE;
        }

        // 5. Check Legacy Forge (1.7.9 - 1.12.2)
        if (isClassPresent("cpw.mods.fml.common.Loader") || isClassPresent("net.minecraftforge.fml.common.Loader")) {
            return LoaderType.FORGE;
        }

        return LoaderType.TEST_ENVIRONMENT;
    }

    private static MCVersion detectMinecraftVersion() {
        // Attempt 1: Modern SharedConstants (1.14+)
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            Method getCurrentVersion = sharedConstants.getMethod("getCurrentVersion");
            Object worldVersion = getCurrentVersion.invoke(null);
            if (worldVersion != null) {
                Method getName = worldVersion.getClass().getMethod("getName");
                String name = (String) getName.invoke(worldVersion);
                if (name != null && !name.isEmpty()) {
                    return MCVersion.of(name);
                }
            }
        } catch (Throwable ignored) {
            // Not running in modern MC or SharedConstants unobfuscated
        }

        // Attempt 2: NeoForge / Forge FMLLoader
        try {
            Class<?> fmlLoader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            Method versionInfoMethod = fmlLoader.getMethod("versionInfo");
            Object versionInfo = versionInfoMethod.invoke(null);
            if (versionInfo != null) {
                Method mcVersionMethod = versionInfo.getClass().getMethod("mcVersion");
                String version = (String) mcVersionMethod.invoke(versionInfo);
                if (version != null && !version.isEmpty()) {
                    return MCVersion.of(version);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> neoLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Method versionInfoMethod = neoLoader.getMethod("versionInfo");
            Object versionInfo = versionInfoMethod.invoke(null);
            if (versionInfo != null) {
                Method mcVersionMethod = versionInfo.getClass().getMethod("mcVersion");
                String version = (String) mcVersionMethod.invoke(versionInfo);
                if (version != null && !version.isEmpty()) {
                    return MCVersion.of(version);
                }
            }
        } catch (Throwable ignored) {}

        // Attempt 3: FabricLoader game version
        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Method getInstance = fabricLoader.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            if (instance != null) {
                Method getModContainer = fabricLoader.getMethod("getModContainer", String.class);
                Object mcContainerOpt = getModContainer.invoke(instance, "minecraft");
                if (mcContainerOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object container = opt.get();
                    Method getMetadata = container.getClass().getMethod("getMetadata");
                    Object metadata = getMetadata.invoke(container);
                    Method getVersion = metadata.getClass().getMethod("getVersion");
                    Object version = getVersion.invoke(metadata);
                    Method getFriendlyString = version.getClass().getMethod("getFriendlyString");
                    return MCVersion.of((String) getFriendlyString.invoke(version));
                }
            }
        } catch (Throwable ignored) {}

        // Default test environment fallback
        return MCVersion.V1_18_2;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, EnvironmentDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public record EnvironmentInfo(LoaderType loader, MCVersion version) {
        public boolean matches(LoaderType otherLoader, MCVersion otherVersion) {
            return this.loader == otherLoader && this.version.equals(otherVersion);
        }
    }
}
