package com.eternalrealm.monster;

import java.util.regex.Pattern;

/**
 * Argument validation for the monster definitions. Definitions are code constants, so a failure here is a programming
 * error and must fail loudly at startup and in tests.
 */
final class Check {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    /** Minecraft resource location of an item or entity type, always with its namespace. */
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");

    private Check() {
    }

    static int range(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
    }

    static long range(String name, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
    }

    static double range(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
    }

    /** A probability in {@code (0, 1]}: an entry that can never happen is a mistake. */
    static double chance(String name, double value) {
        if (!(value > 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(name + " must be in (0, 1]: " + value);
        }
        return value;
    }

    static String id(String name, String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + ID.pattern() + ": " + value);
        }
        return value;
    }

    static String resourceId(String name, String value) {
        if (value == null || !RESOURCE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a namespaced id like 'minecraft:stone': " + value);
        }
        return value;
    }
}
