package com.eternalrealm.monster;

/**
 * Field monsters that stand idle with no player near think only every {@link #EVERY}th tick (the owner, 2026-11-13:
 * 「魔物の多い場所」を軽く、湧く数はそのまま). Idle means no target, not hurt, standing still on dry ground, not burning,
 * no effects, no path to walk and no attack under way (the game side, {@code MonsterEvents}, decides that); the skipped
 * ticks are whole ticks of the monster (thinking, moving, pushing), so an idle one looks exactly the same while nobody
 * is near. It wakes on the next tick something changes: a player within {@code performance.monsterAiThrottleRange}
 * blocks, a hit, a push, a stroll it decides on during one of its own ticks. Dungeon waves, bosses and monsters in a
 * level without players always run every tick. Pure.
 */
public final class MonsterThrottle {
    /** An idle monster with nobody near runs one tick in this many. */
    public static final int EVERY = 4;
    /** Ticks after appearing during which a monster always runs every tick (its arrival and first steps). */
    public static final int SETTLE_TICKS = 40;

    private MonsterThrottle() {
    }

    /** Whether this tick is one of the ticks an idle, lonely monster runs anyway (spread over the monsters by id). */
    public static boolean ownTick(int tickCount, int id) {
        return Math.floorMod(tickCount + id, EVERY) == 0;
    }

    /**
     * Whether a monster skips this tick.
     *
     * @param idle           the game's idle test (see the class description)
     * @param playersInLevel whether its level has players at all
     * @param nearestSqr     squared distance to the nearest player who is not a spectator
     * @param range          blocks within which a player counts as near
     */
    public static boolean skips(int tickCount, int id, boolean idle, boolean playersInLevel, double nearestSqr,
                                double range) {
        return idle && playersInLevel && tickCount >= SETTLE_TICKS && nearestSqr > range * range
                && !ownTick(tickCount, id);
    }
}
