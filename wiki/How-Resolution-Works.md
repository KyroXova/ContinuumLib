# How resolution works

ContinuumLib separates symbol mapping from semantic adaptation.

A symbol identity includes the Minecraft environment, owner, symbol kind, name, JVM descriptor, and mapping namespace. This prevents overloaded methods and reused obfuscated names from being collapsed together.

Mapping answers “what is this symbol called?” Capability providers separately answer “how is this behavior implemented in the target environment?”
