package com.huntcraft.monster.large.b;

import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.HazardEntity;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared attack helpers for Gravadon / Azragos (module large.b). */
final class LBUtil {
    private LBUtil() {
    }

    /** Snaps a point to the nearest walkable ground surface (searches a few blocks up/down). */
    static Vec3 ground(Level level, Vec3 p) {
        int x = Mth.floor(p.x), z = Mth.floor(p.z), y0 = Mth.floor(p.y);
        for (int y = y0 + 3; y >= y0 - 6; y--) {
            BlockPos at = new BlockPos(x, y, z);
            BlockPos below = at.below();
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                    && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(p.x, y, p.z);
            }
        }
        return p;
    }

    /** Hazard at a local offset (side + = right, forward), snapped to the ground. Radius / damage scale with the monster. */
    static void hazard(HuntMonster m, HazardEntity.Kind kind, double side, double forward, float radius, int warmup, int active, float damage, Element element) {
        hazardAt(m, kind, m.localPos(side, 0, forward), radius, warmup, active, damage, element);
    }

    static void hazardAt(HuntMonster m, HazardEntity.Kind kind, Vec3 pos, float radius, int warmup, int active, float damage, Element element) {
        if (m.level().isClientSide) return;
        HazardEntity.spawn(m, ground(m.level(), pos), kind, radius * m.getMonsterScale(), warmup, active, damage * m.damageMultiplier(), element);
    }

    /** Sphere hit with damage flags (each entity at most once per action unless the hit set is cleared). */
    static TimelineAttack.Action sphere(double side, double up, double forward, float radius, float damage, Element element, float knockback, int flags) {
        return (m, target, t) -> m.hitSphere(m.localPos(side, up, forward), radius * m.getMonsterScale(), damage, element, knockback, flags);
    }

    /** Rotates the monster's facing (positive = turn right). */
    static void spin(HuntMonster m, float deg) {
        float y = m.getYRot() + deg;
        m.setYRot(y);
        m.yBodyRot = y;
        m.yHeadRot = y;
    }

    /** Debris of the ground block at a position. */
    static void dig(HuntMonster m, Vec3 at, int count) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        Vec3 g = ground(sl, at);
        BlockPos bp = BlockPos.containing(g.x, g.y - 0.5, g.z);
        BlockState st = sl.getBlockState(bp);
        if (st.isAir()) st = Blocks.DIRT.defaultBlockState();
        double s = m.getMonsterScale();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), g.x, g.y + 0.3, g.z, count, 0.7 * s, 0.35, 0.7 * s, 0.15);
    }

    /** Heavy ground impact: debris, dust and a small blast flash. */
    static void impact(HuntMonster m, Vec3 at, float radius) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        Vec3 g = ground(sl, at);
        dig(m, g, 50);
        double r = radius * m.getMonsterScale();
        sl.sendParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.5, g.z, 3, r * 0.4, 0.2, r * 0.4, 0);
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, g.x, g.y + 0.2, g.z, 10, r * 0.5, 0.1, r * 0.5, 0.01);
        sl.sendParticles(ParticleTypes.POOF, g.x, g.y + 0.3, g.z, 24, r * 0.6, 0.2, r * 0.6, 0.06);
    }

    /** Lobs a gravity projectile in a ballistic arc so it lands near the target (or straight ahead). */
    static void lob(HuntMonster m, @Nullable LivingEntity target, ProjectileSpec spec, Vec3 from, float yawOffsetDeg) {
        if (m.level().isClientSide) return;
        Vec3 aim = target != null ? target.position() : m.localPos(0, 0, 14);
        double dx = aim.x - from.x, dz = aim.z - from.z;
        double horiz = Math.max(2.0, Math.sqrt(dx * dx + dz * dz));
        double dy = aim.y - from.y;
        double v = Math.max(0.2, spec.speed), g = Math.max(0.005, spec.gravity);
        double s = Mth.clamp(horiz * 1.1 * g / (v * v), 0.0, 1.0);
        double angle = Mth.clamp(0.5 * Math.asin(s) + Math.atan2(dy, horiz) * 0.5, 0.15, 1.1);
        double yaw = Math.atan2(dz, dx) + Math.toRadians(yawOffsetDeg);
        Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(angle), Math.sin(angle), Math.sin(yaw) * Math.cos(angle));
        MonsterProjectile p = new MonsterProjectile(m.level(), m, spec, dir, null);
        p.setPos(from.x, from.y, from.z);
        m.level().addFreshEntity(p);
    }

    /** Particles streaming inward to a point from a sphere of the given radius (charge-up effect). */
    static void converge(ServerLevel sl, Vec3 center, double radius, ParticleOptions particle, int n, RandomSource r, double speed) {
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2;
            double k = Math.sqrt(1 - u * u);
            Vec3 d = new Vec3(k * Math.cos(a), u * 0.6, k * Math.sin(a));
            Vec3 p = center.add(d.scale(radius));
            // count 0: the offset is used as the particle velocity (scaled by speed)
            sl.sendParticles(particle, p.x, p.y, p.z, 0, -d.x, -d.y, -d.z, speed);
        }
    }

    /** Ring of particles on the ground. */
    static void ring(ServerLevel sl, Vec3 center, double radius, ParticleOptions particle, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sl.sendParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.2, center.z + Math.sin(a) * radius, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }
}
