package com.kyroxova.continuumlib.knowledgebase.mappings;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance bidirectional mapping translation table across Minecraft obfuscation formats.
 * Supports class names, method names, field names, and descriptor signatures.
 */
public final class MappingTranslationTable {

    public static final class ClassMapping {
        private final Map<MappingFormat, String> primaryNames = new EnumMap<>(MappingFormat.class);
        private final Map<MappingFormat, Set<String>> allAliases = new EnumMap<>(MappingFormat.class);

        public ClassMapping() {
            for (MappingFormat format : MappingFormat.values()) {
                allAliases.put(format, new HashSet<>());
            }
        }

        public String getPrimaryName(MappingFormat format) {
            return primaryNames.get(format);
        }

        public Set<String> getAliases(MappingFormat format) {
            return Collections.unmodifiableSet(allAliases.get(format));
        }
    }

    public static final class MethodMapping {
        private final String ownerClass;
        private final Map<MappingFormat, String> primaryNames = new EnumMap<>(MappingFormat.class);
        private final Map<MappingFormat, Set<String>> allAliases = new EnumMap<>(MappingFormat.class);

        public MethodMapping(String ownerClass) {
            this.ownerClass = ownerClass;
            for (MappingFormat format : MappingFormat.values()) {
                allAliases.put(format, new HashSet<>());
            }
        }

        public String getOwnerClass() {
            return ownerClass;
        }

        public String getPrimaryName(MappingFormat format) {
            return primaryNames.get(format);
        }

        public Set<String> getAliases(MappingFormat format) {
            return Collections.unmodifiableSet(allAliases.get(format));
        }
    }

    public static final class FieldMapping {
        private final String ownerClass;
        private final Map<MappingFormat, String> primaryNames = new EnumMap<>(MappingFormat.class);
        private final Map<MappingFormat, Set<String>> allAliases = new EnumMap<>(MappingFormat.class);

        public FieldMapping(String ownerClass) {
            this.ownerClass = ownerClass;
            for (MappingFormat format : MappingFormat.values()) {
                allAliases.put(format, new HashSet<>());
            }
        }

        public String getOwnerClass() {
            return ownerClass;
        }

        public String getPrimaryName(MappingFormat format) {
            return primaryNames.get(format);
        }

        public Set<String> getAliases(MappingFormat format) {
            return Collections.unmodifiableSet(allAliases.get(format));
        }
    }

    // Bidirectional Fast Lookup Indexes
    private final Map<MappingFormat, Map<String, ClassMapping>> classIndex = new EnumMap<>(MappingFormat.class);

    // Method Indexes (unqualified and owner-scoped)
    private final Map<MappingFormat, Map<String, MethodMapping>> methodIndex = new EnumMap<>(MappingFormat.class);
    private final Map<MappingFormat, Map<String, Map<String, MethodMapping>>> ownerScopedMethodIndex = new EnumMap<>(MappingFormat.class);

    // Field Indexes (unqualified and owner-scoped)
    private final Map<MappingFormat, Map<String, FieldMapping>> fieldIndex = new EnumMap<>(MappingFormat.class);
    private final Map<MappingFormat, Map<String, Map<String, FieldMapping>>> ownerScopedFieldIndex = new EnumMap<>(MappingFormat.class);

    private final Set<ClassMapping> allClasses = ConcurrentHashMap.newKeySet();
    private final Set<MethodMapping> allMethods = ConcurrentHashMap.newKeySet();
    private final Set<FieldMapping> allFields = ConcurrentHashMap.newKeySet();

    public MappingTranslationTable() {
        for (MappingFormat format : MappingFormat.values()) {
            classIndex.put(format, new ConcurrentHashMap<>());
            methodIndex.put(format, new ConcurrentHashMap<>());
            ownerScopedMethodIndex.put(format, new ConcurrentHashMap<>());
            fieldIndex.put(format, new ConcurrentHashMap<>());
            ownerScopedFieldIndex.put(format, new ConcurrentHashMap<>());
        }
    }

