# Configuration

Every ContinuumLib environment is identified by four explicit values:

- Minecraft version
- Loader (XML enum values `FORGE`, `NEOFORGE`, `FABRIC`, or `QUILT`)
- Mapping namespace
- Java version

ContinuumLib does not silently fall back to Forge 1.18.2 or any other environment. Missing and contradictory configuration stops the build with diagnostics.

Files live in the consuming mod's canonical `src/main/resources/continuumlib/` directory. The older `src/main/resources/data/continuumlib/` layout remains a compatibility fallback; defining active project configuration in both roots is an error.

- `knowledge/**/*.xml`: version-scoped class/member/bridge packs; [format](Rule-Packs).
- `transform.properties`: chosen pack and named source/target artifact paths; [single-target example](Jar-Transformation).
- `targets.properties`: explicit `targets`, `perVersion` and `universal` settings.
- `targets/<id>.properties`: each target's individual transform request. Set `target.loaderVersion=<version>` when filter rules use `when.loader_version` / `when.loaderVersion`; the value is carried into the target filter context and is never inferred.

`continuumLibBuildTargets` runs configured per-version transformations. Universal mode is recognized but currently rejected before target transformations run. No configuration setting can turn unsupported migrations into verified compatibility.
