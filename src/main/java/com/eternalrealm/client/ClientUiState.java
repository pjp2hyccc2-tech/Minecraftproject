package com.eternalrealm.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Latest view data per channel pushed by the server for the journal pages ({@code UiDataPacket}). Client thread only.
 */
public final class ClientUiState {
    private static final Map<String, JsonObject> DATA = new HashMap<>();
    private static long revision;

    private ClientUiState() {
    }

    public static void accept(String channel, String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element != null && element.isJsonObject()) {
                DATA.put(channel, element.getAsJsonObject());
                revision++;
            }
        } catch (JsonParseException ignored) {
            // a broken document is simply not shown
        }
    }

    public static Optional<JsonObject> get(String channel) {
        return Optional.ofNullable(DATA.get(channel));
    }

    /** Increases with every update, so pages can rebuild their widgets when data changed. */
    public static long revision() {
        return revision;
    }

    public static void clear() {
        DATA.clear();
        revision++;
    }

    public static int intOr(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : fallback;
    }

    public static long longOr(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsLong() : fallback;
    }

    public static String stringOr(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }
}
