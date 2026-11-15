package com.eternalrealm.guild.battle;

/**
 * The numbers of a guild battle (ギルド対抗戦, docs/modules/guild_battles.md). Times are in server ticks.
 *
 * @param maxPerSide       fighters a guild may send
 * @param minPerSide       fighters each side needs for the battle to start
 * @param challengeTicks   how long a challenge waits for an answer
 * @param musterTicks      how long the members of both guilds have to join after the challenge was accepted
 * @param prepareTicks     the countdown in the arena before the fight
 * @param fightTicks       the length of the fight
 * @param knockOutTarget   knock-outs that win at once
 * @param respawnTicks     how long a knocked-out fighter waits before standing again at their side's gate
 * @param shieldTicks      how long a fighter cannot be hurt after standing again
 * @param endTicks         the results stay in the arena this long before everyone goes home
 * @param cooldownTicks    a guild waits this long after a battle before the next challenge
 */
public record BattleRules(int maxPerSide, int minPerSide, long challengeTicks, long musterTicks, long prepareTicks,
                          long fightTicks, int knockOutTarget, long respawnTicks, long shieldTicks, long endTicks,
                          long cooldownTicks) {
    public static final int TICKS_PER_SECOND = 20;

    public BattleRules {
        if (minPerSide < 1 || maxPerSide < minPerSide || maxPerSide > 40) {
            throw new IllegalArgumentException("fighters per side out of range: " + minPerSide + ".." + maxPerSide);
        }
        if (challengeTicks < 1 || musterTicks < 1 || prepareTicks < 1 || fightTicks < 1 || endTicks < 0
                || respawnTicks < 0 || shieldTicks < 0 || cooldownTicks < 0) {
            throw new IllegalArgumentException("times must be positive");
        }
        if (knockOutTarget < 1) {
            throw new IllegalArgumentException("knockOutTarget must be positive: " + knockOutTarget);
        }
    }

    /**
     * Up to ten a side (one is enough to start), a challenge open for two minutes, 45 seconds to join, a 10 second
     * countdown, five minutes of fighting or 20 knock-outs, back on one's feet after 3 seconds with 3 seconds of
     * shelter, 8 seconds of results, two minutes before a guild fights again.
     */
    public static BattleRules defaults() {
        return new BattleRules(10, 1, seconds(120), seconds(45), seconds(10), seconds(300), 20, seconds(3),
                seconds(3), seconds(8), seconds(120));
    }

    public static long seconds(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }
}
