package com.eternalrealm.quest;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable snapshot of a character's quests, for persistence ({@link QuestLogCodec}, embedded in the season character
 * file) and for syncing to the owning client. The live, mutable form is {@link QuestLogState}.
 *
 * <p>Only structural invariants are checked here; whether the quests still exist and match their definitions is
 * re-established by {@link QuestLogState#restore}, because the catalog may change between versions.</p>
 *
 * @param active            accepted quests in acceptance order
 * @param completions       quest id to number of turn-ins (dailies count up; others are 1)
 * @param lastDailyResetDay day index of the current daily period, {@link #NEVER_RESET} before the first one
 * @param dailyDone         daily quests turned in during {@code lastDailyResetDay}
 */
public record QuestLog(List<ActiveQuest> active, Map<String, Integer> completions, long lastDailyResetDay,
                       Set<String> dailyDone) {
    public static final long NEVER_RESET = -1L;
    /** Structural bound only; the engine limits acceptance to {@link QuestEngine#MAX_ACTIVE_QUESTS}. */
    public static final int MAX_ACTIVE_ENTRIES = 100;
    public static final QuestLog EMPTY = new QuestLog(List.of(), Map.of(), NEVER_RESET, Set.of());

    public QuestLog {
        active = List.copyOf(active);
        Check.range("active quests", active.size(), 0, MAX_ACTIVE_ENTRIES);
        Set<String> activeIds = new HashSet<>();
        for (ActiveQuest quest : active) {
            if (!activeIds.add(quest.questId())) {
                throw new IllegalArgumentException("quest " + quest.questId() + " is active twice");
            }
        }
        Objects.requireNonNull(completions, "completions");
        TreeMap<String, Integer> completionCopy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : completions.entrySet()) {
            Check.id("completed quest", entry.getKey());
            Check.range("completion count", Objects.requireNonNull(entry.getValue(), "count"), 1, Integer.MAX_VALUE);
            completionCopy.put(entry.getKey(), entry.getValue());
        }
        completions = Collections.unmodifiableMap(completionCopy);
        Check.range("lastDailyResetDay", lastDailyResetDay, NEVER_RESET, Long.MAX_VALUE);
        Objects.requireNonNull(dailyDone, "dailyDone");
        TreeSet<String> dailyCopy = new TreeSet<>();
        for (String id : dailyDone) {
            dailyCopy.add(Check.id("daily quest", id));
        }
        dailyDone = Collections.unmodifiableSet(dailyCopy);
    }
}
