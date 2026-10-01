package com.kyroxova.continuumlib.source.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.kyroxova.continuumlib.bytecode.CallBridge;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.source.ast.JvmDescriptors;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.rule.SourceMigrationRule;
import org.objectweb.asm.Opcodes;

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
            if (!targetFile.startsWith(outputDir.toAbsolutePath().normalize())) {
                throw new IOException("Generated source escapes output directory: " + unit.relativePath());
            }
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, astCopy.toString(), StandardCharsets.UTF_8);
            generatedFiles.add(targetFile);
        }

        return Collections.unmodifiableList(generatedFiles);
    }

    public CompilationUnit transformAst(CompilationUnit ast) {
        Map<String, String> simpleToQualified = new HashMap<>();
        for (ImportDeclaration imp : ast.getImports()) {
            String qName = imp.getNameAsString();
            int dot = qName.lastIndexOf('.');
            String simple = dot >= 0 ? qName.substring(dot + 1) : qName;
            simpleToQualified.put(simple, qName);
        }

        for (ImportDeclaration imp : new ArrayList<>(ast.getImports())) {
            String qName = imp.getNameAsString();
            var rename = plan.findClassRename(qName);
            rename.ifPresent(imp::setName);
        }

        ast.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceType n, Void arg) {
                String name = n.getNameAsString();
                String qualified = resolveQualified(name, simpleToQualified);
                var rename = plan.findClassRename(qualified);
                if (rename.isPresent()) {
                    String targetQ = rename.get();
                    n.setName(simpleName(targetQ));
                    ensureImport(ast, targetQ);
                }
                return super.visit(n, arg);
            }

            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                Optional<MemberReference> resolvedConstructor = resolveConstructor(n);
                super.visit(n, arg);

                if (resolvedConstructor.isPresent()) {
                    var exact = plan.findExactConstructorFactory(resolvedConstructor.get());
                    if (exact.isPresent()) {
                        return staticCall(ast, exact.get().factory(), List.copyOf(n.getArguments()));
                    }
                }

                String typeName = n.getType().getNameAsString();
                String qualified = resolveQualified(typeName, simpleToQualified);
                var legacy = plan.findConstructorToFactory(qualified);
                if (legacy.isPresent()) {
                    SourceMigrationRule.ConstructorToFactory rule = legacy.get();
                    ensureImport(ast, rule.factoryOwner().replace('/', '.'));
                    return new MethodCallExpr(
                            new NameExpr(simpleName(rule.factoryOwner())),
                            rule.factoryMethod(),
                            cloneArguments(n.getArguments())
                    );
                }

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
                Optional<ResolvedMethodUse> resolved = resolveMethod(n);
                super.visit(n, arg);

                if (resolved.isPresent()) {
                    ResolvedMethodUse use = resolved.get();
                    Optional<CallBridge.Rule> bridge = exactMethodBridge(use);
                    if (bridge.isPresent() && !(n.getScope().orElse(null) instanceof SuperExpr)) {
                        List<Expression> arguments = new ArrayList<>();
                        if (!use.isStatic()) {
                            arguments.add(n.getScope().<Expression>map(Expression::clone).orElseGet(ThisExpr::new));
                        }
                        n.getArguments().forEach(argument -> arguments.add(argument.clone()));
                        return staticCall(ast, bridge.get().hook(), arguments);
                    }

                    var exactRename = plan.findExactMemberMigration(use.member());
                    if (exactRename.isPresent() && exactRename.get().descriptor().startsWith("(")) {
                        n.setName(exactRename.get().name());
                        if (use.isStatic() && n.getScope().isPresent() && exactRename.get().owner() != null) {
                            replaceStaticOwnerIfTypeName(ast, n, exactRename.get().owner(), simpleToQualified);
                        }
                        return n;
                    }
                }

                String methodName = n.getNameAsString();
                Optional<Expression> scope = n.getScope();

                if (scope.isPresent() && scope.get() instanceof NameExpr scopeName) {
                    String scopeText = scopeName.getNameAsString();

                    if (simpleToQualified.containsKey(scopeText) || (!scopeText.isEmpty() && Character.isUpperCase(scopeText.charAt(0)))) {
                        String qualified = resolveQualified(scopeText, simpleToQualified);
                        var f2c = plan.findFactoryToConstructor(qualified, methodName);
                        if (f2c.isPresent()) {
                            String constrSimple = simpleName(f2c.get().constructorOwner());
                            ensureImport(ast, f2c.get().constructorOwner().replace('/', '.'));
                            return new ObjectCreationExpr(null, new ClassOrInterfaceType(null, constrSimple), cloneArguments(n.getArguments()));
                        }

                        var rename = plan.findMethodRename(qualified, methodName);
                        rename.ifPresent(n::setName);

                        var classRename = plan.findClassRename(qualified);
                        if (classRename.isPresent()) {
                            scopeName.setName(simpleName(classRename.get()));
                            ensureImport(ast, classRename.get());
                        }
                        return n;
                    }

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

                // No global name-only fallback. Ambiguous/unresolved calls remain unchanged.
                return n;
            }

            @Override
            public Visitable visit(FieldAccessExpr n, Void arg) {
                Optional<MemberReference> resolvedField = resolveField(n);
                super.visit(n, arg);

                if (resolvedField.isPresent()) {
                    var getField = plan.findExactCallBridge(resolvedField.get(), Opcodes.GETFIELD);
                    var getStatic = plan.findExactCallBridge(resolvedField.get(), Opcodes.GETSTATIC);
                    if (getField.isPresent() && getStatic.isPresent()) {
                        throw new IllegalStateException("Ambiguous field bridge opcode for " + resolvedField.get());
                    }
                    if (getField.isPresent()) {
                        return staticCall(ast, getField.get().hook(), List.of(n.getScope().clone()));
                    }
                    if (getStatic.isPresent()) {
                        return staticCall(ast, getStatic.get().hook(), List.of());
                    }

                    var exactRename = plan.findExactMemberMigration(resolvedField.get());
                    if (exactRename.isPresent() && !exactRename.get().descriptor().startsWith("(")) {
                        n.setName(exactRename.get().name());
                        return n;
                    }
                }

                String fieldName = n.getNameAsString();
                Expression scope = n.getScope();

                if (scope instanceof NameExpr scopeName) {
                    String scopeText = scopeName.getNameAsString();
                    if (simpleToQualified.containsKey(scopeText) || (!scopeText.isEmpty() && Character.isUpperCase(scopeText.charAt(0)))) {
                        String qualified = resolveQualified(scopeText, simpleToQualified);
                        var f2a = plan.findFieldToAccessor(qualified, fieldName);
                        if (f2a.isPresent()) {
                            return new MethodCallExpr(scope.clone(), f2a.get().getterMethod());
                        }
                    } else {
                        Optional<String> varType = findVariableType(n, scopeText);
                        if (varType.isPresent()) {
                            String qualified = resolveQualified(varType.get(), simpleToQualified);
                            var f2a = plan.findFieldToAccessor(qualified, fieldName);
                            if (f2a.isPresent()) {
                                return new MethodCallExpr(scope.clone(), f2a.get().getterMethod());
                            }
                        }
                    }
                }

                // No global field-name fallback. Ambiguous/unresolved fields remain unchanged.
                return n;
            }
        }, null);

        return ast;
    }

    private Optional<CallBridge.Rule> exactMethodBridge(ResolvedMethodUse use) {
        if (use.isStatic()) {
            return plan.findExactCallBridge(use.member(), Opcodes.INVOKESTATIC);
        }
        Optional<CallBridge.Rule> virtual = plan.findExactCallBridge(use.member(), Opcodes.INVOKEVIRTUAL);
        Optional<CallBridge.Rule> iface = plan.findExactCallBridge(use.member(), Opcodes.INVOKEINTERFACE);
        if (virtual.isPresent() && iface.isPresent()) {
            throw new IllegalStateException("Ambiguous method bridge opcode for " + use.member());
        }
        return virtual.isPresent() ? virtual : iface;
    }

    private static Optional<MemberReference> resolveConstructor(ObjectCreationExpr expression) {
        try {
            return JvmDescriptors.constructor(expression.resolve());
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ResolvedMethodUse> resolveMethod(MethodCallExpr expression) {
        try {
            var resolved = expression.resolve();
            return JvmDescriptors.method(resolved).map(member -> new ResolvedMethodUse(member, resolved.isStatic()));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<MemberReference> resolveField(FieldAccessExpr expression) {
        try {
            return JvmDescriptors.field(expression.resolve().asField());
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static MethodCallExpr staticCall(CompilationUnit ast, MemberReference target, List<Expression> arguments) {
        String owner = target.owner().replace('/', '.');
        ensureImport(ast, owner);
        NodeList<Expression> cloned = new NodeList<>();
        arguments.forEach(argument -> cloned.add(argument.clone()));
        return new MethodCallExpr(new NameExpr(simpleName(owner)), target.name(), cloned);
    }

    private static void replaceStaticOwnerIfTypeName(CompilationUnit ast, MethodCallExpr call, String targetOwner,
                                                     Map<String, String> imports) {
        if (call.getScope().isEmpty() || !(call.getScope().get() instanceof NameExpr scope)) return;
        String current = scope.getNameAsString();
        if (!imports.containsKey(current) && (current.isEmpty() || !Character.isUpperCase(current.charAt(0)))) return;
        String owner = targetOwner.replace('/', '.');
        scope.setName(simpleName(owner));
        ensureImport(ast, owner);
    }

    private static NodeList<Expression> cloneArguments(NodeList<Expression> arguments) {
        NodeList<Expression> copy = new NodeList<>();
        arguments.forEach(argument -> copy.add(argument.clone()));
        return copy;
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

    private record ResolvedMethodUse(MemberReference member, boolean isStatic) {}
}
