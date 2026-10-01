package com.kyroxova.continuumlib.resolver;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.cir.CirCompilationUnit;
import com.kyroxova.continuumlib.cir.CirResolutionResult;
import com.kyroxova.continuumlib.cir.CirResolutionStatus;
import com.kyroxova.continuumlib.knowledgebase.profile.TargetProfile;

import java.util.*;

/**
 * Resolved target model holding target API decisions and diagnostic results.
 */
public final class ResolvedTargetModel {

    private final CirCompilationUnit cir;
    private final TargetSpec baseSpec;
    private final TargetProfile targetProfile;
    private final List<CirResolutionResult> resolutionResults = new ArrayList<>();
    private final Set<String> targetImports = new LinkedHashSet<>();

    public ResolvedTargetModel(CirCompilationUnit cir, TargetSpec baseSpec, TargetProfile targetProfile) {
        this.cir = Objects.requireNonNull(cir, "cir cannot be null");
        this.baseSpec = Objects.requireNonNull(baseSpec, "baseSpec cannot be null");
        this.targetProfile = Objects.requireNonNull(targetProfile, "targetProfile cannot be null");
    }

    public CirCompilationUnit getCir() {
        return cir;
    }

    public TargetSpec getBaseSpec() {
        return baseSpec;
    }

    public TargetProfile getTargetProfile() {
        return targetProfile;
    }

    public List<CirResolutionResult> getResolutionResults() {
        return Collections.unmodifiableList(resolutionResults);
    }

    public void addResult(CirResolutionResult result) {
        this.resolutionResults.add(result);
    }

    public Set<String> getTargetImports() {
        return targetImports;
    }

    public void addTargetImport(String imp) {
        this.targetImports.add(imp);
    }

    public long countStatus(CirResolutionStatus status) {
        return resolutionResults.stream().filter(r -> r.getStatus() == status).count();
    }

    public boolean hasUnsupported() {
        return countStatus(CirResolutionStatus.UNSUPPORTED) > 0;
    }
}
