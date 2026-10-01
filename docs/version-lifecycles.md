# Version ranges and API lifecycles

ContinuumLib models Minecraft API availability explicitly instead of treating every mapping as valid for every release.

Capability and symbol metadata use an inclusive start and exclusive end version range. A symbol lifecycle may record when the symbol was introduced, deprecated, removed, and the stable identity of its replacement.

Supported release numbers are numeric dotted versions such as `1.7.10`, `1.18.2`, `1.20.5`, `1.21.1`, and `26.1`. Snapshot identifiers are rejected until a dedicated snapshot parser and ordering policy are implemented; they are never guessed.

Deprecation metadata does not automatically mean a call must be replaced. The target provider decides whether the deprecated symbol remains executable, whether a newer form is preferred, or whether removal requires a semantic adapter.
