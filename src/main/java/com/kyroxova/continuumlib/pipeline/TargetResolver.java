package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.continuumlib.api.config.TransformRequest;
import com.kyroxova.continuumlib.filter.condition.TargetContext;
import com.kyroxova.continuumlib.knowledge.rule.RuleCatalog;
import com.kyroxova.continuumlib.knowledge.rule.RulePack;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Resolves one explicit consumer target without guessing nearby versions, loaders, or namespaces. */
public final class TargetResolver {
    public ResolvedTarget resolve(String targetId, TransformRequest request, Collection<RulePack> rulePacks) {
        return resolve(targetId, request, rulePacks, null, "per_version");
    }

    public ResolvedTarget resolve(String targetId, TransformRequest request, Collection<RulePack> rulePacks,
                                  String loaderVersion, String outputMode) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(rulePacks, "rulePacks");
        List<RulePack> packs = List.copyOf(rulePacks);
        new RuleCatalog(packs); // Enforce globally unique pack IDs before selecting one.

        RulePack selected = packs.stream()
                .filter(pack -> pack.id().equals(request.packId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ContinuumLib pack: " + request.packId()));

        TargetContext context = TargetContext.of(selected.target(), loaderVersion, outputMode);
        return new ResolvedTarget(targetId, request, selected, context);
    }
}
