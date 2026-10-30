package com.mcbesiege.machine;

import com.mcbesiege.Config;
import com.mcbesiege.entity.CannonballEntity;
import com.mcbesiege.part.PartConfig;
import com.mcbesiege.part.PartType;
import com.mcbesiege.physics.Collider;
import com.mcbesiege.physics.Joints;
import com.mcbesiege.physics.RigidBody;
import com.mcbesiege.physics.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Per-tick logic of every powered / special part. */
public final class Behaviors {
    private Behaviors() {}

    static final double WHEEL_SPEED = 8.0;   // rad/s at speed 1
    static final double STEER_RATE = 2.0;    // rad/s at speed 1
    static final double SPIN_SPEED = 10.0;
    static final double BLADE_SPEED = 25.0;

    static V3 facing(MPart p, boolean head) {
        MPart.Sub s = head && p.head != null ? p.head : p.base;
        return s.body.rb.rot.rotate(p.facingVec);
    }

    static V3 cell(MPart.Sub s) { return s.worldCellCenter(); }

    static void tick(Machine m, MPart p) {
        if (p.base.body == null) return;
        switch (p.type) {
            case WHEEL, IRON_WHEEL, LARGE_TIRE -> wheel(m, p);
            case SMALL_WHEEL -> {
                if (p.joint instanceof Joints.Hinge h) h.frictionTorque = 0.05;
            }
            case STEERING_HINGE -> steering(m, p, true);
            case STEERING_BLOCK -> steering(m, p, false);
            case SPINNING_BLOCK, SAW_BLADE, DRILL -> spinner(m, p);
            case HINGE -> passiveHinge(p);
            case SUSPENSION -> suspension(p);
            case PISTON -> piston(m, p);
            case DECOUPLER -> decoupler(m, p);
            case GRABBER -> grabber(m, p);
            case CANNON -> cannon(m, p);
            case CROSSBOW -> crossbow(m, p);
            case FLAMETHROWER -> flamethrower(m, p);
            case BOMB, POWDER_BARREL -> bomb(m, p);
            case BOULDER -> boulder(m, p);
            case ROCKET -> rocket(m, p);
            case PROPELLER -> propeller(p);
            case WING -> wing(p);
            case FLYING_BLOCK -> flyingBlock(m, p);
            case BALLOON -> balloon(p);
            case TIMER -> timer(m, p);
            case SENSOR -> sensor(m, p);
            case ALTIMETER -> altimeter(m, p);
            case LOGIC_GATE -> logic(m, p);
            default -> {}
        }
        if (p.type == PartType.DRILL && p.active) drillBlocks(m, p);
    }

    // ------------------------------------------------------------------------------------------------ locomotion

    static void wheel(Machine m, MPart p) {
        if (!(p.joint instanceof Joints.Hinge h)) return;
        PartConfig c = p.cfg;
        if (!c.toggle("powered")) {
            h.motorEnabled = false;
            h.frictionTorque = 0.2;
            return;
        }
        int dir;
        if (c.toggle("toggle")) {
            if (p.pressed(m, "forward", 0)) p.value = p.value == 1 ? 0 : 1;
            if (p.pressed(m, "backward", 1)) p.value = p.value == -1 ? 0 : -1;
            dir = (int) p.value;
        } else {
            dir = (p.keyDown(m, "forward") ? 1 : 0) - (p.keyDown(m, "backward") ? 1 : 0);
        }
        if (dir == 0 && c.toggle("automatic")) dir = 1;
        double speed = c.slider("speed") * WHEEL_SPEED * p.autoSign * (c.toggle("flip") ? -1 : 1);
        h.frictionTorque = 0;
        if (dir != 0) {
            h.motorEnabled = true;
            h.motorSpeed = dir * speed;
            h.maxMotorTorque = p.type.torque;
            m.markActive();
        } else if (c.toggle("autobrake")) {
            h.motorEnabled = true;
            h.motorSpeed = 0;
            h.maxMotorTorque = p.type.torque * 0.6;
        } else {
            h.motorEnabled = false;
            h.frictionTorque = 0.1;
        }
    }

    // ------------------------------------------------------------------------------------------------ mechanical

