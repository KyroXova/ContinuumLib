package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.bytecode.*;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;

/** Audits every scanned member instruction, including unchanged instructions and method handles. */
@CacheableTask
public abstract class AuditTargetTask extends ArtifactRequestTask {
    public AuditTargetTask() { getFailOnUnresolved().convention(false); }
    @Input public abstract Property<Boolean> getFailOnUnresolved();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getInputJar();
    @OutputFile public abstract RegularFileProperty getReportFile();
    @OutputFile public abstract RegularFileProperty getTypeReportFile();

    @TaskAction public void audit() throws IOException {
        var request = request();
        var input = getInputJar().get().getAsFile().toPath();
        var classes = new HashMap<>(outputApi(request));
        for (var entry : ArtifactIndex.read(List.of(input)).classes().entrySet())
            if (classes.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new GradleException("Mod duplicates target API class: " + entry.getKey());
        var audit = new TargetReferenceAudit(classes);
        var scanner = new ReferenceScanner();
        var report = getReportFile().get().getAsFile().toPath();
        var typeReport = getTypeReportFile().get().getAsFile().toPath();
        protectOutput(report, List.of(input));
        protectOutput(typeReport, List.of(input));
        if (sameFile(report, typeReport)) throw new GradleException("Audit reports require distinct output paths");
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.createDirectories(typeReport.toAbsolutePath().getParent());
        var counts = new EnumMap<TargetReferenceAudit.Status, Integer>(TargetReferenceAudit.Status.class);
        int inspectedClasses = 0, missingTypes = 0;
        try (var output = Files.newBufferedWriter(report, StandardCharsets.UTF_8);
             var types = Files.newBufferedWriter(typeReport, StandardCharsets.UTF_8);
             var jar = new JarFile(input.toFile())) {
            output.write("# DECLARATION_AUDIT_ONLY: access, dispatch, reflection, resources and gameplay are not certified\n");
            output.write("status\tcaller\tmethod\tcallerDescriptor\tline\towner\tmember\tdescriptor\topcode\thandle\tdetail\n");
            types.write("# TYPE_INVENTORY_ONLY: presence does not certify access or compatibility\nstatus\tclass\treferencedType\n");
            for (var entry : Collections.list(jar.entries()).stream().sorted(Comparator.comparing(java.util.jar.JarEntry::getName)).toList()) {
                if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                byte[] bytes;
                try (var stream = jar.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                String caller = new org.objectweb.asm.ClassReader(bytes).getClassName();
                inspectedClasses++;
                for (String type : scanner.types(bytes)) {
                    if (!classes.containsKey(type)) missingTypes++;
                    types.write((classes.containsKey(type) ? "TYPE_PRESENT" : "TYPE_MISSING")
                            + "\t" + escape(caller) + "\t" + escape(type) + "\n");
                }
                for (var use : scanner.scan(bytes)) {
                    var finding = audit.check(use);
                    counts.merge(finding.status(), 1, Integer::sum);
                    output.write(String.join("\t", finding.status().name(), escape(use.caller().owner()), escape(use.caller().name()),
                            escape(use.caller().descriptor()), Integer.toString(use.line()), escape(use.target().owner()),
                            escape(use.target().name()), escape(use.target().descriptor()), Integer.toString(use.opcode()),
                            Boolean.toString(use.handle()), escape(finding.detail())) + "\n");
                }
            }
        }
        getLogger().lifecycle("ContinuumLib target declaration audit (NOT_CERTIFIED): {}. Report: {}", counts, report);
        long unresolved = counts.entrySet().stream()
                .filter(entry -> entry.getKey() != TargetReferenceAudit.Status.DECLARATION_FOUND)
                .mapToLong(Map.Entry::getValue).sum();
        if (getFailOnUnresolved().get() && (inspectedClasses == 0 || missingTypes > 0 || unresolved > 0))
            throw new GradleException("ContinuumLib strict declaration audit failed: " + unresolved
                    + " unresolved member references, " + missingTypes + " missing type references, "
                    + inspectedClasses + " inspected classes. See " + report + " and " + typeReport
                    + ". Declaration checks do not certify gameplay compatibility.");
    }
    private static boolean sameFile(java.nio.file.Path first, java.nio.file.Path second) throws IOException {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }
}
