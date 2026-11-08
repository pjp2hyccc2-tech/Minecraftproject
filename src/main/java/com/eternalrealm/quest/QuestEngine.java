package com.eternalrealm.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The quest rules, applied to a character's {@link QuestLogState}. Stateless apart from the catalog, so one engine
 * serves every player; server thread only (the states are not thread-safe).
 *
 * <ul>
 *     <li><b>Accept</b>: not active, not completed (MAIN/SIDE) or not done in the current day (DAILY), prerequisite
 *         quests completed, required area unlocked, level reached, fewer than {@link #MAX_ACTIVE_QUESTS} active.</li>
 *     <li><b>Progress</b>: every matching event advances every active quest it matches; counters stop at the required
 *         amount. Events from before acceptance never count.</li>
 *     <li><b>Turn in</b>: only when every objective is complete; the reward is returned exactly once. Nothing is
 *         consumed (CollectItems counts pickups, see {@link Objective.CollectItems}).</li>
 *     <li><b>Dailies</b>: a DAILY can be turned in once per day index; a later day index starts a new period. Active
 *         dailies are kept across the reset.</li>
 * </ul>
 */
public final class QuestEngine {
    public static final int MAX_ACTIVE_QUESTS = 10;

    private final QuestCatalog catalog;

    public QuestEngine(QuestCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public QuestCatalog catalog() {
        return catalog;
    }

    /**
     * Quests the character could accept now, in catalog order. Does not change the state. The active-quest limit is not
     * applied here (the UI still lists the quests; {@link #accept} then reports {@link AcceptResult#TOO_MANY_ACTIVE}).
     */
    public List<QuestDefinition> available(QuestLogState state, QuestContext context) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(context, "context");
        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition quest : catalog.all()) {
            if (check(state, quest, context) == AcceptResult.SUCCESS) {
                result.add(quest);
            }
        }
        return List.copyOf(result);
    }

    /** What {@link #accept} would answer, without changing anything. */
    public AcceptResult canAccept(QuestLogState state, String questId, QuestContext context) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(context, "context");
        Optional<QuestDefinition> quest = catalog.byId(questId);
        if (quest.isEmpty()) {
            return AcceptResult.UNKNOWN_QUEST;
        }
        AcceptResult result = check(state, quest.get(), context);
        if (result == AcceptResult.SUCCESS && state.activeCount() >= MAX_ACTIVE_QUESTS) {
            return AcceptResult.TOO_MANY_ACTIVE;
        }
        return result;
    }

    /**
     * Accepts the quest if allowed. Starts the daily period of {@code context.dayIndex()} first. A
     * {@link Objective.ReachArea} for the area the player is standing in is completed immediately.
     */
    public AcceptResult accept(QuestLogState state, String questId, QuestContext context) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(context, "context");
        resetDailies(state, context.dayIndex());
        AcceptResult result = canAccept(state, questId, context);
        if (!result.isSuccess()) {
            return result;
        }
        QuestDefinition quest = catalog.byId(questId).orElseThrow();
        state.start(quest);
        if (context.currentAreaId() != 0) {
            advance(state, quest, new GameEvent.AreaEntered(context.currentAreaId()), new ArrayList<>());
        }
        return AcceptResult.SUCCESS;
    }

    /** Drops an active quest and its progress. @return {@code false} if it was not active */
    public boolean abandon(QuestLogState state, String questId) {
        Objects.requireNonNull(state, "state");
        return state.remove(questId);
    }

    /** Advances every active quest the event matches. @return what changed, in order (empty if nothing) */
    public List<QuestUpdate> onEvent(QuestLogState state, GameEvent event) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");
        List<QuestUpdate> updates = new ArrayList<>();
        for (String questId : state.activeQuestIds()) {
            Optional<QuestDefinition> quest = catalog.byId(questId);
            if (quest.isPresent()) {
                advance(state, quest.get(), event, updates);
            }
        }
        return List.copyOf(updates);
    }

    public boolean isReadyToTurnIn(QuestLogState state, String questId) {
        Objects.requireNonNull(state, "state");
        Optional<QuestDefinition> quest = catalog.byId(questId);
        return quest.isPresent() && state.isActive(questId) && quest.get().isComplete(state.progress(questId));
    }

    /**
     * Completes the quest if every objective is done. Starts the daily period of {@code dayIndex} first, so a daily is
     * always booked to the day it was turned in.
     *
     * @return the reward to grant, or empty if the quest is not active or not complete (nothing changes then)
     */
    public Optional<QuestReward> turnIn(QuestLogState state, String questId, long dayIndex) {
        Objects.requireNonNull(state, "state");
        resetDailies(state, dayIndex);
        if (!isReadyToTurnIn(state, questId)) {
            return Optional.empty();
        }
        QuestDefinition quest = catalog.byId(questId).orElseThrow();
        state.complete(quest);
        return Optional.of(quest.reward());
    }

    /**
     * The daily reset: if {@code dayIndex} is later than the character's current period, every daily becomes available
     * again. Idempotent; an earlier day index (clock moved back) changes nothing. Call it at login and when the day
     * changes so the reset is persisted; accept and turn-in apply it as well.
     *
     * @return {@code true} if a new daily period started
     */
    public boolean resetDailies(QuestLogState state, long dayIndex) {
        Objects.requireNonNull(state, "state");
        Check.range("dayIndex", dayIndex, 0L, Long.MAX_VALUE);
        return state.startDailyPeriod(dayIndex);
    }

    private AcceptResult check(QuestLogState state, QuestDefinition quest, QuestContext context) {
        if (state.isActive(quest.id())) {
            return AcceptResult.ALREADY_ACTIVE;
        }
        if (quest.kind().isRepeatable()) {
            if (doneInPeriod(state, quest.id(), context.dayIndex())) {
                return AcceptResult.DAILY_ALREADY_DONE;
            }
        } else if (state.hasCompleted(quest.id())) {
            return AcceptResult.ALREADY_COMPLETED;
        }
        for (String required : quest.prerequisites().questIds()) {
            if (!state.hasCompleted(required)) {
                return AcceptResult.PREREQUISITES_MISSING;
            }
        }
        if (!context.isAreaUnlocked(quest.prerequisites().requiredAreaId())) {
            return AcceptResult.AREA_LOCKED;
        }
        if (context.level() < quest.prerequisites().minLevel()) {
            return AcceptResult.LEVEL_TOO_LOW;
        }
        return AcceptResult.SUCCESS;
    }

    /** A daily turned in during the current period is done until a later day index starts the next period. */
    private static boolean doneInPeriod(QuestLogState state, String questId, long dayIndex) {
        return dayIndex <= state.lastDailyResetDay() && state.isDailyDone(questId);
    }

    private static void advance(QuestLogState state, QuestDefinition quest, GameEvent event, List<QuestUpdate> updates) {
        List<Integer> progress = state.progress(quest.id());
        if (quest.isComplete(progress)) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            int current = progress.get(i);
            if (current >= objective.required()) {
                continue;
            }
            int gain = objective.progressFrom(event);
            if (gain <= 0) {
                continue;
            }
            int next = (int) Math.min(objective.required(), (long) current + gain);
            state.setProgress(quest.id(), i, next);
            updates.add(new QuestUpdate.ProgressChanged(quest.id(), i, next, objective.required()));
            if (next >= objective.required()) {
                updates.add(new QuestUpdate.ObjectiveCompleted(quest.id(), i));
            }
            changed = true;
        }
        if (changed && quest.isComplete(state.progress(quest.id()))) {
            updates.add(new QuestUpdate.ReadyToTurnIn(quest.id()));
        }
    }
}
