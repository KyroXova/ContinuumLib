package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.TargetContext;

import java.util.Objects;

/**
 * Logical set of inclusion rules merged from all files under inclusions/.
 * Missing or empty inclusions mean no inclusion filtering is applied (not "exclude everything").
 */
public record InclusionRuleSet(RuleSet rules) {
    public static final InclusionRuleSet EMPTY = new InclusionRuleSet(RuleSet.EMPTY);

    public InclusionRuleSet {
        Objects.requireNonNull(rules, "rules");
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public InclusionRuleSet filterFor(TargetContext context) {
        return new InclusionRuleSet(rules.filterFor(context));
    }
}
