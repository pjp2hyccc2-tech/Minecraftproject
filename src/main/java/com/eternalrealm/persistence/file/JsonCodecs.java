package com.eternalrealm.persistence.file;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.player.AccountRecord;
import com.eternalrealm.season.SeasonId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned JSON encoding of persistent records (format: docs/DATABASE_SCHEMA.md).
 *
 * <p>Decoding is strict: missing fields, wrong types, non-integral numbers, unknown stats or an identity that does
 * not match the requested file are corruption. A schema newer than supported is {@link IncompatibleDataException}
 * (the data is valid, this build is too old) and is never quarantined.</p>
 */
public final class JsonCodecs {
    /**
     * 2: job, skills, gold and module extensions (schema 1 files are read with empty defaults); 3: the skill loadout
     * (schema 2 files are read with the default loadout).
     */
    public static final int CHARACTER_SCHEMA_VERSION = 3;
    public static final int ACCOUNT_SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonCodecs() {
    }

    // ---------------------------------------------------------------------------------------------- character

    public static String encodeCharacter(CharacterRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", CHARACTER_SCHEMA_VERSION);
        root.addProperty("playerId", record.playerId().toString());
        root.addProperty("seasonId", record.seasonId().value());
        root.addProperty("level", record.level());
        root.addProperty("experience", record.experience());
        root.addProperty("totalExperience", record.totalExperience());
        JsonObject stats = new JsonObject();
        for (StatType type : StatType.values()) {
            stats.addProperty(type.id(), record.allocatedStats().get(type));
        }
        root.add("allocatedStats", stats);
        root.addProperty("createdAt", record.createdAt().toString());
        root.addProperty("updatedAt", record.updatedAt().toString());
        root.addProperty("jobId", record.jobId());
        JsonObject skills = new JsonObject();
        record.skillInvestments().forEach(skills::addProperty);
        root.add("skills", skills);
        root.addProperty("gold", record.gold());
        JsonObject extensions = new JsonObject();
        record.extensions().forEach((key, json) -> extensions.add(key, embed(json)));
        root.add("extensions", extensions);
        com.google.gson.JsonArray loadout = new com.google.gson.JsonArray();
        record.skillLoadout().forEach(loadout::add);
        root.add("skillLoadout", loadout);
        return GSON.toJson(root) + "\n";
    }

