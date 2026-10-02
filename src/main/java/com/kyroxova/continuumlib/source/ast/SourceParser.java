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
        this(sourceRoots, classpathJars, 21);
    }

    public SourceParser(List<Path> sourceRoots, List<Path> classpathJars, int javaVersion) {
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
                .setLanguageLevel(languageLevel(javaVersion))
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
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            throw new IOException("At least one source root is required");
        }

        List<Path> roots = sourceRoots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .sorted(Comparator.comparingInt(Path::getNameCount)
                        .reversed()
                        .thenComparing(Path::toString))
                .toList();
        List<Path> ordered = files.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .sorted()
                .toList();

        List<SourceUnit> units = new ArrayList<>();
        for (Path file : ordered) {
            Path matchingRoot = roots.stream()
                    .filter(file::startsWith)
                    .findFirst()
                    .orElseThrow(() -> new IOException("Selected source is outside configured source roots: " + file));
            if (!Files.isRegularFile(file) || !file.toString().endsWith(".java")) {
                throw new IOException("Selected source is not a Java file: " + file);
            }

            String relative = matchingRoot.relativize(file).toString().replace('\\', '/');
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                units.add(new SourceUnit(file, relative, result.getResult().get()));
            } else {
                throw new IOException("Failed to parse Java file: " + file + " -> " + result.getProblems());
            }
        }
        return List.copyOf(units);
    }

    private static ParserConfiguration.LanguageLevel languageLevel(int javaVersion) {
        return switch (javaVersion) {
            case 8 -> ParserConfiguration.LanguageLevel.JAVA_8;
            case 9 -> ParserConfiguration.LanguageLevel.JAVA_9;
            case 10 -> ParserConfiguration.LanguageLevel.JAVA_10;
            case 11 -> ParserConfiguration.LanguageLevel.JAVA_11;
            case 12 -> ParserConfiguration.LanguageLevel.JAVA_12;
            case 13 -> ParserConfiguration.LanguageLevel.JAVA_13;
            case 14 -> ParserConfiguration.LanguageLevel.JAVA_14;
            case 15 -> ParserConfiguration.LanguageLevel.JAVA_15;
            case 16 -> ParserConfiguration.LanguageLevel.JAVA_16;
            case 17 -> ParserConfiguration.LanguageLevel.JAVA_17;
            case 18 -> ParserConfiguration.LanguageLevel.JAVA_18;
            case 19 -> ParserConfiguration.LanguageLevel.JAVA_19;
            case 20 -> ParserConfiguration.LanguageLevel.JAVA_20;
            case 21 -> ParserConfiguration.LanguageLevel.JAVA_21;
            case 22 -> ParserConfiguration.LanguageLevel.JAVA_22;
            case 23 -> ParserConfiguration.LanguageLevel.JAVA_23;
            case 24 -> ParserConfiguration.LanguageLevel.JAVA_24;
            case 25 -> ParserConfiguration.LanguageLevel.JAVA_25;
            case 26 -> ParserConfiguration.LanguageLevel.JAVA_26;
            default -> throw new IllegalArgumentException("Unsupported Java source level: " + javaVersion);
        };
    }

    public SourceUnit parseString(String relativePath, String code) {
        ParseResult<CompilationUnit> result = parser.parse(code);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return new SourceUnit(Path.of(relativePath), relativePath, result.getResult().get());
        }
        throw new IllegalArgumentException("Failed to parse code for " + relativePath + ": " + result.getProblems());
    }
}
