package com.kyroxova.continuumlib.source;

import com.github.javaparser.ast.CompilationUnit;
import com.kyroxova.continuumlib.bytecode.*;
import com.kyroxova.continuumlib.source.ast.SourceParser;
import com.kyroxova.continuumlib.source.ast.SourceUnit;
import com.kyroxova.continuumlib.source.compile.SourceCompiler;
import com.kyroxova.continuumlib.source.compile.TargetJarPackager;
import com.kyroxova.continuumlib.source.rule.SourceMigrationPlan;
import com.kyroxova.continuumlib.source.transform.SourceTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SourceTransformationTest {

    @Test
    void testOriginalSourceNeverModified(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path originalFile = srcDir.resolve("ExampleMod.java");
        String originalContent = """
                package com.example;
                
                public class ExampleMod {
                    public void init() {
                        System.out.println("Hello native Minecraft!");
                    }
                }
                """;
        Files.writeString(originalFile, originalContent, StandardCharsets.UTF_8);

        SourceParser parser = new SourceParser(List.of(srcDir), List.of());
        List<SourceUnit> units = parser.parseDirectory(srcDir);
        assertEquals(1, units.size());

        SourceMigrationPlan plan = SourceMigrationPlan.builder().build();
        SourceTransformer transformer = new SourceTransformer(plan);

        Path genDir = tempDir.resolve("build/continuum/generated-src");
        List<Path> generated = transformer.transformAndWrite(units, genDir);

        assertEquals(1, generated.size());
        // Verify original file on disk remains COMPLETELY unchanged
        assertEquals(originalContent, Files.readString(originalFile, StandardCharsets.UTF_8));
        assertTrue(Files.exists(generated.get(0)));
    }

    @Test
    void testMethodRename() {
        String code = """
                package com.example;
                import com.example.Block;
                
                public class Mod {
                    public void place(Block block) {
                        block.oldMethod("param");
                    }
                }
                """;
        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("Mod.java", code);

        SourceMigrationPlan plan = SourceMigrationPlan.builder()
                .addMethodRename("com/example/Block", "oldMethod", "newMethod")
                .build();

        SourceTransformer transformer = new SourceTransformer(plan);
        CompilationUnit transformed = transformer.transformAst(unit.ast());

        String result = transformed.toString();
        assertTrue(result.contains("block.newMethod(\"param\")"), "Result was: " + result);
        assertFalse(result.contains("oldMethod"), "Result was: " + result);
    }

    @Test
    void testClassRenameAndImports() {
        String code = """
                package com.example;
                import com.example.oldpkg.OldBlock;
                
                public class Mod {
                    private OldBlock block = new OldBlock();
                }
                """;
        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("Mod.java", code);

        SourceMigrationPlan plan = SourceMigrationPlan.builder()
                .addClassRename("com/example/oldpkg/OldBlock", "com/example/newpkg/NewBlock")
                .build();

        SourceTransformer transformer = new SourceTransformer(plan);
        CompilationUnit transformed = transformer.transformAst(unit.ast());

        String result = transformed.toString();
        assertTrue(result.contains("import com.example.newpkg.NewBlock;"), "Missing new import: " + result);
        assertTrue(result.contains("NewBlock block = new NewBlock();"), "Missing renamed class usage: " + result);
        assertFalse(result.contains("OldBlock"), "Still contains OldBlock: " + result);
    }

    @Test
    void testConstructorToStaticFactory() {
        String code = """
                package com.example;
                import net.minecraft.resources.ResourceLocation;
                
                public class Mod {
                    public ResourceLocation createId() {
                        return new ResourceLocation("mod", "block");
                    }
                }
                """;
        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("Mod.java", code);

        SourceMigrationPlan plan = SourceMigrationPlan.builder()
                .addConstructorToFactory(
                        "net/minecraft/resources/ResourceLocation",
                        "net/minecraft/resources/ResourceLocation",
                        "fromNamespaceAndPath")
                .build();

        SourceTransformer transformer = new SourceTransformer(plan);
        CompilationUnit transformed = transformer.transformAst(unit.ast());

        String result = transformed.toString();
        assertTrue(result.contains("ResourceLocation.fromNamespaceAndPath(\"mod\", \"block\")"), "Result was: " + result);
        assertFalse(result.contains("new ResourceLocation("), "Result was: " + result);
    }

    @Test
    void testFactoryToConstructor() {
        // Direct test of the user's specific requirement:
        // Identifier.of("mod", "block") -> new Identifier("mod", "block")
        String code = """
                package com.example;
                import net.minecraft.util.Identifier;
                
                public class Mod {
                    public Identifier createId() {
                        return Identifier.of("mod", "block");
                    }
                }
                """;
        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("Mod.java", code);

        SourceMigrationPlan plan = SourceMigrationPlan.builder()
                .addFactoryToConstructor("net/minecraft/util/Identifier", "of", "net/minecraft/util/Identifier")
                .build();

        SourceTransformer transformer = new SourceTransformer(plan);
        CompilationUnit transformed = transformer.transformAst(unit.ast());

        String result = transformed.toString();
        assertTrue(result.contains("new Identifier(\"mod\", \"block\")"), "Result was: " + result);
        assertFalse(result.contains("Identifier.of("), "Result was: " + result);
    }

    @Test
    void testFieldToAccessor() {
        String code = """
                package com.example;
                import net.minecraft.world.level.block.Block;
                
                public class Mod {
                    public float check(Block block) {
                        return block.hardness;
                    }
                }
                """;
        SourceParser parser = new SourceParser(List.of(), List.of());
        SourceUnit unit = parser.parseString("Mod.java", code);

        SourceMigrationPlan plan = SourceMigrationPlan.builder()
                .addFieldToAccessor("net/minecraft/world/level/block/Block", "hardness", "getHardness")
                .build();

        SourceTransformer transformer = new SourceTransformer(plan);
        CompilationUnit transformed = transformer.transformAst(unit.ast());

        String result = transformed.toString();
        assertTrue(result.contains("block.getHardness()"), "Result was: " + result);
        assertFalse(result.contains("block.hardness"), "Result was: " + result);
    }

    @Test
    void testEndToEndCompilationPackagingAndAudit(@TempDir Path tempDir) throws IOException {
        // 1. Create a dummy target API jar: com.minecraft.Identifier with constructor (String, String)
        Path targetJar = tempDir.resolve("target-api.jar");
        createTargetApiJar(targetJar);

        // 2. Original developer source in src/
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir.resolve("com/example"));
        Path sourceFile = srcDir.resolve("com/example/MyMod.java");
        String originalSource = """
                package com.example;
                import com.minecraft.Identifier;
                
                public class MyMod {
                    public Identifier makeId() {
                        return Identifier.of("testmod", "ruby_block");
                    }
                }
                """;
        Files.writeString(sourceFile, originalSource, StandardCharsets.UTF_8);

        // 3. Parse original source
        SourceParser parser = new SourceParser(List.of(srcDir), List.of());
        List<SourceUnit> units = parser.parseDirectory(srcDir);
        assertEquals(1, units.size());

        // 4. Migration plan: FactoryToConstructor: Identifier.of -> new Identifier
        SourceMigrationPlan plan = SourceMigrationPlan.builder()
                .addFactoryToConstructor("com/minecraft/Identifier", "of", "com/minecraft/Identifier")
                .build();

        // 5. Transform AST and write to temporary generated source directory
        Path genDir = tempDir.resolve("build/continuum/target/generated-src");
        SourceTransformer transformer = new SourceTransformer(plan);
        List<Path> generatedFiles = transformer.transformAndWrite(units, genDir);

        assertEquals(1, generatedFiles.size());
        // Verify original source is untouched
        assertEquals(originalSource, Files.readString(sourceFile, StandardCharsets.UTF_8));

        String transformedContent = Files.readString(generatedFiles.get(0), StandardCharsets.UTF_8);
        assertTrue(transformedContent.contains("new Identifier(\"testmod\", \"ruby_block\")"));

        // 6. Compile generated sources against target API jar
        Path classesDir = tempDir.resolve("build/continuum/target/classes");
        SourceCompiler.compile(generatedFiles, List.of(targetJar), classesDir, 17);
        assertTrue(Files.exists(classesDir.resolve("com/example/MyMod.class")));

        // 7. Package target JAR
        Path outputJar = tempDir.resolve("build/libs/MyMod-target.jar");
        TargetJarPackager.packageJar(classesDir, null, outputJar);
        assertTrue(Files.exists(outputJar));

        // 8. Audit resulting bytecode against target declarations
        var targetIndex = ArtifactIndex.read(List.of(targetJar));
        var audit = new TargetReferenceAudit(targetIndex.classes());
        var scanner = new ReferenceScanner();
        List<ReferenceScanner.Use> uses = new ArrayList<>();
        try (var jar = new java.util.jar.JarFile(outputJar.toFile())) {
            for (var entry : Collections.list(jar.entries())) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (var in = jar.getInputStream(entry)) {
                        uses.addAll(scanner.scan(in.readAllBytes()));
                    }
                }
            }
        }

        assertFalse(uses.isEmpty(), "Expected bytecode uses in packaged jar");
        for (var use : uses) {
            if (use.target().owner().startsWith("com/minecraft/")) {
                var check = audit.check(use);
                assertEquals(TargetReferenceAudit.Status.DECLARATION_FOUND, check.status(),
                        "Audit failed for " + use.target() + " in " + use.caller() + ": " + check.status());
            }
        }
    }

    private static void createTargetApiJar(Path jarPath) throws IOException {
        // Generates com/minecraft/Identifier class bytecode
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/minecraft/Identifier", null, "java/lang/Object", null);

        // public Identifier(String namespace, String path)
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 3);
        mv.visitEnd();

        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();

        try (var jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("com/minecraft/Identifier.class"));
            jos.write(classBytes);
            jos.closeEntry();
        }
    }
}
