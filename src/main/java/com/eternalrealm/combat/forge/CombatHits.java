package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.AttackOrigin;
import com.eternalrealm.combat.Box;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.HitGeometry;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.network.forge.DamageNumberPacket;
import com.eternalrealm.network.forge.ERNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Executes a due hit frame on the server: finds the targets inside the frame's volume (server positions, server
 * facing), checks line of sight, rolls and applies damage, and reports the damage to the attacker.
 */
public final class CombatHits {
    /** The hit being applied right now (server thread only): lets the knockback event use the action's value. */
    private static ActiveHit current;

    private CombatHits() {
    }

    private record ActiveHit(LivingEntity target, double knockback) {
    }

    private record Candidate(LivingEntity entity, double distanceSquared) {
    }

    /**
     * @param random uniform {@code [0, 1)} source for critical rolls
     * @return number of targets damaged
     */
    public static int execute(ServerPlayer attacker, CombatStats stats, CombatEvent.Hit hit, DoubleSupplier random) {
        if (!attacker.isAlive() || attacker.isSpectator()) {
            return 0;
        }
        ServerLevel level = attacker.serverLevel();
        HitShape shape = hit.frame().shape();
        Vec3 eye = attacker.getEyePosition();
        AttackOrigin origin = new AttackOrigin(eye.x, eye.y, eye.z, attacker.getYRot(), attacker.getXRot());

        AABB search = attacker.getBoundingBox().inflate(shape.reach() + 2.0);
        List<Candidate> inside = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, search,
                candidate -> isValidTarget(attacker, candidate))) {
            Box box = toBox(entity.getBoundingBox());
            if (HitGeometry.contains(shape, origin, box) && hasLineOfSight(level, attacker, eye, entity)) {
                inside.add(new Candidate(entity, HitGeometry.distanceSquared(origin, box)));
            }
        }
        inside.sort(Comparator.comparingDouble(Candidate::distanceSquared));

        CombatEffects.hitFrame(attacker, hit);
        int damaged = 0;
        for (Candidate candidate : inside) {
            if (damaged >= shape.maxTargets()) {
                break;
            }
            if (applyHit(attacker, stats, hit, candidate.entity(), random.getAsDouble())) {
                damaged++;
            }
        }
        return damaged;
    }

    /**
     * Who an attack may damage. Excludes the attacker, spectators, armor stands, the attacker's pets and mount/riders,
     * allies (same team) and players the attacker may not harm (server PvP setting, team friendly fire).
     */
    public static boolean isValidTarget(ServerPlayer attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive() || target.isSpectator() || !target.isAttackable()) {
            return false;
        }
        if (target instanceof ArmorStand || attacker.isPassengerOfSameVehicle(target)) {
            return false;
        }
        if (target instanceof OwnableEntity ownable) {
            UUID owner = ownable.getOwnerUUID();
            if (owner != null && owner.equals(attacker.getUUID())) {
                return false;
            }
        }
        if (attacker.isAlliedTo(target)) {
            return false;
        }
        return !(target instanceof Player other) || attacker.canHarmPlayer(other);
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer attacker, Vec3 eye, LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 closest = new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
        Vec3 center = box.getCenter();
        Vec3 head = new Vec3(center.x, Math.min(box.maxY - 0.1, target.getEyeY()), center.z);
        for (Vec3 point : new Vec3[]{closest, center, head}) {
            if (level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker))
                    .getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private static boolean applyHit(ServerPlayer attacker, CombatStats stats, CombatEvent.Hit hit, LivingEntity target,
                                    double random) {
        DamageRoll roll = DamageCalculator.roll(stats, hit.weapon(), hit.frame(), hit.damageScale(), random);
        float amount = (float) roll.amount();
        if (!(amount > 0.0F)) {
            return false;
        }
        // Combo hits land faster than vanilla's 10-tick hurt immunity (D-032).
        target.invulnerableTime = 0;
        boolean damaged;
        current = new ActiveHit(target, hit.frame().knockback());
        try {
            damaged = target.hurt(attacker.damageSources().playerAttack(attacker), amount);
        } finally {
            current = null;
        }
        if (!damaged) {
            return false;
        }
        attacker.setLastHurtMob(target);
        attacker.causeFoodExhaustion(0.1F);
        CombatEffects.targetHit(attacker, target, roll, hit);
        ERNetwork.sendToPlayer(attacker, new DamageNumberPacket(target.getId(), (float) target.getX(),
                (float) (target.getY() + target.getBbHeight() + 0.25), (float) target.getZ(), amount,
                DamageNumberPacket.flags(roll.critical(), hit.action().kind())));
        return true;
    }

    /** Replaces vanilla's fixed knockback of a hit with the action's own value (D-032). Forge bus, server thread. */
    public static void onKnockBack(LivingKnockBackEvent event) {
        ActiveHit hit = current;
        if (hit != null && event.getEntity() == hit.target()) {
            event.setStrength((float) hit.knockback());
        }
    }

    static Box toBox(AABB box) {
        return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
