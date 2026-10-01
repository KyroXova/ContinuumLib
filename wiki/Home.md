# ContinuumLib

ContinuumLib is being rebuilt as a Minecraft API adaptation library and Gradle plugin. Mod developers keep native source for one environment. ContinuumLib inspects the compiled classes and applies evidenced rules for configured targets, without regenerating their Java source.

Development JAR transformation and explicit multi-target output are implemented. Full cross-version Minecraft compatibility and universal runtime bootstrapping are not. Output is labeled as uncertified until linkage, resources, loader behavior and gameplay have been verified.

The active implementation follows the conventional package root:

```text
src/main/java/com/kyroxova/continuumlib/
```

The previous prototype is preserved at `backup/src/` and is excluded from active compilation.

See [Development Status](Development-Status) for implemented capabilities.

Start with [Developer Guide](Developer-Guide), then [Configuration](Configuration), [JAR Transformation](Jar-Transformation), [Rule Packs](Rule-Packs) and [Namespace Mappings](Namespace-Mappings).
