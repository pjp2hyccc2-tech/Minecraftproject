package com.eternalrealm.job;

/** Validation helpers of the job definitions (code constants: a failure is a programming error). */
final class Checks {
    private Checks() {
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

    static int frame(int frame) {
        return range("frame", frame, SkillEffect.ON_START, 63);
    }

    static String id(String name, String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException(name + " must match [a-z0-9_]{1,64}: " + value);
        }
        return value;
    }
}
