# Constructor and namespace migrations

ContinuumLib now rewrites supported constructor allocations into explicit static factory calls. This is a semantic bytecode strategy, not a class-name alias or source-code generator.

## Current verified knowledge

| Exact route | Implemented change | Verification boundary |
|---|---|---|
| Vanilla API 1.20.1 → 1.21.1 | Two public `ResourceLocation` constructors → `fromNamespaceAndPath` / `parse`; `of` → `bySeparator` | Official artifact hashes, mapped member declarations, Gradle-produced JAR and actual 1.21.1 API execution on Java 21 |
| Vanilla API 1.21.1 → 26.3 | `ResourceLocation` → `Identifier`; `isAllowedInResourceLocation` → `isAllowedInIdentifier` | Every source public method (30) and public field (6) checked individually against target declarations; not a Java 25 gameplay run |
| Vanilla 1.7.10 | Obfuscated → SRG declaration indexing, including descriptorless field enrichment | Real server/MCP artifact checks; **not a cross-version migration pack** |

`VANILLA` is an explicit API-only scope, not a loader adapter. These packs do not port Forge/Fabric entrypoints, metadata or lifecycle code. They do not establish support for every intervening version. Private/protected API use, nested identifier types, removed helpers such as 1.20.1 `isValidResourceLocation`, and behavior changes need their own rules. Finding the containing class is insufficient.

## XML rule

```xml
<constructor-factory
    from-owner="net/minecraft/resources/ResourceLocation"
    from-name="&lt;init&gt;"
    from-descriptor="(Ljava/lang/String;)V"
    to-owner="net/minecraft/resources/ResourceLocation"
    to-name="parse"
    to-descriptor="(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"/>
```

Place this inside a normal artifact-bound `rules` pack. The factory must be public/static on a public class, consume the same arguments, and return the constructed source type before class remapping. The endpoint verifier checks the exact declarations. If a class is also renamed, the final factory owner and descriptor are remapped afterward.

The implementation identifies allocations using bytecode dataflow. It supports canonical `NEW`/`DUP` allocation sequences, nested allocations, wide arguments, and constructor method handles including lambda constructor references. It rejects super/this constructor chaining, escaped uninitialized locals, extra aliases and intervening control flow/stack frames. Rejection leaves an existing output JAR intact.

This is intentionally constrained. Replacing allocation with a factory can change initialization timing or allocation/exception behavior; rule authors must establish the intended semantics. Arbitrary factories are not automatically equivalent constructors.

## Consumer configuration against obfuscated APIs

The consumer's `src/main/resources/data/continuumlib/transform.properties` can normalize declarations through explicit mappings before applying named rules:

```properties
pack=vanilla-identifiers-1.20.1-to-1.21.1
source.minecraft=apis/minecraft-1.20.1-server-extracted.jar
target.minecraft=apis/minecraft-1.21.1-server-extracted.jar

mapping.source.file=apis/minecraft-1.20.1-server_mappings.txt
mapping.source.sha256=dca153d20defb32cfac3f069c3bf77b3e13c30ae63847637479d3137e099bb72
mapping.source.from=OBFUSCATED
mapping.source.namespaces=source:MOJMAP,target:OBFUSCATED
mapping.source.license=Mojang mappings license; local use only

mapping.target.file=apis/minecraft-1.21.1-server_mappings.txt
mapping.target.sha256=9d0b04bead421c8229aff14b534432bbc927bea642e7c8593d1276b8df8ba53f
mapping.target.from=OBFUSCATED
mapping.target.namespaces=source:MOJMAP,target:OBFUSCATED
mapping.target.license=Mojang mappings license; local use only

# Optional: emit actual obfuscated target symbols instead of the rule namespace.
output.namespace=OBFUSCATED
```

Paths are relative to the consuming mod project. The server artifacts above are the embedded game JARs extracted from Mojang's server bundles, not the outer launcher bundles. Input mod bytecode must use the source rule namespace (`MOJMAP` here); API declaration normalization is not automatic input-mod namespace detection. Keep loader reobfuscation stages in mind when choosing the input JAR.

