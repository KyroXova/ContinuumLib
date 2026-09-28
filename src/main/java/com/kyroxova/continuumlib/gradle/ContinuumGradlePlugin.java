package com.kyroxova.continuumlib.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.util.ArrayList;

/**
 * ContinuumLib Gradle Plugin.
 * Enables zero-boilerplate multi-target builds directly from standard Gradle projects.
 */
public class ContinuumGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        ContinuumExtension extension = project.getExtensions().create("continuum", ContinuumExtension.class, project);

        // 1. Register main buildContinuumTargets task
        TaskProvider<ContinuumBuildTask> buildTask = project.getTasks().register("buildContinuumTargets", ContinuumBuildTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Builds multi-version and multi-loader JARs using ContinuumLib bytecode retargeting.");

            task.getModId().set(extension.getModId());
            task.getMode().set(extension.getMode());
            task.getUniversalBundle().set(extension.getUniversalBundle());
            task.getJarNamingFormat().set(extension.getJarNamingFormat());
            task.getDestinationPath().set(extension.getDestinationPath());
            task.getBaseSpec().set(extension.getBaseSpec());
            task.getTargets().set(extension.getTargets());
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("libs"));
        });

        // 2. Register dedicated buildUniversalJar task
        TaskProvider<ContinuumBuildTask> universalTask = project.getTasks().register("buildUniversalJar", ContinuumBuildTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Packages a Universal In-Memory Multi-Loader Bootstrap Bundle.");

            task.getModId().set(extension.getModId());
            task.getMode().set("runtime");
            task.getUniversalBundle().set(true);
            task.getJarNamingFormat().set(extension.getJarNamingFormat());
            task.getDestinationPath().set(extension.getDestinationPath());
            task.getBaseSpec().set(extension.getBaseSpec());
            task.getTargets().set(new ArrayList<>());
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("libs"));
        });

        // Automatically configure input jar from jar task if available
        project.getTasks().withType(Jar.class).matching(j -> "jar".equals(j.getName())).configureEach(jarTask -> {
            buildTask.configure(task -> {
                task.getInputJar().set(jarTask.getArchiveFile());
                task.dependsOn(jarTask);
            });
            universalTask.configure(task -> {
                task.getInputJar().set(jarTask.getArchiveFile());
                task.dependsOn(jarTask);
            });
        });

        // Hook buildContinuumTargets into assemble task
        project.getTasks().matching(t -> "assemble".equals(t.getName())).configureEach(assembleTask -> {
            assembleTask.dependsOn(buildTask);
        });
    }
}
