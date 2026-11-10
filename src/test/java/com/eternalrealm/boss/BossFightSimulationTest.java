package com.eternalrealm.boss;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * player-facing guarantees on every tick: every shape that hits was shown before with exactly the warning it got,
 * every shown shape hits exactly once or is called off, one ability at a time, transitions and openers.
 */
public class BossFightSimulationTest {
    private static final double DAMAGE_PER_TICK = 1.0 / 4000.0;
    private static final long ADD_KILL_DELAY = 100;

    @Test
    public void everyBossKeepsItsPromisesOnEveryDifficulty() {
        for (BossDefinition boss : Bosses.everyBoss()) {
            for (Difficulty difficulty : Difficulty.values()) {
                for (int seed = 1; seed <= 3; seed++) {
                    int party = boss.minPlayers() == 1 ? 1 + (seed * 2 - 1) % 5 : boss.minPlayers() + seed * 3;
                    new Simulation(boss, difficulty, party, seed, seed == 2).run();
                }
            }
        }
    }

    /** A shape on the ground: when it must hit. */
    private record Mark(BossShape shape, long fireTick) {
    }

    private static final class Simulation {
        private final BossDefinition boss;
        private final Difficulty difficulty;
        private final int party;
        private final BossBrain brain;
        private final String label;

        private double hp = 1.0;
        private BossAbility active;
        private long activeEnd;
        private final List<Mark> pending = new ArrayList<>();
        private final List<Mark> safe = new ArrayList<>();
        private long freeAt = Long.MIN_VALUE;
        private long phaseTick = Long.MIN_VALUE;
        private long transitionEnd = Long.MIN_VALUE;
        private String expectedTransition = "";
        private String expectedOpener;
        private int phase = -1;
        private boolean shieldShown;
        private int enrages;
        private final Set<Integer> arenaWidePhases = new HashSet<>();
        private final Set<String> used = new HashSet<>();
        private final TreeMap<Long, Integer> addKills = new TreeMap<>();

