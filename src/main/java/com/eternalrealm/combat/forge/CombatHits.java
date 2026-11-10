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
import com.eternalrealm.job.PassiveTotals;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;
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

    /** Health fraction below which "last stand" passives work. */
    public static final double LAST_STAND_HEALTH = 0.5;
    /** Share of a thunder weapon hit that arcs to a second enemy. */
    private static final double THUNDER_ARC = 0.3;
    private static final double THUNDER_RANGE = 4.0;

    /**
     * Damage of one hit before the target's defenses: the formula of {@link DamageCalculator}, the held weapon's
     * bonus attack, the attacker's attack-up and critical-up effects and last-stand passives.
     */
    public static DamageRoll roll(ServerPlayer attacker, CombatStats stats, WeaponProfile weapon, HitFrame frame,
                                  double damageScale, DamageType type, double random) {
        double bonusAttack = WeaponBonus.bonusAttack(attacker, attacker.getMainHandItem());
        double extraCrit = Math.min(1.0, ERMobEffects.magnitude(attacker, ERMobEffects.CRIT_UP.get()));
        DamageRoll base = DamageCalculator.roll(stats, weapon, frame, damageScale, random, type, bonusAttack,
                extraCrit);
        PassiveTotals passive = passives(attacker);
        double lastStand = attacker.getHealth() < attacker.getMaxHealth() * LAST_STAND_HEALTH
                ? passive.lastStandDamage() : 0.0;
        double amount = Math.min(CombatRules.MAX_DAMAGE, base.amount() * ERMobEffects.attackMultiplier(attacker)
                * (1.0 + lastStand));
        return new DamageRoll(amount, base.critical());
    }

    /** The attacker's passive skill totals (none outside a running server, e.g. in some GameTests). */
    static PassiveTotals passives(ServerPlayer player) {
        return com.eternalrealm.server.ServerContext.current().map(context -> context.combat().passives(player))
                .orElse(PassiveTotals.NONE);
    }

    /** The roll with the frame's execute bonuses against a target below their health threshold. */
    static DamageRoll withExecute(DamageRoll roll, LivingEntity target, List<SkillEffect.Execute> executes) {
        double bonus = 0.0;
        for (SkillEffect.Execute execute : executes) {
            if (target.getHealth() < target.getMaxHealth() * execute.threshold()) {
                bonus += execute.bonus();
            }
        }
        return bonus <= 0.0 ? roll : new DamageRoll(Math.min(CombatRules.MAX_DAMAGE, roll.amount() * (1.0 + bonus)),
                roll.critical());
    }

    /** A melee frame: every valid target inside the volume, nearest first, up to the shape's target limit. */
    public static List<HitTarget> execute(ServerPlayer attacker, CombatStats stats, CombatEvent.Hit hit, DamageType type,
                                          DoubleSupplier random) {
        return execute(attacker, stats, hit, type, random, List.of(), null);
    }

    /**
     * A melee frame of a skill: as {@link #execute(ServerPlayer, CombatStats, CombatEvent.Hit, DamageType,
     * DoubleSupplier)}, with the frame's execute bonuses and the skill's theme on the swept volume.
     */
    public static List<HitTarget> execute(ServerPlayer attacker, CombatStats stats, CombatEvent.Hit hit, DamageType type,
                                          DoubleSupplier random, List<SkillEffect.Execute> executes, SkillTheme theme) {
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
        if (theme != null) {
            com.eternalrealm.job.forge.SkillVisuals.frame(attacker, theme, shape.reach(),
                    shape.kind() == HitShape.Kind.CIRCLE && shape.offsetForward() == 0.0);
        }
        for (Candidate candidate : inside) {
            if (damaged.size() >= shape.maxTargets()) {
                break;
            }
            DamageRoll roll = withExecute(roll(attacker, stats, hit.weapon(), hit.frame(), hit.damageScale(), type,
                    random.getAsDouble()), candidate.entity(), executes);
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
        float amount = deal(attacker, target, withExecute(payload.roll(), target, payload.executes()), payload.type(),
                payload.knockback(), payload.kind(), false);
        if (amount > 0.0F) {
            List<HitTarget> hit = List.of(new HitTarget(target, amount));
            Afflictions.apply(attacker, hit, payload.afflictions(), payload.effectScale(), attacker.position());
            siphon(attacker, hit, payload.siphons());
        }
    }

    /** An exploding projectile: full damage to every valid target within the radius (no block damage). */
    public static void explosion(ServerPlayer attacker, ServerLevel level, Vec3 at, ProjectilePayload payload) {
        double radius = payload.spec().explosionRadius();
        if (payload.theme() != null) {
            com.eternalrealm.job.forge.SkillVisuals.explosion(level, payload.theme(), at, radius);
        } else {
            level.sendParticles(radius >= 4.0 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y,
                    at.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, (int) (radius * 12), radius * 0.4, 0.4,
                    radius * 0.4, 0.05);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        List<HitTarget> damaged = new ArrayList<>();
        AABB area = new AABB(at, at).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> isValidTarget(attacker, candidate))) {
            if (entity.getBoundingBox().distanceToSqr(at) > radius * radius) {
                continue;
            }
            float amount = deal(attacker, entity, withExecute(payload.roll(), entity, payload.executes()),
                    payload.type(), payload.knockback(), payload.kind(), false);
            if (amount > 0.0F) {
                damaged.add(new HitTarget(entity, amount));
            }
        }
        Afflictions.apply(attacker, damaged, payload.afflictions(), payload.effectScale(), at);
        siphon(attacker, damaged, payload.siphons());
    }

    /** One pulse of a skill's ground area hit {@code target}. @return the damage dealt */
    public static float areaHit(ServerPlayer attacker, LivingEntity target, DamageRoll roll, DamageType type) {
        return deal(attacker, target, roll, type, 0.1, ActionKind.SKILL, false);
    }

    /** Heals the caster's party by the siphons' share of the damage in {@code damaged}. */
    public static void siphon(ServerPlayer attacker, List<HitTarget> damaged, List<SkillEffect.Siphon> siphons) {
        if (siphons.isEmpty() || damaged.isEmpty()) {
            return;
        }
        double total = 0.0;
        for (HitTarget hit : damaged) {
            total += hit.amount();
        }
        for (SkillEffect.Siphon siphon : siphons) {
            float heal = (float) (total * siphon.fraction());
            for (ServerPlayer ally : com.eternalrealm.job.forge.SkillEffectsExecutor.allies(attacker, siphon.radius())) {
                if (heal > 0.0F && ally.getHealth() < ally.getMaxHealth()) {
                    ally.heal(heal);
                    attacker.serverLevel().sendParticles(ERParticles.HEAL.get(), ally.getX(),
                            ally.getY() + ally.getBbHeight() + 0.2, ally.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
                }
            }
        }
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
        onHit(attacker, target, amount, melee);
        CombatEffects.targetHit(attacker, target, roll.critical(), kind);
        ERNetwork.sendToPlayer(attacker, new DamageNumberPacket(target.getId(), (float) target.getX(),
                (float) (target.getY() + target.getBbHeight() + 0.25), (float) target.getZ(), amount,
                DamageNumberPacket.flags(roll.critical(), kind)));
        return amount;
    }

    /**
     * What happens after a hit of a player landed: lifesteal (passives and the lifesteal buff), on-hit afflictions of
     * passives, and the elemental weapon buffs on melee hits (frost chills, venom poisons, thunder arcs to a second
     * enemy for {@value #THUNDER_ARC} of the damage; arcs never arc again).
     */
    private static void onHit(ServerPlayer attacker, LivingEntity target, float amount, boolean melee) {
        PassiveTotals passive = passives(attacker);
        double lifesteal = passive.lifesteal() + ERMobEffects.magnitude(attacker, ERMobEffects.LIFESTEAL.get());
        if (lifesteal > 0.0 && attacker.getHealth() < attacker.getMaxHealth()) {
            attacker.heal((float) (amount * Math.min(0.5, lifesteal)));
        }
        for (PassiveTotals.Proc proc : passive.procs()) {
            if ((!proc.meleeOnly() || melee) && attacker.getRandom().nextDouble() < proc.chance()) {
                Afflictions.apply(attacker, target, amount, proc.kind(), proc.durationTicks(), proc.power(), 1.0,
                        attacker.position());
            }
        }
        if (!melee || !target.isAlive()) {
            return;
        }
        if (attacker.hasEffect(ERMobEffects.FROST_WEAPON.get())) {
            Afflictions.apply(attacker, target, amount, SkillEffect.AfflictionKind.SLOW, 40, 2, 1.0, attacker.position());
            attacker.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
                    target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        if (attacker.hasEffect(ERMobEffects.VENOM_WEAPON.get())) {
            ERMobEffects.applyPoison(target, attacker, amount * 0.15F, 80);
        }
        if (attacker.hasEffect(ERMobEffects.THUNDER_WEAPON.get())) {
            ServerLevel level = attacker.serverLevel();
            LivingEntity second = null;
            double best = THUNDER_RANGE * THUNDER_RANGE;
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(THUNDER_RANGE), candidate -> candidate != target
                            && isValidTarget(attacker, candidate))) {
                double distance = other.distanceToSqr(target);
                if (distance < best) {
                    best = distance;
                    second = other;
                }
            }
            if (second != null) {
                Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0);
                Vec3 to = second.position().add(0, second.getBbHeight() * 0.6, 0);
                for (int i = 0; i <= 8; i++) {
                    Vec3 at = from.add(to.subtract(from).scale(i / 8.0));
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
                deal(attacker, second, new DamageRoll(amount * THUNDER_ARC, false), DamageType.MAGIC, 0.1,
                        ActionKind.SKILL, false);
            }
        }
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
