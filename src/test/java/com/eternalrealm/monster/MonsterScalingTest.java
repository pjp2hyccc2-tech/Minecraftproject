package com.eternalrealm.monster;

import com.eternalrealm.world.LevelRange;
import org.junit.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MonsterScalingTest {
    private static final double EPS = 1e-9;

    private static MonsterTemplate template(MonsterRole role, double health, double healthPerLevel, double armor,
                                            double armorPerLevel) {
        return new MonsterTemplate("test_" + role.id(), "minecraft:zombie", role, LevelRange.of(1, 50), health,
                healthPerLevel, 3.0, 0.2, armor, armorPerLevel, 1.1, 10.0, 2, 4, "none", Set.of(1),
                SpawnHints.helmet("minecraft:leather_helmet"));
    }

    @Test
    public void statsFollowTheDocumentedFormulas() {
        MonsterTemplate t = template(MonsterRole.NORMAL, 26.0, 2.0, 1.0, 0.5);
        MonsterStats level1 = MonsterScaling.stats(t, 1);
        assertEquals("twice the catalog's health", 52.0, level1.health(), EPS);
        assertEquals(3.0, level1.damage(), EPS);
        assertEquals(1.0, level1.armor(), EPS);
        assertEquals(1.1, level1.speed(), EPS);
        assertEquals(10, level1.exp());
        assertEquals("gold ×1.2: 2.4", 2, level1.goldMin());
        assertEquals("gold ×1.2: 4.8", 5, level1.goldMax());

        MonsterStats level11 = MonsterScaling.stats(t, 11);
        assertEquals(92.0, level11.health(), EPS);
        assertEquals(5.0, level11.damage(), EPS);
        assertEquals(6.0, level11.armor(), EPS);
        assertEquals(Math.round(10 * Math.pow(11, 1.2)), level11.exp());
        assertEquals("2 × 2 × 1.2", 5, level11.goldMin());
        assertEquals("4 × 2 × 1.2", 10, level11.goldMax());

        MonsterTemplate minion = template(MonsterRole.MINION, 26.0, 2.0, 1.0, 0.5);
        assertEquals("boss summons keep their health", 26.0, MonsterScaling.stats(minion, 1).health(), EPS);
    }

    @Test
    public void healthAndArmorAreCappedAtTheVanillaAttributeLimits() {
        MonsterTemplate t = template(MonsterRole.ELITE, 900.0, 50.0, 25.0, 5.0);
        MonsterStats stats = MonsterScaling.stats(t, 100);
        assertEquals(MonsterScaling.MAX_HEALTH, stats.health(), EPS);
        assertEquals(MonsterScaling.MAX_ARMOR, stats.armor(), EPS);
    }

    @Test
    public void everyTemplateIsMonotonicInLevelUpToTheDataModelCap() {
        for (MonsterTemplate t : Monsters.all()) {
            MonsterStats previous = MonsterScaling.stats(t, 1);
            for (int level = 2; level <= MonsterScaling.MAX_LEVEL; level++) {
                MonsterStats next = MonsterScaling.stats(t, level);
                String where = t.id() + " at " + level;
                assertTrue(where, next.health() >= previous.health());
                assertTrue(where, next.damage() >= previous.damage());
                assertTrue(where, next.armor() >= previous.armor());
                assertEquals(where, previous.speed(), next.speed(), EPS);
                assertTrue(where, next.exp() >= previous.exp());
                assertTrue(where, next.goldMin() >= previous.goldMin() && next.goldMax() >= previous.goldMax());
                previous = next;
            }
        }
    }

    @Test
    public void everyTemplateIsSaneAtLevelsOneAndFifty() {
        for (MonsterTemplate t : Monsters.all()) {
            for (int level : new int[]{1, 50}) {
                MonsterStats stats = MonsterScaling.stats(t, level);
                String where = t.id() + " at " + level;
                assertTrue(where, stats.health() >= 10 && stats.health() <= MonsterScaling.MAX_HEALTH);
                assertTrue(where, stats.damage() >= 1 && stats.damage() <= 40);
                assertTrue(where, stats.armor() <= 25);
                assertTrue(where, stats.speed() >= 0.8 && stats.speed() <= 1.25);
                assertTrue(where, stats.exp() >= 1 && stats.exp() <= 6000);
            }
        }
    }

    /** The catalog's 3-6, 9-18 and 1-4 hits, with field and dungeon monsters at twice their health (2026-11-14). */
    @Test
    public void levelMatchedPlayersNeedSixToTwelveHitsForNormalMonstersAndAboutThreeTimesForElites() {
        for (MonsterTemplate t : Monsters.all()) {
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (int level = t.levels().min(); level <= t.levels().max(); level++) {
                int hits = MonsterScaling.hitsToKill(MonsterScaling.stats(t, level), MonsterScaling.referenceLightHit(level));
                min = Math.min(min, hits);
                max = Math.max(max, hits);
            }
            String where = t.id() + " needs " + min + "-" + max + " hits";
            switch (t.role()) {
                case NORMAL -> assertTrue(where, min >= 6 && max <= 12);
                case ELITE -> assertTrue(where, min >= 18 && max <= 36);
                case MINION -> assertTrue(where, min >= 1 && max <= 4);
            }
        }
    }

    @Test
    public void theTuningMatchesTheBriefAtTheEndsOfTheLevelRange() {
        MonsterTemplate standard = Monsters.require("wandering_dead");
        assertEquals("level 1: 6.6 per light hit", 8,
                MonsterScaling.hitsToKill(MonsterScaling.stats(standard, 1), 6.6));
        MonsterStats level50 = MonsterScaling.stats(Monsters.require("void_wanderer"), 50);
        assertTrue(MonsterScaling.hitsToKill(level50, 30.0) <= 12);
        assertTrue(MonsterScaling.hitsToKill(level50, 60.0) >= 6);
        MonsterStats elite50 = MonsterScaling.stats(Monsters.require("herald_of_ruin"), 50);
        double ratio = (double) MonsterScaling.hitsToKill(elite50, 42.0) / MonsterScaling.hitsToKill(level50, 42.0);
        assertTrue("elites take about three times longer: " + ratio, ratio >= 2.5 && ratio <= 3.5);
    }

    @Test
    public void referenceHitAndArmorFollowTheVanillaRules() {
        assertEquals(6.6, MonsterScaling.referenceLightHit(1), EPS);
        assertEquals(42.0, MonsterScaling.referenceLightHit(50), EPS);
        for (int level = 1; level < 50; level++) {
            assertEquals("linear in level", (42.0 - 6.6) / 49,
                    MonsterScaling.referenceLightHit(level + 1) - MonsterScaling.referenceLightHit(level), EPS);
        }
        // Vanilla CombatRules.getDamageAfterAbsorb with toughness 0.
        assertEquals(10.0, MonsterScaling.damageAfterArmor(10.0, 0.0), EPS);
        assertEquals(10.0 * (1 - 5.0 / 25), MonsterScaling.damageAfterArmor(10.0, 10.0), EPS);
        assertEquals(4.0 * (1 - 8.0 / 25), MonsterScaling.damageAfterArmor(4.0, 10.0), EPS);
        assertEquals(1.0 * (1 - 20.0 / 25), MonsterScaling.damageAfterArmor(1.0, 30.0), EPS);
        MonsterStats stats = new MonsterStats(20.0, 3.0, 0.0, 1.0, 5, 0, 0);
        assertEquals(4, MonsterScaling.hitsToKill(stats, 6.6));
        assertEquals(1, MonsterScaling.hitsToKill(stats, 20.0));
    }

    @Test
    public void invalidInputsAreRejected() {
        MonsterTemplate t = Monsters.require("meadow_slime");
        assertThrows(IllegalArgumentException.class, () -> MonsterScaling.stats(t, 0));
        assertThrows(IllegalArgumentException.class, () -> MonsterScaling.stats(t, 101));
        assertThrows(NullPointerException.class, () -> MonsterScaling.stats(null, 1));
        assertThrows(IllegalArgumentException.class, () -> MonsterScaling.referenceLightHit(0));
        assertThrows(IllegalArgumentException.class, () -> MonsterScaling.hitsToKill(MonsterScaling.stats(t, 1), 0.0));
        assertThrows(IllegalArgumentException.class, () -> MonsterScaling.damageAfterArmor(Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new MonsterStats(0.5, 1, 0, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MonsterStats(10, 1, 0, 1, 0, 5, 4));
        assertThrows(IllegalArgumentException.class, () -> new MonsterStats(10, 1, 31, 1, 0, 0, 0));
    }

    @Test
    public void goldRollsStayInsideTheRange() {
        MonsterStats stats = new MonsterStats(10, 1, 0, 1, 0, 3, 6);
        Random random = new Random(7);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 500; i++) {
            long gold = stats.rollGold(random);
            assertTrue(gold >= 3 && gold <= 6);
            sawMin |= gold == 3;
            sawMax |= gold == 6;
        }
        assertTrue(sawMin && sawMax);
        assertEquals(0, new MonsterStats(10, 1, 0, 1, 0, 0, 0).rollGold(random));
    }
}
