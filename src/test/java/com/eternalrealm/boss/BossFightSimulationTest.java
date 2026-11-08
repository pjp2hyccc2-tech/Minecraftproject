package com.eternalrealm.boss;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Whole fights of every shipped boss on every difficulty with simulated players: the party removes 1/4000 of the
 * health per tick while the boss is vulnerable and kills shield adds 100 ticks after they appear. Checks the
 * player-facing guarantees on every tick.
 */
public class BossFightSimulationTest {
    private static final double DAMAGE_PER_TICK = 1.0 / 4000.0;
    private static final long ADD_KILL_DELAY = 100;

    @Test
    public void everyBossKeepsItsPromisesOnEveryDifficulty() {
        for (BossDefinition boss : Bosses.all()) {
            for (Difficulty difficulty : Difficulty.values()) {
                for (int seed = 1; seed <= 3; seed++) {
                    int party = boss.minPlayers() == 1 ? 1 + (seed * 2 - 1) % 5 : boss.minPlayers() + seed * 3;
                    new Simulation(boss, difficulty, party, seed).run();
                }
            }
        }
    }

    private static final class Simulation {
        private final BossDefinition boss;
        private final Difficulty difficulty;
        private final int party;
        private final BossBrain brain;
        private final String label;

        private double hp = 1.0;
        private BossAbility active;
        private long activeExecuteTick;
        private long activeEndTick;
        private List<BossShape> activeShapes;
        private boolean activeExecuted;
        private long freeAt = Long.MIN_VALUE;
        private long transitionEnd = Long.MIN_VALUE;
        private String expectedOpener;
        private int phase = -1;
        private boolean shieldShown;
        private int enrages;
        private final Set<Integer> arenaWidePhases = new HashSet<>();
        private final TreeMap<Long, Integer> addKills = new TreeMap<>();

        Simulation(BossDefinition boss, Difficulty difficulty, int party, int seed) {
            this.boss = boss;
            this.difficulty = difficulty;
            this.party = party;
            this.brain = new BossBrain(boss, party, difficulty, new Random(seed));
            this.label = boss.id() + "/" + difficulty + "/" + party + "p/seed " + seed;
        }

        void run() {
            BossPosition self = new BossPosition(100, 64, 100, 0);
            for (long t = 0; t < 40_000 && !brain.isDead(); t++) {
                if (t > 0 && !brain.isInvulnerable(t)) {
                    hp = Math.max(0.0, hp - DAMAGE_PER_TICK);
                }
                Integer kills = addKills.remove(t);
                for (int i = 0; kills != null && i < kills; i++) {
                    brain.onAddKilled();
                }
                List<BossCommand> commands = brain.tick(t, hp, self, players(t));
                check(t, commands);
            }
            assertTrue(label + ": the fight must end", brain.isDead());
            assertEquals(label + ": every phase was played", boss.phases().size() - 1, phase);
            assertTrue(label + ": at most one enrage", enrages <= 1);
            if (boss == Bosses.FINAL) {
                assertEquals(label + ": an arena-wide attack in every phase", Set.of(0, 1, 2, 3), arenaWidePhases);
            }
        }

        private List<TargetSnapshot> players(long t) {
            List<TargetSnapshot> targets = new ArrayList<>();
            for (int i = 0; i < party; i++) {
                double angle = t / 60.0 + i * 2.4;
                double radius = 3 + (i % 4) * 4;
                targets.add(new TargetSnapshot(BossFixtures.player(i), 100 + radius * Math.cos(angle), 64,
                        100 + radius * Math.sin(angle), i % 3));
            }
            return targets;
        }

