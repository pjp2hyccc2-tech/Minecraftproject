package com.eternalrealm.ranking;

import com.eternalrealm.core.data.CorruptDataException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * JSON form of {@link SeasonStats}, embedded as an object in the character file and in the season's ranking file:
 * {@code {"monstersKilled": 12, ..., "level": 7, "totalExperience": 4567}} (keys are {@link SeasonCounter#id()}).
 *
 * <p><b>Tolerant to missing fields:</b> an absent (or {@code null}) field reads as 0, so a counter added in a later
 * version needs no schema change and older files stay readable. Unknown fields are ignored for the same reason.
 * A field that is present but is not a non-negative integer is corruption: guessing a value would silently change
 * somebody's ranking.</p>
 */
public final class SeasonStatsCodec {
    public static final String LEVEL = "level";
    public static final String TOTAL_EXPERIENCE = "totalExperience";

    private SeasonStatsCodec() {
    }

    public static JsonObject encode(SeasonStats stats) {
        Objects.requireNonNull(stats, "stats");
        JsonObject json = new JsonObject();
        for (SeasonCounter counter : SeasonCounter.all()) {
            json.addProperty(counter.id(), stats.get(counter));
        }
        json.addProperty(LEVEL, stats.level());
        json.addProperty(TOTAL_EXPERIENCE, stats.totalExperience());
        return json;
    }

    public static SeasonStats decode(JsonObject json) throws CorruptDataException {
        Objects.requireNonNull(json, "json");
        long[] counters = new long[SeasonCounter.all().size()];
        for (SeasonCounter counter : SeasonCounter.all()) {
            counters[counter.ordinal()] = readNonNegative(json, counter.id());
        }
        long level = readNonNegative(json, LEVEL);
        if (level > Integer.MAX_VALUE) {
            throw new CorruptDataException("Season stat '" + LEVEL + "' is out of range: " + level);
        }
        return SeasonStats.of(counters, (int) level, readNonNegative(json, TOTAL_EXPERIENCE));
    }

    private static long readNonNegative(JsonObject json, String key) throws CorruptDataException {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return 0L;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new CorruptDataException("Season stat '" + key + "' is not a number: " + element);
        }
        long value;
        try {
            value = element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new CorruptDataException("Season stat '" + key + "' is not an integer: " + element, e);
        }
        if (value < 0) {
            throw new CorruptDataException("Season stat '" + key + "' is negative: " + value);
        }
        return value;
    }
}
