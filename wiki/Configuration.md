# Configuration

Every ContinuumLib environment is identified by four explicit values:

- Minecraft version
- Loader (`forge`, `neoforge`, `fabric`, or `quilt`)
- Mapping namespace
- Java version

ContinuumLib does not silently fall back to Forge 1.18.2 or any other environment. Missing and contradictory configuration stops the build with diagnostics.
