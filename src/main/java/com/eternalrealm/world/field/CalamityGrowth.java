package com.eternalrealm.world.field;

import com.eternalrealm.monster.MonsterScaling;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * How far each field's 災厄 (calamity) has grown (owner's request of 2026-11-13: "各フィールドの災厄は倒される毎にレベルが
 * 1上がるようにして"). Every time a calamity is subdued its field's count goes up by one, and it manifests the next time one
 * level higher, up to level {@link #MAX_LEVEL}:
 *
 * <pre>
 * level  = min(100, boss level + times subdued)
 * health × r(level) / r(boss level)                  r = the reference light hit of a level-matched player
 * damage × (1 + 0.7 × (r(level) / r(boss level) − 1))  (MonsterScaling#referenceLightHit), so the fight keeps pace
 *                                                      with the players who keep beating it
 * rewards  experience and gold of the grown level
 * </pre>
 *
 * <p>The counts are saved with アストリア (they survive restarts and seasons of the same world). A count keeps going
 * after level 100 (shown on the board), the level stays there.</p>
 */
public final class CalamityGrowth {
    /** The highest level a calamity grows to (the level cap of characters and monsters). */
    public static final int MAX_LEVEL = MonsterScaling.MAX_LEVEL;
    /** How much of the health growth the damage follows. */
    public static final double DAMAGE_SHARE = 0.7;
    /** Counts stop here (a sanity limit for stored data). */
    public static final int MAX_DEFEATS = 1_000_000;

    private final int[] defeats = new int[FieldLayout.FIELD_COUNT + 1];

    private static boolean valid(int field) {
        return field >= 1 && field <= FieldLayout.FIELD_COUNT;
    }

    /** How many times the calamity of {@code field} was subdued. */
    public int defeats(int field) {
        return valid(field) ? defeats[field] : 0;
    }

    /** The calamity of {@code field} was subdued: it comes back one level higher. */
    public void defeated(int field) {
        if (valid(field) && defeats[field] < MAX_DEFEATS) {
            defeats[field]++;
        }
    }

    /** The level the calamity of {@code field} manifests at, from its boss's own level. */
    public int level(int field, int baseLevel) {
        return levelAfter(baseLevel, defeats(field));
    }

    /** {@code min(100, base + defeats)}, at least the base. */
    public static int levelAfter(int baseLevel, int defeats) {
        int base = Math.max(1, Math.min(MAX_LEVEL, baseLevel));
        return (int) Math.min(MAX_LEVEL, (long) base + Math.max(0, defeats));
    }

    /** Health of a calamity grown from {@code baseLevel} to {@code level}, as a factor of its own. */
    public static double healthFactor(int baseLevel, int level) {
        int base = Math.max(1, Math.min(MAX_LEVEL, baseLevel));
        int grown = Math.max(base, Math.min(MAX_LEVEL, level));
        return MonsterScaling.referenceLightHit(grown) / MonsterScaling.referenceLightHit(base);
    }

    /** Damage of a calamity grown from {@code baseLevel} to {@code level}, as a factor of its own. */
    public static double damageFactor(int baseLevel, int level) {
        return 1.0 + DAMAGE_SHARE * (healthFactor(baseLevel, level) - 1.0);
    }

    // ------------------------------------------------------------------ persistence

    public String toJson() {
        JsonArray array = new JsonArray();
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            array.add(defeats[field]);
        }
        JsonObject json = new JsonObject();
        json.add("defeats", array);
        return json.toString();
    }

    /** Broken or missing data starts every calamity at its own level. */
    public static CalamityGrowth fromJson(String text) {
        CalamityGrowth growth = new CalamityGrowth();
        if (text == null || text.isEmpty()) {
            return growth;
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (element.isJsonObject() && element.getAsJsonObject().has("defeats")) {
                JsonArray array = element.getAsJsonObject().getAsJsonArray("defeats");
                for (int i = 0; i < array.size() && i < FieldLayout.FIELD_COUNT; i++) {
                    growth.defeats[i + 1] = Math.max(0, Math.min(MAX_DEFEATS, array.get(i).getAsInt()));
                }
            }
        } catch (RuntimeException e) {
            // broken data: start from the bosses' own levels
        }
        return growth;
    }
}
