package com.eternalrealm.quest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Mutable quest state of one character: active quests with per-objective progress, completion counts and the daily
 * period. All rules live in {@link QuestEngine}; this class only holds the data and its invariants.
 *
 * <p><b>Threading:</b> server thread only. Persistence and the client receive immutable {@link QuestLog} snapshots.
 * Every change increments {@link #revision()}, so the owner can tell when the character needs saving and syncing.</p>
 */
public final class QuestLogState {
    private final LinkedHashMap<String, int[]> active = new LinkedHashMap<>();
    private final TreeMap<String, Integer> completions = new TreeMap<>();
    private final TreeSet<String> dailyDone = new TreeSet<>();
    private long lastDailyResetDay = QuestLog.NEVER_RESET;
    private long revision;

    private QuestLogState() {
    }

    /** A character without any quest history. */
    public static QuestLogState fresh() {
        return new QuestLogState();
    }

    /** Restores a stored log and {@link #reconcile reconciles} it with the current catalog. */
    public static QuestLogState restore(QuestLog log, QuestCatalog catalog) {
        QuestLogState state = new QuestLogState();
        for (ActiveQuest quest : log.active()) {
            int[] progress = new int[quest.progress().size()];
            for (int i = 0; i < progress.length; i++) {
                progress[i] = quest.progress().get(i);
            }
            state.active.put(quest.questId(), progress);
        }
        state.completions.putAll(log.completions());
        state.dailyDone.addAll(log.dailyDone());
        state.lastDailyResetDay = log.lastDailyResetDay();
        state.reconcile(catalog);
        return state;
    }

    /**
     * Re-establishes the invariants under {@code catalog} (after loading, or when an update changed quests): active
     * quests the catalog no longer knows are dropped, as are once-only quests that are already completed; progress is
     * reset when the number of objectives changed and clamped to {@code [0, required]} otherwise; daily markers of
     * quests that are not (or no longer) dailies are dropped. Completion counts are kept as history.
     *
     * @return {@code true} if anything had to be adjusted (the state is then dirty)
     */
    public boolean reconcile(QuestCatalog catalog) {
        boolean changed = false;
        Iterator<Map.Entry<String, int[]>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, int[]> entry = iterator.next();
            Optional<QuestDefinition> quest = catalog.byId(entry.getKey());
            if (quest.isEmpty() || (!quest.get().kind().isRepeatable() && completions.containsKey(entry.getKey()))) {
                iterator.remove();
                changed = true;
                continue;
            }
            List<Objective> objectives = quest.get().objectives();
            int[] progress = entry.getValue();
            if (progress.length != objectives.size()) {
                entry.setValue(new int[objectives.size()]);
                changed = true;
                continue;
            }
            for (int i = 0; i < progress.length; i++) {
                int clamped = Math.max(0, Math.min(objectives.get(i).required(), progress[i]));
                if (clamped != progress[i]) {
                    progress[i] = clamped;
                    changed = true;
                }
            }
        }
        if (dailyDone.removeIf(id -> catalog.byId(id).map(quest -> quest.kind() != QuestKind.DAILY).orElse(true))) {
            changed = true;
        }
        if (changed) {
            revision++;
        }
        return changed;
    }

    public QuestLog snapshot() {
        List<ActiveQuest> quests = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : active.entrySet()) {
            quests.add(new ActiveQuest(entry.getKey(), toList(entry.getValue())));
        }
        return new QuestLog(quests, completions, lastDailyResetDay, dailyDone);
    }

    public boolean isActive(String questId) {
        return active.containsKey(questId);
    }

    /** Active quest ids in acceptance order. */
    public List<String> activeQuestIds() {
        return List.copyOf(active.keySet());
    }

    public int activeCount() {
        return active.size();
    }

    /** Per-objective progress of an active quest; empty if the quest is not active. */
    public List<Integer> progress(String questId) {
        int[] progress = active.get(questId);
        return progress == null ? List.of() : toList(progress);
    }

    public int completionCount(String questId) {
        return completions.getOrDefault(questId, 0);
    }

    public boolean hasCompleted(String questId) {
        return completionCount(questId) > 0;
    }

    /** Whether the daily was turned in during the period {@link #lastDailyResetDay()}. */
    public boolean isDailyDone(String questId) {
        return dailyDone.contains(questId);
    }

    public long lastDailyResetDay() {
        return lastDailyResetDay;
    }

    public long revision() {
        return revision;
    }

    // ------------------------------------------------------------------------------ mutations (QuestEngine only)

    void start(QuestDefinition quest) {
        active.put(quest.id(), new int[quest.objectives().size()]);
        revision++;
    }

    boolean remove(String questId) {
        if (active.remove(questId) == null) {
            return false;
        }
        revision++;
        return true;
    }

    void setProgress(String questId, int objectiveIndex, int value) {
        int[] progress = active.get(questId);
        if (progress == null) {
            throw new IllegalStateException("quest " + questId + " is not active");
        }
        if (progress[objectiveIndex] != value) {
            progress[objectiveIndex] = value;
            revision++;
        }
    }

    void complete(QuestDefinition quest) {
        active.remove(quest.id());
        completions.merge(quest.id(), 1, (count, one) -> count == Integer.MAX_VALUE ? count : count + one);
        if (quest.kind() == QuestKind.DAILY) {
            dailyDone.add(quest.id());
        }
        revision++;
    }

    /** Starts the daily period {@code dayIndex} if it is later than the current one. */
    boolean startDailyPeriod(long dayIndex) {
        if (dayIndex <= lastDailyResetDay) {
            return false;
        }
        lastDailyResetDay = dayIndex;
        dailyDone.clear();
        revision++;
        return true;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return List.copyOf(list);
    }
}
