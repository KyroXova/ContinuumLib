# Obfuscated and named API identities

`MappingIoImporter` uses [FabricMC Mapping-IO](https://github.com/FabricMC/mapping-io) to read text mapping artifacts. ContinuumLib verifies a supplied SHA-256 digest before importing symbols and requires explicit bindings between file namespace labels and ContinuumLib namespaces. A label such as `official` or `named` is not guessed to mean a particular naming system.

The implemented wrapper has tests for Tiny v2, actual Mojang ProGuard mappings and legacy 1.7.10 MCP SRG mappings. Other parser formats are not automatically certified by those tests. Descriptorless fields can be enriched from unique actual artifact declarations; the strict no-artifact importer still rejects them. Overloads must never be merged by name alone.

## Import contract

```java
var importer = new MappingIoImporter(Map.of(
    "official", MappingNamespace.OBFUSCATED,
    "intermediary", MappingNamespace.INTERMEDIARY,
    "named", MappingNamespace.YARN
));
var symbols = importer.importMappings(input, environment, provenance);
```

`provenance` contains the source name, source URI, actual SHA-256 checksum and license declaration. The caller owns `input`. A mismatched digest or missing selected namespace fails the import. Mappings and their licensing are not inferred from a filename, downloaded by this API or automatically redistributed.

Classes, fields, constructors and methods become descriptor-aware aliases. Descriptors are remapped into each namespace, including referenced classes and arrays. Each alias carries that namespace in its environment identity. Symbol IDs are scoped to version, loader, Java runtime and mapping-artifact hash; they do not imply that similarly named methods have equivalent behavior across game versions. JVM names are preserved exactly, including unusual legal obfuscated names.

## Build a namespace-only rule pack

`NamespaceRulePack.create(...)` converts imported aliases into an ordinary `RulePack`, using supplied source/target API artifact manifests. It requires the same Minecraft version, loader and Java runtime with distinct namespaces. A source symbol without its selected target alias is rejected. Constructors retain their special names while class references and descriptors are remapped.

The resulting pack uses the existing catalog, artifact-binding and class-transformation APIs. A runtime execution test verifies class, field and method renaming together. Namespace conversion is not a substitute for cross-version semantic migration; the factory rejects attempts to change the Minecraft version.

Explicit mapping-file configuration is connected to the consumer Gradle tasks. Source/target API declarations can be normalized into the rule namespace; `output.namespace` optionally remaps the output JAR using the target mappings. Files, digests, namespace labels and license declarations must be supplied explicitly. See [Constructor Migrations](Constructor-Migrations) for a complete configuration and exact verified coverage. Mapping-file discovery and automatic input-mod namespace detection are not implemented.

## Real mapping verification

The supplied official 1.18.2 client mapping file was verified against the Mojang version manifest's SHA-1 and imported successfully:

- SHA-1: `a661c6a55a0600bd391bdbbd6827654c05b2109c`
- SHA-256: `a2aa6ee1030bfef79e9b2e08e79de1637fdd7ecb5bf8891cf2e9a4b186042543`
- 6,399 classes and 89,107 total symbols.

This verifies ingestion of that mapping artifact, not Minecraft gameplay compatibility. The mapping text itself is not bundled in ContinuumLib.
