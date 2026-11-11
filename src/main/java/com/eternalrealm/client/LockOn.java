package com.eternalrealm.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Hold-to-aim for skills (D-LOCKON, owner's request of 2026-11-11): holding the key of an aimed skill picks the enemy
 * nearest the crosshair (within the skill's reach and in sight) and outlines it in white; releasing the key turns the
 * player toward it and casts the skill at it (the server keeps the skill on it). A short tap casts at once as before,
 * and area skills cast when the key goes down. Client thread only.
 */
public final class LockOn {
    /** Ticks a key must stay down before it aims (a shorter press is a tap). */
    static final int HOLD_TICKS = 4;
    /** Widest angle from the crosshair (degrees) at which an enemy can be picked. */
    static final double MAX_ANGLE = 40.0;
    /** How much better (score: degrees off the crosshair + half a point per block) another enemy must be to take over. */
    static final double SWITCH_MARGIN = 6.0;

    private static int slot = -1;
    private static long pressedAt;
    private static double range;
    private static LivingEntity target;
    private static LivingEntity outlined;
    private static boolean outlinedBefore;

    private LockOn() {
    }

    /** The skill slot being aimed, or -1. */
    static int aimingSlot() {
        return slot;
    }

    static boolean isAiming() {
        return slot >= 0;
    }

    /** The key of an aimed skill went down: start aiming (nothing shows until it has been held a moment). */
    static void begin(int skillSlot, double skillRange, long now) {
        cancel();
        slot = skillSlot;
        pressedAt = now;
        range = skillRange;
    }

    /** Every tick while the key is held: pick (or keep) the enemy and outline it. */
    static void hold(LocalPlayer player, long now) {
        if (!isAiming() || now - pressedAt < HOLD_TICKS) {
            return;
        }
        target = pick(player, range, target);
        outline(target);
    }

    /**
     * The key was released: turns the player toward the enemy aimed at (if it was held long enough to aim) and ends
     * aiming.
     *
     * @return the entity id of the enemy to send with the skill, or -1
     */
    static int release(LocalPlayer player, long now) {
        LivingEntity aimed = isAiming() && now - pressedAt >= HOLD_TICKS && target != null && valid(player, target, range)
                ? target : null;
        cancel();
        if (aimed == null) {
            return -1;
        }
        face(player, aimed);
        return aimed.getId();
    }

    /** Stops aiming without casting (and removes the outline). */
    static void cancel() {
        outline(null);
        slot = -1;
        target = null;
    }

    /**
     * The enemy to aim at: within {@code reach}, in sight, at most {@link #MAX_ANGLE} off the crosshair, the one with
     * the best score (degrees off the crosshair plus half a point per block); the current one stays unless another is
     * clearly better.
     */
    static LivingEntity pick(LocalPlayer player, double reach, LivingEntity current) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double currentScore = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach), candidate -> valid(player, candidate, reach))) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance < 1.0e-3) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.scale(1.0 / distance).dot(look), -1.0, 1.0)));
            if (angle > MAX_ANGLE) {
                continue;
            }
            double score = angle + distance * 0.5;
            if (entity == current) {
                currentScore = score;
            }
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        if (current != null && currentScore != Double.MAX_VALUE && currentScore <= bestScore + SWITCH_MARGIN) {
            return current;
        }
        return best;
    }

    /** A living enemy (never a player, a villager or a town NPC) within {@code reach} and in sight. */
    static boolean valid(LocalPlayer player, LivingEntity entity, double reach) {
        return entity != player && entity.isAlive() && !entity.isSpectator() && entity instanceof Enemy
                && !(entity instanceof Player) && player.distanceToSqr(entity) <= reach * reach
                && player.hasLineOfSight(entity);
    }

    /** Turns the player (camera included) toward the middle of {@code entity}. */
    static void face(LocalPlayer player, LivingEntity entity) {
        Vec3 eye = player.getEyePosition();
        Vec3 at = entity.getBoundingBox().getCenter();
        double dx = at.x - eye.x;
        double dy = at.y - eye.y;
        double dz = at.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = Mth.clamp((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -89.0F, 89.0F);
        // keep the yaw continuous with the current one (no full turn through ±180)
        float current = player.getYRot();
        yaw = current + Mth.wrapDegrees(yaw - current);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    /** Moves the white outline to {@code entity} (or removes it), restoring the previous entity's own glow. */
    private static void outline(LivingEntity entity) {
        if (outlined != null && outlined != entity) {
            GlowAccess.setGlowing(outlined, outlinedBefore);
            outlined = null;
        }
        if (entity == null) {
            return;
        }
        if (outlined == null) {
            outlined = entity;
            outlinedBefore = GlowAccess.isGlowing(entity);
        }
        GlowAccess.setGlowing(entity, true); // every tick: an update of the entity's flags from the server resets it
    }
}
