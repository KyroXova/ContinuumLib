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
                // Ignore unreadable or corrupt supplemental jars in type solver
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

        List<SourceUnit> units = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            var javaFiles = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path file : javaFiles) {
                String relative = sourceDir.relativize(file).toString().replace('\\', '/');
                ParseResult<CompilationUnit> result = parser.parse(file);
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    units.add(new SourceUnit(file, relative, result.getResult().get()));
                } else {
                    throw new IOException("Failed to parse Java file: " + file + " -> " + result.getProblems());
                }
            }
        }
        return units;
    }

    public List<SourceUnit> parseFiles(List<Path> files, List<Path> sourceRoots) throws IOException {
        List<SourceUnit> units = new ArrayList<>();
        for (Path file : files) {
            Path matchingRoot = sourceRoots.get(0);
            for (Path root : sourceRoots) {
                if (file.startsWith(root)) {
                    matchingRoot = root;
                    break;
                }
            }
            String relative = matchingRoot.relativize(file).toString().replace('\\', '/');
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                units.add(new SourceUnit(file, relative, result.getResult().get()));
            } else {
                throw new IOException("Failed to parse Java file: " + file + " -> " + result.getProblems());
            }
        }
        return units;
    }

    public SourceUnit parseString(String relativePath, String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return new SourceUnit(Path.of(relativePath), relativePath, result.getResult().get());
        }
        throw new IllegalArgumentException("Failed to parse code for " + relativePath + ": " + result.getProblems());
    }
}
