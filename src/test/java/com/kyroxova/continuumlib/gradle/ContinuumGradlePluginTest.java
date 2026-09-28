package com.kyroxova.continuumlib.gradle;

import com.kyroxova.bootstrapper.config.TargetSpec;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        ext.base("1.18.2", "forge");
        ext.target("1.20.1", "forge");
        ext.target("1.20.4", "neoforge");
        ext.target("1.21.1", "fabric");

        // Verify baseSpec and targets
        TargetSpec base = ext.getBaseSpec().get();
        assertEquals("1.18.2", base.getVersion().getRaw());
        assertEquals("forge", base.getLoader().getId());

        List<TargetSpec> targets = ext.getTargets().get();
        assertEquals(3, targets.size());
        assertEquals("1.20.1", targets.get(0).getVersion().getRaw());
        assertEquals("1.20.4", targets.get(1).getVersion().getRaw());
        assertEquals("1.21.1", targets.get(2).getVersion().getRaw());

        // Verify task registration
        Task task = project.getTasks().findByName("buildContinuumTargets");
        assertNotNull(task, "buildContinuumTargets task must be registered");
        assertInstanceOf(ContinuumBuildTask.class, task);
    }
}
