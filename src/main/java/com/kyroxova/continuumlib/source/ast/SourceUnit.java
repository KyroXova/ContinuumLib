package com.kyroxova.continuumlib.source.ast;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.Objects;

public record SourceUnit(
        Path sourceFile,
        String relativePath,
        CompilationUnit ast
) {
    public SourceUnit {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(ast, "ast");
    }
}
