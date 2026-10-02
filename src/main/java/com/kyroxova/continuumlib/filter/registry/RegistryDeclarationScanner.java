package com.kyroxova.continuumlib.filter.registry;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.util.*;

public final class RegistryDeclarationScanner {

    public RegistryIndex scan(Collection<SourceUnit> units) {
        List<RegistryEntry> entries = new ArrayList<>();
        for (SourceUnit unit : units) {
            entries.addAll(scanUnit(unit));
        }
        return new RegistryIndex(entries);
    }

    public List<RegistryEntry> scanUnit(SourceUnit unit) {
        List<RegistryEntry> entries = new ArrayList<>();
        CompilationUnit ast = unit.ast();
        String pkg = ast.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("");

        for (ClassOrInterfaceDeclaration typeDecl : ast.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = typeDecl.getFullyQualifiedName().orElse(pkg + typeDecl.getNameAsString());
            ClassContext context = context(typeDecl);

            for (FieldDeclaration field : typeDecl.getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    inspectField(variable, className, unit.relativePath(), context)
                            .ifPresent(entries::add);
                }
            }
        }
        return entries;
    }

    private static ClassContext context(ClassOrInterfaceDeclaration type) {
        Map<String, String> constants = new HashMap<>();
        for (FieldDeclaration field : type.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                variable.getInitializer()
                        .flatMap(initializer -> literalString(initializer, constants))
                        .ifPresent(value -> constants.put(variable.getNameAsString(), value));
            }
        }

        Map<String, String> registryNamespaces = new HashMap<>();
        for (FieldDeclaration field : type.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                inferRegistryNamespace(variable, constants)
                        .ifPresent(namespace -> registryNamespaces.put(variable.getNameAsString(), namespace));
            }
        }
        return new ClassContext(Map.copyOf(constants), Map.copyOf(registryNamespaces));
    }

    private static Optional<String> inferRegistryNamespace(
            VariableDeclarator variable,
            Map<String, String> constants
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
            Optional<String> value = literalString(arguments.get(i), constants);
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
            ClassContext context
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

        IdPair id = extractId(call.getArguments(), context.stringConstants(), fallbackNamespace);
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
            String fallbackNamespace
    ) {
        for (Expression argument : arguments) {
            Optional<String> direct = literalString(argument, constants);
            if (direct.isPresent()) {
                return parseId(direct.get(), fallbackNamespace);
            }

            if (argument instanceof ObjectCreationExpr creation) {
                IdPair pair = identifierArguments(creation.getArguments(), constants, fallbackNamespace);
                if (pair != null) return pair;
            }

            if (argument instanceof MethodCallExpr call && isIdentifierFactory(call.getNameAsString())) {
                IdPair pair = identifierArguments(call.getArguments(), constants, fallbackNamespace);
                if (pair != null) return pair;
            }
        }
        return null;
    }

    private static IdPair identifierArguments(
            List<Expression> arguments,
            Map<String, String> constants,
            String fallbackNamespace
    ) {
        if (arguments.size() >= 2) {
            Optional<String> namespace = literalString(arguments.get(0), constants);
            Optional<String> path = literalString(arguments.get(1), constants);
            if (namespace.isPresent() && path.isPresent()) {
                return new IdPair(namespace.get(), path.get());
            }
        }

        if (arguments.size() == 1) {
            return literalString(arguments.get(0), constants)
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
            Map<String, String> constants
    ) {
        if (expression instanceof StringLiteralExpr string) {
            return Optional.of(string.getValue());
        }
        if (expression instanceof NameExpr name) {
            return Optional.ofNullable(constants.get(name.getNameAsString()));
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Optional<String> left = literalString(binary.getLeft(), constants);
            Optional<String> right = literalString(binary.getRight(), constants);
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
            String scopeName = call.getScope().get().toString().toLowerCase(Locale.ROOT);
            RegistryType fromScope = inferFromRegistryText(scopeName);
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
}
