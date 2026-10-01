package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Universal Generic Pre-Flattening Compatibility Shim & Dynamic State Virtualization System.
 * Eliminates hardcoded vanilla ID tables in favor of generic, dynamic registration and state virtualization
 * for arbitrary mod-defined blocks, items, and properties across 1.7.9 -> 26.3+.
 */
public final class LegacyMetadataShim {

    private static final Logger LOGGER = Logger.getLogger(LegacyMetadataShim.class.getName());

    // Dynamic virtual ID allocation sequence for custom mod blocks and items
    private static final AtomicInteger NEXT_VIRTUAL_ID = new AtomicInteger(1000);

    // Dynamic block registration tables
    private static final Map<Integer, DynamicBlockEntry> BLOCKS_BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, DynamicBlockEntry> BLOCKS_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<Object, DynamicBlockEntry> BLOCKS_BY_INSTANCE = new ConcurrentHashMap<>();

    // Dynamic item registration tables
    private static final Map<Integer, Object> ITEMS_BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, Object> ITEMS_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<Object, Integer> ITEMS_TO_ID = new ConcurrentHashMap<>();

    // Bidirectional ID <-> Name mappings for generic legacy registry queries
    private static final Map<Integer, String> ID_TO_NAME = new ConcurrentHashMap<>();
    private static final Map<String, Integer> NAME_TO_ID = new ConcurrentHashMap<>();

    // Dynamic sub-variant names for (id, metadata) combinations
    // Key: ((long) id << 32) | (metadata & 0xFFFFFFFFL)
    private static final Map<Long, String> SUB_VARIANTS = new ConcurrentHashMap<>();

    // Dynamic extended state cache for 1.7.10 runtime coordinate-based state storage
    // Key: "levelHash:x,y,z"
    private static final Map<String, VirtualBlockState> EXTENDED_WORLD_STATES = new ConcurrentHashMap<>();

    static {
        // Only seed canonical Air as 0; all mod and vanilla blocks are registered dynamically
        ID_TO_NAME.put(0, "minecraft:air");
        NAME_TO_ID.put("minecraft:air", 0);
        NAME_TO_ID.put("air", 0);
    }

    private LegacyMetadataShim() {}

    // =========================================================================
    // 1. Generic Mod Registration & Virtual ID Tracking
    // =========================================================================

    /**
     * Dynamically registers a custom mod block and initializes its state virtualization container.
     */
    public static DynamicBlockEntry registerDynamicBlock(String modId, String name, Object block) {
        return registerDynamicBlock(modId, name, block, -1);
    }

    /**
     * Dynamically registers a custom mod block with an explicit or pre-assigned virtual ID.
     */
    public static DynamicBlockEntry registerDynamicBlock(String modId, String name, Object block, int explicitId) {
        String effectiveModId = (modId != null && !modId.trim().isEmpty()) ? modId.trim().toLowerCase(Locale.ROOT) : "continuumlib";
        String effectivePath = (name != null) ? name.trim().toLowerCase(Locale.ROOT) : "block_" + System.identityHashCode(block);
        if (effectivePath.contains(":")) {
            String[] parts = effectivePath.split(":", 2);
            effectiveModId = parts[0];
            effectivePath = parts[1];
        }

        String fullId = effectiveModId + ":" + effectivePath;
        int virtualId = explicitId > 0 ? explicitId : NAME_TO_ID.computeIfAbsent(fullId, k -> NEXT_VIRTUAL_ID.getAndIncrement());

        DynamicBlockEntry entry = new DynamicBlockEntry(effectiveModId, effectivePath, fullId, virtualId, block);
        BLOCKS_BY_ID.put(virtualId, entry);
        BLOCKS_BY_NAME.put(fullId, entry);
        BLOCKS_BY_NAME.put(effectivePath, entry);
        if (block != null) {
            BLOCKS_BY_INSTANCE.put(block, entry);
        }

        ID_TO_NAME.put(virtualId, fullId);
        NAME_TO_ID.put(fullId, virtualId);
        NAME_TO_ID.putIfAbsent(effectivePath, virtualId);

        return entry;
    }

    /**
     * Dynamically registers a custom mod block with pre-declared custom mod properties.
     */
    public static DynamicBlockEntry registerDynamicBlock(String modId, String name, Object block, Property<?>... properties) {
        DynamicBlockEntry entry = registerDynamicBlock(modId, name, block, -1);
        if (properties != null) {
            for (Property<?> prop : properties) {
                if (prop != null) {
                    entry.addProperty(prop);
                }
            }
        }
        return entry;
    }

    /**
     * Dynamically registers a custom mod item and assigns a virtual ID.
     */
    public static Object registerDynamicItem(String modId, String name, Object item) {
        return registerDynamicItem(modId, name, item, -1);
    }

    /**
     * Dynamically registers a custom mod item with explicit or auto-generated virtual ID.
     */
    public static Object registerDynamicItem(String modId, String name, Object item, int explicitId) {
        if (item == null) return null;

        String effectiveModId = (modId != null && !modId.trim().isEmpty()) ? modId.trim().toLowerCase(Locale.ROOT) : "continuumlib";
        String effectivePath = (name != null) ? name.trim().toLowerCase(Locale.ROOT) : "item_" + System.identityHashCode(item);
        if (effectivePath.contains(":")) {
            String[] parts = effectivePath.split(":", 2);
            effectiveModId = parts[0];
            effectivePath = parts[1];
        }

        String fullId = effectiveModId + ":" + effectivePath;
        int virtualId = explicitId > 0 ? explicitId : NEXT_VIRTUAL_ID.getAndIncrement();

        ITEMS_BY_ID.put(virtualId, item);
        ITEMS_BY_NAME.put(fullId, item);
        ITEMS_BY_NAME.put(effectivePath, item);
        ITEMS_TO_ID.put(item, virtualId);

        ID_TO_NAME.putIfAbsent(virtualId, fullId);
        NAME_TO_ID.putIfAbsent(fullId, virtualId);
        NAME_TO_ID.putIfAbsent(effectivePath, virtualId);

        return item;
    }

    /**
     * Registers a legacy block ID and name mapping dynamically.
     */
    public static void registerLegacyBlock(int id, String modernName) {
        registerLegacyBlock(id, modernName, null);
    }

