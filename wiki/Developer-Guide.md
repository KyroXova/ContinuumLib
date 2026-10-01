# ContinuumLib Developer Guide

## Current availability

ContinuumLib currently provides a Gradle inspection plugin, version-scoped rule packs and an explicit development JAR transformation task. It does **not** yet generate certified cross-version Minecraft mod JARs. Inspection or transformation success must not be interpreted as compatibility. See [JAR Transformation](Jar-Transformation).

## Consume the published Gradle plugin (no source checkout)

ContinuumLib is a **build-time plugin**, not a required player-installed mod. Apply it to the developer's existing mod project; do not copy ContinuumLib source into that project and do not add it to the mod's `implementation` or runtime dependencies. Current per-version transformations do not embed ContinuumLib or its dependencies. Explicit custom bridge hooks must still be present in the output or an intended runtime dependency.

The build now produces a Maven repository containing the plugin marker, implementation JAR, sources and dependency metadata. Maintainers generate it with:

```text
gradlew publishAllPublicationsToDistributionRepository
```

The output is `build/repository/`. This directory can be distributed independently of the source checkout or hosted as a Maven repository. No public repository URL has been provisioned and this command does not upload anything. ASM and Mapping-IO dependencies are resolved from Maven Central, not bundled into the mod.

Consumer `settings.gradle` (replace the example location with the actual distributed repository):

```groovy
pluginManagement {
    repositories {
        maven { url = uri('file:///E:/Libraries/ContinuumLib/repository/') }
        mavenCentral()
        gradlePluginPortal() // other build plugins, if needed
    }
}
```

Consumer `build.gradle`, alongside its existing loader plugin:

```groovy
plugins {
    id 'com.kyroxova.continuumlib' version '1.0.0'
}
```

Place the migration configuration in **the mod project's** `src/main/resources/data/continuumlib/`, then run `gradlew continuumLibTransformJar` or the configured multi-target tasks. The result is the mod's adapted JAR; it remains a development output until the target's unresolved APIs, loader integration, resources and gameplay have been verified. Universal self-bootstrapping JARs remain unimplemented.

Automated consumer tests resolve the plugin from this repository without `includeBuild` or a TestKit-injected plugin classpath. They execute the transformed fixture with only its target API and the Java platform available; the ContinuumLib plugin is not loadable there. This proves build/runtime separation, not Minecraft-wide compatibility.

Publication uses Gradle's [Maven publishing and plugin marker conventions](https://docs.gradle.org/current/userguide/preparing_to_publish.html).

## Optional source-checkout integration for plugin contributors

Only when developing ContinuumLib itself, a local composite build is an alternative. It is not required for normal consumers. In a test mod project's `settings.gradle`:

```groovy
pluginManagement {
    includeBuild('E:/Minecraft/ContinuumLib')
}
```

Apply the plugin alongside your existing mod build plugins:

```groovy
plugins {
    id 'com.kyroxova.continuumlib'
}
```

Run `gradlew continuumLibInspect`. The task depends on the Java `jar` output and writes `build/reports/continuumlib/api-inventory.tsv`. No source files are regenerated or changed.

For a loader build whose relevant compiled artifact is produced by a different task, set `continuumLibInspect.inputJar` to that task's archive provider. Choose an artifact in the namespace you intend to inspect; this task does not remap it automatically.

The report starts with `INVENTORY_ONLY`. Rows identify classes, fields, methods, referenced types and member uses. Member identities include owner, name and JVM descriptor so overloaded methods remain distinct. `USE` rows include caller identity, target identity, JVM opcode, whether the reference is a method handle, and source line (`-1` when unavailable). Tabs, line breaks and backslashes inside values are escaped.

## Adaptation contracts

`ClassAdapter` applies explicitly supplied class/member renames to bytecode. It preserves custom bodies; it is not a source generator. A renamed method must retain its descriptor after class-name remapping. Changed owners outside class remapping and changed argument lists require a semantic strategy.

