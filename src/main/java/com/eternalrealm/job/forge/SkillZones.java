package com.eternalrealm.job.forge;

import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.Afflictions;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Ground areas of skills ({@link SkillEffect.Zone}): burning ground, poison clouds, healing circles, traps and
 * delayed blasts. Each pulse damages the enemies inside with the caster's stats as they were when the area was made
 * (like a hit frame of the skill), heals the caster's party inside and shows the area. Server thread only; areas live
 * at most 30 seconds and vanish when their caster leaves.
 */
public final class SkillZones {
    private static final int MAX_ZONES = 512;
    private static final List<Active> ZONES = new ArrayList<>();

    private SkillZones() {
    }

    private static final class Active {
        final UUID caster;
        final ServerLevel level;
        final Vec3 center;
        final SkillEffect.Zone zone;
        final SkillTheme theme;
        final CombatStats stats;
        final WeaponProfile weapon;
        final DamageType type;
        final double damageScale;
        final double effectScale;
        final long firstPulse;
        int pulsesDone;

        Active(UUID caster, ServerLevel level, Vec3 center, SkillEffect.Zone zone, SkillTheme theme, CombatStats stats,
               WeaponProfile weapon, DamageType type, double damageScale, double effectScale, long firstPulse) {
            this.caster = caster;
            this.level = level;
            this.center = center;
            this.zone = zone;
            this.theme = theme;
            this.stats = stats;
            this.weapon = weapon;
            this.type = type;
            this.damageScale = damageScale;
            this.effectScale = effectScale;
            this.firstPulse = firstPulse;
        }
    }

    /** Creates the area of {@code zone} for {@code caster} now. */
    public static void create(ServerPlayer caster, CombatStats stats, WeaponProfile weapon, DamageType type,
                              SkillTheme theme, double damageScale, double effectScale, SkillEffect.Zone zone) {
        if (weapon == null || !caster.isAlive() || caster.isSpectator()) {
            return;
        }
        Vec3 center = switch (zone.placement()) {
            case CASTER -> caster.position();
            case AHEAD -> {
                double yaw = Math.toRadians(caster.getYRot());
                Vec3 ahead = caster.position().add(-Math.sin(yaw) * zone.distance(), 0.0, Math.cos(yaw) * zone.distance());
                yield new Vec3(ahead.x, SkillVisuals.groundY(caster.serverLevel(), ahead.add(0, 1.0, 0)), ahead.z);
            }
            case LOOK -> SkillVisuals.lookPoint(caster, zone.distance());
        };
        if (ZONES.size() >= MAX_ZONES) {
            ZONES.remove(0);
        }
        ZONES.add(new Active(caster.getUUID(), caster.serverLevel(), center, zone, theme, stats, weapon, type,
                damageScale, effectScale, caster.server.getTickCount() + zone.delayTicks()));
        SkillVisuals.zonePulse(caster.serverLevel(), theme, center, zone.radius(), zone.healFraction() > 0.0);
    }

    /** Pulses due areas (once per server tick). */
    public static void tick(MinecraftServer server) {
        if (ZONES.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        Iterator<Active> iterator = ZONES.iterator();
        while (iterator.hasNext()) {
            Active area = iterator.next();
            ServerPlayer caster = server.getPlayerList().getPlayer(area.caster);
            if (caster == null || caster.serverLevel() != area.level) {
                iterator.remove();
                continue;
            }
            long due = area.firstPulse + (long) area.pulsesDone * area.zone.intervalTicks();
            if (now < due) {
                continue;
            }
            try {
                pulse(caster, area);
            } catch (RuntimeException e) {
                iterator.remove();
                continue;
            }
            area.pulsesDone++;
            if (area.pulsesDone >= area.zone.pulses()) {
                iterator.remove();
            }
        }
    }

    private static void pulse(ServerPlayer caster, Active area) {
        SkillEffect.Zone zone = area.zone;
        double radius = zone.radius();
        SkillVisuals.zonePulse(area.level, area.theme, area.center, radius, zone.healFraction() > 0.0);
        if (zone.damageMultiplier() > 0.0) {
            AABB box = new AABB(area.center, area.center).inflate(radius, 2.5, radius);
            HitFrame frame = new HitFrame(0, HitFrame.NO_SHAPE, zone.damageMultiplier(), 0.1);
            List<CombatHits.HitTarget> damaged = new ArrayList<>();
            for (LivingEntity entity : area.level.getEntitiesOfClass(LivingEntity.class, box,
                    candidate -> CombatHits.isValidTarget(caster, candidate))) {
                double dx = entity.getX() - area.center.x;
                double dz = entity.getZ() - area.center.z;
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                DamageRoll roll = CombatHits.roll(caster, area.stats, area.weapon, frame, area.damageScale, area.type,
                        caster.getRandom().nextDouble());
                float dealt = CombatHits.areaHit(caster, entity, roll, area.type);
                if (dealt > 0.0F) {
                    damaged.add(new CombatHits.HitTarget(entity, dealt));
                }
            }
            if (zone.affliction() != null && !damaged.isEmpty()) {
                for (CombatHits.HitTarget hit : damaged) {
                    Afflictions.apply(caster, hit.entity(), hit.amount(), zone.affliction(), zone.afflictionTicks(),
                            zone.afflictionPower(), area.effectScale, area.center);
                }
            }
        }
        if (zone.healFraction() > 0.0) {
            double heal = zone.healFraction() * area.effectScale;
            for (ServerPlayer ally : SkillEffectsExecutor.allies(caster, 64.0)) {
                double dx = ally.getX() - area.center.x;
                double dz = ally.getZ() - area.center.z;
                if (dx * dx + dz * dz <= radius * radius && Math.abs(ally.getY() - area.center.y) <= 3.0) {
                    ally.heal((float) DamageCalculator.healing(area.stats, ally.getMaxHealth(),
                            Math.min(1.0, heal), 0.0));
                }
            }
        }
    }

    /** Forgets every area (server stop). */
    public static void clear() {
        ZONES.clear();
    }
}
