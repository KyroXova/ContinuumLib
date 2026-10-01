# ContinuumLib Rebuild Design

## 1. Purpose

ContinuumLib is a compiler-first Minecraft compatibility system. A mod developer writes ordinary native mod code for one declared Minecraft version, loader, and mapping namespace. ContinuumLib analyzes the complete mod project, resolves recognized Minecraft and loader operations for configured targets, compiles native target variants, and optionally packages those variants into one universal bootstrap JAR.

Automatic resolution is the default. Developer hooks are an explicit escape hatch for ambiguous or mod-specific behavior. ContinuumLib must never silently claim that an unresolved operation is compatible.

## 2. Success Criteria

The first production milestone is complete when a sample mod written only for Forge 1.18.2 can:

1. Register a custom block and item using ordinary Forge 1.18.2 source.
2. Be analyzed into loader-neutral semantic operations.
3. Produce native source for a selected later Forge or NeoForge target.
4. Compile against real Minecraft and loader artifacts rather than generated API stubs.
5. Produce a target-native JAR and a machine-readable resolution report.
6. Fail the build with a precise diagnostic for every unresolved or ambiguous relevant operation.

Later milestones expand capability coverage, version coverage, loader coverage, runtime tests, and universal packaging.

## 3. Non-Goals for the First Milestone

- Arbitrary runtime transformation of unknown mod bytecode.
- Complete support for every Minecraft version and loader in the initial release.
- Silent fallback through reflection when a target API cannot be resolved.
- Translating arbitrary third-party APIs without a registered knowledge provider.
- Treating symbol renaming as sufficient for semantic API changes.

ContinuumLib will eventually index all classes, constructors, methods, and fields exposed by supported artifacts. Indexing a symbol does not imply that ContinuumLib can automatically adapt its behavior.

## 4. Repository Migration

The current prototype is valuable research and must remain recoverable. Before rebuilding:

1. Record the current Git state and preserve all user changes.
2. Move the complete existing `src/` tree to `<project-root>/backup/src/` in a history-preserving operation.
3. Keep `backup/` outside every active Gradle source set and exclude it from compilation, packaging, publication, and runtime classpaths.
4. Preserve useful existing documentation under `<project-root>/backup/docs/` unless a document is intentionally rewritten for the new architecture.
5. Create the new multi-project build only after the backup is verifiably present.
6. Never delete or overwrite user changes during migration.

The exact migration commit boundaries will be defined by the implementation plan.

## 5. Package Structure

ContinuumLib remains one conventional Gradle Java project. User-facing names always use the complete name `ContinuumLib`; internal responsibilities are separated through focused packages and interfaces rather than root-level Gradle subprojects.

```text
ContinuumLib/
├─ backup/
│  ├─ src/
│  └─ docs/
├─ src/
│  ├─ main/
│  │  ├─ java/com/kyroxova/continuumlib/
│  │  │  ├─ api/
│  │  │  ├─ model/
│  │  │  ├─ analyzer/
│  │  │  ├─ knowledge/
│  │  │  ├─ resolver/
│  │  │  ├─ emitter/
│  │  │  ├─ resources/
│  │  │  ├─ compiler/
│  │  │  ├─ packager/
│  │  │  ├─ bootstrap/
│  │  │  ├─ gradle/
│  │  │  └─ compatibility/
│  │  └─ resources/
│  └─ test/
│     ├─ java/com/kyroxova/continuumlib/
│     └─ resources/
├─ docs/
└─ wiki/
```

Java packages use `com.kyroxova.continuumlib` followed by the responsibility domain. The project publishes a single primary ContinuumLib artifact and its Gradle plugin marker.

### 5.1 Package Responsibilities

- `api`: stable public extension contracts, hook SPIs, annotations if later required, diagnostics API, and configuration model exposed to consumers.
- `model`: loader-neutral symbols, semantic operations, project graph, resolution plans, statuses, and provenance records. It has no Minecraft or loader dependencies.
- `analyzer-java`: project-wide Java parsing, symbol attribution, call and constructor resolution, inheritance analysis, and conversion of recognized patterns into semantic operations.
- `knowledge`: generated symbol databases plus hand-authored semantic capability providers.
- `resolver`: selects target strategies, checks prerequisites, applies policies and hooks, and produces a complete resolution plan.
- `emitter-java`: generates target-native Java sources from resolved operations while preserving unaffected application logic.
- `resources`: translates loader metadata, datapack layouts, tags, recipes, models, mixin configuration, and other versioned resources.
- `compiler`: resolves actual target toolchains and compiles generated sources against real Minecraft and loader artifacts.
- `packager`: creates native per-target JARs and the universal variant container.
- `bootstrap`: detects the runtime and activates the matching precompiled embedded variant.
- `gradle-plugin`: consumer-facing configuration and task orchestration.
- `testkit`: fixtures, real-artifact compile tests, launch tests, and diagnostics assertions.
- `compatibility`: built-in and consumer-provided adapters for behavior that cannot be resolved generically.

Package dependencies point inward toward `model` and `api`. The model cannot depend on analyzers, emitters, Gradle, Minecraft, loader classes, or implementation packages.

