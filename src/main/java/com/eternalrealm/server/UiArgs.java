package com.eternalrealm.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;
import java.util.regex.Pattern;

/** Strict readers for UI action arguments: anything a genuine client cannot send is an {@link InvalidArgumentException}. */
public final class UiArgs {
    private static final Pattern ID = Pattern.compile("[a-z0-9_:.\\-]{1,64}");

    private UiArgs() {
    }

    public static final class InvalidArgumentException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public InvalidArgumentException(String message) {
            super(message);
        }
    }

    public static String id(JsonObject args, String key) {
        String value = string(args, key, 64);
        if (!ID.matcher(value).matches()) {
            throw new InvalidArgumentException(key + " is not an id");
        }
        return value;
    }

    public static String string(JsonObject args, String key, int maxLength) {
        JsonElement element = args.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new InvalidArgumentException(key + " missing or not a string");
        }
        String value = element.getAsString();
        if (value.length() > maxLength) {
            throw new InvalidArgumentException(key + " too long");
        }
        return value;
    }

    public static long number(JsonObject args, String key, long min, long max) {
        JsonElement element = args.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new InvalidArgumentException(key + " missing or not a number");
        }
        long value;
        try {
            value = element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new InvalidArgumentException(key + " is not an integer");
        }
        if (value < min || value > max) {
            throw new InvalidArgumentException(key + " out of range");
        }
        return value;
    }

    public static boolean flag(JsonObject args, String key) {
        JsonElement element = args.get(key);
        if (element == null) {
            return false;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new InvalidArgumentException(key + " is not a boolean");
        }
        return element.getAsBoolean();
    }

    public static UUID uuid(JsonObject args, String key) {
        String value = string(args, key, 36);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentException(key + " is not a UUID");
        }
    }
}
