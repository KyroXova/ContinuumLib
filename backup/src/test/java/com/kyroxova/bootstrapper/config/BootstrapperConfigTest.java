package com.kyroxova.bootstrapper.config;

import com.kyroxova.bootstrapper.environment.LoaderType;
import com.kyroxova.bootstrapper.environment.MCVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BootstrapperConfigTest {

    @Test
    public void testConfigLoadingFromClasspath() {
        BootstrapperConfig config = BootstrapperConfig.loadFromClasspath(getClass().getClassLoader());

        assertNotNull(config);
        assertEquals("examplemod", config.getModId());
        assertEquals("hybrid", config.getMode());

        TargetSpec base = config.getBaseSpec();
        assertEquals(MCVersion.V1_18_2, base.getVersion());
        assertEquals(LoaderType.FORGE, base.getLoader());

        assertFalse(config.getTargetSpecs().isEmpty());

        String jarName = config.formatJarName("1.20.4", "neoforge");
        assertEquals("examplemod-neoforge-1.20.4.jar", jarName);

        String destDir = config.formatDestinationDir("forge");
        assertEquals("build/libs/forge/", destDir);
    }
}
