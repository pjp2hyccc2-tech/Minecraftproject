package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.item.WeaponArt;
import com.eternalrealm.item.WeaponArts;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillAiming;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Locked skills on the server: a skill sent with the enemy the player aimed at by holding its key (D-LOCKON) turns the
 * player toward that enemy for its start and for every hit, its projectiles home in on it, and its areas placed where
 * the player looks land on it. The client only names the enemy; the server checks it (a valid target, alive, in sight,
 * within {@link SkillAiming#MAX_RANGE} plus a little) on every use and forgets the lock when the action is over or the
 * enemy is gone. Locks are kept per action, so another skill pressed meanwhile neither drops nor steals a lock. Server
 * thread only.
 */
public final class SkillLocks {
    /** Extra blocks over the longest aim range a target may have moved away by the time the request arrives. */
    private static final double RANGE_SLACK = 4.0;
    /** A locked area lands on the enemy when it is within the skill's reach plus this. */
    private static final double PLACE_SLACK = 3.0;

    /** @param reach how far the skill reaches (its aim range): its areas land on the enemy within it */
    private record Lock(int entityId, double reach, long expiresAt) {
    }

    private static final Map<UUID, Map<String, Lock>> LOCKS = new HashMap<>();
    /** The action whose start or hit is being carried out (its areas use its lock); server thread. */
    private static UUID contextPlayer;
    private static String contextAction;

    private SkillLocks() {
    }

    /**
     * Locks the player's skill {@code action} (started or buffered just now) on the entity {@code entityId}, if that is
     * a fair target. Replaces only that action's lock.
     *
     * @return whether the lock was taken
     */
    public static boolean lock(ServerPlayer player, int entityId, ActionDefinition action, int durationTicks, long now) {
        return lock(player, entityId, action.id(), reach(action), durationTicks, now);
    }

    /** As {@link #lock(ServerPlayer, int, ActionDefinition, int, long)} for an action id and its reach. */
    public static boolean lock(ServerPlayer player, int entityId, String actionId, double reach, int durationTicks,
                               long now) {
        clear(player, actionId);
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof LivingEntity target) || !fair(player, target)) {
            return false;
        }
        LOCKS.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .put(actionId, new Lock(entityId, reach, now + durationTicks + 2));
        return true;
    }

    /** How far the skill or art of {@code action} reaches ({@link SkillAiming#range}). */
    static double reach(ActionDefinition action) {
        List<SkillEffect> effects = Jobs.skillOfAction(action.id()).map(SkillDefinition::effects)
                .or(() -> WeaponArts.byId(action.id()).map(WeaponArt::effects)).orElse(List.of());
        return SkillAiming.range(action, effects);
    }

    private static boolean fair(ServerPlayer player, LivingEntity target) {
        double reach = SkillAiming.MAX_RANGE + RANGE_SLACK;
        return target.isAlive() && CombatHits.isValidTarget(player, target)
                && player.distanceToSqr(target) <= reach * reach && player.hasLineOfSight(target);
    }

    /** The enemy the player's {@code actionId} is locked on, while the lock lasts and the enemy is still fair. */
    public static Optional<LivingEntity> target(ServerPlayer player, String actionId, long now) {
        Map<String, Lock> locks = LOCKS.get(player.getUUID());
        Lock lock = locks == null ? null : locks.get(actionId);
        if (lock == null) {
            return Optional.empty();
        }
        Entity entity = player.serverLevel().getEntity(lock.entityId());
        if (now > lock.expiresAt() || !(entity instanceof LivingEntity target) || !fair(player, target)) {
            clear(player, actionId);
            return Optional.empty();
        }
        return Optional.of(target);
    }

    /**
     * Carries out the start or a hit of {@code actionId}: while it runs, a locked player faces the enemy (melee shapes,
     * shots and areas ahead go its way) and areas placed where the player looks land on it; afterwards the player's own
     * rotation is back (the client only sends its rotation when the mouse moves, so a turned rotation would linger in
     * the next light attacks and guards).
     */
    public static void during(ServerPlayer player, String actionId, long now, Runnable work) {
        LivingEntity target = target(player, actionId, now).orElse(null);
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float head = player.getYHeadRot();
        float body = player.yBodyRot;
        UUID outerPlayer = contextPlayer;
        String outerAction = contextAction;
        contextPlayer = player.getUUID();
        contextAction = actionId;
        try {
            if (target != null) {
                face(player, target);
            }
            work.run();
        } finally {
            contextPlayer = outerPlayer;
            contextAction = outerAction;
            if (target != null) {
                player.setYRot(yaw);
                player.setXRot(pitch);
                player.setYHeadRot(head);
                player.setYBodyRot(body);
            }
        }
    }

    /** Turns the player toward the middle of {@code target}. */
    static void face(ServerPlayer player, LivingEntity target) {
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
    }

    /**
     * Where an area of the action being carried out ({@link #during}) that is placed where the player looks lands: on
     * its locked enemy (its feet) when the enemy is within the area's own distance or the skill's reach, else empty.
     */
    public static Optional<Vec3> placeOnTarget(ServerPlayer player, double distance, long now) {
        if (!player.getUUID().equals(contextPlayer) || contextAction == null) {
            return Optional.empty();
        }
        Map<String, Lock> locks = LOCKS.get(player.getUUID());
        Lock lock = locks == null ? null : locks.get(contextAction);
        if (lock == null) {
            return Optional.empty();
        }
        double reach = Math.max(distance, lock.reach()) + PLACE_SLACK;
        return target(player, contextAction, now).filter(target -> player.distanceTo(target) <= reach)
                .map(Entity::position);
    }

    /** Forgets the lock of {@code actionId} (a new use of that skill without a target). */
    public static void clear(ServerPlayer player, String actionId) {
        Map<String, Lock> locks = LOCKS.get(player.getUUID());
        if (locks != null) {
            locks.remove(actionId);
            if (locks.isEmpty()) {
                LOCKS.remove(player.getUUID());
            }
        }
    }

    public static void clear(ServerPlayer player) {
        LOCKS.remove(player.getUUID());
    }

    public static void clearAll() {
        LOCKS.clear();
        contextPlayer = null;
        contextAction = null;
    }
}
