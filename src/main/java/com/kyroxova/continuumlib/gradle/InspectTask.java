package com.kyroxova.continuumlib.gradle;

import com.kyroxova.continuumlib.bytecode.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.jar.JarFile;

@CacheableTask
public abstract class InspectTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();
    @OutputFile public abstract RegularFileProperty getReportFile();

    @TaskAction public void inspect() throws IOException {
        var report = getReportFile().get().getAsFile().toPath();
        StringBuilder out = new StringBuilder("ContinuumLib\tINVENTORY_ONLY\n");
        var inspector = new ClassInspector();
        var scanner = new ReferenceScanner();
        try (var jar = new JarFile(getInputJar().get().getAsFile())) {
            if (jar.isMultiRelease()) throw new IOException("Multi-release input requires explicit runtime selection");
            var entries = jar.stream().filter(e -> !e.isDirectory() && e.getName().endsWith(".class")
                    && !e.getName().equals("module-info.class")).sorted(Comparator.comparing(e -> e.getName())).toList();
            for (var entry : entries) {
                byte[] bytes;
                try (var input = jar.getInputStream(entry)) { bytes = input.readAllBytes(); }
                var info = inspector.inspect(bytes);
                row(out, "CLASS", info.name(), info.superName(), String.join(",", info.interfaces()));
                for (String type : scanner.types(bytes)) row(out, "TYPE", info.name(), type);
                for (var field : info.fields()) row(out, "FIELD", info.name(), field.name(), field.descriptor());
                for (var method : info.methods()) row(out, "METHOD", info.name(), method.name(), method.descriptor());
                for (var ref : scanner.scan(bytes)) row(out, "USE", ref.caller().owner(), ref.caller().name(),
                        ref.caller().descriptor(), ref.target().owner(), ref.target().name(), ref.target().descriptor(),
                        Integer.toString(ref.opcode()), Boolean.toString(ref.handle()), Integer.toString(ref.line()));
            }
        }
        Files.createDirectories(report.getParent());
        Files.writeString(report, out, StandardCharsets.UTF_8);
    }
    private static void row(StringBuilder out, String... columns) {
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) out.append('\t');
            String value = columns[i] == null ? "" : columns[i];
            out.append(value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r"));
        }
        out.append('\n');
    }
}
