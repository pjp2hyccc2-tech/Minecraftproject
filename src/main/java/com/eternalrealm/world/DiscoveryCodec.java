package com.eternalrealm.world;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;
import java.util.TreeSet;

/**
 * JSON object embedded in a season character file (e.g. under {@code "discovery"}):
 * <pre>
 * { "schema": 1, "areas": [1, 2, 3] }
 * </pre>
 * The object carries its own schema so it can evolve independently of the character document. Unknown or duplicate
 * area ids are corruption.
 */
public final class DiscoveryCodec {
    public static final int SCHEMA_VERSION = 1;

    private DiscoveryCodec() {
    }

    public static JsonObject toJson(Discovery discovery) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray areas = new JsonArray();
        for (int id : discovery.sortedIds()) {
            areas.add(id);
        }
        root.add("areas", areas);
        return root;
    }

    public static Discovery fromJson(JsonElement element) throws CorruptDataException, IncompatibleDataException {
        JsonObject root = StrictJson.asObject(element, "discovery");
        StrictJson.checkSchema(root, SCHEMA_VERSION, "discovery");
        JsonArray areas = StrictJson.requireArray(root, "areas");
        Set<Integer> ids = new TreeSet<>();
        for (int i = 0; i < areas.size(); i++) {
            int id = StrictJson.asInt(areas.get(i), "areas[" + i + "]");
            if (!Areas.exists(id)) {
                throw new CorruptDataException("Unknown area id " + id);
            }
            if (!ids.add(id)) {
                throw new CorruptDataException("Duplicate area id " + id);
            }
        }
        return new Discovery(ids);
    }
}
