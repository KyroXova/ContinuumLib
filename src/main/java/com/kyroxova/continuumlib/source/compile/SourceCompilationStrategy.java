package com.kyroxova.continuumlib.source.compile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface SourceCompilationStrategy {
    void compile(
            List<Path> sourceFiles,
            List<Path> classpathJars,
            Path outputClassesDir,
            int javaVersion
    ) throws IOException;
}
