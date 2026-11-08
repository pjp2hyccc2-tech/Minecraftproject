package com.eternalrealm.boss;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
 * The patterns of several hits (checkerboard, crosses, trails, exaflares, scatter, charges, knockback, rotating beams,
 * safe zones, sequences), phase transitions with their ability and enraged forms. Fixture bosses deal 10 base damage,
 * damaging fixture abilities have multiplier 2.0; with a global cooldown of 10 the first ability starts at tick 10.
 */
public class BossPatternTest {
    private static final double EPS = 1e-9;

    private static FightLog fight(BossAbility only, TargetSnapshot... targets) {
        return new FightLog(new BossBrain(boss(10, List.of(only)), 1, Difficulty.NORMAL, new Random(3)),
                List.of(targets), 0);
    }

    private static List<BossCommand.Telegraph> telegraphsAt(FightLog log, long tick) {
        List<BossCommand.Telegraph> found = new ArrayList<>();
        for (FightLog.Entry entry : log.entries) {
            if (entry.tick() == tick && entry.command() instanceof BossCommand.Telegraph telegraph) {
                found.add(telegraph);
            }
        }
        return found;
    }

    private static List<BossCommand.Execute> executesAt(FightLog log, long tick) {
        List<BossCommand.Execute> found = new ArrayList<>();
        for (FightLog.Entry entry : log.entries) {
            if (entry.tick() == tick && entry.command() instanceof BossCommand.Execute execute) {
                found.add(execute);
            }
        }
        return found;
    }

    private static List<BossShape> shapes(List<? extends BossCommand> commands) {
        List<BossShape> shapes = new ArrayList<>();
        for (BossCommand command : commands) {
            if (command instanceof BossCommand.Telegraph telegraph) {
                shapes.add(telegraph.shape());
            } else if (command instanceof BossCommand.Execute execute) {
                shapes.add(execute.shape());
            }
        }
        return shapes;
    }

    // ------------------------------------------------------------------ shapes

