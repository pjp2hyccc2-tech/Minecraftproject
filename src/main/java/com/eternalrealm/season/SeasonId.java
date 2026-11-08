package com.eternalrealm.season;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifier of a 14-day season (e.g. {@code s1}). Every piece of season-scoped data carries one so that
 * season data, permanent account data and archived records never mix (see docs/DATABASE_SCHEMA.md).
 *
 * <p>The format is deliberately restrictive because the value is used in file paths and database keys:
 * lower-case ASCII letters, digits, {@code _} and {@code -}; 1 to 32 characters; must start with a letter or digit.</p>
 */
public record SeasonId(String value) {
    public static final int MAX_LENGTH = 32;
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public SeasonId {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid season id '" + value + "' (expected " + FORMAT.pattern() + ")");
        }
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    public static SeasonId of(String value) {
        return new SeasonId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
