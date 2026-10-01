package com.kyroxova.continuumlib.filter.rule;

import com.kyroxova.continuumlib.filter.condition.TargetContext;

import java.util.Objects;

/**
 * Logical set of exclusion rules merged from all files under exclusions/.
 * Missing or empty exclusions mean nothing is excluded.
 */
public record ExclusionRuleSet(RuleSet rules) {
    public static final ExclusionRuleSet EMPTY = new ExclusionRuleSet(RuleSet.EMPTY);

    public ExclusionRuleSet {
        Objects.requireNonNull(rules, "rules");
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public ExclusionRuleSet filterFor(TargetContext context) {
        return new ExclusionRuleSet(rules.filterFor(context));
    }
}
