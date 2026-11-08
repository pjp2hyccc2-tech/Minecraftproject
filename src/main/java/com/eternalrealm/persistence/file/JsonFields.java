package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.UUID;

/**
 * Strict field access for the season and ranking codecs ({@link SeasonJsonCodecs}), with the same rules as
 * {@link JsonCodecs}: a missing field, a wrong type or a non-integral number is corruption, and a schema newer than
 * supported is {@link IncompatibleDataException}. Value errors of {@code Instant.parse} / {@code UUID.fromString} /
 * record constructors are left to the caller, which reports them as corruption too.
 */
final class JsonFields {
    private JsonFields() {
    }

    static JsonObject parseObject(String json) throws CorruptDataException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new CorruptDataException("Malformed JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isJsonObject()) {
            throw new CorruptDataException("Root element is not a JSON object");
        }
        return root.getAsJsonObject();
    }

    static void checkSchema(JsonObject root, int supported, String kind)
            throws CorruptDataException, IncompatibleDataException {
        int schema = requireInt(root, "schema");
        if (schema > supported) {
            throw new IncompatibleDataException("The " + kind + " data uses schema " + schema
                    + " but this build only supports up to " + supported + ". Update the mod.");
        }
        if (schema < 1) {
            throw new CorruptDataException("Invalid " + kind + " schema version " + schema);
        }
        // Migrations from older schema versions go here when schema 2 is introduced.
    }

    static JsonElement require(JsonObject object, String key) throws CorruptDataException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new CorruptDataException("Missing field '" + key + "'");
        }
        return element;
    }

    static String requireString(JsonObject object, String key) throws CorruptDataException {
        JsonElement element = require(object, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new CorruptDataException("Field '" + key + "' is not a string");
        }
        return element.getAsString();
    }

    static JsonObject requireObject(JsonObject object, String key) throws CorruptDataException {
        return asObject(require(object, key), key);
    }

    static JsonArray requireArray(JsonObject object, String key) throws CorruptDataException {
        JsonElement element = require(object, key);
        if (!element.isJsonArray()) {
            throw new CorruptDataException("Field '" + key + "' is not an array");
        }
        return element.getAsJsonArray();
    }

    static JsonObject asObject(JsonElement element, String what) throws CorruptDataException {
        if (element == null || !element.isJsonObject()) {
            throw new CorruptDataException("'" + what + "' is not an object");
        }
        return element.getAsJsonObject();
    }

    static boolean requireBoolean(JsonObject object, String key) throws CorruptDataException {
        JsonElement element = require(object, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new CorruptDataException("Field '" + key + "' is not a boolean");
        }
        return element.getAsBoolean();
    }

    static long requireLong(JsonObject object, String key) throws CorruptDataException {
        JsonElement element = require(object, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new CorruptDataException("Field '" + key + "' is not a number");
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new CorruptDataException("Field '" + key + "' is not an integer: " + element, e);
        }
    }

    static int requireInt(JsonObject object, String key) throws CorruptDataException {
        long value = requireLong(object, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new CorruptDataException("Field '" + key + "' is out of int range: " + value);
        }
        return (int) value;
    }

    /** @throws java.time.format.DateTimeParseException for a malformed value (reported as corruption by the caller) */
    static Instant requireInstant(JsonObject object, String key) throws CorruptDataException {
        return Instant.parse(requireString(object, key));
    }

    /** An optional instant: absent or {@code null} reads as {@code null}. */
    static Instant optionalInstant(JsonObject object, String key) throws CorruptDataException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return requireInstant(object, key);
    }

    /** @throws IllegalArgumentException for a malformed value (reported as corruption by the caller) */
    static UUID requireUuid(JsonObject object, String key) throws CorruptDataException {
        return UUID.fromString(requireString(object, key));
    }
}
