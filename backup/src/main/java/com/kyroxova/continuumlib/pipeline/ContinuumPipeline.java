package com.kyroxova.continuumlib.pipeline;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.analyzer.JavaSourceAnalyzer;
import com.kyroxova.continuumlib.cir.CirCompilationUnit;
import com.kyroxova.continuumlib.emitter.TargetJavaEmitter;
import com.kyroxova.continuumlib.knowledgebase.ApiKnowledgeBase;
import com.kyroxova.continuumlib.resolver.ContinuumResolver;
import com.kyroxova.continuumlib.resolver.ResolvedTargetModel;
import com.kyroxova.continuumlib.verification.ResolutionReport;
import com.kyroxova.continuumlib.verification.TargetCompilationVerifier;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * End-to-end ContinuumLib Universal API Resolution Pipeline.
 *
 * Execution flow:
 * Native Base Source
 *        ↓
 * AST + Symbol Analysis (JavaSourceAnalyzer)
 *        ↓
 * ContinuumLib Semantic IR (CIR)
 *        ↓
 * Version/API Resolver (ContinuumResolver)
 *        ↓
 * Target API Knowledge (ApiKnowledgeBase / TargetProfile)
 *        ↓
 * Target Native Source (TargetJavaEmitter)
 *        ↓
 * Compile + Verify (TargetCompilationVerifier)
 *        ↓
 * ContinuumLib Resolution Report
 */
public final class ContinuumPipeline {

    private final ApiKnowledgeBase knowledgeBase;
    private final TargetSpec baseSpec;
    private final List<TargetSpec> targetSpecs;
    private final JavaSourceAnalyzer analyzer;
    private final ContinuumResolver resolver;
    private final TargetJavaEmitter emitter;
    private final TargetCompilationVerifier verifier;

    private CirCompilationUnit lastAnalyzedCir;

    public ContinuumPipeline(com.kyroxova.bootstrapper.config.BootstrapperConfig config) {
        this(config.getBaseSpec(), config.getTargetSpecs(), ApiKnowledgeBase.createDefault());
    }

    public ContinuumPipeline(TargetSpec baseSpec, List<TargetSpec> targetSpecs) {
        this(baseSpec, targetSpecs, ApiKnowledgeBase.createDefault());
    }

    public ContinuumPipeline(TargetSpec baseSpec, List<TargetSpec> targetSpecs, ApiKnowledgeBase knowledgeBase) {
        this.baseSpec = Objects.requireNonNull(baseSpec, "baseSpec cannot be null");
        this.targetSpecs = new ArrayList<>(Objects.requireNonNull(targetSpecs, "targetSpecs cannot be null"));
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");

        this.analyzer = new JavaSourceAnalyzer(baseSpec);
        this.resolver = new ContinuumResolver(knowledgeBase);
        this.emitter = new TargetJavaEmitter();
        this.verifier = new TargetCompilationVerifier();
    }

    public CirCompilationUnit getLastAnalyzedCir() {
        return lastAnalyzedCir;
    }

    public ResolutionReport processSource(String sourceCode, Map<TargetSpec, String> generatedSourcesOutput) {
        // 1. AST + Symbol Analysis -> CIR
        CirCompilationUnit cir = analyzer.analyze(sourceCode);
        this.lastAnalyzedCir = cir;

        // 2. Validate CIR integrity
        validateCir(cir);

        ResolutionReport report = new ResolutionReport(baseSpec);

        // 3. For each target, resolve, emit, and compile
        for (TargetSpec target : targetSpecs) {
            ResolvedTargetModel resolved = resolver.resolve(cir, baseSpec, target);
            String targetSource = emitter.emit(resolved);

            if (generatedSourcesOutput != null) {
                generatedSourcesOutput.put(target, targetSource);
            }

            String fullClassName = (cir.getPackageName() != null && !cir.getPackageName().isBlank())
                    ? cir.getPackageName() + "." + cir.getPrimaryClassName()
                    : cir.getPrimaryClassName();

            boolean compiled = verifier.verifyCompilation(targetSource, fullClassName, target);
            report.recordTarget(resolved, compiled);
        }

        return report;
    }

    public ResolutionReport processFile(File sourceFile, Map<TargetSpec, String> generatedSourcesOutput) throws IOException {
        CirCompilationUnit cir = analyzer.analyze(sourceFile);
        validateCir(cir);

        ResolutionReport report = new ResolutionReport(baseSpec);
        for (TargetSpec target : targetSpecs) {
            ResolvedTargetModel resolved = resolver.resolve(cir, baseSpec, target);
            String targetSource = emitter.emit(resolved);

            if (generatedSourcesOutput != null) {
                generatedSourcesOutput.put(target, targetSource);
            }

            String fullClassName = (cir.getPackageName() != null && !cir.getPackageName().isBlank())
                    ? cir.getPackageName() + "." + cir.getPrimaryClassName()
                    : cir.getPrimaryClassName();

            boolean compiled = verifier.verifyCompilation(targetSource, fullClassName, target);
            report.recordTarget(resolved, compiled);
        }
        return report;
    }

    private void validateCir(CirCompilationUnit cir) {
        // Enforce STRICT RULE: CIR MUST NOT contain version-specific types
        String cirString = cir.toString();
        if (cirString.contains("RegistryObject") ||
                cirString.contains("DeferredBlock") ||
                cirString.contains("DeferredRegister") ||
                cirString.contains("ForgeRegistries") ||
                cirString.contains("ResourceLocation") ||
                cirString.contains("Identifier")) {
            throw new IllegalStateException("CIR validation failed: detected version-specific implementation types in CIR: " + cirString);
        }
    }
}
