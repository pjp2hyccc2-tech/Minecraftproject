package com.nihonisshu.tabi.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nihonisshu.tabi.TabiMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Static mod data read straight from the jar (same on client and server). */
public final class TabiData {
    private TabiData() {}

    private static final Map<String, JsonObject> CACHE = new HashMap<>();

    public static synchronized JsonObject read(String path) {
        return CACHE.computeIfAbsent(path, p -> {
            try (InputStream in = TabiMod.class.getResourceAsStream(p)) {
                if (in == null) {
                    TabiMod.LOGGER.warn("[tabi] missing resource {}", p);
                    return new JsonObject();
                }
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonElement e = JsonParser.parseReader(r);
                    return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
                }
            } catch (Exception ex) {
                TabiMod.LOGGER.error("[tabi] failed to read {}", p, ex);
                return new JsonObject();
            }
        });
    }

    public static JsonObject meta() {
        return read("/data/tabi/tabi_world/meta.json");
    }

    public static JsonObject dialogue(String npc) {
        return read("/data/tabi/dialogue/" + npc + ".json");
    }

    public static JsonObject shops() {
        return read("/data/tabi/shops.json");
    }

    public static JsonObject bubbles() {
        return read("/assets/tabi/tabi/bubbles.json");
    }

    public static JsonObject journal() {
        return read("/assets/tabi/tabi/journal.json");
    }

    public static String str(JsonObject o, String key, String def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    public static int integer(JsonObject o, String key, int def) {
        return o != null && o.has(key) ? o.get(key).getAsInt() : def;
    }

    public static double dbl(JsonObject o, String key, double def) {
        return o != null && o.has(key) ? o.get(key).getAsDouble() : def;
    }
}