    /**
     * Creates and initializes a default translation table with fundamental Minecraft mappings.
     */
    public static MappingTranslationTable createDefault() {
        MappingTranslationTable table = new MappingTranslationTable();
        table.registerDefaultMappings();
        return table;
    }

    // ==================== Registration API ====================

    /**
     * Registers a class mapping across all 4 formats.
     */
    public MappingTranslationTable registerClass(String mojmap, String intermediary, String srg, String yarn) {
        ClassMapping mapping = new ClassMapping();
        bindClassFormat(mapping, MappingFormat.MOJMAP, mojmap);
        bindClassFormat(mapping, MappingFormat.INTERMEDIARY, intermediary);
        bindClassFormat(mapping, MappingFormat.SRG, srg);
        bindClassFormat(mapping, MappingFormat.YARN, yarn);
        allClasses.add(mapping);
        return this;
    }

    /**
     * Adds an alias for an existing class mapping under a specific format.
     */
    public MappingTranslationTable registerClassAlias(MappingFormat format, String primaryName, String alias) {
        if (primaryName == null || alias == null) return this;
        String normalizedPrimary = normalizeName(primaryName);
        String normalizedAlias = normalizeName(alias);
        ClassMapping mapping = classIndex.get(format).get(normalizedPrimary);
        if (mapping != null) {
            mapping.allAliases.get(format).add(normalizedAlias);
            classIndex.get(format).put(normalizedAlias, mapping);
        }
        return this;
    }

    private void bindClassFormat(ClassMapping mapping, MappingFormat format, String name) {
        if (name == null || name.isEmpty()) return;
        String normalized = normalizeName(name);
        mapping.primaryNames.put(format, normalized);
        mapping.allAliases.get(format).add(normalized);
        classIndex.get(format).put(normalized, mapping);
    }

    /**
     * Registers a method mapping across all 4 formats.
     */
    public MappingTranslationTable registerMethod(String ownerMojmap, String mojmap, String intermediary, String srg, String yarn) {
        String normalizedOwner = ownerMojmap != null ? normalizeName(ownerMojmap) : null;
        MethodMapping mapping = new MethodMapping(normalizedOwner);
        bindMethodFormat(mapping, MappingFormat.MOJMAP, mojmap);
        bindMethodFormat(mapping, MappingFormat.INTERMEDIARY, intermediary);
        bindMethodFormat(mapping, MappingFormat.SRG, srg);
        bindMethodFormat(mapping, MappingFormat.YARN, yarn);
        allMethods.add(mapping);
        return this;
    }

    /**
     * Registers an alias for a method under a specific format.
     */
    public MappingTranslationTable registerMethodAlias(MappingFormat format, String primaryName, String alias) {
        if (primaryName == null || alias == null) return this;
        MethodMapping mapping = methodIndex.get(format).get(primaryName);
        if (mapping != null) {
            mapping.allAliases.get(format).add(alias);
            methodIndex.get(format).put(alias, mapping);
            if (mapping.getOwnerClass() != null) {
                ownerScopedMethodIndex.get(format)
                        .computeIfAbsent(mapping.getOwnerClass(), k -> new ConcurrentHashMap<>())
                        .put(alias, mapping);
            }
        }
        return this;
    }

    private void bindMethodFormat(MethodMapping mapping, MappingFormat format, String name) {
        if (name == null || name.isEmpty()) return;
        mapping.primaryNames.put(format, name);
        mapping.allAliases.get(format).add(name);
        methodIndex.get(format).put(name, mapping);
        if (mapping.getOwnerClass() != null) {
            ownerScopedMethodIndex.get(format)
                    .computeIfAbsent(mapping.getOwnerClass(), k -> new ConcurrentHashMap<>())
                    .put(name, mapping);
        }
    }

