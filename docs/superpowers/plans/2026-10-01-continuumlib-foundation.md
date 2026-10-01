# ContinuumLib Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the prototype and deliver the first compiler-first ContinuumLib vertical slice that recognizes Forge 1.18.2 block registration and produces a verified semantic resolution plan.

**Architecture:** Preserve the conventional single-project Gradle layout and separate responsibilities through focused packages built around a loader-neutral model. The first slice backs up the old source, establishes consumer configuration and diagnostics, imports pinned symbol data, analyzes the supplied Forge registration pattern, and resolves it through a typed block-registry capability.

**Tech Stack:** Java 17, Gradle 8, JUnit 5, JavaParser Symbol Solver, Gson, Gradle TestKit.

**Spec:** `docs/superpowers/specs/2026-10-01-continuumlib-rebuild-design.md`

## Global Constraints

- Move the complete old source tree to exactly `E:/Minecraft/ContinuumLib/backup/src/` before creating new active source trees.
- `backup/` must not participate in compilation, packaging, publication, testing, or runtime classpaths.
- User-facing names use `ContinuumLib`; internal module names stay short.
- Code under `com.kyroxova.continuumlib.model` cannot depend on Minecraft, Forge, NeoForge, Fabric, Gradle, JavaParser, or implementation packages.
- Unsupported or ambiguous operations are explicit errors; there is no silent Forge 1.18.2 fallback.
- Tests that claim target compatibility must eventually use real pinned artifacts; generated stubs cannot satisfy compatibility acceptance.
- Update `docs/` and `wiki/` in the same task as user-facing functionality.
- Preserve all current user changes during migration.

## Review Focus

- A dirty current `src/` tree must arrive byte-for-byte under `backup/src/`; Task 1 verifies file hashes before removing the active original.
- Gradle must never discover backup Java files; Task 1 asserts no compile task includes a path under `backup/`.
- Overloaded or obfuscated symbols must be keyed by owner, kind, name, descriptor, namespace, and environment; Task 3 tests descriptor-distinct overloads.
- Nested/chained Forge registration expressions must retain constructor and property semantics; Task 4 tests the supplied `BuildersWorkbenchBlock` example.
- Missing or contradictory environment data must fail with evidence instead of selecting defaults; Task 2 tests both cases.

---

## File Structure for This Slice

```text
backup/src/                         preserved prototype
src/main/java/com/kyroxova/continuumlib/api/            public contracts
src/main/java/com/kyroxova/continuumlib/model/          neutral model
src/main/java/com/kyroxova/continuumlib/analyzer/       Java analysis
src/main/java/com/kyroxova/continuumlib/knowledge/      symbols and capabilities
src/main/java/com/kyroxova/continuumlib/resolver/       resolution planning
src/main/java/com/kyroxova/continuumlib/gradle/         Gradle integration
src/test/java/com/kyroxova/continuumlib/                tests
docs/                              maintained developer documentation
wiki/                              Git wiki-compatible pages
```

## Task 1: Preserve Prototype and Establish the Single-Project Build

**Files:**
- Move: `src/**` → `backup/src/**`
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `wiki/Home.md`
- Create: `wiki/Development-Status.md`
- Test: `src/test/java/com/kyroxova/continuumlib/structure/RepositoryLayoutTest.java`

**Interfaces:**
- Consumes: current repository and dirty working-tree contents.
- Produces: isolated `backup/src/`, conventional active source roots, Java/JUnit conventions, and `RepositoryLayoutTest`.

- [ ] **Step 1: Record and verify the migration input**

Generate a sorted SHA-256 manifest for every file under `src/` and save it outside both source trees during the move.

- [ ] **Step 2: Move the tree to the exact backup path**

Move `E:/Minecraft/ContinuumLib/src` to `E:/Minecraft/ContinuumLib/backup/src` without rewriting file contents, then compare the destination manifest with the recorded source manifest.

- [ ] **Step 3: Write the failing repository-layout test**

`RepositoryLayoutTest` asserts that `backup/src/main` and `backup/src/test` exist, the active conventional source directories exist, and no Gradle `SourceSet` contains a path under `backup/`.

- [ ] **Step 4: Run the layout test and verify failure**

Run: `.\gradlew.bat test --tests *RepositoryLayoutTest`

Expected: failure because the active source roots are not configured.

- [ ] **Step 5: Create the minimal single-project Gradle build**

Use Java 17 toolchains, JUnit 5, Maven Central, and group `com.kyroxova.continuumlib`. Keep conventional source sets and do not add `backup/` to any source directory.

- [ ] **Step 6: Update project documentation**

Document the backup location, active modules, current milestone, and non-compilation guarantee in `wiki/Home.md` and `wiki/Development-Status.md`.

- [ ] **Step 7: Verify the layout**

Run: `.\gradlew.bat test --tests *RepositoryLayoutTest`

Expected: all modules listed and the test passes.

- [ ] **Step 8: Commit**

Commit only the migration, build skeleton, layout test, and corresponding wiki pages.

## Task 2: Environment, Configuration, and Diagnostics Model

