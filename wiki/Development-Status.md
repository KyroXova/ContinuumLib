# Development Status

## Restart — 1 October 2026

ContinuumLib is being rebuilt as a compiled-class adaptation library with Gradle integration. This is not yet a working all-version Minecraft compatibility release.

The original prototype is preserved at `backup/src/`. The source tree present at this restart is also preserved at `backup/restart-2026-10-01/src/`, with its build configuration beside it. Active Java code stays under `src/main/java/com/kyroxova/continuumlib/`.

## Implemented and tested

- Full declared class, constructor, method, field and hierarchy indexing from supplied JARs, without loading game classes. Duplicate classes and unselected multi-release artifacts are rejected.
- Instruction-level member reference inventory: calls, field accesses, constructors, lambda/bootstrap method handles and dynamic constants, with caller identities and available source line numbers.
- Type reference inventory across descriptors, signatures, annotations, hierarchy, instructions and stack maps.
- Exact class/member renaming that preserves existing method bodies. Unsupported signature changes are rejected instead of guessed.
- Explicit static bridge hooks for ordinary static, virtual and interface calls, including bound lambda method references. Bridge descriptors must preserve the original operand stack, including the receiver. Constructor and super-call bridges are rejected.
- Conservative inherited declaration lookup with separate missing-member, incomplete-classpath, ambiguous and cyclic-hierarchy results. This is not full JVM linkage validation.
- Gradle plugin `com.kyroxova.continuumlib` with `continuumLibInspect`, tested in a separate consumer project. It produces an inventory report, not a compatible target JAR.
- The older operation resolver retains analysis diagnostics and no longer reports an empty analysis as complete.
- Version-scoped rule packs now compose class renames, exact member renames and call bridges. Selection requires the exact version, loader, namespace and Java environment pair; no nearest-version fallback is used.
- Artifact binding verifies complete named source/target SHA-256 manifests before class transformation. Conflicting rules and artifact identities are rejected. Java class-file downgrades and preview bytecode are rejected explicitly.
- An offline XML reader loads rule packs, rejecting unsupported schemas, unknown fields, duplicate entries and document types/external entities. The Gradle `continuumLibValidateRules` task discovers consumer packs under `src/main/resources/data/continuumlib/knowledge/**/*.xml` and validates their composition.
- The bundled knowledge base now contains the three Forge `NetworkHooks.openGui` to `openScreen` overload migrations for exactly Forge 1.18.2-40.3.12 to 1.19.2-43.5.1, pinned to official Maven artifact hashes. This is partial API coverage, not a supported game-version pair.
- The same route also includes the whole `ConfigGuiHandler` / nested factory type rename to `ConfigScreenHandler`, including the factory lookup method. Official source artifacts and both factory constructor descriptors were checked.
- `continuumLibTransformJar` reads a consumer `transform.properties`, binds source/target artifacts, checks rule endpoint declarations, and writes an explicitly uncertified transformed JAR. Source JARs are preserved; failed transformations do not replace prior output. Signed, modular, multi-release and colliding inputs are rejected.
- Standard Java service registrations and manifest entrypoint class names follow class renames. Other resources are copied byte-for-byte; Minecraft-specific resource migrations remain unfinished.
- `continuumLibCompareApis` produces exact whole-artifact declaration differences covering classes, inheritance, methods/constructors, fields, access flags and generic member signatures. It does not guess semantic replacements.
- Mapping import is now backed by Mapping-IO with digest verification and explicit namespace bindings. Tiny v2 and the actual official 1.18.2 ProGuard mapping file are tested; the latter imports 6,399 classes and 89,107 symbols. Descriptorless fields require actual artifact declarations for enrichment; unresolved/ambiguous descriptor information is not guessed. Namespace-only packs feed the existing adapters and cannot masquerade as cross-version migrations.
- Optional source-hierarchy-aware renaming preserves custom virtual overrides and inherited field references for exact rename rules. The Gradle transform task supplies mod/API hierarchy data. Incomplete required hierarchy and conflicting interface renames fail explicitly; unrelated private ancestor names are not propagated.
- `continuumLibBuildTargets` reads explicit per-version/universal output choices and fans out configured development target JAR tasks. Per-version output orchestration is implemented and exercised with Gradle configuration-cache enabled. Universal requests fail validation before target transforms because bootstrap support is not implemented.

The previous registry-name recognizer and unverified registry capability were removed. Registry-family names are not evidence that those APIs are supported.

## Verification

The latest local full build passed **101 tests**, with zero failures, errors or skips. The verification matrix includes the optional Forge, external reference mod, 26.3 indexing, official mappings, legacy/newer Minecraft artifact and Java 21 execution checks. Plugin validation passed. Gradle still reports deprecation warnings; this is not a Gradle 9 compatibility claim.

