package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.bytecode.ArtifactIndex;
import com.kyroxova.continuumlib.knowledge.diff.ApiDelta;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@CacheableTask
public abstract class CompareApisTask extends ArtifactRequestTask {
    @OutputFile public abstract RegularFileProperty getReportFile();
    @TaskAction public void compare() throws IOException {
        var request = request();
        var changes = new ApiDelta().compare(api(request, true), api(request, false));
        var report = new StringBuilder("ContinuumLib\tDECLARATION_DIFF_ONLY\nkind\towner\tname\tdescriptor\tbefore\tafter\n");
        for (var change : changes) {
            String[] columns = {change.kind().name(), change.owner(), change.name(), change.descriptor(), change.before(), change.after()};
            for (int i = 0; i < columns.length; i++) {
                if (i != 0) report.append('\t');
                report.append(columns[i].replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r"));
            }
            report.append('\n');
        }
        var output = getReportFile().get().getAsFile().toPath();
        protectOutput(output, java.util.List.of());
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
        getLogger().lifecycle("ContinuumLib found {} declaration differences; no semantic replacements inferred", changes.size());
    }
}
