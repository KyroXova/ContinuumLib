package com.kyroxova.continuumlib.source.compile;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public final class SourceCompiler {

    public static void compile(
            List<Path> sourceFiles,
            List<Path> classpathJars,
            Path outputClassesDir,
            int javaVersion
    ) throws IOException {
        Objects.requireNonNull(sourceFiles, "sourceFiles");
        Objects.requireNonNull(classpathJars, "classpathJars");
        Objects.requireNonNull(outputClassesDir, "outputClassesDir");
        if (sourceFiles.isEmpty()) return;

        Files.createDirectories(outputClassesDir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler not available in current runtime environment");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(outputClassesDir.toAbsolutePath().normalize().toString());
            options.add("-encoding");
            options.add("UTF-8");
            options.add("--release");
            options.add(Integer.toString(javaVersion));

            if (!classpathJars.isEmpty()) {
                options.add("-classpath");
                String cp = classpathJars.stream()
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .collect(Collectors.joining(File.pathSeparator));
                options.add(cp);
            }

            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

            if (!Boolean.TRUE.equals(task.call())) {
                throw new IOException(formatDiagnostics(diagnostics));
            }
        }
    }

    public static void compileWithJavac(
            Path javacExecutable,
            List<Path> sourceFiles,
            List<Path> classpathJars,
            Path outputClassesDir,
            int javaVersion
    ) throws IOException {
        compileWithJavac(
                javacExecutable,
                sourceFiles,
                classpathJars,
                outputClassesDir,
                javaVersion,
                Math.max(9, javaVersion)
        );
    }

    public static void compileWithJavac(
            Path javacExecutable,
            List<Path> sourceFiles,
            List<Path> classpathJars,
            Path outputClassesDir,
            int javaVersion,
            int compilerJavaVersion
    ) throws IOException {
        Objects.requireNonNull(javacExecutable, "javacExecutable");
        Objects.requireNonNull(sourceFiles, "sourceFiles");
        Objects.requireNonNull(classpathJars, "classpathJars");
        Objects.requireNonNull(outputClassesDir, "outputClassesDir");
        if (sourceFiles.isEmpty()) return;

        Path javac = javacExecutable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(javac)) {
            throw new IOException("Resolved javac executable does not exist: " + javac);
        }

        Path output = outputClassesDir.toAbsolutePath().normalize();
        Files.createDirectories(output);
        Path argFile = Files.createTempFile(output, ".continuum-javac-", ".args");

        try {
            List<String> args = new ArrayList<>();
            args.add("-d");
            args.add(argFileToken(output));
            args.add("-encoding");
            args.add("UTF-8");
            args.addAll(externalLanguageLevelOptions(javaVersion, compilerJavaVersion));
            if (!classpathJars.isEmpty()) {
                args.add("-classpath");
                String cp = classpathJars.stream()
                        .map(path -> portable(path.toAbsolutePath().normalize()))
                        .collect(Collectors.joining(File.pathSeparator));
                args.add(argFileToken(cp));
            }
            sourceFiles.stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .map(SourceCompiler::portable)
                    .map(SourceCompiler::argFileToken)
                    .forEach(args::add);

            Files.writeString(
                    argFile,
                    String.join(System.lineSeparator(), args) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            Process process = new ProcessBuilder(javac.toString(), "@" + argFile.toAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            String compilerOutput;
            try (var input = process.getInputStream()) {
                compilerOutput = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exit;
            try {
                exit = process.waitFor();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("Interrupted while compiling generated source with " + javac, interrupted);
            }

            if (exit != 0) {
                throw new IOException("Compilation of generated source failed using " + javac + ":\n" + compilerOutput);
            }
        } finally {
            Files.deleteIfExists(argFile);
        }
    }

    static List<String> externalLanguageLevelOptions(int targetJavaVersion, int compilerJavaVersion) {
        if (targetJavaVersion < 8) {
            throw new IllegalArgumentException("Unsupported Java target version: " + targetJavaVersion);
        }
        if (compilerJavaVersion < targetJavaVersion) {
            throw new IllegalArgumentException(
                    "Compiler Java " + compilerJavaVersion
                            + " cannot target Java " + targetJavaVersion
            );
        }
        if (compilerJavaVersion >= 9) {
            return List.of("--release", Integer.toString(targetJavaVersion));
        }
        return List.of(
                "-source", Integer.toString(targetJavaVersion),
                "-target", Integer.toString(targetJavaVersion)
        );
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder errorLog = new StringBuilder("Compilation of generated source failed:")
                .append(System.lineSeparator());
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;
            errorLog.append("  ")
                    .append(diagnostic.getSource() != null ? diagnostic.getSource().getName() : "")
                    .append(":").append(diagnostic.getLineNumber())
                    .append(" - ").append(diagnostic.getMessage(Locale.ROOT))
                    .append(System.lineSeparator());
        }
        return errorLog.toString();
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String argFileToken(Path path) {
        return argFileToken(portable(path));
    }

    private static String argFileToken(String value) {
        return "\"" + value.replace("\\", "/").replace("\"", "\\\"") + "\"";
    }
}