The 1.7.10 server artifact declaration index preserved 6,632 classes, 64,165 methods/constructors and 16,649 fields while applying SRG names. These totals cover the entire server artifact, including bundled libraries; they are not counts of newly supported Minecraft operations. The Gradle identifier fixture produced both 1.21.1 and 26.3 target JARs; the obfuscated 1.21.1 result executed against actual target classes on Java 21, while the 26.3 result was checked structurally against actual target declarations.

Executable fixtures verify unchanged custom arithmetic around rewritten calls, bridge invocation and bound lambda adaptation. These fixtures test transformation machinery; they do not certify Minecraft behavior.

The local `ref/server-extracted-26.3.jar` has been inspected successfully: 7,762 classes and 72,269 declared methods/constructors. This validates artifact indexing, not cross-version adaptation.

A separate Gradle consumer compiles its own class and runs the plugin inventory task without source rewriting. Gradle plugin validation is also run.

The actual BuildScape Forge 1.18.2 reference compiled successfully using its existing ForgeGradle build. Its 715 compiled classes contain 143,967 instruction-level member uses. Applying the bundled network migration changes 2 calls in 2 classes; tests verify that every other scanned member reference and all declared custom APIs remain unchanged. This is bytecode transformation verification, not a 1.19.2 game launch or full BuildScape port.

An end-to-end consumer test also compiles against a source fixture API, builds a transformed JAR, and executes it against the target fixture API. A changed target artifact invalidates the Gradle task and is rejected by hash verification.

The packaged plugin was also run against BuildScape's unchanged reference Gradle build: 715 classes and 19,528 resources were processed into a labeled network-only development JAR. The complete Forge declaration comparison produced 2,539 differences. This remains partial migration, not a target-game compatibility result.

The new target audit also ran against that output. It checked 143,967 member instructions and exposed the missing `ForgeFlowingFluid.Properties` constructor used by `ModFluids`, alongside incomplete dependency information. Its type report contains 3,025 present and 13,426 missing per-class type references, including `FluidAttributes` usages in custom blocks, a renderer and fluid registration. Counts are references per class, not unique API classes. See [Target Audit](Target-Audit) for the results and interpretation limits.

## Not implemented or certified

- Comprehensive cross-version semantic knowledge and automatic discovery of the correct mapping files. Explicit verified mapping-file configuration and selected migration packs are implemented.
- Arbitrary semantic migrations for Minecraft constructors, changed signatures, custom overrides, registries, hitboxes, block connections, entities, rendering, networking or persistence. Exact constructor-to-factory and rename/bridge strategies are implemented; the rest must not be inferred from class presence.
- Full target linkage, access, interface dispatch and Java-runtime compatibility verification. Same-descriptor inherited rename propagation is implemented, but changed override signatures and behavior still need semantic strategies.
- Universal bootstrap implementation. Consumer output-mode configuration, per-target configuration and development JAR production are connected.
- Minecraft resource-schema, mixin, access-transformer, loader metadata and reflection migrations. Standard Java service/manifest class-name resources are handled.
- Certified per-version mod JAR output. An explicit development transformation task exists, but complete target linkage and resource compatibility are not established.
- Loader-specific universal-JAR runtime bootstrapping.
- BuildScape target-version compilation or gameplay verification. Base-version compilation and partial bytecode transformation are verified as described above.

The current library build uses Java 17. This does not establish support for Java 8-era Minecraft runtimes. No Minecraft version pair is certified yet.

See [Developer Guide](Developer-Guide) for the currently available Gradle task and transformation contracts.

