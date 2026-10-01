# Registry families

Status correction: the name-based Forge classifier described below has been removed. Registry field names do not establish API identity. The active bytecode inspection layer records actual owner classes, names, descriptors and inheritance; semantic registration adapters are pending.

ContinuumLib represents registry meaning with `RegistryKind` rather than comparing plural field names or treating every registration as a block.

The model currently identifies blocks, items, block entity types, entity types, fluids, sounds, particles, menu types, recipe serializers, creative tabs, biomes, configured features, placed features, and custom registries. Historical aliases such as Forge `TILE_ENTITIES` and `CONTAINERS` normalize to stable semantic kinds.

Registration factories and custom implementation classes are analyzed separately because a `BlockEntityType` or `EntityType` is a registered factory for custom instances, not the instance itself.
