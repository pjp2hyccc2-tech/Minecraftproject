package com.eternalrealm.boss;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.eternalrealm.boss.BossFixtures.AT_ORIGIN;
import static com.eternalrealm.boss.BossFixtures.ability;
import static com.eternalrealm.boss.BossFixtures.boss;
import static com.eternalrealm.boss.BossFixtures.player;
import static com.eternalrealm.boss.BossFixtures.slam;
import static com.eternalrealm.boss.BossFixtures.target;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Behaviour of the boss state machine with small hand-made bosses. Fights start at tick 0 unless stated otherwise;
 * fixture bosses deal 10 base damage, damaging fixture abilities have multiplier 2.0, the enrage comes after 6000
 * ticks.
 */
public class BossBrainTest {
    private static final double EPS = 1e-9;
    private static final BossAbility SLAM = slam("t_slam", 20, 10, 100);
    private static final BossAbility SWIPE =
            ability("t_swipe", new AbilityKind.Cone(5, 45), 12, 6, 0, 10, TargetRule.NEAREST);

    private static BossBrain brain(BossDefinition definition) {
        return new BossBrain(definition, 1, Difficulty.NORMAL, new Random(1));
    }

    private static FightLog fight(BossDefinition definition, TargetSnapshot... targets) {
        return new FightLog(brain(definition), List.of(targets), 0);
    }

    // ------------------------------------------------------------------ start & targets

    @Test
    public void nothingHappensWithoutTargets() {
        BossBrain brain = brain(boss(30, List.of(SLAM)));
        for (long t = 0; t <= 10_000; t++) {
            assertEquals(List.of(), brain.tick(t, 1.0, AT_ORIGIN, List.of()));
        }
        assertFalse(brain.hasStarted());
        assertFalse("the enrage timer only starts with the fight", brain.isEnraged());
        assertEquals(0, brain.phase());
        assertFalse(brain.currentAbility().isPresent());
    }

    @Test
    public void theFirstTickWithATargetStartsTheFightInPhaseZero() {
        BossBrain brain = brain(boss(30, List.of(SLAM)));
        brain.tick(5, 1.0, AT_ORIGIN, List.of());
        List<BossCommand> commands = brain.tick(6, 1.0, AT_ORIGIN, List.of(target(1, 3, 0)));
        assertEquals(1, commands.size());
        BossCommand.PhaseChanged changed = (BossCommand.PhaseChanged) commands.get(0);
        assertEquals(0, changed.phaseIndex());
        assertEquals(brain.definition().phases().get(0), changed.phase());
        assertTrue(brain.hasStarted());
        assertEquals(6, brain.fightStartTick());
        assertFalse("no transition shield at the start", brain.isInvulnerable(6));
    }

    @Test
    public void anAbilityAlreadyTelegraphedResolvesWithoutTargetsButNoNewOneStarts() {
        FightLog log = fight(boss(30, List.of(slam("t_quick", 20, 10, 0))), target(1, 2, 0));
        log.runTo(31); // started at 30
        log.targets = List.of();
        log.runTo(400);
        assertEquals(List.of(50L), log.ticksOf(command -> command instanceof BossCommand.Execute));
        assertEquals(List.of(30L), log.startTicks());
        log.targets = List.of(target(1, 2, 0));
        log.runTo(401);
        assertEquals("starts again at once: the global cooldown has long passed", List.of(30L, 401L),
                log.startTicks());
    }

    // ------------------------------------------------------------------ timeline

