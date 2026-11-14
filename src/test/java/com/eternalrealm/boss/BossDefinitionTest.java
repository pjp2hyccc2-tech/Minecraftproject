package com.eternalrealm.boss;

import org.junit.Test;

import java.util.List;

import static com.eternalrealm.boss.BossFixtures.ability;
import static com.eternalrealm.boss.BossFixtures.slam;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Validation of the immutable definitions: a broken definition must fail at startup, not during a fight. */
public class BossDefinitionTest {
    private static final BossAbility SLAM = slam("t_slam", 20, 10, 100);
    private static final BossAbility SWIPE =
            ability("t_swipe", new AbilityKind.Cone(5, 45), 12, 6, 60, 10, TargetRule.NEAREST);
    private static final BossAbility EXTRA =
            ability("t_extra", new AbilityKind.Ring(3, 8), 20, 10, 200, 10, TargetRule.NEAREST);

    @Test
    public void damagingAbilitiesMustBeTelegraphed() {
        assertThrows(IllegalArgumentException.class, () -> slam("t_fast", BossRules.MIN_TELEGRAPH_TICKS - 1, 0, 0));
        slam("t_ok", BossRules.MIN_TELEGRAPH_TICKS, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> ability("t_arena", new AbilityKind.ArenaWide(),
                BossRules.ARENA_WIDE_MIN_TELEGRAPH_TICKS - 1, 0, 0, 1, TargetRule.NEAREST));
        assertThrows(IllegalArgumentException.class, () -> ability("t_soak", new AbilityKind.Soak(4, 2),
                BossRules.SOAK_MIN_TELEGRAPH_TICKS - 1, 0, 0, 1, TargetRule.RANDOM));
        // non-damaging abilities may act instantly
        ability("t_buff", new AbilityKind.Buff(1.2, 100), 0, 0, 0, 1, TargetRule.NEAREST);
    }

    @Test
    public void damageMultiplierMatchesTheKind() {
        assertThrows(IllegalArgumentException.class, () -> new BossAbility("t_heal", new AbilityKind.Heal(0.1), 20,
                0, 100, 1.0, 1, TargetRule.NEAREST));
        assertThrows(IllegalArgumentException.class, () -> new BossAbility("t_slam2",
                new AbilityKind.CircleAroundBoss(4), 20, 0, 100, 0.0, 1, TargetRule.NEAREST));
        assertThrows(IllegalArgumentException.class, () -> new BossAbility("t_weight",
                new AbilityKind.CircleAroundBoss(4), 20, 0, 100, 1.0, 0, TargetRule.NEAREST));
        assertThrows(IllegalArgumentException.class, () -> new BossAbility("Bad-Id",
                new AbilityKind.CircleAroundBoss(4), 20, 0, 100, 1.0, 1, TargetRule.NEAREST));
        assertThrows(NullPointerException.class, () -> new BossAbility("t_rule",
                new AbilityKind.CircleAroundBoss(4), 20, 0, 100, 1.0, 1, null));
        assertEquals("boss_ability.eternalrealm.t_slam", SLAM.translationKey());
        assertEquals(30, SLAM.busyTicks());
    }