        private void check(long t, List<BossCommand> commands) {
            List<BossShape> executed = new ArrayList<>();
            for (BossCommand command : commands) {
                if (command instanceof BossCommand.PhaseChanged changed) {
                    assertTrue(label + ": phases only advance", changed.phaseIndex() > phase);
                    assertTrue(label + ": no skipped phase at this damage rate", changed.phaseIndex() == phase + 1);
                    phase = changed.phaseIndex();
                    transitionEnd = phase == 0 ? t : t + BossRules.PHASE_TRANSITION_TICKS;
                    List<String> openers = changed.phase().openerIds();
                    expectedOpener = openers.isEmpty() ? null : openers.get(0);
                    if (active != null && !activeExecuted) {
                        fail(label + ": " + active.id() + " must be cancelled before the phase changes");
                    }
                    active = null;
                    freeAt = Long.MIN_VALUE;
                } else if (command instanceof BossCommand.Cancelled cancelled) {
                    assertNotNull(label + ": cancel without ability", active);
                    assertEquals(active, cancelled.ability());
                    assertFalse(activeExecuted);
                    active = null;
                    freeAt = Long.MIN_VALUE;
                } else if (command instanceof BossCommand.AbilityStarted started) {
                    onStart(t, started);
                } else if (command instanceof BossCommand.Telegraph telegraph) {
                    assertEquals(label, active, telegraph.ability());
                    assertTrue(label + ": telegraphs come with the start", activeExecuteTick - t
                            == telegraph.durationTicks());
                    if (telegraph.ability().kind().damaging()) {
                        assertTrue(telegraph.durationTicks() >= BossRules.MIN_TELEGRAPH_TICKS);
                    }
                    activeShapes.add(telegraph.shape());
                } else if (command instanceof BossCommand.Execute execute) {
                    expectExecution(t, execute.ability());
                    assertTrue(label + ": damage", execute.damage() > 0.0);
                    assertTrue(execute.damageMultiplier() >= execute.ability().damageMultiplier()
                            * boss.tuning(difficulty).damageMultiplier() - 1e-9);
                    executed.add(execute.shape());
                    if (execute.ability().kind() instanceof AbilityKind.ArenaWide) {
                        arenaWidePhases.add(phase);
                    }
                } else if (command instanceof BossCommand.Volley volley) {
                    expectExecution(t, volley.ability());
                    assertEquals(((AbilityKind.ProjectileVolley) volley.ability().kind()).count(),
                            volley.yawDegrees().size());
                } else if (command instanceof BossCommand.Summon summon) {
                    expectExecution(t, active);
                    if (summon.shieldAdds()) {
                        addKills.merge(t + ADD_KILL_DELAY, summon.count(), Integer::sum);
                    }
                } else if (command instanceof BossCommand.Teleport || command instanceof BossCommand.Heal
                        || command instanceof BossCommand.Buffed) {
                    expectExecution(t, active);
                } else if (command instanceof BossCommand.ShieldChanged shield) {
                    assertTrue(label + ": shield toggles alternate", shield.on() != shieldShown);
                    shieldShown = shield.on();
                    assertEquals(label + ": visuals follow the immunity", brain.isInvulnerable(t), shield.on());
                } else if (command instanceof BossCommand.Enraged) {
                    enrages++;
                    assertEquals(label, brain.fightStartTick() + boss.enrageAfterTicks(), t);
                }
            }
            if (!executed.isEmpty()) {
                assertEquals(label + ": executes exactly the telegraphed shapes", activeShapes, executed);
            }
            if (active != null && activeExecuted && t >= activeEndTick) {
                active = null;
            }
        }

        private void onStart(long t, BossCommand.AbilityStarted started) {
            assertNull(label + ": one ability at a time", active);
            assertTrue(label + ": nothing during a phase transition", t >= transitionEnd);
            assertTrue(label + ": global cooldown", t >= freeAt);
            if (expectedOpener != null) {
                assertEquals(label + ": the phase opens with its opener", expectedOpener, started.ability().id());
                if (phase > 0) {
                    assertEquals(label + ": opener right after the transition", transitionEnd, t);
                }
                expectedOpener = null;
            }
            active = started.ability();
            activeExecuteTick = t + started.executeInTicks();
            activeEndTick = activeExecuteTick + active.castTicks();
            activeShapes = new ArrayList<>();
            activeExecuted = false;
            freeAt = activeEndTick;
        }

        private void expectExecution(long t, BossAbility ability) {
            assertNotNull(label + ": execution without a running ability", active);
            assertEquals(label, active, ability);
            assertEquals(label + ": " + ability.id() + " executes exactly telegraphTicks after its start",
                    activeExecuteTick, t);
            activeExecuted = true;
        }
    }
}
