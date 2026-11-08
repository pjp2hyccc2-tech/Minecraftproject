package com.eternalrealm.combat;

/**
 * Argument validation shared by the combat definitions. Definitions are code constants or derived from validated
 * config values, so a failure here is a programming error and must fail loudly at startup / in tests.
 */
final class Check {
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

    static String id(String name, String value) {
        if (value == null || value.isEmpty() || value.length() > 64 || !value.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(name + " must match [a-z0-9_]{1,64}: " + value);
        }
        return value;
    }
}
