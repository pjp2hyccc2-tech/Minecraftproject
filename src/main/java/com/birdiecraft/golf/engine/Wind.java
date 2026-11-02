package com.birdiecraft.golf.engine;

import java.util.Random;

/**
 * Wind for a hole. Direction uses the Minecraft yaw convention: the wind blows TOWARD
 * the direction vector (-sin(yaw), cos(yaw)).
 */
public final class Wind {
    public static final Wind CALM = new Wind(0.0, 0.0);

    /** Metres per block: 1 block = 2 yards. */
    private static final double METRES_PER_BLOCK = GolfUnits.YARDS_PER_BLOCK * 0.9144;

    public final double speedMps;
    public final double yawDeg;
    /** Wind velocity in blocks per tick. */
    public final double vx;
    public final double vz;

    public Wind(double speedMps, double yawDeg) {
        this.speedMps = speedMps;
        this.yawDeg = yawDeg;
        double bpt = speedMps / METRES_PER_BLOCK / 20.0;
        double r = Math.toRadians(yawDeg);
        this.vx = -Math.sin(r) * bpt;
        this.vz = Math.cos(r) * bpt;
    }

    public static Wind random(Random rng) {
        double roll = rng.nextDouble();
        double speed;
        if (roll < 0.28) speed = rng.nextDouble() * 2.0;
        else if (roll < 0.68) speed = 2.0 + rng.nextDouble() * 3.0;
        else if (roll < 0.92) speed = 5.0 + rng.nextDouble() * 3.0;
        else speed = 8.0 + rng.nextDouble() * 3.0;
        speed = Math.round(speed * 10.0) / 10.0;
        double yaw = rng.nextDouble() * 360.0;
        return new Wind(speed, yaw);
    }

    public boolean isCalm() {
        return speedMps < 0.05;
    }
}