    /**
     * Registers a field mapping across all 4 formats.
     */
    public MappingTranslationTable registerField(String ownerMojmap, String mojmap, String intermediary, String srg, String yarn) {
        String normalizedOwner = ownerMojmap != null ? normalizeName(ownerMojmap) : null;
        FieldMapping mapping = new FieldMapping(normalizedOwner);
        bindFieldFormat(mapping, MappingFormat.MOJMAP, mojmap);
        bindFieldFormat(mapping, MappingFormat.INTERMEDIARY, intermediary);
        bindFieldFormat(mapping, MappingFormat.SRG, srg);
        bindFieldFormat(mapping, MappingFormat.YARN, yarn);
        allFields.add(mapping);
        return this;
    }

    /**
     * Registers an alias for a field under a specific format.
     */
    public MappingTranslationTable registerFieldAlias(MappingFormat format, String primaryName, String alias) {
        if (primaryName == null || alias == null) return this;
        FieldMapping mapping = fieldIndex.get(format).get(primaryName);
        if (mapping != null) {
            mapping.allAliases.get(format).add(alias);
            fieldIndex.get(format).put(alias, mapping);
            if (mapping.getOwnerClass() != null) {
                ownerScopedFieldIndex.get(format)
                        .computeIfAbsent(mapping.getOwnerClass(), k -> new ConcurrentHashMap<>())
                        .put(alias, mapping);
            }
        }
        return this;
    }

    private void bindFieldFormat(FieldMapping mapping, MappingFormat format, String name) {
        if (name == null || name.isEmpty()) return;
        mapping.primaryNames.put(format, name);
        mapping.allAliases.get(format).add(name);
        fieldIndex.get(format).put(name, mapping);
        if (mapping.getOwnerClass() != null) {
            ownerScopedFieldIndex.get(format)
                    .computeIfAbsent(mapping.getOwnerClass(), k -> new ConcurrentHashMap<>())
                    .put(name, mapping);
        }
    }

    // ==================== Query & Translation API ====================

    /**
     * Translates a class name from one MappingFormat to another.
     * Preserves dot or slash package notation.
     * Returns the original className if from == to or no translation exists.
     */
    public String translateClass(String className, MappingFormat from, MappingFormat to) {
        if (className == null || from == null || to == null || from == to) {
            return className;
        }
        boolean isDotNotation = className.contains(".") && !className.contains("/");
        String normalized = normalizeName(className);
        ClassMapping mapping = classIndex.get(from).get(normalized);
        if (mapping != null) {
            String targetName = mapping.getPrimaryName(to);
            if (targetName != null) {
                return isDotNotation ? targetName.replace('/', '.') : targetName;
            }
        }
        return className;
    }

    public Optional<String> getClassMapping(String className, MappingFormat from, MappingFormat to) {
        if (className == null || from == null || to == null) return Optional.empty();
        String normalized = normalizeName(className);
        ClassMapping mapping = classIndex.get(from).get(normalized);
        if (mapping != null) {
            String target = mapping.getPrimaryName(to);
            if (target != null) {
                return Optional.of(className.contains(".") && !className.contains("/") ? target.replace('/', '.') : target);
            }
        }
        return Optional.empty();
    }

    public boolean hasClass(String className, MappingFormat format) {
        if (className == null || format == null) return false;
        return classIndex.get(format).containsKey(normalizeName(className));
    }

    /**
     * Translates a method name, prioritizing owner-scoped match if owner is provided.
     */
    public String translateMethod(String owner, String methodName, String methodDesc, MappingFormat from, MappingFormat to) {
        if (methodName == null || from == null || to == null || from == to) {
            return methodName;
        }
        if (owner != null) {
            String normalizedOwner = normalizeName(owner);
            // Check direct owner match
            Map<String, MethodMapping> scoped = ownerScopedMethodIndex.get(from).get(normalizedOwner);
            if (scoped != null) {
                MethodMapping mapping = scoped.get(methodName);
                if (mapping != null && mapping.getPrimaryName(to) != null) {
                    return mapping.getPrimaryName(to);
                }
            }
            // Check owner mapped back to MOJMAP owner id
            String mojmapOwner = translateClass(normalizedOwner, from, MappingFormat.MOJMAP);
            if (!mojmapOwner.equals(normalizedOwner)) {
                Map<String, MethodMapping> mojmapScoped = ownerScopedMethodIndex.get(from).get(mojmapOwner);
                if (mojmapScoped != null) {
                    MethodMapping mapping = mojmapScoped.get(methodName);
                    if (mapping != null && mapping.getPrimaryName(to) != null) {
                        return mapping.getPrimaryName(to);
                    }
                }
            }
        }
        // Fallback to unqualified method lookup
        MethodMapping mapping = methodIndex.get(from).get(methodName);
        if (mapping != null) {
            String targetName = mapping.getPrimaryName(to);
            if (targetName != null) {
                return targetName;
            }
        }
        return methodName;
    }

