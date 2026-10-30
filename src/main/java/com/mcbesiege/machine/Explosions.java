package com.mcbesiege.machine;

import com.mcbesiege.Config;
import com.mcbesiege.physics.Collider;
import com.mcbesiege.physics.RigidBody;
import com.mcbesiege.physics.V3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Explosions: vanilla blast (blocks / entities) plus physics impulses and part damage for every body nearby. */
public final class Explosions {
    private Explosions() {}

    public static void explode(ServerLevel level, V3 c, double power, Entity sourceEntity, Entity cause) {
        Level.ExplosionInteraction mode = Config.explosionsBreakBlocks() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(cause != null ? cause : sourceEntity, c.x, c.y, c.z, (float) power, mode);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        LevelPhysics lp = LevelPhysics.get(level);
        double radius = power * 2.0 + 1.0;
        for (RigidBody b : lp.world.bodies) {
            if (b.isStatic) continue;
            boolean touched = false;
            for (Collider col : b.colliders) {
                V3 d = col.wPos.sub(c);
                double dist = d.len();
                if (dist > radius) continue;
                double f = 1.0 - dist / radius;
                V3 dir = dist > 1e-3 ? d.mul(1.0 / dist) : V3.Y;
                double j = power * 4.0 * f * Math.max(col.mass, 0.05);
                b.applyImpulse(dir.mul(j), col.wPos);
                touched = true;
                if (col.userData instanceof MPart.Sub s) {
                    Machine m = lp.machineOf(b);
                    if (m != null) {
                        m.addDamage(s, power * 28 * f * f);
                        if (s.part.type.flammable && f > 0.4 && level.random.nextFloat() < 0.3f) m.ignite(s.part);
                        if (s.part.isExplosive() && s.part.fuse < 0) s.part.fuse = 2 + level.random.nextInt(4);
                    }
                }
            }
            if (touched) lp.world.wakeIsland(b);
        }
    }
}
