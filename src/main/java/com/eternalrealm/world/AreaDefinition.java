package com.eternalrealm.world;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.core.ModInfo;

/**
 * One of the fields of アストリア around the royal capital ({@link Areas}; where each lies is
 * {@link com.eternalrealm.world.field.FieldLayout}).
 *
 * <p>The level range is a recommendation (shown to players, used by the monster tables); it never stops anyone from
 * entering. {@code openEnded} shows it as "Lv.40〜" (the high fields). Whether players may enter a locked field at all is
 * decided by the Forge layer from {@link WorldProgress#isAreaUnlocked(int)}.</p>
 *
 * @param slug             stable lower-case name used in translation keys and logs; never rename it (the field names
 *                         changed with the field map, the slugs did not)
 * @param unlockedByBossId boss whose first defeat on the server unlocks the area for everyone; {@code null} for the
 *                         starting area, which is always open
 * @param monsterTableId   monster table that populates the area (monster module)
 * @param color            RGB colour for the UI (area title, map)
 * @param kind             how the field is marked on the field map
 * @param tier             content tier 1..7 of its level band (equipment rolls, materials); the seven story fields
 *                         have tier = id, the side fields share the tier of their level band
 */
public record AreaDefinition(int id, String slug, int minLevel, int maxLevel, boolean openEnded,
                             String unlockedByBossId, String monsterTableId, int color, Kind kind, int tier) {
    /** Highest content tier. */
    public static final int MAX_TIER = 7;

    /** Map legend of the field map. */
    public enum Kind {
        /** 主要エリア: on the main road of the story. */
        MAIN,
        /** クエストエリア: side fields full of requests. */
        QUEST,
        /** 危険地帯: the end of the world. */
        DANGER
    }

    public AreaDefinition {
        Check.range("id", id, 1, 99);
        Check.id("slug", slug);
        Check.range("minLevel", minLevel, 1, CharacterRules.MAX_LEVEL);
        Check.range("maxLevel", maxLevel, minLevel, CharacterRules.MAX_LEVEL);
        if (unlockedByBossId != null) {
            Check.id("unlockedByBossId", unlockedByBossId);
        }
        Check.id("monsterTableId", monsterTableId);
        Check.range("color", color, 0, 0xFFFFFF);
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        Check.range("tier", tier, 1, MAX_TIER);
    }

    public String translationKey() {
        return "area." + ModInfo.MOD_ID + "." + slug;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    public LevelRange levelRange() {
        return new LevelRange(minLevel, maxLevel);
    }

    /** The starting area is open from the beginning of the season. */
    public boolean isOpenFromStart() {
        return unlockedByBossId == null;
    }
}
