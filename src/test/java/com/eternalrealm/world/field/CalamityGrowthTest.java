package com.eternalrealm.world.field;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.item.Relics;
import com.eternalrealm.monster.MonsterScaling;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Owner's request of 2026-11-13: every field's 災厄 comes back one level higher each time it is subdued, and its relics
 * drop one kill in a hundred.
 */
public class CalamityGrowthTest {
    private static final double EPS = 1.0e-9;

    @Test
    public void everyDefeatAddsALevelUpToTheCap() {
        CalamityGrowth growth = new CalamityGrowth();
        assertEquals(15, growth.level(1, 15));
        growth.defeated(1);
        assertEquals(1, growth.defeats(1));
        assertEquals(16, growth.level(1, 15));
        assertEquals(20, growth.level(2, 20)); // other fields untouched
        for (int i = 0; i < 200; i++) {
            growth.defeated(1);
        }
        assertEquals(201, growth.defeats(1));
        assertEquals(CalamityGrowth.MAX_LEVEL, growth.level(1, 15));
        // unknown fields neither count nor grow
        growth.defeated(0);
        growth.defeated(FieldLayout.FIELD_COUNT + 1);
        assertEquals(0, growth.defeats(0));
        assertEquals(7, growth.level(99, 7));
        assertEquals(1, CalamityGrowth.levelAfter(0, 0));
        assertEquals(100, CalamityGrowth.levelAfter(55, Integer.MAX_VALUE));
    }

    @Test
    public void aGrownCalamityHasMoreHealthAndDamage() {
        assertEquals(1.0, CalamityGrowth.healthFactor(15, 15), EPS);
        assertEquals(1.0, CalamityGrowth.damageFactor(15, 15), EPS);
        double health = CalamityGrowth.healthFactor(15, 25);
        assertEquals(MonsterScaling.referenceLightHit(25) / MonsterScaling.referenceLightHit(15), health, EPS);
        assertTrue(health > 1.4 && health < 1.5);
        assertEquals(1.0 + 0.7 * (health - 1.0), CalamityGrowth.damageFactor(15, 25), EPS);
        // every level counts, and a level never shrinks the boss
        for (int level = 16; level <= 100; level++) {
            assertTrue(CalamityGrowth.healthFactor(15, level) > CalamityGrowth.healthFactor(15, level - 1));
        }
        assertEquals(1.0, CalamityGrowth.healthFactor(40, 30), EPS);
        // the meadow's calamity, beaten 85 times, fights at level 100 with about 4.7 times its health
        BossDefinition meadow = Bosses.byId("c_meadow").orElseThrow();
        int level = CalamityGrowth.levelAfter(meadow.level(), 85);
        assertEquals(100, level);
        BossDefinition grown = meadow.atLevel(level, CalamityGrowth.healthFactor(meadow.level(), level),
                CalamityGrowth.damageFactor(meadow.level(), level));
        assertEquals(100, grown.level());
        assertTrue(grown.maxHealth(1, Difficulty.NORMAL) > meadow.maxHealth(1, Difficulty.NORMAL) * 4.0);
        assertTrue(grown.damage(Difficulty.NORMAL) > meadow.damage(Difficulty.NORMAL) * 3.0);
        assertEquals(meadow.phases(), grown.phases());
        assertEquals(meadow.abilities(), grown.abilities());
        assertSame(meadow, meadow.atLevel(meadow.level(), 1.0, 1.0));
    }

    @Test
    public void growthSurvivesTheSaveAndBrokenDataStartsOver() {
        CalamityGrowth growth = new CalamityGrowth();
        growth.defeated(3);
        growth.defeated(3);
        growth.defeated(10);
        CalamityGrowth read = CalamityGrowth.fromJson(growth.toJson());
        assertEquals(2, read.defeats(3));
        assertEquals(1, read.defeats(10));
        assertEquals(0, read.defeats(1));
        assertEquals(0, CalamityGrowth.fromJson("{broken").defeats(3));
        assertEquals(0, CalamityGrowth.fromJson("").defeats(3));
        assertEquals(0, CalamityGrowth.fromJson("{\"defeats\":[-5]}").defeats(1));
    }

    @Test
    public void calamityRelicsDropOneKillInAHundred() {
        assertEquals(0.01, Relics.dropChance(Relics.SourceKind.CALAMITY), 0.0);
        assertEquals(0.001, Relics.dropChance(Relics.SourceKind.LORD), 0.0);
        assertEquals(0.001, Relics.dropChance(Relics.SourceKind.BOSS), 0.0);
        assertEquals(0.001, Relics.dropChance(Relics.SourceKind.WANDERER), 0.0);
        java.util.SplittableRandom random = new java.util.SplittableRandom(3);
        int hits = 0;
        int rolls = 200_000;
        for (int i = 0; i < rolls; i++) {
            hits += Relics.rolls(Relics.dropChance(Relics.SourceKind.CALAMITY), random) ? 1 : 0;
        }
        assertEquals(0.01, hits / (double) rolls, 0.001);
    }
}
