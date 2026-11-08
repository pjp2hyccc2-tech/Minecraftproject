package com.eternalrealm.quest;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.WorldProgress;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the quest rules need to know about the character and the world when deciding availability.
 *
 * @param unlockedAreaIds areas open on the server ({@link WorldProgress#unlockedAreaIds()})
 * @param dayIndex        current daily period ({@link DailyReset#dayIndex}), {@code >= 0}
 * @param currentAreaId   area the player stands in (credits {@link Objective.ReachArea} on acceptance), 0 if unknown
 */
public record QuestContext(int level, Set<Integer> unlockedAreaIds, long dayIndex, int currentAreaId) {
    public QuestContext {
        Check.range("level", level, 1, CharacterRules.MAX_LEVEL);
        Objects.requireNonNull(unlockedAreaIds, "unlockedAreaIds");
        TreeSet<Integer> areas = new TreeSet<>();
        for (Integer id : unlockedAreaIds) {
            if (id == null || !Areas.exists(id)) {
                throw new IllegalArgumentException("Unknown area id: " + id);
            }
            areas.add(id);
        }
        unlockedAreaIds = Collections.unmodifiableSet(areas);
        Check.range("dayIndex", dayIndex, 0L, Long.MAX_VALUE);
        Check.range("currentAreaId", currentAreaId, 0, Areas.LAST_ID);
    }

    public static QuestContext of(int level, WorldProgress world, long dayIndex, int currentAreaId) {
        return new QuestContext(level, world.unlockedAreaIds(), dayIndex, currentAreaId);
    }

    public boolean isAreaUnlocked(int areaId) {
        return unlockedAreaIds.contains(areaId);
    }
}
