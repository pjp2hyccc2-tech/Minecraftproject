package com.eternalrealm.boss;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Rules for the shipped bosses: ids that other modules rely on, fairness rules and the balance target. */
public class BossesContentTest {
    private static final Map<String, Integer> LEVELS = Map.of("b1", 6, "b2", 13, "b3", 20, "b4", 26, "b5", 29,
            "b6", 35, "b7", 42, "b8", 47, "final", 50);
    private static final Set<String> VANILLA_BASES = Set.of("minecraft:ravager", "minecraft:iron_golem",
            "minecraft:wither_skeleton", "minecraft:evoker", "minecraft:warden", "minecraft:piglin_brute",
            "minecraft:elder_guardian", "minecraft:blaze", "minecraft:vindicator");

    @Test
    public void idsLevelsAndTemplatesLineUpWithTheOtherModules() {
        List<String> ids = Bosses.all().stream().map(BossDefinition::id).toList();
        assertEquals(List.of("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "final"), ids);
        for (BossDefinition boss : Bosses.all()) {
            assertEquals(boss.id(), LEVELS.get(boss.id()).intValue(), boss.level());
            assertEquals(boss, Bosses.byId(boss.id()).orElseThrow());
            assertEquals("boss.eternalrealm." + boss.id(), boss.translationKey());
            assertTrue(boss.entityType(), VANILLA_BASES.contains(boss.entityType()));
            assertEquals(List.of(boss.id() + "_minion"), boss.summonTemplateIds());
        }
        assertFalse(Bosses.byId("b9").isPresent());
        assertEquals("b3_minion", Bosses.minionTemplateId("b3"));
    }

    @Test
    public void abilityIdsAreGloballyUniqueAndPrefixedWithTheirBoss() {
        Set<String> seen = new HashSet<>();
        for (BossDefinition boss : Bosses.all()) {
            for (BossAbility ability : boss.abilities()) {
                assertTrue(ability.id(), ability.id().startsWith(boss.id() + "_"));
                assertTrue("duplicate " + ability.id(), seen.add(ability.id()));
            }
        }
    }

    @Test
    public void bigHitsGetLongTelegraphs() {
        for (BossDefinition boss : Bosses.all()) {
            for (BossAbility ability : boss.abilities()) {
                if (ability.kind().damaging()) {
                    assertTrue(ability.id(), ability.telegraphTicks() >= BossRules.MIN_TELEGRAPH_TICKS);
                }
                if (ability.damageMultiplier() >= 2.0 && !ability.kind().splitsDamage()) {
                    assertTrue(ability.id() + " hurts a lot: at least one second of warning",
                            ability.telegraphTicks() >= 20);
                }
            }
        }
    }

    @Test
    public void harderDifficultiesAreStrongerAndExAddsAMechanic() {
        for (BossDefinition boss : Bosses.all()) {
            assertEquals(DifficultyTuning.NORMAL, boss.normal());
            assertTrue(boss.hard().healthMultiplier() > 1.0 && boss.hard().damageMultiplier() > 1.0);
            assertTrue(boss.ex().healthMultiplier() > boss.hard().healthMultiplier());
            assertTrue(boss.ex().damageMultiplier() > boss.hard().damageMultiplier());
            assertTrue(boss.ex().cooldownMultiplier() < 1.0);
            assertFalse(boss.id() + ": EX must add an ability", boss.ex().extraAbilityIds().isEmpty());
            for (String extra : boss.ex().extraAbilityIds()) {
                for (BossPhase phase : boss.phases()) {
                    assertFalse(extra + " must be new in EX", phase.abilityIds().contains(extra));
                }
            }
            for (int i = 0; i < boss.phases().size(); i++) {
                assertTrue(boss.abilitiesFor(i, Difficulty.EX).size() > boss.abilitiesFor(i, Difficulty.NORMAL).size());
            }
        }
    }

    @Test
    public void regularBossesAreForPartiesAndHaveSeveralPhases() {
        for (BossDefinition boss : Bosses.all()) {
            if (boss == Bosses.FINAL) {
                continue;
            }
            assertEquals(1, boss.minPlayers());
            assertEquals(5, boss.maxPlayers());
            assertTrue(boss.phases().size() >= 2);
            for (BossAbility ability : boss.abilitiesFor(0, Difficulty.NORMAL)) {
                assertFalse(boss.id() + ": no soak in the first phase", ability.kind().splitsDamage());
            }
        }
    }

    @Test
    public void theFinalBossNeedsTheWholeRaid() {
        BossDefinition king = Bosses.FINAL;
        assertEquals(4, king.phases().size());
        assertEquals(10, king.minPlayers());
        assertEquals(20, king.maxPlayers());
        assertEquals("minecraft:wither_skeleton", king.entityType());
        boolean soak = false;
        boolean shieldWithAdds = false;
        for (BossAbility ability : king.abilities()) {
            if (ability.kind() instanceof AbilityKind.Soak s && s.minPlayers() >= 4) {
                soak = true;
            }
            if (ability.kind() instanceof AbilityKind.Shield s && s.addCount() >= 4) {
                shieldWithAdds = true;
            }
        }
        assertTrue("a soak that needs several players", soak);
        assertTrue("adds that must be killed to drop a shield", shieldWithAdds);
        for (BossPhase phase : king.phases()) {
            BossAbility opener = king.ability(phase.openerIds().get(0)).orElseThrow();
            assertTrue("every phase opens with an arena-wide attack: " + opener.id(),
                    opener.kind() instanceof AbilityKind.ArenaWide);
            assertTrue(phase.abilityIds().stream().anyMatch(id -> id.equals(opener.id())));
        }
    }

    // ------------------------------------------------------------------ patterns & forms

    private static boolean isPattern(AbilityKind kind) {
        return kind instanceof AbilityKind.Checkerboard || kind instanceof AbilityKind.Crosses
                || kind instanceof AbilityKind.Trail || kind instanceof AbilityKind.Exaflare
                || kind instanceof AbilityKind.Scatter || kind instanceof AbilityKind.Charge
                || kind instanceof AbilityKind.Knockback || kind instanceof AbilityKind.RotatingBeam
                || kind instanceof AbilityKind.SafeZones || kind instanceof AbilityKind.Sequence;
    }

    /** The shortest warning any hit of the ability gets (later waves of a pattern have their own). */
    static int shortestWarning(BossAbility ability) {
        AbilityKind kind = ability.kind();
        int warning = ability.telegraphTicks();
        if (kind instanceof AbilityKind.Checkerboard board && board.waves() > 1) {
            warning = Math.min(warning, board.intervalTicks());
        } else if (kind instanceof AbilityKind.Crosses crosses && crosses.waves() > 1) {
            warning = Math.min(warning, crosses.intervalTicks());
        } else if (kind instanceof AbilityKind.RotatingBeam beam && beam.steps() > 1) {
            warning = Math.min(warning, beam.intervalTicks());
        } else if (kind instanceof AbilityKind.Charge charge && charge.charges() > 1) {
            warning = Math.min(warning, charge.intervalTicks() - BossRules.DASH_TICKS - 1);
        } else if (kind instanceof AbilityKind.Sequence sequence) {
            for (int i = 1; i < sequence.parts().size(); i++) {
                warning = Math.min(warning, sequence.parts().get(i).telegraphTicks());
            }
        }
        return warning;
    }

    @Test
    public void everyBossHasAtLeastFivePatternAttacksAndLaterBossesMoreForms() {
        Map<String, Integer> forms = Map.of("b1", 2, "b2", 3, "b3", 3, "b4", 3, "b5", 4, "b6", 4, "b7", 4, "b8", 4,
                "final", 4);
        for (BossDefinition boss : Bosses.all()) {
            long patterns = boss.abilities().stream().filter(ability -> isPattern(ability.kind())).count();
            assertTrue(boss.id() + " has " + patterns + " pattern attacks", patterns >= 5);
            assertEquals(boss.id(), forms.get(boss.id()).intValue(), boss.phases().size());
            Set<Class<?>> kinds = new HashSet<>();
            for (BossAbility ability : boss.abilities()) {
                kinds.add(ability.kind().getClass());
            }
            assertTrue(boss.id() + ": varied attacks", kinds.size() >= 8);
            for (int i = 1; i < boss.phases().size(); i++) {
                BossPhase phase = boss.phases().get(i);
                assertTrue(boss.id() + " form " + (i + 1) + " transforms", phase.hasTransition());
                BossAbility transition = boss.transitionAbility(i).orElseThrow();
                assertTrue(transition.id() + ": a shockwave", transition.kind() instanceof AbilityKind.Knockback);
                assertTrue(transition.id() + " fits into the transition",
                        transition.busyTicks() <= BossRules.PHASE_TRANSITION_TICKS);
                assertTrue(boss.id() + ": later forms move faster", phase.movementSpeedMultiplier()
                        >= boss.phases().get(i - 1).movementSpeedMultiplier());
                assertTrue(boss.id() + ": later forms act more often", phase.globalCooldownTicks()
                        <= boss.phases().get(i - 1).globalCooldownTicks());
            }
        }
        for (String strongest : List.of("b6", "b7", "b8")) {
            BossDefinition boss = Bosses.byId(strongest).orElseThrow();
            assertTrue(strongest + ": the last form is enraged", boss.phases().get(boss.phases().size() - 1).enrages());
        }
    }

    @Test
    public void everyHitOfAPatternIsWarnedAndPatternsDoNotLastForever() {
        for (BossDefinition boss : Bosses.all()) {
            for (BossAbility ability : boss.abilities()) {
                if (!ability.kind().damaging()) {
                    continue;
                }
                int warning = shortestWarning(ability);
                assertTrue(ability.id() + ": " + warning, warning >= BossRules.MIN_TELEGRAPH_TICKS);
                if (ability.damageMultiplier() >= 2.0 && !ability.kind().splitsDamage()) {
                    assertTrue(ability.id() + " hurts a lot: every hit warned for a second", warning >= 20);
                }
                assertTrue(ability.id() + " keeps the boss busy for " + ability.busyTicks(),
                        ability.busyTicks() <= 160);
                if (ability.kind() instanceof AbilityKind.SafeZones zones) {
                    assertTrue(ability.id() + ": enough room for the party",
                            zones.count() * zones.radius() * zones.radius() * Math.PI >= 6.0 * boss.maxPlayers());
                }
            }
        }
    }

    // ------------------------------------------------------------------ balance

    /** Average light-hit damage of a level-matched player: about 8 at level 1, 45 at level 50. */
    static double playerHit(int level) {
        return 8.0 + 37.0 * (level - 1) / 49.0;
    }

    /** Vanilla armor absorption (CombatRules.getDamageAfterAbsorb, toughness 0). */
    static double afterArmor(double damage, double armor) {
        double reduction = Math.min(20.0, Math.max(armor / 5.0, armor - damage / 2.0));
        return damage * (1.0 - reduction / 25.0);
    }

    /** Seconds for {@code players} level-matched players at 1.5 effective hits per second each. */
    static double fightSeconds(BossDefinition boss, int players) {
        double hit = afterArmor(playerHit(boss.level()), boss.armor());
        return boss.maxHealth(players, Difficulty.NORMAL) / (players * 1.5 * hit);
    }

    @Test
    public void normalFightsLastTwoToFourMinutesForLevelMatchedParties() {
        for (BossDefinition boss : Bosses.all()) {
            for (int players = boss.minPlayers(); players <= boss.maxPlayers(); players++) {
                double seconds = fightSeconds(boss, players);
                assertTrue(boss.id() + " with " + players + " players: " + seconds + " s",
                        seconds >= 120.0 && seconds <= 240.0);
            }
            assertTrue(boss.id() + ": enrage comes well after the expected end",
                    boss.enrageAfterTicks() / 20.0 > fightSeconds(boss, boss.minPlayers()) * 1.5);
        }
    }
}