    @Test
    public void squaresCrossesAndOutsideAreasHitTheRightPlaces() {
        Point origin = new Point(0, 64, 0);
        BossShape square = new BossShape.Square(origin, 0, 2);
        assertTrue(square.contains(2, 64, -2));
        assertFalse(square.contains(2.1, 64, 0));
        BossShape diamond = new BossShape.Square(origin, 45, 2);
        assertTrue("a turned square reaches further along the diagonal", diamond.contains(2.7, 64, 0));
        assertFalse(diamond.contains(2, 64, 2));

        BossShape plus = new BossShape.Cross(origin, 0, 10, 2);
        assertTrue(plus.contains(0, 64, 9.9));
        assertTrue(plus.contains(-9.9, 64, 0.5));
        assertFalse("between the arms", plus.contains(3, 64, 3));
        assertFalse("beyond an arm", plus.contains(0, 64, 10.1));
        BossShape x = new BossShape.Cross(origin, 45, 10, 2);
        assertTrue(x.contains(3, 64, 3));
        assertFalse(x.contains(0, 64, 5));
        assertFalse(plus.contains(0, 64 + BossRules.VERTICAL_TOLERANCE + 0.5, 5));

        BossShape outside = new BossShape.Outside(origin, List.of(new BossShape.Circle(new Point(5, 64, 0), 2)));
        assertTrue(outside.contains(0, 64, 0));
        assertFalse("inside the safe circle", outside.contains(6, 64, 0));
        assertTrue(outside.contains(100, 64, 100));
        assertThrows(IllegalArgumentException.class, () -> new BossShape.Outside(origin, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new BossShape.Outside(origin, List.of(new BossShape.Arena(origin))));
    }

    // ------------------------------------------------------------------ chained waves

    @Test
    public void checkerboardAlternatesTheTilesAndEachWaveAppearsWhenThePreviousHits() {
        BossAbility board = ability("t_board", new AbilityKind.Checkerboard(4, 4, 3, 20), 30, 10, 1000, 10,
                TargetRule.NEAREST);
        assertEquals(30 + 40 + 10, board.busyTicks());
        FightLog log = fight(board, target(1, 3, 0));
        log.runTo(10 + 30 + 40 + 10);
        List<BossShape> first = shapes(telegraphsAt(log, 10));
        assertEquals("half of the 16 tiles", 8, first.size());
        for (BossCommand.Telegraph telegraph : telegraphsAt(log, 10)) {
            assertEquals(30, telegraph.durationTicks());
        }
        assertEquals(first, shapes(executesAt(log, 40)));
        List<BossShape> second = shapes(telegraphsAt(log, 40));
        assertEquals(8, second.size());
        for (BossCommand.Telegraph telegraph : telegraphsAt(log, 40)) {
            assertEquals("later waves warn for the interval", 20, telegraph.durationTicks());
        }
        Set<BossShape> all = new HashSet<>(first);
        all.addAll(second);
        assertEquals("the two waves cover the whole grid", 16, all.size());
        assertEquals(second, shapes(executesAt(log, 60)));
        assertEquals("the third wave is the first again", first, shapes(executesAt(log, 80)));
        BossShape.Square tile = (BossShape.Square) first.get(0);
        assertEquals(2.0, tile.halfSize(), EPS);
        assertEquals("tiles centred on the boss: 4 tiles of 4 from -6 to 6", -6.0, tile.center().x(), EPS);
    }

    @Test
    public void checkerboardLinesUpWithTheArenaWhenKnown() {
        BossAbility board = ability("t_board", new AbilityKind.Checkerboard(5, 3, 1, 20), 30, 10, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(board, target(1, 3, 0));
        log.brain.setArena(new Point(100, 70, -50), 7.5);
        log.self = new BossPosition(96, 64, -48, 0);
        log.runTo(10);
        Set<Point> centers = new HashSet<>();
        for (BossShape shape : shapes(telegraphsAt(log, 10))) {
            centers.add(shape.anchor());
        }
        assertEquals("5 of 9 tiles around the arena centre, at the boss's floor height",
                Set.of(new Point(95, 64, -55), new Point(105, 64, -55), new Point(100, 64, -50),
                        new Point(95, 64, -45), new Point(105, 64, -45)), centers);
    }

    @Test
    public void crossesTurnByFortyFiveDegrees() {
        BossAbility crosses = ability("t_cross", new AbilityKind.Crosses(12, 3, 2, 16), 24, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(crosses, target(1, 3, 0));
        log.runTo(10 + 24 + 16);
        BossShape.Cross plus = (BossShape.Cross) telegraphsAt(log, 10).get(0).shape();
        BossShape.Cross x = (BossShape.Cross) telegraphsAt(log, 34).get(0).shape();
        assertEquals(0.0, plus.yawDegrees(), EPS);
        assertEquals(45.0, x.yawDegrees(), EPS);
        assertEquals(new Point(0, 64, 0), x.center());
        assertEquals(List.of(plus), shapes(executesAt(log, 34)));
        assertEquals(List.of(x), shapes(executesAt(log, 50)));
    }

    @Test
    public void rotatingBeamsTurnStepByStepAroundTheBoss() {
        BossAbility beams = ability("t_beam", new AbilityKind.RotatingBeam(2, 10, 2, 30, 3, 12), 20, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(beams, target(1, 0, 6)); // south: yaw 0
        log.runTo(10 + 20 + 24);
        List<Double> yaws = new ArrayList<>();
        for (long tick : List.of(10L, 30L, 42L)) {
            List<BossCommand.Telegraph> wave = telegraphsAt(log, tick);
            assertEquals(2, wave.size());
            BossShape.Line a = (BossShape.Line) wave.get(0).shape();
            BossShape.Line b = (BossShape.Line) wave.get(1).shape();
            assertEquals(10.0, a.length(), 1e-6);
            assertEquals("evenly spaced: opposite beams", -a.end().x(), b.end().x(), 1e-6);
            yaws.add(Math.toDegrees(Math.atan2(-a.end().x(), a.end().z())));
        }
        assertEquals(0.0, yaws.get(0), 1e-6);
        double step = yaws.get(1) - yaws.get(0);
        assertEquals("turns 30 degrees per wave", 30.0, Math.abs(step), 1e-6);
        assertEquals("keeps turning the same way", 2 * step, yaws.get(2) - yaws.get(0), 1e-6);
        assertEquals(2, executesAt(log, 54).size());
    }

    // ------------------------------------------------------------------ waves placed in advance

    @Test
    public void exaflareRowsAdvanceOutwardOneAfterAnother() {
        BossAbility exaflare = ability("t_exa", new AbilityKind.Exaflare(2, 4, 3, 10, 5), 20, 0, 1000, 10,
                TargetRule.NEAREST);
        assertEquals(20 + 15, exaflare.lastHitTicks());
        FightLog log = fight(exaflare, target(1, 0, 8));
        log.runTo(10 + 35);
        List<BossCommand.Telegraph> shown = telegraphsAt(log, 10);
        assertEquals("all rows in both directions are shown at once", 8, shown.size());
        for (int k = 0; k < 4; k++) {
            List<BossCommand.Execute> row = executesAt(log, 30 + 5L * k);
            assertEquals(2, row.size());
            BossShape.Line south = (BossShape.Line) row.get(0).shape();
            BossShape.Line north = (BossShape.Line) row.get(1).shape();
            double distance = 1.0 + 3 * (k + 0.5);
            assertEquals("row " + k + " moves outward", distance, south.origin().z(), 1e-6);
            assertEquals(-distance, north.origin().z(), 1e-6);
            assertEquals("rows lie across the travel", 10.0, south.length(), 1e-6);
            assertEquals(3.0, south.width(), EPS);
        }
        assertEquals("each row warned until its own hit", 20 + 15, shown.get(6).durationTicks());
    }

    @Test
    public void scatterExplodesOneAfterAnotherInsideTheArenaAndLingers() {
        BossAbility mines = ability("t_mines", new AbilityKind.Scatter(5, 2, 30, 4, 60), 20, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(mines, target(1, 3, 0));
        log.brain.setArena(new Point(0, 64, 0), 10);
        log.runTo(10 + 20 + 16);
        List<BossCommand.Telegraph> shown = telegraphsAt(log, 10);
        assertEquals(5, shown.size());
        for (int i = 0; i < 5; i++) {
            BossShape.Circle circle = (BossShape.Circle) shown.get(i).shape();
            assertEquals(20 + 4 * i, shown.get(i).durationTicks());
            assertTrue("inside the arena", Math.abs(circle.center().x()) <= 10 - 2 && Math.abs(circle.center().z())
                    <= 10 - 2);
            assertEquals(List.of(circle), shapes(executesAt(log, 30 + 4L * i)));
        }
        List<BossCommand.Hazard> hazards = log.all(BossCommand.Hazard.class);
        assertEquals(5, hazards.size());
        assertEquals(60, hazards.get(0).durationTicks());
        assertEquals(BossRules.HAZARD_PULSE_TICKS, hazards.get(0).intervalTicks());
        assertEquals(20.0 * BossRules.HAZARD_DAMAGE_FRACTION, hazards.get(0).damage(), EPS);
    }

    @Test
    public void trailsDropUnderTheChasedPlayerWhereverSheGoes() {
        BossAbility trail = ability("t_trail", new AbilityKind.Trail(2, 1, 3, 8, 0), 12, 0, 1000, 10,
                TargetRule.FARTHEST);
        FightLog log = fight(trail, target(1, 2, 0), target(2, 10, 0));
        log.runTo(10);
        assertEquals(new Point(10, 64, 0), telegraphsAt(log, 10).get(0).shape().anchor());
        log.targets = List.of(target(1, 2, 0), target(2, 10, 5));
        log.runTo(18);
        assertEquals("the second drop follows player 2", new Point(10, 64, 5),
                telegraphsAt(log, 18).get(0).shape().anchor());
        log.targets = List.of(target(1, 2, 0));
        log.runTo(40);
        assertTrue("nothing drops for a player who left", telegraphsAt(log, 26).isEmpty());
        assertEquals(List.of(22L, 30L), log.ticksOf(command -> command instanceof BossCommand.Execute));
        assertTrue("no linger", log.all(BossCommand.Hazard.class).isEmpty());
    }

    // ------------------------------------------------------------------ movement

    @Test
    public void chargesMoveTheBossAndTheNextOneStartsWhereItArrived() {
        BossAbility charge = ability("t_charge", new AbilityKind.Charge(10, 3, 2, 20), 20, 0, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(charge, target(1, 0, 20));
        log.runTo(30);
        List<BossCommand.Dash> dashes = log.all(BossCommand.Dash.class);
        assertEquals(1, dashes.size());
        assertEquals(new Point(0, 64, 0), dashes.get(0).from());
        assertEquals(0.0, dashes.get(0).to().x(), 1e-9);
        assertEquals(10.0, dashes.get(0).to().z(), 1e-9);
        assertEquals(1, executesAt(log, 30).size());
        log.self = new BossPosition(0, 64, 9, 0); // stopped a block early by a wall
        log.runTo(30 + BossRules.DASH_TICKS + 1);
        BossCommand.Telegraph next = telegraphsAt(log, 30 + BossRules.DASH_TICKS + 1).get(0);
        assertEquals("aimed from where the boss arrived", new Point(0, 64, 9), next.shape().anchor());
        assertEquals(20 - BossRules.DASH_TICKS - 1, next.durationTicks());
        log.runTo(50);
        assertEquals(2, log.all(BossCommand.Dash.class).size());
    }

    @Test
    public void knockbackHitsAndPushesOrPulls() {
        BossAbility push = ability("t_push", new AbilityKind.Knockback(6, 1.5), 20, 0, 1000, 10, TargetRule.NEAREST);
        FightLog log = fight(push, target(1, 3, 0));
        log.runTo(30);
        List<BossCommand> fired = log.entries.stream().filter(e -> e.tick() == 30).map(FightLog.Entry::command)
                .toList();
        assertEquals(new BossShape.Circle(new Point(0, 64, 0), 6), ((BossCommand.Execute) fired.get(0)).shape());
        assertEquals(new BossCommand.Displace(push, new Point(0, 64, 0), 6, 1.5), fired.get(1));
        assertTrue(new AbilityKind.Knockback(6, -1.0).pulls());
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Knockback(6, 0.1));
    }

    // ------------------------------------------------------------------ safe zones & sequences

    @Test
    public void safeZonesMarkWhereToStandAndHitEveryoneElse() {
        BossAbility zones = ability("t_zones", new AbilityKind.SafeZones(3, 2, 6), 40, 0, 1000, 10,
                TargetRule.NEAREST);
        assertFalse(zones.kind().dodgeable() || zones.kind().guardable());
        assertThrows(IllegalArgumentException.class, () -> ability("t_quick", new AbilityKind.SafeZones(3, 2, 6),
                BossRules.SAFE_ZONE_MIN_TELEGRAPH_TICKS - 1, 0, 0, 1, TargetRule.NEAREST));
        FightLog log = fight(zones, target(1, 3, 0));
        log.brain.setArena(new Point(0, 64, 0), 6);
        log.runTo(50);
        List<BossCommand.SafeSpot> spots = log.all(BossCommand.SafeSpot.class);
        assertEquals(3, spots.size());
        List<BossShape> safe = new ArrayList<>();
        for (BossCommand.SafeSpot spot : spots) {
            assertEquals(40, spot.durationTicks());
            BossShape.Circle circle = (BossShape.Circle) spot.shape();
            assertTrue("fully inside the arena", Math.abs(circle.center().x()) + 2 <= 6 + EPS
                    && Math.abs(circle.center().z()) + 2 <= 6 + EPS);
            safe.add(circle);
        }
        BossCommand.Execute hit = executesAt(log, 50).get(0);
        assertEquals(new BossShape.Outside(new Point(0, 64, 0), safe), hit.shape());
        assertFalse(hit.dodgeable() || hit.guardable());
    }

    @Test
    public void sequencesChainTheirPartsWithTheirOwnWarningsDamageAndFlags() {
        AbilityKind.Sequence combo = new AbilityKind.Sequence(List.of(
                new AbilityKind.Part(new AbilityKind.Knockback(10, -1.2), 0, 0.0),
                AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(4), 16),
                new AbilityKind.Part(new AbilityKind.Soak(3, 2), 30, 0.5)));
        assertEquals(46, combo.extraTicks());
        assertEquals(BossRules.MIN_TELEGRAPH_TICKS, combo.minTelegraphTicks());
        assertFalse("not every part can be dodged", combo.dodgeable());
        BossAbility ability = ability("t_combo", combo, 20, 0, 1000, 10, TargetRule.NEAREST);
        FightLog log = fight(ability, target(1, 3, 0), target(2, -3, 0));
        log.runTo(10 + 20 + 46);
        BossCommand.Execute pull = executesAt(log, 30).get(0);
        assertEquals(0.0, pull.damage(), EPS);
        assertTrue(pull.hitKind() instanceof AbilityKind.Knockback);
        assertEquals(1, log.all(BossCommand.Displace.class).size());
        assertEquals("the blast appears when the pull hits", 16, telegraphsAt(log, 30).get(0).durationTicks());
        BossCommand.Execute blast = executesAt(log, 46).get(0);
        assertEquals(20.0, blast.damage(), EPS);
        assertTrue(blast.dodgeable() && blast.guardable());
        BossCommand.Execute soak = executesAt(log, 76).get(0);
        assertEquals(10.0, soak.damage(), EPS);
        assertTrue(soak.splitsDamage() && !soak.dodgeable());

        FightLog alone = fight(ability, target(1, 3, 0));
        alone.runTo(500);
        assertTrue("a sequence with a soak needs enough players", alone.startTicks().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Sequence(List.of(
                AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(4), 10),
                AbilityKind.Part.of(new AbilityKind.Ring(3, 8), 20))));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Sequence(List.of(
                AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(4), 0),
                AbilityKind.Part.of(new AbilityKind.Ring(3, 8), BossRules.MIN_TELEGRAPH_TICKS - 1))));
        assertThrows(IllegalArgumentException.class, () -> AbilityKind.Part.of(
                new AbilityKind.Checkerboard(4, 4, 2, 20), 20));
    }

    @Test
    public void cancellingAPatternRemovesItsRemainingWaves() {
        BossAbility board = ability("t_board", new AbilityKind.Checkerboard(4, 4, 3, 20), 30, 10, 1000, 10,
                TargetRule.NEAREST);
        FightLog log = fight(board, target(1, 3, 0));
        log.runTo(45); // first wave hit at 40, second shown
        log.hp = 0.0;
        assertEquals(List.of(new BossCommand.Cancelled(board)), log.step());
    }

    // ------------------------------------------------------------------ transitions & forms

    private static BossDefinition withTransition(boolean enrages) {
        BossAbility shock = ability("t_shock", new AbilityKind.Knockback(6, 1.2), 36, 12, 0, 1, TargetRule.NEAREST);
        BossAbility swipe = ability("t_swipe", new AbilityKind.Cone(5, 45), 12, 6, 0, 10, TargetRule.NEAREST);
        return boss(List.of(new BossPhase(1.0, List.of("t_slam"), 30, 1.0),
                        new BossPhase(0.5, List.of("t_slam", "t_swipe"), 30, 1.3, List.of("t_swipe"), "t_shock",
                                enrages)),
                List.of(slam("t_slam", 20, 10, 10_000), swipe, shock), DifficultyTuning.NORMAL);
    }

    @Test
    public void aNewFormStartsItsTransitionAbilityWhileInvulnerable() {
        FightLog log = new FightLog(new BossBrain(withTransition(false), 1, Difficulty.NORMAL, new Random(1)),
                List.of(target(1, 2, 0)), 0);
        log.runTo(39); // slam telegraphed at 30
        log.hp = 0.4;
        List<BossCommand> transition = log.step();
        assertEquals("t_slam", ((BossCommand.Cancelled) transition.get(0)).ability().id());
        assertEquals(1, ((BossCommand.PhaseChanged) transition.get(1)).phaseIndex());
        assertEquals("t_shock", ((BossCommand.AbilityStarted) transition.get(2)).ability().id());
        assertTrue(transition.get(3) instanceof BossCommand.Telegraph);
        assertEquals(new BossCommand.ShieldChanged(true), transition.get(transition.size() - 1));
        assertTrue(log.brain.isTransforming(40));
        assertTrue(log.brain.isInvulnerable(76));
        log.runTo(76);
        assertEquals("the shockwave hits during the transition", List.of(76L),
                log.ticksOf(command -> command instanceof BossCommand.Displace));
        long end = 40 + BossRules.PHASE_TRANSITION_TICKS;
        assertEquals(BossRules.PHASE_TRANSITION_TICKS, log.brain.definition().transitionTicks(1));
        log.runTo(end);
        assertFalse(log.brain.isTransforming(end));
        assertEquals("the opener follows right after the transition", List.of(end), log.startTicks("t_swipe"));
        assertFalse("no enrage", log.brain.isEnraged());
        assertThrows(IllegalArgumentException.class, () -> boss(List.of(
                new BossPhase(1.0, List.of("t_slam"), 30, 1.0, List.of(), "t_slam", false)),
                List.of(slam("t_slam", 20, 10, 100)), DifficultyTuning.NORMAL));
    }

    @Test
    public void anEnragedFormEnragesOnceAndTheTimerDoesNotRepeatIt() {
        FightLog log = new FightLog(new BossBrain(withTransition(true), 1, Difficulty.NORMAL, new Random(1)),
                List.of(target(1, 2, 0)), 0);
        log.runTo(100);
        log.hp = 0.3;
        log.runTo(101);
        assertEquals(List.of(101L), log.ticksOf(command -> command instanceof BossCommand.Enraged));
        assertTrue(log.brain.isEnraged());
        assertEquals(15.0, log.brain.meleeDamage(101), EPS);
        log.runTo(7000);
        assertEquals("the soft enrage has nothing left to do", 1, log.all(BossCommand.Enraged.class).size());
    }

    @Test
    public void patternsAreDeterministicPerSeed() {
        BossAbility mines = ability("t_mines", new AbilityKind.Scatter(6, 2, 12, 3, 0), 20, 0, 50, 10,
                TargetRule.NEAREST);
        BossAbility beams = ability("t_beam", new AbilityKind.RotatingBeam(3, 10, 2, 20, 4, 10), 20, 0, 60, 10,
                TargetRule.RANDOM);
        List<BossShape> first = shapesOfFight(List.of(mines, beams), 5);
        assertEquals(first, shapesOfFight(List.of(mines, beams), 5));
        assertNotEquals(first, shapesOfFight(List.of(mines, beams), 6));
    }

    private static List<BossShape> shapesOfFight(List<BossAbility> abilities, long seed) {
        BossBrain brain = new BossBrain(boss(10, abilities), 2, Difficulty.NORMAL, new Random(seed));
        List<BossShape> shapes = new ArrayList<>();
        for (long t = 0; t < 600; t++) {
            for (BossCommand command : brain.tick(t, 1.0, BossFixtures.AT_ORIGIN, List.of(target(1, 4, 0),
                    target(2, -3, 5)))) {
                if (command instanceof BossCommand.Telegraph telegraph) {
                    shapes.add(telegraph.shape());
                }
            }
        }
        return shapes;
    }

    @Test
    public void patternParametersAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Checkerboard(1, 4, 2, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityKind.Checkerboard(4, 4, 2, BossRules.MIN_TELEGRAPH_TICKS - 1));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Crosses(12, 3, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Trail(2, 0, 3, 8, 0));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Exaflare(0, 4, 3, 10, 5));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Scatter(0, 2, 8, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.Charge(10, 3, 2,
                BossRules.DASH_TICKS + BossRules.MIN_TELEGRAPH_TICKS));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.RotatingBeam(2, 10, 2, 120, 3, 12));
        assertThrows(IllegalArgumentException.class, () -> new AbilityKind.SafeZones(0, 2, 6));
        assertEquals(player(1), target(1, 0, 0).id());
    }
}
