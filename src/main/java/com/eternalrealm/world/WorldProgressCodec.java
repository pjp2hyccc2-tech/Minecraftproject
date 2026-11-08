package com.eternalrealm.world;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.season.SeasonId;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON document {@code seasons/<seasonId>/world_progress.json}:
 * <pre>
 * { "schema": 1, "seasonId": "s1", "bossFirstDefeats": { "b1": "2026-11-09T12:00:00Z", ... } }
 * </pre>
 * Decoding is strict (see {@link StrictJson}); unknown boss ids and a season that does not match the directory are
 * corruption.
 */
public final class WorldProgressCodec {
    public static final int SCHEMA_VERSION = 1;

    private WorldProgressCodec() {
    }

    public static String encode(WorldProgressRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("seasonId", record.seasonId().value());
        JsonObject defeats = new JsonObject();
        record.bossFirstDefeats().forEach((boss, at) -> defeats.addProperty(boss, at.toString()));
        root.add("bossFirstDefeats", defeats);
        return StrictJson.print(root);
    }

    public static WorldProgressRecord decode(String json, SeasonId expectedSeason)
            throws CorruptDataException, IncompatibleDataException {
        JsonObject root = StrictJson.parseObject(json);
        StrictJson.checkSchema(root, SCHEMA_VERSION, "world progress");
        try {
            SeasonId season = new SeasonId(StrictJson.requireString(root, "seasonId"));
            if (!season.equals(expectedSeason)) {
                throw new CorruptDataException("File belongs to season " + season + ", expected " + expectedSeason);
            }
            JsonObject defeatsJson = StrictJson.requireObject(root, "bossFirstDefeats");
            Map<String, Instant> defeats = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : defeatsJson.entrySet()) {
                if (!Areas.isBossId(entry.getKey())) {
                    throw new CorruptDataException("Unknown boss '" + entry.getKey() + "'");
                }
                defeats.put(entry.getKey(), StrictJson.asInstant(entry.getValue(), "bossFirstDefeats." + entry.getKey()));
            }
            return new WorldProgressRecord(season, defeats);
        } catch (IllegalArgumentException e) {
            throw new CorruptDataException("Invalid world progress data: " + e.getMessage(), e);
        }
    }
}
