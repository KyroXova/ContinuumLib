package com.kyroxova.bootstrapper.environment;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust semantic version representation and comparator for Minecraft game releases.
 * Supports releases from 1.7.9 up to modern 26.3+ releases, including snapshots and pre-releases.
 */
public final class MCVersion implements Comparable<MCVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-_.]?(.*))?$");

    public static final MCVersion V1_7_9 = MCVersion.of("1.7.9");
    public static final MCVersion V1_7_10 = MCVersion.of("1.7.10");
    public static final MCVersion V1_12_2 = MCVersion.of("1.12.2");
    public static final MCVersion V1_13 = MCVersion.of("1.13");
    public static final MCVersion V1_16_5 = MCVersion.of("1.16.5");
    public static final MCVersion V1_18_2 = MCVersion.of("1.18.2");
    public static final MCVersion V1_19_2 = MCVersion.of("1.19.2");
    public static final MCVersion V1_19_4 = MCVersion.of("1.19.4");
    public static final MCVersion V1_20_1 = MCVersion.of("1.20.1");
    public static final MCVersion V1_20_4 = MCVersion.of("1.20.4");
    public static final MCVersion V1_20_6 = MCVersion.of("1.20.6");
    public static final MCVersion V1_21 = MCVersion.of("1.21");
    public static final MCVersion V1_21_1 = MCVersion.of("1.21.1");
    public static final MCVersion V26_3 = MCVersion.of("26.3");

    private final String raw;
    private final int major;
    private final int minor;
    private final int patch;
    private final String qualifier;

    private MCVersion(String raw, int major, int minor, int patch, String qualifier) {
        this.raw = raw;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier == null ? "" : qualifier;
    }

    public static MCVersion of(String versionString) {
        if (versionString == null || versionString.trim().isEmpty()) {
            throw new IllegalArgumentException("Version string cannot be null or empty");
        }
        String clean = versionString.trim();
        Matcher matcher = VERSION_PATTERN.matcher(clean);
        if (!matcher.matches()) {
            return new MCVersion(clean, 0, 0, 0, clean);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        String qualifier = matcher.group(4) != null ? matcher.group(4) : "";

        return new MCVersion(clean, major, minor, patch, qualifier);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public String getQualifier() {
        return qualifier;
    }

    public String getRaw() {
        return raw;
    }

    public boolean isBefore(MCVersion other) {
        return compareTo(other) < 0;
    }

    public boolean isAtLeast(MCVersion other) {
        return compareTo(other) >= 0;
    }

    public boolean isAfter(MCVersion other) {
        return compareTo(other) > 0;
    }

    public boolean isBetween(MCVersion startInclusive, MCVersion endInclusive) {
        return this.isAtLeast(startInclusive) && this.compareTo(endInclusive) <= 0;
    }

    @Override
    public int compareTo(MCVersion o) {
        if (this.major != o.major) {
            return Integer.compare(this.major, o.major);
        }
        if (this.minor != o.minor) {
            return Integer.compare(this.minor, o.minor);
        }
        if (this.patch != o.patch) {
            return Integer.compare(this.patch, o.patch);
        }
        return this.qualifier.compareTo(o.qualifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCVersion that)) return false;
        return major == that.major && minor == that.minor && patch == that.patch && Objects.equals(qualifier, that.qualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, qualifier);
    }

    @Override
    public String toString() {
        return raw;
    }
}
