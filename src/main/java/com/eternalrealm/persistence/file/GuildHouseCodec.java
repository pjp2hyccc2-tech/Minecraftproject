package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.guild.house.BoardNote;
import com.eternalrealm.guild.house.GuildHouse;
import com.eternalrealm.guild.house.StorageLogEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JSON form of the guild houses' data ({@code guild_houses.json}, schema 1; docs/modules/guildhouse.md):
 * <pre>
 * { "schema": 1,
 *   "houses": [ { "guild": "&lt;uuid&gt;", "design": "great_tree", "since": "&lt;ISO-8601&gt;", "fund": 1200,
 *                 "motd": "...", "nextId": 4,
 *                 "notes": [ { "id": 3, "author": "&lt;uuid&gt;", "name": "Alice", "text": "...", "at": "..." } ],
 *                 "log": [ { "at": "...", "player": "&lt;uuid&gt;", "name": "Bob", "item": "minecraft:iron_ingot",
 *                            "count": 12, "deposit": true, "piece": "chest.2" } ] } ] }
 * </pre>
 * {@code design} and {@code since} are absent for a guild without a house (it may still have a fund or a board).
 */
public final class GuildHouseCodec {
    public static final int SCHEMA_VERSION = 1;

    private GuildHouseCodec() {
    }

    public static String encode(List<GuildHouse> houses) {
        JsonArray array = new JsonArray();
        for (GuildHouse house : houses) {
            JsonObject json = new JsonObject();
            json.addProperty("guild", house.guildId().toString());
            if (house.design() != null) {
                json.addProperty("design", house.design());
                json.addProperty("since", house.since().toString());
            }
            json.addProperty("fund", house.fund());
            json.addProperty("motd", house.motd());
            json.addProperty("nextId", house.nextId());
            JsonArray notes = new JsonArray();
            for (BoardNote note : house.notes()) {
                JsonObject n = new JsonObject();
                n.addProperty("id", note.id());
                n.addProperty("author", note.author().toString());
                n.addProperty("name", note.authorName());
                n.addProperty("text", note.text());
                n.addProperty("at", note.at().toString());
                notes.add(n);
            }
            json.add("notes", notes);
            JsonArray log = new JsonArray();
            for (StorageLogEntry entry : house.log()) {
                JsonObject e = new JsonObject();
                e.addProperty("at", entry.at().toString());
                e.addProperty("player", entry.player().toString());
                e.addProperty("name", entry.playerName());
                e.addProperty("item", entry.itemId());
                e.addProperty("count", entry.count());
                e.addProperty("deposit", entry.deposit());
                e.addProperty("piece", entry.piece());
                log.add(e);
            }
            json.add("log", log);
            array.add(json);
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.add("houses", array);
        return JsonSupport.toJson(root);
    }

    public static List<GuildHouse> decode(String json) throws CorruptDataException, IncompatibleDataException {
        JsonObject root = JsonSupport.parseObject(json);
        JsonSupport.checkSchema(root, SCHEMA_VERSION, "guild house");
        try {
            List<GuildHouse> houses = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (JsonElement element : JsonSupport.requireArray(root, "houses")) {
                GuildHouse house = decodeHouse(JsonSupport.asObject(element, "house"));
                if (!seen.add(house.guildId())) {
                    throw new CorruptDataException("Guild " + house.guildId() + " stored twice");
                }
                houses.add(house);
            }
            return List.copyOf(houses);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new CorruptDataException("Invalid guild house data: " + e.getMessage(), e);
        }
    }

    private static GuildHouse decodeHouse(JsonObject json) throws CorruptDataException {
        String design = json.has("design") ? JsonSupport.requireString(json, "design") : null;
        Instant since = design == null ? null : JsonSupport.requireInstant(json, "since");
        List<BoardNote> notes = new ArrayList<>();
        for (JsonElement element : JsonSupport.requireArray(json, "notes")) {
            JsonObject n = JsonSupport.asObject(element, "note");
            notes.add(new BoardNote(JsonSupport.requireLong(n, "id"), JsonSupport.requireUuid(n, "author"),
                    JsonSupport.requireString(n, "name"), JsonSupport.requireString(n, "text"),
                    JsonSupport.requireInstant(n, "at")));
        }
        List<StorageLogEntry> log = new ArrayList<>();
        for (JsonElement element : JsonSupport.requireArray(json, "log")) {
            JsonObject e = JsonSupport.asObject(element, "log entry");
            JsonElement deposit = JsonSupport.require(e, "deposit");
            if (!deposit.isJsonPrimitive() || !deposit.getAsJsonPrimitive().isBoolean()) {
                throw new CorruptDataException("log entry: deposit must be a boolean");
            }
            log.add(new StorageLogEntry(JsonSupport.requireInstant(e, "at"), JsonSupport.requireUuid(e, "player"),
                    JsonSupport.requireString(e, "name"), JsonSupport.requireString(e, "item"),
                    JsonSupport.requireInt(e, "count"), deposit.getAsBoolean(), JsonSupport.requireString(e, "piece")));
        }
        return new GuildHouse(JsonSupport.requireUuid(json, "guild"), design, since,
                JsonSupport.requireLong(json, "fund"), JsonSupport.requireString(json, "motd"), notes, log,
                JsonSupport.requireLong(json, "nextId"));
    }
}
