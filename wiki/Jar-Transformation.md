# Development JAR transformation

`continuumLibTransformJar` applies verified rule endpoints to existing compiled class bodies. It produces a **NOT_CERTIFIED** development artifact, not a promise that the mod runs on the target Minecraft version. The task is deliberately not attached to `assemble` or publishing.

## Consumer configuration

Apply `com.kyroxova.continuumlib` as described in the [Developer Guide](Developer-Guide). Create this file in **your mod project**:

`src/main/resources/data/continuumlib/transform.properties`

```properties
pack=forge-network-1.18.2-to-1.19.2
source.forge=apis/forge-1.18.2-40.3.12-universal.jar
target.forge=apis/forge-1.19.2-43.5.1-universal.jar
```

Paths are relative to the consuming project root. Use forward slashes in properties files, including on Windows. This configuration selects the Forge route's bundled network and config-screen migrations; other API differences remain unresolved. Newer vanilla identifier routes and explicit mapping/output-namespace configuration are documented in [Constructor Migrations](Constructor-Migrations).

The official artifacts are available from [Forge 1.18.2-40.3.12](https://maven.minecraftforge.net/net/minecraftforge/forge/1.18.2-40.3.12/) and [Forge 1.19.2-43.5.1](https://maven.minecraftforge.net/net/minecraftforge/forge/1.19.2-43.5.1/). Use the `universal` classifier specified above. Source JARs document the implementation but are not valid substitutes for the hashed API JARs.

Run:

```text
gradlew continuumLibCompareApis
gradlew continuumLibTransformJar
```

The comparison report is `build/reports/continuumlib/api-delta.tsv`. It compares exact names and descriptors, including private and synthetic declarations; added/removed pairs do not automatically imply equivalent methods. The transform output is `build/continuumlib/<project-name>-transformed.jar`.

The chosen pack identifies an exact environment route. All bundled and consumer packs matching that route are composed; source/target artifact names must match their combined manifests. The current Forge route includes network and config-screen rename packs. No namespace, loader or nearby-version fallback is performed. A loader-specific artifact can be selected by configuring `continuumLibTransformJar.inputJar` with the appropriate task's archive provider.

## Multiple target outputs

Create `src/main/resources/data/continuumlib/targets.properties`:

```properties
targets=forge-1.19.2
perVersion=true
universal=false
```

Place that target's pack/artifact configuration in `src/main/resources/data/continuumlib/targets/forge-1.19.2.properties`, using the same format as `transform.properties`. Add further comma-separated IDs only when corresponding rule routes and artifact configurations exist. IDs are portable output names, not declarations of supported versions.

Run `gradlew continuumLibBuildTargets`. It creates `build/continuumlib/<id>.jar` for each configured target. Each transform independently performs the checks below. The batch is not an all-target atomic transaction: earlier successful targets may remain if a later target fails. Target IDs reject path traversal, portable filename hazards and case-insensitive duplicates.

`perVersion` and `universal` must be explicit booleans. Universal output currently fails mode validation before target transformations run. No universal file is fabricated and there is no hidden fallback to a different mode. Existing output files are not automatically deleted when configuration changes.

## Verification performed

- Artifact content changes are tracked by Gradle and checked against SHA-256 manifests.
- Class/member rule endpoints must exist in the supplied API artifacts. Missing endpoints, static/instance mismatches and public-member visibility reductions are rejected. Bridge hooks must be public static methods on non-interface classes.
- Conflicting rules, unsupported Java class-file downgrades and preview bytecode are rejected.
- Class entry names track renamed classes. Duplicate entries and duplicate member signatures are rejected.
- Signed inputs, digest-bearing manifests, modular inputs and multi-release inputs are rejected pending explicit policies.
- Output is written to a temporary file and atomically published. Input JARs, API artifacts, mapping files, rule files and the request configuration cannot be selected as output.
- Standard `META-INF/services` interface/provider names and supported manifest class entrypoints are remapped. Unrelated resources retain their original bytes.
- Same-descriptor rename rules propagate through supplied source hierarchy data to custom overrides and inherited references. Missing hierarchy needed for a possible rename and conflicting interface rename requirements are rejected. Private ancestor methods are not treated as overridden methods.

## Not verified

This is not a full JVM linker. The separate audit tasks inspect all scanned references, including ones without migration rules, but changed override signatures/semantics, caller-specific access, reflection, mixins, access transformers, loader metadata, Minecraft JSON/data formats, packet semantics and gameplay behavior still need their own checks and migration rules. A successfully transformed file may still be incompatible with its target game.

The library build currently requires Java 17. No Java 8-era runtime bootstrap or universal self-starting mod JAR is supplied yet.

## Reproduce the real-reference checks

Compile an external Forge 1.18.2 mod with its existing build. Supply the compiled classes directory and the directory containing the two pinned Forge universal artifacts. From the ContinuumLib root:

```text
gradlew test -PforgeArtifacts=<artifact-directory> -PreferenceClasses=<external-mod-classes-directory>
```

These checks inspect official Forge method declarations and transform the external mod classes. They compare every member reference against the selected rules and preserve the declared custom API. A mod need not use the selected migration: unchanged references must stay unchanged too. The test does not launch a target Minecraft runtime.

An opt-in script at `src/test/resources/integration/external-mod.init.gradle` resolves the published plugin for an unchanged external build. It can be copied independently of this checkout. Supply `-Dcontinuumlib.repository=<Maven-URL>`, `-Dcontinuumlib.version=<version>` and `-Dcontinuumlib.config=<absolute-transform.properties>`, then invoke the mod's wrapper with `--init-script <script-path> continuumLibTransformJar`. Artifact paths in that configuration are relative to the consumer project. Normal users should apply the plugin directly as described in the [Developer Guide](Developer-Guide).

The script targets only the root project by default. For a multi-project build, supply `-Dcontinuumlib.project=:mod` and invoke `:mod:continuumLibTransformJar`; the request's relative artifact paths resolve against that selected project. Unknown project paths are rejected. Unrelated projects are not given ContinuumLib tasks.
