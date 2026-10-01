# Forge 1.18.2 block registration

Historical example: the source-pattern recognizer described below has been removed. The active implementation inspects compiled classes and preserves their bodies. Cross-version registration support is not yet certified.

ContinuumLib currently recognizes Forge 1.18.2 block registrations created with `DeferredRegister.create(...)` and `register(id, () -> new CustomBlock(properties))`.

The analyzer preserves the registry kind, mod ID expression, registration ID, custom block type, factory expression, material, map color, strength, and sound as typed semantic data. It does not translate `RegistryObject` directly into a target identifier type.

Unsupported registration shapes are not guessed. Later capability providers either resolve them or return an explicit diagnostic.
