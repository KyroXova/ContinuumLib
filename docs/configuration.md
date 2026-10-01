# ContinuumLib configuration

ContinuumLib requires an explicit base environment and explicit targets. It never assumes a Minecraft version, loader, mapping namespace, or Java version.

Consumer runtime configuration belongs under `src/main/resources/data/continuumlib/`. Build configuration will be exposed through the ContinuumLib Gradle extension as the compiler pipeline is implemented.

At least one output mode must be enabled: native per-version JARs, a universal variant JAR, or both. Duplicate targets and repeating the base as a target are configuration errors.
