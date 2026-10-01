# How resolution works

ContinuumLib separates symbol mapping from semantic adaptation.

A symbol identity includes the Minecraft environment, owner, symbol kind, name, JVM descriptor, and mapping namespace. This prevents overloaded methods and reused obfuscated names from being collapsed together.

Mapping answers “what is this symbol called?” Semantic rules separately answer “how must this call change?” The active Gradle pipeline uses class/member rules and typed call bridges, not registry-family recognition.

The current build path is:

1. Compile the consumer's unchanged source through its existing Gradle/loader toolchain.
2. Read an explicit target request and compose matching knowledge packs.
3. Verify source/target artifact hashes and rule endpoint declarations.
4. Apply call bridges, then type/member renames; use source hierarchy data for custom overrides and inherited references.
5. Remap supported Java service/manifest class names and preserve other resource bytes.
6. Atomically publish a development JAR, explicitly without gameplay certification.

Changed signatures, missing functionality and changed behavior require evidenced semantic strategies. A nearby version number, matching method name, indexed symbol or successfully rewritten JAR does not prove compatibility. The API-difference report and unresolved diagnostics identify work that still needs those strategies.
