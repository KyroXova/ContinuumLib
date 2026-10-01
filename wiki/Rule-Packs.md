# Version-scoped knowledge packs

Rule packs connect explicit migration knowledge to the bytecode adapters. They do not infer equivalent behavior from similar names, and they are not Minecraft compatibility certificates.

Constructor-to-factory rules are supported alongside class/member renames and ordinary call bridges. See [Constructor Migrations](Constructor-Migrations) for syntax, allocation safety rules, newer version packs and mapped artifact configuration.

Field accesses also support explicit bridges. This synthetic rule replaces an instance field read with a static accessor hook:

```xml
<bridge opcode="GETFIELD"
        from-owner="api/Block" from-name="shape" from-descriptor="I"
        to-owner="hooks/BlockAccess" to-name="shape" to-descriptor="(Lapi/Block;)I"/>
```

Supported field opcodes are `GETFIELD`, `PUTFIELD`, `GETSTATIC`, `PUTSTATIC`. For a field of type `T`, the hook shapes are `(Owner)T`, `(Owner,T)void`, `()T`, `(T)void` respectively. Exact descriptors and public-static hook declarations are verified. Field handles are rewritten too. Rules apply before class/member renaming and require authored semantic hooks available in the target runtime; no hook is invented or automatically bundled. Executable synthetic fixtures verify all four shapes, a getter handle, and a public field becoming private with a replacement accessor in a produced consumer JAR.

## Location and validation

Place consumer packs under `src/main/resources/data/continuumlib/knowledge/`. Subdirectories may group source version, loader and target version, for example `knowledge/1.18.2/forge/1.20.1/`. Environment metadata inside each file, not the directory name, controls selection.

Run `gradlew continuumLibValidateRules`. It reads `**/*.xml`, validates the schema and composes each environment route to detect conflicting rules. An empty knowledge directory fails this task. The report at `build/reports/continuumlib/rule-packs.txt` explicitly says `ARTIFACTS_UNVERIFIED`: this task has not checked artifact bytes or generated a mod JAR.

## XML schema 1

This illustrates the format only, using fictional API names. Replace the digest placeholders with actual 64-character SHA-256 values before validation. This example is **not** an evidenced Minecraft migration.

```xml
<rules schema="1" id="example-provider-rename"
       evidence="Describe the source/target artifacts and evidence for this change">
  <source minecraft="1.18.2" loader="FORGE" namespace="MOJMAP" java="17">
    <artifact name="game" sha256="SOURCE_SHA256"/>
  </source>
  <target minecraft="1.20.1" loader="FORGE" namespace="MOJMAP" java="17">
    <artifact name="game" sha256="TARGET_SHA256"/>
  </target>
  <class from="sample/OldApi" to="sample/NewApi"/>
  <member from-owner="sample/OldApi" from-name="oldName" from-descriptor="(I)I"
          to-owner="sample/NewApi" to-name="newName" to-descriptor="(I)I"/>
</rules>
```

Use JVM internal class names (`package/Class`) and full JVM descriptors. Member rules cover methods and fields; constructors cannot be renamed. Loader and namespace values use the existing enum names exactly. Multiple artifacts may be named under each environment; names must be unique. Unknown attributes/elements, duplicate keys and unsupported schemas fail instead of being ignored. Parsing is offline: document types, external entities and includes are unavailable.

For a semantic call bridge, use a `bridge` element with the same `from-*` and `to-*` attributes and an `opcode` of `INVOKESTATIC`, `INVOKEVIRTUAL` or `INVOKEINTERFACE`. The destination is a static hook; instance receivers become its first argument. The existing bridge stack-shape restrictions apply. A member cannot have both a rename rule and a bridge in the same selected plan.

## Runtime-independent API

`RulePackReader.read(InputStream)` reads one pack; the caller owns the stream. `RuleCatalog` accepts packs and rejects duplicate IDs. `select(sourceEnvironment, targetEnvironment)` composes all packs matching that exact pair, rejecting conflicts. It never assumes a route exists because the version number parses.

Before calling a transform, bind the plan:

```java
var plan = new RuleCatalog(packs).select(sourceEnvironment, targetEnvironment);
var bound = plan.bind(sourceArtifactPaths, targetArtifactPaths);
byte[] transformed = bound.adapt(originalClassBytes);
```

Artifact-path maps associate each manifest name with a local `Path`. Their keys must exactly match the composed manifest, and file hashes must match the recorded values. Verification observes bytes at binding time; callers must keep those artifacts unchanged for later compilation/loading. Hashes establish identity, not author trust or behavioral correctness.

The bound transform applies bridges, then class/member renames. It refuses classes above either declared Java runtime's class-file level and refuses preview bytecode. It does not rewrite class-file headers to pretend newer Java code can run on Java 8.

## Remaining integration

These APIs transform individual classes and are now connected to the explicit `continuumLibTransformJar` development task. Whole-JAR rewriting and standard JVM class-name resources are implemented. Full target linkage/access verification, inherited-override migration, Minecraft resource-schema migration and loader bootstrap integration remain unfinished.

The bundled catalog includes the three Forge `NetworkHooks.openGui` to `openScreen` overloads and the `ConfigGuiHandler` / nested config-factory class migration for 1.18.2-40.3.12 to 1.19.2-43.5.1. Tests verify real artifact declarations and apply the network rules to the compiled BuildScape reference. These narrow rule packs do not establish general support for either version pair or loader environment.
