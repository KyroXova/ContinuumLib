package com.kyroxova.continuumlib.knowledge.rule;

import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.resolver.ClassAdaptationPlan;
import java.util.*;

/** Exact routes only: a nearby version, namespace or loader is never an implicit fallback. */
public final class RuleCatalog {
    private final List<RulePack> packs;
    public RuleCatalog(Collection<RulePack> packs) {
        this.packs = packs.stream().sorted(Comparator.comparing(RulePack::id)).toList();
        var ids = new HashSet<String>();
        for (RulePack pack : this.packs)
            if (!ids.add(pack.id())) throw new IllegalArgumentException("Duplicate pack ID: " + pack.id());
    }
    public ClassAdaptationPlan select(EnvironmentId source, EnvironmentId target) {
        var matching = packs.stream().filter(p -> p.source().equals(source) && p.target().equals(target)).toList();
        if (matching.isEmpty()) throw new IllegalArgumentException("No rule packs for " + source + " -> " + target);
        return new ClassAdaptationPlan(source, target, matching);
    }
}
