package com.eternalrealm.dungeon;

import java.util.regex.Pattern;

/**
 * Argument validation shared by the dungeon definitions and layouts. Definitions are code constants, so a failure
 * here is a programming error and must fail loudly at startup / in tests.
 */
final class Check {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    private Check() {
    }

    static double range(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
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

    static String id(String name, String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + ID.pattern() + ": " + value);
        }
        return value;
    }
}
