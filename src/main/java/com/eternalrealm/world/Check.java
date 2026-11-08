package com.eternalrealm.world;

import java.util.regex.Pattern;

/**
 * Argument validation for the world definitions. Definitions are code constants, so a failure here is a programming
 * error and must fail loudly at startup and in tests.
 */
final class Check {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    private Check() {
    }

    static int range(String name, int value, int min, int max) {
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