**Files:**
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/environment/EnvironmentId.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/environment/Loader.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/environment/MappingNamespace.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/diagnostic/Diagnostic.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/diagnostic/DiagnosticCode.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/diagnostic/Severity.java`
- Create: `api/src/main/java/com/kyroxova/continuumlib/api/config/ContinuumLibConfiguration.java`
- Create: `api/src/main/java/com/kyroxova/continuumlib/api/config/OutputConfiguration.java`
- Create: `api/src/main/java/com/kyroxova/continuumlib/api/config/ConfigurationValidator.java`
- Test: `api/src/test/java/com/kyroxova/continuumlib/api/config/ConfigurationValidatorTest.java`
- Create: `docs/configuration.md`
- Create: `wiki/Configuration.md`

**Interfaces:**
- Produces: `EnvironmentId(minecraftVersion, loader, mappings, javaVersion)`, immutable `ContinuumLibConfiguration`, and `ConfigurationValidator.validate(ContinuumLibConfiguration): List<Diagnostic>`.

- [ ] **Step 1: Write failing configuration tests**

Test valid Forge 1.18.2 Mojmap configuration, missing version, unknown loader input, contradictory duplicate target, both output flags disabled, and absence of any implicit default environment.

- [ ] **Step 2: Verify failure**

Run: `.\gradlew.bat :api:test --tests *ConfigurationValidatorTest`

Expected: compilation failure because the configuration interfaces do not exist.

- [ ] **Step 3: Implement immutable environment and diagnostic records**

Use value-based records/enums with normalized but preserved version strings. Loader values initially include `FORGE`, `NEOFORGE`, `FABRIC`, and `QUILT`; namespaces include `OFFICIAL`, `MOJMAP`, `SRG`, `INTERMEDIARY`, `YARN`, and `OBFUSCATED`.

- [ ] **Step 4: Implement configuration validation**

Return ordered diagnostics with stable codes; do not throw for ordinary validation failures and do not insert defaults.

- [ ] **Step 5: Verify tests**

Run: `.\gradlew.bat :model:test :api:test`

Expected: all tests pass.

- [ ] **Step 6: Document configuration**

Add the consumer Gradle example, resource path, validation rules, and output options to both documentation targets.

- [ ] **Step 7: Commit**

Commit the model, validation tests, implementation, and documentation together.

## Task 3: Descriptor-Aware Symbol Database

**Files:**
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/symbol/SymbolKind.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/symbol/SymbolKey.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/symbol/SymbolName.java`
- Create: `knowledge/src/main/java/com/kyroxova/continuumlib/knowledge/symbol/SymbolDatabase.java`
- Create: `knowledge/src/main/java/com/kyroxova/continuumlib/knowledge/symbol/InMemorySymbolDatabase.java`
- Create: `knowledge/src/main/java/com/kyroxova/continuumlib/knowledge/mapping/MappingImporter.java`
- Create: `knowledge/src/main/java/com/kyroxova/continuumlib/knowledge/mapping/MappingProvenance.java`
- Test: `knowledge/src/test/java/com/kyroxova/continuumlib/knowledge/symbol/InMemorySymbolDatabaseTest.java`
- Create: `wiki/How-Resolution-Works.md`

**Interfaces:**
- Consumes: `EnvironmentId` and `MappingNamespace` from Task 2.
- Produces: `SymbolKey(environment, owner, kind, name, descriptor, namespace)`, `SymbolDatabase.find(SymbolKey)`, and a mapping-import SPI with provenance.

- [ ] **Step 1: Write failing symbol database tests**

Assert exact lookup, missing lookup, namespace alias lookup, constructor storage, field storage, and two same-named methods with different descriptors remaining distinct.

- [ ] **Step 2: Verify failure**

Run: `.\gradlew.bat :knowledge:test --tests *InMemorySymbolDatabaseTest`

Expected: compilation failure because symbol types do not exist.

- [ ] **Step 3: Implement symbol identities and in-memory storage**

Use immutable keys and indexes that never discard owner, descriptor, namespace, or environment.

- [ ] **Step 4: Define importer and provenance contracts**

`MappingImporter.importMappings(InputStream, EnvironmentId, MappingProvenance): Collection<SymbolName>` must reject malformed records with source-line diagnostics.

- [ ] **Step 5: Verify tests**

Run: `.\gradlew.bat :model:test :knowledge:test`

Expected: all tests pass.

- [ ] **Step 6: Document symbol resolution**

Explain the difference between mapping a name and adapting semantics, including owner/name/descriptor examples.

- [ ] **Step 7: Commit**

Commit symbol model, database, importer contracts, tests, and wiki update.

## Task 4: Analyze Forge 1.18.2 Block Registration