        Simulation(BossDefinition boss, Difficulty difficulty, int party, int seed, boolean arena) {
            this.boss = boss;
            this.difficulty = difficulty;
            this.party = party;
            this.brain = new BossBrain(boss, party, difficulty, new Random(seed));
            if (arena) {
                brain.setArena(new Point(100, 64, 100), boss == Bosses.FINAL ? 25.5 : 14.5);
            }
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
            assertTrue(label + ": nothing left on the ground", pending.isEmpty() && safe.isEmpty());
            assertEquals(label + ": every phase was played", boss.phases().size() - 1, phase);
            assertTrue(label + ": at most one enrage", enrages <= 1);
            for (int i = 1; i < boss.phases().size(); i++) {
                if (boss.phases().get(i).hasTransition()) {
                    assertTrue(label + ": transition " + i, used.contains(boss.phases().get(i).transitionId()));
                }
            }
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
            Map<BossShape, Integer> hitsThisTick = new HashMap<>();
            for (BossCommand command : commands) {
                if (command instanceof BossCommand.PhaseChanged changed) {
                    assertTrue(label + ": phases only advance", changed.phaseIndex() > phase);
                    assertTrue(label + ": no skipped phase at this damage rate", changed.phaseIndex() == phase + 1);
                    assertTrue(label + ": an ability that still had hits to come is cancelled before the phase "
                            + "changes (one in its recovery simply ends)", pending.isEmpty() && safe.isEmpty());
                    active = null;
                    phase = changed.phaseIndex();
                    phaseTick = t;
                    transitionEnd = phase == 0 ? t : t + boss.transitionTicks(phase);
                    expectedTransition = phase == 0 ? "" : changed.phase().transitionId();
                    List<String> openers = changed.phase().openerIds();
                    expectedOpener = openers.isEmpty() ? null : openers.get(0);
                    freeAt = Long.MIN_VALUE;
                } else if (command instanceof BossCommand.Cancelled cancelled) {
                    assertNotNull(label + ": cancel without ability", active);
                    assertEquals(active, cancelled.ability());
                    assertFalse(label + ": a cancelled ability still had something to do",
                            pending.isEmpty() && safe.isEmpty());
                    pending.clear();
                    safe.clear();
                    active = null;
                    freeAt = Long.MIN_VALUE;
                } else if (command instanceof BossCommand.AbilityStarted started) {
                    onStart(t, started);
                } else if (command instanceof BossCommand.Telegraph telegraph) {
                    assertEquals(label, active, telegraph.ability());
                    if (telegraph.ability().kind().damaging()) {
                        assertTrue(label + ": " + telegraph.abilityId() + " warns long enough",
                                telegraph.durationTicks() >= minWarning(telegraph.ability().kind()));
                    }
                    pending.add(new Mark(telegraph.shape(), t + telegraph.durationTicks()));
                } else if (command instanceof BossCommand.SafeSpot spot) {
                    assertEquals(label, active, spot.ability());
                    assertTrue(spot.durationTicks() >= BossRules.SAFE_ZONE_MIN_TELEGRAPH_TICKS);
                    safe.add(new Mark(spot.shape(), t + spot.durationTicks()));
                } else if (command instanceof BossCommand.Execute execute) {
                    assertEquals(label, active, execute.ability());
                    resolve(t, execute.shape(), execute.abilityId());
                    hitsThisTick.merge(execute.shape(), 1, Integer::sum);
                    assertTrue(label + ": damage", execute.damage() >= 0.0);
                    double scale = damageScale(execute.ability(), execute.hitKind());
                    assertTrue(label + ": " + execute.abilityId() + " multiplier",
                            execute.damageMultiplier() >= execute.ability().damageMultiplier() * scale
                                    * boss.tuning(difficulty).damageMultiplier() - 1e-9);
                    if (scale > 0.0) {
                        assertTrue(label + ": damage", execute.damage() > 0.0);
                    }
                    if (execute.shape() instanceof BossShape.Outside outside) {
                        for (BossShape spot : outside.safe()) {
                            assertTrue(label + ": safe spots go with their hit",
                                    safe.removeIf(mark -> mark.shape().equals(spot) && mark.fireTick() == t));
                        }
                    }
                    if (execute.hitKind() instanceof AbilityKind.ArenaWide) {
                        arenaWidePhases.add(phase);
                    }
                } else if (command instanceof BossCommand.Volley volley) {
                    assertEquals(label, active, volley.ability());
                    assertEquals(((AbilityKind.ProjectileVolley) volley.ability().kind()).count(),
                            volley.yawDegrees().size());
                    resolveAllDue(t, 1);
                } else if (command instanceof BossCommand.Summon summon) {
                    assertNotNull(label + ": summon without ability", active);
                    resolveAllDue(t, summon.count());
                    if (summon.shieldAdds()) {
                        addKills.merge(t + ADD_KILL_DELAY, summon.count(), Integer::sum);
                    }
                } else if (command instanceof BossCommand.Teleport) {
                    assertNotNull(label + ": teleport without ability", active);
                    resolveAllDue(t, 1);
                } else if (command instanceof BossCommand.Heal || command instanceof BossCommand.Buffed) {
                    assertNotNull(label + ": effect without ability", active);
                } else if (command instanceof BossCommand.Displace displace) {
                    assertEquals(label, active, displace.ability());
                    assertTrue(label + ": a push comes with its blast",
                            hitsThisTick.containsKey(new BossShape.Circle(displace.center(), displace.radius())));
                } else if (command instanceof BossCommand.Hazard hazard) {
                    assertEquals(label, active, hazard.ability());
                    assertTrue(label + ": a hazard stays where its hit landed", hitsThisTick.containsKey(hazard.shape()));
                    assertTrue(hazard.damage() > 0.0 && hazard.durationTicks() > 0);
                } else if (command instanceof BossCommand.Dash dash) {
                    assertEquals(label, active, dash.ability());
                    assertTrue(label + ": the charge hits its strip", hitsThisTick.containsKey(
                            new BossShape.Line(dash.from(), dash.to(),
                                    ((AbilityKind.Charge) dash.ability().kind()).width())));
                } else if (command instanceof BossCommand.ShieldChanged shield) {
                    assertTrue(label + ": shield toggles alternate", shield.on() != shieldShown);
                    shieldShown = shield.on();
                    assertEquals(label + ": visuals follow the immunity", brain.isInvulnerable(t), shield.on());
                } else if (command instanceof BossCommand.Enraged) {
                    enrages++;
                    boolean timed = t == brain.fightStartTick() + boss.enrageAfterTicks();
                    boolean byForm = phaseTick == t && boss.phases().get(phase).enrages();
                    assertTrue(label + ": enrage by time or by an enraged form", timed || byForm);
                }
            }
            for (Mark mark : pending) {
                assertTrue(label + ": " + mark + " was due at " + mark.fireTick() + " (now " + t + ")",
                        mark.fireTick() > t);
            }
            if (active != null && t >= activeEnd) {
                assertTrue(label + ": " + active.id() + " finished with everything resolved",
                        pending.isEmpty() && safe.isEmpty());
                active = null;
            }
        }

