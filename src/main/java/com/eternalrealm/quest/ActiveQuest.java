package com.eternalrealm.quest;

import java.util.List;

/**
 * An accepted, not yet turned-in quest in a {@link QuestLog}.
 *
 * @param progress one counter per objective, in objective order
 */
public record ActiveQuest(String questId, List<Integer> progress) {
    public ActiveQuest {
        Check.id("questId", questId);
        progress = List.copyOf(progress);
        Check.range("objective count", progress.size(), 1, QuestDefinition.MAX_OBJECTIVES);
        for (int value : progress) {
            Check.range("progress", value, 0, Objective.MAX_COUNT);
        }
    }
}
