package com.eternalrealm.world;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.eternalrealm.season.SeasonId;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldCodecsTest {
    private static final SeasonId SEASON = SeasonId.of("s1");
    private static final Instant T1 = Instant.parse("2026-11-09T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-10T21:15:30.250Z");

    private static WorldProgressRecord sample() {
        return new WorldProgressRecord(SEASON, Map.of("b1", T1, "b2", T2));
    }

    private static String validJson() {
        return WorldProgressCodec.encode(sample());
    }

    @Test
    public void worldProgressRoundTrip() throws Exception {
        String json = validJson();
        assertTrue(json.contains("\"schema\": 1"));
        assertTrue(json.contains("\"b2\": \"2026-11-10T21:15:30.250Z\""));
        assertTrue(json.endsWith("\n"));
        assertEquals(sample(), WorldProgressCodec.decode(json, SEASON));
        WorldProgressRecord empty = WorldProgressRecord.empty(SEASON);
        assertEquals(empty, WorldProgressCodec.decode(WorldProgressCodec.encode(empty), SEASON));
    }

    @Test
    public void malformedOrMistypedWorldProgressIsCorrupt() {
        assertCorrupt("");
        assertCorrupt("[]");
        assertCorrupt("{not json");
        assertCorrupt(validJson().substring(0, validJson().length() / 2));
        assertCorrupt(validJson().replace("\"seasonId\": \"s1\",", ""));
        assertCorrupt(validJson().replace("\"b1\": \"2026-11-09T10:00:00Z\"", "\"b1\": \"yesterday\""));
        assertCorrupt(validJson().replace("\"b1\": \"2026-11-09T10:00:00Z\"", "\"b1\": 5"));
        assertCorrupt(validJson().replace("\"b1\":", "\"b9\":"));
        assertCorrupt(validJson().replace("\"schema\": 1", "\"schema\": 0"));
        assertCorrupt(validJson().replace("\"schema\": 1", "\"schema\": 1.5"));
        assertCorrupt(validJson().replace("\"seasonId\": \"s1\"", "\"seasonId\": \"S 1\""));
    }

    @Test
    public void theFileMustBelongToTheRequestedSeason() {
        assertThrows(CorruptDataException.class, () -> WorldProgressCodec.decode(validJson(), SeasonId.of("s2")));
    }

    @Test
    public void newerSchemasAreIncompatibleNotCorrupt() {
        String newer = validJson().replace("\"schema\": 1", "\"schema\": 2");
        assertThrows(IncompatibleDataException.class, () -> WorldProgressCodec.decode(newer, SEASON));
        JsonObject discovery = DiscoveryCodec.toJson(Discovery.of(1));
        discovery.addProperty("schema", 7);
        assertThrows(IncompatibleDataException.class, () -> DiscoveryCodec.fromJson(discovery));
    }

    @Test
    public void discoveryRoundTrip() throws Exception {
        Discovery discovery = Discovery.of(4, 1, 2);
        JsonObject json = DiscoveryCodec.toJson(discovery);
        assertEquals("{\"schema\":1,\"areas\":[1,2,4]}", json.toString());
        assertEquals(discovery, DiscoveryCodec.fromJson(json));
        assertEquals(Discovery.NONE, DiscoveryCodec.fromJson(DiscoveryCodec.toJson(Discovery.NONE)));
    }

    @Test
    public void invalidDiscoveryIsCorrupt() {
        for (String json : new String[]{"{\"schema\":1,\"areas\":[1,1]}", "{\"schema\":1,\"areas\":[8]}",
                "{\"schema\":1,\"areas\":[1.5]}", "{\"schema\":1,\"areas\":[\"1\"]}", "{\"schema\":1}",
                "{\"schema\":1,\"areas\":{}}", "{\"areas\":[1]}", "[1,2]", "{\"schema\":0,\"areas\":[]}"}) {
            assertThrows(json, CorruptDataException.class, () -> DiscoveryCodec.fromJson(JsonParser.parseString(json)));
        }
        assertThrows(CorruptDataException.class, () -> DiscoveryCodec.fromJson(null));
    }

    @Test
    public void strictJsonRejectsWhatLenientReadersWouldAccept() throws Exception {
        JsonObject object = StrictJson.parseObject("{\"n\": 12345678901, \"i\": 7, \"s\": \"x\", \"f\": 1.0}");
        assertEquals(12_345_678_901L, StrictJson.requireLong(object, "n"));
        assertEquals(7, StrictJson.requireInt(object, "i"));
        assertEquals("1.0 is integral", 1, StrictJson.requireInt(object, "f"));
        assertThrows(CorruptDataException.class, () -> StrictJson.requireInt(object, "n"));
        assertThrows(CorruptDataException.class, () -> StrictJson.requireInt(object, "s"));
        assertThrows(CorruptDataException.class, () -> StrictJson.requireString(object, "i"));
        assertThrows(CorruptDataException.class, () -> StrictJson.require(object, "missing"));
        assertThrows(CorruptDataException.class, () -> StrictJson.requireArray(object, "s"));
        assertThrows(CorruptDataException.class, () -> StrictJson.requireObject(object, "s"));
        assertThrows("explicit null counts as missing", CorruptDataException.class,
                () -> StrictJson.require(StrictJson.parseObject("{\"a\": null}"), "a"));
    }

    private static void assertCorrupt(String json) {
        assertThrows(json, CorruptDataException.class, () -> WorldProgressCodec.decode(json, SEASON));
    }
}