    @Test
    public void telegraphShapesAreFixedAndExecuteExactlyTelegraphTicksLater() {
        BossAbility mark = ability("t_mark", new AbilityKind.CircleAtTargets(3, 1), 20, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(mark, SLAM)), target(1, 5, 5));
        log.runTo(10);
        List<BossCommand> started = log.entries.stream().filter(e -> e.tick() == 10).map(FightLog.Entry::command)
                .toList();
        BossCommand.AbilityStarted abilityStarted = (BossCommand.AbilityStarted) started.get(0);
        BossAbility first = abilityStarted.ability();
        assertEquals(first.telegraphTicks(), abilityStarted.executeInTicks());
        BossCommand.Telegraph telegraph = (BossCommand.Telegraph) started.get(1);
        assertEquals(first, telegraph.ability());
        assertEquals(first.telegraphTicks(), telegraph.durationTicks());

        // players and boss move after the telegraph started: the attack still lands where it was shown
        log.targets = List.of(target(1, 20, 20));
        log.self = new BossPosition(3, 64, 3, 45);
        log.runTo(10 + first.telegraphTicks() - 1);
        assertTrue(log.all(BossCommand.Execute.class).isEmpty());
        log.step();
        List<BossCommand.Execute> executes = log.all(BossCommand.Execute.class);
        assertEquals(1, executes.size());
        assertEquals(telegraph.shape(), executes.get(0).shape());
        assertEquals(first, executes.get(0).ability());
        Point expectedCenter = first == mark ? new Point(5, 64, 5) : new Point(0, 64, 0);
        assertEquals(expectedCenter, executes.get(0).shape().anchor());
        assertEquals(2.0, executes.get(0).damageMultiplier(), EPS);
        assertEquals(20.0, executes.get(0).damage(), EPS);
        assertEquals(first.id(), executes.get(0).abilityId());
    }

    @Test
    public void perAbilityCooldownCountsFromTheStart() {
        FightLog log = fight(boss(30, List.of(SLAM)), target(1, 2, 0));
        log.runTo(400);
        assertEquals(List.of(30L, 130L, 230L, 330L), log.startTicks());
        assertEquals(List.of(50L, 150L, 250L, 350L), log.ticksOf(command -> command instanceof BossCommand.Execute));
    }

    @Test
    public void globalCooldownCountsFromTheEndOfTheAbility() {
        FightLog log = fight(boss(30, List.of(slam("t_spam", 20, 10, 0))), target(1, 2, 0));
        log.runTo(239);
        assertTrue("busy until 240 (started at 210)", log.brain.currentAbility().isPresent());
        log.runTo(240);
        assertFalse(log.brain.currentAbility().isPresent());
        log.runTo(269);
        assertEquals(List.of(30L, 90L, 150L, 210L), log.startTicks());
        log.runTo(270);
        assertEquals(List.of(30L, 90L, 150L, 210L, 270L), log.startTicks());
    }

    @Test
    public void onlyOneAbilityRunsAtATime() {
        FightLog log = fight(boss(0, List.of(slam("t_spam", 20, 10, 0), SWIPE)), target(1, 2, 0));
        log.runTo(2000);
        List<BossCommand.AbilityStarted> starts = log.all(BossCommand.AbilityStarted.class);
        List<Long> ticks = log.startTicks();
        assertTrue(starts.size() > 50);
        Set<String> used = new HashSet<>();
        for (int i = 1; i < starts.size(); i++) {
            BossAbility previous = starts.get(i - 1).ability();
            used.add(previous.id());
            assertEquals("next ability starts right after the previous one ended (no global cooldown)",
                    previous.busyTicks(), ticks.get(i) - ticks.get(i - 1));
        }
        assertEquals(Set.of("t_spam", "t_swipe"), used);
    }

    // ------------------------------------------------------------------ phases

    private static BossDefinition twoPhases(List<String> openers) {
        BossAbility lazySlam = slam("t_slam", 20, 10, 10_000);
        return boss(List.of(new BossPhase(1.0, List.of("t_slam"), 30, 1.0),
                        new BossPhase(0.5, List.of("t_slam", "t_swipe"), 30, 1.3, openers)),
                List.of(lazySlam, SWIPE), DifficultyTuning.NORMAL);
    }

    @Test
    public void crossingAThresholdCancelsTheAbilityAndStartsAnInvulnerableTransition() {
        FightLog log = fight(twoPhases(List.of("t_swipe")), target(1, 2, 0));
        log.runTo(39); // slam telegraphed at 30, would execute at 50
        log.hp = 0.4;
        List<BossCommand> transition = log.step();
        assertEquals(3, transition.size());
        assertEquals("t_slam", ((BossCommand.Cancelled) transition.get(0)).ability().id());
        assertEquals(1, ((BossCommand.PhaseChanged) transition.get(1)).phaseIndex());
        assertEquals(new BossCommand.ShieldChanged(true), transition.get(2));
        assertEquals(1, log.brain.phase());
        assertEquals(1.3, log.brain.movementSpeedMultiplier(), EPS);
        assertFalse(log.brain.currentAbility().isPresent());

        long end = 40 + BossRules.PHASE_TRANSITION_TICKS;
        assertTrue(log.brain.isInvulnerable(40));
        assertTrue(log.brain.isInvulnerable(end - 1));
        assertFalse(log.brain.isInvulnerable(end));
        log.runTo(end - 1);
        assertEquals("nothing during the transition", List.of(30L), log.startTicks());
        assertTrue(log.all(BossCommand.Execute.class).isEmpty());

        List<BossCommand> after = log.step();
        assertEquals(new BossCommand.ShieldChanged(false), after.get(0));
        assertEquals("the phase opener comes first", "t_swipe",
                ((BossCommand.AbilityStarted) after.get(1)).ability().id());
    }

    @Test
    public void transitionDuringRecoveryDoesNotReportACancel() {
        FightLog log = fight(twoPhases(List.of()), target(1, 2, 0));
        log.runTo(55); // slam executed at 50, recovering until 60
        log.hp = 0.4;
        List<BossCommand> transition = log.step();
        assertTrue(transition.get(0) instanceof BossCommand.PhaseChanged);
        assertTrue(log.all(BossCommand.Cancelled.class).isEmpty());
    }

    @Test
    public void openersIgnoreTheirCooldownAndRunInOrder() {
        FightLog log = fight(twoPhases(List.of("t_slam", "t_swipe")), target(1, 2, 0));
        log.runTo(60); // slam used at 30 (cooldown 10000)
        log.hp = 0.4;
        log.runTo(400);
        long end = 61 + BossRules.PHASE_TRANSITION_TICKS;
        assertEquals(List.of(30L, end), log.startTicks("t_slam"));
        assertEquals("swipe follows after slam (30 busy) and the global cooldown (30)",
                end + 60, (long) log.startTicks("t_swipe").get(0));
    }

    @Test
    public void phasesNeverGoBackAndCanBeSkipped() {
        BossDefinition three = boss(List.of(new BossPhase(1.0, List.of("t_slam"), 30, 1.0),
                        new BossPhase(0.7, List.of("t_slam"), 30, 1.1),
                        new BossPhase(0.3, List.of("t_slam", "t_swipe"), 30, 1.2)),
                List.of(SLAM, SWIPE), DifficultyTuning.NORMAL);
        FightLog log = fight(three, target(1, 2, 0));
        log.runTo(10);
        log.hp = 0.1; // one big hit through two thresholds
        log.runTo(11);
        List<BossCommand.PhaseChanged> changes = log.all(BossCommand.PhaseChanged.class);
        assertEquals(2, changes.size());
        assertEquals(2, changes.get(1).phaseIndex());
        log.hp = 0.9; // healed above every threshold
        log.runTo(500);
        assertEquals(2, log.all(BossCommand.PhaseChanged.class).size());
        assertEquals(2, log.brain.phase());
    }

    @Test
    public void aFightStartingBelowAThresholdStartsInThatPhaseWithoutTransition() {
        BossBrain brain = brain(twoPhases(List.of()));
        List<BossCommand> commands = brain.tick(0, 0.3, AT_ORIGIN, List.of(target(1, 2, 0)));
        assertEquals(List.of(new BossCommand.PhaseChanged(1, brain.definition().phases().get(1))), commands);
        assertFalse(brain.isInvulnerable(0));
    }

    // ------------------------------------------------------------------ enrage

    @Test
    public void softEnrageRaisesDamageAndShortensCooldowns() {
        FightLog log = fight(boss(30, List.of(slam("t_slow", 20, 10, 1000))), target(1, 2, 0));
        log.runTo(5999);
        assertFalse(log.brain.isEnraged());
        assertEquals(10.0, log.brain.meleeDamage(5999), EPS);
        log.runTo(7300);
        assertEquals(List.of(6000L), log.ticksOf(command -> command instanceof BossCommand.Enraged));
        assertTrue(log.brain.isEnraged());
        assertEquals(15.0, log.brain.meleeDamage(7300), EPS);
        assertEquals(List.of(30L, 1030L, 2030L, 3030L, 4030L, 5030L, 6030L, 6630L, 7230L), log.startTicks());
        List<BossCommand.Execute> executes = log.all(BossCommand.Execute.class);
        assertEquals(2.0, executes.get(5).damageMultiplier(), EPS); // 5050
        assertEquals(2.0 * BossRules.ENRAGE_DAMAGE_MULTIPLIER, executes.get(6).damageMultiplier(), EPS); // 6050
        assertEquals(30.0, executes.get(6).damage(), EPS);
    }

    // ------------------------------------------------------------------ determinism & targeting

    private static BossDefinition varied() {
        return boss(10, List.of(
                slam("t_slam", 20, 10, 60),
                ability("t_cone", new AbilityKind.Cone(6, 40), 14, 6, 40, 20, TargetRule.NEAREST),
                ability("t_marks", new AbilityKind.CircleAtTargets(3, 2), 24, 6, 80, 15, TargetRule.RANDOM),
                ability("t_volley", new AbilityKind.ProjectileVolley(3, 1.2, 30), 12, 6, 100, 10, TargetRule.RANDOM),
                ability("t_line", new AbilityKind.Line(10, 2), 16, 8, 70, 10, TargetRule.FARTHEST)));
    }

    private static List<TargetSnapshot> circling(long t) {
        List<TargetSnapshot> targets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double angle = t / 40.0 + i * 1.7;
            targets.add(new TargetSnapshot(player(i), 8 * Math.cos(angle), 64, 8 * Math.sin(angle), i));
        }
        return targets;
    }

    private static List<FightLog.Entry> run(long seed, boolean reverseTargets) {
        BossBrain brain = new BossBrain(varied(), 4, Difficulty.HARD, new Random(seed));
        List<FightLog.Entry> entries = new ArrayList<>();
        for (long t = 0; t < 3000; t++) {
            List<TargetSnapshot> targets = circling(t);
            if (reverseTargets) {
                Collections.reverse(targets);
            }
            double hp = Math.max(0.01, 1.0 - t / 4000.0);
            for (BossCommand command : brain.tick(t, hp, new BossPosition(0, 64, 0, t % 360), targets)) {
                entries.add(new FightLog.Entry(t, command));
            }
        }
        return entries;
    }

    @Test
    public void theSameSeedGivesTheSameFight() {
        List<FightLog.Entry> first = run(42, false);
        assertTrue(first.size() > 200);
        assertEquals(first, run(42, false));
        assertNotEquals(first, run(43, false));
    }

    @Test
    public void theOrderOfTheTargetListDoesNotMatter() {
        assertEquals(run(7, false), run(7, true));
    }

    private static double coneYaw(TargetRule rule, TargetSnapshot... targets) {
        BossAbility cone = ability("t_cone", new AbilityKind.Cone(6, 40), 14, 6, 1000, 10, rule);
        FightLog log = fight(boss(10, List.of(cone)), targets);
        log.runTo(10);
        return ((BossShape.Cone) log.all(BossCommand.Telegraph.class).get(0).shape()).yawDegrees();
    }

    /** Compares yaws modulo 360 (north may come out as 180 or -180). */
    private static void assertYaw(String message, double expected, double actual) {
        double difference = ((actual - expected) % 360.0 + 540.0) % 360.0 - 180.0;
        assertEquals(message + " (yaw " + actual + ")", 0.0, difference, 1e-6);
    }

    @Test
    public void targetRulesPickTheRightPlayer() {
        TargetSnapshot near = target(1, 0, 5);
        TargetSnapshot far = target(2, -10, 0);
        TargetSnapshot threat = target(3, 0, -7, 50);
        assertYaw("nearest is south", 0.0, coneYaw(TargetRule.NEAREST, near, far, threat));
        assertYaw("farthest is west", 90.0, coneYaw(TargetRule.FARTHEST, near, far, threat));
        assertYaw("highest threat is north", 180.0, coneYaw(TargetRule.HIGHEST_THREAT, near, far, threat));
        assertYaw("threat tie: the nearest", -90.0,
                coneYaw(TargetRule.HIGHEST_THREAT, target(1, 9, 0, 5), target(2, 4, 0, 5)));
        assertYaw("standing on the boss: the boss's own yaw", 0.0, coneYaw(TargetRule.NEAREST, target(1, 0, 0)));
    }

    @Test
    public void randomTargetingReachesEveryone() {
        BossAbility mark = ability("t_mark", new AbilityKind.CircleAtTargets(2, 1), 10, 0, 0, 10, TargetRule.RANDOM);
        FightLog log = fight(boss(0, List.of(mark)), target(1, 5, 0), target(2, -5, 0), target(3, 0, 5));
        log.runTo(400);
        Set<Point> centers = new HashSet<>();
        for (BossCommand.Execute execute : log.all(BossCommand.Execute.class)) {
            centers.add(execute.shape().anchor());
        }
        assertEquals(Set.of(new Point(5, 64, 0), new Point(-5, 64, 0), new Point(0, 64, 5)), centers);
    }

    @Test
    public void circlesAtTargetsUseDistinctTargets() {
        BossAbility marks = ability("t_marks", new AbilityKind.CircleAtTargets(3, 2), 20, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(marks)), target(1, 9, 0), target(2, 2, 0), target(3, 0, 4));
        log.runTo(30);
        List<Point> telegraphed = new ArrayList<>();
        for (BossCommand.Telegraph telegraph : log.all(BossCommand.Telegraph.class)) {
            telegraphed.add(telegraph.shape().anchor());
        }
        assertEquals(List.of(new Point(2, 64, 0), new Point(0, 64, 4)), telegraphed);
        assertEquals(2, log.all(BossCommand.Execute.class).size());

        FightLog few = fight(boss(10, List.of(marks)), target(1, 9, 0));
        few.runTo(30);
        assertEquals("fewer targets than circles", 1, few.all(BossCommand.Execute.class).size());
    }

    @Test
    public void linesAndConesPointAtTheTarget() {
        BossAbility line = ability("t_line", new AbilityKind.Line(12, 3), 16, 0, 1000, 10, TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(line)), target(1, -6, 0));
        log.runTo(10);
        BossShape.Line shape = (BossShape.Line) log.all(BossCommand.Telegraph.class).get(0).shape();
        assertEquals(new Point(0, 64, 0), shape.origin());
        assertEquals(-12.0, shape.end().x(), 1e-9);
        assertEquals(0.0, shape.end().z(), 1e-9);
        assertEquals(3.0, shape.width(), EPS);
        assertTrue(shape.contains(-11, 64, 1));
        assertFalse(shape.contains(6, 64, 0));
    }

    // ------------------------------------------------------------------ special kinds

    @Test
    public void soakIsOnlyUsedWithEnoughPlayers() {
        BossAbility soak = ability("t_soak", new AbilityKind.Soak(4, 2), 40, 0, 0, 10, TargetRule.RANDOM);
        FightLog alone = fight(boss(10, List.of(soak)), target(1, 3, 0));
        alone.runTo(2000);
        assertTrue(alone.startTicks().isEmpty());

        FightLog pair = fight(boss(10, List.of(soak)), target(1, 3, 0), target(2, -3, 0));
        pair.runTo(59); // starts at 10, executes at 50, may start again at 60
        assertEquals(List.of(10L), pair.startTicks());
        BossCommand.Execute execute = pair.all(BossCommand.Execute.class).get(0);
        assertTrue(execute.ability().kind().splitsDamage());
        assertEquals(4.0, ((BossShape.Circle) execute.shape()).radius(), EPS);
    }

    @Test
    public void shieldHoldsUntilAllAddsDie() {
        BossAbility shield = ability("t_shield", new AbilityKind.Shield("t_minion", 3, 400), 20, 0, 10_000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(shield)), target(1, 2, 0));
        log.brain.onAddKilled(); // no shield yet: ignored
        log.runTo(29);
        assertEquals("one marker per add", 3, log.all(BossCommand.Telegraph.class).size());
        assertFalse(log.brain.isInvulnerable(29));
        List<BossCommand> executed = log.step(); // 30
        BossCommand.Summon summon = (BossCommand.Summon) executed.get(0);
        assertEquals("t_minion", summon.templateId());
        assertEquals(3, summon.count());
        assertTrue(summon.shieldAdds());
        assertEquals(new BossCommand.ShieldChanged(true), executed.get(1));
        assertTrue(log.brain.isInvulnerable(30));

        log.runTo(100);
        log.brain.onAddKilled();
        log.brain.onAddKilled();
        assertTrue(log.brain.isInvulnerable(100));
        log.brain.onAddKilled();
        assertFalse("drops at once", log.brain.isInvulnerable(100));
        assertEquals(List.of(new BossCommand.ShieldChanged(false)), log.step());
        log.brain.onAddKilled(); // extra calls are harmless
        assertFalse(log.brain.isInvulnerable(102));
    }

    @Test
    public void shieldTimesOutAndIsNotRecastWhileUp() {
        BossAbility shield = ability("t_shield", new AbilityKind.Shield("t_minion", 2, 400), 20, 0, 0, 10,
                TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(shield)), target(1, 2, 0));
        log.runTo(429);
        assertTrue(log.brain.isInvulnerable(429));
        assertFalse("timed out", log.brain.isInvulnerable(430));
        log.runTo(600);
        assertEquals(List.of(10L, 430L), log.startTicks());
        assertEquals(List.of(30L, 450L), log.ticksOf(command -> command.equals(new BossCommand.ShieldChanged(true))));
        assertEquals(List.of(430L), log.ticksOf(command -> command.equals(new BossCommand.ShieldChanged(false))));
    }

    @Test
    public void healOnlyWhenTheBossLostEnough() {
        BossAbility heal = ability("t_heal", new AbilityKind.Heal(0.1), 40, 0, 0, 10, TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(heal)), target(1, 2, 0));
        log.hp = 0.95;
        log.runTo(500);
        assertTrue(log.startTicks().isEmpty());
        log.hp = 0.9;
        log.runTo(541);
        assertEquals(List.of(501L), log.startTicks());
        assertEquals(List.of(new BossCommand.Heal(0.1)), log.all(BossCommand.Heal.class));
        assertTrue("no shapes, a cast bar only", log.all(BossCommand.Telegraph.class).isEmpty());
    }

    @Test
    public void buffRaisesDamageWhileItLasts() {
        BossAbility buff = ability("t_buff", new AbilityKind.Buff(1.5, 200), 20, 0, 0, 10, TargetRule.NEAREST);
        BossAbility quick = slam("t_quick", 20, 10, 0);
        BossDefinition definition = boss(List.of(new BossPhase(1.0, List.of("t_buff", "t_quick"), 10, 1.0,
                List.of("t_buff"))), List.of(buff, quick), DifficultyTuning.NORMAL);
        FightLog log = fight(definition, target(1, 2, 0));
        log.runTo(229);
        assertEquals(List.of(new BossCommand.Buffed(1.5, 200)), log.all(BossCommand.Buffed.class));
        assertEquals(List.of(10L), log.startTicks("t_buff"));
        List<BossCommand.Execute> executes = log.all(BossCommand.Execute.class);
        assertEquals(5, executes.size());
        for (BossCommand.Execute execute : executes) {
            assertEquals(3.0, execute.damageMultiplier(), EPS);
        }
        assertEquals(15.0, log.brain.meleeDamage(229), EPS);
        assertEquals(10.0, log.brain.meleeDamage(230), EPS);
    }

    @Test
    public void teleportGoesWhereTheTargetStoodAtTheTelegraph() {
        BossAbility blink = ability("t_blink", new AbilityKind.TeleportToTarget(), 20, 10, 1000, 10,
                TargetRule.FARTHEST);
        FightLog log = fight(boss(10, List.of(blink)), target(1, 2, 0), target(2, 0, 12));
        log.runTo(10);
        assertEquals(new BossShape.Circle(new Point(0, 64, 12), BossRules.MARKER_RADIUS),
                log.all(BossCommand.Telegraph.class).get(0).shape());
        log.targets = List.of(target(1, 2, 0), target(2, 9, 9));
        log.runTo(30);
        assertEquals(List.of(new BossCommand.Teleport(player(2), new Point(0, 64, 12))),
                log.all(BossCommand.Teleport.class));
    }

    @Test
    public void volleyFansOutTowardsTheTarget() {
        BossAbility volley = ability("t_volley", new AbilityKind.ProjectileVolley(5, 1.5, 40), 16, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(volley)), target(1, -8, 0));
        log.runTo(26);
        BossShape.Cone fan = (BossShape.Cone) log.all(BossCommand.Telegraph.class).get(0).shape();
        assertYaw("aimed west", 90.0, fan.yawDegrees());
        assertEquals(20.0, fan.halfAngleDegrees(), EPS);
        assertEquals(BossRules.VOLLEY_RANGE, fan.range(), EPS);
        BossCommand.Volley shot = log.all(BossCommand.Volley.class).get(0);
        assertEquals(5, shot.yawDegrees().size());
        double[] expected = {70, 80, 90, 100, 110};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], shot.yawDegrees().get(i), 1e-6);
        }
        assertEquals(1.5, shot.speed(), EPS);
        assertEquals(20.0, shot.damage(), EPS);
        assertEquals(new Point(0, 64, 0), shot.origin());
        assertTrue(log.all(BossCommand.Execute.class).isEmpty());
    }

    @Test
    public void summonsAppearAroundTheBoss() {
        BossAbility call = ability("t_call", new AbilityKind.Summon("t_minion", 4), 20, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(boss(10, List.of(call)), target(1, 2, 0));
        log.self = new BossPosition(10, 70, 10, 0);
        log.runTo(30);
        BossCommand.Summon summon = log.all(BossCommand.Summon.class).get(0);
        assertFalse(summon.shieldAdds());
        double[][] expected = {{10, 14}, {6, 10}, {10, 6}, {14, 10}};
        for (int i = 0; i < 4; i++) {
            Point position = summon.positions().get(i);
            assertEquals(expected[i][0], position.x(), 1e-9);
            assertEquals(70.0, position.y(), EPS);
            assertEquals(expected[i][1], position.z(), 1e-9);
        }
        assertFalse("summons do not shield the boss", log.brain.isInvulnerable(30));
    }

    // ------------------------------------------------------------------ death, difficulty, invalid input

    @Test
    public void aDeadBossCancelsAndStops() {
        FightLog log = fight(boss(30, List.of(SLAM)), target(1, 2, 0));
        log.runTo(40);
        log.hp = 0.0;
        assertEquals(List.of(new BossCommand.Cancelled(SLAM)), log.step());
        assertTrue(log.brain.isDead());
        log.hp = 0.5;
        log.runTo(500);
        assertEquals(1, log.startTicks().size());
        assertTrue(log.all(BossCommand.Execute.class).isEmpty());
        assertFalse(log.brain.isInvulnerable(500));
    }

    @Test
    public void difficultyAndPartySizeShapeTheBrain() {
        BossAbility extra = ability("t_extra", new AbilityKind.Ring(3, 8), 20, 10, 200, 10, TargetRule.NEAREST);
        BossDefinition definition = boss(List.of(new BossPhase(1.0, List.of("t_slam"), 30, 1.0)),
                List.of(SLAM, extra), DifficultyTuning.ex("t_extra"));
        BossBrain normal = new BossBrain(definition, 3, Difficulty.NORMAL, new Random(1));
        BossBrain ex = new BossBrain(definition, 3, Difficulty.EX, new Random(1));
        assertEquals(1000.0 * 2.0, normal.maxHealth(), EPS);
        assertEquals("EX: four times the health", 1000.0 * 2.0 * 4.0, ex.maxHealth(), EPS);
        assertEquals(List.of(SLAM), normal.rotation());
        assertEquals(List.of(SLAM, extra), ex.rotation());
        assertEquals("EX: twice the damage", 20.0, ex.meleeDamage(0), EPS);
        assertEquals(2.0, ex.armor(), EPS);
        assertEquals(3, ex.partySize());
        assertEquals(Difficulty.EX, ex.difficulty());

        FightLog log = new FightLog(ex, List.of(target(1, 2, 0)), 0);
        log.runTo(30);
        BossAbility first = log.all(BossCommand.AbilityStarted.class).get(0).ability();
        long started = log.startTicks(first.id()).get(0);
        assertEquals("EX cooldowns are shorter", Math.round(first.cooldownTicks() * 0.75),
                log.brain.cooldownRemaining(first.id(), started));
    }

    @Test
    public void invalidInputIsRejected() {
        BossDefinition definition = boss(30, List.of(SLAM));
        assertThrows(IllegalArgumentException.class, () -> new BossBrain(definition, 0, Difficulty.NORMAL,
                new Random(1)));
        assertThrows(NullPointerException.class, () -> new BossBrain(definition, 1, null, new Random(1)));
        assertThrows(NullPointerException.class, () -> new BossBrain(definition, 1, Difficulty.NORMAL, null));
        BossBrain brain = brain(definition);
        List<TargetSnapshot> one = List.of(target(1, 2, 0));
        brain.tick(10, 1.0, AT_ORIGIN, one);
        assertThrows(IllegalArgumentException.class, () -> brain.tick(9, 1.0, AT_ORIGIN, one));
        assertThrows(IllegalArgumentException.class, () -> brain.tick(11, 1.1, AT_ORIGIN, one));
        assertThrows(IllegalArgumentException.class, () -> brain.tick(11, -0.1, AT_ORIGIN, one));
        assertThrows(IllegalArgumentException.class, () -> brain.tick(11, Double.NaN, AT_ORIGIN, one));
        assertThrows(NullPointerException.class, () -> brain.tick(11, 1.0, null, one));
        assertThrows(NullPointerException.class, () -> brain.tick(11, 1.0, AT_ORIGIN, null));
        brain.tick(10, 1.0, AT_ORIGIN, one); // the same tick again is allowed
        assertEquals(UUID.class, one.get(0).id().getClass());
    }
}