    /** Module documents are embedded as JSON; anything unparsable is kept verbatim as a string so it round-trips. */
    private static JsonElement embed(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element != null && (element.isJsonObject() || element.isJsonArray())) {
                return element;
            }
        } catch (JsonParseException ignored) {
            // fall through
        }
        return new com.google.gson.JsonPrimitive(json);
    }

    private static String unembed(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return element.toString();
    }

    public static CharacterRecord decodeCharacter(String json, UUID expectedPlayer, SeasonId expectedSeason)
            throws CorruptDataException, IncompatibleDataException {
        JsonObject root = parseObject(json);
        checkSchema(root, CHARACTER_SCHEMA_VERSION, "character");
        try {
            UUID playerId = UUID.fromString(requireString(root, "playerId"));
            if (!playerId.equals(expectedPlayer)) {
                throw new CorruptDataException("File belongs to " + playerId + ", expected " + expectedPlayer);
            }
            SeasonId seasonId = new SeasonId(requireString(root, "seasonId"));
            if (!seasonId.equals(expectedSeason)) {
                throw new CorruptDataException("File belongs to season " + seasonId + ", expected " + expectedSeason);
            }
            JsonObject statsJson = requireObject(root, "allocatedStats");
            Map<StatType, Integer> stats = new EnumMap<>(StatType.class);
            for (Map.Entry<String, JsonElement> entry : statsJson.entrySet()) {
                StatType type = StatType.byId(entry.getKey())
                        .orElseThrow(() -> new CorruptDataException("Unknown stat '" + entry.getKey() + "'"));
                stats.put(type, toInt(entry.getValue(), "allocatedStats." + entry.getKey()));
            }
            for (StatType type : StatType.values()) {
                if (!stats.containsKey(type)) {
                    throw new CorruptDataException("Missing stat '" + type.id() + "'");
                }
            }
            String jobId = CharacterRecord.NO_JOB;
            Map<String, Integer> skills = new LinkedHashMap<>();
            long gold = 0L;
            Map<String, String> extensions = new LinkedHashMap<>();
            java.util.List<String> loadout = new java.util.ArrayList<>();
            int schema = requireInt(root, "schema");
            if (schema >= 3) {
                JsonElement array = require(root, "skillLoadout");
                if (!array.isJsonArray()) {
                    throw new CorruptDataException("Field 'skillLoadout' is not an array");
                }
                for (JsonElement entry : array.getAsJsonArray()) {
                    if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                        throw new CorruptDataException("skillLoadout entries must be strings");
                    }
                    loadout.add(entry.getAsString());
                }
            }
            if (schema >= 2) {
                jobId = requireString(root, "jobId");
                for (Map.Entry<String, JsonElement> entry : requireObject(root, "skills").entrySet()) {
                    skills.put(entry.getKey(), toInt(entry.getValue(), "skills." + entry.getKey()));
                }
                gold = requireLong(root, "gold");
                for (Map.Entry<String, JsonElement> entry : requireObject(root, "extensions").entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isJsonNull()) {
                        throw new CorruptDataException("Extension '" + entry.getKey() + "' is null");
                    }
                    extensions.put(entry.getKey(), unembed(entry.getValue()));
                }
            }
            return new CharacterRecord(playerId, seasonId,
                    requireInt(root, "level"),
                    requireLong(root, "experience"),
                    requireLong(root, "totalExperience"),
                    StatBlock.of(stats),
                    requireInstant(root, "createdAt"),
                    requireInstant(root, "updatedAt"),
                    jobId, skills, gold, extensions, loadout);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new CorruptDataException("Invalid character data: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------------------------------- account

    public static String encodeAccount(AccountRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", ACCOUNT_SCHEMA_VERSION);
        root.addProperty("playerId", record.playerId().toString());
        root.addProperty("lastKnownName", record.lastKnownName());
        root.addProperty("firstLoginAt", record.firstLoginAt().toString());
        root.addProperty("lastLoginAt", record.lastLoginAt().toString());
        root.addProperty("loginCount", record.loginCount());
        return GSON.toJson(root) + "\n";
    }

    public static AccountRecord decodeAccount(String json, UUID expectedPlayer)
            throws CorruptDataException, IncompatibleDataException {
        JsonObject root = parseObject(json);
        checkSchema(root, ACCOUNT_SCHEMA_VERSION, "account");
        try {
            UUID playerId = UUID.fromString(requireString(root, "playerId"));
            if (!playerId.equals(expectedPlayer)) {
                throw new CorruptDataException("File belongs to " + playerId + ", expected " + expectedPlayer);
            }
            return new AccountRecord(playerId,
                    requireString(root, "lastKnownName"),
                    requireInstant(root, "firstLoginAt"),
                    requireInstant(root, "lastLoginAt"),
                    requireLong(root, "loginCount"));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new CorruptDataException("Invalid account data: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static JsonObject parseObject(String json) throws CorruptDataException {
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

    private static void checkSchema(JsonObject root, int supported, String kind)
            throws CorruptDataException, IncompatibleDataException {
        int schema = requireInt(root, "schema");
        if (schema > supported) {
            throw new IncompatibleDataException("The " + kind + " data uses schema " + schema
                    + " but this build only supports up to " + supported + ". Update the mod.");
        }
        if (schema < 1) {
            throw new CorruptDataException("Invalid " + kind + " schema version " + schema);
        }
        // Older schemas are migrated while decoding (missing fields get their defaults).
    }

    private static JsonElement require(JsonObject root, String key) throws CorruptDataException {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            throw new CorruptDataException("Missing field '" + key + "'");
        }
        return element;
    }

    private static String requireString(JsonObject root, String key) throws CorruptDataException {
        JsonElement element = require(root, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new CorruptDataException("Field '" + key + "' is not a string");
        }
        return element.getAsString();
    }

    private static JsonObject requireObject(JsonObject root, String key) throws CorruptDataException {
        JsonElement element = require(root, key);
        if (!element.isJsonObject()) {
            throw new CorruptDataException("Field '" + key + "' is not an object");
        }
        return element.getAsJsonObject();
    }

    private static long requireLong(JsonObject root, String key) throws CorruptDataException {
        return toLong(require(root, key), key);
    }

    private static int requireInt(JsonObject root, String key) throws CorruptDataException {
        return toInt(require(root, key), key);
    }

    private static Instant requireInstant(JsonObject root, String key) throws CorruptDataException {
        return Instant.parse(requireString(root, key));
    }

    private static long toLong(JsonElement element, String key) throws CorruptDataException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new CorruptDataException("Field '" + key + "' is not a number");
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new CorruptDataException("Field '" + key + "' is not an integer: " + element, e);
        }
    }

    private static int toInt(JsonElement element, String key) throws CorruptDataException {
        long value = toLong(element, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new CorruptDataException("Field '" + key + "' is out of int range: " + value);
        }
        return (int) value;
    }
}
