package com.eternalrealm.combat.forge;

import com.eternalrealm.job.SkillAiming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Locked skills on the server: a skill sent with the enemy the player aimed at by holding its key (D-LOCKON) turns the
 * player toward that enemy at its start and before every hit, its projectiles home in on it, and its areas placed
 * where the player looks land on it. The client only names the enemy; the server checks it (a valid target, alive,
 * in sight, within {@link SkillAiming#MAX_RANGE} plus a little) and forgets the lock when the action is over or the
 * enemy is gone. Server thread only.
 */
public final class SkillLocks {
    /** Extra blocks over the longest aim range a target may have moved away by the time the request arrives. */
    private static final double RANGE_SLACK = 4.0;
    /** A locked area lands on the enemy only this much farther than the area's own distance. */
    private static final double PLACE_SLACK = 3.0;

    private record Lock(int entityId, String actionId, long expiresAt) {
    }

    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    private SkillLocks() {
    }

    /**
     * Locks the action the player just started on the entity {@code entityId}, if that is a fair target.
     *
     * @return whether the lock was taken
     */
    public static boolean lock(ServerPlayer player, int entityId, String actionId, int durationTicks, long now) {
        LOCKS.remove(player.getUUID());
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof LivingEntity target) || !fair(player, target)) {
            return false;
        }
        LOCKS.put(player.getUUID(), new Lock(entityId, actionId, now + durationTicks + 2));
        return true;
    }

    private static boolean fair(ServerPlayer player, LivingEntity target) {
        double reach = SkillAiming.MAX_RANGE + RANGE_SLACK;
        return target.isAlive() && CombatHits.isValidTarget(player, target)
                && player.distanceToSqr(target) <= reach * reach && player.hasLineOfSight(target);
    }

    /** The enemy the player's current action is locked on, while the lock lasts and the enemy is still fair. */
    public static Optional<LivingEntity> target(ServerPlayer player, long now) {
        Lock lock = LOCKS.get(player.getUUID());
        if (lock == null) {
            return Optional.empty();
        }
        Entity entity = player.serverLevel().getEntity(lock.entityId());
        if (now > lock.expiresAt() || !(entity instanceof LivingEntity target) || !fair(player, target)) {
            LOCKS.remove(player.getUUID());
            return Optional.empty();
        }
        return Optional.of(target);
    }

    /** The locked enemy of {@code actionId} (another action's lock does not count). */
    public static Optional<LivingEntity> target(ServerPlayer player, String actionId, long now) {
        Lock lock = LOCKS.get(player.getUUID());
        return lock != null && lock.actionId().equals(actionId) ? target(player, now) : Optional.empty();
    }

    /** Turns the player toward the locked enemy of {@code actionId} (its start and every hit). */
    public static void face(ServerPlayer player, String actionId, long now) {
        target(player, actionId, now).ifPresent(target -> {
            Vec3 eye = player.getEyePosition();
            Vec3 at = target.getBoundingBox().getCenter();
            double dx = at.x - eye.x;
            double dy = at.y - eye.y;
            double dz = at.z - eye.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            player.setYRot(yaw);
            player.setXRot(Mth.clamp(pitch, -89.0F, 89.0F));
            player.setYHeadRot(yaw);
            player.setYBodyRot(yaw);
        });
    }

    /**
     * Where an area placed where the player looks lands: on the locked enemy (its feet) when it is within the area's
     * reach, else empty.
     */
    public static Optional<Vec3> placeOnTarget(ServerPlayer player, double distance, long now) {
        return target(player, now).filter(target -> player.distanceTo(target) <= distance + PLACE_SLACK)
                .map(Entity::position);
    }

    public static void clear(ServerPlayer player) {
        LOCKS.remove(player.getUUID());
    }

    public static void clearAll() {
        LOCKS.clear();
    }
}
