# Target API Audit

Run `gradlew continuumLibAuditTarget` in the consuming mod. It builds the configured development transformation first, then checks every scanned method/constructor call, field instruction and method handle against the transformed mod and supplied target API artifacts. Rules are not required for a reference to be audited: unchanged calls are checked too.

The report is `build/reports/continuumlib/target-audit.tsv`. It records caller class, caller method and descriptor, available source line, target owner/member/descriptor, instruction opcode, method-handle flag and result. Tab, newline and backslash characters in obfuscated names are escaped.

A second report, `target-types.tsv`, lists present/missing types referenced by each mod class, including descriptors, signatures, annotations, hierarchy and stack-map types. Multiple-target runs write `<id>-types.tsv`. `TYPE_PRESENT` only establishes that an indexed declaration exists, not that its access or use is valid.

For configured multiple outputs, run `gradlew continuumLibAuditTargets`. Each target receives `build/reports/continuumlib/<id>-audit.tsv`; individual tasks are named `continuumLibAudit_<id>`.

## Reading the results

- `DECLARATION_FOUND`: an exact declaration was found with the expected static/instance shape, or a legal interface default method, signature-polymorphic method, nestmate private access, or verified protected subclass receiver was proven. This is **not** proof of compatible behavior or successful game loading.
- `OWNER_MISSING`: the referenced owner is not among the supplied target classes. It may be a missing dependency rather than a removed API.
- `HIERARCHY_INCOMPLETE`: an ancestor needed for lookup is absent, or a hierarchy cycle was found. Do not infer a missing member from this result.
- `MEMBER_MISSING`: no declaration was found in the checked complete superclass and superinterface chain, or a constructor/initializer is absent on its exact owner. Constructors are never inherited.
- `STATIC_MISMATCH`: the declaration and instruction disagree about static versus instance access.
- `OWNER_KIND_MISMATCH`: a method reference's class/interface owner flag disagrees with the supplied class declaration, or a constructor was targeted on an interface.
- `INHERITANCE_REQUIRES_REVIEW`: conflicting maximally-specific default methods from multiple interfaces, ambiguous interface fields, or a private ancestor declaration requires more detailed resolution; the audit intentionally does not guess.
- `ACCESS_DENIED`: a non-public symbolic owner or package-private member is in another package, different nest hosts for private access, illegal `invokespecial` receiver/super-call, or a protected member's caller is provably not a subclass.
- `ACCESS_REQUIRES_REVIEW`: non-nestmate private access lacking nest attributes, runtime package/class-loader identity for non-public members, or a protected receiver called through a superclass reference needs additional runtime validation.
- `FINAL_WRITE_ILLEGAL`: a final-field write violates the declaring-class/initializer restriction, or a setter method handle targets a final field.

The audit is diagnostic by default. To fail the build when any scanned member remains unresolved, a referenced type is missing, or the input contains no classes:

```groovy
tasks.withType(com.kyroxova.continuumlib.gradle.AuditTargetTask).configureEach {
    failOnUnresolved.set(true)
}
```

This applies to single- and multi-target audit tasks. Both reports are written before the strict failure so callers can locate the gaps. It does not automatically attach the audit to `build`; invoke `continuumLibAuditTarget`/`continuumLibAuditTargets` in CI or add the desired task dependency. A development transformed JAR may already exist when its subsequent audit fails; do not publish that output based on transformation success alone.

Even a passing strict audit is labeled `DECLARATION_AUDIT_ONLY`; outputs remain `NOT_CERTIFIED`. Runtime classes are not silently borrowed from the JDK running Gradle. Supply runtime and dependency declarations explicitly as described below.

## Additional dependency declarations

In the consumer's `transform.properties`, without changing the selected rule-pack artifact manifest:

```properties
classpath.source.library.file=apis/source-library.jar
classpath.source.library.sha256=<64 hexadecimal SHA-256 characters>
classpath.target.library.file=apis/target-library.jar
classpath.target.library.sha256=<64 hexadecimal SHA-256 characters>
classpath.target.java-base.file=apis/target-jdk/jmods/java.base.jmod
classpath.target.java-base.sha256=<64 hexadecimal SHA-256 characters>
```

Replace placeholders with actual digests. Every entry requires a path and checksum; identifiers allow letters, numbers, underscores and hyphens. Paths resolve against the consumer project. Files are Gradle inputs and protected from output overwrites. Changed bytes and duplicate classes are rejected.

JARs and explicit `.jmod` files are supported. JMOD declarations come from `classes/`, excluding `module-info.class`. For Java 8 supply the target runtime JAR. Do not substitute Gradle's host JDK for another target runtime. These dependencies are declaration inputs only: they are not executed, rewritten, embedded or installed into the mod.

Dependencies must use the same **input namespace as that side's API artifacts**. They are merged before namespace normalization, including descriptors that refer to game types. Supply environment-matching versions. Source dependencies support hierarchy resolution; target dependencies support rule endpoint checks and auditing. API comparisons also include supplied dependencies.

Module descriptors are not modeled: exports, readability, opens and launch flags are **not checked**. A public member in a non-exported JDK package can still be `DECLARATION_FOUND`. JMOD indexing and strict auditing do not establish module-aware linkage.

The implemented access and final-write checks follow [JVM access control](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.4) and [field-write restrictions](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.putfield), including nestmate validation (Java 11+ `NestHost`/`NestMembers`), array type synthesization (`java/lang/Object` superclass, `Cloneable`, `Serializable`, public `clone()`), signature-polymorphic call handling (`MethodHandle`/`VarHandle`), interface default method selection (JVMS §5.4.3.4), `invokespecial` target legality, and protected receiver constraint checking.

Reflective strings, Minecraft resources, class-loader separation, runtime package identity across distinct loaders, and module exports/readability require additional validation. This report is not a complete target linker.

## BuildScape reference finding

The local Forge 1.18.2 BuildScape development output was audited against the pinned Forge 1.19.2 universal artifact. Among 143,967 member instructions, 73,953 had declarations found, 66,067 had missing owners, 3,339 had incomplete hierarchy and 607 required inheritance review. One exact constructor was missing: `ForgeFlowingFluid.Properties(Supplier, Supplier, FluidAttributes.Builder)`, used by `ModFluids` at source line 43. This is a concrete unhandled migration, not a rule to rename constructors blindly. The large missing-owner count reflects the intentionally incomplete target classpath, which does not include the Minecraft or Java runtime API.
