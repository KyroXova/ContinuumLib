# ContinuumLib

ContinuumLib is a work-in-progress Minecraft API adaptation library and Gradle plugin. It supports both pre-compilation target-source generation in isolated build directories and compiled mod-class/JAR adaptation using version-scoped rules, namespace mappings and explicit semantic bridge hooks. Developer source files are never rewritten in place.

**Current boundary:** development JAR transformation works, including a real BuildScape reference check. Complete Minecraft version compatibility and universal runtime bootstrapping are not implemented. A transformed JAR is explicitly **NOT_CERTIFIED** for gameplay.

New adaptation coverage includes constructor-to-factory bytecode changes for the 1.20.1 → 1.21.1 vanilla identifier API, optional obfuscated output, and individually checked identifier members for 1.21.1 → 26.3. Legacy 1.7.10 SRG declaration indexing is also verified. See [constructor migrations and exact coverage](wiki/Constructor-Migrations.md); these are not blanket version-support claims.

## Use the published plugin

In the consuming mod's `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven { url = uri('file:///E:/Libraries/ContinuumLib/repository/') }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

In its `build.gradle`, alongside its existing loader plugins:

```groovy
plugins {
    id 'com.kyroxova.continuumlib' version '1.0.0'
}
```

Replace the example repository location with the actual distributed Maven repository. Maintainers generate it with `gradlew publishAllPublicationsToDistributionRepository` into `build/repository/`; no public hosting or remote upload is configured. Consumers do not need a ContinuumLib source checkout. Contributor-only `includeBuild` usage is documented in the developer guide.

The build-time workflow requires the Gradle plugin; adding a Java library dependency alone does not run transformation tasks. The current per-version transformer does not bundle ContinuumLib or add it to the mod's runtime dependencies. Players do not install the build plugin. Universal runtime embedding remains unimplemented.

All consumer configuration lives under the **consumer mod's** `src/main/resources/data/continuumlib/` directory:

- `knowledge/**/*.xml`: optional additional version-scoped rules.
- `transform.properties`: one development transformation request.
- `targets.properties` and `targets/<id>.properties`: multiple explicitly configured outputs.

## Available tasks

| Task | Result |
|---|---|
| `continuumLibInspect` | Compiled class/type/member-reference inventory |
| `continuumLibCompareApis` | Exact source/target API declaration differences |
| `continuumLibValidateRules` | Consumer XML rule schema and composition checks |
| `continuumLibTransformSource` | Pre-compilation AST source transformation into target source, compilation, packaging, and auditing |
| `continuumLibTransformJar` | One artifact-bound, uncertified development JAR |
| `continuumLibBuildTargets` | All configured per-version development JAR tasks |
| `continuumLibAuditTarget` / `continuumLibAuditTargets` | Member-reference declaration audits of transformed outputs |

## Architecture Pipelines

1. **Pre-Compilation Java Source Transformation Pipeline**:
   - Parses developer Java source in `src/main/java` into ASTs using JavaParser and symbol solver.
   - Preserves developer source files completely unmodified on disk.
   - Applies AST migrations (`ClassRename`, `MethodRename`, `ConstructorToFactory`, `FactoryToConstructor`, `FieldToAccessor`).
   - Generates temporary target-specific Java source under `build/continuum/<target>/generated-src/`.
   - Compiles temporary source against target Minecraft/loader dependencies using `javax.tools.JavaCompiler`.
   - Packages target JARs and audits bytecode references against target declarations via `TargetReferenceAudit`.

2. **Knowledge Generation Pipeline**:
   - Durable API snapshots (`ApiSnapshot`, `ClassSnapshot`, `MemberSnapshot`) capturing exact classes, members, access, descriptors, and SHA-256 manifests.
   - Lineage analysis across environments with `SymbolId`, `SymbolVersion`, and `SymbolLineage`.
   - Evidence-backed candidate discovery (`CandidateDetector`) assessing 16 evidence dimensions (`EvidenceType`, `Confidence`) with explicit ambiguity preservation (`REVIEW_REQUIRED`).
   - Verification gates (`RulePromoter`) strictly preventing unverified candidates from being promoted to executable `RulePack` rules.

3. **Generic Target-Aware Inclusion/Exclusion System**:
   - Discovers configuration from `src/main/resources/continuumlib/` (with fallback to `data/continuumlib/`).
   - Supports recursive multi-file rules across `inclusions/` and `exclusions/` directories.
   - Domains: `REGISTRY` (categorized and extensible `RegistryType` like block, item, block entity), `SOURCE` (paths), `CLASS` (names), and `RESOURCE` (assets/data).
   - Reusable `EnvironmentCondition` with multi-operator compound version ranges (`>=`, `<=`, `>`, `<`, `=`, `!=`).
   - Semantic AST registry declaration scanning and reference conflict validation with descriptive diagnostics (`CONTINUUM EXCLUSION CONFLICT`).
   - Developer source files and original assets are never modified.

4. **Unified Per-Target Generation Architecture**:
   - Canonical `ResolvedTarget` consolidating environments, manifests, classpaths, mappings, rule packs, isolated workspaces, and project configuration.
   - Deterministic, target-isolated workspaces (`GeneratedWorkspace`) under `build/continuum/targets/<target-id>/`. Staging ensures failed builds never expose partial final JARs.
   - Pre-AST file selection: source files and resources are filtered prior to JavaParser parsing.
   - Unified configuration discovery service (`ProjectConfigurationLocator`) with dual-configuration conflict detection (`DUAL_CONFIGURATION_CONFLICT`).
   - High-precision migration plan (`CanonicalMigrationPlan`) maintaining JVM descriptors and confidence levels (`SEMANTICALLY_RESOLVED`, `STRUCTURALLY_RESOLVED`, `AMBIGUOUS_MIGRATION`).
   - Clean layer separation (`MigrationLayer`): `SOURCE_AST` migrations are accounted for and not duplicated in the `BYTECODE` layer.
   - Deterministic per-target reporting (`generation.txt`).
   - Integrated via Gradle (`GenerateTargetTask`, `continuumLibGenerate_<id>`, and `continuumLibGenerateTargets`).

See the [developer guide](wiki/Developer-Guide.md), [JAR configuration](wiki/Jar-Transformation.md), [rule format](wiki/Rule-Packs.md), [namespace mappings](wiki/Namespace-Mappings.md), and [current status](wiki/Development-Status.md). Universal requests fail explicitly until bootstrap support exists.

## Verification

The library uses Java 17 and the supplied Gradle wrapper:

```text
gradlew build
```

Local real-artifact checks are opt-in and documented in the wiki. Verified reference work includes BuildScape's Forge 1.18.2 compilation, 715-class transformation, official Forge API endpoints, and official 1.18.2 mapping ingestion. These checks are not target-game launch tests.

All production Java remains under `src/main/java/com/kyroxova/continuumlib/`. The original prototype is preserved in `backup/src/`; the later restart snapshot is in `backup/restart-2026-10-01/`. Neither is part of active compilation.