    /**
     * Registers a legacy block ID, modern name, and optional block instance dynamically.
     */
    public static void registerLegacyBlock(int id, String modernName, Object block) {
        if (modernName == null) return;
        String cleanName = modernName.trim().toLowerCase(Locale.ROOT);
        ID_TO_NAME.put(id, cleanName);
        NAME_TO_ID.put(cleanName, id);

        String path = cleanName.contains(":") ? cleanName.split(":", 2)[1] : cleanName;
        String modId = cleanName.contains(":") ? cleanName.split(":", 2)[0] : "minecraft";
        NAME_TO_ID.putIfAbsent(path, id);

        if (block != null) {
            registerDynamicBlock(modId, path, block, id);
        }
    }

    /**
     * Dynamically registers a sub-variant name for a specific block ID and metadata.
     */
    public static void registerSubVariant(int id, int metadata, String flattenedName) {
        long key = (((long) id) << 32) | (metadata & 0xFFFFFFFFL);
        SUB_VARIANTS.put(key, flattenedName);
        DynamicBlockEntry entry = BLOCKS_BY_ID.get(id);
        if (entry != null) {
            entry.registerSubName(metadata, flattenedName);
        }
    }

    /**
     * Intercepts dynamic registration from {@link RegistryShim} and modern {@code DeferredRegister}.
     */
    public static void onDynamicRegister(Object registryType, String modId, String name, Object value) {
        if (value == null) return;
        String typeStr = (registryType != null) ? registryType.toString().toLowerCase(Locale.ROOT) : "";
        String valClass = value.getClass().getName().toLowerCase(Locale.ROOT);

        if (typeStr.contains("block") || valClass.contains("block")) {
            registerDynamicBlock(modId, name, value);
        } else if (typeStr.contains("item") || valClass.contains("item")) {
            registerDynamicItem(modId, name, value);
        } else {
            // General registry tracking
            registerDynamicBlock(modId, name, value);
        }
    }

    /**
     * Retrieves the dynamically registered block instance for a given mod and name.
     */
    public static Object getDynamicBlock(String modId, String name) {
        if (name == null) return null;
        String key = (modId != null ? modId + ":" : "") + name;
        DynamicBlockEntry entry = BLOCKS_BY_NAME.get(key.toLowerCase(Locale.ROOT));
        if (entry == null && modId == null) {
            entry = BLOCKS_BY_NAME.get(name.toLowerCase(Locale.ROOT));
        }
        return entry != null ? entry.getBlock() : null;
    }

    /**
     * Retrieves the dynamically registered item instance for a given mod and name.
     */
    public static Object getDynamicItem(String modId, String name) {
        if (name == null) return null;
        String key = (modId != null ? modId + ":" : "") + name;
        Object item = ITEMS_BY_NAME.get(key.toLowerCase(Locale.ROOT));
        if (item == null && modId == null) {
            item = ITEMS_BY_NAME.get(name.toLowerCase(Locale.ROOT));
        }
        return item;
    }

    // =========================================================================
    // 2. Mod-Defined Properties Hierarchy
    // =========================================================================

    /**
     * Universal property abstraction supporting arbitrary mod-defined types.
     */
    public interface Property<T extends Comparable<T>> {
        String getName();
        Class<T> getValueClass();
        Collection<T> getPossibleValues();
        String getName(T value);
        Optional<T> parseValue(String valueStr);
    }

    /**
     * Mod-defined boolean property (true/false).
     */
    public static class BooleanProperty implements Property<Boolean> {
        private final String name;
        private static final List<Boolean> VALUES = List.of(Boolean.TRUE, Boolean.FALSE);

        private BooleanProperty(String name) {
            this.name = Objects.requireNonNull(name, "Property name cannot be null");
        }

        public static BooleanProperty create(String name) {
            return new BooleanProperty(name);
        }

        @Override public String getName() { return name; }
        @Override public Class<Boolean> getValueClass() { return Boolean.class; }
        @Override public Collection<Boolean> getPossibleValues() { return VALUES; }
        @Override public String getName(Boolean value) { return String.valueOf(value); }

        @Override
        public Optional<Boolean> parseValue(String valueStr) {
            if ("true".equalsIgnoreCase(valueStr)) return Optional.of(Boolean.TRUE);
            if ("false".equalsIgnoreCase(valueStr)) return Optional.of(Boolean.FALSE);
            return Optional.empty();
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof BooleanProperty bp && name.equals(bp.name));
        }

