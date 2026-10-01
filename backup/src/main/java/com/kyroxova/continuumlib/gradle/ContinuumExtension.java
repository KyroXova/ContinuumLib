package com.kyroxova.continuumlib.gradle;

import com.kyroxova.bootstrapper.config.TargetSpec;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Gradle configuration extension for ContinuumLib multi-target builds.
 *
 * Example build.gradle:
 * <pre>
 * continuumlib {
 *     modId = "mymod"
 *     mode = "hybrid" // "hybrid", "multi-jar", or "runtime"
 *     universalBundle = true
 *     base "1.18.2", "forge"
 *     targets {
 *         target "1.20.1", "forge"
 *         target "1.20.4", "neoforge"
 *         target "1.20.6", "neoforge"
 *         target "1.21.1", "neoforge"
 *         target "1.21.1", "fabric"
 *         target "1.20.1", "quilt"
 *     }
 * }
 * </pre>
 */
public class ContinuumExtension {

    private final Property<String> modId;
    private final Property<String> mode;
    private final Property<Boolean> universalBundle;
    private final Property<String> jarNamingFormat;
    private final Property<String> destinationPath;
    private final Property<TargetSpec> baseSpec;
    private final ListProperty<TargetSpec> targets;
    private final Project project;

    @Inject
    public ContinuumExtension(Project project) {
        this.project = project;
        ObjectFactory objects = project.getObjects();

        this.modId = objects.property(String.class).convention("mymod");
        this.mode = objects.property(String.class).convention("hybrid");
        this.universalBundle = objects.property(Boolean.class).convention(true);
        this.jarNamingFormat = objects.property(String.class).convention("%modid%-%loader%-%version%.jar");
        this.destinationPath = objects.property(String.class).convention("build/libs/%loader%/");
        this.baseSpec = objects.property(TargetSpec.class).convention(TargetSpec.of("1.18.2", "forge"));
        this.targets = objects.listProperty(TargetSpec.class).convention(new ArrayList<>());
    }

    public Property<String> getModId() {
        return modId;
    }

    public Property<String> getMode() {
        return mode;
    }

    public void mode(String mode) {
        this.mode.set(mode);
    }

    public Property<Boolean> getUniversalBundle() {
        return universalBundle;
    }

    public void universal(boolean enabled) {
        this.universalBundle.set(enabled);
    }

    public Property<String> getJarNamingFormat() {
        return jarNamingFormat;
    }

    public void jarNamingFormat(String format) {
        this.jarNamingFormat.set(format);
    }

    public Property<String> getDestinationPath() {
        return destinationPath;
    }

    public void destinationPath(String path) {
        this.destinationPath.set(path);
    }

    public Property<TargetSpec> getBaseSpec() {
        return baseSpec;
    }

    public ListProperty<TargetSpec> getTargets() {
        return targets;
    }

    public void base(String version, String loader) {
        this.baseSpec.set(TargetSpec.of(version, loader));
    }

    public void base(String version, String loader, String mappings) {
        this.baseSpec.set(TargetSpec.of(version, loader, mappings));
    }

    public void target(String version, String loader) {
        this.targets.add(TargetSpec.of(version, loader));
    }

    public void target(String version, String loader, String mappings) {
        this.targets.add(TargetSpec.of(version, loader, mappings));
    }

    public void targets(Action<TargetContainer> action) {
        TargetContainer container = new TargetContainer();
        action.execute(container);
        this.targets.addAll(container.configuredTargets);
    }

    public static class TargetContainer {
        private final List<TargetSpec> configuredTargets = new ArrayList<>();

        public void target(String version, String loader) {
            configuredTargets.add(TargetSpec.of(version, loader));
        }

        public void target(String version, String loader, String mappings) {
            configuredTargets.add(TargetSpec.of(version, loader, mappings));
        }
    }
}
