package com.eternalrealm.boss;

/**
 * Where the boss stands this tick, in Minecraft's conventions (yaw 0 = +Z/south, 90 = -X/west).
 *
 * <p>{@code y} is the height of the ground the boss stands on: ground shapes are drawn at this height. For a hovering
 * boss pass the floor height below it, not the entity's own y.</p>
 */
public record BossPosition(double x, double y, double z, double yawDegrees) {
    public BossPosition {
        Check.finite("x", x);
        Check.finite("y", y);
        Check.finite("z", z);
        Check.finite("yawDegrees", yawDegrees);
    }
}
