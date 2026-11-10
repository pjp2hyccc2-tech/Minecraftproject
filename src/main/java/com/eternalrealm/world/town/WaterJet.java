package com.eternalrealm.world.town;

/**
 * Ballistics of the fountain jets, in blocks and ticks with Minecraft-like gravity. A vertical jet of power 1..3
 * climbs about 1.5 / 3 / 5 blocks; a spout (horizontal facing) leaves the nozzle 25 degrees above the horizon and
 * lands about 1.5 / 2.5 / 4 blocks out at the nozzle's height.
 */
public final class WaterJet {
    /** Gravity of the water, blocks per tick squared. */
    public static final double GRAVITY = 0.04;
    private static final double[] APEX = {1.5, 3.0, 5.0};
    private static final double[] REACH = {1.5, 2.5, 4.0};
    private static final double SPOUT_ANGLE = Math.toRadians(25.0);

    private WaterJet() {
    }

    public static int clampPower(int power) {
        return Math.max(1, Math.min(3, power));
    }

    /** Apex height of a vertical jet. */
    public static double apex(int power) {
        return APEX[clampPower(power) - 1];
    }

    /** Launch speed (blocks/tick) of a vertical jet reaching {@link #apex}. */
    public static double verticalSpeed(int power) {
        return Math.sqrt(2.0 * GRAVITY * apex(power));
    }

    /** Horizontal distance a spout reaches when it falls back to the nozzle's height. */
    public static double reach(int power) {
        return REACH[clampPower(power) - 1];
    }

    /** Launch speed of a spout so that it lands {@link #reach} out at the nozzle's height. */
    public static double spoutSpeed(int power) {
        return Math.sqrt(reach(power) * GRAVITY / Math.sin(2.0 * SPOUT_ANGLE));
    }

    /** Horizontal component of the spout's launch velocity. */
    public static double spoutForward(int power) {
        return spoutSpeed(power) * Math.cos(SPOUT_ANGLE);
    }

    /** Vertical component of the spout's launch velocity. */
    public static double spoutUp(int power) {
        return spoutSpeed(power) * Math.sin(SPOUT_ANGLE);
    }

    /**
     * Point of a spout's arc after {@code t} ticks: {@code {forward, up}} relative to the nozzle.
     */
    public static double[] spoutPoint(int power, double t) {
        return new double[]{spoutForward(power) * t, spoutUp(power) * t - 0.5 * GRAVITY * t * t};
    }

    /** Ticks until a spout's water has dropped {@code drop} blocks below the nozzle (drop >= 0). */
    public static double spoutTimeToDrop(int power, double drop) {
        double vy = spoutUp(power);
        return (vy + Math.sqrt(vy * vy + 2.0 * GRAVITY * Math.max(0.0, drop))) / GRAVITY;
    }
}
