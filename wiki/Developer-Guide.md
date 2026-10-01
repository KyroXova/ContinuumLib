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

## Intended configuration location

The consumer's configuration belongs in `src/main/resources/data/continuumlib/`, not in the ContinuumLib checkout. XML knowledge packs can be placed under its `knowledge/` subtree and checked with `gradlew continuumLibValidateRules`. The `transform.properties` file selects a route and supplies source/target artifact paths for `continuumLibTransformJar`. For multiple outputs, `targets.properties` and `targets/<id>.properties` drive `continuumLibBuildTargets`. Universal output is explicitly rejected until runtime bootstrap support exists.

## Coverage policy

A numeric version entry, indexed method, or successful rename is not proof of supported gameplay. A certified migration requires real source/target artifacts, namespace and loader identities, an evidenced rule, linkage checks and relevant execution tests. Reflection, resources, mixins and access transformers need explicit handling outside ordinary instruction renames.

See [Development Status](Development-Status) for the implemented boundary and remaining work.