See [Rule Packs](Rule-Packs) for the implemented knowledge format and artifact-binding contract, and [JAR Transformation](Jar-Transformation) for Gradle usage and the current safety boundary.
- `continuumLibAuditTarget` and `continuumLibAuditTargets` check all scanned transformed member instructions, not just migration-rule matches. Reports distinguish found declarations, missing owners, incomplete hierarchy, missing members and static/interface shape mismatches; complex inheritance remains explicitly unresolved. These are declaration audits, not full target linkage certification. See [Target Audit](Target-Audit).
- Constructor-to-static-factory strategy integrated into XML packs, endpoint verification and Gradle JAR output, including constructor references and strict rejection of unsupported uninitialized-object flows.
- Verified vanilla identifier API packs for 1.20.1 → 1.21.1 and 1.21.1 → 26.3. The latter checks all 30 public methods and six public fields individually, not just the class mapping. Neither is whole-game certification.
- Explicit consumer source/target mapping configuration normalizes obfuscated API declarations; optional `output.namespace` remaps the produced JAR to target runtime names. The real-artifact Gradle fixture executes its transformed identifier call against actual 1.21.1 classes and bundled libraries on Java 21 when that runtime is supplied.
- Legacy SRG field descriptors can be recovered from real artifact declarations. Actual 1.7.10 block/item/entity declarations are included; this is not yet legacy cross-version JAR compatibility. See [Constructor Migrations](Constructor-Migrations).
- Namespace export preserves unrelated Java platform calls even when Minecraft mappings rename an identical method signature. Missing third-party hierarchy data still fails conservatively. Exported mod names are checked against target API names before writing; a real-artifact regression verifies rejection of an `akr` collision and preservation of the previous generated JAR.
- Portable Maven publication now includes the plugin marker and dependency metadata in `build/repository/`. Published-plugin consumer tests generate and execute adapted fixture JARs without a source checkout, injected plugin classpath or ContinuumLib runtime dependency. Public remote hosting is not provisioned.
- External test integration uses generic names and `referenceClasses`, with a selected Gradle project rather than applying a request to unrelated subprojects. Active source/test configuration no longer refers to a particular mod. Historical verification notes retain the identity of the reference previously tested.
- Optional `AuditTargetTask.failOnUnresolved` fails on unresolved member findings, missing referenced types or an empty class inventory after writing diagnostic reports. It is not a full linker or gameplay gate. See [Target Audit](Target-Audit).
- Checksum-pinned supplemental source/target dependencies are supported independently of bundled rule manifests. Gradle tracks their bytes and prevents reports from overwriting them. JARs and explicit target JDK JMODs can provide declarations without using the host runtime implicitly; module exports/readability remain unchecked.
- Field-to-accessor bridges now rewrite all four instance/static read/write instructions and field handles. The consumer integration test executes a generated JAR where the source public field became private and access is redirected to a target accessor. This is a reusable strategy, not a newly certified Minecraft API/version pair.
- Audits now distinguish definite access failures, access needing nest/package/receiver verification, and illegal final-field writes. Hook lookup rejects static/private interface methods as inherited class methods. Review findings were reproduced and fixed; malformed field descriptors are rejected before transformation.
- Target reference audits now perform complete JVM-aligned interface and default method resolution (JVMS §5.4.3.3/§5.4.3.4), searching superclasses before superinterfaces and resolving public `Object` methods on interfaces. Conflicting default methods from multiple superinterfaces are identified as `INHERITANCE_REQUIRES_REVIEW`.
- Target linkage verification now indexes `NestHost` and `NestMembers` (Java 11+ nestmates), granting verified nestmate private access (`DECLARATION_FOUND`) and rejecting cross-nest or cross-package private access (`ACCESS_DENIED`).
- Array types are synthesized per JVMS §5.4.3.3 with `java/lang/Object` superclass, `Cloneable`, `Serializable`, and public `clone()`. Signature-polymorphic calls (`MethodHandle`/`VarHandle`) match arbitrary invocation descriptors. `INVOKESPECIAL` targets validate subclass and interface implementation constraints, and protected receiver checks statically verify calls directed to caller subclasses.
- Continuum Knowledge Generation Pipeline implemented:
  - Durable `ApiSnapshot`, `ClassSnapshot`, and `MemberSnapshot` capture exact classes, members, access flags, descriptors, signatures, interfaces, superclasses, and artifact SHA-256 manifests.
  - Multi-version symbol lineage tracking with `SymbolId`, `SymbolVersion`, and `SymbolLineage` supports tracking conceptual identities across Minecraft versions, loaders, and mapping namespaces without collapsing overloads.
  - Conservative, evidence-backed candidate discovery (`CandidateDetector`) detects unchanged members, method renames, constructor-to-factory, and field-to-accessor patterns across 16 categorized evidence types (`EvidenceType`).
  - Strict ambiguity handling: ambiguous candidates (e.g. multiple same-descriptor methods or parameter permutations) are marked as `REVIEW_REQUIRED` and strictly prevented from automatic acceptance.
  - Verification gates (`RulePromoter`) enforce that only verified candidates (`VERIFIED_TRANSFORMATION`, `VERIFIED_LINKAGE`, `VERIFIED_RUNTIME`, `VERIFIED_BEHAVIOR`) can be promoted to executable `RulePack` rules; unverified candidates fail fast.
- Pre-Compilation Java Source Transformation Pipeline implemented:
  - Developer source files in `src/main/java` are preserved completely unmodified on disk; generated source is written strictly to temporary build directories (`build/continuum/<target>/generated-src/`).
  - JavaParser AST parser and symbol solver (`SourceParser`, `SourceUnit`) resolve API usages in native Minecraft mod sources.
  - Full AST-level rewriting (`SourceTransformer`) supports method renames, class renames with import reconciliation, bidirectional constructor ↔ static factory transformations (e.g. `Identifier.of("mod", "block")` ↔ `new Identifier("mod", "block")`), and field-to-accessor rewriting.
  - Target source compilation (`SourceCompiler`) compiles temporary generated Java sources against target Minecraft/loader dependencies using `javax.tools.JavaCompiler`.
  - Packaging (`TargetJarPackager`) produces target mod JARs, verified end-to-end against target declarations using `TargetReferenceAudit`.
  - New Gradle task `continuumLibTransformSource` orchestrates the complete source transformation workflow.
