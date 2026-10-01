package com.kyroxova.bootstrapper.hooks;

import com.kyroxova.bootstrapper.ContinuumBootstrapper;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ModLauncher transformation service plugin for modern Forge and NeoForge (1.13+ through 26.3+).
 * Intercepts classloading in ModLauncher's classloading pipeline and applies dynamic bytecode rewrites.
 * Registered via META-INF/services/cpw.mods.modlauncher.serviceapi.ITransformationService.
 */
public class ModLauncherPluginHook implements ContinuumTransformerHook {

    private static final Logger LOGGER = Logger.getLogger(ModLauncherPluginHook.class.getName());
    private static final String SERVICE_NAME = "continuumlib";
    private static volatile boolean initialized = false;

    public ModLauncherPluginHook() {}

    /**
     * Service name identifier required by ModLauncher.
     */
    public String name() {
        return SERVICE_NAME;
    }

    /**
     * ModLauncher initialization lifecycle hook.
     *
     * @param environment ModLauncher environment object
     */
    public void initialize(Object environment) {
        init();
    }

    /**
     * ModLauncher service loading callback.
     */
    public void onLoad(Object env, Set<String> otherServices) {
        LOGGER.info("[ContinuumLib] ModLauncherPluginHook loaded alongside services: " + otherServices);
        init();
    }

    /**
     * Initializes the ContinuumLib bootstrapper if not already started.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            LOGGER.info("[ContinuumLib] ModLauncherPluginHook activating ContinuumBootstrapper...");
            ContinuumBootstrapper bootstrapper = ContinuumBootstrapper.getInstance();
            if (bootstrapper.isTransformationRequired()) {
                LOGGER.info("[ContinuumLib] Dynamic in-memory bytecode transformation active under ModLauncher.");
            } else {
                LOGGER.info("[ContinuumLib] Native Forge/NeoForge environment matches authoring target.");
            }
            initialized = true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[ContinuumLib] Failed to initialize ModLauncherPluginHook", t);
        }
    }

    /**
     * Returns list of transformer providers or self if running under ModLauncher.
     */
    public List<Object> transformers() {
        return Collections.singletonList(this);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void reset() {
        initialized = false;
    }

    @Override
    public byte[] transform(String className, byte[] basicClass) {
        if (className == null || basicClass == null || basicClass.length == 0) {
            return basicClass;
        }
        return ContinuumBootstrapper.getInstance().transform(className, basicClass);
    }
}