    public String translateMethod(String methodName, MappingFormat from, MappingFormat to) {
        return translateMethod(null, methodName, null, from, to);
    }

    public Optional<String> getMethodMapping(String owner, String methodName, String methodDesc, MappingFormat from, MappingFormat to) {
        String translated = translateMethod(owner, methodName, methodDesc, from, to);
        if (translated != null && !translated.equals(methodName)) {
            return Optional.of(translated);
        }
        return Optional.empty();
    }

    public boolean hasMethod(String methodName, MappingFormat format) {
        if (methodName == null || format == null) return false;
        return methodIndex.get(format).containsKey(methodName);
    }

    /**
     * Translates a field name, prioritizing owner-scoped match if owner is provided.
     */
    public String translateField(String owner, String fieldName, String fieldDesc, MappingFormat from, MappingFormat to) {
        if (fieldName == null || from == null || to == null || from == to) {
            return fieldName;
        }
        if (owner != null) {
            String normalizedOwner = normalizeName(owner);
            Map<String, FieldMapping> scoped = ownerScopedFieldIndex.get(from).get(normalizedOwner);
            if (scoped != null) {
                FieldMapping mapping = scoped.get(fieldName);
                if (mapping != null && mapping.getPrimaryName(to) != null) {
                    return mapping.getPrimaryName(to);
                }
            }
            String mojmapOwner = translateClass(normalizedOwner, from, MappingFormat.MOJMAP);
            if (!mojmapOwner.equals(normalizedOwner)) {
                Map<String, FieldMapping> mojmapScoped = ownerScopedFieldIndex.get(from).get(mojmapOwner);
                if (mojmapScoped != null) {
                    FieldMapping mapping = mojmapScoped.get(fieldName);
                    if (mapping != null && mapping.getPrimaryName(to) != null) {
                        return mapping.getPrimaryName(to);
                    }
                }
            }
        }
        FieldMapping mapping = fieldIndex.get(from).get(fieldName);
        if (mapping != null) {
            String targetName = mapping.getPrimaryName(to);
            if (targetName != null) {
                return targetName;
            }
        }
        return fieldName;
    }

    public String translateField(String fieldName, MappingFormat from, MappingFormat to) {
        return translateField(null, fieldName, null, from, to);
    }

    public Optional<String> getFieldMapping(String owner, String fieldName, String fieldDesc, MappingFormat from, MappingFormat to) {
        String translated = translateField(owner, fieldName, fieldDesc, from, to);
        if (translated != null && !translated.equals(fieldName)) {
            return Optional.of(translated);
        }
        return Optional.empty();
    }

    public boolean hasField(String fieldName, MappingFormat format) {
        if (fieldName == null || format == null) return false;
        return fieldIndex.get(format).containsKey(fieldName);
    }