- Generic Target-Aware Inclusion/Exclusion Configuration System implemented:
  - Configuration structure under consumer mod's `src/main/resources/continuumlib/` (with fallback to `data/continuumlib/`):
    - `inclusions/`: optional directory of inclusion rules merged into a single logical `InclusionRuleSet`. Missing or empty means no inclusion filtering.
    - `exclusions/`: optional directory of exclusion rules merged into a single logical `ExclusionRuleSet`. Missing or empty means nothing is excluded.
  - Filter Domains:
    - `REGISTRY`: identifies elements by categorized, extensible `RegistryType` (`BLOCK`, `ITEM`, `BLOCK_ENTITY`, `ENTITY_TYPE`, `FLUID`, `MENU`, `SOUND_EVENT`, `PARTICLE`, `RECIPE_TYPE`, `RECIPE_SERIALIZER`, `ENCHANTMENT`, `EFFECT`, `ATTRIBUTE`, `CUSTOM_REGISTRY_ENTRY`). Independent filtering ensures removing a block does not implicitly remove its item.
    - `SOURCE`: filters source files by relative path (e.g. `com/example/legacy/OldFeature.java`).
    - `CLASS`: filters by fully-qualified class name (e.g. `com.example.legacy.OldFeature`).
    - `RESOURCE`: filters asset/data resource files by relative path (e.g. `assets/example/models/block/old_block.json`).
  - Reusable Target Environment Conditions (`EnvironmentCondition`):
    - Multi-operator version constraints (`>=`, `<=`, `>`, `<`, `=`, `!=`, compound ranges such as `>=1.20 <1.21`).
    - Evaluates against target Minecraft version, loader, loader version, Java version, mapping namespace, and output mode.
  - Semantic Registry Indexing and Conflict Detection:
    - `RegistryDeclarationScanner` indexes AST registrations from Forge/NeoForge `DeferredRegister` and Fabric/Vanilla `Registry.register`.
    - `ExclusionConflictDetector` verifies that excluded registry declarations are not referenced by target-enabled source code, producing formatted diagnostics (`CONTINUUM EXCLUSION CONFLICT`) and preventing silent compilation failures.
  - Zero modification to developer's original files: filtering applies strictly to generated target trees and packaged target JARs.
- Unified Per-Target Generation Architecture:
  - Canonical `ResolvedTarget` representation consolidating environments, artifact manifests, classpaths, mappings, rule packs, isolated workspaces, and project configuration.
  - Deterministic, target-isolated workspaces (`GeneratedWorkspace`) under `build/continuum/targets/<target-id>/` (`source/`, `resources/`, `classes/`, `reports/`, `metadata/`, `staging/`, `output/`). Staged output ensures failed builds never expose partial final JARs.
  - File-level source selection performed before AST parsing (`FILE SELECTION -> AST PARSING -> SEMANTIC TRANSFORMATION`), preventing excluded files from being unnecessarily parsed.
  - Unified configuration discovery service (`ProjectConfigurationLocator`) discovering canonical config at `src/main/resources/continuumlib`, with fallback to `src/main/resources/data/continuumlib`, and fast failure upon conflicting dual configurations (`DUAL_CONFIGURATION_CONFLICT`).
  - Canonical migration precision (`CanonicalMigrationRule`, `CanonicalMigrationPlan`): preserves exact source/target owners, names, descriptors, opcodes, environments, originating rule packs, and evidence.
  - Explicit matching confidence (`MigrationConfidence`): `SEMANTICALLY_RESOLVED`, `STRUCTURALLY_RESOLVED`, `HEURISTIC`, `AMBIGUOUS`, and `UNRESOLVED`. Ambiguous overloads emit clear diagnostics (`AMBIGUOUS_MIGRATION`) and never guess.
  - Layer-aware transformation dispatch (`MigrationLayer`): clearly distinguishes `SOURCE_AST`, `BYTECODE`, and `VALIDATION_ONLY` rules to prevent duplicating transformations.
  - Transformation accounting and deterministic reporting (`AppliedMigration`, `GenerationReportWriter`) outputting `build/continuum/targets/<target-id>/reports/generation.txt`.
  - Reusable target generation pipeline (`TargetGenerationPipeline`) orchestrating the entire lifecycle, callable independently and integrated via Gradle (`GenerateTargetTask` and `continuumLibGenerate_<id>`).




