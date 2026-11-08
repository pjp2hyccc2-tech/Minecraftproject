package com.eternalrealm.world;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.core.ModInfo;

/**
 * One of the concentric overworld areas around the world spawn ({@link Areas}).
 *
 * <p>An area covers the horizontal distances {@code innerRadius <= d < outerRadius} from the spawn; the outermost area
 * has {@link #UNBOUNDED} as its outer radius. The level range is a recommendation (shown to players, used by the monster
 * tables); it never stops anyone from entering. Whether players may enter a locked area at all is decided by the Forge
 * layer from {@link WorldProgress#isAreaUnlocked(int)}.</p>
 *
 * @param slug             stable lower-case name used in translation keys and logs; never rename it
 * @param unlockedByBossId boss whose first defeat on the server unlocks the area for everyone; {@code null} for the
 *                         starting area, which is always open
 * @param monsterTableId   monster table that populates the area (monster module)
 * @param color            RGB colour for the UI (area title, map)
 */
public record AreaDefinition(int id, String slug, int innerRadius, int outerRadius, int minLevel, int maxLevel,
                             String unlockedByBossId, String monsterTableId, int color) {
    /** Outer radius of the outermost area. */
    public static final int UNBOUNDED = Integer.MAX_VALUE;
    /** Minecraft's largest world border radius; a finite ring beyond it could never be reached. */
    public static final int MAX_RADIUS = 30_000_000;

    public AreaDefinition {
        Check.range("id", id, 1, 99);
        Check.id("slug", slug);
        Check.range("innerRadius", innerRadius, 0, MAX_RADIUS - 1);
        if (outerRadius != UNBOUNDED) {
            Check.range("outerRadius", outerRadius, innerRadius + 1, MAX_RADIUS);
        }
        Check.range("minLevel", minLevel, 1, CharacterRules.MAX_LEVEL);
        Check.range("maxLevel", maxLevel, minLevel, CharacterRules.MAX_LEVEL);
        if (unlockedByBossId != null) {
            Check.id("unlockedByBossId", unlockedByBossId);
        }
        Check.id("monsterTableId", monsterTableId);
        Check.range("color", color, 0, 0xFFFFFF);
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

    public boolean isUnbounded() {
        return outerRadius == UNBOUNDED;
    }

    /** The starting area is open from the beginning of the season. */
    public boolean isOpenFromStart() {
        return unlockedByBossId == null;
    }

    /** Whether a horizontal distance from the spawn lies inside this ring. */
    public boolean containsDistance(double distance) {
        return distance >= innerRadius && (isUnbounded() || distance < outerRadius);
    }
}
