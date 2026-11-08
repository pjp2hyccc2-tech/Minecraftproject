package com.eternalrealm.quest;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.world.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * JSON object for embedding a {@link QuestLog} in the season character file (e.g. under {@code "quests"}):
 * <pre>
 * {
 *   "schema": 1,
 *   "active": [ { "quest": "m1_hunt", "progress": [3, 6] } ],
 *   "completed": { "m1_arrival": 1, "daily_plains_patrol": 4 },
 *   "lastDailyReset": 20765,
 *   "dailyDone": [ "daily_plains_patrol" ]
 * }
 * </pre>
 * The object has its own schema so it can evolve independently of the character document. Decoding is structural and
 * strict (see {@link StrictJson}); unknown quest ids are kept here and dropped by {@link QuestLogState#restore}.
 */
public final class QuestLogCodec {
    public static final int SCHEMA_VERSION = 1;

    private QuestLogCodec() {
    }

    public static JsonObject toJson(QuestLog log) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray active = new JsonArray();
        for (ActiveQuest quest : log.active()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("quest", quest.questId());
            JsonArray progress = new JsonArray();
            quest.progress().forEach(progress::add);
            entry.add("progress", progress);
            active.add(entry);
        }
        root.add("active", active);
        JsonObject completed = new JsonObject();
        log.completions().forEach(completed::addProperty);
        root.add("completed", completed);
        root.addProperty("lastDailyReset", log.lastDailyResetDay());
        JsonArray daily = new JsonArray();
        log.dailyDone().forEach(daily::add);
        root.add("dailyDone", daily);
        return root;
    }

    public static QuestLog fromJson(JsonElement element) throws CorruptDataException, IncompatibleDataException {
        JsonObject root = StrictJson.asObject(element, "quest log");
        StrictJson.checkSchema(root, SCHEMA_VERSION, "quest log");
        try {
            JsonArray activeJson = StrictJson.requireArray(root, "active");
            List<ActiveQuest> active = new ArrayList<>();
            for (int i = 0; i < activeJson.size(); i++) {
                JsonObject entry = StrictJson.asObject(activeJson.get(i), "active[" + i + "]");
                JsonArray progressJson = StrictJson.requireArray(entry, "progress");
                List<Integer> progress = new ArrayList<>();
                for (int j = 0; j < progressJson.size(); j++) {
                    progress.add(StrictJson.asInt(progressJson.get(j), "active[" + i + "].progress[" + j + "]"));
                }
                active.add(new ActiveQuest(StrictJson.requireString(entry, "quest"), progress));
            }
            JsonObject completedJson = StrictJson.requireObject(root, "completed");
            Map<String, Integer> completions = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : completedJson.entrySet()) {
                completions.put(entry.getKey(), StrictJson.asInt(entry.getValue(), "completed." + entry.getKey()));
            }
            long lastReset = StrictJson.requireLong(root, "lastDailyReset");
            JsonArray dailyJson = StrictJson.requireArray(root, "dailyDone");
            Set<String> daily = new TreeSet<>();
            for (int i = 0; i < dailyJson.size(); i++) {
                String id = StrictJson.asString(dailyJson.get(i), "dailyDone[" + i + "]");
                if (!daily.add(id)) {
                    throw new CorruptDataException("Duplicate daily quest " + id);
                }
            }
            return new QuestLog(active, completions, lastReset, daily);
        } catch (IllegalArgumentException e) {
            throw new CorruptDataException("Invalid quest log: " + e.getMessage(), e);
        }
    }
}