    @Test
    public void kindParametersAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.CircleAroundBoss(0.5));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.CircleAtTargets(3, 0));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Cone(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Line(1, 2));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Ring(5, 6));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Soak(4, 1));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.ProjectileVolley(0, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Summon("Bad Id", 2));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Shield("x_minion", 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Heal(0.0));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Buff(1.0, 100));
    }

    @Test
    public void defenseFlagsFollowTheKind() {
        AbilityKind arena = new AbilityKind.ArenaWide();
        assertTrue(arena.damaging() && arena.dodgeable() && !arena.guardable() && !arena.splitsDamage());
        AbilityKind soak = new AbilityKind.Soak(4, 2);
        assertTrue(soak.damaging() && !soak.dodgeable() && !soak.guardable() && soak.splitsDamage());
        AbilityKind cone = new AbilityKind.Cone(5, 30);
        assertTrue(cone.damaging() && cone.dodgeable() && cone.guardable() && !cone.splitsDamage());
        AbilityKind summon = new AbilityKind.Summon("x_minion", 2);
        assertFalse(summon.damaging() || summon.dodgeable() || summon.guardable());
        assertEquals(0, summon.minTelegraphTicks());
    }

    @Test
    public void phasesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new BossPhase(1.0, List.of(), 20, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BossPhase(1.0, List.of("a", "a"), 20, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BossPhase(0.0, List.of("a"), 20, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BossPhase(1.0, List.of("a"), 20, 0.0));
        assertThrows("openers must be part of the phase", IllegalArgumentException.class,
                () -> new BossPhase(1.0, List.of("a"), 20, 1.0, List.of("b")));
        assertEquals(List.of(), new BossPhase(1.0, List.of("a"), 20, 1.0).openerIds());
    }

    @Test
    public void definitionsRejectInconsistentPhasesAndReferences() {
        List<BossAbility> abilities = List.of(SLAM, SWIPE);
        BossPhase first = new BossPhase(1.0, List.of("t_slam"), 20, 1.0);
        BossPhase second = new BossPhase(0.5, List.of("t_slam", "t_swipe"), 20, 1.0);
        BossFixtures.boss(List.of(first, second), abilities, DifficultyTuning.NORMAL); // valid

        assertThrows("first phase must start at 1.0", IllegalArgumentException.class, () -> BossFixtures.boss(
                List.of(new BossPhase(0.9, List.of("t_slam", "t_swipe"), 20, 1.0)), abilities,
                DifficultyTuning.NORMAL));
        assertThrows("thresholds must decrease", IllegalArgumentException.class, () -> BossFixtures.boss(
                List.of(first, second, new BossPhase(0.6, List.of("t_slam"), 20, 1.0)), abilities,
                DifficultyTuning.NORMAL));
        assertThrows("unknown ability", IllegalArgumentException.class, () -> BossFixtures.boss(
                List.of(new BossPhase(1.0, List.of("t_slam", "t_nope"), 20, 1.0)), List.of(SLAM),
                DifficultyTuning.NORMAL));
        assertThrows("unused ability", IllegalArgumentException.class, () -> BossFixtures.boss(
                List.of(first), abilities, DifficultyTuning.NORMAL));
        assertThrows("duplicate ability", IllegalArgumentException.class, () -> BossFixtures.boss(
                List.of(first), List.of(SLAM, SLAM), DifficultyTuning.NORMAL));
        assertThrows("unknown EX ability", IllegalArgumentException.class, () -> BossFixtures.boss(
                List.of(first, second), abilities, DifficultyTuning.ex("t_nope")));
        assertThrows(IllegalArgumentException.class, () -> new BossDefinition("t", "zombie", 10, 1000, 10, 2, 0.5,
                1, 5, List.of(first, second), abilities, 6000, List.of(), DifficultyTuning.NORMAL,
                DifficultyTuning.NORMAL, DifficultyTuning.NORMAL));
    }

    @Test
    public void summonTemplatesMustMatchTheAbilities() {
        BossAbility summon = ability("t_call", new AbilityKind.Summon("t_minion", 2), 20, 0, 100, 1,
                TargetRule.NEAREST);
        List<BossPhase> phases = List.of(new BossPhase(1.0, List.of("t_slam", "t_call"), 20, 1.0));
        List<BossAbility> abilities = List.of(SLAM, summon);
        new BossDefinition("t", "minecraft:zombie", 10, 1000, 10, 2, 0.5, 1, 5, phases, abilities, 6000,
                List.of("t_minion"), DifficultyTuning.NORMAL, DifficultyTuning.NORMAL, DifficultyTuning.NORMAL);
        assertThrows(IllegalArgumentException.class, () -> new BossDefinition("t", "minecraft:zombie", 10, 1000, 10,
                2, 0.5, 1, 5, phases, abilities, 6000, List.of(), DifficultyTuning.NORMAL, DifficultyTuning.NORMAL,
                DifficultyTuning.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new BossDefinition("t", "minecraft:zombie", 10, 1000, 10,
                2, 0.5, 1, 5, phases, abilities, 6000, List.of("t_minion", "t_other"), DifficultyTuning.NORMAL,
                DifficultyTuning.NORMAL, DifficultyTuning.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> new BossDefinition("t", "minecraft:zombie", 10, 1000, 10,
                2, 0.5, 1, 5, phases, abilities, 6000, List.of("t_minion", "t_minion"), DifficultyTuning.NORMAL,
                DifficultyTuning.NORMAL, DifficultyTuning.NORMAL));
    }

    @Test
    public void healthScalesWithPartySizeAndDifficulty() {
        BossDefinition boss = BossFixtures.boss(List.of(new BossPhase(1.0, List.of("t_slam", "t_swipe"), 20, 1.0)),
                List.of(SLAM, SWIPE, EXTRA), DifficultyTuning.ex("t_extra"));
        assertEquals(1000.0, boss.maxHealth(1, Difficulty.NORMAL), 1e-9);
        assertEquals(1000.0 * (1 + 0.5 * 4), boss.maxHealth(5, Difficulty.NORMAL), 1e-9);
        assertEquals(1000.0 * 1.4, boss.maxHealth(1, Difficulty.HARD), 1e-9);
        assertEquals("EX: four times the health", 1000.0 * 2.0 * 4.0, boss.maxHealth(3, Difficulty.EX), 1e-9);
        assertEquals("EX: twice the damage", 10.0 * 2.0, boss.damage(Difficulty.EX), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> boss.maxHealth(0, Difficulty.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> boss.maxHealth(BossRules.MAX_PARTY_SIZE + 1,
                Difficulty.NORMAL));
        assertEquals("boss.eternalrealm.test_boss", boss.translationKey());
    }

    @Test
    public void exAddsItsAbilitiesToEveryPhase() {
        BossDefinition boss = BossFixtures.boss(List.of(new BossPhase(1.0, List.of("t_slam"), 20, 1.0),
                        new BossPhase(0.4, List.of("t_slam", "t_swipe"), 20, 1.0)),
                List.of(SLAM, SWIPE, EXTRA), DifficultyTuning.ex("t_extra"));
        assertEquals(List.of(SLAM), boss.abilitiesFor(0, Difficulty.NORMAL));
        assertEquals(List.of(SLAM), boss.abilitiesFor(0, Difficulty.HARD));
        assertEquals(List.of(SLAM, EXTRA), boss.abilitiesFor(0, Difficulty.EX));
        assertEquals(List.of(SLAM, SWIPE, EXTRA), boss.abilitiesFor(1, Difficulty.EX));
        assertThrows(IllegalArgumentException.class, () -> boss.abilitiesFor(2, Difficulty.NORMAL));
    }

    @Test
    public void phaseIndexFollowsTheThresholds() {
        BossDefinition boss = BossFixtures.boss(List.of(new BossPhase(1.0, List.of("t_slam"), 20, 1.0),
                        new BossPhase(0.6, List.of("t_slam"), 20, 1.0),
                        new BossPhase(0.3, List.of("t_slam", "t_swipe"), 20, 1.0)),
                List.of(SLAM, SWIPE), DifficultyTuning.NORMAL);
        assertEquals(0, boss.phaseIndexFor(1.0));
        assertEquals(0, boss.phaseIndexFor(0.61));
        assertEquals(1, boss.phaseIndexFor(0.6));
        assertEquals(1, boss.phaseIndexFor(0.31));
        assertEquals(2, boss.phaseIndexFor(0.3));
        assertEquals(2, boss.phaseIndexFor(0.0));
    }

    @Test
    public void difficultiesHaveStableIds() {
        assertEquals("normal", Difficulty.NORMAL.id());
        assertEquals("ex", Difficulty.EX.id());
        assertEquals("difficulty.eternalrealm.hard", Difficulty.HARD.translationKey());
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(difficulty, Difficulty.byId(difficulty.id()).orElseThrow());
        }
        assertFalse(Difficulty.byId("nightmare").isPresent());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyTuning(1, 1, 0.1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> DifficultyTuning.ex("a", "a"));
    }
}