## 6. Consumer Integration

A consumer applies the ContinuumLib Gradle plugin:

```groovy
plugins {
    id "com.kyroxova.continuumlib" version "<version>"
}

continuumLib {
    base {
        minecraft = "1.18.2"
        loader = "forge"
        mappings = "mojmap"
    }

    targets {
        target("1.19.2", "forge")
        target("1.20.1", "forge")
        target("1.21.1", "neoforge")
    }

    output {
        perVersionJars = true
        universalJar = true
    }
}
```

Additional packaged configuration lives in the consumer mod:

```text
<consumer-mod>/src/main/resources/data/continuumlib/
├─ continuum.json
├─ targets.json
├─ policies.json
└─ hooks.json
```

Gradle configuration controls the build. Packaged resource configuration supplies runtime variant metadata, policies, and hook declarations. Conflicting values are errors unless an explicitly documented precedence rule exists.

## 7. Resolution Pipeline

For every consumer build, ContinuumLib performs these stages:

1. Read and validate configuration.
2. Resolve the real base and target Minecraft, loader, mapping, and Java toolchain artifacts.
3. Load or generate symbol databases for all configured environments.
4. Analyze the entire consumer source set as one project.
5. Build a typed project-wide symbol graph.
6. Convert recognized API usage into semantic operations.
7. Resolve each operation through target capability providers.
8. Apply explicit consumer hooks where automatic resolution is ambiguous or unavailable.
9. Generate target-native sources and resources.
10. Compile against the real target artifacts.
11. Perform structural and policy verification.
12. Package native target JARs.
13. Optionally package native variants into a universal bootstrap JAR.
14. Write human-readable and machine-readable reports.

No stage may replace an error with an undocumented fallback.

## 8. Semantic Operations

ContinuumLib adapts operations rather than blindly replacing type names. The Forge 1.18.2 source:

```java
public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, BuildScape.MODID);

public static final RegistryObject<Block> BUILDERS_WORKBENCH = BLOCKS.register(
        "builders_workbench",
        () -> new BuildersWorkbenchBlock(
                BlockBehaviour.Properties.of(Material.WOOD, MaterialColor.COLOR_BROWN)
                        .strength(2.5f)
                        .sound(SoundType.WOOD)));
```

is represented as operations similar to:

```text
DeclareRegistry(BLOCK, modId)
RegisterBlock(
  id = "builders_workbench",
  implementation = BuildersWorkbenchBlock,
  properties = BlockProperties(
    material = WOOD,
    mapColor = COLOR_BROWN,
    strength = 2.5,
    sound = WOOD
  )
)
```

Target providers choose the correct registration lifecycle, reference type, constructor, imports, and block-property representation. `DeferredBlock`, `RegistryObject`, and `Identifier` are not treated as direct equivalents.

## 9. Knowledge Base

The knowledge base combines generated symbol data with hand-authored semantic migrations.

```text
knowledge/
├─ capabilities/
│  ├─ registry/
│  ├─ blocks/
│  ├─ items/
│  ├─ events/
│  ├─ networking/
│  ├─ rendering/
│  ├─ saveddata/
│  └─ worldgen/
├─ versions/
│  ├─ mc_1_18_2/
│  ├─ mc_1_19_2/
│  ├─ mc_1_20_1/
│  └─ mc_1_21_1/
├─ loaders/
│  ├─ forge/
│  ├─ neoforge/
│  ├─ fabric/
│  └─ quilt/
└─ mappings/
   ├─ official/
   ├─ mojmap/
   ├─ srg/
   └─ intermediary/
```

Capabilities define stable semantic concepts. Version modules contain only version-specific differences. Loader modules define lifecycle and platform behavior. Generated mappings cover symbol names and descriptors without duplicating semantic migration rules.

### 9.1 Symbol Identity

Every indexed symbol is resolved using:

```text
environment + owner + kind + name + JVM descriptor + mapping namespace
```

The database stores aliases across official, Mojmap, SRG, intermediary, named/Yarn where legally distributable, and obfuscated namespaces. Overloads are distinct. Constructors and fields are first-class symbols.

Mapping importers record source, license/provenance, artifact checksum, Minecraft version, loader version, and generation time. Generated data is reproducible from pinned inputs.

### 9.2 Semantic Migration Rules

Semantic rules describe behavior that mappings cannot express, such as:

- removed materials in block properties;
- event bus versus callback registration;
- changed interaction methods;
- NBT versus item data components;
- simple channels versus typed payloads and codecs;
- code-driven versus data-driven registries.

Each rule declares its source environment range, target range, required symbol predicates, output strategy, limitations, provenance, and tests.

## 10. Resolution Statuses and Diagnostics

Every relevant operation receives exactly one status:

- `DIRECT`: valid without modification.
- `MAPPED`: only names or descriptors changed.
- `ADAPTED`: a semantic migration was applied.
- `GENERATED`: supporting target code or resources were generated.
- `DEVELOPER_HOOK`: an explicit consumer hook resolved the operation.
- `UNSUPPORTED`: no implementation exists.
- `AMBIGUOUS`: more than one behavior is plausible.

