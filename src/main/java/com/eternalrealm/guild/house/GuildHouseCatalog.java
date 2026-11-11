package com.eternalrealm.guild.house;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The guild-house designs on offer, read from {@value #RESOURCE} (the single source of truth for the game and the
 * builders in {@code tools/estates/guild} and {@code tools/capital/districts/guild_gates.py}). Immutable; designs in
 * file order (tier by tier, cheapest first).
 */
public final class GuildHouseCatalog {
    public static final String RESOURCE = "/data/eternalrealm/guildhouse/designs.json";
    public static final int SCHEMA = 1;
    private static volatile GuildHouseCatalog standard;

    private final List<GuildHouseDesign> designs;
    private final Map<String, GuildHouseDesign> byId;

    public GuildHouseCatalog(List<GuildHouseDesign> designs) {
        Map<String, GuildHouseDesign> map = new LinkedHashMap<>();
        for (GuildHouseDesign design : designs) {
            if (map.put(design.id(), design) != null) {
                throw new IllegalArgumentException("duplicate design " + design.id());
            }
        }
        if (map.isEmpty()) {
            throw new IllegalArgumentException("no designs");
        }
        this.designs = List.copyOf(designs);
        this.byId = Collections.unmodifiableMap(map);
    }

    /**
     * Parses the catalogue document.
     *
     * @throws IllegalArgumentException if it is malformed or a design is invalid
     */
    public static GuildHouseCatalog parse(Reader reader) {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("schema") || root.get("schema").getAsInt() != SCHEMA) {
                throw new IllegalArgumentException("unsupported guild-house catalogue schema");
            }
            JsonArray array = root.getAsJsonArray("designs");
            List<GuildHouseDesign> designs = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject json = element.getAsJsonObject();
                JsonArray size = json.getAsJsonArray("size");
                designs.add(new GuildHouseDesign(json.get("id").getAsString(), json.get("tier").getAsInt(),
                        json.get("price").getAsLong(), color(json, "base"), color(json, "accent"),
                        size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt()));
            }
            return new GuildHouseCatalog(designs);
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException
                 | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("malformed guild-house catalogue: " + e.getMessage(), e);
        }
    }

    private static int color(JsonObject json, String key) {
        String text = json.get(key).getAsString();
        if (!text.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("bad colour '" + text + "'");
        }
        return Integer.parseInt(text.substring(1), 16);
    }

    /** The catalogue shipped with the mod (read once from the class path). */
    public static GuildHouseCatalog standard() {
        GuildHouseCatalog loaded = standard;
        if (loaded == null) {
            try (InputStream in = GuildHouseCatalog.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + RESOURCE);
                }
                loaded = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            standard = loaded;
        }
        return loaded;
    }

    public List<GuildHouseDesign> designs() {
        return designs;
    }

    public Optional<GuildHouseDesign> design(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public boolean contains(String id) {
        return id != null && byId.containsKey(id);
    }

    public List<GuildHouseDesign> tier(int tier) {
        List<GuildHouseDesign> result = new ArrayList<>();
        for (GuildHouseDesign design : designs) {
            if (design.tier() == tier) {
                result.add(design);
            }
        }
        return List.copyOf(result);
    }

    public int size() {
        return designs.size();
    }
}
