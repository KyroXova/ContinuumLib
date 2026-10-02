package com.kyroxova.continuumlib.filter.config;

import com.google.gson.*;
import com.kyroxova.continuumlib.filter.condition.EnvironmentCondition;
import com.kyroxova.continuumlib.filter.condition.VersionConstraint;
import com.kyroxova.continuumlib.filter.domain.FilterDomain;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.filter.rule.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Discovers and parses inclusion and exclusion rule configurations recursively.
 */
public final class FilterConfigurationReader {
    private static final Gson GSON = new Gson();
    private static final Set<String> RULE_KEYS = Set.of(
            "domain", "type", "id", "class", "className", "path", "when"
    );
    private static final Set<String> CONDITION_KEYS = Set.of(
            "minecraft", "loader", "loader_version", "loaderVersion",
            "java", "java_version", "namespace", "output_mode", "outputMode"
    );

    public ContinuumProjectConfiguration load(Path configurationRoot) throws IOException {
        if (configurationRoot == null || !Files.exists(configurationRoot)) {
            return ContinuumProjectConfiguration.empty(configurationRoot);
        }
        if (!Files.isDirectory(configurationRoot)) {
            throw new FilterConfigurationException(
                    "ContinuumLib filter configuration root is not a directory: " + configurationRoot
            );
        }

        Path inclusionsDir = configurationRoot.resolve("inclusions");
        Path exclusionsDir = configurationRoot.resolve("exclusions");

        InclusionRuleSet inclusions = loadInclusions(inclusionsDir);
        ExclusionRuleSet exclusions = loadExclusions(exclusionsDir);

        validateNoContradictions(inclusions, exclusions);

        return new ContinuumProjectConfiguration(configurationRoot, inclusions, exclusions);
    }

    public InclusionRuleSet loadInclusions(Path inclusionsDir) throws IOException {
        if (inclusionsDir == null || !Files.exists(inclusionsDir) || !Files.isDirectory(inclusionsDir)) {
            return InclusionRuleSet.EMPTY;
        }
        RuleSet.Builder builder = RuleSet.builder();
        List<Path> jsonFiles = findJsonFiles(inclusionsDir);
        for (Path file : jsonFiles) {
            RuleSet rules = parseRuleFile(file);
            builder.addAll(rules);
        }
        return new InclusionRuleSet(builder.build());
    }

    public ExclusionRuleSet loadExclusions(Path exclusionsDir) throws IOException {
        if (exclusionsDir == null || !Files.exists(exclusionsDir) || !Files.isDirectory(exclusionsDir)) {
            return ExclusionRuleSet.EMPTY;
        }
        RuleSet.Builder builder = RuleSet.builder();
        List<Path> jsonFiles = findJsonFiles(exclusionsDir);
        for (Path file : jsonFiles) {
            RuleSet rules = parseRuleFile(file);
            builder.addAll(rules);
        }
        return new ExclusionRuleSet(builder.build());
    }

    public RuleSet parseRuleFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root;
            try {
                root = JsonParser.parseReader(reader);
            } catch (JsonParseException e) {
                throw new FilterConfigurationException("Malformed JSON in " + file + ": " + e.getMessage(), e);
            }

            if (root == null || root.isJsonNull()) {
                return RuleSet.EMPTY;
            }

