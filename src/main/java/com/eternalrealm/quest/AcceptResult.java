package com.eternalrealm.quest;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

/** Outcome of {@link QuestEngine#accept}. Failures are normal gameplay, never protocol violations. */
public enum AcceptResult {
    SUCCESS,
    UNKNOWN_QUEST,
    ALREADY_ACTIVE,
    /** A MAIN or SIDE quest can only be completed once per season. */
    ALREADY_COMPLETED,
    /** The daily was already turned in during the current day index. */
    DAILY_ALREADY_DONE,
    /** A required quest has not been completed yet. */
    PREREQUISITES_MISSING,
    /** The quest's area is not unlocked on the server yet. */
    AREA_LOCKED,
    LEVEL_TOO_LOW,
    /** {@link QuestEngine#MAX_ACTIVE_QUESTS} quests are active; abandon or turn in one first. */
    TOO_MANY_ACTIVE;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** Message for the player. Arg: the quest name (the requested id for {@link #UNKNOWN_QUEST}). */
    public String messageKey() {
        return "message." + ModInfo.MOD_ID + ".quest.accept." + name().toLowerCase(Locale.ROOT);
    }
}
