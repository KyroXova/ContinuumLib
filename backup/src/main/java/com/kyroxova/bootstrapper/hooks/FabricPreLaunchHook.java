package com.kyroxova.bootstrapper.hooks;

import com.kyroxova.bootstrapper.ContinuumBootstrapper;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fabric preLaunch entrypoint hook for ContinuumLib.
 * Invoked by Fabric Loader before Minecraft game classes are initialized.
 * Registered in fabric.mod.json entrypoints under "preLaunch".
 */
public class FabricPreLaunchHook implements ContinuumTransformerHook {

    private static final Logger LOGGER = Logger.getLogger(FabricPreLaunchHook.class.getName());
    private static volatile boolean initialized = false;

    public FabricPreLaunchHook() {}

    /**
     * Standard Fabric Loader PreLaunchEntrypoint method.
     */
    public void onPreLaunch() {
        init();
    }

    /**
     * Initializes the ContinuumLib bootstrapper and attaches in-memory transformation hooks.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            LOGGER.info("[ContinuumLib] FabricPreLaunchHook initializing ContinuumBootstrapper runtime pipeline...");
            ContinuumBootstrapper bootstrapper = ContinuumBootstrapper.getInstance();
            if (bootstrapper.isTransformationRequired()) {
                LOGGER.info("[ContinuumLib] Dynamic in-memory bytecode transformation activated for Fabric runtime.");
            } else {
                LOGGER.info("[ContinuumLib] Native loader match; running in zero-overhead pass-through mode.");
            }
            initialized = true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[ContinuumLib] Failed to initialize FabricPreLaunchHook", t);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void reset() {
        initialized = false;
    }

    @Override
    public byte[] transform(String className, byte[] basicClass) {
        return ContinuumBootstrapper.getInstance().transform(className, basicClass);
    }
}
