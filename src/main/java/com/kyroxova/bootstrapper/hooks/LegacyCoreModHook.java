package com.kyroxova.bootstrapper.hooks;

import com.kyroxova.bootstrapper.ContinuumBootstrapper;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Legacy Forge CoreMod and ClassTransformer hook for Minecraft 1.7.9 through 1.12.2.
 * Adheres to FML's IFMLLoadingPlugin and IClassTransformer protocols.
 * Referenced by JAR MANIFEST.MF via FMLCorePlugin attribute.
 */
public class LegacyCoreModHook implements ContinuumTransformerHook {

    private static final Logger LOGGER = Logger.getLogger(LegacyCoreModHook.class.getName());
    private static volatile boolean initialized = false;

    public LegacyCoreModHook() {}

    /**
     * IFMLLoadingPlugin: Returns transformer class names to register with FML.
     */
    public String[] getASMTransformerClass() {
        return new String[]{ LegacyCoreModHook.class.getName() };
    }

    /**
     * IFMLLoadingPlugin: Mod container class (null if none).
     */
    public String getModContainerClass() {
        return null;
    }

    /**
     * IFMLLoadingPlugin: Setup class.
     */
    public String getSetupClass() {
        return null;
    }

    /**
     * IFMLLoadingPlugin: Injected data map from FML.
     */
    public void injectData(Map<String, Object> data) {
        init();
    }

    /**
     * IFMLLoadingPlugin: Access transformer class.
     */
    public String getAccessTransformerClass() {
        return null;
    }

    /**
     * Initializes the Continuum bootstrapper for legacy FML runtimes.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            LOGGER.info("[ContinuumLib] LegacyCoreModHook activating ContinuumBootstrapper for legacy FML...");
            ContinuumBootstrapper bootstrapper = ContinuumBootstrapper.getInstance();
            if (bootstrapper.isTransformationRequired()) {
                LOGGER.info("[ContinuumLib] Legacy CoreMod dynamic bytecode transformation active.");
            }
            initialized = true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[ContinuumLib] Failed to initialize LegacyCoreModHook", t);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void reset() {
        initialized = false;
    }

    /**
     * IClassTransformer: Standard FML class transformer signature.
     *
     * @param name original untransformed name
     * @param transformedName deobfuscated or mapped class name
     * @param basicClass raw incoming class bytes
     * @return transformed class bytes
     */
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        String targetName = (transformedName != null && !transformedName.isEmpty()) ? transformedName : name;
        return transform(targetName, basicClass);
    }

    @Override
    public byte[] transform(String className, byte[] basicClass) {
        if (className == null || basicClass == null || basicClass.length == 0) {
            return basicClass;
        }
        return ContinuumBootstrapper.getInstance().transform(className, basicClass);
    }
}
