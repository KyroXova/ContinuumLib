# Configuration

Every ContinuumLib environment is identified by four explicit values:

- Minecraft version
- Loader (XML enum values `FORGE`, `NEOFORGE`, `FABRIC`, or `QUILT`)
- Mapping namespace
- Java version

ContinuumLib does not silently fall back to Forge 1.18.2 or any other environment. Missing and contradictory configuration stops the build with diagnostics.

Files live in the consuming mod's `src/main/resources/data/continuumlib/`, not in the library checkout:

- `knowledge/**/*.xml`: version-scoped class/member/bridge packs; [format](Rule-Packs).
- `transform.properties`: chosen pack and named source/target artifact paths; [single-target example](Jar-Transformation).
- `targets.properties`: explicit `targets`, `perVersion` and `universal` settings.
- `targets/<id>.properties`: each target's individual transform request.

`continuumLibBuildTargets` runs configured per-version transformations. Universal mode is recognized but currently rejected before target transformations run. No configuration setting can turn unsupported migrations into verified compatibility.
