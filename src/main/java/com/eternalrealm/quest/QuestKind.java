package com.eternalrealm.quest;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

public enum QuestKind {
    /** The story chain; each can be completed once per season. */
    MAIN,
    /** Optional quests; once per season. */
    SIDE,
    /** Repeatable once per day index ({@link DailyReset}). */
    DAILY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "quest_kind." + ModInfo.MOD_ID + "." + id();
    }

    public boolean isRepeatable() {
        return this == DAILY;
    }
}
