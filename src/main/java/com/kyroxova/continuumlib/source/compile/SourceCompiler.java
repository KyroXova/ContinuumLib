package com.kyroxova.continuumlib.source.compile;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class SourceCompiler {

    public static void compile(
            List<Path> sourceFiles,
            List<Path> classpathJars,
            Path outputClassesDir,
            int javaVersion
    ) throws IOException {
        if (sourceFiles.isEmpty()) return;

        Files.createDirectories(outputClassesDir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler not available in current runtime environment");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);

        List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(outputClassesDir.toAbsolutePath().toString());
        options.add("-encoding");
        options.add("UTF-8");

        if (javaVersion >= 8) {
            options.add("--release");
            options.add(Integer.toString(javaVersion));
        }

        if (!classpathJars.isEmpty()) {
            options.add("-classpath");
            String cp = classpathJars.stream()
                    .map(p -> p.toAbsolutePath().normalize().toString())
                    .collect(Collectors.joining(File.pathSeparator));
            options.add(cp);
        }

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(sourceFiles);

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
        boolean success = Boolean.TRUE.equals(task.call());

        if (!success) {
            StringBuilder errorLog = new StringBuilder();
            errorLog.append("Compilation of generated source failed:\n");
            for (Diagnostic<? extends JavaFileObject> diag : diagnostics.getDiagnostics()) {
                if (diag.getKind() == Diagnostic.Kind.ERROR) {
                    errorLog.append("  ").append(diag.getSource() != null ? diag.getSource().getName() : "")
                            .append(":").append(diag.getLineNumber())
                            .append(" - ").append(diag.getMessage(Locale.ROOT)).append("\n");
                }
            }
            throw new IOException(errorLog.toString());
        }
    }
}
