package com.kyroxova.continuumlib.knowledgebase.mappings;

import com.kyroxova.bootstrapper.config.TargetSpec;

import java.util.Locale;
import java.util.Objects;

/**
 * Supported Minecraft obfuscation and deobfuscation mapping formats.
 */
public enum MappingFormat {
    MOJMAP("mojmap", "Mojang Official Mappings", true),
    INTERMEDIARY("intermediary", "Fabric Intermediary Mappings", false),
    SRG("srg", "Searge / MCP / Forge SRG Mappings", false),
    YARN("yarn", "Fabric Yarn Mappings", true);

    private final String id;
    private final String displayName;
    private final boolean deobfuscated;

    MappingFormat(String id, String displayName, boolean deobfuscated) {
        this.id = id;
        this.displayName = displayName;
        this.deobfuscated = deobfuscated;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDeobfuscated() {
        return deobfuscated;
    }

    public boolean isNamed() {
        return deobfuscated;
    }

    /**
     * Resolves a MappingFormat from a string name or alias.
     * Defaults to MOJMAP if null, empty, or unrecognized.
     */
    public static MappingFormat fromString(String name) {
        return fromString(name, MOJMAP);
    }

    /**
     * Resolves a MappingFormat from a string name or alias with a specified fallback.
     */
    public static MappingFormat fromString(String name, MappingFormat defaultFormat) {
        if (name == null || name.trim().isEmpty()) {
            return defaultFormat;
        }
        String clean = name.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "mojmap", "mojang", "official", "moj" -> MOJMAP;
            case "intermediary", "fabric", "inter" -> INTERMEDIARY;
            case "srg", "searge", "forge", "mcp" -> SRG;
            case "yarn", "quilt" -> YARN;
            default -> {
                for (MappingFormat format : values()) {
                    if (format.name().equalsIgnoreCase(clean) || format.getId().equalsIgnoreCase(clean)) {
                        yield format;
                    }
                }
                yield defaultFormat;
            }
        };
    }

    /**
     * Resolves MappingFormat from a TargetSpec configuration.
     */
    public static MappingFormat fromTargetSpec(TargetSpec spec) {
        if (spec == null || spec.getMappings() == null) {
            return MOJMAP;
        }
        return fromString(spec.getMappings());
    }

    /**
     * Heuristically detects the MappingFormat from an identifier or signature.
     */
    public static MappingFormat detectFromIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return MOJMAP;
        }
        if (identifier.contains("class_") || identifier.contains("method_")
                || (identifier.contains("field_") && !identifier.matches(".*field_\\d+_[a-zA-Z0-9]+.*"))) {
            if (identifier.matches(".*class_\\d+.*")
                    || identifier.matches(".*method_\\d+.*")
                    || identifier.matches(".*field_\\d+($|[^_].*)")) {
                return INTERMEDIARY;
            }
        }
        if (identifier.matches(".*func_\\d+_[a-zA-Z0-9]+.*")
                || identifier.matches(".*field_\\d+_[a-zA-Z0-9]+.*")
                || identifier.matches(".*[mfp]_\\d+_.*")
                || identifier.contains("src/C_")
                || identifier.contains("src.C_")) {
            return SRG;
        }
        if (identifier.startsWith("net/minecraft/class_")) {
            return INTERMEDIARY;
        }
        return MOJMAP;
    }

    /**
     * Detects mapping format specifically from a class name.
     */
    public static MappingFormat detectFromClassName(String className) {
        if (className == null) return MOJMAP;
        if (className.contains("class_") || className.contains("/class_") || className.contains(".class_")) {
            return INTERMEDIARY;
        }
        if (className.contains("/src/C_") || className.contains(".src.C_")) {
            return SRG;
        }
        return MOJMAP;
    }

    /**
     * Detects mapping format specifically from a method or field name.
     */
    public static MappingFormat detectFromMemberName(String memberName) {
        if (memberName == null) return MOJMAP;
        if (memberName.startsWith("method_") || (memberName.startsWith("field_") && !memberName.matches("field_\\d+_.*"))) {
            return INTERMEDIARY;
        }
        if (memberName.startsWith("func_") || memberName.matches("field_\\d+_.*") || memberName.matches("[mf]_\\d+_.*")) {
            return SRG;
        }
        return MOJMAP;
    }
}
