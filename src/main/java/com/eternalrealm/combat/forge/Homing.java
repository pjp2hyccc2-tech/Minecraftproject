package com.eternalrealm.combat.forge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Steering of projectiles of a skill locked on to an enemy (D-LOCKON): they curve into it while it lives. */
final class Homing {
    /** Share of the way to the enemy's direction a projectile turns each tick (it keeps its speed). */
    static final double TURN = 0.3;

    private Homing() {
    }

    /**
     * Turns {@code projectile} toward the entity {@code targetId} (server side, before it moves this tick).
     *
     * @return the id to keep steering toward next tick, -1 once the enemy is gone
     */
    static int steer(Projectile projectile, int targetId) {
        if (targetId < 0) {
            return -1;
        }
        Entity entity = projectile.level().getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            return -1;
        }
        Vec3 motion = projectile.getDeltaMovement();
        double speed = motion.length();
        Vec3 toward = target.getBoundingBox().getCenter().subtract(projectile.position());
        if (speed < 1.0e-3 || toward.lengthSqr() < 1.0e-6) {
            return targetId;
        }
        Vec3 next = motion.scale((1.0 - TURN) / speed).add(toward.normalize().scale(TURN));
        projectile.setDeltaMovement(next.normalize().scale(speed));
        return targetId;
    }
}
