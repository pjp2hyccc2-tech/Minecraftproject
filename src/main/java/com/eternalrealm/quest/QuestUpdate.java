package com.eternalrealm.quest;

/**
 * A change caused by {@link QuestEngine#onEvent}, in the order it happened, for HUD toasts and syncing. Completing the
 * last objective of a quest produces {@link ProgressChanged}, {@link ObjectiveCompleted} and {@link ReadyToTurnIn}.
 */
public sealed interface QuestUpdate permits QuestUpdate.ProgressChanged, QuestUpdate.ObjectiveCompleted,
        QuestUpdate.ReadyToTurnIn {
    String questId();

    /** An objective's counter increased. */
    record ProgressChanged(String questId, int objectiveIndex, int progress, int required) implements QuestUpdate {
    }

    /** An objective reached its required count. */
    record ObjectiveCompleted(String questId, int objectiveIndex) implements QuestUpdate {
    }

    /** Every objective of the quest is complete; it can be turned in. */
    record ReadyToTurnIn(String questId) implements QuestUpdate {
    }
}
