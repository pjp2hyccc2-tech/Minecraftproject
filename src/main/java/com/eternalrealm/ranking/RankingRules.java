package com.eternalrealm.ranking;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-configured weights that turn {@link SeasonStats} into ranking scores (formulas: {@link RankingCalculator}).
 * Immutable; replaced as a whole when the config is reloaded, after which the board recomputes every score
 * ({@link RankingBoard#setRules(RankingRules)}), because scores are always derived from the stats.
 *
 * <p>Points are integers so that scores are exact; the two continuous measures (damage, distance) are given as
 * "units per point" instead, where {@code 0} means the measure does not count.</p>
 *
 * @param monsterKillPoints COMBAT points per monster kill (elites included)
 * @param eliteKillPoints   extra COMBAT points per elite kill (on top of the monster kill)
 * @param damagePerPoint    damage points per COMBAT point; 0 = damage does not count
 * @param pvpWinPoints      COMBAT points per PvP win
 * @param areaPoints        EXPLORATION points per discovered area
 * @param blocksPerPoint    blocks travelled per EXPLORATION point; 0 = distance does not count
 * @param overallWeights    one entry for every {@link RankingCategory#overallComponents() overall component}
 */
public record RankingRules(int monsterKillPoints, int eliteKillPoints, int damagePerPoint, int pvpWinPoints,
                           int areaPoints, int blocksPerPoint, Map<RankingCategory, OverallWeight> overallWeights) {
    public static final int MAX_POINTS = 1_000_000;
    public static final int MAX_UNITS_PER_POINT = 1_000_000_000;

    public RankingRules {
        Check.range("monsterKillPoints", monsterKillPoints, 0, MAX_POINTS);
        Check.range("eliteKillPoints", eliteKillPoints, 0, MAX_POINTS);
        Check.range("damagePerPoint", damagePerPoint, 0, MAX_UNITS_PER_POINT);
        Check.range("pvpWinPoints", pvpWinPoints, 0, MAX_POINTS);
        Check.range("areaPoints", areaPoints, 0, MAX_POINTS);
        Check.range("blocksPerPoint", blocksPerPoint, 0, MAX_UNITS_PER_POINT);
        Objects.requireNonNull(overallWeights, "overallWeights");
        Map<RankingCategory, OverallWeight> copy = new EnumMap<>(RankingCategory.class);
        for (Map.Entry<RankingCategory, OverallWeight> entry : overallWeights.entrySet()) {
            RankingCategory category = Objects.requireNonNull(entry.getKey(), "overall weight category");
            if (!category.isOverallComponent()) {
                throw new IllegalArgumentException(category.id() + " cannot be part of the overall score");
            }
            copy.put(category, Objects.requireNonNull(entry.getValue(), "overall weight of " + category.id()));
        }
        long totalWeight = 0;
        for (RankingCategory component : RankingCategory.overallComponents()) {
            OverallWeight weight = copy.get(component);
            if (weight == null) {
                throw new IllegalArgumentException("Missing overall weight for " + component.id());
            }
            totalWeight += weight.weight();
        }
        if (totalWeight == 0) {
            throw new IllegalArgumentException("At least one overall weight must be positive");
        }
        overallWeights = Collections.unmodifiableMap(copy);
    }

    /**
     * Initial balance for a 14-day season (docs/modules/ranking.md). Half scores are roughly what a committed player
     * reaches by the end of a season, e.g. 200,000 season EXP is about level 30 on the default EXP curve.
     */
    public static RankingRules defaults() {
        Map<RankingCategory, OverallWeight> overall = new EnumMap<>(RankingCategory.class);
        overall.put(RankingCategory.LEVEL, new OverallWeight(2, 200_000));
        overall.put(RankingCategory.COMBAT, new OverallWeight(2, 50_000));
        overall.put(RankingCategory.EXPLORATION, new OverallWeight(1, 5_000));
        overall.put(RankingCategory.BOSS, new OverallWeight(2, 10));
        overall.put(RankingCategory.CRAFTING, new OverallWeight(1, 100));
        overall.put(RankingCategory.PARTY, new OverallWeight(1, 10));
        return new RankingRules(10, 50, 10, 30, 100, 100, overall);
    }

    public OverallWeight overallWeight(RankingCategory component) {
        OverallWeight weight = overallWeights.get(Objects.requireNonNull(component, "component"));
        if (weight == null) {
            throw new IllegalArgumentException(component.id() + " is not part of the overall score");
        }
        return weight;
    }

    /**
     * How one category enters the overall score: {@code weight × normalise(score, halfScore)}, see
     * {@link RankingCalculator#normalise(long, long)}.
     *
     * @param weight    multiplier, 0 to leave the category out of the overall score
     * @param halfScore category score that earns half of {@link RankingCalculator#NORMALISED_MAX}
     */
    public record OverallWeight(int weight, long halfScore) {
        public static final int MAX_WEIGHT = 1_000;
        public static final long MAX_HALF_SCORE = 1_000_000_000_000_000L;

        public OverallWeight {
            Check.range("weight", weight, 0, MAX_WEIGHT);
            Check.range("halfScore", halfScore, 1L, MAX_HALF_SCORE);
        }
    }
}
