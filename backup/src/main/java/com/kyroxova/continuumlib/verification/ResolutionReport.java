package com.kyroxova.continuumlib.verification;

import com.kyroxova.bootstrapper.config.TargetSpec;
import com.kyroxova.continuumlib.cir.CirResolutionResult;
import com.kyroxova.continuumlib.cir.CirResolutionStatus;
import com.kyroxova.continuumlib.resolver.ResolvedTargetModel;

import java.util.*;

/**
 * Encapsulates the multi-target resolution statistics and diagnostics.
 */
public final class ResolutionReport {

    private final TargetSpec baseSpec;
    private final Map<TargetSpec, List<CirResolutionResult>> targetResults = new LinkedHashMap<>();
    private final Map<TargetSpec, Boolean> targetCompilationStatus = new LinkedHashMap<>();

    public ResolutionReport(TargetSpec baseSpec) {
        this.baseSpec = Objects.requireNonNull(baseSpec, "baseSpec cannot be null");
    }

    public void recordTarget(ResolvedTargetModel model, boolean compiledSuccessfully) {
        TargetSpec target = model.getTargetProfile().getTargetSpec();
        targetResults.put(target, new ArrayList<>(model.getResolutionResults()));
        targetCompilationStatus.put(target, compiledSuccessfully);
    }

    public TargetSpec getBaseSpec() {
        return baseSpec;
    }

    public Map<TargetSpec, List<CirResolutionResult>> getTargetResults() {
        return Collections.unmodifiableMap(targetResults);
    }

    public Map<TargetSpec, Boolean> getTargetCompilationStatus() {
        return Collections.unmodifiableMap(targetCompilationStatus);
    }

    public long getCount(TargetSpec target, CirResolutionStatus status) {
        List<CirResolutionResult> list = targetResults.get(target);
        if (list == null) return 0;
        return list.stream().filter(r -> r.getStatus() == status).count();
    }

    public String generateReportString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ContinuumLib Resolution Report\n\n");
        sb.append("Base:\n");
        sb.append(capitalizeLoader(baseSpec.getLoader().getId())).append(" ").append(baseSpec.getVersion().getRaw()).append("\n\n");

        for (Map.Entry<TargetSpec, List<CirResolutionResult>> entry : targetResults.entrySet()) {
            TargetSpec target = entry.getKey();
            sb.append(String.format("Target: %s %s\n", capitalizeLoader(target.getLoader().getId()), target.getVersion().getRaw()));
            sb.append(String.format("EXACT       %4d\n", getCount(target, CirResolutionStatus.EXACT)));
            sb.append(String.format("ADAPTED     %4d\n", getCount(target, CirResolutionStatus.ADAPTED)));
            sb.append(String.format("EMULATED    %4d\n", getCount(target, CirResolutionStatus.EMULATED)));
            sb.append(String.format("DEGRADED    %4d\n", getCount(target, CirResolutionStatus.DEGRADED)));
            sb.append(String.format("UNSUPPORTED %4d\n", getCount(target, CirResolutionStatus.UNSUPPORTED)));

            Boolean compiled = targetCompilationStatus.get(target);
            if (compiled != null) {
                sb.append(String.format("COMPILATION %s\n", compiled ? "PASSED" : "FAILED"));
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String capitalizeLoader(String loader) {
        if ("forge".equalsIgnoreCase(loader)) return "Forge";
        if ("neoforge".equalsIgnoreCase(loader)) return "NeoForge";
        if ("fabric".equalsIgnoreCase(loader)) return "Fabric";
        if ("quilt".equalsIgnoreCase(loader)) return "Quilt";
        if ("paper".equalsIgnoreCase(loader)) return "Paper";
        if ("spigot".equalsIgnoreCase(loader)) return "Spigot";
        return loader;
    }
}