    static void steering(Machine m, MPart p, boolean limited) {
        if (!(p.joint instanceof Joints.Hinge h)) return;
        double rate = p.cfg.slider("speed") * STEER_RATE;
        boolean l = p.keyDown(m, "left"), r = p.keyDown(m, "right");
        double target = p.value;
        double step = rate * 0.05;
        if (l && !r) target += step;
        else if (r && !l) target -= step;
        else if (limited && p.cfg.toggle("return")) {
            if (Math.abs(target) <= step) target = 0;
            else target -= Math.signum(target) * step;
        }
        if (limited) {
            double lim = Math.toRadians(p.cfg.slider("limit"));
            target = Math.max(-lim, Math.min(lim, target));
        }
        p.value = target;
        double sign = p.autoSign * (p.cfg.toggle("flip") ? -1 : 1);
        h.servoEnabled = true;
        h.servoTarget = target * sign;
        h.servoMaxSpeed = Math.max(0.5, rate * 1.6);
        h.servoGain = 20;
        h.maxServoTorque = 350 * Config.jointStrength();
        if (l || r) m.markActive();
    }

    static void spinner(Machine m, MPart p) {
        if (!(p.joint instanceof Joints.Hinge h)) return;
        boolean on;
        if (p.cfg.toggle("automatic")) on = true;
        else if (p.cfg.toggle("toggle")) {
            if (p.pressed(m, "spin", 0)) p.toggled = !p.toggled;
            on = p.toggled;
        } else on = p.keyDown(m, "spin");
        double base = p.type == PartType.SPINNING_BLOCK ? SPIN_SPEED : BLADE_SPEED;
        if (on) {
            h.motorEnabled = true;
            h.motorSpeed = p.cfg.slider("speed") * base * (p.cfg.toggle("flip") ? -1 : 1);
            h.maxMotorTorque = p.type.torque;
            h.frictionTorque = 0;
            if (Math.abs(h.motorSpeed) > 0.01) m.markActive();
        } else {
            h.motorEnabled = false;
            h.frictionTorque = 0.4;
        }
        p.active = on && Math.abs(h.angularSpeed()) > 3;
    }

    static void passiveHinge(MPart p) {
        if (!(p.joint instanceof Joints.Hinge h)) return;
        h.frictionTorque = 0.12;
        if (p.cfg.toggle("limits")) {
            double a = Math.toRadians(p.cfg.slider("angle"));
            h.limitEnabled = true;
            h.lower = -a;
            h.upper = a;
        } else h.limitEnabled = false;
    }

    static void suspension(MPart p) {
        if (!(p.joint instanceof Joints.Slider s)) return;
        s.springEnabled = true;
        s.springTarget = 0;
        s.springStiffness = 140 * p.cfg.slider("spring");
        s.springDampingCoeff = 2 + 6 * p.cfg.slider("damper");
        s.limitEnabled = true;
    }

    static void piston(Machine m, MPart p) {
        if (!(p.joint instanceof Joints.Slider s)) return;
        boolean ext;
        if (p.cfg.toggle("toggle")) {
            if (p.pressed(m, "extend", 0)) p.toggled = !p.toggled;
            ext = p.toggled;
        } else ext = p.keyDown(m, "extend");
        s.servoEnabled = true;
        s.servoTarget = ext ? 1.0 : 0.0;
        s.servoMaxSpeed = p.cfg.slider("speed") * 2.0;
        s.maxServoForce = 900 * Config.jointStrength();
        s.limitEnabled = true;
        boolean was = p.value > 0.5;
        if (ext != was) {
            V3 c = cell(p.base);
            m.level.playSound(null, BlockPos.containing(c.x, c.y, c.z), ext ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 1.1f);
            p.value = ext ? 1 : 0;
            m.markActive();
        }
    }