    /**
     * Translates all class types embedded within a bytecode descriptor or type signature.
     */
    public String translateDescriptor(String descriptor, MappingFormat from, MappingFormat to) {
        if (descriptor == null || from == null || to == null || from == to) {
            return descriptor;
        }
        if (!descriptor.contains("L")) {
            return descriptor;
        }
        StringBuilder sb = new StringBuilder(descriptor.length() + 16);
        int len = descriptor.length();
        int i = 0;
        while (i < len) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                if (semi > i) {
                    String internalName = descriptor.substring(i + 1, semi);
                    String remapped = translateClass(internalName, from, to);
                    sb.append('L').append(remapped).append(';');
                    i = semi + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    public int getRegisteredClassCount() {
        return allClasses.size();
    }

    public int getRegisteredMethodCount() {
        return allMethods.size();
    }

    public int getRegisteredFieldCount() {
        return allFields.size();
    }

    private static String normalizeName(String name) {
        return name.replace('.', '/');
    }

    // ==================== Default Mappings ====================

    private void registerDefaultMappings() {
        // --- 1. Classes ---
        // Block
        registerClass("net/minecraft/world/level/block/Block", "net/minecraft/class_2248", "net/minecraft/block/Block", "net/minecraft/block/Block");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/level/block/Block", "net/minecraft/block/Block");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/block/Block", "net/minecraft/world/level/block/Block");

        // BlockState
        registerClass("net/minecraft/world/level/block/state/BlockState", "net/minecraft/class_2680", "net/minecraft/block/BlockState", "net/minecraft/block/BlockState");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/level/block/state/BlockState", "net/minecraft/block/BlockState");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/block/BlockState", "net/minecraft/world/level/block/state/BlockState");

        // BlockEntity / TileEntity
        registerClass("net/minecraft/world/level/block/entity/BlockEntity", "net/minecraft/class_2586", "net/minecraft/tileentity/TileEntity", "net/minecraft/block/entity/BlockEntity");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/level/block/entity/BlockEntity", "net/minecraft/tileentity/TileEntity");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/tileentity/TileEntity", "net/minecraft/world/level/block/entity/BlockEntity");

