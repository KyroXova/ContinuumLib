package com.kyroxova.continuumlib.knowledgebase.epochs;

import com.kyroxova.bootstrapper.environment.MCVersion;

/**
 * Historical architectural eras of Minecraft modding APIs.
 */
public enum VersionEpoch {
    PRE_FLATTENING("1.7.9 - 1.12.2", "Pre-Flattening (Numeric IDs, GameRegistry, SRG)"),
    MODERN("1.13 - 1.19.4", "Modern (The Flattening, Mojmap, DeferredRegister)"),
    CONTEMPORARY("1.20 - 26.3+", "Contemporary (Material Removal, NeoForge Split, Creative Tab Events, Components)");

    private final String versionRange;
    private final String description;

    VersionEpoch(String versionRange, String description) {
        this.versionRange = versionRange;
        this.description = description;
    }

    public static VersionEpoch of(MCVersion version) {
        if (version.isBefore(MCVersion.V1_13)) {
            return PRE_FLATTENING;
        } else if (version.isBefore(MCVersion.V1_20_1)) {
            return MODERN;
        } else {
            return CONTEMPORARY;
        }
    }

    public String getVersionRange() {
        return versionRange;
    }

    public String getDescription() {
        return description;
    }
}
