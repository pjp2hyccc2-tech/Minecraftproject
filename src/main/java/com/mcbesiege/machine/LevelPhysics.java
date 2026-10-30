package com.mcbesiege.machine;

import com.mcbesiege.Config;
import com.mcbesiege.entity.CannonballEntity;
import com.mcbesiege.physics.Collider;
import com.mcbesiege.physics.Joint;
import com.mcbesiege.physics.PhysicsListener;
import com.mcbesiege.physics.PhysicsWorld;
import com.mcbesiege.physics.RigidBody;
import com.mcbesiege.physics.StaticBox;
import com.mcbesiege.physics.V3;
import com.mcbesiege.part.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One physics world per dimension, shared by all machines and cannonballs in it. */
public final class LevelPhysics implements PhysicsListener {
    private static final Map<ResourceKey<Level>, LevelPhysics> LEVELS = new HashMap<>();

    public final ServerLevel level;
    public final PhysicsWorld world = new PhysicsWorld();
    public final McWorldGeometry geometry;
    public final List<Machine> machines = new ArrayList<>();
    public final List<CannonballEntity> balls = new ArrayList<>();
    private final Set<BlockPos> pendingBreaks = new LinkedHashSet<>();
    public long tickCount;

    private LevelPhysics(ServerLevel level) {
        this.level = level;
        this.geometry = new McWorldGeometry(level);
        world.worldGeometry = geometry;
        world.listener = this;
        world.filter = (a, b) -> {
            if (a.userData instanceof MPart.Sub sa && b.userData instanceof MPart.Sub sb && sa.body != null && sb.body != null) {
                Machine ma = owner(sa), mb = owner(sb);
                if (ma != null && ma == mb) return !ma.noCollide.contains(Machine.pair(sa.body.rb, sb.body.rb));
            }
            return true;
        };
    }

    public static LevelPhysics get(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new LevelPhysics(level));
    }

    public static LevelPhysics peek(ServerLevel level) {
        LevelPhysics lp = LEVELS.get(level.dimension());
        return lp != null && lp.level == level ? lp : null;
    }

    public static void clearAll() {
        LEVELS.clear();
    }

    private Machine owner(MPart.Sub s) {
        for (Machine m : machines) if (m.bodies.contains(s.body)) return m;
        return null;
    }

    public Machine machineOf(RigidBody rb) {
        if (rb == null || !(rb.userData instanceof MBody mb)) return null;
        for (Machine m : machines) if (m.bodies.contains(mb)) return m;
        return null;
    }

    public void tick() {
        tickCount++;
        if (machines.isEmpty() && balls.isEmpty()) return;
        world.gravity = new V3(0, -Config.gravity(), 0);
        world.substeps = Config.substeps();
        world.iterations = Config.iterations();
        for (Machine m : new ArrayList<>(machines)) m.preStep();
        for (CannonballEntity b : new ArrayList<>(balls)) b.preStep();
        world.step(0.05);
        for (Machine m : new ArrayList<>(machines)) m.postStep();
        for (CannonballEntity b : new ArrayList<>(balls)) b.postStep();
        flushBreaks();
        for (Machine m : new ArrayList<>(machines)) m.entity.syncAfterStep();
    }

    // ---------------------------------------------------------------------------------------------------- blocks

    public boolean canBreak(BlockPos pos, double energy) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getCollisionShape(level, pos).isEmpty()) return false;
        float hardness = s.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > 40) return false;
        if (s.getBlock() instanceof com.mcbesiege.block.MachinePartBlock) return false;
        return energy > breakEnergy(hardness);
    }

    public static double breakEnergy(float hardness) {
        return (25 + 35 * hardness) * Config.blockToughness();
    }

    public void queueBreak(BlockPos pos) {
        if (pendingBreaks.size() < 256) pendingBreaks.add(pos.immutable());
    }

    private void flushBreaks() {
        if (pendingBreaks.isEmpty()) return;
        boolean drops = Config.blockDrops();
        for (BlockPos p : pendingBreaks) {
            if (!level.isLoaded(p)) continue;
            level.destroyBlock(p, drops);
        }
        pendingBreaks.clear();
    }

    // ---------------------------------------------------------------------------------------------------- listener

    @Override
    public boolean onWorldImpact(RigidBody body, Collider c, StaticBox box, V3 point, V3 normal, double speed, double effMass) {
        double mul = 1.0;
        if (c.userData instanceof MPart.Sub s) {
            Machine m = owner(s);
            if (m != null && speed > 4) {
                double e = 0.5 * effMass * (speed - 4) * (speed - 4);
                m.addDamage(s, e * Config.impactDamage());
                if (s.part.isExplosive() && 0.5 * effMass * speed * speed > 25) s.part.fuse = 1;
            }
            mul = s.part.type.damageMul > 1 ? 1.5 : 1.0;
        } else if (body.userData instanceof CannonballEntity) {
            mul = 1.8;
        }
        if (Config.machinesBreakBlocks() && speed > 5) {
            double energy = 0.5 * effMass * speed * speed * mul;
            BlockPos first = BlockPos.containing(point.x + normal.x * 0.05, point.y + normal.y * 0.05, point.z + normal.z * 0.05);
            BlockPos p = first;
            for (int i = 0; i < 4 && canBreak(p, energy); i++) {
                BlockState st = level.getBlockState(p);
                energy -= breakEnergy(st.getDestroySpeed(level, p));
                queueBreak(p);
                p = BlockPos.containing(p.getX() + 0.5 + normal.x, p.getY() + 0.5 + normal.y, p.getZ() + 0.5 + normal.z);
            }
        }
        return true;
    }

    @Override
    public void onBodyImpact(RigidBody a, Collider ca, RigidBody b, Collider cb, V3 point, V3 normal, double speed, double effMass) {
        if (speed <= 4) return;
        double e = 0.5 * effMass * (speed - 4) * (speed - 4) * Config.impactDamage();
        damageFrom(ca, e, effMass, speed, cb);
        damageFrom(cb, e, effMass, speed, ca);
    }

    private void damageFrom(Collider c, double e, double effMass, double speed, Collider other) {
        if (!(c.userData instanceof MPart.Sub s)) return;
        Machine m = owner(s);
        if (m == null) return;
        double mul = 1.0;
        if (other != null && other.userData instanceof MPart.Sub os) mul = os.part.type.damageMul;
        m.addDamage(s, e * mul);
        if (s.part.isExplosive() && 0.5 * effMass * speed * speed > 25) s.part.fuse = 1;
        if (s.part.type == PartType.BALLOON) m.addDamage(s, 10);
    }

    @Override
    public void onJointBroken(Joint j) {
        Machine m = machineOf(j.a);
        if (m == null) m = machineOf(j.b);
        if (m != null) m.onJointBroken(j);
        else {
            // grabber joints and others
            for (Machine mm : machines) {
                for (MPart p : mm.parts) {
                    if (p.grab == j) {
                        p.grab = null;
                        p.grabCooldown = 20;
                    }
                }
            }
        }
    }
}
