package com.kyroxova.bootstrapper;

import com.kyroxova.bootstrapper.config.BootstrapperConfig;
import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.bootstrapper.environment.EnvironmentDetector;
import com.kyroxova.bootstrapper.hooks.ContinuumTransformerHook;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.transformer.ContinuumBytecodeTransformer;

import java.util.logging.Logger;

/**
 * Main bootstrapper entry point for ContinuumLib.
 * Coordinates environment detection, configuration loading, and bytecode transformer invocation.
 */
public final class ContinuumBootstrapper implements ContinuumTransformerHook {

    private static final Logger LOGGER = Logger.getLogger(ContinuumBootstrapper.class.getName());
    private static ContinuumBootstrapper instance;

    private final BootstrapperConfig config;
    private final EnvironmentDetector.EnvironmentInfo currentEnv;
    private final ContinuumBytecodeTransformer transformer;
    private final boolean requiresTransformation;

    private ContinuumBootstrapper() {
        this.config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());
        this.currentEnv = EnvironmentDetector.getEnvironment();

        TargetSpec base = config.getBaseSpec();
        boolean loaderMatch = (currentEnv.loader() == base.getLoader());
        boolean versionMatch = currentEnv.version().equals(base.getVersion());

        // In test environment, allow simulated transformation
        if (currentEnv.loader() == com.kyroxova.bootstrapper.environment.LoaderType.TEST_ENVIRONMENT) {
            this.requiresTransformation = true;
        } else {
            this.requiresTransformation = !(loaderMatch && versionMatch);
        }

        if (this.requiresTransformation) {
            LOGGER.info(String.format("[ContinuumBootstrapper] Active environment (%s %s) differs from Base (%s %s). Activating dynamic transformation pipeline.",
                    currentEnv.loader(), currentEnv.version(), base.getLoader(), base.getVersion()));
            ApiKnowledgeBase kb = ApiKnowledgeBase.createDefault();
            this.transformer = new ContinuumBytecodeTransformer(kb, base, new TargetSpec(currentEnv.version(), currentEnv.loader(), "mojmap", 0));
        } else {
            LOGGER.info("[ContinuumBootstrapper] Active environment matches Base authoring specification. Operating in zero-overhead pass-through mode.");
            this.transformer = null;
        }
    }

    public static synchronized ContinuumBootstrapper getInstance() {
        if (instance == null) {
            instance = new ContinuumBootstrapper();
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = null;
    }

    public BootstrapperConfig getConfig() {
        return config;
    }

    public EnvironmentDetector.EnvironmentInfo getCurrentEnv() {
        return currentEnv;
    }

    public boolean isTransformationRequired() {
        return requiresTransformation;
    }

    @Override
    public byte[] transform(String className, byte[] basicClass) {
        if (!requiresTransformation || transformer == null || basicClass == null || basicClass.length == 0) {
            return basicClass;
        }
        return transformer.transform(className, basicClass);
    }
}
