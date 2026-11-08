package com.eternalrealm.social.forge;

import com.eternalrealm.job.JobType;
import net.minecraft.ChatFormatting;

/** A symbol and colour per job, shown before player names so jobs are recognisable at a glance. */
public final class JobEmblems {
    private JobEmblems() {
    }

    public static String symbol(JobType job) {
        return switch (job) {
            case KNIGHT -> "\u26E8";      // shield
            case ASSASSIN -> "\u2620";    // skull
            case MAGE -> "\u2726";        // star
            case RANGER -> "\u27B6";      // arrow
            case PRIEST -> "\u271A";      // cross
            case SPELLBLADE -> "\u2694";  // crossed swords
            case NONE -> "";
        };
    }

    public static ChatFormatting color(JobType job) {
        return switch (job) {
            case KNIGHT -> ChatFormatting.BLUE;
            case ASSASSIN -> ChatFormatting.DARK_RED;
            case MAGE -> ChatFormatting.LIGHT_PURPLE;
            case RANGER -> ChatFormatting.GREEN;
            case PRIEST -> ChatFormatting.GOLD;
            case SPELLBLADE -> ChatFormatting.AQUA;
            case NONE -> ChatFormatting.WHITE;
        };
    }
}
