package com.eternalrealm.world;

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
import java.time.format.DateTimeParseException;

/**
 * Strict JSON reading shared by the codecs of the world and quest modules (same policy as
 * {@code persistence.file.JsonCodecs}): missing fields, wrong types and non-integral numbers are corruption; a schema
 * newer than supported is {@link IncompatibleDataException} (valid data, this build is too old) and must never be
 * quarantined or overwritten.
 */
public final class StrictJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StrictJson() {
    }

    /** Pretty-printed JSON with a trailing newline (file format of every data file). */
    public static String print(JsonElement element) {
        return GSON.toJson(element) + "\n";
    }

    public static JsonObject parseObject(String json) throws CorruptDataException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new CorruptDataException("Malformed JSON: " + e.getMessage(), e);
        }
        return asObject(root, "root element");
    }

    /**
     * Rejects schemas newer than {@code supported} (incompatible) and values below 1 (corrupt).
     *
     * @return the schema version found, for migrations
     */
    public static int checkSchema(JsonObject root, int supported, String kind)
            throws CorruptDataException, IncompatibleDataException {
        int schema = requireInt(root, "schema");
        if (schema > supported) {
            throw new IncompatibleDataException("The " + kind + " data uses schema " + schema
                    + " but this build only supports up to " + supported + ". Update the mod.");
        }
        if (schema < 1) {
            throw new CorruptDataException("Invalid " + kind + " schema version " + schema);
        }
        return schema;
    }

    public static JsonElement require(JsonObject root, String key) throws CorruptDataException {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            throw new CorruptDataException("Missing field '" + key + "'");
        }
        return element;
    }

    public static JsonObject asObject(JsonElement element, String what) throws CorruptDataException {
        if (element == null || !element.isJsonObject()) {
            throw new CorruptDataException(what + " is not a JSON object");
        }
        return element.getAsJsonObject();
    }

    public static JsonArray asArray(JsonElement element, String what) throws CorruptDataException {
        if (element == null || !element.isJsonArray()) {
            throw new CorruptDataException(what + " is not a JSON array");
        }
        return element.getAsJsonArray();
    }

    public static String asString(JsonElement element, String what) throws CorruptDataException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new CorruptDataException(what + " is not a string");
        }
        return element.getAsString();
    }

    public static long asLong(JsonElement element, String what) throws CorruptDataException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new CorruptDataException(what + " is not a number");
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new CorruptDataException(what + " is not an integer: " + element, e);
        }
    }

    public static int asInt(JsonElement element, String what) throws CorruptDataException {
        long value = asLong(element, what);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new CorruptDataException(what + " is out of int range: " + value);
        }
        return (int) value;
    }

    public static Instant asInstant(JsonElement element, String what) throws CorruptDataException {
        String text = asString(element, what);
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new CorruptDataException(what + " is not an ISO-8601 instant: " + text, e);
        }
    }

    public static JsonObject requireObject(JsonObject root, String key) throws CorruptDataException {
        return asObject(require(root, key), "Field '" + key + "'");
    }

    public static JsonArray requireArray(JsonObject root, String key) throws CorruptDataException {
        return asArray(require(root, key), "Field '" + key + "'");
    }

    public static String requireString(JsonObject root, String key) throws CorruptDataException {
        return asString(require(root, key), "Field '" + key + "'");
    }

    public static long requireLong(JsonObject root, String key) throws CorruptDataException {
        return asLong(require(root, key), "Field '" + key + "'");
    }

    public static int requireInt(JsonObject root, String key) throws CorruptDataException {
        return asInt(require(root, key), "Field '" + key + "'");
    }
}