            RuleSet.Builder builder = RuleSet.builder();
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("rules")) {
                    validateKeys(obj, Set.of("rules"), file, "root");
                    if (!obj.get("rules").isJsonArray()) {
                        throw new FilterConfigurationException("'rules' must be an array in " + file);
                    }
                    JsonArray rules = obj.getAsJsonArray("rules");
                    for (int i = 0; i < rules.size(); i++) {
                        parseRuleElement(rules.get(i), file, i, builder);
                    }
                } else {
                    // Single rule object
                    parseRuleElement(obj, file, 0, builder);
                }
            } else if (root.isJsonArray()) {
                JsonArray rules = root.getAsJsonArray();
                for (int i = 0; i < rules.size(); i++) {
                    parseRuleElement(rules.get(i), file, i, builder);
                }
            } else {
                throw new FilterConfigurationException("Invalid JSON root in " + file + ": expected JSON object or array");
            }

            return builder.build();
        }
    }

    private void parseRuleElement(JsonElement elem, Path file, int index, RuleSet.Builder builder) {
        if (!elem.isJsonObject()) {
            throw new FilterConfigurationException("Invalid rule in " + file + " (rule #" + index + "): expected JSON object");
        }
        JsonObject obj = elem.getAsJsonObject();
        validateKeys(obj, RULE_KEYS, file, "rule #" + index);

        EnvironmentCondition condition = parseCondition(obj.get("when"), file, index);
        String explicitDomain = getString(obj, "domain");
        FilterDomain domain = explicitDomain == null
                ? inferRuleDomain(obj, file, index)
                : parseExplicitDomain(explicitDomain, file, index);

        switch (domain) {
            case REGISTRY -> {
                rejectPresent(obj, file, index, "path", "class", "className");
                String typeStr = requireString(obj, "type", file, index);
                String id = requireString(obj, "id", file, index);
                builder.addRegistry(new RegistryFilterRule(RegistryType.from(typeStr), id, condition, file));
            }
            case CLASS -> {
                rejectPresent(obj, file, index, "type", "id", "path");
                String className = aliasedString(obj, "class", "className", file, index);
                if (className == null || className.isBlank()) {
                    throw new FilterConfigurationException("Missing 'class' in " + file + " (rule #" + index + ")");
                }
                builder.addClass(new ClassFilterRule(className, condition, file));
            }
            case SOURCE, RESOURCE -> {
                rejectPresent(obj, file, index, "type", "id", "class", "className");
                String path = requireString(obj, "path", file, index);
                if (domain == FilterDomain.SOURCE) {
                    builder.addSource(new SourceFilterRule(path, condition, file));
                } else {
                    builder.addResource(new ResourceFilterRule(path, condition, file));
                }
            }
        }
    }

    private static FilterDomain inferRuleDomain(JsonObject obj, Path file, int index) {
        boolean registry = obj.has("type") || obj.has("id");
        boolean clazz = obj.has("class") || obj.has("className");
        boolean path = obj.has("path");
        int candidates = (registry ? 1 : 0) + (clazz ? 1 : 0) + (path ? 1 : 0);
        if (candidates != 1) {
            throw new FilterConfigurationException(
                    "Cannot determine one filter rule domain in " + file + " (rule #" + index + ")"
            );
        }
        if (registry) return FilterDomain.REGISTRY;
        if (clazz) return FilterDomain.CLASS;
        return resolveInferredPathDomain(getString(obj, "path"), file);
    }

    private static FilterDomain parseExplicitDomain(String value, Path file, int index) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "registry" -> FilterDomain.REGISTRY;
            case "class" -> FilterDomain.CLASS;
            case "source" -> FilterDomain.SOURCE;
            case "resource" -> FilterDomain.RESOURCE;
            default -> throw new FilterConfigurationException(
                    "Unknown filter domain '" + value + "' in " + file + " (rule #" + index + ")"
            );
        };
    }

    private static String requireString(JsonObject obj, String member, Path file, int index) {
        String value = getString(obj, member);
        if (value == null || value.isBlank()) {
            throw new FilterConfigurationException(
                    "Missing '" + member + "' in " + file + " (rule #" + index + ")"
            );
        }
        return value;
    }

    private static void rejectPresent(
            JsonObject obj,
            Path file,
            int index,
            String... members
    ) {
        for (String member : members) {
            if (obj.has(member)) {
                throw new FilterConfigurationException(
                        "Field '" + member + "' is incompatible with domain '"
                                + getString(obj, "domain") + "' in " + file + " (rule #" + index + ")"
                );
            }
        }
    }

    private static String aliasedString(
            JsonObject obj,
            String first,
            String second,
            Path file,
            int index
    ) {
        boolean hasFirst = obj.has(first) && !obj.get(first).isJsonNull();
        boolean hasSecond = obj.has(second) && !obj.get(second).isJsonNull();
        if (hasFirst && hasSecond) {
            throw new FilterConfigurationException(
                    "Use only one of '" + first + "' or '" + second + "' in "
                            + file + " (rule #" + index + ")"
            );
        }
        return hasFirst ? getString(obj, first) : getString(obj, second);
    }

    private static FilterDomain resolveInferredPathDomain(String path, Path file) {
        if (path == null || path.isBlank()) {
            throw new FilterConfigurationException("Missing 'path' in " + file);
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".java")) return FilterDomain.SOURCE;
        if (lowerPath.startsWith("assets/") || lowerPath.startsWith("data/")) return FilterDomain.RESOURCE;

        String filePath = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (filePath.contains("/source/")) return FilterDomain.SOURCE;
        if (filePath.contains("/resource") || filePath.contains("/resources/")) return FilterDomain.RESOURCE;
        return FilterDomain.RESOURCE;
    }

    private EnvironmentCondition parseCondition(JsonElement whenElem, Path file, int index) {
        if (whenElem == null || whenElem.isJsonNull()) {
            return EnvironmentCondition.ALWAYS;
        }
        if (!whenElem.isJsonObject()) {
            throw new FilterConfigurationException("Invalid 'when' condition in " + file + " (rule #" + index + "): expected JSON object");
        }
        JsonObject when = whenElem.getAsJsonObject();
        validateKeys(when, CONDITION_KEYS, file, "rule #" + index + " when");
        String mc = getString(when, "minecraft");
        String loader = getString(when, "loader");
        String loaderVer = aliasedString(when, "loader_version", "loaderVersion", file, index);
        String java = aliasedString(when, "java", "java_version", file, index);
        String namespace = getString(when, "namespace");
        String outputMode = aliasedString(when, "output_mode", "outputMode", file, index);

        // Validate version constraint syntax upfront
        if (mc != null && !mc.isBlank()) {
            try {
                VersionConstraint.parse(mc);
            } catch (IllegalArgumentException e) {
                throw new FilterConfigurationException("Invalid Minecraft version expression '" + mc + "' in " + file + " (rule #" + index + "): " + e.getMessage(), e);
            }
        }
        if (loaderVer != null && !loaderVer.isBlank()) {
            try {
                VersionConstraint.parse(loaderVer);
            } catch (IllegalArgumentException e) {
                throw new FilterConfigurationException("Invalid loader version expression '" + loaderVer + "' in " + file + " (rule #" + index + "): " + e.getMessage(), e);
            }
        }

        return new EnvironmentCondition(mc, loader, loaderVer, java, namespace, outputMode);
    }

    private static String getString(JsonObject obj, String member) {
        if (!obj.has(member) || obj.get(member).isJsonNull()) return null;
        JsonElement value = obj.get(member);
        if (!value.isJsonPrimitive()) {
            throw new FilterConfigurationException("Expected scalar value for '" + member + "'");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString() && !primitive.isNumber()) {
            throw new FilterConfigurationException("Expected string/number value for '" + member + "'");
        }
        return primitive.getAsString().trim();
    }

    private static void validateKeys(
            JsonObject object,
            Set<String> allowed,
            Path file,
            String context
    ) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new FilterConfigurationException(
                        "Unknown key '" + key + "' in " + file + " (" + context + ")"
                );
            }
        }
    }

    private static List<Path> findJsonFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private void validateNoContradictions(InclusionRuleSet inclusions, ExclusionRuleSet exclusions) {
        if (inclusions.isEmpty() || exclusions.isEmpty()) {
            return;
        }
        for (var inc : inclusions.rules().allRules()) {
            for (var exc : exclusions.rules().allRules()) {
                if (inc.domain() == exc.domain() && inc.targetIdentifier().equals(exc.targetIdentifier())) {
                    if (inc.condition().equals(exc.condition())) {
                        throw new FilterConfigurationException("Contradictory rules for " + inc.domain() + " " + inc.targetIdentifier()
                                + ": defined in both inclusions (" + inc.sourceFile() + ") and exclusions (" + exc.sourceFile() + ")");
                    }
                }
            }
        }
    }
}
