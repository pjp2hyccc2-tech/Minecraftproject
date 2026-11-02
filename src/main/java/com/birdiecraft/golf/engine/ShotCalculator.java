package com.birdiecraft.golf.engine;

/** Turns a {@link ShotInput} into launch conditions. */
public final class ShotCalculator {
    /** Selectable putter ranges in yards (full gauge). */
    public static final int[] PUTT_RANGES_YARDS = {10, 20, 40, 80};

    private ShotCalculator() {
    }

    public static double maxCarryBlocks(Club club, ShotMode mode, PlayStyle style) {
        return GolfUnits.toBlocks(club.maxCarryYards) * style.distance * mode.distanceFactor;
    }

    /** Automatically choose the smallest putter range that covers the distance comfortably. */
    public static int autoPuttRange(double distBlocks) {
        double yards = GolfUnits.toYards(distBlocks);
        for (int i = 0; i < PUTT_RANGES_YARDS.length; i++) {
            if (yards * 1.25 <= PUTT_RANGES_YARDS[i]) return i;
        }
        return PUTT_RANGES_YARDS.length - 1;
    }

    /**
     * @param lieFactor power multiplier rolled within the lie's range (server rolls it randomly,
     *                  previews and the AI use the middle of the range)
     */
    public static ShotLaunch compute(ShotInput in, Lie lie, PlayStyle style,
                                     double sx, double sy, double sz, double lieFactor, long seed) {
        Club club = in.club();
        double dev = in.deviation();
        boolean perfect = in.isPerfect();

        if (club.isPutter()) {
            double range = GolfUnits.toBlocks(PUTT_RANGES_YARDS[in.puttRange()]);
            double ad = Math.abs(dev);
            double angErr = perfect ? 0 : (Math.signum(dev) * (Math.min(ad, 1.0) * 1.2 + Math.max(0, ad - 1.0) * 3.0));
            double distF = perfect ? 1.0 : 1.0 - 0.04 * Math.max(0, ad - GaugeMath.PERFECT_WINDOW);
            double dist = in.power() * range * distF;
            double speed = Calibration.puttSpeedFor(dist);
            double yaw = Math.toRadians(in.yawDeg() + angErr);
            double vx = -Math.sin(yaw) * speed;
            double vz = Math.cos(yaw) * speed;
            return new ShotLaunch(sx, sy, sz, vx, 0, vz, 0, 0, 0, true, seed);
        }

        ShotMode mode = in.mode();
        double maxCarry = maxCarryBlocks(club, mode, style);
        double ad = Math.abs(dev);
        double distF;
        if (perfect) {
            distF = mode == ShotMode.POWER ? 1.04 : 1.03;
        } else {
            distF = 1.0 - 0.05 * Math.min(1.0, Math.max(0, ad - GaugeMath.PERFECT_WINDOW)) - 0.10 * Math.max(0, ad - 1.0);
        }
        double carry = maxCarry * in.power() * lieFactor * distF;

        int spinY = in.spinY();
        int spinX = in.spinX();
        double angle = club.launchDeg + mode.launchAdd + lie.launchAdd;
        if (spinY > 0) angle -= 1.6 * spinY;
        else angle += 1.2 * (-spinY);
        angle = Math.max(3.0, angle);

        double lieSpin = lie.spinFactor(club);
        double back = club.baseSpin * style.spin * lieSpin * (1.0 - 0.32 * spinY);
        double topBounce = spinY > 0 ? 0.028 * spinY * style.spin : 0;
        double side = 0.5 * spinX * style.spin * Math.max(0.4, lieSpin);
        double yawOff = 0;
        if (!perfect) {
            side += dev * 0.30;
            yawOff = Math.signum(dev) * (Math.min(ad, 1.0) * 1.6 + Math.max(0, ad - 1.0) * 3.0);
        }

        double speed = Calibration.speedFor(angle, back, carry);
        double a = Math.toRadians(angle);
        double yaw = Math.toRadians(in.yawDeg() + yawOff);
        double hs = speed * Math.cos(a);
        double vx = -Math.sin(yaw) * hs;
        double vz = Math.cos(yaw) * hs;
        double vy = speed * Math.sin(a);
        return new ShotLaunch(sx, sy + 0.02, sz, vx, vy, vz, back, side, topBounce, false, seed);
    }
}
