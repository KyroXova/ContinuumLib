package com.kyroxova.continuumlib.filter.condition;

import com.kyroxova.continuumlib.model.version.MinecraftVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates version expressions such as ">=1.20 <1.21", ">=1.21", "<=1.20.1", "=1.20.1", "!=1.20.1".
 */
public final class VersionConstraint implements Predicate<String> {
    private static final Pattern CLAUSE_PATTERN = Pattern.compile("^(>=|<=|>|<|!=|=|==)?\\s*(.+)$");

    private final String expression;
    private final List<Predicate<MinecraftVersion>> clauses;

    private VersionConstraint(String expression, List<Predicate<MinecraftVersion>> clauses) {
        this.expression = expression;
        this.clauses = List.copyOf(clauses);
    }

    public static VersionConstraint parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        String trimmed = expression.trim();
        if (trimmed.isEmpty() || trimmed.equals("*")) {
            return new VersionConstraint(expression, List.of(v -> true));
        }

        String[] tokens = trimmed.split("\\s+");
        List<Predicate<MinecraftVersion>> predicates = new ArrayList<>();

        for (String token : tokens) {
            Matcher m = CLAUSE_PATTERN.matcher(token);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid version expression clause: " + token);
            }
            String op = m.group(1);
            String verStr = m.group(2);
            MinecraftVersion targetVer = MinecraftVersion.parse(verStr);

            if (op == null || op.equals("=") || op.equals("==")) {
                predicates.add(v -> v.compareTo(targetVer) == 0);
            } else if (op.equals("!=")) {
                predicates.add(v -> v.compareTo(targetVer) != 0);
            } else if (op.equals(">=")) {
                predicates.add(v -> v.compareTo(targetVer) >= 0);
            } else if (op.equals(">")) {
                predicates.add(v -> v.compareTo(targetVer) > 0);
            } else if (op.equals("<=")) {
                predicates.add(v -> v.compareTo(targetVer) <= 0);
            } else if (op.equals("<")) {
                predicates.add(v -> v.compareTo(targetVer) < 0);
            } else {
                throw new IllegalArgumentException("Unsupported version operator: " + op);
            }
        }

        return new VersionConstraint(expression, predicates);
    }

    @Override
    public boolean test(String versionString) {
        if (versionString == null) return false;
        try {
            MinecraftVersion ver = MinecraftVersion.parse(versionString);
            for (var clause : clauses) {
                if (!clause.test(ver)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean testJava(String javaConstraint, int javaVersion) {
        if (javaConstraint == null || javaConstraint.isBlank() || javaConstraint.equals("*")) {
            return true;
        }
        String[] tokens = javaConstraint.trim().split("\\s+");
        for (String token : tokens) {
            Matcher m = CLAUSE_PATTERN.matcher(token);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid java version clause: " + token);
            }
            String op = m.group(1);
            int targetJava = Integer.parseInt(m.group(2));
            boolean match = switch (op != null ? op : "=") {
                case "=", "==" -> javaVersion == targetJava;
                case "!=" -> javaVersion != targetJava;
                case ">=" -> javaVersion >= targetJava;
                case ">" -> javaVersion > targetJava;
                case "<=" -> javaVersion <= targetJava;
                case "<" -> javaVersion < targetJava;
                default -> throw new IllegalArgumentException("Unsupported operator: " + op);
            };
            if (!match) return false;
        }
        return true;
    }

    public String expression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}
