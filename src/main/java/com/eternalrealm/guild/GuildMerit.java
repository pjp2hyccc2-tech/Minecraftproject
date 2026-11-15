package com.eternalrealm.guild;

import java.util.Locale;

/**
 * What a guild earns its merit (ギルド経験値) with: the deeds of its members, its battles and its fund
 * (docs/modules/guild_levels.md). The merit adds up per season and over all seasons; the total decides the guild's
 * level ({@link GuildRules}). Pure.
 *
 * <pre>
 * a monster defeated          1 per member who shared its experience (an elite 3)
 * a quest turned in           15
 * a dungeon cleared           NORMAL 50, HARD 80, EX 120 per member; the final raid 300
 * a lord (ヌシ) subdued        30 per rewarded member
 * a wanderer (放浪する大物)     80 per rewarded member
 * a calamity (災厄) subdued    150 per rewarded member
 * the house fund              1 per 100 G given
 * a guild battle              win 500, draw 250, loss 150 (for the guild)
 * </pre>
 */
public final class GuildMerit {
    public static final long MONSTER = 1;
    public static final long ELITE = 3;
    public static final long QUEST = 15;
    public static final long DUNGEON_NORMAL = 50;
    public static final long DUNGEON_HARD = 80;
    public static final long DUNGEON_EX = 120;
    public static final long RAID = 300;
    public static final long LORD = 30;
    public static final long WANDERER = 80;
    public static final long CALAMITY = 150;
    /** Gold given to the house fund per merit point. */
    public static final long GOLD_PER_POINT = 100;
    public static final long BATTLE_WIN = 500;
    public static final long BATTLE_DRAW = 250;
    public static final long BATTLE_LOSS = 150;

    private GuildMerit() {
    }

    /** A monster defeated (for each member who shared its experience). */
    public static long monster(boolean elite) {
        return elite ? ELITE : MONSTER;
    }

    /** A dungeon cleared on {@code difficulty} ("normal", "hard", "ex"; any case), or the final raid. */
    public static long dungeon(String difficulty, boolean raid) {
        if (raid) {
            return RAID;
        }
        return switch (difficulty == null ? "" : difficulty.toLowerCase(Locale.ROOT)) {
            case "hard" -> DUNGEON_HARD;
            case "ex" -> DUNGEON_EX;
            default -> DUNGEON_NORMAL;
        };
    }

    /** Gold given to the house fund (whole points; small gifts round down to nothing). */
    public static long donation(long gold) {
        return Math.max(0L, gold) / GOLD_PER_POINT;
    }

    /** A guild battle's result for one side. */
    public static long battle(int ownScore, int otherScore) {
        return ownScore > otherScore ? BATTLE_WIN : ownScore == otherScore ? BATTLE_DRAW : BATTLE_LOSS;
    }
}
