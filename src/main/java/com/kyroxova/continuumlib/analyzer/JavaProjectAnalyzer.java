package com.kyroxova.continuumlib.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.kyroxova.continuumlib.model.environment.EnvironmentId;
import com.kyroxova.continuumlib.model.environment.Loader;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.diagnostic.Severity;
import com.kyroxova.continuumlib.model.operation.BlockProperties;
import com.kyroxova.continuumlib.model.operation.DeclareRegistry;
import com.kyroxova.continuumlib.model.operation.RegisterBlock;
import com.kyroxova.continuumlib.model.operation.SemanticOperation;
import com.kyroxova.continuumlib.model.operation.SourceExpression;
import com.kyroxova.continuumlib.model.project.ProjectModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JavaProjectAnalyzer {
    public ProjectModel analyze(Path sourceRoot, EnvironmentId environment) throws IOException {
        if (!environment.minecraftVersion().equals("1.18.2") || environment.loader() != Loader.FORGE) {
            throw new IllegalArgumentException("No Java analyzer is registered for " + environment);
        }
        List<SemanticOperation> operations = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                analyzeFile(file, operations, diagnostics);
            }
        }
        return new ProjectModel(operations, diagnostics);
    }

    private void analyzeFile(Path file, List<SemanticOperation> operations, List<Diagnostic> diagnostics) throws IOException {
        var unit = StaticJavaParser.parse(file);
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            field.getVariables().forEach(variable -> variable.getInitializer().ifPresent(initializer -> {
                if (initializer.isMethodCallExpr()) {
                    MethodCallExpr call = initializer.asMethodCallExpr();
                    if (isRegistryDeclaration(call)) {
                        operations.add(readRegistry(variable.getNameAsString(), call));
                    } else if (call.getNameAsString().equals("register")
                            && call.getArguments().size() >= 2
                            && field.getElementType().asString().contains("Block")) {
                        var block = readBlock(variable.getNameAsString(), call);
                        if (block.isPresent()) {
                            operations.add(block.get());
                        } else {
                            diagnostics.add(new Diagnostic(
                                    DiagnosticCode.UNRESOLVED_OPERATION,
                                    Severity.ERROR,
                                    "Cannot safely resolve block registration field " + variable.getNameAsString()
                                            + " in " + file));
                        }
                    }
                }
            }));
        }
    }

    private boolean isRegistryDeclaration(MethodCallExpr call) {
        return call.getNameAsString().equals("create")
                && call.getScope().map(Object::toString).orElse("").endsWith("DeferredRegister")
                && call.getArguments().size() >= 2;
    }

    private DeclareRegistry readRegistry(String fieldName, MethodCallExpr call) {
        String registry = call.getArgument(0).toString();
        String kind = registry.substring(registry.lastIndexOf('.') + 1).replaceAll("S$", "");
        return new DeclareRegistry(fieldName, kind, expression(call.getArgument(1)));
    }

    private java.util.Optional<RegisterBlock> readBlock(String fieldName, MethodCallExpr call) {
        if (!call.getArgument(0).isStringLiteralExpr() || !call.getArgument(1).isLambdaExpr()) {
            return java.util.Optional.empty();
        }
        LambdaExpr lambda = call.getArgument(1).asLambdaExpr();
        Expression body = lambda.getExpressionBody().orElse(null);
        if (body == null || !body.isObjectCreationExpr()) {
            return java.util.Optional.empty();
        }
        ObjectCreationExpr creation = body.asObjectCreationExpr();
        if (creation.getArguments().isEmpty()) {
            return java.util.Optional.empty();
        }
        Expression propertiesExpression = creation.getArgument(creation.getArguments().size() - 1);
        BlockProperties properties = readProperties(propertiesExpression);
        String registryField = call.getScope().map(Object::toString).orElse("");
        return java.util.Optional.of(new RegisterBlock(
                registryField,
                fieldName,
                call.getArgument(0).asStringLiteralExpr().asString(),
                creation.getTypeAsString(),
                expression(body),
                properties));
    }

    private BlockProperties readProperties(Expression expression) {
        SourceExpression material = null;
        SourceExpression mapColor = null;
        Double strength = null;
        SourceExpression sound = null;
        for (MethodCallExpr call : expression.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("of") && call.getArguments().size() >= 2) {
                material = expression(call.getArgument(0));
                mapColor = expression(call.getArgument(1));
            } else if (call.getNameAsString().equals("strength") && !call.getArguments().isEmpty()) {
                strength = Double.parseDouble(call.getArgument(0).toString().replaceAll("[fFdD]$", ""));
            } else if (call.getNameAsString().equals("sound") && !call.getArguments().isEmpty()) {
                sound = expression(call.getArgument(0));
            }
        }
        return new BlockProperties(material, mapColor, strength, sound);
    }

    private SourceExpression expression(Expression expression) {
        return new SourceExpression(expression.toString());
    }
}