`UNSUPPORTED` and `AMBIGUOUS` are build failures unless a developer hook resolves them. Reports include source locations, source and target symbols, selected rules, confidence, provenance, generated files, and suggested extension points.

## 11. Developer Extension Points

Hooks are optional and strongly typed. The public SPI supports:

- symbol aliases for third-party or generated names;
- constructor adaptation;
- method invocation adaptation;
- semantic operation providers;
- target-specific source generation;
- resource conversion;
- validation policies;
- post-generation processing with declared inputs and outputs.

Hooks are declared in configuration and identified in reports. ContinuumLib never presents hook-provided behavior as built-in automatic support. Unsafe raw text injection is avoided in favor of typed source-model or operation APIs.

## 12. Environment Detection

Build-time environments are derived from explicit Gradle configuration and resolved dependencies. Runtime universal-JAR detection uses this precedence:

1. Embedded ContinuumLib variant metadata.
2. Loader-provided APIs and metadata.
3. Minecraft version constants.
4. Well-known marker classes.
5. Explicit consumer override.
6. Hard failure if the result remains missing or contradictory.

Detection records all probes and evidence. There is no silent default environment.

## 13. Output Modes

### 13.1 Per-Version Mode

Each target is independently generated and compiled into a native JAR:

```text
build/libs/<loader>/<modid>-<loader>-<minecraft>.jar
```

### 13.2 Universal Mode

The first universal format contains precompiled native variants plus a minimal bootstrap selector. It does not transform arbitrary mod bytecode into a new platform during class loading.

The packager validates that every configured runtime has exactly one compatible embedded variant and that loader metadata does not cause incompatible variants to be discovered simultaneously.

### 13.3 Reports and Generated Files

```text
build/continuumlib/
├─ reports/
├─ generated/
├─ diagnostics/
└─ symbols/
```

Generated source remains inspectable to make adaptation behavior reviewable.

## 14. Testing Strategy

Tests progress through increasingly realistic layers:

1. Model and parser unit tests.
2. Mapping importer fixtures with descriptor and overload coverage.
3. Capability contract tests for each source-target pair.
4. Golden-source tests for generated Java and resources.
5. Compilation against real pinned Minecraft and loader artifacts.
6. Sample-mod packaging tests.
7. Dedicated-server launch and shutdown smoke tests.
8. Client smoke tests where automation is available.
9. Persistence, registry, networking, side-safety, and classloading tests.

All integration tests have explicit timeouts and retain logs on failure. Generated stubs may support isolated unit tests but cannot satisfy target compatibility acceptance criteria.

## 15. External Reference Policy

Implementation may consult official loader documentation, official mappings, Minecraft artifacts available through supported toolchains, and open-source mods. External mod code is used as behavioral evidence rather than copied blindly. Relevant version, source URL, license, and the conclusion derived from it are recorded in rule documentation or tests.

When current technical details may have changed, implementation research uses current primary sources.

## 16. Documentation and Wiki

Documentation is implemented alongside code. A capability is not complete until its developer-facing behavior, limitations, configuration, and example are documented.

```text
docs/
├─ README.md
├─ getting-started.md
├─ configuration.md
├─ supported-versions.md
├─ compatibility-model.md
├─ developer-hooks.md
├─ diagnostics.md
├─ universal-jar.md
├─ per-version-jars.md
├─ contributing-version-support.md
└─ examples/

wiki/
├─ Home.md
├─ Installation.md
├─ Configuration.md
├─ How-Resolution-Works.md
├─ Developer-Hooks.md
├─ Supported-Versions.md
├─ Troubleshooting.md
└─ Adding-Version-Support.md
```

Wiki pages remain version-controlled. Publishing them to a hosted Git wiki is a separate release action performed only when the repository remote and credentials support it.

## 17. Initial Delivery Sequence

The rebuild proceeds in vertical slices rather than creating empty modules for every future capability:

1. Safely move the prototype to `backup/src/`, preserve its relevant documentation in `backup/docs/`, and establish the conventional single-project source skeleton.
2. Implement configuration, environment identities, diagnostics, and semantic model foundations.
3. Implement pinned artifact and mapping ingestion for Forge 1.18.2 and the first selected target.
4. Implement project-wide analysis for the example block registry and properties pattern.
5. Implement registry and block-property capability providers.
6. Emit and compile real target-native source.
7. Package the native target JAR and produce reports.
8. Add a sample consumer mod and server smoke test.
9. Add the next target version only after the vertical slice passes.
10. Add universal variant packaging after at least two native variants are proven.

## 18. Architectural Invariants

1. The core semantic model contains no loader-specific types.
2. Every target claim is verified against real pinned artifacts.
3. Every transformation is attributable to a mapping, capability provider, or developer hook.
4. Unsupported and ambiguous operations fail explicitly.
5. Version-specific modules contain deltas rather than copies of the full knowledge base.
6. Runtime bootstrap selection chooses precompiled variants; it is not the primary compatibility compiler.
7. Documentation and wiki pages change in the same work that introduces or changes user-facing behavior.
8. The existing prototype remains recoverable under `backup/` throughout the rebuild and is never included in active source sets or produced artifacts.
