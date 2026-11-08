package com.eternalrealm.boss;

import java.util.Objects;
import java.util.UUID;

/**
 * One player the boss may attack this tick: alive, in the fight (inside the arena) and not a spectator. The platform
 * decides who is a target; the brain only reads the snapshot.
 *
 * @param threat aggro value maintained by the platform (damage dealt, healing, taunts); only compared, never scaled
 */
public record TargetSnapshot(UUID id, double x, double y, double z, double threat) {
    public TargetSnapshot {
        Objects.requireNonNull(id, "id");
        Check.finite("x", x);
        Check.finite("y", y);
        Check.finite("z", z);
        Check.finite("threat", threat);
    }
}
