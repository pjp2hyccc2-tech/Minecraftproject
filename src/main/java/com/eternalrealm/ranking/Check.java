package com.eternalrealm.ranking;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Argument validation shared by the ranking records. Values come from server code or from stored data that the
 * repositories decode (they report a failure here as corrupt data), so a failure must fail loudly.
 */
final class Check {
    /** Display names are informational (identity is always the id); same limit as the account's last known name. */
    static final int MAX_NAME_LENGTH = 64;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    /** Guild ids come from the guild module; this admits UUID strings and namespaced ids but nothing path-like. */
    private static final Pattern GUILD_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    private Check() {
    }

    static long nonNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0: " + value);
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

    static String guildId(String value) {
        if (value == null || !GUILD_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("guildId must match " + GUILD_ID.pattern() + ": " + value);
        }
        return value;
    }

    static String displayName(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(name + " must have 1 to " + MAX_NAME_LENGTH + " characters: '" + value + "'");
        }
        return value;
    }
}
