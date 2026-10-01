package com.kyroxova.continuumlib.source.transform;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.kyroxova.continuumlib.bytecode.MemberReference;
import com.kyroxova.continuumlib.model.diagnostic.Diagnostic;
import com.kyroxova.continuumlib.model.diagnostic.DiagnosticCode;
import com.kyroxova.continuumlib.model.diagnostic.Severity;
import com.kyroxova.continuumlib.pipeline.migration.*;
import com.kyroxova.continuumlib.source.ast.JvmDescriptors;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.rule.SourceMigrationRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.Opcodes;

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
        Map<String, String> simpleToQualified = new HashMap<>();
        for (ImportDeclaration imp : ast.getImports()) {
            if (imp.isStatic() || imp.isAsterisk()) continue;
            String qName = imp.getNameAsString();
            int dot = qName.lastIndexOf('.');
            String simple = dot >= 0 ? qName.substring(dot + 1) : qName;
            simpleToQualified.put(simple, qName);
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
                Optional<MemberReference> resolvedConstructor = resolveConstructor(n);
                super.visit(n, arg);

                if (resolvedConstructor.isPresent()) {
                    var exact = plan.findExactConstructorFactory(resolvedConstructor.get());
                    if (exact.isPresent()) {
                        appliedMigrations.add(AppliedMigration.from(
                                exact.get(),
                                MigrationLayer.SOURCE_AST,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return staticCall(ast, exact.get(), List.copyOf(n.getArguments()));
                    }
                }

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
                Optional<ResolvedMethodUse> resolvedUse = resolveMethod(n);
                super.visit(n, arg);

                if (resolvedUse.isPresent()) {
                    ResolvedMethodUse use = resolvedUse.get();
                    Optional<CanonicalMigrationRule> bridge = exactMethodBridge(use);
                    if (bridge.isPresent() && !(n.getScope().orElse(null) instanceof SuperExpr)) {
                        List<Expression> arguments = new ArrayList<>();
                        if (!use.isStatic()) {
                            arguments.add(n.getScope().<Expression>map(Expression::clone).orElseGet(ThisExpr::new));
                        }
                        n.getArguments().forEach(argument -> arguments.add(argument.clone()));
                        appliedMigrations.add(AppliedMigration.from(
                                bridge.get(),
                                MigrationLayer.SOURCE_AST,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return staticCall(ast, bridge.get(), arguments);
                    }

                    var exactRename = plan.findExactMemberRename(use.member());
                    if (exactRename.isPresent()) {
                        n.setName(exactRename.get().targetName());
                        if (use.isStatic()) {
                            replaceStaticOwnerIfTypeName(ast, n, exactRename.get().targetOwner(), simpleToQualified);
                        }
                        appliedMigrations.add(AppliedMigration.from(
                                exactRename.get(),
                                MigrationLayer.SOURCE_AST,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return n;
                    }
                }

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
            public Visitable visit(MethodReferenceExpr n, Void arg) {
                Optional<ResolvedMethodUse> resolvedUse = resolveMethodReference(n);
                String sourceOwner = typeScopeOwner(n.getScope(), simpleToQualified).orElse(null);
                super.visit(n, arg);

                if ("new".equals(n.getIdentifier())) {
                    if (sourceOwner != null && !plan.findConstructorToFactories(sourceOwner).isEmpty()) {
                        diagnostics.add(Diagnostic.builder()
                                .code(DiagnosticCode.MIGRATION_UNRESOLVED)
                                .severity(Severity.ERROR)
                                .message("Constructor method reference requires explicit overload resolution: " + n)
                                .path(Path.of(sourcePath))
                                .line(n.getBegin().map(p -> p.line).orElse(-1))
                                .build());
                    }
                    return n;
                }

                if (resolvedUse.isEmpty()) return n;
                ResolvedMethodUse use = resolvedUse.get();

                var bridge = exactMethodBridge(use);
                if (bridge.isPresent()) {
                    if (use.isStatic() || isTypeScope(n.getScope(), simpleToQualified)) {
                        setMethodReferenceTarget(ast, n, bridge.get().targetOwner(), bridge.get().targetName());
                        appliedMigrations.add(AppliedMigration.from(
                                bridge.get(),
                                MigrationLayer.SOURCE_AST,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return n;
                    }

                    if (n.getScope() instanceof NameExpr || n.getScope() instanceof ThisExpr) {
                        appliedMigrations.add(AppliedMigration.from(
                                bridge.get(),
                                MigrationLayer.SOURCE_AST,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return boundBridgeLambda(ast, n.getScope(), bridge.get());
                    }

                    diagnostics.add(Diagnostic.builder()
                            .code(DiagnosticCode.MIGRATION_UNRESOLVED)
                            .severity(Severity.ERROR)
                            .message("Bound method reference has a receiver that cannot be safely re-evaluated: " + n)
                            .path(Path.of(sourcePath))
                            .line(n.getBegin().map(p -> p.line).orElse(-1))
                            .build());
                    return n;
                }

                var exactRename = plan.findExactMemberRename(use.member());
                if (exactRename.isPresent()) {
                    n.setIdentifier(exactRename.get().targetName());
                    if (isTypeScope(n.getScope(), simpleToQualified)) {
                        setMethodReferenceTarget(ast, n, exactRename.get().targetOwner(), exactRename.get().targetName());
                    }
                    appliedMigrations.add(AppliedMigration.from(
                            exactRename.get(),
                            MigrationLayer.SOURCE_AST,
                            MigrationConfidence.SEMANTICALLY_RESOLVED,
                            sourcePath,
                            n.getBegin().map(p -> p.line).orElse(-1)
                    ));
                }
                return n;
            }

            @Override
            public Visitable visit(NameExpr n, Void arg) {
                Optional<ResolvedFieldUse> resolvedField = resolveNameField(n);
                super.visit(n, arg);
                if (resolvedField.isEmpty()) return n;

                ResolvedFieldUse use = resolvedField.get();
                var exactRename = plan.findExactFieldRename(use.member());
                if (exactRename.isPresent()) {
                    appliedMigrations.add(AppliedMigration.from(
                            exactRename.get(),
                            MigrationLayer.SOURCE_AST,
                            MigrationConfidence.SEMANTICALLY_RESOLVED,
                            sourcePath,
                            n.getBegin().map(p -> p.line).orElse(-1)
                    ));
                    if (use.isStatic()) {
                        return staticFieldAccess(ast, exactRename.get());
                    }
                    n.setName(exactRename.get().targetName());
                }

                if (!isWriteTarget(n)) {
                    int opcode = use.isStatic() ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
                    var bridge = plan.findExactCallBridge(use.member(), opcode);
                    if (bridge.isPresent()) {
                        List<Expression> arguments = use.isStatic()
                                ? List.of()
                                : List.of(new ThisExpr());
                        appliedMigrations.add(AppliedMigration.from(
                                bridge.get(),
                                MigrationLayer.SOURCE_AST,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                        return staticCall(ast, bridge.get(), arguments);
                    }
                }
                return n;
            }

            @Override
            public Visitable visit(FieldAccessExpr n, Void arg) {
                Optional<ResolvedFieldUse> resolvedField = resolveField(n);
                super.visit(n, arg);

                if (resolvedField.isPresent()) {
                    ResolvedFieldUse use = resolvedField.get();
                    var exactRename = plan.findExactFieldRename(use.member());
                    if (exactRename.isPresent()) {
                        n.setName(exactRename.get().targetName());
                        if (use.isStatic()) {
                            replaceStaticOwnerIfTypeName(ast, n, exactRename.get().targetOwner(), simpleToQualified);
                        }
                        appliedMigrations.add(AppliedMigration.from(
                                exactRename.get(),
                                MigrationLayer.SOURCE_AST,
                                MigrationConfidence.SEMANTICALLY_RESOLVED,
                                sourcePath,
                                n.getBegin().map(p -> p.line).orElse(-1)
                        ));
                    }

                    if (!isWriteTarget(n)) {
                        int opcode = use.isStatic() ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
                        var bridge = plan.findExactCallBridge(use.member(), opcode);
                        if (bridge.isPresent()) {
                            List<Expression> arguments = use.isStatic()
                                    ? List.of()
                                    : List.of(n.getScope().clone());
                            appliedMigrations.add(AppliedMigration.from(
                                    bridge.get(),
                                    MigrationLayer.SOURCE_AST,
                                    MigrationConfidence.SEMANTICALLY_RESOLVED,
                                    sourcePath,
                                    n.getBegin().map(p -> p.line).orElse(-1)
                            ));
                            return staticCall(ast, bridge.get(), arguments);
                        }
                    }
                }

                String fieldName = n.getNameAsString();
                Expression scope = n.getScope();

                if (scope instanceof NameExpr scopeName) {
                    String scopeText = scopeName.getNameAsString();
                    if (simpleToQualified.containsKey(scopeText) || (!scopeText.isEmpty() && Character.isUpperCase(scopeText.charAt(0)))) {
                        String qualified = resolveQualified(scopeText, simpleToQualified);
                        var renameRules = plan.findFieldRenames(qualified, fieldName);
                        if (renameRules.size() == 1) {
                            CanonicalMigrationRule rename = renameRules.get(0);
                            n.setName(rename.targetName());
                            appliedMigrations.add(AppliedMigration.from(
                                    rename,
                                    MigrationLayer.SOURCE_AST,
                                    MigrationConfidence.STRUCTURALLY_RESOLVED,
                                    sourcePath,
                                    n.getBegin().map(p -> p.line).orElse(-1)
                            ));
                        }
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
                            var renameRules = plan.findFieldRenames(qualified, fieldName);
                            if (renameRules.size() == 1) {
                                CanonicalMigrationRule rename = renameRules.get(0);
                                n.setName(rename.targetName());
                                appliedMigrations.add(AppliedMigration.from(
                                        rename,
                                        MigrationLayer.SOURCE_AST,
                                        MigrationConfidence.STRUCTURALLY_RESOLVED,
                                        sourcePath,
                                        n.getBegin().map(p -> p.line).orElse(-1)
                                ));
                            }
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

            @Override
            public Visitable visit(AssignExpr n, Void arg) {
                Optional<ResolvedFieldUse> assignedField = resolveAssignedField(n.getTarget());
                super.visit(n, arg);

                if (assignedField.isEmpty()) return n;

                ResolvedFieldUse use = assignedField.get();
                int opcode = use.isStatic() ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;
                var bridge = plan.findExactCallBridge(use.member(), opcode);
                if (bridge.isEmpty()) return n;

                if (n.getOperator() != AssignExpr.Operator.ASSIGN) {
                    diagnostics.add(Diagnostic.builder()
                            .code(DiagnosticCode.MIGRATION_UNRESOLVED)
                            .severity(Severity.ERROR)
                            .message("Compound field assignment requires an explicit source strategy for " + use.member())
                            .path(Path.of(sourcePath))
                            .line(n.getBegin().map(p -> p.line).orElse(-1))
                            .build());
                    return n;
                }

                List<Expression> arguments = new ArrayList<>();
                if (!use.isStatic()) {
                    arguments.add(receiverForAssignment(n.getTarget()));
                }
                arguments.add(n.getValue().clone());
                appliedMigrations.add(AppliedMigration.from(
                        bridge.get(),
                        MigrationLayer.SOURCE_AST,
                        MigrationConfidence.SEMANTICALLY_RESOLVED,
                        sourcePath,
                        n.getBegin().map(p -> p.line).orElse(-1)
                ));
                return staticCall(ast, bridge.get(), arguments);
            }
        }, null);

        rewriteImports(ast, simpleToQualified, sourcePath, appliedMigrations, diagnostics);
    }

    private Optional<CanonicalMigrationRule> exactMethodBridge(ResolvedMethodUse use) {
        if (use.isStatic()) {
            return plan.findExactCallBridge(use.member(), Opcodes.INVOKESTATIC);
        }
        return plan.findExactCallBridge(
                use.member(),
                use.isInterface() ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL
        );
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
            return JvmDescriptors.method(resolved)
                    .map(member -> new ResolvedMethodUse(
                            member,
                            resolved.isStatic(),
                            resolved.declaringType().isInterface()
                    ));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ResolvedMethodUse> resolveMethodReference(MethodReferenceExpr expression) {
        try {
            var resolved = expression.resolve();
            return JvmDescriptors.method(resolved)
                    .map(member -> new ResolvedMethodUse(
                            member,
                            resolved.isStatic(),
                            resolved.declaringType().isInterface()
                    ));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ResolvedFieldUse> resolveField(FieldAccessExpr expression) {
        try {
            var field = expression.resolve().asField();
            return JvmDescriptors.field(field).map(member -> new ResolvedFieldUse(member, field.isStatic()));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ResolvedFieldUse> resolveNameField(NameExpr expression) {
        try {
            var resolved = expression.resolve();
            if (!resolved.isField()) return Optional.empty();
            var field = resolved.asField();
            return JvmDescriptors.field(field).map(member -> new ResolvedFieldUse(member, field.isStatic()));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ResolvedFieldUse> resolveAssignedField(Expression expression) {
        if (expression instanceof FieldAccessExpr fieldAccess) {
            return resolveField(fieldAccess);
        }
        if (expression instanceof NameExpr name) {
            try {
                var resolved = name.resolve();
                if (!resolved.isField()) return Optional.empty();
                var field = resolved.asField();
                return JvmDescriptors.field(field).map(member -> new ResolvedFieldUse(member, field.isStatic()));
            } catch (Throwable ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isWriteTarget(Expression expression) {
        return expression.getParentNode()
                .filter(AssignExpr.class::isInstance)
                .map(AssignExpr.class::cast)
                .map(assign -> assign.getTarget() == expression)
                .orElse(false);
    }

    private static Expression receiverForAssignment(Expression target) {
        if (target instanceof FieldAccessExpr fieldAccess) {
            return fieldAccess.getScope().clone();
        }
        return new ThisExpr();
    }

    private static MethodCallExpr staticCall(
            CompilationUnit ast,
            CanonicalMigrationRule rule,
            List<Expression> arguments
    ) {
        String owner = rule.targetOwner().replace('/', '.');
        ensureImport(ast, owner);
        NodeList<Expression> cloned = new NodeList<>();
        arguments.forEach(argument -> cloned.add(argument.clone()));
        return new MethodCallExpr(new NameExpr(simpleName(owner)), rule.targetName(), cloned);
    }

    private static void replaceStaticOwnerIfTypeName(
            CompilationUnit ast,
            MethodCallExpr call,
            String targetOwner,
            Map<String, String> imports
    ) {
        if (targetOwner == null) return;

        String owner = targetOwner.replace('/', '.');
        if (call.getScope().isEmpty()) {
            call.setScope(new NameExpr(simpleName(owner)));
            ensureImport(ast, owner);
            return;
        }
        if (!(call.getScope().get() instanceof NameExpr scope)) return;

        String current = scope.getNameAsString();
        if (!imports.containsKey(current) && (current.isEmpty() || !Character.isUpperCase(current.charAt(0)))) {
            return;
        }
        scope.setName(simpleName(owner));
        ensureImport(ast, owner);
    }

    private static void replaceStaticOwnerIfTypeName(
            CompilationUnit ast,
            FieldAccessExpr access,
            String targetOwner,
            Map<String, String> imports
    ) {
        if (targetOwner == null || !(access.getScope() instanceof NameExpr scope)) {
            return;
        }
        String current = scope.getNameAsString();
        if (!imports.containsKey(current) && (current.isEmpty() || !Character.isUpperCase(current.charAt(0)))) {
            return;
        }
        String owner = targetOwner.replace('/', '.');
        scope.setName(simpleName(owner));
        ensureImport(ast, owner);
    }

    private void rewriteImports(
            CompilationUnit ast,
            Map<String, String> sourceImports,
            String sourcePath,
            List<AppliedMigration> appliedMigrations,
            List<Diagnostic> diagnostics
    ) {
        for (ImportDeclaration imported : new ArrayList<>(ast.getImports())) {
            String name = imported.getNameAsString();

            if (!imported.isStatic()) {
                if (imported.isAsterisk()) continue;
                plan.findClassRename(name).ifPresent(rule -> {
                    replaceImport(ast, imported, rule.targetOwner().replace('/', '.'));
                    appliedMigrations.add(AppliedMigration.from(
                            rule,
                            MigrationLayer.SOURCE_AST,
                            MigrationConfidence.SEMANTICALLY_RESOLVED,
                            sourcePath,
                            imported.getBegin().map(p -> p.line).orElse(-1)
                    ));
                });
                continue;
            }

            if (imported.isAsterisk()) {
                plan.findClassRename(name).ifPresent(rule ->
                        replaceImport(ast, imported, rule.targetOwner().replace('/', '.')));
                continue;
            }

            int dot = name.lastIndexOf('.');
            if (dot <= 0) continue;
            String owner = name.substring(0, dot);
            String member = name.substring(dot + 1);

            boolean migratedMember = !plan.findMethodRules(owner, member).isEmpty()
                    || !plan.findFieldRenames(owner, member).isEmpty()
                    || !plan.findFieldToAccessors(owner, member).isEmpty()
                    || !plan.findCallBridges(owner, member).isEmpty();

            if (migratedMember) {
                if (hasUnqualifiedReference(ast, owner, member)) {
                    diagnostics.add(Diagnostic.builder()
                            .code(DiagnosticCode.MIGRATION_UNRESOLVED)
                            .severity(Severity.ERROR)
                            .message("Static import still has unresolved uses after migration: " + name)
                            .path(Path.of(sourcePath))
                            .line(imported.getBegin().map(p -> p.line).orElse(-1))
                            .build());
                } else {
                    imported.remove();
                }
                continue;
            }

            plan.findClassRename(owner).ifPresent(rule ->
                    replaceImport(ast, imported, rule.targetOwner().replace('/', '.') + "." + member));
        }
    }

    private static boolean hasUnqualifiedReference(CompilationUnit ast, String owner, String member) {
        for (MethodCallExpr call : ast.findAll(MethodCallExpr.class)) {
            if (call.getScope().isEmpty() && call.getNameAsString().equals(member)) return true;
        }
        for (NameExpr name : ast.findAll(NameExpr.class)) {
            if (!name.getNameAsString().equals(member)) continue;
            try {
                var resolved = name.resolve();
                if (resolved.isField()) {
                    var field = resolved.asField();
                    if (field.declaringType().getQualifiedName().replace('/', '.').equals(owner.replace('/', '.'))) {
                        return true;
                    }
                    continue;
                }
                continue;
            } catch (Throwable ignored) {
                return true;
            }
        }
        return false;
    }

    private static void replaceImport(CompilationUnit ast, ImportDeclaration imported, String targetName) {
        boolean duplicate = ast.getImports().stream()
                .filter(other -> other != imported)
                .anyMatch(other -> other.isStatic() == imported.isStatic()
                        && other.isAsterisk() == imported.isAsterisk()
                        && other.getNameAsString().equals(targetName));
        if (duplicate) imported.remove();
        else imported.setName(targetName);
    }

    private static Optional<String> typeScopeOwner(Expression scope, Map<String, String> imports) {
        if (scope instanceof TypeExpr type) {
            return Optional.of(resolveQualified(type.getType().asString(), imports));
        }
        if (scope instanceof NameExpr name && isTypeScope(scope, imports)) {
            return Optional.of(resolveQualified(name.getNameAsString(), imports));
        }
        return Optional.empty();
    }

    private static boolean isTypeScope(Expression scope, Map<String, String> imports) {
        if (scope instanceof TypeExpr) return true;
        if (!(scope instanceof NameExpr name)) return false;
        String value = name.getNameAsString();
        if (imports.containsKey(value)) return true;
        try {
            name.resolve();
            return false;
        } catch (Throwable ignored) {
            return !value.isEmpty() && Character.isUpperCase(value.charAt(0));
        }
    }

    private static void setMethodReferenceTarget(
            CompilationUnit ast,
            MethodReferenceExpr reference,
            String targetOwner,
            String targetName
    ) {
        String owner = targetOwner.replace('/', '.');
        reference.setScope(new NameExpr(simpleName(owner)));
        reference.setIdentifier(targetName);
        ensureImport(ast, owner);
    }

    private static LambdaExpr boundBridgeLambda(
            CompilationUnit ast,
            Expression receiver,
            CanonicalMigrationRule bridge
    ) {
        int count = DescriptorMatcher.parameterCount(bridge.sourceDescriptor());
        NodeList<com.github.javaparser.ast.body.Parameter> parameters = new NodeList<>();
        List<Expression> arguments = new ArrayList<>();
        arguments.add(receiver.clone());

        Set<String> usedNames = new HashSet<>();
        ast.findAll(com.github.javaparser.ast.expr.SimpleName.class)
                .forEach(name -> usedNames.add(name.asString()));

        int nextName = 0;
        for (int i = 0; i < count; i++) {
            String name;
            do {
                name = "__continuum$arg" + nextName++;
            } while (!usedNames.add(name));

            parameters.add(new com.github.javaparser.ast.body.Parameter(
                    new com.github.javaparser.ast.type.UnknownType(),
                    name
            ));
            arguments.add(new NameExpr(name));
        }
        return new LambdaExpr(parameters, staticCall(ast, bridge, arguments));
    }

    private static FieldAccessExpr staticFieldAccess(CompilationUnit ast, CanonicalMigrationRule rule) {
        String owner = rule.targetOwner().replace('/', '.');
        ensureImport(ast, owner);
        return new FieldAccessExpr(new NameExpr(simpleName(owner)), rule.targetName());
    }

    private record ResolvedMethodUse(MemberReference member, boolean isStatic, boolean isInterface) {
    }

    private record ResolvedFieldUse(MemberReference member, boolean isStatic) {
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