`CallBridge` redirects an exact ordinary invocation to a static hook. For an instance method `api/Block.shape(I)I`, the hook descriptor must be `(Lapi/Block;I)I`: original receiver first, then original arguments, then the same return type. The hook implements the migration, including any null handling, argument conversion or fallback behavior required by that API. It must be supplied as a public static method on a non-interface class. The current transformer checks stack shape, not the hook's semantic correctness or availability.

Apply call bridges before class/member renaming. The same rules are applied to supported method handles, including lambda references. Constructors, super invocations and changed override contracts are deliberately not treated as ordinary bridges.

Field-to-accessor bridges support `GETFIELD`, `PUTFIELD`, `GETSTATIC` and `PUTSTATIC`. Instance reads pass the receiver and return the field type; instance writes pass receiver/value and return `void`. Static reads take no arguments; static writes take the value. Hooks are public static methods on public non-interface classes. Hooks must deliberately handle nulls, initialization order, volatility and semantics; preserving stack shape alone does not establish equivalence.

Checksum-pinned source/target dependency JARs and target JDK JMODs can be supplied separately from the selected rule manifest. See [Target Audit](Target-Audit#additional-dependency-declarations). These are declaration inputs, not automatically bundled dependencies.

`MemberLookup` finds declarations through supplied hierarchy data. `FOUND` means only that a declaration exists; access checks, invocation compatibility and behavior still require verification. Missing hierarchy artifacts never count as compatibility.

## Source-Level Adaptation Pipeline

ContinuumLib's primary adaptation mechanism is pre-compilation Java source AST transformation. Rather than attempting to guess semantic mappings during post-compilation bytecode rewriting, ContinuumLib parses the developer's original source code with JavaParser and symbol solver, transforms the AST, and compiles the target code against the target environment's dependencies:

1. **Zero modifications to developer source**: Developer code under `src/main/java` is read-only and never modified.
2. **AST rewriting**:
   - `ClassRename`: Updates class usage and reconciles package imports.
   - `MethodRename`: Replaces method names on static scopes and instance variables.
   - `ConstructorToFactory`: Rewrites `new Identifier("mod", "id")` to `Identifier.fromNamespaceAndPath("mod", "id")`.
   - `FactoryToConstructor`: Rewrites `Identifier.of("mod", "id")` to `new Identifier("mod", "id")`.
   - `FieldToAccessor`: Rewrites field access like `block.hardness` to accessor calls `block.getHardness()`.
3. **Generated source output**: Generated target Java files are emitted to temporary build directories (`build/continuum/<target>/generated-src/`).
4. **Target compilation**: `SourceCompiler` invokes `javax.tools.JavaCompiler` against target classpath dependencies and Java versions.
5. **Packaged output & audit**: `TargetJarPackager` produces the target JAR, and `TargetReferenceAudit` audits all member instructions to confirm linkage compatibility against target declarations.

Run this with `gradlew continuumLibTransformSource`.

## Knowledge Generation & Verification Pipeline

ContinuumLib uses an evidence-based knowledge pipeline to discover API evolution:
- **API Snapshots**: Exact representations of declared types, members, access flags, descriptors, signatures, and SHA-256 manifests.
- **Candidate Detection**: Evaluates candidates against 16 structural and contextual evidence types (`EvidenceType`).
- **Ambiguity Guard**: When multiple candidates share identical signatures or parameter patterns, they are classified as `REVIEW_REQUIRED` and blocked from automatic promotion.
- **Verification Gates**: Only candidates in `VERIFIED_*` states can be converted by `RulePromoter` into executable `RulePack` rules.

## Target-Aware Inclusion & Exclusion System

ContinuumLib provides a generic, target-aware configuration system allowing mods to filter registry elements, source files, classes, and resources on a per-target basis without hardcoding mod-specific or backport-specific assumptions.

### Directory Layout

Rules are organized under the mod project's `src/main/resources/continuumlib/` (with fallback to `data/continuumlib/`):

```text
src/main/resources/continuumlib/
├── inclusions/
│   ├── registry.json
│   ├── source.json
│   └── ...
└── exclusions/
    ├── registry/
    │   ├── blocks.json
    │   └── items.json
    ├── source/
    │   └── legacy.json
    └── resources/
        └── models.json
```

All compatible JSON files within `inclusions/` are recursively merged into one logical `InclusionRuleSet`. All files within `exclusions/` merge into one logical `ExclusionRuleSet`.

- **Optional directories**: Missing or empty `inclusions/` means no inclusion filtering is applied (normal content remains included). Missing or empty `exclusions/` means nothing is excluded.
- **Original source invariance**: Original consumer files are **never** modified. Exclusions affect only temporary generated target source and packaged target JARs.

### Filtering Domains & Rule Formats

#### 1. Registry Elements
Rules identify target elements using categorized, extensible types (`block`, `item`, `block_entity`, `entity_type`, `fluid`, `menu`, etc.):

```json
{
  "rules": [
    {
      "when": { "minecraft": ">=1.21" },
      "type": "block",
      "id": "example:old_block"
    },
    {
      "when": { "minecraft": ">=1.21" },
      "type": "item",
      "id": "example:old_item"
    }
  ]
}
```

Removing a block does **not** implicitly remove an item with the same identifier; each domain element must be explicitly configured or linked.

#### 2. Source Files & Classes
Exclude files or classes from compilation in the target environment:

```json
{
  "rules": [
    {
      "when": { "minecraft": ">=1.21" },
      "path": "com/example/legacy/OldFeature.java"
    },
    {
      "when": { "minecraft": ">=1.21" },
      "class": "com.example.legacy.OldClass"
    }
  ]
}
```

#### 3. Resources
Omit asset or data files from the target JAR:

```json
{
  "rules": [
    {
      "when": { "minecraft": ">=1.21" },
      "path": "assets/example/models/block/old_block.json"
    }
  ]
}
```

### Environment Conditions

Rules support multi-field conditional matching under `"when"`:
- `minecraft`: Compound version expressions (e.g. `">=1.20 <1.21"`, `">=1.21"`, `"=1.20.1"`, `"!="`, `"<"`).
- `loader`: Target loader name (e.g. `"forge"`, `"fabric"`, `"neoforge"`).
- `loader_version`: Target loader version constraint.
- `java`: Java language version constraint (e.g. `">=17"`, `"21"`).
- `namespace`: Target mapping namespace (e.g. `"official"`).
- `output_mode`: Output mode (e.g. `"per_version"`).

### Conflict Detection & Reference Validation

If a registry element is excluded for a target, but active (non-excluded) source code still references that declaration (such as `ModBlocks.TEST.get()`), ContinuumLib's reference validator halts the build immediately with an informative diagnostic:

```text
CONTINUUM EXCLUSION CONFLICT

Target:
Minecraft 1.21.1

Excluded element:
BLOCK example:test

Declaration:
com.example.ModBlocks.TEST

Remaining reference:
com/example/SomeFeature.java:42

The excluded registry element is still referenced by target-enabled source.
```

## Intended configuration location

The consumer's configuration belongs in `src/main/resources/continuumlib/` (or `src/main/resources/data/continuumlib/`), not in the ContinuumLib checkout. XML knowledge packs can be placed under its `knowledge/` subtree and checked with `gradlew continuumLibValidateRules`. The `transform.properties` file selects a route and supplies source/target artifact paths for `continuumLibTransformJar` and `continuumLibTransformSource`. For multiple outputs, `targets.properties` and `targets/<id>.properties` drive `continuumLibBuildTargets`. Universal output is explicitly rejected until runtime bootstrap support exists.

## Coverage policy

A numeric version entry, indexed method, or successful rename is not proof of supported gameplay. A certified migration requires real source/target artifacts, namespace and loader identities, an evidenced rule, linkage checks and relevant execution tests. Reflection, resources, mixins and access transformers need explicit handling outside ordinary instruction renames.

See [Development Status](Development-Status) for the implemented boundary and remaining work.
