package com.kyroxova.continuumlib.source.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.diagnostic.Severity;
import com.kyroxova.continuumlib.pipeline.migration.*;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.rule.SourceMigrationRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class SourceTransformer {
    private final CanonicalMigrationPlan plan;

    public record TransformationResult(
            List<Path> generatedFiles,
            List<AppliedMigration> appliedMigrations,
            List<Diagnostic> diagnostics
    ) {}

    public SourceTransformer(CanonicalMigrationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public SourceTransformer(SourceMigrationPlan legacyPlan) {
        this(adaptLegacyPlan(legacyPlan));
    }

    private static CanonicalMigrationPlan adaptLegacyPlan(SourceMigrationPlan legacy) {
        List<CanonicalMigrationRule> rules = new ArrayList<>();
        for (var rule : legacy.rules()) {
            if (rule instanceof SourceMigrationRule.ClassRename cr) {
                rules.add(CanonicalMigrationRule.builder()
                        .type(MigrationType.CLASS_RENAME)
                        .sourceOwner(cr.sourceClass())
                        .targetOwner(cr.targetClass())
                        .build());
            } else if (rule instanceof SourceMigrationRule.MethodRename mr) {
                rules.add(CanonicalMigrationRule.builder()
                        .type(MigrationType.MEMBER_RENAME)
                        .sourceOwner(mr.ownerClass())
                        .sourceName(mr.sourceMethod())
                        .targetName(mr.targetMethod())
                        .build());
            } else if (rule instanceof SourceMigrationRule.ConstructorToFactory cf) {
                rules.add(CanonicalMigrationRule.builder()
                        .type(MigrationType.CONSTRUCTOR_TO_FACTORY)
                        .sourceOwner(cf.constructorOwner())
                        .targetOwner(cf.factoryOwner())
                        .targetName(cf.factoryMethod())
                        .build());
            } else if (rule instanceof SourceMigrationRule.FactoryToConstructor fc) {
                rules.add(CanonicalMigrationRule.builder()
                        .type(MigrationType.FACTORY_TO_CONSTRUCTOR)
                        .sourceOwner(fc.factoryOwner())
                        .sourceName(fc.factoryMethod())
                        .targetOwner(fc.constructorOwner())
                        .build());
            } else if (rule instanceof SourceMigrationRule.FieldToAccessor fa) {
                rules.add(CanonicalMigrationRule.builder()
                        .type(MigrationType.FIELD_TO_ACCESSOR)
                        .sourceOwner(fa.ownerClass())
                        .sourceName(fa.fieldName())
                        .targetName(fa.getterMethod())
                        .build());
            }
        }
        return new CanonicalMigrationPlan(rules);
    }

    public List<Path> transformAndWrite(List<SourceUnit> sourceUnits, Path outputDir) throws IOException {
        return transform(sourceUnits, outputDir).generatedFiles();
    }

    public TransformationResult transform(List<SourceUnit> sourceUnits, Path outputDir) throws IOException {
        Path normalizedOutput = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedOutput);
        List<Path> generatedFiles = new ArrayList<>();
        List<AppliedMigration> applied = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (SourceUnit unit : sourceUnits) {
            CompilationUnit astCopy = unit.ast().clone();
            transformAstWithAccounting(astCopy, unit.relativePath(), applied, diagnostics);

            Path targetFile = normalizedOutput.resolve(unit.relativePath()).normalize();
            if (!targetFile.startsWith(normalizedOutput)) {
                throw new IOException("Generated source escapes output directory: " + unit.relativePath());
            }
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, astCopy.toString(), StandardCharsets.UTF_8);
            generatedFiles.add(targetFile);
        }

        return new TransformationResult(
                Collections.unmodifiableList(generatedFiles),
                Collections.unmodifiableList(applied),
                Collections.unmodifiableList(diagnostics)
        );
    }

    public CompilationUnit transformAst(CompilationUnit ast) {
        List<AppliedMigration> applied = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        transformAstWithAccounting(ast, "unknown", applied, diagnostics);
        return ast;
    }

    public void transformAstWithAccounting(
            CompilationUnit ast,
            String sourcePath,
            List<AppliedMigration> appliedMigrations,
            List<Diagnostic> diagnostics
    ) {
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
                imp.setName(rename.get().targetOwner().replace('/', '.'));
                appliedMigrations.add(AppliedMigration.from(
                        rename.get(),
                        MigrationConfidence.SEMANTICALLY_RESOLVED,
                        sourcePath,
                        imp.getBegin().map(p -> p.line).orElse(-1)
                ));
            }
        }

        ast.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceType n, Void arg) {
                String name = n.getNameAsString();
                String qualified = resolveQualified(name, simpleToQualified);
                var rename = plan.findClassRename(qualified);
                if (rename.isPresent()) {
                    String targetQ = rename.get().targetOwner().replace('/', '.');
                    String simpleTarget = simpleName(targetQ);
                    n.setName(simpleTarget);
                    ensureImport(ast, targetQ);
                    appliedMigrations.add(AppliedMigration.from(
                            rename.get(),
                            MigrationConfidence.STRUCTURALLY_RESOLVED,
                            sourcePath,
                            n.getBegin().map(p -> p.line).orElse(-1)
                    ));
                }
                return super.visit(n, arg);
            }

            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);

                String typeName = n.getType().getNameAsString();
                String qualified = resolveQualified(typeName, simpleToQualified);

                // Check constructor-to-factory rules with descriptor matching
                var c2fRules = plan.findConstructorToFactories(qualified);
                if (!c2fRules.isEmpty()) {
                    CanonicalMigrationRule matchedRule = selectConstructorRule(c2fRules, n, qualified, sourcePath, diagnostics);
                    if (matchedRule != null) {
                        String factoryClassSimple = simpleName(matchedRule.targetOwner());
                        ensureImport(ast, matchedRule.targetOwner().replace('/', '.'));

                        MethodCallExpr factoryCall = new MethodCallExpr(
                                new NameExpr(factoryClassSimple),
                                matchedRule.targetName(),
                                n.getArguments()
                        );
                        appliedMigrations.add(AppliedMigration.from(
                                matchedRule,
                                matchedRule.sourceDescriptor() != null ? MigrationConfidence.SEMANTICALLY_RESOLVED : MigrationConfidence.STRUCTURALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return factoryCall;
                    }
                }

                // Check class rename on creation expression
                var classRename = plan.findClassRename(qualified);
                if (classRename.isPresent()) {
                    String newClassSimple = simpleName(classRename.get().targetOwner());
                    n.getType().setName(newClassSimple);
                    ensureImport(ast, classRename.get().targetOwner().replace('/', '.'));
                    appliedMigrations.add(AppliedMigration.from(
                            classRename.get(),
                            MigrationConfidence.STRUCTURALLY_RESOLVED,
                            sourcePath,
                            n.getBegin().map(p -> p.line).orElse(-1)
                    ));
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

                        var f2cRules = plan.findFactoryToConstructors(qualified, methodName);
                        if (!f2cRules.isEmpty()) {
                            CanonicalMigrationRule f2c = f2cRules.get(0);
                            String constrSimple = simpleName(f2c.targetOwner());
                            ensureImport(ast, f2c.targetOwner().replace('/', '.'));
                            appliedMigrations.add(AppliedMigration.from(
                                    f2c,
                                    MigrationConfidence.STRUCTURALLY_RESOLVED,
                                    sourcePath,
                                    n.getBegin().map(p -> p.line).orElse(-1)
                            ));
                            return new ObjectCreationExpr(null, new ClassOrInterfaceType(null, constrSimple), n.getArguments());
                        }

                        var methodRules = plan.findMethodRules(qualified, methodName);
                        if (!methodRules.isEmpty()) {
                            CanonicalMigrationRule matched = selectMethodRule(methodRules, n, qualified, methodName, sourcePath, diagnostics);
                            if (matched != null) {
                                n.setName(matched.targetName());
                                appliedMigrations.add(AppliedMigration.from(
                                        matched,
                                        matched.sourceDescriptor() != null ? MigrationConfidence.SEMANTICALLY_RESOLVED : MigrationConfidence.STRUCTURALLY_RESOLVED,
                                        sourcePath,
                                        n.getBegin().map(p -> p.line).orElse(-1)
                                ));
                            }
                        }

                        var classRename = plan.findClassRename(qualified);
                        if (classRename.isPresent()) {
                            scopeName.setName(simpleName(classRename.get().targetOwner()));
                            ensureImport(ast, classRename.get().targetOwner().replace('/', '.'));
                            appliedMigrations.add(AppliedMigration.from(
                                    classRename.get(),
                                    MigrationConfidence.STRUCTURALLY_RESOLVED,
                                    sourcePath,
                                    n.getBegin().map(p -> p.line).orElse(-1)
                            ));
                        }
                        return n;
                    }

                    // 2. Instance call on variable: look up variable type in AST
                    Optional<String> varType = findVariableType(n, scopeText);
                    if (varType.isPresent()) {
                        String qualified = resolveQualified(varType.get(), simpleToQualified);
                        var methodRules = plan.findMethodRules(qualified, methodName);
                        if (!methodRules.isEmpty()) {
                            CanonicalMigrationRule matched = selectMethodRule(methodRules, n, qualified, methodName, sourcePath, diagnostics);
                            if (matched != null) {
                                n.setName(matched.targetName());
                                appliedMigrations.add(AppliedMigration.from(
                                        matched,
                                        matched.sourceDescriptor() != null ? MigrationConfidence.SEMANTICALLY_RESOLVED : MigrationConfidence.STRUCTURALLY_RESOLVED,
                                        sourcePath,
                                        n.getBegin().map(p -> p.line).orElse(-1)
                                ));
                                return n;
                            }
                        }
                    }
                }

                // 3. Symbol resolution fallback
                if (scope.isPresent()) {
                    try {
                        var resolved = n.resolve();
                        String declaringType = resolved.declaringType().getQualifiedName();
                        var methodRules = plan.findMethodRules(declaringType, methodName);
                        if (!methodRules.isEmpty()) {
                            CanonicalMigrationRule matched = selectMethodRule(methodRules, n, declaringType, methodName, sourcePath, diagnostics);
                            if (matched != null) {
                                n.setName(matched.targetName());
                                appliedMigrations.add(AppliedMigration.from(
                                        matched,
                                        MigrationConfidence.SEMANTICALLY_RESOLVED,
                                        sourcePath,
                                        n.getBegin().map(p -> p.line).orElse(-1)
                                ));
                                return n;
                            }
                        }
                    } catch (Throwable ignored) {
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
                        var f2aRules = plan.findFieldToAccessors(qualified, fieldName);
                        if (!f2aRules.isEmpty()) {
                            CanonicalMigrationRule f2a = f2aRules.get(0);
                            appliedMigrations.add(AppliedMigration.from(
                                    f2a,
                                    MigrationConfidence.STRUCTURALLY_RESOLVED,
                                    sourcePath,
                                    n.getBegin().map(p -> p.line).orElse(-1)
                            ));
                            return new MethodCallExpr(scope, f2a.targetName());
                        }
                    } else {
                        Optional<String> varType = findVariableType(n, scopeText);
                        if (varType.isPresent()) {
                            String qualified = resolveQualified(varType.get(), simpleToQualified);
                            var f2aRules = plan.findFieldToAccessors(qualified, fieldName);
                            if (!f2aRules.isEmpty()) {
                                CanonicalMigrationRule f2a = f2aRules.get(0);
                                appliedMigrations.add(AppliedMigration.from(
                                        f2a,
                                        MigrationConfidence.STRUCTURALLY_RESOLVED,
                                        sourcePath,
                                        n.getBegin().map(p -> p.line).orElse(-1)
                                ));
                                return new MethodCallExpr(scope, f2a.targetName());
                            }
                        }
                    }
                }

                try {
                    var resolved = n.resolve();
                    String declaringType = resolved.asField().declaringType().getQualifiedName();
                    var f2aRules = plan.findFieldToAccessors(declaringType, fieldName);
                    if (!f2aRules.isEmpty()) {
                        CanonicalMigrationRule f2a = f2aRules.get(0);
                        appliedMigrations.add(AppliedMigration.from(
                                f2a,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return new MethodCallExpr(scope, f2a.targetName());
                    }
                } catch (Throwable ignored) {
                }

                return n;
            }
        }, null);
    }

    private static CanonicalMigrationRule selectConstructorRule(
            List<CanonicalMigrationRule> rules,
            ObjectCreationExpr n,
            String owner,
            String sourcePath,
            List<Diagnostic> diagnostics
    ) {
        if (rules.size() == 1 && rules.get(0).sourceDescriptor() == null) {
            return rules.get(0);
        }

        int argCount = n.getArguments().size();
        List<CanonicalMigrationRule> matchingCount = new ArrayList<>();
        for (var rule : rules) {
            if (rule.sourceDescriptor() != null) {
                if (DescriptorMatcher.parameterCount(rule.sourceDescriptor()) == argCount) {
                    matchingCount.add(rule);
                }
            } else {
                matchingCount.add(rule);
            }
        }

        if (matchingCount.size() == 1) {
            return matchingCount.get(0);
        }

        if (matchingCount.size() > 1) {
            // Check argument types against descriptors
            List<CanonicalMigrationRule> typeMatched = new ArrayList<>();
            for (var rule : matchingCount) {
                if (rule.sourceDescriptor() != null && matchesArgTypes(rule.sourceDescriptor(), n.getArguments())) {
                    typeMatched.add(rule);
                }
            }
            if (typeMatched.size() == 1) {
                return typeMatched.get(0);
            }

            int line = n.getBegin().map(p -> p.line).orElse(-1);
            diagnostics.add(Diagnostic.builder()
                    .code(DiagnosticCode.AMBIGUOUS_MIGRATION)
                    .severity(Severity.ERROR)
                    .message("Ambiguous constructor invocation for " + owner + " with " + argCount + " arguments; matches multiple rules")
                    .path(Path.of(sourcePath))
                    .line(line)
                    .build());
            return null;
        }

        return null;
    }

    private static CanonicalMigrationRule selectMethodRule(
            List<CanonicalMigrationRule> rules,
            MethodCallExpr n,
            String owner,
            String methodName,
            String sourcePath,
            List<Diagnostic> diagnostics
    ) {
        if (rules.size() == 1 && rules.get(0).sourceDescriptor() == null) {
            return rules.get(0);
        }

        int argCount = n.getArguments().size();
        List<CanonicalMigrationRule> matchingCount = new ArrayList<>();
        for (var rule : rules) {
            if (rule.sourceDescriptor() != null) {
                if (DescriptorMatcher.parameterCount(rule.sourceDescriptor()) == argCount) {
                    matchingCount.add(rule);
                }
            } else {
                matchingCount.add(rule);
            }
        }

        if (matchingCount.size() == 1) {
            return matchingCount.get(0);
        }

        if (matchingCount.size() > 1) {
            List<CanonicalMigrationRule> typeMatched = new ArrayList<>();
            for (var rule : matchingCount) {
                if (rule.sourceDescriptor() != null && matchesArgTypes(rule.sourceDescriptor(), n.getArguments())) {
                    typeMatched.add(rule);
                }
            }
            if (typeMatched.size() == 1) {
                return typeMatched.get(0);
            }

            int line = n.getBegin().map(p -> p.line).orElse(-1);
            diagnostics.add(Diagnostic.builder()
                    .code(DiagnosticCode.AMBIGUOUS_MIGRATION)
                    .severity(Severity.ERROR)
                    .message("Ambiguous method invocation " + methodName + " on " + owner + " with " + argCount + " arguments; matches multiple overloads")
                    .path(Path.of(sourcePath))
                    .line(line)
                    .build());
            return null;
        }

        return null;
    }

    private static boolean matchesArgTypes(String descriptor, NodeList<Expression> args) {
        List<String> paramTypes = DescriptorMatcher.parseParameterTypes(descriptor);
        if (paramTypes.size() != args.size()) return false;
        for (int i = 0; i < args.size(); i++) {
            Expression arg = args.get(i);
            String expected = paramTypes.get(i);
            if (arg instanceof StringLiteralExpr && !expected.equals("java.lang.String")) {
                return false;
            }
            if (arg instanceof IntegerLiteralExpr && !expected.equals("int") && !expected.equals("long")) {
                return false;
            }
            if (arg instanceof BooleanLiteralExpr && !expected.equals("boolean")) {
                return false;
            }
            if (arg instanceof DoubleLiteralExpr && !expected.equals("double") && !expected.equals("float")) {
                return false;
            }
        }
        return true;
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
