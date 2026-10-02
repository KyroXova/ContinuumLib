package com.kyroxova.continuumlib.filter.registry;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.util.*;

public final class RegistryDeclarationScanner {

    public RegistryIndex scan(Collection<SourceUnit> units) {
        List<SourceUnit> sourceUnits = List.copyOf(units);
        GlobalConstants globalConstants = GlobalConstants.from(sourceUnits);
        List<RegistryEntry> entries = new ArrayList<>();
        for (SourceUnit unit : sourceUnits) {
            entries.addAll(scanUnit(unit, globalConstants));
        }
        return new RegistryIndex(entries);
    }

    public List<RegistryEntry> scanUnit(SourceUnit unit) {
        return scanUnit(unit, GlobalConstants.from(List.of(unit)));
    }

    private List<RegistryEntry> scanUnit(SourceUnit unit, GlobalConstants globalConstants) {
        List<RegistryEntry> entries = new ArrayList<>();
        CompilationUnit ast = unit.ast();
        String pkg = ast.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");

        for (ClassOrInterfaceDeclaration typeDecl : ast.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = typeDecl.getFullyQualifiedName().orElse(pkg + typeDecl.getNameAsString());
            ClassContext context = context(typeDecl, globalConstants);

            for (FieldDeclaration field : typeDecl.getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    inspectField(variable, className, unit.relativePath(), context, globalConstants)
                            .ifPresent(entries::add);
                }
            }
        }
        return entries;
    }

    private static ClassContext context(
            ClassOrInterfaceDeclaration type,
            GlobalConstants globalConstants
    ) {
        Map<String, String> constants = new HashMap<>();
        for (FieldDeclaration field : type.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                variable.getInitializer()
                        .flatMap(initializer -> literalString(initializer, constants, globalConstants))
                        .ifPresent(value -> constants.put(variable.getNameAsString(), value));
            }
        }

        Map<String, String> registryNamespaces = new HashMap<>();
        for (FieldDeclaration field : type.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                inferRegistryNamespace(variable, constants, globalConstants)
                        .ifPresent(namespace -> registryNamespaces.put(variable.getNameAsString(), namespace));
            }
        }
        return new ClassContext(Map.copyOf(constants), Map.copyOf(registryNamespaces));
    }

    private static Optional<String> inferRegistryNamespace(
            VariableDeclarator variable,
            Map<String, String> constants,
            GlobalConstants globalConstants
    ) {
        String type = variable.getType().asString().toLowerCase(Locale.ROOT).replace(" ", "");
        if (!type.contains("deferredregister")) return Optional.empty();
        if (variable.getInitializer().isEmpty()
                || !(variable.getInitializer().get() instanceof MethodCallExpr call)) {
            return Optional.empty();
        }

        String method = call.getNameAsString();
        if (!method.equals("create") && !method.equals("createEntries")) {
            return Optional.empty();
        }

        List<Expression> arguments = call.getArguments();
        for (int i = arguments.size() - 1; i >= 0; i--) {
            Optional<String> value = literalString(arguments.get(i), constants, globalConstants);
            if (value.isPresent() && !value.get().isBlank()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<RegistryEntry> inspectField(
            VariableDeclarator variable,
            String className,
            String sourcePath,
            ClassContext context,
            GlobalConstants globalConstants
    ) {
        if (variable.getInitializer().isEmpty()) return Optional.empty();

        Expression initializer = variable.getInitializer().get();
        if (!(initializer instanceof MethodCallExpr call) || !call.getNameAsString().equals("register")) {
            return Optional.empty();
        }

        RegistryType registryType = inferRegistryType(variable.getType().asString(), call);
        if (registryType == null) return Optional.empty();

        String fallbackNamespace = call.getScope()
                .filter(NameExpr.class::isInstance)
                .map(NameExpr.class::cast)
                .map(NameExpr::getNameAsString)
                .map(context.registryNamespaces()::get)
                .orElse(null);

        IdPair id = extractId(
                call.getArguments(),
                context.stringConstants(),
                globalConstants,
                fallbackNamespace
        );
        if (id == null) return Optional.empty();

        int line = variable.getRange().map(range -> range.begin.line).orElse(-1);
        return Optional.of(new RegistryEntry(
                registryType,
                id.namespace(),
                id.id(),
                className,
                variable.getNameAsString(),
                sourcePath,
                line
        ));
    }

    private record ClassContext(
            Map<String, String> stringConstants,
            Map<String, String> registryNamespaces
    ) {}

    private record IdPair(String namespace, String id) {}

    private IdPair extractId(
            List<Expression> arguments,
            Map<String, String> constants,
            GlobalConstants globalConstants,
            String fallbackNamespace
    ) {
        for (Expression argument : arguments) {
            Optional<String> direct = literalString(argument, constants, globalConstants);
            if (direct.isPresent()) {
                return parseId(direct.get(), fallbackNamespace);
            }

            if (argument instanceof ObjectCreationExpr creation) {
                IdPair pair = identifierArguments(
                        creation.getArguments(),
                        constants,
                        globalConstants,
                        fallbackNamespace
                );
                if (pair != null) return pair;
            }

            if (argument instanceof MethodCallExpr call && isIdentifierFactory(call.getNameAsString())) {
                IdPair pair = identifierArguments(
                        call.getArguments(),
                        constants,
                        globalConstants,
                        fallbackNamespace
                );
                if (pair != null) return pair;
            }
        }
        return null;
    }

    private static IdPair identifierArguments(
            List<Expression> arguments,
            Map<String, String> constants,
            GlobalConstants globalConstants,
            String fallbackNamespace
    ) {
        if (arguments.size() >= 2) {
            Optional<String> namespace = literalString(arguments.get(0), constants, globalConstants);
            Optional<String> path = literalString(arguments.get(1), constants, globalConstants);
            if (namespace.isPresent() && path.isPresent()) {
                return new IdPair(namespace.get(), path.get());
            }
        }

        if (arguments.size() == 1) {
            return literalString(arguments.get(0), constants, globalConstants)
                    .map(value -> parseId(value, fallbackNamespace))
                    .orElse(null);
        }
        return null;
    }

    private static boolean isIdentifierFactory(String name) {
        return name.equals("of")
                || name.equals("parse")
                || name.equals("tryParse")
                || name.equals("fromNamespaceAndPath");
    }

    private static IdPair parseId(String value, String fallbackNamespace) {
        int colon = value.indexOf(':');
        if (colon >= 0) {
            return new IdPair(value.substring(0, colon), value.substring(colon + 1));
        }
        return new IdPair(fallbackNamespace, value);
    }

    private static Optional<String> literalString(
            Expression expression,
            Map<String, String> constants,
            GlobalConstants globalConstants
    ) {
        if (expression instanceof StringLiteralExpr string) {
            return Optional.of(string.getValue());
        }
        if (expression instanceof NameExpr name) {
            return Optional.ofNullable(constants.get(name.getNameAsString()));
        }
        if (expression instanceof FieldAccessExpr access) {
            return globalConstants.lookup(access.getScope().toString(), access.getNameAsString());
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Optional<String> left = literalString(binary.getLeft(), constants, globalConstants);
            Optional<String> right = literalString(binary.getRight(), constants, globalConstants);
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(left.get() + right.get());
            }
        }
        return Optional.empty();
    }

    private RegistryType inferRegistryType(String fieldType, MethodCallExpr call) {
        RegistryType fromType = inferFromTypeString(fieldType);
        if (fromType != null) return fromType;

        if (call.getScope().isPresent()) {
            RegistryType fromScope = inferFromRegistryText(
                    call.getScope().get().toString().toLowerCase(Locale.ROOT)
            );
            if (fromScope != null) return fromScope;
        }

        if (!call.getArguments().isEmpty()) {
            RegistryType fromArgument = inferFromRegistryText(
                    call.getArguments().get(0).toString().toLowerCase(Locale.ROOT)
            );
            if (fromArgument != null) return fromArgument;
        }

        return RegistryType.CUSTOM_REGISTRY_ENTRY;
    }

    private static RegistryType inferFromRegistryText(String text) {
        if (text.contains("block_entity") || text.contains("blockentity")) return RegistryType.BLOCK_ENTITY;
        if (text.contains("block")) return RegistryType.BLOCK;
        if (text.contains("item")) return RegistryType.ITEM;
        if (text.contains("entity")) return RegistryType.ENTITY_TYPE;
        if (text.contains("fluid")) return RegistryType.FLUID;
        if (text.contains("sound")) return RegistryType.SOUND_EVENT;
        if (text.contains("particle")) return RegistryType.PARTICLE;
        if (text.contains("menu")) return RegistryType.MENU;
        if (text.contains("recipe")) return RegistryType.RECIPE_TYPE;
        if (text.contains("enchantment")) return RegistryType.ENCHANTMENT;
        if (text.contains("effect")) return RegistryType.EFFECT;
        if (text.contains("attribute")) return RegistryType.ATTRIBUTE;
        return null;
    }

    private RegistryType inferFromTypeString(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        if (lower.contains("blockentity") || lower.contains("block_entity")) return RegistryType.BLOCK_ENTITY;
        if (lower.contains("deferredblock") || lower.contains("<block>") || lower.endsWith("block")) return RegistryType.BLOCK;
        if (lower.contains("deferreditem") || lower.contains("<item>") || lower.endsWith("item")) return RegistryType.ITEM;
        if (lower.contains("entitytype") || lower.contains("<entitytype")) return RegistryType.ENTITY_TYPE;
        if (lower.contains("<fluid>") || lower.endsWith("fluid")) return RegistryType.FLUID;
        if (lower.contains("soundevent")) return RegistryType.SOUND_EVENT;
        if (lower.contains("particletype")) return RegistryType.PARTICLE;
        if (lower.contains("menutype")) return RegistryType.MENU;
        if (lower.contains("recipeserializer")) return RegistryType.RECIPE_SERIALIZER;
        if (lower.contains("recipetype")) return RegistryType.RECIPE_TYPE;
        if (lower.contains("enchantment")) return RegistryType.ENCHANTMENT;
        if (lower.contains("mobeffect") || lower.contains("effect")) return RegistryType.EFFECT;
        if (lower.contains("attribute")) return RegistryType.ATTRIBUTE;
        return null;
    }

    private static final class GlobalConstants {
        private final Map<String, String> qualified;
        private final Map<String, String> uniqueSuffixes;

        private GlobalConstants(Map<String, String> qualified, Map<String, String> uniqueSuffixes) {
            this.qualified = qualified;
            this.uniqueSuffixes = uniqueSuffixes;
        }

        static GlobalConstants from(Collection<SourceUnit> units) {
            Map<String, String> qualified = new HashMap<>();
            for (SourceUnit unit : units) {
                String pkg = unit.ast().getPackageDeclaration()
                        .map(declaration -> declaration.getNameAsString() + ".")
                        .orElse("");
                for (ClassOrInterfaceDeclaration type : unit.ast().findAll(ClassOrInterfaceDeclaration.class)) {
                    String owner = type.getFullyQualifiedName().orElse(pkg + type.getNameAsString());
                    for (FieldDeclaration field : type.getFields()) {
                        for (VariableDeclarator variable : field.getVariables()) {
                            variable.getInitializer()
                                    .flatMap(RegistryDeclarationScanner::directLiteralString)
                                    .ifPresent(value -> qualified.put(owner + "." + variable.getNameAsString(), value));
                        }
                    }
                }
            }

            Map<String, List<String>> suffixValues = new HashMap<>();
            for (var entry : qualified.entrySet()) {
                String key = entry.getKey();
                int fieldDot = key.lastIndexOf('.');
                if (fieldDot <= 0) continue;
                String owner = key.substring(0, fieldDot);
                String field = key.substring(fieldDot + 1);
                int ownerDot = owner.lastIndexOf('.');
                String simpleOwner = ownerDot >= 0 ? owner.substring(ownerDot + 1) : owner;
                suffixValues.computeIfAbsent(simpleOwner + "." + field, ignored -> new ArrayList<>())
                        .add(entry.getValue());
            }

            Map<String, String> uniqueSuffixes = new HashMap<>();
            for (var entry : suffixValues.entrySet()) {
                List<String> values = entry.getValue().stream().distinct().toList();
                if (values.size() == 1) {
                    uniqueSuffixes.put(entry.getKey(), values.get(0));
                }
            }
            return new GlobalConstants(Map.copyOf(qualified), Map.copyOf(uniqueSuffixes));
        }

        Optional<String> lookup(String owner, String field) {
            String key = owner + "." + field;
            String exact = qualified.get(key);
            if (exact != null) return Optional.of(exact);

            String suffix = uniqueSuffixes.get(key);
            if (suffix != null) return Optional.of(suffix);

            List<String> matches = qualified.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("." + key))
                    .map(Map.Entry::getValue)
                    .distinct()
                    .toList();
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        }
    }

    private static Optional<String> directLiteralString(Expression expression) {
        if (expression instanceof StringLiteralExpr string) {
            return Optional.of(string.getValue());
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Optional<String> left = directLiteralString(binary.getLeft());
            Optional<String> right = directLiteralString(binary.getRight());
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(left.get() + right.get());
            }
        }
        return Optional.empty();
    }
}