    static void decoupler(Machine m, MPart p) {
        if (p.joint == null || p.jointBroken) return;
        if (p.pressed(m, "detach", 0)) {
            double f = p.cfg.slider("force") * 3.0;
            V3 axis = facing(p, false);
            V3 c = cell(p.base);
            RigidBody a = p.base.rb(), b = p.head.rb();
            m.breakJoint(p);
            if (a != null) a.applyImpulse(axis.mul(-f), c);
            if (b != null) b.applyImpulse(axis.mul(f), c);
            m.level.playSound(null, BlockPos.containing(c.x, c.y, c.z), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.5f, 1.8f);
            m.level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 10, 0.2, 0.2, 0.2, 0.03);
        }
    }

    static void grabber(Machine m, MPart p) {
        if (p.grabCooldown > 0) p.grabCooldown--;
        if (p.pressed(m, "release", 0) && p.grab != null) {
            m.physics.world.removeJoint(p.grab);
            p.grab = null;
            p.grabCooldown = 20;
        }
    }

    static void boulder(Machine m, MPart p) {
        if (p.joint == null || p.jointBroken) return;
        if (p.pressed(m, "release", 0)) {
            m.breakJoint(p);
            V3 c = cell(p.head);
            m.level.playSound(null, BlockPos.containing(c.x, c.y, c.z), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 0.9f);
        }
    }

    // ------------------------------------------------------------------------------------------------ weapons

    static void cannon(Machine m, MPart p) {
        if (p.pressed(m, "fire", 0) && p.cooldown == 0) {
            double power = p.cfg.slider("power");
            V3 dir = facing(p, false);
            V3 muzzle = cell(p.base).addScaled(dir, 0.8);
            RigidBody rb = p.base.rb();
            double speed = 34 * power;
            V3 vel = rb.pointVelocity(muzzle).addScaled(dir, speed);
            CannonballEntity.spawn(m.level, muzzle, vel, m.ownerPlayer(), 1.0, power);
            rb.applyImpulse(dir.mul(-speed * 0.55), muzzle);
            m.physics.world.wakeIsland(rb);
            p.cooldown = 16;
            BlockPos bp = BlockPos.containing(muzzle.x, muzzle.y, muzzle.z);
            m.level.playSound(null, bp, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.6f, 1.5f);
            m.level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.15, 0.15, 0.15, 0.05);
            m.level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.05);
            m.markActive();
        }
    }

    static void crossbow(Machine m, MPart p) {
        if (p.pressed(m, "fire", 0) && p.cooldown == 0 && p.ammo > 0) {
            double power = p.cfg.slider("power");
            V3 dir = facing(p, false);
            V3 muzzle = cell(p.base).addScaled(dir, 0.75);
            Arrow arrow = new Arrow(m.level, muzzle.x, muzzle.y, muzzle.z);
            ServerPlayer owner = m.ownerPlayer();
            if (owner != null) arrow.setOwner(owner);
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            arrow.setBaseDamage(2.5 + power * 1.5);
            arrow.shoot(dir.x, dir.y, dir.z, (float) (3.0 * power), 0.5f);
            V3 pv = p.base.rb().pointVelocity(muzzle).mul(0.05);
            arrow.setDeltaMovement(arrow.getDeltaMovement().add(pv.x, pv.y, pv.z));
            m.level.addFreshEntity(arrow);
            p.ammo--;
            p.cooldown = 8;
            m.level.playSound(null, BlockPos.containing(muzzle.x, muzzle.y, muzzle.z), SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.0f, 1.0f);
            p.base.rb().applyImpulse(dir.mul(-0.5), muzzle);
        }
    }

    static void flamethrower(Machine m, MPart p) {
        boolean on;
        if (p.cfg.toggle("toggle")) {
            if (p.pressed(m, "fire", 0)) p.toggled = !p.toggled;
            on = p.toggled;
        } else on = p.keyDown(m, "fire");
        p.active = on && p.fuel > 0;
        if (!p.active) return;
        p.fuel -= 1;
        m.markActive();
        ServerLevel level = m.level;
        V3 dir = facing(p, false);
        V3 o = cell(p.base).addScaled(dir, 0.65);
        for (int i = 0; i < 5; i++) {
            double sx = dir.x + (level.random.nextDouble() - 0.5) * 0.25;
            double sy = dir.y + (level.random.nextDouble() - 0.5) * 0.25;
            double sz = dir.z + (level.random.nextDouble() - 0.5) * 0.25;
            level.sendParticles(ParticleTypes.FLAME, o.x, o.y, o.z, 0, sx, sy, sz, 0.35 + level.random.nextDouble() * 0.2);
        }
        if (m.ticks % 4 == 0) {
            level.playSound(null, BlockPos.containing(o.x, o.y, o.z), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4f, 0.7f + level.random.nextFloat() * 0.3f);
        }
        if (m.ticks % 2 != 0) return;
        for (int d = 1; d <= 5; d++) {
            V3 pt = o.addScaled(dir, d);
            double r = 0.35 + d * 0.22;
            BlockPos bp = BlockPos.containing(pt.x, pt.y, pt.z);
            AABB box = new AABB(pt.x - r, pt.y - r, pt.z - r, pt.x + r, pt.y + r, pt.z + r);
            for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive())) {
                e.setSecondsOnFire(5);
                e.hurt(level.damageSources().inFire(), 1.5f);
            }
            for (Machine other : m.physics.machines) {
                for (MPart q : other.parts) {
                    if (!q.alive || !q.type.flammable || q.burning || q.base.body == null) continue;
                    if (other == m && q == p) continue;
                    if (q.base.worldCellCenter().distSq(pt) < r * r) other.ignite(q);
                }
            }
            if (level.getBlockState(bp).isSolidRender(level, bp)) break;
            if (Config.fireGriefing() && level.random.nextInt(8) == 0 && level.isEmptyBlock(bp)) {
                if (BaseFireBlock.canBePlacedAt(level, bp, net.minecraft.core.Direction.UP)) {
                    level.setBlockAndUpdate(bp, BaseFireBlock.getState(level, bp));
                }
            }
        }
    }

    static void bomb(Machine m, MPart p) {
        if (p.type == PartType.BOMB && p.pressed(m, "detonate", 0)) {
            m.explode(p);
            return;
        }
        if (p.fuse > 0) {
            p.fuse--;
            V3 c = cell(p.base);
            if (p.fuse % 4 == 0) m.level.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.5, c.z, 1, 0.05, 0.05, 0.05, 0.01);
            if (p.fuse == 0) m.explode(p);
        }
    }

    static void rocket(Machine m, MPart p) {
        if (!p.used && p.pressed(m, "launch", 0)) {
            p.used = true;
            p.active = true;
            p.timer = (int) (p.cfg.slider("duration") * 20);
            V3 c = cell(p.base);
            m.level.playSound(null, BlockPos.containing(c.x, c.y, c.z), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        if (!p.active) return;
        V3 dir = facing(p, false);
        V3 c = cell(p.base);
        RigidBody rb = p.base.rb();
        rb.addForce(dir.mul(60 * p.cfg.slider("thrust")), c);
        V3 tail = c.addScaled(dir, -0.6);
        m.level.sendParticles(ParticleTypes.FIREWORK, tail.x, tail.y, tail.z, 2, 0.05, 0.05, 0.05, 0.02);
        m.level.sendParticles(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 1, 0.05, 0.05, 0.05, 0.01);
        m.markActive();
        if (--p.timer <= 0) {
            p.active = false;
            if (p.cfg.toggle("explode")) {
                Explosions.explode(m.level, c, 2.0, m.entity, m.ownerPlayer());
                m.destroyPart(p);
            }
        }
    }

    static void drillBlocks(Machine m, MPart p) {
        if (!Config.machinesBreakBlocks() || p.head == null || p.head.body == null || m.ticks % 6 != 0) return;
        V3 dir = facing(p, true);
        V3 tip = cell(p.head).addScaled(dir, 0.95);
        BlockPos bp = BlockPos.containing(tip.x, tip.y, tip.z);
        if (m.physics.canBreak(bp, 25)) m.physics.queueBreak(bp);
    }

    // ------------------------------------------------------------------------------------------------ flight

    static void propeller(MPart p) {
        RigidBody rb = p.base.rb();
        V3 axis = facing(p, false);
        double w = rb.angVel.dot(axis);
        double thrust = 0.11 * w * Math.abs(w) * (p.cfg.toggle("flip") ? -1 : 1);
        thrust = Math.max(-120, Math.min(120, thrust));
        if (Math.abs(thrust) > 0.01) rb.addForce(axis.mul(thrust), cell(p.base));
    }

    static void wing(MPart p) {
        RigidBody rb = p.base.rb();
        V3 n = facing(p, false);
        V3 c = cell(p.base);
        double vn = rb.pointVelocity(c).dot(n);
        double f = -0.7 * vn * Math.abs(vn);
        f = Math.max(-250, Math.min(250, f));
        if (Math.abs(f) > 0.01) rb.addForce(n.mul(f), c);
    }

    static void flyingBlock(Machine m, MPart p) {
        boolean on;
        if (p.cfg.toggle("automatic")) on = true;
        else if (p.cfg.toggle("toggle")) {
            if (p.pressed(m, "thrust", 0)) p.toggled = !p.toggled;
            on = p.toggled;
        } else on = p.keyDown(m, "thrust");
        p.active = on;
        if (!on) return;
        V3 dir = facing(p, false);
        V3 c = cell(p.base);
        p.base.rb().addForce(dir.mul(26 * p.cfg.slider("power")), c);
        if (m.ticks % 2 == 0) {
            V3 t = c.addScaled(dir, -0.55);
            m.level.sendParticles(ParticleTypes.FIREWORK, t.x, t.y, t.z, 1, 0.08, 0.08, 0.08, 0.01);
        }
        m.markActive();
    }

    static void balloon(MPart p) {
        RigidBody rb = p.base.rb();
        V3 c = cell(p.base);
        V3 drag = rb.pointVelocity(c).mul(-0.25);
        rb.addForce(new V3(0, 10 * p.cfg.slider("buoyancy"), 0).add(drag), c);
    }

    // ------------------------------------------------------------------------------------------------ automation

    static void timer(Machine m, MPart p) {
        if (p.pressed(m, "trigger", 0) && p.timer <= 0 && !p.active) p.timer = Math.max(1, (int) (p.cfg.slider("delay") * 20));
        if (p.timer > 0 && --p.timer == 0) {
            p.active = true;
            p.value2 = Math.max(1, p.cfg.slider("duration") * 20);
        }
        if (p.active) {
            m.emulate(p.cfg.keys("output"));
            if (--p.value2 <= 0) p.active = false;
        }
    }

    static void sensor(Machine m, MPart p) {
        V3 dir = facing(p, false);
        V3 o = cell(p.base).addScaled(dir, 0.55);
        double range = p.cfg.slider("range");
        V3 end = o.addScaled(dir, range);
        boolean hit = false;
        if (p.cfg.toggle("blocks")) {
            BlockHitResult r = m.level.clip(new ClipContext(new Vec3(o.x, o.y, o.z), new Vec3(end.x, end.y, end.z),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            hit = r.getType() == HitResult.Type.BLOCK;
        }
        if (!hit && p.cfg.toggle("entities")) {
            AABB box = new AABB(o.x, o.y, o.z, end.x, end.y, end.z).inflate(0.4);
            List<Entity> es = m.level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive());
            hit = !es.isEmpty();
        }
        if (!hit) {
            // other machines (bodies of different islands)
            var rh = m.physics.world.raycastBodies(o, end, p.base.rb());
            hit = rh != null;
        }
        if (p.cfg.toggle("invert")) hit = !hit;
        p.active = hit;
        if (hit) m.emulate(p.cfg.keys("output"));
    }

    static void altimeter(Machine m, MPart p) {
        double h = cell(p.base).y - p.startY;
        boolean above = h > p.cfg.slider("height");
        boolean on = p.cfg.toggle("above") == above;
        p.active = on;
        if (on) m.emulate(p.cfg.keys("output"));
    }

    static void logic(Machine m, MPart p) {
        boolean a = p.keyDown(m, "input_a"), b = p.keyDown(m, "input_b");
        int mode = Math.round(p.cfg.slider("mode"));
        boolean out = switch (mode) {
            case 1 -> a || b;
            case 2 -> a ^ b;
            case 3 -> !a;
            case 4 -> !(a && b);
            case 5 -> !(a || b);
            default -> a && b;
        };
        p.active = out;
        if (out) m.emulate(p.cfg.keys("output"));
    }

    // ------------------------------------------------------------------------------------------------ connectors

    static void connector(Machine m, Machine.Connector c) {
        if (c.broken || c.mj == null || !(c.mj.joint instanceof Joints.Distance d)) return;
        if (c.kind.equals("spring")) {
            int[] k = c.cfg.contains("keys") ? c.cfg.getIntArray("keys") : new int[]{com.mcbesiege.part.Keys.L};
            boolean down = m.isDown(k);
            double target = down ? Math.max(0.3, c.baseLength * 0.15) : c.baseLength;
            d.length = target;
            if (down) m.markActive();
        } else if (c.kind.equals("rope")) {
            int[] wind = c.cfg.contains("wind") ? c.cfg.getIntArray("wind") : new int[]{com.mcbesiege.part.Keys.O};
            int[] unwind = c.cfg.contains("unwind") ? c.cfg.getIntArray("unwind") : new int[]{com.mcbesiege.part.Keys.P};
            float speed = c.cfg.contains("speed") ? c.cfg.getFloat("speed") : 1f;
            double step = 2.0 * speed * 0.05;
            if (m.isDown(wind)) {
                d.length = Math.max(0.3, d.length - step);
                m.markActive();
            } else if (m.isDown(unwind)) {
                d.length = Math.min(Math.max(c.baseLength * 3, 4), d.length + step);
                m.markActive();
            }
        }
    }

    static boolean isMine(Machine m, Collider c) {
        return c != null && c.userData instanceof MPart.Sub s && s.body != null && m.bodies.contains(s.body);
    }
}
