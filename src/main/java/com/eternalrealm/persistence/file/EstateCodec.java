package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.estate.EstateData;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstatePlot;
import com.eternalrealm.estate.EstateRecord;
import com.eternalrealm.estate.EstateRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON form of the estate registry ({@code estates.json}, schema 1; docs/modules/estates.md):
 * <pre>
 * { "schema": 1,
 *   "estates": [ { "kind": "room", "owner": "&lt;uuid&gt;", "design": "attic", "plot": [0, 12],
 *                  "previous": [0, 3], "createdAt": 1762851200000 } ],
 *   "wiping": [ [0, 7] ] }
 * </pre>
 * {@code previous} is present only while a redesign is being built. Decoding is strict; data that the registry would
 * not accept (two estates of one kind and owner, a plot used twice) is corruption.
 */
public final class EstateCodec {
    public static final int SCHEMA_VERSION = 1;

    private EstateCodec() {
    }

    public static String encode(EstateData data) {
        EstateRegistry.of(data); // never write what could not be read back
        JsonArray estates = new JsonArray();
        for (EstateRecord record : data.estates()) {
            JsonObject json = new JsonObject();
            json.addProperty("kind", record.key().kind());
            json.addProperty("owner", record.key().owner().toString());
            json.addProperty("design", record.key().design());
            json.add("plot", plot(record.plot()));
            if (record.previous() != null) {
                json.add("previous", plot(record.previous()));
            }
            json.addProperty("createdAt", record.createdAtMillis());
            estates.add(json);
        }
        JsonArray wiping = new JsonArray();
        for (EstatePlot plot : data.wiping()) {
            wiping.add(plot(plot));
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.add("estates", estates);
        root.add("wiping", wiping);
        return JsonSupport.toJson(root);
    }

    public static EstateData decode(String json) throws CorruptDataException, IncompatibleDataException {
        JsonObject root = JsonSupport.parseObject(json);
        JsonSupport.checkSchema(root, SCHEMA_VERSION, "estate");
        try {
            List<EstateRecord> estates = new ArrayList<>();
            for (JsonElement element : JsonSupport.requireArray(root, "estates")) {
                JsonObject estate = JsonSupport.asObject(element, "estate");
                EstateKey key = new EstateKey(JsonSupport.requireString(estate, "kind"),
                        JsonSupport.requireUuid(estate, "owner"), JsonSupport.requireString(estate, "design"));
                EstatePlot previous = estate.has("previous") && !estate.get("previous").isJsonNull()
                        ? plot(estate.get("previous"), "previous") : null;
                estates.add(new EstateRecord(key, plot(JsonSupport.require(estate, "plot"), "plot"), previous,
                        JsonSupport.requireLong(estate, "createdAt")));
            }
            List<EstatePlot> wiping = new ArrayList<>();
            for (JsonElement element : JsonSupport.requireArray(root, "wiping")) {
                wiping.add(plot(element, "wiping"));
            }
            EstateData data = new EstateData(estates, wiping);
            EstateRegistry.of(data);
            return data;
        } catch (IllegalArgumentException e) {
            throw new CorruptDataException("Invalid estate data: " + e.getMessage(), e);
        }
    }

    private static JsonArray plot(EstatePlot plot) {
        JsonArray array = new JsonArray();
        array.add(plot.region());
        array.add(plot.index());
        return array;
    }

    private static EstatePlot plot(JsonElement element, String what) throws CorruptDataException {
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 2) {
            throw new CorruptDataException("'" + what + "' is not a [region, index] pair");
        }
        JsonArray array = element.getAsJsonArray();
        long region = JsonSupport.toLong(array.get(0), what + "[0]");
        long index = JsonSupport.toLong(array.get(1), what + "[1]");
        if (region < 0 || region >= EstatePlot.REGIONS || index < 0 || index >= EstatePlot.CAPACITY) {
            throw new CorruptDataException("'" + what + "' is out of range: " + element);
        }
        return new EstatePlot((int) region, (int) index);
    }
}
