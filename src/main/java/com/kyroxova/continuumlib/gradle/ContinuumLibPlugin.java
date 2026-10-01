package com.kyroxova.continuumlib.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import com.kyroxova.continuumlib.api.config.OutputTargets;
import java.io.IOException;

/** Consumer integration. Inspection is deliberately separate from compatibility certification. */
public final class ContinuumLibPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        project.getPluginManager().apply("java");
        project.getTasks().withType(ArtifactRequestTask.class).configureEach(task ->
                task.getRuleFiles().from(project.fileTree("src/main/resources/data/continuumlib/knowledge", tree -> tree.include("**/*.xml"))));
        var jar = project.getTasks().named("jar", Jar.class);
        project.getTasks().register("continuumLibCompareApis", CompareApisTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Compare complete declared API surfaces without guessing migration rules");
            task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            task.getConfigFile().convention(project.getLayout().getProjectDirectory().file("src/main/resources/data/continuumlib/transform.properties"));
            task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/api-delta.tsv"));
        });
        var transform = project.getTasks().register("continuumLibTransformJar", TransformJarTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Apply artifact-bound rules to a mod JAR; output is not gameplay-certified");
            task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            task.getInputJar().convention(jar.flatMap(Jar::getArchiveFile));
            task.getConfigFile().convention(project.getLayout().getProjectDirectory().file("src/main/resources/data/continuumlib/transform.properties"));
            task.getRuleFiles().from(project.fileTree("src/main/resources/data/continuumlib/knowledge", tree -> tree.include("**/*.xml")));
            task.getOutputJar().convention(project.getLayout().getBuildDirectory().file("continuumlib/" + project.getName() + "-transformed.jar"));
        });
        var transformSource = project.getTasks().register("continuumLibTransformSource", TransformSourceTask.class, task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Transform developer Java source AST into target-compatible generated source and compile target JAR");
            task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            task.getSourceDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/java"));
            task.getConfigFile().convention(project.getLayout().getProjectDirectory().file("src/main/resources/data/continuumlib/transform.properties"));
            task.getRuleFiles().from(project.fileTree("src/main/resources/data/continuumlib/knowledge", tree -> tree.include("**/*.xml")));
            task.getGeneratedSourceDirectory().convention(project.getLayout().getBuildDirectory().dir("continuum/targets/source/source"));
            task.getCompiledClassesDirectory().convention(project.getLayout().getBuildDirectory().dir("continuum/targets/source/classes"));
            task.getOutputJar().convention(project.getLayout().getBuildDirectory().file("continuum/targets/source/output/" + project.getName() + "-source-adapted.jar"));
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
        configureTargets(project, jar);
    }
    private static void configureTargets(Project project, TaskProvider<Jar> jar) {
        var config = project.getLayout().getProjectDirectory().file("src/main/resources/data/continuumlib/targets.properties");
        var validation = project.getTasks().register("continuumLibValidateOutputModes", ValidateOutputModesTask.class, task -> {
            task.setGroup("ContinuumLib"); task.getConfigFile().convention(config);
        });
        var aggregate = project.getTasks().register("continuumLibBuildTargets", task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Build every explicitly configured development target JAR");
            task.dependsOn(validation);
        });
        var audits = project.getTasks().register("continuumLibAuditTargets", task -> {
            task.setGroup("ContinuumLib");
            task.setDescription("Audit member declarations for every configured development target");
            task.dependsOn(validation);
        });
        if (!config.getAsFile().isFile()) return;
        OutputTargets outputs;
        try { outputs = OutputTargets.read(config.getAsFile().toPath()); }
        catch (IOException e) { throw new GradleException(e.getMessage(), e); }
        if (!outputs.perVersion()) return;
        for (String id : outputs.targets()) {
            var target = project.getTasks().register("continuumLibTransform_" + id, TransformJarTask.class, task -> {
                task.setGroup("ContinuumLib");
                task.setDescription("Transform development target " + id + " (not gameplay-certified)");
                task.dependsOn(validation);
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getInputJar().convention(jar.flatMap(Jar::getArchiveFile));
                task.getConfigFile().convention(project.getLayout().getProjectDirectory().file("src/main/resources/data/continuumlib/targets/" + id + ".properties"));
                task.getRuleFiles().from(project.fileTree("src/main/resources/data/continuumlib/knowledge", tree -> tree.include("**/*.xml")));
                task.getOutputJar().convention(project.getLayout().getBuildDirectory().file("continuumlib/" + id + ".jar"));
            });
            aggregate.configure(task -> task.dependsOn(target));
            var audit = project.getTasks().register("continuumLibAudit_" + id, AuditTargetTask.class, task -> {
                task.setGroup("ContinuumLib");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getConfigFile().convention(target.flatMap(TransformJarTask::getConfigFile));
                task.getInputJar().convention(target.flatMap(TransformJarTask::getOutputJar));
                task.getReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/" + id + "-audit.tsv"));
                task.getTypeReportFile().convention(project.getLayout().getBuildDirectory().file("reports/continuumlib/" + id + "-types.tsv"));
            });
            audits.configure(task -> task.dependsOn(audit));
        }
    }
}
