package com.eternalrealm.quest;

import java.util.List;

/**
 * Translation keys of the quest texts the Forge layer shows besides the quest/objective names
 * ({@link QuestDefinition#translationKey()}, {@link Objective#translationKey()}) and {@link AcceptResult#messageKey()}.
 */
public final class QuestMessages {
    /** Toast for {@link QuestUpdate.ProgressChanged}. Args: quest name, objective line. */
    public static final String PROGRESS = "message.eternalrealm.quest.progress";
    /** Toast for {@link QuestUpdate.ObjectiveCompleted}. Args: quest name, objective line. */
    public static final String OBJECTIVE_COMPLETE = "message.eternalrealm.quest.objective_complete";
    /** Toast for {@link QuestUpdate.ReadyToTurnIn}. Args: quest name. */
    public static final String READY_TO_TURN_IN = "message.eternalrealm.quest.ready_to_turn_in";
    /** After a successful turn-in. Args: quest name. */
    public static final String COMPLETED = "message.eternalrealm.quest.completed";
    /** Reward line after a turn-in. Args: EXP, gold. */
    public static final String REWARD = "message.eternalrealm.quest.reward";
    /** Turn-in refused because objectives are missing. Args: quest name. */
    public static final String NOT_READY = "message.eternalrealm.quest.not_ready";
    /** After abandoning. Args: quest name. */
    public static final String ABANDONED = "message.eternalrealm.quest.abandoned";
    /** When a new daily period started while the player is online. No args. */
    public static final String DAILY_RESET = "message.eternalrealm.quest.daily_reset";

    public static final List<String> ALL = List.of(PROGRESS, OBJECTIVE_COMPLETE, READY_TO_TURN_IN, COMPLETED, REWARD,
            NOT_READY, ABANDONED, DAILY_RESET);

    private QuestMessages() {
    }
}
