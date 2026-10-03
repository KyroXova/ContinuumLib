package com.kyroxova.continuumlib.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.tasks.Jar;
import com.kyroxova.continuumlib.api.config.OutputTargets;
import com.kyroxova.continuumlib.pipeline.config.ProjectConfigurationLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ContinuumLibPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        project.getPluginManager().apply("java");

        ProjectConfigurationLocator locator = new ProjectConfigurationLocator();
        var config = locator.locate(project.getProjectDir().toPath());

        project.getTasks().withType(ArtifactRequestTask.class).configureEach(task -> {
            task.getRuleFiles().from(project.fileTree("src/main/resources/continuumlib/knowledge", tree -> tree.include("**/*.xml")));
            task.getRuleFiles().from(project.fileTree("src/main/resources/data/continuumlib/knowledge", tree -> tree.include("**/*.xml")));
        });

        var jar = project.getTasks().named("jar", Jar.class);
        var mainSourceSet = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Path transformCfg = config.transformFile().toFile().isFile()
                ? config.transformFile()
                : project.getProjectDir().toPath().resolve("src/main/resources/data/continuumlib/transform.properties");

        project.getTasks().register("continuumLibCompareApis", CompareApisTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Compare complete declared API surfaces without guessing migration rules");
            task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            task.getConfigFile().convention(project.getLayout().file(project.provider(transformCfg::toFile)));
            task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/api-delta.tsv"));
        });

        var transform = project.getTasks().register("continuumLibTransformJar", TransformJarTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Apply artifact-bound rules to a mod JAR; output is not gameplay-certified");
            task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            task.getInputJar().convention(jar.flatMap(Jar::getArchiveFile));
            task.getConfigFile().convention(project.getLayout().file(project.provider(transformCfg::toFile)));
            task.getOutputJar().convention(project.getLayout().getBuildDirectory().file("continuumlib/" + project.getName() + "-transformed.jar"));
        });

        var transformSource = project.getTasks().register("continuumLibTransformSource", TransformSourceTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Transform developer Java source AST into target-compatible generated source and compile target JAR");
            task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            task.getSourceRoots().from(project.provider(() -> mainSourceSet.getJava().getSrcDirs()));
            task.getSourceFiles().from(mainSourceSet.getJava());
            task.getResourceFiles().from(mainSourceSet.getResources());
            task.getResourceRoots().from(project.provider(() -> mainSourceSet.getResources().getSrcDirs()));
            task.getManifestAttributes().set(project.provider(() -> {
                java.util.Map<String, String> attributes = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                jar.get().getManifest().getAttributes().forEach(
                        (key, value) -> attributes.put(key, String.valueOf(value))
                );
                return attributes;
            }));
            task.getTargetWorkspaceDirectory().convention(project.getLayout().getBuildDirectory().dir("continuum/targets/source"));
            task.getConfigFile().convention(project.getLayout().file(project.provider(transformCfg::toFile)));
            task.getGeneratedSourceDirectory().convention(project.getLayout().getBuildDirectory().dir("continuum/generated-src"));
            task.getCompiledClassesDirectory().convention(project.getLayout().getBuildDirectory().dir("continuum/classes"));
            task.getOutputJar().convention(project.getLayout().getBuildDirectory().file("continuumlib/" + project.getName() + "-source-adapted.jar"));
        });

        project.getTasks().register("continuumLibAuditTarget", AuditTargetTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Audit all transformed member references against supplied target declarations");
            task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            task.getConfigFile().convention(transform.flatMap(TransformJarTask::getConfigFile));
            task.getInputJar().convention(transform.flatMap(TransformJarTask::getOutputJar));
            task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/target-audit.tsv"));
            task.getTypeReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/target-types.tsv"));
        });

        project.getTasks().register("continuumLibValidateRules", ValidateRulesTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Validate consumer rule packs; artifact binding and compatibility are separate checks");
            task.getRuleFiles().from(project.fileTree("src/main/resources/continuumlib/knowledge", tree -> tree.include("**/*.xml")));
            task.getRuleFiles().from(project.fileTree("src/main/resources/data/continuumlib/knowledge", tree -> tree.include("**/*.xml")));
            task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/rule-packs.txt"));
        });

        project.getTasks().register("continuumLibInspect", InspectTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Inventory compiled classes and API references; does not certify compatibility");
            task.getInputJar().convention(jar.flatMap(Jar::getArchiveFile));
            task.getReportFile().convention(project.getLayout().getBuildDirectory()
                    .file("reports/continuumlib/api-inventory.tsv"));
        });

        configureTargets(project, jar, config, mainSourceSet);
    }

    private static void configureTargets(
            Project project,
            TaskProvider<Jar> jar,
            ProjectConfigurationLocator.DiscoveredConfiguration config,
            SourceSet mainSourceSet
    ) {
        Path targetsPath = config.targetsFile().toFile().isFile()
                ? config.targetsFile()
                : project.getProjectDir().toPath().resolve("src/main/resources/data/continuumlib/targets.properties");

        var validation = project.getTasks().register("continuumLibValidateOutputModes", ValidateOutputModesTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.getConfigFile().convention(project.getLayout().file(project.provider(targetsPath::toFile)));
        });

        var aggregate = project.getTasks().register("continuumLibBuildTargets", task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Build every explicitly configured development target JAR");
            task.dependsOn(validation);
        });

        var generateTargets = project.getTasks().register("continuumLibGenerateTargets", task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Generate all configured development targets via the unified pipeline");
            task.dependsOn(validation);
        });

        var audits = project.getTasks().register("continuumLibAuditTargets", task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Audit member declarations for every configured development target");
            task.dependsOn(validation);
        });

        if (!Files.isRegularFile(targetsPath)) return;

        OutputTargets outputs;
        try { outputs = OutputTargets.read(targetsPath); }
        catch (IOException e) { throw new GradleException(e.getMessage(), e); }
        if (!outputs.perVersion()) return;

        for (String id : outputs.targets()) {
            Path targetConfigFile = config.targetsDir().resolve(id + ".properties");
            if (!Files.isRegularFile(targetConfigFile)) {
                targetConfigFile = project.getProjectDir().toPath().resolve("src/main/resources/data/continuumlib/targets/" + id + ".properties");
            }
            Path finalTargetConfigFile = targetConfigFile;

            var generate = project.getTasks().register("continuumLibGenerate_" + id, GenerateTargetTask.class, task -> {
                task.setGroup("ContinuumLib");
                task.setDescription("Generate development target " + id + " using the unified pipeline");
                task.dependsOn(validation);
                task.getTargetId().convention(id);
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getSourceRoots().from(project.provider(() -> mainSourceSet.getJava().getSrcDirs()));
                task.getSourceFiles().from(mainSourceSet.getJava());
                task.getResourceFiles().from(mainSourceSet.getResources());
                task.getResourceRoots().from(project.provider(() -> mainSourceSet.getResources().getSrcDirs()));
                task.getManifestAttributes().set(project.provider(() -> {
                    java.util.Map<String, String> attributes = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    jar.get().getManifest().getAttributes().forEach(
                            (key, value) -> attributes.put(key, String.valueOf(value))
                    );
                    return attributes;
                }));
                task.getConfigFile().convention(project.getLayout().file(project.provider(finalTargetConfigFile::toFile)));
                task.getTargetWorkspaceDirectory().convention(project.getLayout().getBuildDirectory().dir("continuum/targets/" + id));
                task.getOutputJar().convention(project.getLayout().getBuildDirectory().file("continuumlib/" + id + ".jar"));
            });
            generateTargets.configure(task -> task.dependsOn(generate));
            aggregate.configure(task -> task.dependsOn(generate));

            var target = project.getTasks().register("continuumLibTransform_" + id, TransformJarTask.class, task -> {
                task.setGroup("ContinuumLib");
                task.setDescription("Transform development target " + id + " (not gameplay-certified)");
                task.dependsOn(validation);
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getInputJar().convention(jar.flatMap(Jar::getArchiveFile));
                task.getConfigFile().convention(project.getLayout().file(project.provider(finalTargetConfigFile::toFile)));
                task.getOutputJar().convention(project.getLayout().getBuildDirectory().file("continuum/legacy-bytecode/" + id + ".jar"));
            });

            var audit = project.getTasks().register("continuumLibAudit_" + id, AuditTargetTask.class, task -> {
                task.setGroup("ContinuumLib");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getConfigFile().convention(generate.flatMap(GenerateTargetTask::getConfigFile));
                task.getInputJar().convention(generate.flatMap(GenerateTargetTask::getOutputJar));
                task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/" + id + "-audit.tsv"));
                task.getTypeReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/" + id + "-types.tsv"));
            });
            audits.configure(task -> task.dependsOn(audit));
        }
    }
}