        private void resolve(long t, BossShape shape, String abilityId) {
            Iterator<Mark> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Mark mark = iterator.next();
                if (mark.shape().equals(shape)) {
                    assertEquals(label + ": " + abilityId + " hits exactly when its telegraph said", mark.fireTick(),
                            t);
                    iterator.remove();
                    return;
                }
            }
            fail(label + ": " + abilityId + " hit a shape that was never shown: " + shape);
        }

        /** Summons, teleports and volleys resolve their markers by time. */
        private void resolveAllDue(long t, int atLeast) {
            int removed = 0;
            Iterator<Mark> iterator = pending.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().fireTick() == t) {
                    iterator.remove();
                    removed++;
                }
            }
            assertTrue(label + ": markers before the effect", removed >= Math.min(1, atLeast));
        }

        private void onStart(long t, BossCommand.AbilityStarted started) {
            assertNull(label + ": one ability at a time", active);
            BossAbility ability = started.ability();
            used.add(ability.id());
            if (t < transitionEnd) {
                assertEquals(label + ": only the transition ability during a transition", expectedTransition,
                        ability.id());
                assertEquals(label + ": the transition ability starts with the phase", phaseTick, t);
                assertTrue(label + ": it fits into the transition", t + ability.busyTicks() <= transitionEnd);
            } else {
                assertTrue(label + ": global cooldown", t >= freeAt);
                if (expectedOpener != null) {
                    assertEquals(label + ": the phase opens with its opener", expectedOpener, ability.id());
                    if (phase > 0) {
                        assertEquals(label + ": opener right after the transition", transitionEnd, t);
                    }
                    expectedOpener = null;
                }
            }
            assertEquals(ability.telegraphTicks(), started.executeInTicks());
            active = ability;
            activeEnd = t + ability.busyTicks();
            freeAt = activeEnd;
        }

        /** Damage share of the part that hits (1 for anything but sequence parts). */
        private static double damageScale(BossAbility ability, AbilityKind hitKind) {
            if (ability.kind() instanceof AbilityKind.Sequence sequence) {
                double scale = Double.MAX_VALUE;
                for (AbilityKind.Part part : sequence.parts()) {
                    if (part.kind().equals(hitKind)) {
                        scale = Math.min(scale, part.damageScale());
                    }
                }
                assertTrue("the hit is one of the parts", scale != Double.MAX_VALUE);
                return scale;
            }
            assertEquals(ability.kind(), hitKind);
            return 1.0;
        }

        private static int minWarning(AbilityKind kind) {
            if (kind instanceof AbilityKind.Sequence) {
                return BossRules.MIN_TELEGRAPH_TICKS;
            }
            return kind.minTelegraphTicks();
        }
    }
}
