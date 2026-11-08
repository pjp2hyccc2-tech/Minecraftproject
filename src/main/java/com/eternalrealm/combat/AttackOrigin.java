package com.eternalrealm.combat;

/**
 * Where an attack comes from: the attacker's eye position and facing, in Minecraft's conventions
 * (yaw 0 = +Z/south, yaw 90 = -X/west; pitch +90 = straight down).
 */
public record AttackOrigin(double x, double eyeY, double z, double yawDegrees, double pitchDegrees) {
    public AttackOrigin {
        if (!Double.isFinite(x) || !Double.isFinite(eyeY) || !Double.isFinite(z)
                || !Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("non-finite attack origin");
        }
    }
}
