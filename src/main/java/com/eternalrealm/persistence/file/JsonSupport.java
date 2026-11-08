package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.UUID;

/**
 * Strict JSON reading shared by the guild and market codecs, with the same rules as {@link JsonCodecs}: missing
 * fields, wrong types and non-integral numbers are corruption; a schema newer than supported is incompatible (the data
 * is valid, this build is too old) and must never be quarantined.
 *
 * <p>Value-level problems ({@code UUID.fromString}, {@code Instant.parse}, record validation) surface as
 * {@link IllegalArgumentException} / {@link java.time.format.DateTimeParseException}; codecs convert them to
 * {@link CorruptDataException}.</p>
 */
final class JsonSupport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonSupport() {
    }

    static String toJson(JsonObject root) {
        return GSON.toJson(root) + "\n";
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

    static UUID requireUuid(JsonObject object, String key) throws CorruptDataException {
        return UUID.fromString(requireString(object, key));
    }

    static Instant requireInstant(JsonObject object, String key) throws CorruptDataException {
        return Instant.parse(requireString(object, key));
    }

    static long requireLong(JsonObject object, String key) throws CorruptDataException {
        return toLong(require(object, key), key);
    }

    static int requireInt(JsonObject object, String key) throws CorruptDataException {
        long value = requireLong(object, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new CorruptDataException("Field '" + key + "' is out of int range: " + value);
        }
        return (int) value;
    }

    static long toLong(JsonElement element, String key) throws CorruptDataException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new CorruptDataException("Field '" + key + "' is not a number");
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new CorruptDataException("Field '" + key + "' is not an integer: " + element, e);
        }
    }
}