Without `output.namespace`, the transformed JAR uses the rule target namespace. With it, target mappings drive a second bytecode pass over classes, member references and supported class-name resources. Custom override renaming uses target/mod hierarchy information and fails on unresolved required ancestry. Audit tasks inspect declarations in the chosen output namespace. This does not rewrite reflection strings, mixins or loader metadata.

Run `gradlew continuumLibAuditTarget`. The transform writes `build/continuumlib/<project>-transformed.jar`, and audits cover all scanned references rather than only those matching rules. Outputs remain `NOT_CERTIFIED` because a successful specific migration is not whole-mod compatibility.

## Legacy mappings

Old SRG files commonly omit field descriptors. With explicit API declarations, the importer recovers the unique descriptor from the supplied artifact and translates its types across namespaces. Ambiguous same-name fields are rejected. Descriptorless fields absent from that client/server artifact are omitted rather than invented. All actual artifact declarations remain in the index. The strict importer overload without artifacts still rejects missing descriptors.

For 1.7.10, the tested files are Mojang's server JAR and `joined.srg` from Forge Maven's `mcp-1.7.10-srg.zip`; bindings are `source:OBFUSCATED,target:SRG`. This handles the old namespace format but does not solve legacy Java, rendering, registry, resource or lifecycle changes by itself.

## Artifact provenance

- [Mojang 1.20.1 version metadata](https://piston-meta.mojang.com/v1/packages/c0a00f47b3dae01d83e21be9a646c9232379d9ab/1.20.1.json): embedded server SHA-256 `80db52b203ac5de6e5fc1c5082259df440fb2b5390b4c61d474e8fbc63cc41f5`.
- [Mojang 1.21.1 version metadata](https://piston-meta.mojang.com/v1/packages/22a1966494dfa4eeb5ee778c8e6ed5b774839582/1.21.1.json): embedded server SHA-256 `c301de10f575027d13eac18c7f34409d60648cf56a35d566aa1f530ff617840a`.
- Local 26.3 embedded server SHA-256 `a362163eec5d1612d520772bc16e5b39c09e3b234fdc045f56bf544284ee8ae6`; its `version.json` identifies stable 26.3, Java 25, and its classes have major version 69.
- [Mojang 1.7.10 version metadata](https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json): server SHA-256 `c70870f00c4024d829e154f7e5f4e885b02dd87991726a3308d81f513972f3fc`.
- [Forge Maven MCP 1.7.10 SRG archive](https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.7.10/mcp-1.7.10-srg.zip): archive SHA-256 `d49df5c445dcf00ada532ab3b0e4283bf4b05ac13f210647658dfd3cec8b34c2`; extracted `joined.srg` SHA-256 `43d4376110b2638213d3096f80f8395c076eb05b58d6c0dbd22c0d999b37a357`.
- [NeoForged's 1.21 porting primer](https://docs.neoforged.net/primer/docs/1.21/) documents the constructor/factory transition; the [1.21.11 primer](https://docs.neoforged.net/primer/docs/1.21.11/) documents identifier renaming. Exact endpoints were additionally checked against the pinned artifacts.

Minecraft binaries and mappings are local verification inputs, not redistributed in ContinuumLib.

## Reproduce local checks

With the exact artifacts above present in `ref/artifacts` and the existing 26.3 reference file, run from the ContinuumLib checkout (substitute your Java 21 executable):

```text
gradlew build -PminecraftArtifacts=ref/artifacts -PinspectionArtifact=ref/server-extracted-26.3.jar -PminecraftJava21="path/to/jdk-21/bin/java"
```

The Minecraft consumer test creates a 1.20.1-style caller JAR, uses the actual Gradle plugin to produce named and obfuscated 1.21.1 outputs, executes the obfuscated output against the real target game classes/bundled dependencies, then verifies a further 26.3 identifier output. The 26.3 portion checks produced bytecode and declarations without launching Java 25. Existing Forge/BuildScape/1.18.2 mapping tests have their separate opt-in artifact properties; enable those too for the complete local matrix.
