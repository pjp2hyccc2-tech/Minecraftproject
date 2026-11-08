package com.eternalrealm.quest;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.world.Areas;

import java.util.HashSet;
import java.util.List;

/**
 * What a character needs before accepting a quest.
 *
 * @param questIds       quests the character must have completed (at least once)
 * @param minLevel       minimum character level
 * @param requiredAreaId area that must be unlocked on the server ({@link com.eternalrealm.world.WorldProgress})
 */
public record QuestPrerequisites(List<String> questIds, int minLevel, int requiredAreaId) {
    public static final int MAX_QUESTS = 8;

    public QuestPrerequisites {
        questIds = List.copyOf(questIds);
        Check.range("prerequisite quests", questIds.size(), 0, MAX_QUESTS);
        for (String id : questIds) {
            Check.id("prerequisite quest", id);
        }
        if (new HashSet<>(questIds).size() != questIds.size()) {
            throw new IllegalArgumentException("duplicate prerequisite quests: " + questIds);
        }
        Check.range("minLevel", minLevel, 1, CharacterRules.MAX_LEVEL);
        Check.range("requiredAreaId", requiredAreaId, Areas.FIRST_ID, Areas.LAST_ID);
    }
}
