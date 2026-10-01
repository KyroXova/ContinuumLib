package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.knowledge.rule.*;
import org.gradle.api.*;
import org.gradle.api.file.*;
import org.gradle.api.tasks.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@CacheableTask
public abstract class ValidateRulesTask extends DefaultTask {
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getRuleFiles();
    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction public void validateRules() throws IOException {
        var files = getRuleFiles().getFiles().stream().sorted(Comparator.comparing(File::getAbsolutePath)).toList();
        if (files.isEmpty()) throw new GradleException("No ContinuumLib XML rule packs found under consumer data/continuumlib/knowledge");
        var packs = new ArrayList<RulePack>();
        for (File file : files) {
            try (var input = Files.newInputStream(file.toPath())) {
                packs.add(new RulePackReader().read(input));
            } catch (IOException e) {
                throw new GradleException("Cannot read ContinuumLib rule pack " + file + ": " + e.getMessage(), e);
            }
        }
        var catalog = new RuleCatalog(packs);
        for (RulePack pack : packs) catalog.select(pack.source(), pack.target());
        var output = new StringBuilder("ContinuumLib RULES_VALIDATED_ARTIFACTS_UNVERIFIED\n");
        packs.stream().sorted(Comparator.comparing(RulePack::id)).forEach(pack -> output.append(
                pack.id().replace("\r", "\\r").replace("\n", "\\n")).append('\n'));
        var report = getReportFile().get().getAsFile().toPath();
        Files.createDirectories(report.getParent());
        Files.writeString(report, output, StandardCharsets.UTF_8);
    }
}
