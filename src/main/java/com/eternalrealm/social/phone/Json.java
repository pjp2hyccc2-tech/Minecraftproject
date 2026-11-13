package com.eternalrealm.social.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.UUID;

/** Lenient reading of the phone's saved documents: anything missing or of the wrong kind gives the fallback. */
final class Json {
    private Json() {
    }

    static JsonArray array(JsonObject json, String key) {
        JsonElement e = json == null ? null : json.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    static JsonObject object(JsonElement e) {
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    static JsonObject object(JsonObject json, String key) {
        return object(json == null ? null : json.get(key));
    }

    static String string(JsonObject json, String key, String fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
    }

    static long number(JsonObject json, String key, long fallback) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return e.getAsLong();
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static Optional<UUID> uuid(JsonObject json, String key) {
        return uuid(json.get(key));
    }

    static Optional<UUID> uuid(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(e.getAsString()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