        @Override public int hashCode() { return name.hashCode(); }
        @Override public String toString() { return "BooleanProperty{" + name + "}"; }
    }

    /**
     * Mod-defined integer property with bounded min/max values.
     */
    public static class IntegerProperty implements Property<Integer> {
        private final String name;
        private final int min;
        private final int max;
        private final List<Integer> values;

        private IntegerProperty(String name, int min, int max) {
            if (min >= max) throw new IllegalArgumentException("min must be < max");
            this.name = Objects.requireNonNull(name);
            this.min = min;
            this.max = max;
            List<Integer> list = new ArrayList<>(max - min + 1);
            for (int i = min; i <= max; i++) list.add(i);
            this.values = Collections.unmodifiableList(list);
        }

        public static IntegerProperty create(String name, int min, int max) {
            return new IntegerProperty(name, min, max);
        }

        @Override public String getName() { return name; }
        @Override public Class<Integer> getValueClass() { return Integer.class; }
        @Override public Collection<Integer> getPossibleValues() { return values; }
        @Override public String getName(Integer value) { return String.valueOf(value); }
        public int getMin() { return min; }
        public int getMax() { return max; }

        @Override
        public Optional<Integer> parseValue(String valueStr) {
            try {
                int val = Integer.parseInt(valueStr);
                return (val >= min && val <= max) ? Optional.of(val) : Optional.empty();
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof IntegerProperty ip && min == ip.min && max == ip.max && name.equals(ip.name));
        }

        @Override public int hashCode() { return Objects.hash(name, min, max); }
        @Override public String toString() { return "IntegerProperty{" + name + "[" + min + ".." + max + "]}"; }
    }

    /**
     * Mod-defined enum property supporting arbitrary enum types.
     */
    public static class EnumProperty<E extends Enum<E>> implements Property<E> {
        private final String name;
        private final Class<E> enumClass;
        private final List<E> values;
        private final Map<String, E> nameToValue = new HashMap<>();

        protected EnumProperty(String name, Class<E> enumClass, Collection<E> values) {
            this.name = Objects.requireNonNull(name);
            this.enumClass = Objects.requireNonNull(enumClass);
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
            for (E val : values) {
                nameToValue.put(val.name().toLowerCase(Locale.ROOT), val);
            }
        }

        public static <E extends Enum<E>> EnumProperty<E> create(String name, Class<E> enumClass) {
            return new EnumProperty<>(name, enumClass, Arrays.asList(enumClass.getEnumConstants()));
        }

        public static <E extends Enum<E>> EnumProperty<E> create(String name, Class<E> enumClass, Collection<E> values) {
            return new EnumProperty<>(name, enumClass, values);
        }

        @SafeVarargs
        public static <E extends Enum<E>> EnumProperty<E> create(String name, Class<E> enumClass, E... values) {
            return new EnumProperty<>(name, enumClass, Arrays.asList(values));
        }

        @Override public String getName() { return name; }
        @Override public Class<E> getValueClass() { return enumClass; }
        @Override public Collection<E> getPossibleValues() { return values; }
        @Override public String getName(E value) { return value.name().toLowerCase(Locale.ROOT); }

        @Override
        public Optional<E> parseValue(String valueStr) {
            if (valueStr == null) return Optional.empty();
            return Optional.ofNullable(nameToValue.get(valueStr.toLowerCase(Locale.ROOT)));
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof EnumProperty<?> ep && name.equals(ep.name) && enumClass.equals(ep.enumClass));
        }

        @Override public int hashCode() { return Objects.hash(name, enumClass); }
        @Override public String toString() { return "EnumProperty{" + name + ", " + enumClass.getSimpleName() + "}"; }
    }

    /**
     * Mod-defined direction property supporting all 6 Minecraft facings.
     */
    public static class DirectionProperty extends EnumProperty<DirectionProperty.Direction> {

        public enum Direction {
            DOWN("down", 0, 1, 0, -1, 0),
            UP("up", 1, 0, 0, 1, 0),
            NORTH("north", 2, 3, 0, 0, -1),
            SOUTH("south", 3, 2, 0, 0, 1),
            WEST("west", 4, 5, -1, 0, 0),
            EAST("east", 5, 4, 1, 0, 0);

            private final String serializedName;
            private final int data3d;
            private final int oppositeIndex;
            private final int stepX, stepY, stepZ;

            Direction(String name, int data3d, int oppositeIndex, int stepX, int stepY, int stepZ) {
                this.serializedName = name;
                this.data3d = data3d;
                this.oppositeIndex = oppositeIndex;
                this.stepX = stepX;
                this.stepY = stepY;
                this.stepZ = stepZ;
            }

            public int get3DDataValue() { return data3d; }
            public int get2DDataValue() {
                return switch (this) {
                    case SOUTH -> 0;
                    case WEST -> 1;
                    case NORTH -> 2;
                    case EAST -> 3;
                    default -> -1;
                };
            }
            public Direction getOpposite() { return values()[oppositeIndex]; }
            public int getStepX() { return stepX; }
            public int getStepY() { return stepY; }
            public int getStepZ() { return stepZ; }

            public static Direction from3DDataValue(int val) {
                return values()[Math.abs(val % 6)];
            }

            public static Direction from2DDataValue(int val) {
                return switch (Math.abs(val % 4)) {
                    case 0 -> SOUTH;
                    case 1 -> WEST;
                    case 2 -> NORTH;
                    case 3 -> EAST;
                    default -> NORTH;
                };
            }

            public static Direction byName(String name) {
                if (name == null) return NORTH;
                for (Direction d : values()) {
                    if (d.serializedName.equalsIgnoreCase(name) || d.name().equalsIgnoreCase(name)) {
                        return d;
                    }
                }
                return NORTH;
            }
        }

        private DirectionProperty(String name, Collection<Direction> values) {
            super(name, Direction.class, values);
        }

        public static DirectionProperty create(String name) {
            return new DirectionProperty(name, Arrays.asList(Direction.values()));
        }

        public static DirectionProperty create(String name, Direction... values) {
            return new DirectionProperty(name, Arrays.asList(values));
        }

        public static DirectionProperty create(String name, Collection<Direction> values) {
            return new DirectionProperty(name, values);
        }
    }

    // =========================================================================
    // 3. VirtualBlockState: Universal Property Container
    // =========================================================================

    /**
     * Virtual BlockState container representing arbitrary mod states across versions.
     */
    public static class VirtualBlockState {
        private final Object block;
        private final Map<Property<?>, Comparable<?>> propertyValues;
        private final Map<String, Object> namedValues;
        private final int metadata;
        private final DynamicBlockEntry ownerEntry;

        public VirtualBlockState(Object block, int metadata) {
            this(block, Collections.emptyMap(), Collections.emptyMap(), metadata, null);
        }

        public VirtualBlockState(Object block, int metadata, Map<String, Object> properties) {
            this(block, Collections.emptyMap(), properties, metadata, null);
        }

        public VirtualBlockState(Object block,
                                 Map<Property<?>, Comparable<?>> propertyValues,
                                 Map<String, Object> namedValues,
                                 int metadata,
                                 DynamicBlockEntry ownerEntry) {
            this.block = block;
            this.propertyValues = Map.copyOf(propertyValues);
            Map<String, Object> combinedNamed = new HashMap<>(namedValues);
            // Ensure typed property values are visible via string name lookup
            for (Map.Entry<Property<?>, Comparable<?>> e : propertyValues.entrySet()) {
                combinedNamed.putIfAbsent(e.getKey().getName(), e.getValue());
            }
            // Ensure synthetic metadata property is always accessible
            combinedNamed.putIfAbsent("meta", metadata & 0xF);
            combinedNamed.putIfAbsent("metadata", metadata & 0xF);
            this.namedValues = Collections.unmodifiableMap(combinedNamed);
            this.metadata = metadata;
            this.ownerEntry = (ownerEntry != null) ? ownerEntry : (block != null ? BLOCKS_BY_INSTANCE.get(block) : null);
        }

        public Object getBlock() { return block; }
        public int getMetadata() { return metadata & 0xF; }
        public int getExtendedMetadata() { return metadata; }
        public int toMetadata() { return getMetadata(); }

        public DynamicBlockEntry getOwnerEntry() { return ownerEntry; }

        @SuppressWarnings("unchecked")
        public <T extends Comparable<T>> T getValue(Property<T> property) {
            if (property == null) return null;
            Comparable<?> val = propertyValues.get(property);
            if (val != null && property.getValueClass().isInstance(val)) {
                return (T) val;
            }
            Object namedVal = namedValues.get(property.getName());
            if (namedVal != null) {
                if (property.getValueClass().isInstance(namedVal)) {
                    return (T) namedVal;
                }
                Optional<T> parsed = property.parseValue(namedVal.toString());
                if (parsed.isPresent()) return parsed.get();
            }
            return property.getPossibleValues().iterator().next();
        }

        public Object getValue(String name) {
            return namedValues.get(name);
        }

        @SuppressWarnings("unchecked")
        public <T extends Comparable<T>> T getValue(String name, Class<T> type) {
            Object val = namedValues.get(name);
            if (type.isInstance(val)) {
                return (T) val;
            }
            return null;
        }

        public Object getValue(Object property) {
            if (property == null) return null;
            if (property instanceof Property<?> p) {
                return getValue(p);
            }
            return getValue(getPropertyName(property));
        }

        public <T extends Comparable<T>, V extends T> VirtualBlockState setValue(Property<T> property, V value) {
            if (property == null || value == null) return this;
            Map<Property<?>, Comparable<?>> newProps = new HashMap<>(this.propertyValues);
            newProps.put(property, value);

            Map<String, Object> newNamed = new HashMap<>(this.namedValues);
            newNamed.put(property.getName(), value);

            if (ownerEntry != null) {
                return ownerEntry.getOrCreateState(newProps, newNamed);
            }

            int computedMeta = calculateMetadata(newProps, newNamed);
            return new VirtualBlockState(block, newProps, newNamed, computedMeta, ownerEntry);
        }

        public VirtualBlockState setValue(String name, Object value) {
            if (name == null || value == null) return this;
            if (ownerEntry != null) {
                Property<?> prop = ownerEntry.getProperty(name);
                if (prop != null) {
                    return setValueTypedHelper(prop, value);
                }
            }

            Map<Property<?>, Comparable<?>> newProps = new HashMap<>(this.propertyValues);
            Map<String, Object> newNamed = new HashMap<>(this.namedValues);
            newNamed.put(name, value);

            int computedMeta = calculateMetadata(newProps, newNamed);
            return new VirtualBlockState(block, newProps, newNamed, computedMeta, ownerEntry);
        }

        @SuppressWarnings("unchecked")
        private <T extends Comparable<T>> VirtualBlockState setValueTypedHelper(Property<T> prop, Object value) {
            if (prop.getValueClass().isInstance(value)) {
                return setValue(prop, (T) value);
            }
            Optional<T> parsed = prop.parseValue(value.toString());
            return parsed.map(t -> setValue(prop, t)).orElse(this);
        }

        public VirtualBlockState setValue(Object property, Object value) {
            if (property == null || value == null) return this;
            if (property instanceof Property<?> p) {
                return setValueTypedHelper(p, value);
            }
            return setValue(getPropertyName(property), value);
        }

        public <T extends Comparable<T>> VirtualBlockState cycle(Property<T> property) {
            if (property == null) return this;
            List<T> possible = new ArrayList<>(property.getPossibleValues());
            T current = getValue(property);
            int idx = possible.indexOf(current);
            T next = possible.get((idx + 1) % possible.size());
            return setValue(property, next);
        }

        public boolean hasProperty(Property<?> property) {
            return property != null && (propertyValues.containsKey(property) || namedValues.containsKey(property.getName()));
        }

        public boolean hasProperty(String name) {
            return name != null && namedValues.containsKey(name);
        }

        public boolean hasProperty(Object property) {
            if (property == null) return false;
            if (property instanceof Property<?> p) return hasProperty(p);
            return hasProperty(getPropertyName(property));
        }

        public Collection<Property<?>> getProperties() {
            return propertyValues.keySet();
        }

        public Map<Property<?>, Comparable<?>> getValues() {
            return Collections.unmodifiableMap(propertyValues);
        }

        public Map<String, Object> getPropertiesMap() {
            return namedValues;
        }

        public boolean is(Object otherBlock) {
            return Objects.equals(this.block, otherBlock);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VirtualBlockState that)) return false;
            return metadata == that.metadata &&
                    Objects.equals(block, that.block) &&
                    Objects.equals(namedValues, that.namedValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(block, metadata, namedValues);
        }

        @Override
        public String toString() {
            return "VirtualBlockState{block=" + (block != null ? block : "null") +
                    ", meta=" + metadata + ", properties=" + namedValues + "}";
        }
    }

    // =========================================================================
    // 4. DynamicBlockEntry: Mod Block Virtualization Definition
    // =========================================================================

    /**
     * Holds the dynamic registration metadata, properties, and state table for a mod block.
     */
    public static class DynamicBlockEntry {
        private final String modId;
        private final String name;
        private final String fullId;
        private final int virtualId;
        private final Object block;

        private final List<Property<?>> properties = new ArrayList<>();
        private final Map<String, Property<?>> propertyByName = new ConcurrentHashMap<>();

        // Dynamic State Table (metadata <-> VirtualBlockState)
        private final Map<Integer, VirtualBlockState> metaToState = new ConcurrentHashMap<>();
        private final Map<String, Integer> stateKeyToMeta = new ConcurrentHashMap<>();
        private final AtomicInteger nextExtendedMeta = new AtomicInteger(16);
        private final Map<Integer, String> metaToSubName = new ConcurrentHashMap<>();

        private volatile VirtualBlockState defaultState;

        public DynamicBlockEntry(String modId, String name, String fullId, int virtualId, Object block) {
            this.modId = modId;
            this.name = name;
            this.fullId = fullId;
            this.virtualId = virtualId;
            this.block = block;

            // Default synthetic metadata property (0..15)
            Property<Integer> metaProp = IntegerProperty.create("meta", 0, 15);
            addProperty(metaProp);

            this.defaultState = new VirtualBlockState(block, Map.of(metaProp, 0), Map.of("meta", 0), 0, this);
            this.metaToState.put(0, defaultState);
            this.stateKeyToMeta.put("meta=0", 0);
        }

        public String getModId() { return modId; }
        public String getName() { return name; }
        public String getFullId() { return fullId; }
        public int getVirtualId() { return virtualId; }
        public Object getBlock() { return block; }
        public VirtualBlockState getDefaultState() { return defaultState; }

        public synchronized void addProperty(Property<?> property) {
            if (property == null) return;
            if (!propertyByName.containsKey(property.getName())) {
                properties.add(property);
                propertyByName.put(property.getName(), property);
            }
        }

        public Property<?> getProperty(String propName) {
            return propertyByName.get(propName);
        }

        public List<Property<?>> getProperties() {
            return Collections.unmodifiableList(properties);
        }

        public void registerSubName(int metadata, String subName) {
            metaToSubName.put(metadata, subName);
        }

        public String getFlattenedName(int metadata) {
            String sub = metaToSubName.get(metadata);
            if (sub != null) return sub;
            if (metadata == 0) return fullId;
            return fullId + "_" + metadata;
        }

        public VirtualBlockState getState(int metadata) {
            int effectiveMeta = metadata & 0xF;
            return metaToState.computeIfAbsent(effectiveMeta, m -> {
                Map<Property<?>, Comparable<?>> pValues = new HashMap<>();
                Map<String, Object> nValues = new HashMap<>();
                nValues.put("meta", m);

                // Decode known bit patterns into defined properties
                decodeBitsIntoProperties(m, pValues, nValues);

                return new VirtualBlockState(block, pValues, nValues, m, this);
            });
        }

        public VirtualBlockState getOrCreateState(Map<Property<?>, Comparable<?>> pValues, Map<String, Object> nValues) {
            String key = buildStateKey(nValues);
            Integer existingMeta = stateKeyToMeta.get(key);
            if (existingMeta != null) {
                return metaToState.computeIfAbsent(existingMeta, m ->
                        new VirtualBlockState(block, pValues, nValues, m, this));
            }

            // Calculate metadata or allocate from extended table
            int meta = calculateMetadata(pValues, nValues);
            if (meta < 16 && !metaToState.containsKey(meta)) {
                // Fits in 4-bit nibble
                stateKeyToMeta.put(key, meta);
                VirtualBlockState newState = new VirtualBlockState(block, pValues, nValues, meta, this);
                metaToState.put(meta, newState);
                return newState;
            }

            // Extended dynamic state
            int extMeta = nextExtendedMeta.getAndIncrement();
            stateKeyToMeta.put(key, extMeta);
            VirtualBlockState newState = new VirtualBlockState(block, pValues, nValues, extMeta, this);
            metaToState.put(extMeta, newState);
            return newState;
        }

        private void decodeBitsIntoProperties(int meta, Map<Property<?>, Comparable<?>> pVals, Map<String, Object> nVals) {
            for (Property<?> p : properties) {
                if ("meta".equals(p.getName())) {
                    pVals.put(p, meta);
                    continue;
                }
                if (p instanceof DirectionProperty dp) {
                    DirectionProperty.Direction dir = DirectionProperty.Direction.from3DDataValue(meta & 7);
                    pVals.put(dp, dir);
                    nVals.put(dp.getName(), dir);
                } else if (p instanceof BooleanProperty bp) {
                    boolean val = (meta & 8) != 0;
                    pVals.put(bp, val);
                    nVals.put(bp.getName(), val);
                }
            }
        }

        private String buildStateKey(Map<String, Object> values) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (!"metadata".equals(e.getKey())) {
                    entries.add(e.getKey() + "=" + e.getValue());
                }
            }
            Collections.sort(entries);
            return String.join(",", entries);
        }
    }

    // =========================================================================
    // 5. Dynamic Metadata & BlockState Conversion Polyfills
    // =========================================================================

    /**
     * Converts a modern BlockState or VirtualBlockState into a 4-bit numeric metadata value (0..15).
     */
    public static int toMetadata(Object blockState) {
        return toMetadata(null, blockState);
    }

    /**
     * Converts a modern BlockState into a 4-bit numeric metadata value using the Block instance if available.
     */
    public static int toMetadata(Object block, Object blockState) {
        if (blockState == null) return 0;

        if (blockState instanceof VirtualBlockState virtualState) {
            return virtualState.getMetadata();
        }

        // 1. Try 1.8 - 1.12.2 getMetaFromState(IBlockState) on block
        if (block != null) {
            try {
                for (Method m : block.getClass().getMethods()) {
                    if ("getMetaFromState".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        Object res = m.invoke(block, blockState);
                        if (res instanceof Number num) {
                            return num.intValue() & 0xF;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Try getBlock() on state and check getMetaFromState
        try {
            Method getBlockMethod = blockState.getClass().getMethod("getBlock");
            Object stateBlock = getBlockMethod.invoke(blockState);
            if (stateBlock != null && stateBlock != block) {
                for (Method m : stateBlock.getClass().getMethods()) {
                    if ("getMetaFromState".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        Object res = m.invoke(stateBlock, blockState);
                        if (res instanceof Number num) {
                            return num.intValue() & 0xF;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Modern 1.13+ BlockState: extract properties map via getValues()
        try {
            Method getValuesMethod = blockState.getClass().getMethod("getValues");
            Object valuesMap = getValuesMethod.invoke(blockState);
            if (valuesMap instanceof Map<?, ?> map) {
                return extractMetaFromProperties(map);
            }
        } catch (Throwable ignored) {}

        return 0;
    }

    public static int getMetaFromState(Object blockState) {
        return toMetadata(blockState);
    }

    public static int getMetaFromState(Object block, Object blockState) {
        return toMetadata(block, blockState);
    }

    /**
     * Converts a Block and 4-bit numeric metadata (0..15) into a modern {@code BlockState}
     * or a {@link VirtualBlockState} adapter.
     */
    public static Object toBlockState(Object block, int metadata) {
        if (block == null) return null;
        int meta = metadata & 0xF;

        // 1. Check dynamic block registration
        DynamicBlockEntry entry = BLOCKS_BY_INSTANCE.get(block);
        if (entry != null) {
            return entry.getState(meta);
        }

        // 2. Try 1.8 - 1.12.2 getStateFromMeta(int) on block
        try {
            for (Method m : block.getClass().getMethods()) {
                if ("getStateFromMeta".equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                    m.setAccessible(true);
                    return m.invoke(block, meta);
                }
            }
        } catch (Throwable ignored) {}

        // 3. Modern 1.13+: Start with defaultBlockState() and apply property values
        try {
            Method defStateMethod = block.getClass().getMethod("defaultBlockState");
            Object state = defStateMethod.invoke(block);
            if (state != null) {
                return applyMetadataToModernState(state, meta);
            }
        } catch (Throwable ignored) {}

        // 4. Generic Fallback: Virtual BlockState adapter with synthetic properties
        return createVirtualState(block, meta);
    }

    public static Object getStateFromMeta(Object block, int metadata) {
        return toBlockState(block, metadata);
    }

    // =========================================================================
    // 6. Generic Block and Item ID / Name Resolution
    // =========================================================================

    /**
     * Gets the virtual or legacy numeric Block ID from a Block or BlockState object.
     */
    public static int getIdFromBlock(Object blockOrState) {
        if (blockOrState == null) return 0;

        Object block = blockOrState;
        if (blockOrState instanceof VirtualBlockState virtualState) {
            block = virtualState.getBlock();
            if (virtualState.getOwnerEntry() != null) {
                return virtualState.getOwnerEntry().getVirtualId();
            }
        } else {
            try {
                Method getBlock = blockOrState.getClass().getMethod("getBlock");
                Object b = getBlock.invoke(blockOrState);
                if (b != null) block = b;
            } catch (Throwable ignored) {}
        }

        // 1. Check dynamic registration table
        DynamicBlockEntry entry = BLOCKS_BY_INSTANCE.get(block);
        if (entry != null) {
            return entry.getVirtualId();
        }

        // 2. Try legacy 1.7.10 / 1.12.2 Block.getIdFromBlock(Block)
        try {
            Method getId = block.getClass().getMethod("getIdFromBlock", block.getClass());
            Object res = getId.invoke(null, block);
            if (res instanceof Number num && num.intValue() >= 0) return num.intValue();
        } catch (Throwable ignored) {}

        // 3. Try modern BuiltInRegistries.BLOCK.getId(block) or Registry.BLOCK.getId(block)
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field blockRegField = registriesClass.getField("BLOCK");
            Object blockRegistry = blockRegField.get(null);
            Method getIdMethod = blockRegistry.getClass().getMethod("getId", Object.class);
            Object res = getIdMethod.invoke(blockRegistry, block);
            if (res instanceof Number num && num.intValue() >= 0) {
                return num.intValue();
            }
        } catch (Throwable ignored) {}

        // 4. Check name mapping
        String blockString = block.toString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : NAME_TO_ID.entrySet()) {
            if (blockString.contains(e.getKey())) {
                return e.getValue();
            }
        }

        // 5. Dynamically assign a virtual ID for any unknown block
        int newId = NEXT_VIRTUAL_ID.getAndIncrement();
        registerDynamicBlock("dynamically_registered", "block_" + newId, block, newId);
        return newId;
    }

    public static int getBlockId(Object block) {
        return getIdFromBlock(block);
    }

    /**
     * Gets a Block by legacy or dynamic virtual ID.
     */
    public static Object getBlockById(int id) {
        if (id == 0) {
            DynamicBlockEntry airEntry = BLOCKS_BY_ID.get(0);
            if (airEntry != null) return airEntry.getBlock();
        }

        // 1. Check dynamic block registrations
        DynamicBlockEntry entry = BLOCKS_BY_ID.get(id);
        if (entry != null && entry.getBlock() != null) {
            return entry.getBlock();
        }

        // 2. Try legacy Block.getBlockById(int)
        try {
            Class<?> blockClass = Class.forName("net.minecraft.block.Block");
            Method getById = blockClass.getMethod("getBlockById", int.class);
            return getById.invoke(null, id);
        } catch (Throwable ignored) {}

        // 3. Try modern BuiltInRegistries.BLOCK.byId(id)
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field blockRegField = registriesClass.getField("BLOCK");
            Object blockRegistry = blockRegField.get(null);
            Method byIdMethod = blockRegistry.getClass().getMethod("byId", int.class);
            Object res = byIdMethod.invoke(blockRegistry, id);
            if (res != null) return res;
        } catch (Throwable ignored) {}

        // 4. Try modern lookup via registered name
        String modernName = ID_TO_NAME.get(id);
        if (modernName != null) {
            try {
                Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                Field blockRegField = registriesClass.getField("BLOCK");
                Object blockRegistry = blockRegField.get(null);
                Object idObj = ResourceLocationShim.create(modernName);
                Method getMethod = blockRegistry.getClass().getMethod("get", idObj.getClass());
                return getMethod.invoke(blockRegistry, idObj);
            } catch (Throwable ignored) {}
        }

        return null;
    }

    /**
     * Gets the virtual or legacy numeric Item ID from an Item or ItemStack object.
     */
    public static int getIdFromItem(Object itemOrStack) {
        if (itemOrStack == null) return 0;
        Object item = itemOrStack;

        try {
            Method getItem = itemOrStack.getClass().getMethod("getItem");
            Object it = getItem.invoke(itemOrStack);
            if (it != null) item = it;
        } catch (Throwable ignored) {}

        Integer id = ITEMS_TO_ID.get(item);
        if (id != null) return id;

        // Try modern BuiltInRegistries.ITEM.getId(item)
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field itemRegField = registriesClass.getField("ITEM");
            Object itemRegistry = itemRegField.get(null);
            Method getIdMethod = itemRegistry.getClass().getMethod("getId", Object.class);
            Object res = getIdMethod.invoke(itemRegistry, item);
            if (res instanceof Number num && num.intValue() >= 0) return num.intValue();
        } catch (Throwable ignored) {}

        // Dynamic item registration
        int newId = NEXT_VIRTUAL_ID.getAndIncrement();
        registerDynamicItem("dynamically_registered", "item_" + newId, item, newId);
        return newId;
    }

    /**
     * Gets an Item by virtual or legacy ID.
     */
    public static Object getItemById(int id) {
        Object item = ITEMS_BY_ID.get(id);
        if (item != null) return item;

        // Try modern BuiltInRegistries.ITEM.byId(id)
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field itemRegField = registriesClass.getField("ITEM");
            Object itemRegistry = itemRegField.get(null);
            Method byIdMethod = itemRegistry.getClass().getMethod("byId", int.class);
            return byIdMethod.invoke(itemRegistry, id);
        } catch (Throwable ignored) {}

        return null;
    }

    public static Object stateById(int id) {
        Object block = getBlockById(id);
        if (block == null) return null;
        return toBlockState(block, 0);
    }

    public static String getLegacyNameFromId(int id) {
        String name = ID_TO_NAME.get(id);
        if (name != null) return name;

        DynamicBlockEntry entry = BLOCKS_BY_ID.get(id);
        if (entry != null) return entry.getFullId();

        return (id == 0) ? "minecraft:air" : "continuumlib:virtual_block_" + id;
    }

    public static int getIdFromLegacyName(String name) {
        if (name == null) return 0;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        if ("minecraft:air".equals(clean) || "air".equals(clean)) return 0;

        Integer id = NAME_TO_ID.get(clean);
        if (id != null) return id;

        // Dynamically assign virtual ID for any new name
        int newId = NEXT_VIRTUAL_ID.getAndIncrement();
        registerLegacyBlock(newId, clean);
        return newId;
    }

    /**
     * Dynamically resolves the flattened block name for a given ID and 4-bit metadata.
     */
    public static String getFlattenedBlockName(int id, int metadata) {
        int meta = metadata & 0xF;
        if (id == 0) return "minecraft:air";

        // 1. Check registered sub-variants
        long subKey = (((long) id) << 32) | (meta & 0xFFFFFFFFL);
        String subVariant = SUB_VARIANTS.get(subKey);
        if (subVariant != null) return subVariant;

        // 2. Check dynamic block entry
        DynamicBlockEntry entry = BLOCKS_BY_ID.get(id);
        if (entry != null) {
            return entry.getFlattenedName(meta);
        }

        // 3. Fallback to base name + metadata
        String baseName = ID_TO_NAME.get(id);
        if (baseName != null) {
            return (meta == 0) ? baseName : baseName + "_" + meta;
        }

        return "continuumlib:virtual_block_" + id;
    }

    public static Object getFlattenedBlock(int id, int metadata) {
        String flattenedName = getFlattenedBlockName(id, metadata);
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Field blockRegField = registriesClass.getField("BLOCK");
            Object blockRegistry = blockRegField.get(null);
            Object idObj = ResourceLocationShim.create(flattenedName);
            Method getMethod = blockRegistry.getClass().getMethod("get", idObj.getClass());
            return getMethod.invoke(blockRegistry, idObj);
        } catch (Throwable ignored) {}

        return getBlockById(id);
    }

    public static Object getFlattenedBlock(Object block, int metadata) {
        int id = getIdFromBlock(block);
        return getFlattenedBlock(id, metadata);
    }

    // =========================================================================
    // 7. Pre-Flattening Coordinate Polyfills (World x, y, z)
    // =========================================================================

    public static Object getBlockAt(Object level, int x, int y, int z) {
        if (level == null) return null;

        // 1. Try 1.7.10 direct method: world.getBlock(x, y, z)
        try {
            Method m = level.getClass().getMethod("getBlock", int.class, int.class, int.class);
            return m.invoke(level, x, y, z);
        } catch (Throwable ignored) {}

        // 2. Modern: getBlockState(BlockPos) -> getBlock()
        try {
            Object pos = createBlockPos(x, y, z);
            if (pos != null) {
                Object state = WorldShim.getBlockState(level, pos);
                if (state != null) {
                    Method getBlock = state.getClass().getMethod("getBlock");
                    return getBlock.invoke(state);
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static int getMetadataAt(Object level, int x, int y, int z) {
        if (level == null) return 0;

        // 1. Try 1.7.10 direct method: world.getBlockMetadata(x, y, z)
        try {
            Method m = level.getClass().getMethod("getBlockMetadata", int.class, int.class, int.class);
            Object res = m.invoke(level, x, y, z);
            if (res instanceof Number num) return num.intValue();
        } catch (Throwable ignored) {}

        // 2. Check extended coordinate state table
        String coordKey = buildCoordKey(level, x, y, z);
        VirtualBlockState extState = EXTENDED_WORLD_STATES.get(coordKey);
        if (extState != null) {
            return extState.getMetadata();
        }

        // 3. Modern: getBlockState(BlockPos) -> toMetadata(state)
        try {
            Object pos = createBlockPos(x, y, z);
            if (pos != null) {
                Object state = WorldShim.getBlockState(level, pos);
                if (state != null) {
                    return toMetadata(state);
                }
            }
        } catch (Throwable ignored) {}

        return 0;
    }

    public static boolean setBlockAt(Object level, int x, int y, int z, Object block, int metadata, int flags) {
        if (level == null) return false;

        // Track extended metadata if > 15
        if (metadata >= 16) {
            String coordKey = buildCoordKey(level, x, y, z);
            EXTENDED_WORLD_STATES.put(coordKey, new VirtualBlockState(block, metadata));
        }

        // 1. Try 1.7.10 direct method: world.setBlock(x, y, z, block, metadata, flags)
        try {
            for (Method m : level.getClass().getMethods()) {
                if ("setBlock".equals(m.getName()) && m.getParameterCount() == 6) {
                    Class<?>[] ptypes = m.getParameterTypes();
                    if (ptypes[0] == int.class && ptypes[1] == int.class && ptypes[2] == int.class &&
                            ptypes[4] == int.class && ptypes[5] == int.class) {
                        m.setAccessible(true);
                        Object res = m.invoke(level, x, y, z, block, metadata & 0xF, flags);
                        if (res instanceof Boolean b) return b;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Modern: level.setBlock(BlockPos, BlockState, flags)
        try {
            Object pos = createBlockPos(x, y, z);
            Object state = toBlockState(block, metadata);
            if (pos != null && state != null) {
                for (Method m : level.getClass().getMethods()) {
                    if ("setBlock".equals(m.getName()) && m.getParameterCount() == 3) {
                        m.setAccessible(true);
                        Object res = m.invoke(level, pos, state, flags);
                        if (res instanceof Boolean b) return b;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean setMetadataAt(Object level, int x, int y, int z, int metadata, int flags) {
        if (level == null) return false;

        // 1. Try 1.7.10 direct method: world.setBlockMetadataWithNotify(x, y, z, metadata, flags)
        try {
            Method m = level.getClass().getMethod("setBlockMetadataWithNotify", int.class, int.class, int.class, int.class, int.class);
            Object res = m.invoke(level, x, y, z, metadata & 0xF, flags);
            if (res instanceof Boolean b) return b;
        } catch (Throwable ignored) {}

        // 2. Modern: fetch current block, adapt state with metadata, call setBlock
        Object currentBlock = getBlockAt(level, x, y, z);
        return setBlockAt(level, x, y, z, currentBlock, metadata, flags);
    }

    public static Object createBlockPos(int x, int y, int z) {
        try {
            Class<?> posClass = Class.forName("net.minecraft.core.BlockPos");
            Constructor<?> ctor = posClass.getConstructor(int.class, int.class, int.class);
            return ctor.newInstance(x, y, z);
        } catch (Throwable t1) {
            try {
                Class<?> posClass = Class.forName("net.minecraft.util.math.BlockPos");
                Constructor<?> ctor = posClass.getConstructor(int.class, int.class, int.class);
                return ctor.newInstance(x, y, z);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    // =========================================================================
    // 8. Virtual State Helpers & Modern Adaptation
    // =========================================================================

    public static VirtualBlockState createVirtualState(Object block, int metadata) {
        DynamicBlockEntry entry = BLOCKS_BY_INSTANCE.get(block);
        if (entry != null) {
            return entry.getState(metadata);
        }
        return new VirtualBlockState(block, metadata);
    }

    public static VirtualBlockState createVirtualState(Object block, int metadata, Map<String, Object> properties) {
        return new VirtualBlockState(block, metadata, properties);
    }

    private static int calculateMetadata(Map<Property<?>, Comparable<?>> pVals, Map<String, Object> nVals) {
        int meta = 0;

        // Check explicit meta property
        Object explicitMeta = nVals.get("meta");
        if (explicitMeta instanceof Number num) {
            return num.intValue() & 0xF;
        }

        // Generic Direction / Facing encoding
        for (Map.Entry<String, Object> entry : nVals.entrySet()) {
            String propName = entry.getKey().toLowerCase(Locale.ROOT);
            Object val = entry.getValue();

            if (("facing".equals(propName) || "direction".equals(propName)) && val instanceof DirectionProperty.Direction d) {
                meta |= (d.get3DDataValue() & 7);
            } else if ("axis".equals(propName)) {
                String axis = val.toString().toUpperCase(Locale.ROOT);
                if (axis.contains("X")) meta |= 4;
                else if (axis.contains("Z")) meta |= 8;
            } else if (("powered".equals(propName) || "lit".equals(propName) || "open".equals(propName)) && Boolean.TRUE.equals(val)) {
                meta |= 8;
            }
        }

        return meta & 0xF;
    }

    private static int extractMetaFromProperties(Map<?, ?> propertyMap) {
        int meta = 0;
        for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
            Object prop = entry.getKey();
            Object val = entry.getValue();
            if (prop == null || val == null) continue;

            String propName = getPropertyName(prop).toLowerCase(Locale.ROOT);

            // Synthetic or explicit meta
            if ("meta".equals(propName) && val instanceof Number n) {
                return n.intValue() & 0xF;
            }

            // Color / DyeColor
            if (propName.contains("color") && val instanceof Enum<?> e) {
                return e.ordinal() & 0xF;
            }

            // Axis
            if ("axis".equals(propName)) {
                String axisName = val.toString().toUpperCase(Locale.ROOT);
                if (axisName.contains("X")) meta |= 4;
                else if (axisName.contains("Z")) meta |= 8;
                return meta;
            }

            // Facing
            if ("facing".equals(propName) && val instanceof Enum<?> e) {
                String dirName = e.name().toUpperCase(Locale.ROOT);
                int dirCode = switch (dirName) {
                    case "DOWN" -> 0;
                    case "UP" -> 1;
                    case "NORTH" -> 2;
                    case "SOUTH" -> 3;
                    case "WEST" -> 4;
                    case "EAST" -> 5;
                    default -> 0;
                };
                meta |= (dirCode & 7);
            }

            // Booleans: open, powered, lit
            if (("powered".equals(propName) || "lit".equals(propName)) && Boolean.TRUE.equals(val)) {
                meta |= 8;
            }
            if ("open".equals(propName) && Boolean.TRUE.equals(val)) {
                meta |= 4;
            }
        }
        return meta & 0xF;
    }

    private static Object applyMetadataToModernState(Object state, int meta) {
        Object currentState = state;
        try {
            Method getValues = currentState.getClass().getMethod("getValues");
            Object valuesMap = getValues.invoke(currentState);
            if (!(valuesMap instanceof Map<?, ?> map)) return currentState;

            for (Object prop : map.keySet()) {
                String name = getPropertyName(prop).toLowerCase(Locale.ROOT);

                if ("facing".equals(name)) {
                    int facingBits = meta & 7;
                    String targetDir = switch (facingBits) {
                        case 0 -> "DOWN";
                        case 1 -> "UP";
                        case 2 -> "NORTH";
                        case 3 -> "SOUTH";
                        case 4 -> "WEST";
                        case 5 -> "EAST";
                        default -> "NORTH";
                    };
                    Object dirVal = findEnumConstant(prop, targetDir);
                    if (dirVal != null) {
                        currentState = setProperty(currentState, prop, dirVal);
                    }
                }

                if ("axis".equals(name)) {
                    int axisBits = meta & 12;
                    String targetAxis = (axisBits == 4) ? "X" : (axisBits == 8) ? "Z" : "Y";
                    Object axisVal = findEnumConstant(prop, targetAxis);
                    if (axisVal != null) {
                        currentState = setProperty(currentState, prop, axisVal);
                    }
                }

                if ("powered".equals(name)) {
                    currentState = setProperty(currentState, prop, (meta & 8) != 0);
                }
                if ("lit".equals(name)) {
                    currentState = setProperty(currentState, prop, (meta & 8) != 0);
                }
                if ("open".equals(name)) {
                    currentState = setProperty(currentState, prop, (meta & 4) != 0);
                }
            }
        } catch (Throwable ignored) {}

        return currentState;
    }

    private static String getPropertyName(Object prop) {
        try {
            Method getName = prop.getClass().getMethod("getName");
            return (String) getName.invoke(prop);
        } catch (Throwable ignored) {}
        return "";
    }

    private static Object findEnumConstant(Object prop, String targetName) {
        try {
            Method getPossibleValues = prop.getClass().getMethod("getPossibleValues");
            Object vals = getPossibleValues.invoke(prop);
            if (vals instanceof Collection<?> col) {
                for (Object item : col) {
                    if (item.toString().equalsIgnoreCase(targetName)) {
                        return item;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object setProperty(Object state, Object prop, Object val) {
        try {
            for (Method m : state.getClass().getMethods()) {
                if ("setValue".equals(m.getName()) && m.getParameterCount() == 2) {
                    return m.invoke(state, prop, val);
                }
            }
        } catch (Throwable ignored) {}
        return state;
    }

    private static String buildCoordKey(Object level, int x, int y, int z) {
        return System.identityHashCode(level) + ":" + x + "," + y + "," + z;
    }
}
