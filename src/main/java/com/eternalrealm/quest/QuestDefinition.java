package com.eternalrealm.quest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.Areas;

import java.util.List;
import java.util.Objects;

/**
 * A quest. Immutable; the catalog ({@link Quests}) is defined in code so server and client always agree.
 *
 * @param areaId     area the quest belongs to (quest log grouping, map marker)
 * @param objectives all must be complete to turn the quest in; progress is tracked per objective
 */
public record QuestDefinition(String id, QuestKind kind, int areaId, QuestPrerequisites prerequisites,
                              List<Objective> objectives, QuestReward reward) {
    public static final int MAX_OBJECTIVES = 5;

    public QuestDefinition {
        Check.id("id", id);
        Objects.requireNonNull(kind, "kind");
        Check.range("areaId", areaId, Areas.FIRST_ID, Areas.LAST_ID);
        Objects.requireNonNull(prerequisites, "prerequisites");
        objectives = List.copyOf(objectives);
        Check.range("objectives", objectives.size(), 1, MAX_OBJECTIVES);
        Objects.requireNonNull(reward, "reward");
        if (prerequisites.questIds().contains(id)) {
            throw new IllegalArgumentException(id + " cannot require itself");
        }
    }

    public String translationKey() {
        return "quest." + ModInfo.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** Whether {@code progress} (one value per objective) completes every objective. */
    public boolean isComplete(List<Integer> progress) {
        if (progress.size() != objectives.size()) {
            return false;
        }
        for (int i = 0; i < objectives.size(); i++) {
            if (progress.get(i) < objectives.get(i).required()) {
                return false;
            }
        }
        return true;
    }
}