        // Item
        registerClass("net/minecraft/world/item/Item", "net/minecraft/class_1792", "net/minecraft/item/Item", "net/minecraft/item/Item");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/item/Item", "net/minecraft/item/Item");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/item/Item", "net/minecraft/world/item/Item");

        // ItemStack
        registerClass("net/minecraft/world/item/ItemStack", "net/minecraft/class_1799", "net/minecraft/item/ItemStack", "net/minecraft/item/ItemStack");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/item/ItemStack", "net/minecraft/item/ItemStack");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/item/ItemStack", "net/minecraft/world/item/ItemStack");

        // Level / World
        registerClass("net/minecraft/world/level/Level", "net/minecraft/class_1937", "net/minecraft/world/World", "net/minecraft/world/World");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/level/Level", "net/minecraft/world/World");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/world/World", "net/minecraft/world/level/Level");

        // ServerLevel / ServerWorld
        registerClass("net/minecraft/server/level/ServerLevel", "net/minecraft/class_3218", "net/minecraft/world/server/ServerWorld", "net/minecraft/server/world/ServerWorld");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/server/level/ServerLevel", "net/minecraft/world/server/ServerWorld");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/world/server/ServerWorld", "net/minecraft/server/level/ServerLevel");

        // ClientLevel / ClientWorld
        registerClass("net/minecraft/client/multiplayer/ClientLevel", "net/minecraft/class_638", "net/minecraft/client/world/ClientWorld", "net/minecraft/client/world/ClientWorld");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/client/multiplayer/ClientLevel", "net/minecraft/client/world/ClientWorld");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/client/world/ClientWorld", "net/minecraft/client/multiplayer/ClientLevel");

        // Entity
        registerClass("net/minecraft/world/entity/Entity", "net/minecraft/class_1297", "net/minecraft/entity/Entity", "net/minecraft/entity/Entity");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/entity/Entity", "net/minecraft/entity/Entity");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/entity/Entity", "net/minecraft/world/entity/Entity");

        // LivingEntity
        registerClass("net/minecraft/world/entity/LivingEntity", "net/minecraft/class_1309", "net/minecraft/entity/LivingEntity", "net/minecraft/entity/LivingEntity");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/entity/LivingEntity", "net/minecraft/entity/LivingEntity");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/entity/LivingEntity", "net/minecraft/world/entity/LivingEntity");

        // Player
        registerClass("net/minecraft/world/entity/player/Player", "net/minecraft/class_1657", "net/minecraft/entity/player/PlayerEntity", "net/minecraft/entity/player/PlayerEntity");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/entity/player/Player", "net/minecraft/entity/player/PlayerEntity");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/entity/player/PlayerEntity", "net/minecraft/world/entity/player/Player");

        // BlockPos
        registerClass("net/minecraft/core/BlockPos", "net/minecraft/class_2338", "net/minecraft/util/math/BlockPos", "net/minecraft/util/math/BlockPos");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/core/BlockPos", "net/minecraft/util/math/BlockPos");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/util/math/BlockPos", "net/minecraft/core/BlockPos");

        // ResourceLocation / Identifier
        registerClass("net/minecraft/resources/ResourceLocation", "net/minecraft/class_2960", "net/minecraft/util/ResourceLocation", "net/minecraft/util/Identifier");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/resources/ResourceLocation", "net/minecraft/util/ResourceLocation");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/util/ResourceLocation", "net/minecraft/resources/ResourceLocation");

        // AABB / Box
        registerClass("net/minecraft/world/phys/AABB", "net/minecraft/class_238", "net/minecraft/util/math/AxisAlignedBB", "net/minecraft/util/math/Box");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/phys/AABB", "net/minecraft/util/math/AxisAlignedBB");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/util/math/AxisAlignedBB", "net/minecraft/world/phys/AABB");

        // Vec3 / Vec3d
        registerClass("net/minecraft/world/phys/Vec3", "net/minecraft/class_243", "net/minecraft/util/math/vector/Vector3d", "net/minecraft/util/math/Vec3d");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/phys/Vec3", "net/minecraft/util/math/vector/Vector3d");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/util/math/vector/Vector3d", "net/minecraft/world/phys/Vec3");

        // CompoundTag / CompoundNBT / NbtCompound
        registerClass("net/minecraft/nbt/CompoundTag", "net/minecraft/class_2487", "net/minecraft/nbt/CompoundNBT", "net/minecraft/nbt/NbtCompound");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/nbt/CompoundTag", "net/minecraft/nbt/CompoundNBT");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/nbt/CompoundNBT", "net/minecraft/nbt/CompoundTag");

        // Minecraft / MinecraftClient
        registerClass("net/minecraft/client/Minecraft", "net/minecraft/class_310", "net/minecraft/client/Minecraft", "net/minecraft/client/MinecraftClient");

        // BlockGetter / IBlockReader
        registerClass("net/minecraft/world/level/BlockGetter", "net/minecraft/class_1922", "net/minecraft/world/IBlockReader", "net/minecraft/world/BlockView");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/level/BlockGetter", "net/minecraft/world/IBlockReader");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/world/IBlockReader", "net/minecraft/world/level/BlockGetter");

        // LevelAccessor / IWorld
        registerClass("net/minecraft/world/level/LevelAccessor", "net/minecraft/class_1936", "net/minecraft/world/IWorld", "net/minecraft/world/WorldAccess");
        registerClassAlias(MappingFormat.MOJMAP, "net/minecraft/world/level/LevelAccessor", "net/minecraft/world/IWorld");
        registerClassAlias(MappingFormat.SRG, "net/minecraft/world/IWorld", "net/minecraft/world/level/LevelAccessor");

        // --- 2. Methods ---
        // ItemStack methods
        registerMethod("net/minecraft/world/item/ItemStack", "getItem", "method_7909", "func_77973_b", "getItem");
        registerMethodAlias(MappingFormat.SRG, "func_77973_b", "m_41720_");

        registerMethod("net/minecraft/world/item/ItemStack", "getCount", "method_7947", "func_190916_E", "getCount");
        registerMethodAlias(MappingFormat.SRG, "func_190916_E", "m_41613_");

        registerMethod("net/minecraft/world/item/ItemStack", "setCount", "method_7939", "func_190920_e", "setCount");
        registerMethodAlias(MappingFormat.SRG, "func_190920_e", "m_41764_");

        registerMethod("net/minecraft/world/item/ItemStack", "isEmpty", "method_7960", "func_190926_b", "isEmpty");
        registerMethodAlias(MappingFormat.SRG, "func_190926_b", "m_41619_");

        registerMethod("net/minecraft/world/item/ItemStack", "getDamageValue", "method_7919", "func_77952_i", "getDamage");
        registerMethodAlias(MappingFormat.SRG, "func_77952_i", "m_41773_");

        registerMethod("net/minecraft/world/item/ItemStack", "setDamageValue", "method_7956", "func_196085_b", "setDamage");
        registerMethodAlias(MappingFormat.SRG, "func_196085_b", "m_41721_");

        registerMethod("net/minecraft/world/item/ItemStack", "getTag", "method_7969", "func_77978_p", "getNbt");
        registerMethodAlias(MappingFormat.SRG, "func_77978_p", "m_41783_");

        registerMethod("net/minecraft/world/item/ItemStack", "copy", "method_7972", "func_77946_l", "copy");
        registerMethodAlias(MappingFormat.SRG, "func_77946_l", "m_41777_");

        registerMethod("net/minecraft/world/item/ItemStack", "getMaxStackSize", "method_7914", "func_77976_d", "getMaxCount");
        registerMethodAlias(MappingFormat.SRG, "func_77976_d", "m_41741_");

        // Block methods
        registerMethod("net/minecraft/world/level/block/Block", "defaultBlockState", "method_9564", "func_176223_P", "getDefaultState");
        registerMethodAlias(MappingFormat.SRG, "func_176223_P", "m_49966_");

        registerMethod("net/minecraft/world/level/block/Block", "getDescriptionId", "method_9539", "func_208064_t", "getTranslationKey");
        registerMethodAlias(MappingFormat.SRG, "func_208064_t", "m_7346_");

        registerMethod("net/minecraft/world/level/block/Block", "asItem", "method_8389", "func_199767_j", "asItem");
        registerMethodAlias(MappingFormat.SRG, "func_199767_j", "m_5456_");

        // Item methods
        registerMethod("net/minecraft/world/item/Item", "getDefaultInstance", "method_7854", "func_190903_i", "getDefaultStack");
        registerMethodAlias(MappingFormat.SRG, "func_190903_i", "m_7968_");

        registerMethod("net/minecraft/world/item/Item", "getDescriptionId", "method_7876", "func_77658_a", "getTranslationKey");
        registerMethodAlias(MappingFormat.SRG, "func_77658_a", "m_5524_");

        registerMethod("net/minecraft/world/item/Item", "getMaxStackSize", "method_7882", "func_77639_j", "getMaxCount");
        registerMethodAlias(MappingFormat.SRG, "func_77639_j", "m_41459_");

        registerMethod("net/minecraft/world/item/Item", "isDamageable", "method_7846", "func_77645_m", "isDamageable");
        registerMethodAlias(MappingFormat.SRG, "func_77645_m", "m_41462_");

        // Level / World methods
        registerMethod("net/minecraft/world/level/Level", "getBlockState", "method_8320", "func_180495_p", "getBlockState");
        registerMethodAlias(MappingFormat.SRG, "func_180495_p", "m_8055_");

        registerMethod("net/minecraft/world/level/Level", "setBlock", "method_30092", "func_241211_a_", "setBlockState");
        registerMethodAlias(MappingFormat.SRG, "func_241211_a_", "m_7731_");

        registerMethod("net/minecraft/world/level/Level", "getBlockEntity", "method_8321", "func_175625_s", "getBlockEntity");
        registerMethodAlias(MappingFormat.SRG, "func_175625_s", "m_7702_");

        registerMethod("net/minecraft/world/level/Level", "isClientSide", "method_8608", "func_201670_d", "isClient");
        registerMethodAlias(MappingFormat.SRG, "func_201670_d", "m_5776_");

        registerMethod("net/minecraft/world/level/Level", "destroyBlock", "method_8595", "func_241212_a_", "breakBlock");
        registerMethodAlias(MappingFormat.SRG, "func_241212_a_", "m_46961_");

        registerMethod("net/minecraft/world/level/Level", "getGameTime", "method_8510", "func_82737_E", "getTime");
        registerMethodAlias(MappingFormat.SRG, "func_82737_E", "m_46467_");

        // Entity methods
        registerMethod("net/minecraft/world/entity/Entity", "getX", "method_23317", "func_226277_ct_", "getX");
        registerMethodAlias(MappingFormat.SRG, "func_226277_ct_", "m_20185_");

        registerMethod("net/minecraft/world/entity/Entity", "getY", "method_23318", "func_226278_cu_", "getY");
        registerMethodAlias(MappingFormat.SRG, "func_226278_cu_", "m_20186_");

        registerMethod("net/minecraft/world/entity/Entity", "getZ", "method_23321", "func_226281_cx_", "getZ");
        registerMethodAlias(MappingFormat.SRG, "func_226281_cx_", "m_20189_");

        registerMethod("net/minecraft/world/entity/Entity", "setPos", "method_5814", "func_70107_b", "setPosition");
        registerMethodAlias(MappingFormat.SRG, "func_70107_b", "m_6034_");

        registerMethod("net/minecraft/world/entity/Entity", "getId", "method_5628", "func_145782_y", "getId");
        registerMethodAlias(MappingFormat.SRG, "func_145782_y", "m_19879_");

        registerMethod("net/minecraft/world/entity/Entity", "getUUID", "method_5667", "func_110124_au", "getUuid");
        registerMethodAlias(MappingFormat.SRG, "func_110124_au", "m_20148_");

        registerMethod("net/minecraft/world/entity/Entity", "remove", "method_5650", "func_70106_y", "discard");
        registerMethodAlias(MappingFormat.SRG, "func_70106_y", "m_146870_");

        registerMethod("net/minecraft/world/entity/Entity", "isAlive", "method_5805", "func_70089_S", "isAlive");
        registerMethodAlias(MappingFormat.SRG, "func_70089_S", "m_6084_");

        registerMethod("net/minecraft/world/entity/Entity", "tick", "method_5773", "func_70071_h_", "tick");
        registerMethodAlias(MappingFormat.SRG, "func_70071_h_", "m_8119_");

        // BlockEntity methods
        registerMethod("net/minecraft/world/level/block/entity/BlockEntity", "getBlockPos", "method_11016", "func_174877_v", "getPos");
        registerMethodAlias(MappingFormat.SRG, "func_174877_v", "m_58899_");

        registerMethod("net/minecraft/world/level/block/entity/BlockEntity", "getBlockState", "method_11010", "func_195044_w", "getCachedState");
        registerMethodAlias(MappingFormat.SRG, "func_195044_w", "m_58900_");

        registerMethod("net/minecraft/world/level/block/entity/BlockEntity", "setChanged", "method_11012", "func_70296_d", "markDirty");
        registerMethodAlias(MappingFormat.SRG, "func_70296_d", "m_6596_");

        registerMethod("net/minecraft/world/level/block/entity/BlockEntity", "getLevel", "method_10997", "func_145831_w", "getWorld");
        registerMethodAlias(MappingFormat.SRG, "func_145831_w", "m_58904_");

        // --- 3. Fields ---
        // ItemStack.EMPTY
        registerField("net/minecraft/world/item/ItemStack", "EMPTY", "field_8037", "field_190927_a", "EMPTY");
        registerFieldAlias(MappingFormat.SRG, "field_190927_a", "f_41583_");

        // Entity.level / Entity.world
        registerField("net/minecraft/world/entity/Entity", "level", "field_6002", "field_70170_p", "world");
        registerFieldAlias(MappingFormat.SRG, "field_70170_p", "f_19853_");

        // Level.isClientSide
        registerField("net/minecraft/world/level/Level", "isClientSide", "field_9236", "field_72995_K", "isClient");
        registerFieldAlias(MappingFormat.SRG, "field_72995_K", "f_46443_");
    }
}