**Files:**
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/operation/SemanticOperation.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/operation/DeclareRegistry.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/operation/RegisterBlock.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/operation/BlockProperties.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/project/ProjectModel.java`
- Create: `analyzer-java/src/main/java/com/kyroxova/continuumlib/analyzer/java/JavaProjectAnalyzer.java`
- Create: `analyzer-java/src/main/java/com/kyroxova/continuumlib/analyzer/java/Forge1182BlockRegistrationRecognizer.java`
- Test: `analyzer-java/src/test/java/com/kyroxova/continuumlib/analyzer/java/Forge1182BlockRegistrationRecognizerTest.java`
- Fixture: `analyzer-java/src/test/resources/fixtures/forge-1.18.2/ModBlocks.java`
- Create: `docs/examples/block-registration.md`

**Interfaces:**
- Consumes: `EnvironmentId` and semantic symbol identities.
- Produces: `JavaProjectAnalyzer.analyze(Path, EnvironmentId): ProjectModel` containing ordered `SemanticOperation` values and source locations.

- [ ] **Step 1: Add the supplied block-registration fixture and failing assertions**

Assert one `DeclareRegistry(BLOCK, BuildScape.MODID)` and one `RegisterBlock` with id `builders_workbench`, implementation `BuildersWorkbenchBlock`, material `WOOD`, map color `COLOR_BROWN`, strength `2.5f`, and sound `WOOD`.

- [ ] **Step 2: Add adversarial failing tests**

Cover a custom block constructor with extra arguments, registration through a named supplier, chained properties in a different order, and an unresolved registration expression producing a source-located diagnostic instead of guessed output.

- [ ] **Step 3: Verify failure**

Run: `.\gradlew.bat :analyzer-java:test --tests *Forge1182BlockRegistrationRecognizerTest`

Expected: compilation failure because operation and analyzer types do not exist.

- [ ] **Step 4: Implement the minimal project and operation model**

Represent expressions that cannot yet be normalized as typed source expressions with locations; do not reduce arbitrary expressions to unvalidated strings.

- [ ] **Step 5: Implement the Forge 1.18.2 recognizer**

Use JavaParser AST and symbol solving. Match resolved owners and signatures rather than imports or simple names alone.

- [ ] **Step 6: Verify tests**

Run: `.\gradlew.bat :model:test :analyzer-java:test`

Expected: all tests pass.

- [ ] **Step 7: Document the supported example**

Show the consumer source, semantic interpretation, current limitations, and diagnostic behavior.

- [ ] **Step 8: Commit**

Commit model, recognizer, fixtures, tests, and example documentation.

## Task 5: Resolve the First Block Registry Capability

**Files:**
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/resolution/ResolutionStatus.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/resolution/Resolution.java`
- Create: `model/src/main/java/com/kyroxova/continuumlib/model/resolution/ResolutionPlan.java`
- Create: `knowledge/src/main/java/com/kyroxova/continuumlib/knowledge/capability/CapabilityProvider.java`
- Create: `knowledge/src/main/java/com/kyroxova/continuumlib/knowledge/capability/registry/Forge1182BlockRegistryCapability.java`
- Create: `resolver/src/main/java/com/kyroxova/continuumlib/resolver/ContinuumLibResolver.java`
- Test: `resolver/src/test/java/com/kyroxova/continuumlib/resolver/BlockRegistryResolutionTest.java`
- Create: `docs/compatibility-model.md`
- Update: `wiki/Development-Status.md`

**Interfaces:**
- Consumes: `ProjectModel`, source and target `EnvironmentId`, and registered `CapabilityProvider` instances.
- Produces: `ContinuumLibResolver.resolve(ProjectModel, EnvironmentId, EnvironmentId): ResolutionPlan` with one status and provenance record per relevant operation.

- [ ] **Step 1: Write failing resolution tests**

Assert `DIRECT` for Forge 1.18.2 to itself, explicit `UNSUPPORTED` for an unregistered target, complete operation coverage, stable diagnostic codes, and no resolution result being omitted.

- [ ] **Step 2: Verify failure**

Run: `.\gradlew.bat :resolver:test --tests *BlockRegistryResolutionTest`

Expected: compilation failure because resolution contracts do not exist.

- [ ] **Step 3: Implement resolution contracts and provider SPI**

Require every provider to declare source predicates, target predicates, supported operation types, priority, and provenance.

- [ ] **Step 4: Implement same-environment Forge 1.18.2 block capability**

Return `DIRECT` only after confirming the referenced symbols exist in the source/target symbol database; otherwise return explicit diagnostics.

- [ ] **Step 5: Verify the complete foundation**

Run: `.\gradlew.bat clean test`

Expected: all active-module tests pass and no source under `backup/` is compiled.

- [ ] **Step 6: Update documentation and wiki status**

Record implemented statuses, exact supported pattern, unsupported targets, module ownership, test command, and the next target-native emission milestone.

- [ ] **Step 7: Commit**

Commit resolution contracts, capability, tests, and documentation.

## Completion Check

- [ ] `E:/Minecraft/ContinuumLib/backup/src/` contains the complete prior source tree.
- [ ] `backup/` is absent from active Gradle source sets and output JARs.
- [ ] `.\gradlew.bat clean test` succeeds without compiling prototype code.
- [ ] The supplied Forge 1.18.2 `ModBlocks` example produces typed semantic operations.
- [ ] Every recognized operation has an explicit resolution status and provenance.
- [ ] `docs/` and `wiki/` accurately describe only implemented behavior.
