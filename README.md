# ContinuumLib

ContinuumLib is a work-in-progress Minecraft API adaptation library and Gradle plugin. It transforms compiled mod classes using version-scoped rules, namespace mappings and explicit semantic bridge hooks. It does not regenerate the mod's Java source.

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
| `continuumLibTransformJar` | One artifact-bound, uncertified development JAR |
| `continuumLibBuildTargets` | All configured per-version development JAR tasks |
| `continuumLibAuditTarget` / `continuumLibAuditTargets` | Member-reference declaration audits of transformed outputs |

See the [developer guide](wiki/Developer-Guide.md), [JAR configuration](wiki/Jar-Transformation.md), [rule format](wiki/Rule-Packs.md), [namespace mappings](wiki/Namespace-Mappings.md), and [current status](wiki/Development-Status.md). Universal requests fail explicitly until bootstrap support exists.

## Verification

The library uses Java 17 and the supplied Gradle wrapper:

```text
gradlew build
```

Local real-artifact checks are opt-in and documented in the wiki. Verified reference work includes BuildScape's Forge 1.18.2 compilation, 715-class transformation, official Forge API endpoints, and official 1.18.2 mapping ingestion. These checks are not target-game launch tests.

All production Java remains under `src/main/java/com/kyroxova/continuumlib/`. The original prototype is preserved in `backup/src/`; the later restart snapshot is in `backup/restart-2026-10-01/`. Neither is part of active compilation.
