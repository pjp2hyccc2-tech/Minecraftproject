package com.eternalrealm.job.fx;

/**
 * Where a caster stands and faces: feet, eyes, the horizontal forward and right directions, the 3D look direction and
 * the side of the weapon hand ({@code 1} right, {@code -1} left).
 */
public record Pose(V3 feet, V3 eye, V3 fwd, V3 right, V3 look, int side) {
    /** A pose from Minecraft's yaw and pitch (degrees) at the feet, with the eyes {@code eyeHeight} above them. */
    public static Pose of(V3 feet, double yawDegrees, double pitchDegrees, double eyeHeight, int side) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        V3 fwd = new V3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        V3 right = new V3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        V3 look = new V3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        return new Pose(feet, feet.add(0.0, eyeHeight, 0.0), fwd, right, look, side >= 0 ? 1 : -1);
    }

    /** {@code forward} blocks ahead, {@code up} above the feet, {@code toRight} to the weapon hand's side. */
    public V3 at(double forward, double up, double toRight) {
        return feet.plus(fwd, forward).add(0.0, up, 0.0).plus(right, toRight * side);
    }

    /** The weapon hand, held a little ahead. */
    public V3 hand() {
        return at(0.55, 1.2, 0.36);
    }

    public V3 chest() {
        return feet.add(0.0, 1.15, 0.0);
    }

    public V3 head() {
        return feet.add(0.0, 1.75, 0.0);
    }

    /** The same pose moved to {@code newFeet} (eyes follow). */
    public Pose movedTo(V3 newFeet) {
        V3 d = newFeet.sub(feet);
        return new Pose(newFeet, eye.add(d), fwd, right, look, side);
    }
}
