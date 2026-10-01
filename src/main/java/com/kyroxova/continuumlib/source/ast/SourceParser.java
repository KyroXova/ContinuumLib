package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class SourceParser {
    private final JavaParser parser;

    public SourceParser(List<Path> sourceRoots, List<Path> classpathJars) {
        var typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        for (Path jar : classpathJars) {
            try {
                if (Files.exists(jar) && !Files.isDirectory(jar)) {
                    typeSolver.add(new JarTypeSolver(jar));
                }
            } catch (IOException ignored) {
                // Supplemental classpath entries remain optional for parsing. Required artifacts are verified elsewhere.
            }
        }

        for (Path root : sourceRoots) {
            if (Files.exists(root) && Files.isDirectory(root)) {
                typeSolver.add(new JavaParserTypeSolver(root));
            }
        }

        var config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.CURRENT)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        this.parser = new JavaParser(config);
    }

    public List<SourceUnit> parseDirectory(Path sourceDir) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            return List.of();
        }

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            javaFiles = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
        return parseFiles(sourceDir, javaFiles);
    }

    /** Parses an already-selected set of source files. File selection intentionally happens before AST creation. */
    public List<SourceUnit> parseFiles(Path sourceRoot, Collection<Path> sourceFiles) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(sourceFiles, "sourceFiles");

        Path root = sourceRoot.toAbsolutePath().normalize();
        List<Path> ordered = sourceFiles.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .toList();

        List<SourceUnit> units = new ArrayList<>();
        for (Path file : ordered) {
            if (!file.startsWith(root)) {
                throw new IOException("Source file is outside source root: " + file);
            }
            if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) {
                throw new IOException("Selected source is not a Java file: " + file);
            }

            String relative = root.relativize(file).toString().replace('\\', '/');
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                units.add(new SourceUnit(file, relative, result.getResult().get()));
            } else {
                throw new IOException("Failed to parse Java file: " + file + " -> " + result.getProblems());
            }
        }
        return List.copyOf(units);
    }

    public SourceUnit parseString(String relativePath, String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return new SourceUnit(Path.of(relativePath), relativePath, result.getResult().get());
        }
        throw new IllegalArgumentException("Failed to parse code for " + relativePath + ": " + result.getProblems());
    }
}
