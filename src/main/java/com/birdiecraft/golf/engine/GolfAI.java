package com.birdiecraft.golf.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * CPU golfer brain. Plans the ideal shot by simulating candidate shots with the real physics
 * (terrain, wind, slopes) and scoring each outcome by the expected strokes still needed, then
 * applies human-like execution errors according to the difficulty.
 * <p>
 * Planning is incremental ({@link Planner#step(long)}) so the server can spread the work over
 * several ticks while the CPU "thinks".
 */
public final class GolfAI {
    private GolfAI() {
    }

    public record Context(Terrain terrain, double ballX, double ballY, double ballZ, Lie lie,
                          double cupX, double cupY, double cupZ, Wind wind, PlayStyle style,
                          Difficulty difficulty, int powerShots, Predicate<Lie> inBounds) {
    }

    /** Plans synchronously and applies execution error. */
    public static ShotInput decide(Context c, Random rng) {
        Planner p = new Planner(c);
        while (!p.step(Long.MAX_VALUE)) {
            // keep going
        }
        return applyError(p.result(), c.difficulty(), rng);
    }

    /** Spec of one candidate swing. */
    private record Spec(Club club, ShotMode mode, double yaw, double power, int spinY, int puttRange) {
    }

    public static final class Planner {
        private final Context c;
        private final boolean putt;
        private final double dist;
        private List<Spec> queue = new ArrayList<>();
        private int index;
        private boolean refined;
        private Spec best;
        private double bestScore = Double.MAX_VALUE;
        private boolean done;

        public Planner(Context c) {
            this.c = c;
            this.dist = GolfUnits.horizontalDistance(c.ballX(), c.ballZ(), c.cupX(), c.cupZ());
            this.putt = c.lie() == Lie.GREEN || (c.lie() == Lie.FRINGE && dist < 9.0);
            if (putt) buildPuttCandidates();
            else buildFullCandidates();
            if (queue.isEmpty()) {
                double yaw = GolfUnits.yawTo(c.ballX(), c.ballZ(), c.cupX(), c.cupZ());
                best = new Spec(Club.PW, ShotMode.APPROACH, yaw, Math.min(1.0, dist / 30.0), 0, 0);
                done = true;
            }
        }

        /**
         * Evaluates candidates until the time budget (nanoseconds) runs out.
         *
         * @return true once planning is complete
         */
        public boolean step(long budgetNanos) {
            if (done) return true;
            long start = System.nanoTime();
            while (index < queue.size()) {
                Spec s = queue.get(index++);
                double score = putt ? puttScore(s) : fullScore(s);
                if (score < bestScore) {
                    bestScore = score;
                    best = s;
                }
                if (System.nanoTime() - start > budgetNanos) return false;
            }
            if (!refined && best != null) {
                refined = true;
                queue = putt ? puttRefinement(best) : fullRefinement(best);
                index = 0;
                return false;
            }
            done = true;
            return true;
        }

        public boolean isDone() {
            return done;
        }

        public ShotInput result() {
            Spec s = best;
            if (s == null) {
                double yaw = GolfUnits.yawTo(c.ballX(), c.ballZ(), c.cupX(), c.cupZ());
                return new ShotInput(Club.PW, ShotMode.NORMAL, yaw, 0.5, 0, 0, 0, 0);
            }
            return new ShotInput(s.club(), s.mode(), s.yaw(), s.power(), 0.0, 0, s.spinY(), s.puttRange());
        }

        // ------------------------------------------------------------ putting

        private void buildPuttCandidates() {
            int range = ShotCalculator.autoPuttRange(dist);
            double rangeBlocks = GolfUnits.toBlocks(ShotCalculator.PUTT_RANGES_YARDS[range]);
            double baseYaw = GolfUnits.yawTo(c.ballX(), c.ballZ(), c.cupX(), c.cupZ());
            double basePow = Math.min(1.0, (dist + 0.6) / rangeBlocks);
            for (double da = -16; da <= 16.001; da += 2.0) {
                for (double pf = 0.6; pf <= 1.65; pf += 0.08) {
                    queue.add(new Spec(Club.PUTTER, ShotMode.NORMAL, baseYaw + da, Math.min(1.0, basePow * pf), 0, range));
                }
            }
        }

        private List<Spec> puttRefinement(Spec b) {
            List<Spec> list = new ArrayList<>();
            for (double da = -1.5; da <= 1.501; da += 0.25) {
                for (double dp = -0.05; dp <= 0.0501; dp += 0.0125) {
                    double p = Math.max(0.01, Math.min(1.0, b.power() + dp));
                    list.add(new Spec(Club.PUTTER, ShotMode.NORMAL, b.yaw() + da, p, 0, b.puttRange()));
                }
            }
            return list;
        }

        private double puttScore(Spec s) {
            ShotInput in = new ShotInput(Club.PUTTER, ShotMode.NORMAL, s.yaw(), s.power(), 0.0, 0, 0, s.puttRange());
            ShotLaunch l = ShotCalculator.compute(in, c.lie(), c.style(), c.ballX(), c.ballY(), c.ballZ(), 1.0, 7L);
            Trajectory t = BallPhysics.simulate(c.terrain(), l, Wind.CALM, BallPhysics.MAX_TICKS);
            if (t.result == Trajectory.Result.IN_CUP) {
                // prefer a firm-but-safe pace in the middle of the capture window
                return -1.0 + Math.abs(t.captureSpeed - 0.055) * 4.0;
            }
            if (t.result == Trajectory.Result.WATER || t.result == Trajectory.Result.OUT_OF_WORLD) return 50.0;
            double d = GolfUnits.horizontalDistance(t.endX, t.endZ, c.cupX(), c.cupZ());
            double travelled = GolfUnits.horizontalDistance(c.ballX(), c.ballZ(), t.endX, t.endZ);
            double shortPenalty = travelled < dist ? 0.15 : 0.0;
            double lie = c.inBounds().test(t.restLie) ? 0 : 20.0;
            return d + shortPenalty + lie;
        }

        // ------------------------------------------------------------ full shots

        private void buildFullCandidates() {
            double baseYaw = GolfUnits.yawTo(c.ballX(), c.ballZ(), c.cupX(), c.cupZ());
            double dirX = -Math.sin(Math.toRadians(baseYaw));
            double dirZ = Math.cos(Math.toRadians(baseYaw));
            double rightX = -dirZ;
            double rightZ = dirX;
            double windPerp = c.wind().vx * rightX + c.wind().vz * rightZ;

            double longest = 0;
            for (Club club : Club.VALUES) {
                if (club.isPutter()) continue;
                longest = Math.max(longest, ShotCalculator.maxCarryBlocks(club, ShotMode.NORMAL, c.style()) * c.lie().midPower(club));
            }
            for (Club club : Club.VALUES) {
                if (club.isPutter()) continue;
                for (ShotMode mode : ShotMode.VALUES) {
                    if (mode == ShotMode.POWER && (c.powerShots() <= 0
                            || (club.family != ClubFamily.DRIVER && club.family != ClubFamily.WOOD))) continue;
                    if (mode == ShotMode.APPROACH && !club.isWedge()) continue;
                    double maxCarry = ShotCalculator.maxCarryBlocks(club, mode, c.style()) * c.lie().midPower(club);
                    if (maxCarry <= 0) continue;
                    boolean isLongest = maxCarry >= longest * 0.999;
                    if (maxCarry < dist * 0.45 && !isLongest && mode != ShotMode.POWER) continue;
                    if (maxCarry * 0.3 > dist * 1.25) continue;
                    int[] spins = spinsFor(club, dist);
                    for (double p = 0.3; p <= 1.0001; p += 0.1) {
                        double carry = maxCarry * p;
                        if (carry > dist * 1.22 && p > 0.31) continue;
                        double flightTicks = 60 + 45 * Math.min(1.0, carry / 120.0);
                        double drift = BallPhysics.WIND_K * 0.5 * windPerp * flightTicks * flightTicks;
                        double comp = -Math.toDegrees(Math.atan2(drift, Math.max(5.0, carry)));
                        for (int spinY : spins) {
                            for (double off = -4; off <= 4.001; off += 2) {
                                queue.add(new Spec(club, mode, baseYaw + comp + off, Math.min(1.0, p), spinY, 0));
                            }
                        }
                    }
                }
            }
        }

        private List<Spec> fullRefinement(Spec b) {
            List<Spec> list = new ArrayList<>();
            for (double dp = -0.05; dp <= 0.0501; dp += 0.025) {
                for (double da = -1.5; da <= 1.501; da += 0.75) {
                    double p = Math.max(0.05, Math.min(1.0, b.power() + dp));
                    list.add(new Spec(b.club(), b.mode(), b.yaw() + da, p, b.spinY(), 0));
                }
            }
            return list;
        }

        private double fullScore(Spec s) {
            ShotInput in = new ShotInput(s.club(), s.mode(), s.yaw(), s.power(), 0.0, 0, s.spinY(), 0);
            ShotLaunch l = ShotCalculator.compute(in, c.lie(), c.style(), c.ballX(), c.ballY(), c.ballZ(),
                    c.lie().midPower(s.club()), 11L);
            Trajectory t = BallPhysics.simulate(c.terrain(), l, c.wind(), BallPhysics.MAX_TICKS);
            double score = expectedAfter(c, t);
            if (s.mode() == ShotMode.POWER) score += 0.12;
            if (t.touchedTree) score += 0.05;
            // prefer fuller swings with shorter clubs over soft swings with long clubs
            if (!s.club().isWedge()) score += 0.03 * (1.0 - s.power());
            return score;
        }
    }

    private static int[] spinsFor(Club club, double dist) {
        if (club.isWedge() || club == Club.IRON9 || club == Club.IRON8) return new int[]{0, -2};
        if (club == Club.DRIVER && dist > 80) return new int[]{0, 1};
        return new int[]{0};
    }

    /** Expected strokes still needed after the simulated shot (lower is better). */
    static double expectedAfter(Context c, Trajectory t) {
        switch (t.result) {
            case IN_CUP:
                return 0.0;
            case WATER: {
                double d = GolfUnits.horizontalDistance(t.endX, t.endZ, c.cupX(), c.cupZ());
                return 1.0 + offGreen(d, Lie.ROUGH);
            }
            case OUT_OF_WORLD:
                return 1.0 + estimate(c, c.ballX(), c.ballZ(), c.lie());
            default:
                break;
        }
        if (t.restLie.isHazard()) {
            double d = GolfUnits.horizontalDistance(t.endX, t.endZ, c.cupX(), c.cupZ());
            return 1.0 + offGreen(d, Lie.ROUGH);
        }
        if (!c.inBounds().test(t.restLie)) {
            return 1.0 + estimate(c, c.ballX(), c.ballZ(), c.lie());
        }
        return estimate(c, t.endX, t.endZ, t.restLie);
    }

    private static double estimate(Context c, double x, double z, Lie lie) {
        double d = GolfUnits.horizontalDistance(x, z, c.cupX(), c.cupZ());
        if (lie == Lie.GREEN) return onGreen(d);
        return offGreen(d, lie);
    }

    private static double onGreen(double d) {
        if (d < 0.8) return 1.0;
        return 1.0 + Math.min(1.25, 0.12 + d / 13.0);
    }

    private static double offGreen(double d, Lie lie) {
        double pen = switch (lie) {
            case FRINGE -> -0.25;
            case ROUGH, NATURAL -> 0.25;
            case DEEP_ROUGH, SOFT -> 0.5;
            case BUNKER, NATURAL_SAND -> 0.55;
            case HARD, CART_PATH -> 0.15;
            case WOOD -> 0.6;
            default -> 0.0;
        };
        if (d < 12) return 2.0 + d / 60.0 + pen;
        return 2.1 + d / 105.0 + pen;
    }

    // ------------------------------------------------------------------ execution error

    public static ShotInput applyError(ShotInput ideal, Difficulty d, Random rng) {
        boolean putt = ideal.club().isPutter();
        double powSigma = putt ? d.puttPowerSigma : d.powerSigma;
        double aimSigma = putt ? d.puttAimSigma : d.aimSigma;
        double power = ideal.power() * (1.0 + rng.nextGaussian() * powSigma);
        double yaw = ideal.yawDeg() + rng.nextGaussian() * aimSigma;
        double dev;
        double r = rng.nextDouble();
        if (r < d.perfectChance) {
            dev = 0.0;
        } else if (r < d.perfectChance + d.mishitChance) {
            dev = (rng.nextBoolean() ? 1 : -1) * (1.2 + rng.nextDouble() * 1.4);
        } else {
            dev = Math.max(-1.0, Math.min(1.0, rng.nextGaussian() * d.devSigma));
            if (Math.abs(dev) <= GaugeMath.PERFECT_WINDOW) {
                dev = (dev < 0 ? -1 : 1) * (GaugeMath.PERFECT_WINDOW + 0.02);
            }
        }
        return new ShotInput(ideal.club(), ideal.mode(), yaw, power, dev, ideal.spinX(), ideal.spinY(), ideal.puttRange());
    }
}
