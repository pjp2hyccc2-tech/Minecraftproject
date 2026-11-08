package com.eternalrealm.ranking;

import java.util.Collection;
import java.util.Objects;

/**
 * Score formulas of the player categories (docs/modules/ranking.md). Pure functions of one player's
 * {@link SeasonStats} and the {@link RankingRules}: the score never depends on other players, so it can be
 * recomputed at any time (restart, rules change) with exactly the same result. All arithmetic saturates.
 *
 * <pre>
 * LEVEL       = totalExperience
 * COMBAT      = monstersKilled × monsterKillPoints + eliteKills × eliteKillPoints
 *               + floor(damage / damagePerPoint) + pvpWins × pvpWinPoints
 * EXPLORATION = areasDiscovered × areaPoints + floor(distanceTravelled / blocksPerPoint)
 * BOSS        = bossesKilled
 * CRAFTING    = itemsCrafted
 * PARTY       = partyDungeonClears
 * OVERALL     = Σ weight(c) × normalise(score(c), halfScore(c))   over LEVEL, COMBAT, EXPLORATION, BOSS, CRAFTING, PARTY
 * normalise(s, h) = floor(NORMALISED_MAX × s / (s + h))            in [0, NORMALISED_MAX]
 * </pre>
 *
 * <p>The normalisation bounds every category's share of OVERALL by its weight, so no single category can dominate
 * (grinding one category has diminishing returns), and it needs no knowledge of the other players.</p>
 */
public final class RankingCalculator {
    /** Upper bound of {@link #normalise(long, long)}: a category's full share of the overall score per weight point. */
    public static final long NORMALISED_MAX = 10_000;

    private RankingCalculator() {
    }

    /**
     * @param category a player category ({@link RankingCategory#isPlayerCategory()})
     * @throws IllegalArgumentException for GUILD (use {@link #guildScore(Collection)}) and BOSS_TIME_ATTACK (a time)
     */
    public static long score(RankingCategory category, SeasonStats stats, RankingRules rules) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(rules, "rules");
        return switch (category) {
            case OVERALL -> overall(stats, rules);
            case LEVEL -> stats.totalExperience();
            case COMBAT -> combat(stats, rules);
            case EXPLORATION -> exploration(stats, rules);
            case BOSS -> stats.bossesKilled();
            case CRAFTING -> stats.itemsCrafted();
            case PARTY -> stats.partyDungeonClears();
            case GUILD, BOSS_TIME_ATTACK ->
                    throw new IllegalArgumentException(category.id() + " is not a per-player score");
        };
    }

    /** GUILD score: the saturating sum of the members' guild contribution. */
    public static long guildScore(Collection<SeasonStats> members) {
        Objects.requireNonNull(members, "members");
        long total = 0;
        for (SeasonStats member : members) {
            total = Saturating.add(total, Objects.requireNonNull(member, "member").guildContribution());
        }
        return total;
    }

    /**
     * {@code floor(NORMALISED_MAX × score / (score + halfScore))}: 0 at 0, half of the maximum at {@code halfScore},
     * approaching (never exceeding) {@link #NORMALISED_MAX} for large scores. Exact integer arithmetic for every
     * realistic score; astronomically large scores (above {@code Long.MAX_VALUE / NORMALISED_MAX}) use double
     * precision, which is deterministic on every JVM.
     */
    public static long normalise(long score, long halfScore) {
        Check.nonNegative("score", score);
        Check.range("halfScore", halfScore, 1L, RankingRules.OverallWeight.MAX_HALF_SCORE);
        if (score <= Long.MAX_VALUE / NORMALISED_MAX) {
            return score * NORMALISED_MAX / (score + halfScore);
        }
        double fraction = (double) score / ((double) score + (double) halfScore);
        return Math.min(NORMALISED_MAX, (long) (fraction * NORMALISED_MAX));
    }

    private static long overall(SeasonStats stats, RankingRules rules) {
        long total = 0;
        for (RankingCategory component : RankingCategory.overallComponents()) {
            RankingRules.OverallWeight weight = rules.overallWeight(component);
            if (weight.weight() > 0) {
                total += weight.weight() * normalise(score(component, stats, rules), weight.halfScore());
            }
        }
        return total;
    }

    private static long combat(SeasonStats stats, RankingRules rules) {
        long score = Saturating.multiply(stats.monstersKilled(), rules.monsterKillPoints());
        score = Saturating.add(score, Saturating.multiply(stats.eliteKills(), rules.eliteKillPoints()));
        score = Saturating.add(score, per(stats.damageDealt(), rules.damagePerPoint() * 10L));
        return Saturating.add(score, Saturating.multiply(stats.pvpWins(), rules.pvpWinPoints()));
    }

    private static long exploration(SeasonStats stats, RankingRules rules) {
        long score = Saturating.multiply(stats.areasDiscovered(), rules.areaPoints());
        return Saturating.add(score, per(stats.distanceTravelled(), rules.blocksPerPoint()));
    }

    /** {@code floor(amount / unitsPerPoint)}, or 0 when the measure is disabled ({@code unitsPerPoint == 0}). */
    private static long per(long amount, long unitsPerPoint) {
        return unitsPerPoint == 0 ? 0 : amount / unitsPerPoint;
    }
}
