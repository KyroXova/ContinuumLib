package com.kyroxova.continuumlib.source.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.rule.SourceMigrationRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class SourceTransformer {
    private final SourceMigrationPlan plan;

    public SourceTransformer(SourceMigrationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public List<Path> transformAndWrite(List<SourceUnit> sourceUnits, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        List<Path> generatedFiles = new ArrayList<>();

        for (SourceUnit unit : sourceUnits) {
            CompilationUnit astCopy = unit.ast().clone();
            transformAst(astCopy);

            Path targetFile = outputDir.resolve(unit.relativePath()).toAbsolutePath().normalize();
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, astCopy.toString(), StandardCharsets.UTF_8);
            generatedFiles.add(targetFile);
        }

        return Collections.unmodifiableList(generatedFiles);
    }

    public CompilationUnit transformAst(CompilationUnit ast) {
        // Collect imports and simple name mappings before modifying imports
        Map<String, String> simpleToQualified = new HashMap<>();
        for (ImportDeclaration imp : ast.getImports()) {
            String qName = imp.getNameAsString();
            int dot = qName.lastIndexOf('.');
            String simple = dot >= 0 ? qName.substring(dot + 1) : qName;
            simpleToQualified.put(simple, qName);
        }

        // Update imports for class renames
        for (ImportDeclaration imp : new ArrayList<>(ast.getImports())) {
            String qName = imp.getNameAsString();
            var rename = plan.findClassRename(qName);
            if (rename.isPresent()) {
                imp.setName(rename.get());
            }
        }

        ast.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceType n, Void arg) {
                String name = n.getNameAsString();
                String qualified = resolveQualified(name, simpleToQualified);
                var rename = plan.findClassRename(qualified);
                if (rename.isPresent()) {
                    String targetQ = rename.get();
                    String simpleTarget = simpleName(targetQ);
                    n.setName(simpleTarget);
                    ensureImport(ast, targetQ);
                }
                return super.visit(n, arg);
            }

            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);

                String typeName = n.getType().getNameAsString();
                String qualified = resolveQualified(typeName, simpleToQualified);

                // Check constructor-to-factory rule
                var c2f = plan.findConstructorToFactory(qualified);
                if (c2f.isPresent()) {
                    SourceMigrationRule.ConstructorToFactory rule = c2f.get();
                    String factoryClassSimple = simpleName(rule.factoryOwner());
                    ensureImport(ast, rule.factoryOwner().replace('/', '.'));

                    MethodCallExpr factoryCall = new MethodCallExpr(
                            new NameExpr(factoryClassSimple),
                            rule.factoryMethod(),
                            n.getArguments()
                    );
                    return factoryCall;
                }

                // Check class rename
                var classRename = plan.findClassRename(qualified);
                if (classRename.isPresent()) {
                    String newClassSimple = simpleName(classRename.get());
                    n.getType().setName(newClassSimple);
                    ensureImport(ast, classRename.get());
                }

                return n;
            }

            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);

                String methodName = n.getNameAsString();
                Optional<Expression> scope = n.getScope();

                if (scope.isPresent() && scope.get() instanceof NameExpr scopeName) {
                    String scopeText = scopeName.getNameAsString();

                    // 1. Static call: class scope
                    if (simpleToQualified.containsKey(scopeText) || (!scopeText.isEmpty() && Character.isUpperCase(scopeText.charAt(0)))) {
                        String qualified = resolveQualified(scopeText, simpleToQualified);
                        var f2c = plan.findFactoryToConstructor(qualified, methodName);
                        if (f2c.isPresent()) {
                            String constrSimple = simpleName(f2c.get().constructorOwner());
                            ensureImport(ast, f2c.get().constructorOwner().replace('/', '.'));
                            return new ObjectCreationExpr(null, new ClassOrInterfaceType(null, constrSimple), n.getArguments());
                        }

                        var rename = plan.findMethodRename(qualified, methodName);
                        if (rename.isPresent()) {
                            n.setName(rename.get());
                        }

                        var classRename = plan.findClassRename(qualified);
                        if (classRename.isPresent()) {
                            scopeName.setName(simpleName(classRename.get()));
                            ensureImport(ast, classRename.get());
                        }
                        return n;
                    }

                    // 2. Instance call on variable: look up variable type in AST
                    Optional<String> varType = findVariableType(n, scopeText);
                    if (varType.isPresent()) {
                        String qualified = resolveQualified(varType.get(), simpleToQualified);
                        var rename = plan.findMethodRename(qualified, methodName);
                        if (rename.isPresent()) {
                            n.setName(rename.get());
                            return n;
                        }
                    }
                }

                // 3. Symbol resolution fallback
                if (scope.isPresent()) {
                    try {
                        var resolved = n.resolve();
                        String declaringType = resolved.declaringType().getQualifiedName();
                        var rename = plan.findMethodRename(declaringType, methodName);
                        if (rename.isPresent()) {
                            n.setName(rename.get());
                            return n;
                        }
                    } catch (Throwable ignored) {
                    }
                }

                // 4. Global plan fallback by method name
                for (var rule : plan.rules()) {
                    if (rule instanceof SourceMigrationRule.MethodRename mr && mr.sourceMethod().equals(methodName)) {
                        n.setName(mr.targetMethod());
                        break;
                    }
                }

                return n;
            }

            @Override
            public Visitable visit(FieldAccessExpr n, Void arg) {
                super.visit(n, arg);

                String fieldName = n.getNameAsString();
                Expression scope = n.getScope();

                if (scope instanceof NameExpr scopeName) {
                    String scopeText = scopeName.getNameAsString();
                    if (simpleToQualified.containsKey(scopeText) || (!scopeText.isEmpty() && Character.isUpperCase(scopeText.charAt(0)))) {
                        String qualified = resolveQualified(scopeText, simpleToQualified);
                        var f2a = plan.findFieldToAccessor(qualified, fieldName);
                        if (f2a.isPresent()) {
                            return new MethodCallExpr(scope, f2a.get().getterMethod());
                        }
                    } else {
                        Optional<String> varType = findVariableType(n, scopeText);
                        if (varType.isPresent()) {
                            String qualified = resolveQualified(varType.get(), simpleToQualified);
                            var f2a = plan.findFieldToAccessor(qualified, fieldName);
                            if (f2a.isPresent()) {
                                return new MethodCallExpr(scope, f2a.get().getterMethod());
                            }
                        }
                    }
                }

                try {
                    var resolved = n.resolve();
                    String declaringType = resolved.asField().declaringType().getQualifiedName();
                    var f2a = plan.findFieldToAccessor(declaringType, fieldName);
                    if (f2a.isPresent()) {
                        return new MethodCallExpr(scope, f2a.get().getterMethod());
                    }
                } catch (Throwable ignored) {
                }

                for (var rule : plan.rules()) {
                    if (rule instanceof SourceMigrationRule.FieldToAccessor fa && fa.fieldName().equals(fieldName)) {
                        return new MethodCallExpr(scope, fa.getterMethod());
                    }
                }
                return n;
            }
        }, null);

        return ast;
    }

    private static Optional<String> findVariableType(com.github.javaparser.ast.Node node, String varName) {
        com.github.javaparser.ast.Node curr = node;
        while (curr != null) {
            if (curr instanceof com.github.javaparser.ast.body.MethodDeclaration md) {
                for (var param : md.getParameters()) {
                    if (param.getNameAsString().equals(varName)) {
                        return Optional.of(param.getType().asString());
                    }
                }
            } else if (curr instanceof com.github.javaparser.ast.body.ConstructorDeclaration cd) {
                for (var param : cd.getParameters()) {
                    if (param.getNameAsString().equals(varName)) {
                        return Optional.of(param.getType().asString());
                    }
                }
            } else if (curr instanceof com.github.javaparser.ast.stmt.BlockStmt bs) {
                for (var stmt : bs.getStatements()) {
                    if (stmt instanceof com.github.javaparser.ast.stmt.ExpressionStmt es
                            && es.getExpression() instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr vde) {
                        for (var decl : vde.getVariables()) {
                            if (decl.getNameAsString().equals(varName)) {
                                return Optional.of(decl.getType().asString());
                            }
                        }
                    }
                }
            } else if (curr instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration cid) {
                for (var field : cid.getFields()) {
                    for (var decl : field.getVariables()) {
                        if (decl.getNameAsString().equals(varName)) {
                            return Optional.of(decl.getType().asString());
                        }
                    }
                }
            }
            curr = curr.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    private static String resolveQualified(String simpleName, Map<String, String> imports) {
        String imported = imports.get(simpleName);
        return imported != null ? imported.replace('/', '.') : simpleName.replace('/', '.');
    }

    private static String simpleName(String qualifiedName) {
        String norm = qualifiedName.replace('/', '.');
        int dot = norm.lastIndexOf('.');
        return dot >= 0 ? norm.substring(dot + 1) : norm;
    }

    private static void ensureImport(CompilationUnit ast, String qualifiedName) {
        String norm = qualifiedName.replace('/', '.');
        for (ImportDeclaration imp : ast.getImports()) {
            if (imp.getNameAsString().equals(norm)) return;
        }
        ast.addImport(norm);
    }
}
