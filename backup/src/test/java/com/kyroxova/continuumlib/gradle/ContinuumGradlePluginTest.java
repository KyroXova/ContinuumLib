package com.kyroxova.continuumlib.gradle;

import com.kyroxova.bootstrapper.config.TargetSpec;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ContinuumGradlePluginTest {

    @Test
    public void testPluginApplicationAndExtensionConfiguration() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("com.kyroxova.continuumlib");

        // Verify extension is registered
        ContinuumExtension ext = project.getExtensions().findByType(ContinuumExtension.class);
        assertNotNull(ext, "ContinuumExtension must be registered");

        // Configure extension via DSL methods
        ext.getModId().set("testmod");
        ext.mode("hybrid");
        ext.universal(true);
        ext.jarNamingFormat("%modid%-%loader%-%version%.jar");
        ext.destinationPath("build/libs/%loader%/");
        ext.base("1.18.2", "forge");
        ext.target("1.20.1", "forge");
        ext.target("1.20.4", "neoforge");
        ext.target("1.21.1", "fabric");

        // Verify extension values
        assertEquals("testmod", ext.getModId().get());
        assertEquals("hybrid", ext.getMode().get());
        assertTrue(ext.getUniversalBundle().get());
        assertEquals("%modid%-%loader%-%version%.jar", ext.getJarNamingFormat().get());
        assertEquals("build/libs/%loader%/", ext.getDestinationPath().get());

        // Verify baseSpec and targets
        TargetSpec base = ext.getBaseSpec().get();
        assertEquals("1.18.2", base.getVersion().getRaw());
        assertEquals("forge", base.getLoader().getId());

        List<TargetSpec> targets = ext.getTargets().get();
        assertEquals(3, targets.size());
        assertEquals("1.20.1", targets.get(0).getVersion().getRaw());
        assertEquals("1.20.4", targets.get(1).getVersion().getRaw());
        assertEquals("1.21.1", targets.get(2).getVersion().getRaw());

        // Verify task registrations
        Task buildTask = project.getTasks().findByName("buildContinuumTargets");
        assertNotNull(buildTask, "buildContinuumTargets task must be registered");
        assertInstanceOf(ContinuumBuildTask.class, buildTask);

        Task universalTask = project.getTasks().findByName("buildUniversalJar");
        assertNotNull(universalTask, "buildUniversalJar task must be registered");
        assertInstanceOf(ContinuumBuildTask.class, universalTask);
    }

    @Test
    public void testTaskExecutionBuildsJars(@TempDir Path tempDir) throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();
        project.getPlugins().apply("java");
        project.getPlugins().apply("com.kyroxova.continuumlib");

        ContinuumExtension ext = project.getExtensions().getByType(ContinuumExtension.class);
        ext.getModId().set("packagemod");
        ext.mode("hybrid");
        ext.universal(true);
        ext.base("1.18.2", "forge");
        ext.target("1.20.4", "neoforge");

        // Create a mock jar file
        File mockJar = new File(tempDir.toFile(), "input.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(mockJar))) {
            jos.putNextEntry(new JarEntry("com/example/TestClass.class"));
            jos.write(new byte[]{ (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE });
            jos.closeEntry();
        }

        ContinuumBuildTask buildTask = (ContinuumBuildTask) project.getTasks().getByName("buildContinuumTargets");
        buildTask.getInputJar().set(mockJar);

        // Execute task action
        buildTask.buildTargets();

        File libsDir = new File(tempDir.toFile(), "build/libs");
        assertTrue(libsDir.exists(), "libs directory should be created");

        File neoDir = new File(libsDir, "neoforge");
        assertTrue(neoDir.exists());
        File neoJar = new File(neoDir, "packagemod-neoforge-1.20.4.jar");
        assertTrue(neoJar.exists(), "Target NeoForge JAR must be created");

        File universalDir = new File(libsDir, "universal");
        assertTrue(universalDir.exists());
        File universalJar = new File(universalDir, "packagemod-universal-1.18.2.jar");
        assertTrue(universalJar.exists(), "Universal Bundle must be created");
    }
}
