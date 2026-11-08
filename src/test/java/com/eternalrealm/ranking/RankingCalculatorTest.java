package com.eternalrealm.ranking;

import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RankingCalculatorTest {
    private static final RankingRules RULES = RankingRules.defaults();

    @Test
    public void categoriesHaveStableIdsAndScopes() {
        List<String> ids = RankingCategory.all().stream().map(RankingCategory::id).toList();
        assertEquals(List.of("overall", "level", "combat", "exploration", "boss", "crafting", "guild", "party",
                "boss_time_attack"), ids);
        assertEquals(List.of(RankingCategory.OVERALL, RankingCategory.LEVEL, RankingCategory.COMBAT,
                RankingCategory.EXPLORATION, RankingCategory.BOSS, RankingCategory.CRAFTING, RankingCategory.PARTY),
                RankingCategory.playerCategories());
        assertEquals(List.of(RankingCategory.LEVEL, RankingCategory.COMBAT, RankingCategory.EXPLORATION,
                RankingCategory.BOSS, RankingCategory.CRAFTING, RankingCategory.PARTY),
                RankingCategory.overallComponents());
        assertEquals(RankingCategory.Scope.GUILD, RankingCategory.GUILD.scope());
        assertEquals(RankingCategory.Scope.TIME_ATTACK, RankingCategory.BOSS_TIME_ATTACK.scope());
        assertFalse(RankingCategory.BOSS_TIME_ATTACK.isScoreCategory());
        assertTrue(RankingCategory.GUILD.isScoreCategory());
        for (RankingCategory category : RankingCategory.all()) {
            assertEquals(category, RankingCategory.byId(category.id()).orElseThrow());
            assertEquals("ranking_category.eternalrealm." + category.id(), category.translationKey());
        }
        assertTrue(RankingCategory.byId("OVERALL").isEmpty());
    }

    @Test
    public void simpleCategoriesUseTheirCounter() {
        SeasonStats stats = RankingFixtures.distinctStats(10);
        assertEquals(stats.totalExperience(), RankingCalculator.score(RankingCategory.LEVEL, stats, RULES));
        assertEquals(stats.bossesKilled(), RankingCalculator.score(RankingCategory.BOSS, stats, RULES));
        assertEquals(stats.itemsCrafted(), RankingCalculator.score(RankingCategory.CRAFTING, stats, RULES));
        assertEquals(stats.partyDungeonClears(), RankingCalculator.score(RankingCategory.PARTY, stats, RULES));
    }

    @Test
    public void combatAndExplorationFollowTheDocumentedFormulas() {
        SeasonStats stats = RankingFixtures.statsWith(1, 0,
                SeasonCounter.MONSTERS_KILLED, 100, SeasonCounter.ELITE_KILLS, 10, SeasonCounter.DAMAGE_DEALT, 12_345,
                SeasonCounter.PVP_WINS, 3, SeasonCounter.PVP_LOSSES, 50, SeasonCounter.DEATHS, 9,
                SeasonCounter.AREAS_DISCOVERED, 7, SeasonCounter.DISTANCE_TRAVELLED, 1_234);
        // 100×10 + 10×50 + floor(1234.5 damage / 10) + 3×30; losses and deaths do not count
        assertEquals(1_713, RankingCalculator.score(RankingCategory.COMBAT, stats, RULES));
        // 7×100 + floor(1234 blocks / 100)
        assertEquals(712, RankingCalculator.score(RankingCategory.EXPLORATION, stats, RULES));
    }

    @Test
    public void zeroUnitsPerPointLeavesAMeasureOut() {
        RankingRules rules = new RankingRules(10, 50, 0, 30, 100, 0, RULES.overallWeights());
        SeasonStats stats = RankingFixtures.statsWith(1, 0, SeasonCounter.DAMAGE_DEALT, 1_000_000,
                SeasonCounter.DISTANCE_TRAVELLED, 1_000_000, SeasonCounter.AREAS_DISCOVERED, 1);
        assertEquals(0, RankingCalculator.score(RankingCategory.COMBAT, stats, rules));
        assertEquals(100, RankingCalculator.score(RankingCategory.EXPLORATION, stats, rules));
    }

    @Test
    public void scoresSaturateInsteadOfOverflowing() {
        SeasonStats extreme = RankingFixtures.statsWith(1, Long.MAX_VALUE,
                SeasonCounter.MONSTERS_KILLED, Long.MAX_VALUE, SeasonCounter.ELITE_KILLS, Long.MAX_VALUE,
                SeasonCounter.PVP_WINS, Long.MAX_VALUE, SeasonCounter.AREAS_DISCOVERED, Long.MAX_VALUE / 50,
                SeasonCounter.DISTANCE_TRAVELLED, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, RankingCalculator.score(RankingCategory.COMBAT, extreme, RULES));
        assertEquals(Long.MAX_VALUE, RankingCalculator.score(RankingCategory.EXPLORATION, extreme, RULES));
        long overall = RankingCalculator.score(RankingCategory.OVERALL, extreme, RULES);
        assertTrue("never negative: " + overall, overall > 0);
    }

    @Test
    public void normaliseIsBoundedMonotonicAndHalfAtTheHalfScore() {
        assertEquals(0, RankingCalculator.normalise(0, 7));
        assertEquals(5_000, RankingCalculator.normalise(7, 7));
        assertEquals(7_500, RankingCalculator.normalise(21, 7));
        assertEquals(3_333, RankingCalculator.normalise(1, 2));
        assertEquals(1, RankingCalculator.normalise(1, 9_999));
        long boundary = Long.MAX_VALUE / RankingCalculator.NORMALISED_MAX;
        // largest score of the exact branch with the largest half score (10^15): no overflow, exact floor
        assertEquals(4_797, RankingCalculator.normalise(boundary, RankingRules.OverallWeight.MAX_HALF_SCORE));
        long previous = -1;
        for (long score : new long[]{0, 1, 10, 1_000, boundary - 1, boundary, boundary + 1, Long.MAX_VALUE / 2,
                Long.MAX_VALUE}) {
            long value = RankingCalculator.normalise(score, 1_000_000);
            assertTrue("monotonic at " + score, value >= previous);
            assertTrue("bounded at " + score, value <= RankingCalculator.NORMALISED_MAX);
            previous = value;
        }
        assertEquals(RankingCalculator.NORMALISED_MAX, RankingCalculator.normalise(Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> RankingCalculator.normalise(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> RankingCalculator.normalise(1, 0));
        assertThrows(IllegalArgumentException.class, () -> RankingCalculator.normalise(1, Long.MAX_VALUE));
    }

    @Test
    public void overallIsTheWeightedSumOfNormalisedCategories() {
        SeasonStats stats = RankingFixtures.statsWith(30, 200_000,
                SeasonCounter.MONSTERS_KILLED, 100, SeasonCounter.ELITE_KILLS, 10, SeasonCounter.DAMAGE_DEALT, 12_345,
                SeasonCounter.PVP_WINS, 3, SeasonCounter.AREAS_DISCOVERED, 7, SeasonCounter.DISTANCE_TRAVELLED, 1_234);
        // LEVEL 2×5000 + COMBAT 2×floor(1713×10000/51713) + EXPLORATION 1×floor(712×10000/5712)
        assertEquals(2 * 5_000 + 2 * 331 + 1_246, RankingCalculator.score(RankingCategory.OVERALL, stats, RULES));
    }

    @Test
    public void noSingleCategoryCanDominateTheOverallScore() {
        SeasonStats grinder = RankingFixtures.statsWith(1, 0, SeasonCounter.MONSTERS_KILLED, 1_000_000_000_000L);
        SeasonStats balanced = RankingFixtures.statsWith(30, 200_000,
                SeasonCounter.MONSTERS_KILLED, 5_000, SeasonCounter.AREAS_DISCOVERED, 50,
                SeasonCounter.BOSSES_KILLED, 10, SeasonCounter.ITEMS_CRAFTED, 100, SeasonCounter.PARTY_DUNGEON_CLEARS, 10);
        long grinderScore = RankingCalculator.score(RankingCategory.OVERALL, grinder, RULES);
        assertEquals("capped by the COMBAT weight (2 × 10000)", 19_998, grinderScore);
        assertEquals("half of every category: 9 weight points × 5000", 45_000,
                RankingCalculator.score(RankingCategory.OVERALL, balanced, RULES));
        long ceiling = 0;
        for (RankingCategory component : RankingCategory.overallComponents()) {
            ceiling += RULES.overallWeight(component).weight() * RankingCalculator.NORMALISED_MAX;
        }
        SeasonStats everything = RankingFixtures.statsWith(100, Long.MAX_VALUE,
                SeasonCounter.MONSTERS_KILLED, Long.MAX_VALUE, SeasonCounter.AREAS_DISCOVERED, Long.MAX_VALUE,
                SeasonCounter.BOSSES_KILLED, Long.MAX_VALUE, SeasonCounter.ITEMS_CRAFTED, Long.MAX_VALUE,
                SeasonCounter.PARTY_DUNGEON_CLEARS, Long.MAX_VALUE);
        assertTrue(RankingCalculator.score(RankingCategory.OVERALL, everything, RULES) <= ceiling);
    }

    @Test
    public void zeroWeightLeavesACategoryOutOfTheOverallScore() {
        Map<RankingCategory, RankingRules.OverallWeight> weights = new EnumMap<>(RULES.overallWeights());
        weights.put(RankingCategory.LEVEL, new RankingRules.OverallWeight(0, 1));
        RankingRules rules = new RankingRules(10, 50, 10, 30, 100, 100, weights);
        SeasonStats levelOnly = RankingFixtures.statsWith(50, 10_000_000);
        assertEquals(0, RankingCalculator.score(RankingCategory.OVERALL, levelOnly, rules));
    }

    @Test
    public void guildScoreSumsMemberContributionAndSaturates() {
        SeasonStats a = RankingFixtures.statsWith(1, 0, SeasonCounter.GUILD_CONTRIBUTION, 40);
        SeasonStats b = RankingFixtures.statsWith(1, 0, SeasonCounter.GUILD_CONTRIBUTION, 2);
        assertEquals(42, RankingCalculator.guildScore(List.of(a, b, SeasonStats.EMPTY)));
        assertEquals(0, RankingCalculator.guildScore(List.of()));
        SeasonStats huge = RankingFixtures.statsWith(1, 0, SeasonCounter.GUILD_CONTRIBUTION, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, RankingCalculator.guildScore(List.of(huge, huge)));
    }

    @Test
    public void nonPlayerCategoriesHaveNoPlayerScore() {
        assertThrows(IllegalArgumentException.class,
                () -> RankingCalculator.score(RankingCategory.GUILD, SeasonStats.EMPTY, RULES));
        assertThrows(IllegalArgumentException.class,
                () -> RankingCalculator.score(RankingCategory.BOSS_TIME_ATTACK, SeasonStats.EMPTY, RULES));
    }

    @Test
    public void invalidRulesAreRejected() {
        Map<RankingCategory, RankingRules.OverallWeight> weights = RULES.overallWeights();
        assertThrows(IllegalArgumentException.class, () -> new RankingRules(-1, 50, 10, 30, 100, 100, weights));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingRules(10, RankingRules.MAX_POINTS + 1, 10, 30, 100, 100, weights));
        assertThrows(IllegalArgumentException.class, () -> new RankingRules(10, 50, -1, 30, 100, 100, weights));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingRules(10, 50, 10, 30, 100, RankingRules.MAX_UNITS_PER_POINT + 1, weights));

        Map<RankingCategory, RankingRules.OverallWeight> missing = new EnumMap<>(weights);
        missing.remove(RankingCategory.PARTY);
        assertThrows(IllegalArgumentException.class, () -> new RankingRules(10, 50, 10, 30, 100, 100, missing));
        for (RankingCategory notAComponent : List.of(RankingCategory.OVERALL, RankingCategory.GUILD,
                RankingCategory.BOSS_TIME_ATTACK)) {
            Map<RankingCategory, RankingRules.OverallWeight> extra = new EnumMap<>(weights);
            extra.put(notAComponent, new RankingRules.OverallWeight(1, 1));
            assertThrows(notAComponent.id(), IllegalArgumentException.class,
                    () -> new RankingRules(10, 50, 10, 30, 100, 100, extra));
        }
        Map<RankingCategory, RankingRules.OverallWeight> allZero = new EnumMap<>(RankingCategory.class);
        for (RankingCategory component : RankingCategory.overallComponents()) {
            allZero.put(component, new RankingRules.OverallWeight(0, 10));
        }
        assertThrows(IllegalArgumentException.class, () -> new RankingRules(10, 50, 10, 30, 100, 100, allZero));
        Map<RankingCategory, RankingRules.OverallWeight> nullWeight = new HashMap<>(weights);
        nullWeight.put(RankingCategory.LEVEL, null);
        assertThrows(NullPointerException.class, () -> new RankingRules(10, 50, 10, 30, 100, 100, nullWeight));

        assertThrows(IllegalArgumentException.class, () -> new RankingRules.OverallWeight(-1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new RankingRules.OverallWeight(RankingRules.OverallWeight.MAX_WEIGHT + 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new RankingRules.OverallWeight(1, 0));
        assertThrows(IllegalArgumentException.class, () -> RULES.overallWeight(RankingCategory.OVERALL));
    }

    @Test
    public void rulesAreImmutableCopies() {
        Map<RankingCategory, RankingRules.OverallWeight> weights = new EnumMap<>(RULES.overallWeights());
        RankingRules rules = new RankingRules(1, 2, 3, 4, 5, 6, weights);
        weights.put(RankingCategory.LEVEL, new RankingRules.OverallWeight(999, 1));
        assertEquals(RULES.overallWeight(RankingCategory.LEVEL), rules.overallWeight(RankingCategory.LEVEL));
        assertThrows(UnsupportedOperationException.class,
                () -> rules.overallWeights().put(RankingCategory.LEVEL, new RankingRules.OverallWeight(1, 1)));
        assertEquals(RankingRules.defaults(), RankingRules.defaults());
    }
}
