package com.eternalrealm.ranking;

import java.util.Objects;

/**
 * Mutable season statistics of one player, fed by server-side play events (kills, damage, clears, ...).
 *
 * <p><b>Threading:</b> confined to the server thread, like the character it belongs to. Persistence and the ranking
 * board receive immutable {@link SeasonStats} from {@link #snapshot()}.</p>
 *
 * <p>Increments saturate at {@link Long#MAX_VALUE} instead of overflowing, and negative increments are rejected with
 * {@link IllegalArgumentException}: a negative value can only come from a bug, and silently accepting it would let
 * that bug lower somebody's ranking. Every change increments {@link #revision()}, so callers can push the stats to the
 * ranking board only when something changed (for example once per second instead of once per damage event).</p>
 */
public final class SeasonStatsTracker {
    /** A dungeon clear also counts as a party clear from this many members on. */
    public static final int PARTY_MIN_MEMBERS = 2;

    private final long[] counters = new long[SeasonCounter.all().size()];
    private int level;
    private long totalExperience;
    private long revision;

    /** Empty statistics (a new season character). */
    public SeasonStatsTracker() {
    }

    /** Continues from stored statistics (loaded with the character). */
    public SeasonStatsTracker(SeasonStats initial) {
        Objects.requireNonNull(initial, "initial");
        for (SeasonCounter counter : SeasonCounter.all()) {
            counters[counter.ordinal()] = initial.get(counter);
        }
        level = initial.level();
        totalExperience = initial.totalExperience();
    }

    /**
     * Adds {@code amount} to a counter, saturating at {@link Long#MAX_VALUE}. Prefer the specific methods below where
     * one exists: they keep related counters consistent (e.g. an elite kill is also a monster kill).
     *
     * @param amount must be {@code >= 0}; 0 changes nothing
     * @return the new value of the counter
     */
    public long add(SeasonCounter counter, long amount) {
        Objects.requireNonNull(counter, "counter");
        if (amount < 0) {
            throw new IllegalArgumentException("Negative increment of " + counter.id() + ": " + amount);
        }
        int index = counter.ordinal();
        long updated = Saturating.add(counters[index], amount);
        if (updated != counters[index]) {
            counters[index] = updated;
            revision++;
        }
        return updated;
    }

    /** The player got the kill credit for a monster (not a boss: see {@link SeasonCounter#BOSSES_KILLED}). */
    public void recordMonsterKill(boolean elite) {
        add(SeasonCounter.MONSTERS_KILLED, 1);
        if (elite) {
            add(SeasonCounter.ELITE_KILLS, 1);
        }
    }

    /**
     * Damage the player dealt to a monster, as applied by the server (after reductions).
     *
     * @param damage damage points, finite and {@code >= 0}; stored rounded to tenths
     * @return the tenths that were added
     */
    public long addDamageDealt(double damage) {
        if (!Double.isFinite(damage) || damage < 0.0) {
            throw new IllegalArgumentException("damage must be finite and >= 0: " + damage);
        }
        long tenths = Math.round(damage * 10.0);
        add(SeasonCounter.DAMAGE_DEALT, tenths);
        return tenths;
    }

    /**
     * The player cleared a dungeon in a group of {@code partySize} (including the player). Counts a party clear too
     * when the group had at least {@link #PARTY_MIN_MEMBERS} members.
     */
    public void recordDungeonClear(int partySize) {
        if (partySize < 1) {
            throw new IllegalArgumentException("partySize must be >= 1: " + partySize);
        }
        add(SeasonCounter.DUNGEON_CLEARS, 1);
        if (partySize >= PARTY_MIN_MEMBERS) {
            add(SeasonCounter.PARTY_DUNGEON_CLEARS, 1);
        }
    }

    public void recordPvpResult(boolean won) {
        add(won ? SeasonCounter.PVP_WINS : SeasonCounter.PVP_LOSSES, 1);
    }

    /**
     * Mirrors the character's level and season EXP (call after every EXP gain or level change).
     *
     * @return {@code true} if a value changed
     */
    public boolean setProgress(int level, long totalExperience) {
        Check.nonNegative("level", level);
        Check.nonNegative("totalExperience", totalExperience);
        if (level == this.level && totalExperience == this.totalExperience) {
            return false;
        }
        this.level = level;
        this.totalExperience = totalExperience;
        revision++;
        return true;
    }

    public long get(SeasonCounter counter) {
        return counters[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    public int level() {
        return level;
    }

    public long totalExperience() {
        return totalExperience;
    }

    public SeasonStats snapshot() {
        return SeasonStats.of(counters.clone(), level, totalExperience);
    }

    /** Incremented by every change; never by a call that changes nothing. */
    public long revision() {
        return revision;
    }
}
