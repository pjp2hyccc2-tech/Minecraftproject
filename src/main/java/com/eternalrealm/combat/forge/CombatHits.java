package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.AttackOrigin;
import com.eternalrealm.combat.Box;
import com.eternalrealm.combat.CombatEvent;
import com.eternalrealm.combat.CombatRules;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitGeometry;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.network.forge.DamageNumberPacket;
import com.eternalrealm.network.forge.ERNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
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
 * Turns hit frames and projectile impacts into damage on the server: finds the targets (server positions, server
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

    /** A target that took damage, and how much (afflictions such as bleeding scale with it). */
    public record HitTarget(LivingEntity entity, float amount) {
    }

    /**
     * Damage of one hit before the target's defenses: the formula of {@link DamageCalculator}, the held weapon's
     * bonus attack, and the attacker's attack-up effect.
     */
    public static DamageRoll roll(ServerPlayer attacker, CombatStats stats, WeaponProfile weapon, HitFrame frame,
                                  double damageScale, DamageType type, double random) {
        double bonusAttack = WeaponBonus.bonusAttack(attacker, attacker.getMainHandItem());
        DamageRoll base = DamageCalculator.roll(stats, weapon, frame, damageScale, random, type, bonusAttack);
        double amount = Math.min(CombatRules.MAX_DAMAGE, base.amount() * ERMobEffects.attackMultiplier(attacker));
        return new DamageRoll(amount, base.critical());
    }

    /** A melee frame: every valid target inside the volume, nearest first, up to the shape's target limit. */
    public static List<HitTarget> execute(ServerPlayer attacker, CombatStats stats, CombatEvent.Hit hit, DamageType type,
                                          DoubleSupplier random) {
        List<HitTarget> damaged = new ArrayList<>();
        if (!attacker.isAlive() || attacker.isSpectator()) {
            return damaged;
        }
        ServerLevel level = attacker.serverLevel();
        HitShape shape = hit.frame().shape();
        Vec3 eye = attacker.getEyePosition();
        AttackOrigin origin = new AttackOrigin(eye.x, eye.y, eye.z, attacker.getYRot(), attacker.getXRot());

        AABB search = attacker.getBoundingBox().inflate(shape.reach() + shape.offsetForward() + 2.0);
        List<Candidate> inside = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, search,
                candidate -> isValidTarget(attacker, candidate))) {
            Box box = toBox(entity.getBoundingBox());
            if (HitGeometry.contains(shape, origin, box)
                    && (shape.offsetForward() > 0.0 || hasLineOfSight(level, attacker, eye, entity))) {
                inside.add(new Candidate(entity, HitGeometry.distanceSquared(origin, box)));
            }
        }
        inside.sort(Comparator.comparingDouble(Candidate::distanceSquared));

        CombatEffects.hitFrame(attacker, hit);
        for (Candidate candidate : inside) {
            if (damaged.size() >= shape.maxTargets()) {
                break;
            }
            DamageRoll roll = roll(attacker, stats, hit.weapon(), hit.frame(), hit.damageScale(), type,
                    random.getAsDouble());
            float amount = deal(attacker, candidate.entity(), roll, type, hit.frame().knockback(),
                    hit.action().kind(), true);
            if (amount > 0.0F) {
                damaged.add(new HitTarget(candidate.entity(), amount));
            }
        }
        return damaged;
    }

    /** A projectile hit one target. */
    public static void projectileHit(ServerPlayer attacker, LivingEntity target, ProjectilePayload payload) {
        float amount = deal(attacker, target, payload.roll(), payload.type(), payload.knockback(), payload.kind(), false);
        if (amount > 0.0F) {
            Afflictions.apply(attacker, List.of(new HitTarget(target, amount)), payload.afflictions());
        }
    }

    /** An exploding projectile: full damage to every valid target within the radius (no block damage). */
    public static void explosion(ServerPlayer attacker, ServerLevel level, Vec3 at, ProjectilePayload payload) {
        double radius = payload.spec().explosionRadius();
        level.sendParticles(radius >= 4.0 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y, at.z,
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, (int) (radius * 12), radius * 0.4, 0.4,
                radius * 0.4, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.2F);
        List<HitTarget> damaged = new ArrayList<>();
        AABB area = new AABB(at, at).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> isValidTarget(attacker, candidate))) {
            if (entity.getBoundingBox().distanceToSqr(at) > radius * radius) {
                continue;
            }
            float amount = deal(attacker, entity, payload.roll(), payload.type(), payload.knockback(), payload.kind(),
                    false);
            if (amount > 0.0F) {
                damaged.add(new HitTarget(entity, amount));
            }
        }
        Afflictions.apply(attacker, damaged, payload.afflictions());
    }

    /**
     * Applies one rolled hit: holy damage is doubled against undead, vanilla's hurt immunity is skipped (combo hits
     * land faster than it, D-032), the action's knockback replaces vanilla's, a flame weapon ignites melee targets.
     *
     * @return the damage dealt, 0 if the hit did nothing
     */
    static float deal(ServerPlayer attacker, LivingEntity target, DamageRoll roll, DamageType type, double knockback,
                      ActionKind kind, boolean melee) {
        float amount = (float) roll.amount();
        if (type == DamageType.HOLY && target.getMobType() == MobType.UNDEAD) {
            amount *= 2.0F;
        }
        if (!(amount > 0.0F) || !target.isAlive()) {
            return 0.0F;
        }
        target.invulnerableTime = 0;
        boolean damaged;
        current = new ActiveHit(target, knockback);
        try {
            damaged = target.hurt(attacker.damageSources().playerAttack(attacker), amount);
        } finally {
            current = null;
        }
        if (!damaged) {
            return 0.0F;
        }
        attacker.setLastHurtMob(target);
        attacker.causeFoodExhaustion(0.1F);
        float dealt = amount;
        com.eternalrealm.server.ServerContext.current()
                .ifPresent(context -> context.game().rankings().onDamageDealt(attacker, dealt));
        if (melee && attacker.hasEffect(ERMobEffects.FLAME_WEAPON.get())) {
            target.setSecondsOnFire(4);
        }
        CombatEffects.targetHit(attacker, target, roll.critical(), kind);
        ERNetwork.sendToPlayer(attacker, new DamageNumberPacket(target.getId(), (float) target.getX(),
                (float) (target.getY() + target.getBbHeight() + 0.25), (float) target.getZ(), amount,
                DamageNumberPacket.flags(roll.critical(), kind)));
        return amount;
    }

    /**
     * Who an attack may damage. Excludes the attacker, spectators, armor stands, the attacker's pets and mount/riders,
     * allies (same team) and players the attacker may not harm (PvP rules, team friendly fire).
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
        if (!(target instanceof Player other)) {
            return true;
        }
        return other instanceof ServerPlayer opponent && attacker.canHarmPlayer(opponent)
                && PvpRules.canHurt(attacker, opponent);
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
