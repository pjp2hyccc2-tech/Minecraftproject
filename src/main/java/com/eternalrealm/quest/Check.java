package com.eternalrealm.quest;

import java.util.regex.Pattern;

/**
 * Argument validation for the quest definitions and events. Definitions are code constants, so a failure here is a
 * programming error and must fail loudly at startup and in tests.
 */
final class Check {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");

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

    static String id(String name, String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + ID.pattern() + ": " + value);
        }
        return value;
    }

    static String itemId(String name, String value) {
        if (value == null || !ITEM_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a namespaced item id like 'minecraft:bread': " + value);
        }
        return value;
    }
}
