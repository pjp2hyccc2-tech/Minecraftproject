package com.eternalrealm.ranking;

import com.eternalrealm.core.data.CorruptDataException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SeasonStatsCodecTest {
    @Test
    public void roundTrip() throws Exception {
        SeasonStats stats = RankingFixtures.distinctStats(40);
        JsonObject json = SeasonStatsCodec.encode(stats);
        assertEquals(stats, SeasonStatsCodec.decode(json));
        assertEquals(40, json.get("monstersKilled").getAsLong());
        assertEquals(7, json.get(SeasonStatsCodec.LEVEL).getAsInt());
        assertEquals(SeasonCounter.all().size() + 2, json.size());
        SeasonStats extreme = SeasonStats.of(new long[]{Long.MAX_VALUE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                Integer.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(extreme, SeasonStatsCodec.decode(SeasonStatsCodec.encode(extreme)));
    }

    @Test
    public void missingAndNullFieldsReadAsZero() throws Exception {
        assertEquals(SeasonStats.EMPTY, SeasonStatsCodec.decode(new JsonObject()));
        JsonObject partial = new JsonObject();
        partial.addProperty("bossesKilled", 3);
        partial.add("deaths", JsonNull.INSTANCE);
        SeasonStats decoded = SeasonStatsCodec.decode(partial);
        assertEquals(3, decoded.bossesKilled());
        assertEquals(0, decoded.deaths());
        assertEquals(0, decoded.level());
    }

    @Test
    public void unknownFieldsAreIgnored() throws Exception {
        JsonObject json = SeasonStatsCodec.encode(RankingFixtures.distinctStats(1));
        json.addProperty("fishCaught", 99);
        json.add("futureObject", new JsonObject());
        assertEquals(RankingFixtures.distinctStats(1), SeasonStatsCodec.decode(json));
    }

    @Test
    public void presentButInvalidValuesAreCorrupt() {
        assertCorrupt("monstersKilled", "\"5\"");
        assertCorrupt("monstersKilled", "1.5");
        assertCorrupt("monstersKilled", "-1");
        assertCorrupt("monstersKilled", "true");
        assertCorrupt("monstersKilled", "{}");
        assertCorrupt("monstersKilled", "[1]");
        assertCorrupt("totalExperience", "1e30");
        assertCorrupt("level", "-3");
        assertCorrupt("level", "2147483648");
    }

    @Test
    public void readsTheObjectEmbeddedInALargerDocument() throws Exception {
        String characterFile = "{\"schema\": 1, \"seasonStats\": {\"itemsCrafted\": 12, \"level\": 9, \"totalExperience\": 4567.0}}";
        JsonObject embedded = JsonParser.parseString(characterFile).getAsJsonObject().getAsJsonObject("seasonStats");
        SeasonStats stats = SeasonStatsCodec.decode(embedded);
        assertEquals(12, stats.itemsCrafted());
        assertEquals(9, stats.level());
        assertEquals("an integral decimal is still an integer", 4567, stats.totalExperience());
    }

    private static void assertCorrupt(String key, String rawValue) {
        JsonObject json = JsonParser.parseString("{\"" + key + "\": " + rawValue + "}").getAsJsonObject();
        assertThrows(key + "=" + rawValue, CorruptDataException.class, () -> SeasonStatsCodec.decode(json));
    }
}
