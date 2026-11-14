package com.eternalrealm.world.field;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.FieldBosses;
import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ヌシ and 災厄 are sized for five players of their level and rise to the level of a stronger player (2026-11-14:
 * 「(レベル50のプレイヤー)1人でも倒せるけど5人くらい集まらないと倒せないレベルにして」).
 */
public class ThreatPowerTest {
    @Test
    public void theyRiseToTheStrongestPlayerButNeverSink() {
        assertEquals(50, ThreatPower.level(17, 50));
        assertEquals(17, ThreatPower.level(17, 10));
        assertEquals(17, ThreatPower.level(17, 0));
        assertEquals(100, ThreatPower.level(57, 250));
        assertEquals(5, ThreatPower.SIZED_FOR);
    }

    @Test
    public void aLevelFiftyLordTakesFiveAboutFiveMinutesAndOneAboutTwentyFive() {
        double health = ThreatPower.lordHealth(50);
        assertEquals(42.0 * 1.5 * 5 * 240, health, 1e-6);
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            MonsterTemplate template = Monsters.require(lord.template());
            double armor = MonsterScaling.stats(template, 50).armor();
            double five = ThreatPower.seconds(health, armor, 50, 5);
            double one = ThreatPower.seconds(health, armor, 50, 1);
            assertTrue(lord.id() + ": five players need " + five + " s", five >= 240 && five <= 360);
            assertTrue(lord.id() + ": one alone " + one + " s", one >= 1200 && one <= 1800);
        }
        // at the level of its field the same: the fight keeps pace with the players
        double low = ThreatPower.seconds(ThreatPower.lordHealth(17), 6, 17, 5);
        assertTrue("a level-17 lord for five level-17 players: " + low, low >= 220 && low <= 330);
    }

    @Test
    public void calamitiesAreSizedForFiveAtTheLevelOfTheStrongest() {
        for (BossDefinition calamity : FieldBosses.CALAMITIES) {
            assertEquals(calamity.id(), ThreatPower.SIZED_FOR, calamity.minPlayers());
            int level = ThreatPower.level(calamity.level(), 50);
            BossDefinition synced = calamity.atLevel(level, CalamityGrowth.healthFactor(calamity.level(), level),
                    CalamityGrowth.damageFactor(calamity.level(), level));
            double health = synced.maxHealth(ThreatPower.SIZED_FOR, Difficulty.NORMAL);
            double five = ThreatPower.seconds(health, synced.armor(), level, 5);
            double one = ThreatPower.seconds(health, synced.armor(), level, 1);
            assertTrue(calamity.id() + " for five level-50 players: " + five + " s", five >= 240 && five <= 480);
            assertTrue(calamity.id() + ": one alone " + one + " s", one >= 5 * five * 0.99);
            assertTrue(calamity.id() + ": twelve moves, an extra one on EX and the roar",
                    calamity.abilities().size() == 14);
            assertEquals(12, calamity.phases().get(2).abilityIds().size());
        }
    }
}
