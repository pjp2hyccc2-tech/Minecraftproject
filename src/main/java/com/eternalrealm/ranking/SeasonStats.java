package com.eternalrealm.ranking;

import java.util.Objects;

/**
 * One player's statistics for one season: the input of every player ranking ({@link RankingCalculator}).
 *
 * <p>Every value is collected by the server from real play events; nothing here is ever taken from a client. The
 * counters ({@link SeasonCounter}) only grow during a season and saturate at {@link Long#MAX_VALUE}.
 * {@code level} and {@code totalExperience} mirror the character and are 0 until the first update.</p>
 *
 * <p>Immutable: created on the server thread by {@link SeasonStatsTracker#snapshot()} and safe to hand to the I/O
 * thread. Stored inside the character file and the season's ranking file with {@link SeasonStatsCodec}.</p>
 *
 * @param damageDealt       damage dealt to monsters, in tenths of a damage point
 * @param distanceTravelled blocks travelled
 * @param level             character level (set, not added)
 * @param totalExperience   EXP earned this season (set, not added); the {@link RankingCategory#LEVEL} score
 */
public record SeasonStats(long monstersKilled, long eliteKills, long bossesKilled, long damageDealt, long deaths,
                          long dungeonClears, long partyDungeonClears, long questsCompleted, long areasDiscovered,
                          long distanceTravelled, long itemsCrafted, long pvpWins, long pvpLosses,
                          long guildContribution, int level, long totalExperience) {
    public static final SeasonStats EMPTY = new SeasonStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public SeasonStats {
        long[] counters = {monstersKilled, eliteKills, bossesKilled, damageDealt, deaths, dungeonClears,
                partyDungeonClears, questsCompleted, areasDiscovered, distanceTravelled, itemsCrafted, pvpWins,
                pvpLosses, guildContribution};
        for (int i = 0; i < counters.length; i++) {
            Check.nonNegative(SeasonCounter.all().get(i).id(), counters[i]);
        }
        Check.nonNegative("level", level);
        Check.nonNegative("totalExperience", totalExperience);
    }

    public long get(SeasonCounter counter) {
        Objects.requireNonNull(counter, "counter");
        return switch (counter) {
            case MONSTERS_KILLED -> monstersKilled;
            case ELITE_KILLS -> eliteKills;
            case BOSSES_KILLED -> bossesKilled;
            case DAMAGE_DEALT -> damageDealt;
            case DEATHS -> deaths;
            case DUNGEON_CLEARS -> dungeonClears;
            case PARTY_DUNGEON_CLEARS -> partyDungeonClears;
            case QUESTS_COMPLETED -> questsCompleted;
            case AREAS_DISCOVERED -> areasDiscovered;
            case DISTANCE_TRAVELLED -> distanceTravelled;
            case ITEMS_CRAFTED -> itemsCrafted;
            case PVP_WINS -> pvpWins;
            case PVP_LOSSES -> pvpLosses;
            case GUILD_CONTRIBUTION -> guildContribution;
        };
    }

    /**
     * @param counters one value per {@link SeasonCounter}, indexed by ordinal
     */
    static SeasonStats of(long[] counters, int level, long totalExperience) {
        if (counters.length != SeasonCounter.all().size()) {
            throw new IllegalArgumentException("Expected " + SeasonCounter.all().size() + " counters, got " + counters.length);
        }
        return new SeasonStats(
                counters[SeasonCounter.MONSTERS_KILLED.ordinal()],
                counters[SeasonCounter.ELITE_KILLS.ordinal()],
                counters[SeasonCounter.BOSSES_KILLED.ordinal()],
                counters[SeasonCounter.DAMAGE_DEALT.ordinal()],
                counters[SeasonCounter.DEATHS.ordinal()],
                counters[SeasonCounter.DUNGEON_CLEARS.ordinal()],
                counters[SeasonCounter.PARTY_DUNGEON_CLEARS.ordinal()],
                counters[SeasonCounter.QUESTS_COMPLETED.ordinal()],
                counters[SeasonCounter.AREAS_DISCOVERED.ordinal()],
                counters[SeasonCounter.DISTANCE_TRAVELLED.ordinal()],
                counters[SeasonCounter.ITEMS_CRAFTED.ordinal()],
                counters[SeasonCounter.PVP_WINS.ordinal()],
                counters[SeasonCounter.PVP_LOSSES.ordinal()],
                counters[SeasonCounter.GUILD_CONTRIBUTION.ordinal()],
                level, totalExperience);
    }
}
