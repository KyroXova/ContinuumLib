package com.kyroxova.continuumlib.filter.registry;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.kyroxova.continuumlib.filter.domain.RegistryType;
import com.kyroxova.continuumlib.source.ast.SourceUnit;

import java.util.*;

/**
 * Scans Java ASTs for Minecraft registry declarations without relying on simple string search.
 */
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

        for (var typeDecl : ast.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = pkg + typeDecl.getNameAsString();

            for (FieldDeclaration field : typeDecl.getFields()) {
                for (VariableDeclarator varDecl : field.getVariables()) {
                    Optional<RegistryEntry> entry = inspectField(varDecl, className, unit.relativePath());
                    entry.ifPresent(entries::add);
                }
            }
        }
        return entries;
    }

    private Optional<RegistryEntry> inspectField(VariableDeclarator varDecl, String className, String sourcePath) {
        if (varDecl.getInitializer().isEmpty()) {
            return Optional.empty();
        }
        Expression init = varDecl.getInitializer().get();
        String fieldName = varDecl.getNameAsString();
        int line = varDecl.getRange().map(r -> r.begin.line).orElse(-1);
        String fieldType = varDecl.getType().asString();

        // Pattern 1: Method call .register(...) (DeferredRegister, Registry.register, etc.)
        if (init instanceof MethodCallExpr call && call.getNameAsString().equals("register")) {
            RegistryType regType = inferRegistryType(fieldType, call);
            IdPair idPair = extractId(call.getArguments());
            if (idPair != null && regType != null) {
                return Optional.of(new RegistryEntry(regType, idPair.namespace(), idPair.id(), className, fieldName, sourcePath, line));
            }
        }

        // Registry containers describe where entries go; they are not entries themselves.
        if (isRegistryContainerType(fieldType)) {
            return Optional.empty();
        }

        // Pattern 2: Direct assignment of registry type (e.g. Block TEST = new TestBlock();)
        RegistryType directType = inferFromTypeString(fieldType);
        if (directType != null) {
            // Check if field has an id or constructor with id
            if (init instanceof ObjectCreationExpr objInit && !objInit.getArguments().isEmpty()) {
                IdPair idPair = extractId(objInit.getArguments());
                if (idPair != null) {
                    return Optional.of(new RegistryEntry(directType, idPair.namespace(), idPair.id(), className, fieldName, sourcePath, line));
                }
            }
            // Fallback: derive id from field name lowercased
            String derivedId = fieldName.toLowerCase(Locale.ROOT);
            return Optional.of(new RegistryEntry(directType, null, derivedId, className, fieldName, sourcePath, line));
        }

        return Optional.empty();
    }

    private record IdPair(String namespace, String id) {}

    private IdPair extractId(List<Expression> arguments) {
        for (Expression arg : arguments) {
            if (arg instanceof StringLiteralExpr str) {
                String val = str.getValue();
                int colon = val.indexOf(':');
                if (colon >= 0) {
                    return new IdPair(val.substring(0, colon), val.substring(colon + 1));
                }
                return new IdPair(null, val);
            } else if (arg instanceof ObjectCreationExpr creation) {
                // e.g. new Identifier("mod", "id") or new ResourceLocation("mod", "id")
                var args = creation.getArguments();
                if (args.size() >= 2 && args.get(0) instanceof StringLiteralExpr ns && args.get(1) instanceof StringLiteralExpr path) {
                    return new IdPair(ns.getValue(), path.getValue());
                } else if (args.size() == 1 && args.get(0) instanceof StringLiteralExpr str) {
                    String val = str.getValue();
                    int colon = val.indexOf(':');
                    return colon >= 0 ? new IdPair(val.substring(0, colon), val.substring(colon + 1)) : new IdPair(null, val);
                }
            } else if (arg instanceof MethodCallExpr call && (call.getNameAsString().equals("of") || call.getNameAsString().equals("fromNamespaceAndPath"))) {
                var args = call.getArguments();
                if (args.size() >= 2 && args.get(0) instanceof StringLiteralExpr ns && args.get(1) instanceof StringLiteralExpr path) {
                    return new IdPair(ns.getValue(), path.getValue());
                }
            }
        }
        return null;
    }

    private RegistryType inferRegistryType(String fieldType, MethodCallExpr call) {
        RegistryType fromType = inferFromTypeString(fieldType);
        if (fromType != null) return fromType;

        // Try checking call scope (e.g. BLOCKS.register(...))
        if (call.getScope().isPresent()) {
            String scopeName = call.getScope().get().toString().toLowerCase(Locale.ROOT);
            if (scopeName.contains("block_entity") || scopeName.contains("blockentity")) return RegistryType.BLOCK_ENTITY;
            if (scopeName.contains("block")) return RegistryType.BLOCK;
            if (scopeName.contains("item")) return RegistryType.ITEM;
            if (scopeName.contains("entity")) return RegistryType.ENTITY_TYPE;
            if (scopeName.contains("fluid")) return RegistryType.FLUID;
            if (scopeName.contains("sound")) return RegistryType.SOUND_EVENT;
            if (scopeName.contains("particle")) return RegistryType.PARTICLE;
            if (scopeName.contains("menu")) return RegistryType.MENU;
            if (scopeName.contains("recipe")) return RegistryType.RECIPE_TYPE;
            if (scopeName.contains("enchantment")) return RegistryType.ENCHANTMENT;
            if (scopeName.contains("effect")) return RegistryType.EFFECT;
            if (scopeName.contains("attribute")) return RegistryType.ATTRIBUTE;
        }

        // Try checking first argument if Registry.register(BuiltInRegistries.BLOCK, ...)
        if (!call.getArguments().isEmpty()) {
            String firstArg = call.getArguments().get(0).toString().toLowerCase(Locale.ROOT);
            if (firstArg.contains("block_entity")) return RegistryType.BLOCK_ENTITY;
            if (firstArg.contains("block")) return RegistryType.BLOCK;
            if (firstArg.contains("item")) return RegistryType.ITEM;
            if (firstArg.contains("entity")) return RegistryType.ENTITY_TYPE;
            if (firstArg.contains("fluid")) return RegistryType.FLUID;
            if (firstArg.contains("sound")) return RegistryType.SOUND_EVENT;
            if (firstArg.contains("particle")) return RegistryType.PARTICLE;
            if (firstArg.contains("menu")) return RegistryType.MENU;
            if (firstArg.contains("recipe")) return RegistryType.RECIPE_TYPE;
            if (firstArg.contains("enchantment")) return RegistryType.ENCHANTMENT;
            if (firstArg.contains("effect")) return RegistryType.EFFECT;
            if (firstArg.contains("attribute")) return RegistryType.ATTRIBUTE;
        }

        return RegistryType.CUSTOM_REGISTRY_ENTRY;
    }


    private static boolean isRegistryContainerType(String type) {
        String lower = type.toLowerCase(Locale.ROOT).replace(" ", "");
        return lower.contains("deferredregister<")
                || lower.startsWith("registry<")
                || lower.contains(".registry<");
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
        if (lower.contains("recipetype")) return RegistryType.RECIPE_TYPE;
        if (lower.contains("recipeserializer")) return RegistryType.RECIPE_SERIALIZER;
        if (lower.contains("enchantment")) return RegistryType.ENCHANTMENT;
        if (lower.contains("mobeffect") || lower.contains("effect")) return RegistryType.EFFECT;
        if (lower.contains("attribute")) return RegistryType.ATTRIBUTE;
        return null;
    }
}
