package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.OutputTargets;
import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable authoritative target state used during one target-generation execution. */
public record ResolvedTarget(
        String id,
        TransformRequest request,
        RulePack rulePack,
        TargetContext context
) {
    public ResolvedTarget {
        OutputTargets.validateTargetId(id);
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(rulePack, "rulePack");
        Objects.requireNonNull(context, "context");
        if (!request.packId().equals(rulePack.id())) {
            throw new IllegalArgumentException("Transform request pack " + request.packId()
                    + " does not match resolved pack " + rulePack.id());
        }
        if (!context.environmentId().equals(rulePack.target())) {
            throw new IllegalArgumentException("Target context environment does not match resolved rule-pack target");
        }
    }

    public EnvironmentId sourceEnvironment() {
        return rulePack.source();
    }

    public EnvironmentId targetEnvironment() {
        return rulePack.target();
    }

    public List<Path> sourceClasspath() {
        return classpath(true);
    }

    public List<Path> targetClasspath() {
        return classpath(false);
    }

    private List<Path> classpath(boolean source) {
        var result = new ArrayList<Path>();
        var artifacts = source ? request.sourceArtifacts() : request.targetArtifacts();
        var dependencies = source ? request.sourceClasspath() : request.targetClasspath();
        result.addAll(artifacts.values());
        dependencies.values().forEach(artifact -> result.add(artifact.file()));
        result.replaceAll(path -> path.toAbsolutePath().normalize());
        result.sort(Comparator.comparing(Path::toString));
        return List.copyOf(result);
    }
}
