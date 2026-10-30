package com.mcbesiege.machine;

import com.mcbesiege.Config;
import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.part.Orient;
import com.mcbesiege.part.PartType;
import com.mcbesiege.physics.Collider;
import com.mcbesiege.physics.Contact;
import com.mcbesiege.physics.Joint;
import com.mcbesiege.physics.Joints;
import com.mcbesiege.physics.Narrowphase;
import com.mcbesiege.physics.PhysicsWorld;
import com.mcbesiege.physics.Quat;
import com.mcbesiege.physics.RigidBody;
import com.mcbesiege.physics.StaticBox;
import com.mcbesiege.physics.V3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side simulation of one machine: parts, rigid bodies, joints, behaviours, damage and synchronisation data. */
public final class Machine {
    public static final class MJoint {
        public Joint joint;
        public MPart.Sub a, b;
        public MPart owner;
        public Connector connector;
    }

    public static final class Connector {
        public final String kind;
        public final MPart from, to;
        public final Direction fa, fb;
        public final CompoundTag cfg;
        public MPart.Sub subA, subB;
        public V3 offA = V3.ZERO, offB = V3.ZERO;
        public MJoint mj;
        public boolean broken;
        public double baseLength;
        public final boolean[] prevKey = new boolean[2];

        Connector(String kind, MPart from, MPart to, Direction fa, Direction fb, CompoundTag cfg) {
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.fa = fa;
            this.fb = fb;
            this.cfg = cfg;
        }
    }

    private static int nextIsland = 1000;

    public final MachineEntity entity;
    public final ServerLevel level;
    public final LevelPhysics physics;
    public final Blueprint blueprint;
    public UUID owner;
    public final int island = nextIsland++;
    public final List<MPart> parts = new ArrayList<>();
    public final List<MBody> bodies = new ArrayList<>();
    public final List<MJoint> joints = new ArrayList<>();
    public final List<Connector> connectors = new ArrayList<>();
    private int[][] edges = new int[0][];
    private List<List<Integer>> neighbours = new ArrayList<>();
    final Set<Long> noCollide = new HashSet<>();
    public int structureVersion;
    public boolean structureDirty;
    public boolean needStructureSync = true;
    public final Set<Integer> keys = new HashSet<>();
    Set<Integer> emulated = new HashSet<>();
    Set<Integer> emulatedNext = new HashSet<>();
    public long ticks;
    public final V3 forward;
    public MPart core;
    public boolean activeThisTick;
    public boolean inWorld;
    public double startY;

    Machine(MachineEntity entity, ServerLevel level, Blueprint bp, UUID owner) {
        this.entity = entity;
        this.level = level;
        this.physics = LevelPhysics.get(level);
        this.blueprint = bp;
        this.owner = owner;
        this.forward = Orient.dir(bp.forward);
        this.startY = bp.origin.getY();
    }

    // ================================================================================================================ build

    public static Machine create(MachineEntity e, ServerLevel level, Blueprint bp, UUID owner) {
        Machine m = new Machine(e, level, bp, owner);
        m.buildParts();
        m.buildPhysics();
        return m;
    }

    private void buildParts() {
        Map<BlockPos, MPart> byPos = new HashMap<>();
        for (int i = 0; i < blueprint.entries.size(); i++) {
            MPart p = new MPart(i, blueprint.entries.get(i));
            parts.add(p);
            byPos.put(p.rel, p);
            if (p.type == PartType.STARTING_BLOCK && core == null) core = p;
            p.startY = cellCenter(p).y;
        }
        if (core == null && !parts.isEmpty()) core = parts.get(0);
        List<int[]> e = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) neighbours.add(new ArrayList<>());
        for (MPart p : parts) {
            for (Direction d : Direction.values()) {
                if (!Assembler.connects(p.type, p.facing, d)) continue;
                MPart q = byPos.get(p.rel.relative(d));
                if (q == null || q.index < p.index) continue;
                if (!Assembler.connects(q.type, q.facing, d.getOpposite())) continue;
                e.add(new int[]{node(p, d), node(q, d.getOpposite())});
                neighbours.get(p.index).add(q.index);
                neighbours.get(q.index).add(p.index);
            }
        }
        edges = e.toArray(new int[0][]);
        for (MPart p : parts) {
            ListTag cons = p.data.getList("con", Tag.TAG_COMPOUND);
            for (int i = 0; i < cons.size(); i++) {
                CompoundTag c = cons.getCompound(i);
                MPart q = byPos.get(p.rel.offset(c.getInt("dx"), c.getInt("dy"), c.getInt("dz")));
                if (q == null || q == p) continue;
                connectors.add(new Connector(c.getString("kind"), p, q, Direction.from3DDataValue(c.getInt("fa")),
                        Direction.from3DDataValue(c.getInt("fb")), c.getCompound("cfg")));
            }
        }
    }

    private static int node(MPart p, Direction face) {
        return p.type.isJoint() && face == p.facing ? 2 * p.index + 1 : 2 * p.index;
    }

    private static int nodeOf(MPart.Sub s) { return 2 * s.part.index + (s.head ? 1 : 0); }

    private boolean nodeAttached(int node) {
        MPart p = parts.get(node / 2);
        return p.alive && !p.debris && (node % 2 == 0 || p.head != null);
    }

    /** World position of a part's cell center at assembly time (the body frames' reference). */
    public V3 cellCenter(MPart p) {
        BlockPos o = blueprint.origin;
        return new V3(o.getX() + p.rel.getX() + 0.5, o.getY() + p.rel.getY() + 0.5, o.getZ() + p.rel.getZ() + 0.5);
    }

    private static V3 canon(double[] px) { return new V3(px[0] / 16.0 - 0.5, px[1] / 16.0 - 0.5, px[2] / 16.0 - 0.5); }

    private Collider makeCollider(MPart p, boolean head) {
        PartType t = p.type;
        V3 cell = cellCenter(p);
        double mass = t == PartType.BALLAST ? p.cfg.slider("mass") : t.mass;
        double m = t.isJoint() ? (head ? mass * t.headFrac : mass * (1 - t.headFrac)) : mass;
        Collider c;
        if (head && t.headShape != PartType.HeadShape.BOX) {
            V3 center = cell.add(p.orient.rotate(canon(t.headCenter)));
            if (t.headShape == PartType.HeadShape.CYLINDER) c = new Collider.Cylinder(t.headRadius, t.headHalfHeight, center, p.orient, m);
            else c = new Collider.Sphere(t.headRadius, center, m);
        } else if (!head && t.sphereRadius > 0) {
            c = new Collider.Sphere(t.sphereRadius, cell, m);
        } else {
            double[] b = Orient.box(t, head ? t.headBox : t.baseBox, p.facing, p.twist);
            V3 half = new V3(Math.max(0.03, (b[3] - b[0]) / 2), Math.max(0.03, (b[4] - b[1]) / 2), Math.max(0.03, (b[5] - b[2]) / 2));
            V3 center = cell.add(-0.5 + (b[0] + b[3]) / 2, -0.5 + (b[1] + b[4]) / 2, -0.5 + (b[2] + b[5]) / 2);
            c = new Collider.Box(half, center, Quat.IDENTITY, m);
        }
        if (m < 1e-4) {
            c.mass = 0;
            c.solid = false;
        }
        c.friction = t.friction;
        c.restitution = t.restitution;
        return c;
    }

    private void buildPhysics() {
        UF uf = new UF(parts.size() * 2);
        for (int[] e : edges) if (nodeAttached(e[0]) && nodeAttached(e[1])) uf.union(e[0], e[1]);
        Map<Integer, MBody> byRoot = new LinkedHashMap<>();
        for (MPart p : parts) {
            if (!p.alive) continue;
            for (MPart.Sub s : subs(p)) {
                int nd = nodeOf(s);
                int root = p.debris ? -nd - 1 : uf.find(nd);
                MBody b = byRoot.computeIfAbsent(root, k -> new MBody(new RigidBody()));
                s.collider = makeCollider(p, s.head);
                s.collider.userData = s;
                s.colliderToCell = cellCenter(p).sub(s.collider.localPos);
                b.rb.addCollider(s.collider);
                b.subs.add(s);
                s.body = b;
                if (p.debris) b.debris = true;
            }
        }
        bodies.clear();
        for (MBody b : byRoot.values()) {
            b.rb.recomputeMass();
            b.rb.island = island;
            b.rb.linearDamping = 0.05;
            b.rb.angularDamping = 0.15;
            b.rb.updateInertiaWorld();
            b.rb.updateColliders();
            b.rb.computeAabb();
            bodies.add(b);
        }
        reindex();
        joints.clear();
        for (MPart p : parts) {
            if (p.alive && !p.debris && p.type.isJoint() && !p.jointBroken) createJoint(p);
        }
        for (Connector c : connectors) {
            if (!c.broken && c.from.alive && c.to.alive && !c.from.debris && !c.to.debris) createConnector(c);
            else setupConnectorAnchors(c);
        }
        for (MPart p : parts) setupAutoSign(p);
        computeNoCollide();
        structureVersion++;
        needStructureSync = true;
    }

    private static List<MPart.Sub> subs(MPart p) {
        List<MPart.Sub> l = new ArrayList<>(2);
        l.add(p.base);
        if (p.head != null) l.add(p.head);
        return l;
    }

    private void createJoint(MPart p) {
        RigidBody a = p.base.rb(), b = p.head.rb();
        if (a == null || b == null || a == b) return;
        V3 cell = cellCenter(p);
        V3 anchor = p.type.headShape != PartType.HeadShape.BOX ? cell.add(p.orient.rotate(canon(p.type.headCenter))) : cell;
        V3 axis = p.facingVec;
        double str = Config.jointStrength();
        Joint j;
        switch (p.type.joint) {
            case AXIAL -> {
                Joints.Hinge h = new Joints.Hinge(a, b, anchor, axis);
                j = h;
                j.breakForce = (p.type.isWheel() ? 1600 : 1300) * str;
                j.breakTorque = 900 * str;
            }
            case PERP -> {
                Joints.Hinge h = new Joints.Hinge(a, b, anchor, p.jointAxis());
                j = h;
                j.breakForce = 1200 * str;
                j.breakTorque = 700 * str;
            }
            case BALL -> {
                j = new Joints.Ball(a, b, anchor);
                j.breakForce = 1200 * str;
            }
            case SLIDER -> {
                Joints.Slider s = new Joints.Slider(a, b, anchor, axis);
                if (p.type == PartType.PISTON) {
                    s.lower = 0;
                    s.upper = 1.0;
                } else {
                    s.lower = -0.4;
                    s.upper = 0.2;
                }
                j = s;
                j.breakForce = 1800 * str;
                j.breakTorque = 900 * str;
            }
            case RELEASE -> {
                j = new Joints.Fixed(a, b, anchor);
                j.breakForce = 1e9;
                j.breakTorque = 1e9;
            }
            default -> {
                j = new Joints.Fixed(a, b, anchor);
                j.breakForce = 1600 * str;
                j.breakTorque = 900 * str;
            }
        }
        j.userData = p;
        p.joint = j;
        MJoint mj = new MJoint();
        mj.joint = j;
        mj.a = p.base;
        mj.b = p.head;
        mj.owner = p;
        joints.add(mj);
    }

    private void setupConnectorAnchors(Connector c) {
        c.subA = c.from.subForFace(c.fa);
        c.subB = c.to.subForFace(c.fb);
        c.offA = Orient.dir(c.fa).mul(0.5);
        c.offB = Orient.dir(c.fb).mul(0.5);
    }

    private void createConnector(Connector c) {
        setupConnectorAnchors(c);
        RigidBody a = c.subA.rb(), b = c.subB.rb();
        if (a == null || b == null) return;
        V3 wa = cellCenter(c.from).add(c.offA), wb = cellCenter(c.to).add(c.offB);
        c.baseLength = wb.sub(wa).len();
        Joint j = null;
        switch (c.kind) {
            case "spring" -> {
                Joints.Distance d = new Joints.Distance(a, b, wa, wb, Joints.Distance.Mode.SPRING);
                float s = c.cfg.contains("strength") ? c.cfg.getFloat("strength") : 1f;
                d.springStiffness = 60 * s;
                d.springDampingCoeff = 2 + 2 * s;
                d.minLength = 0.25;
                d.breakForce = 2500;
                j = d;
            }
            case "rope" -> {
                Joints.Distance d = new Joints.Distance(a, b, wa, wb, Joints.Distance.Mode.ROPE);
                d.minLength = 0.3;
                d.breakForce = 3000;
                j = d;
            }
            default -> {
                if (a != b) {
                    j = new Joints.Fixed(a, b, wa.add(wb).mul(0.5));
                    j.breakForce = 1500 * Config.jointStrength();
                    j.breakTorque = 900 * Config.jointStrength();
                }
            }
        }
        if (j == null) return;
        j.userData = c;
        MJoint mj = new MJoint();
        mj.joint = j;
        mj.a = c.subA;
        mj.b = c.subB;
        mj.connector = c;
        c.mj = mj;
        joints.add(mj);
    }

    private void setupAutoSign(MPart p) {
        V3 up = V3.Y;
        V3 a = p.jointAxis();
        if (p.type.isPoweredWheel()) {
            double d = a.dot(up.cross(forward));
            p.autoSign = Math.abs(d) < 0.3 ? 1 : Math.signum(d);
        } else if (p.type == PartType.STEERING_HINGE || p.type == PartType.STEERING_BLOCK) {
            double d = a.dot(up);
            if (Math.abs(d) > 0.5) p.autoSign = Math.signum(d);
            else {
                double d2 = a.dot(up.cross(forward));
                p.autoSign = Math.abs(d2) > 0.5 ? Math.signum(d2) : 1;
            }
        }
    }

    private void computeNoCollide() {
        noCollide.clear();
        Map<RigidBody, Set<RigidBody>> adj = new HashMap<>();
        for (MJoint mj : joints) {
            if (mj.joint.broken || mj.joint instanceof Joints.Distance) continue;
            adj.computeIfAbsent(mj.joint.a, k -> new HashSet<>()).add(mj.joint.b);
            adj.computeIfAbsent(mj.joint.b, k -> new HashSet<>()).add(mj.joint.a);
        }
        for (Map.Entry<RigidBody, Set<RigidBody>> e : adj.entrySet()) {
            RigidBody x = e.getKey();
            for (RigidBody y : e.getValue()) {
                noCollide.add(pair(x, y));
                for (RigidBody z : adj.getOrDefault(y, Set.of())) if (z != x) noCollide.add(pair(x, z));
            }
        }
    }

    static long pair(RigidBody a, RigidBody b) {
        long x = Math.min(a.id, b.id), y = Math.max(a.id, b.id);
        return (x << 32) | y;
    }

    private void reindex() {
        for (int i = 0; i < bodies.size(); i++) bodies.get(i).index = i;
    }

    public void addToWorld() {
        if (inWorld) return;
        PhysicsWorld w = physics.world;
        for (MBody b : bodies) w.addBody(b.rb);
        for (MJoint mj : joints) w.addJoint(mj.joint);
        for (MPart p : parts) if (p.grab != null) w.addJoint(p.grab);
        inWorld = true;
        physics.machines.add(this);
    }

    public void removeFromWorld() {
        if (!inWorld) return;
        PhysicsWorld w = physics.world;
        for (MJoint mj : joints) w.removeJoint(mj.joint);
        for (MPart p : parts) if (p.grab != null) w.removeJoint(p.grab);
        for (MBody b : bodies) w.removeBody(b.rb);
        inWorld = false;
        physics.machines.remove(this);
    }

    /** Lifts the freshly assembled machine so no collider starts inside the ground (Besiege "grounding"). */
    public void groundLift() {
        McWorldGeometry geo = physics.geometry;
        for (int iter = 0; iter < 8; iter++) {
            double maxPen = 0;
            for (MBody b : bodies) {
                b.rb.updateColliders();
                b.rb.computeAabb();
                double[] bb = b.rb.aabb();
                List<StaticBox> boxes = new ArrayList<>();
                geo.collect(bb[0] - 0.2, bb[1] - 0.2, bb[2] - 0.2, bb[3] + 0.2, bb[4] + 0.2, bb[5] + 0.2, boxes);
                for (Collider c : b.rb.colliders) {
                    if (!c.solid) continue;
                    for (StaticBox sb : boxes) {
                        if (!c.aabbOverlaps(sb, 0)) continue;
                        Contact.Manifold m = new Contact.Manifold();
                        Narrowphase.collideStatic(c, sb, 0, m);
                        for (Contact.Point pt : m.pts) if (pt.normal.y < -0.3) maxPen = Math.max(maxPen, pt.depth);
                    }
                }
            }
            if (maxPen < 0.004) break;
            double lift = Math.min(maxPen, 1.5) + 0.01;
            for (MBody b : bodies) b.rb.setTransform(b.rb.pos.add(0, lift, 0), b.rb.rot);
        }
    }

    // ================================================================================================================ input

    public boolean isDown(int[] codes) {
        for (int k : codes) if (k >= 0 && (keys.contains(k) || emulated.contains(k))) return true;
        return false;
    }

    public void emulate(int[] codes) {
        for (int k : codes) if (k >= 0) emulatedNext.add(k);
    }

    public void markActive() { activeThisTick = true; }

    public ServerPlayer ownerPlayer() {
        return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
    }

    public DamageSource damageSource() {
        ServerPlayer p = ownerPlayer();
        return p != null ? level.damageSources().playerAttack(p) : level.damageSources().generic();
    }

    // ================================================================================================================ tick

    public void preStep() {
        ticks++;
        Set<Integer> t = emulated;
        emulated = emulatedNext;
        emulatedNext = t;
        emulatedNext.clear();
        activeThisTick = false;
        for (MPart p : parts) {
            if (!p.alive) continue;
            if (p.cooldown > 0) p.cooldown--;
            if (p.flash > 0) p.flash--;
            if (p.debris) {
                p.debrisAge++;
                continue;
            }
            Behaviors.tick(this, p);
        }
        for (Connector c : connectors) Behaviors.connector(this, c);
        environment();
        boolean driven = activeThisTick || !keys.isEmpty() || !emulated.isEmpty();
        for (MBody b : bodies) b.rb.allowSleep = !driven || b.debris;
        if (driven) for (MBody b : bodies) if (!b.debris) physics.world.wakeIsland(b.rb);
    }

    /** Water buoyancy / drag, lava and fire contact. */
    private void environment() {
        if (ticks % 2 != 0) return;
        for (MBody b : bodies) {
            if (b.rb.sleeping) continue;
            for (MPart.Sub s : b.subs) {
                if (!s.part.alive || s.collider.mass <= 0) continue;
                V3 c = s.collider.wPos;
                BlockPos bp = BlockPos.containing(c.x, c.y, c.z);
                FluidState fs = level.getFluidState(bp);
                if (!fs.isEmpty()) {
                    if (fs.is(FluidTags.WATER)) {
                        double h = bp.getY() + fs.getHeight(level, bp);
                        double sub = Math.max(0, Math.min(1, h - (c.y - 0.5)));
                        double vol = Math.min(1.0, s.collider.volume() * 1.2 + 0.2);
                        V3 f = new V3(0, Config.waterDensity() * vol * sub * -physics.world.gravity.y * 2, 0);
                        V3 drag = b.rb.pointVelocity(c).mul(-1.2 * vol * sub * 2);
                        b.rb.addForce(f.add(drag), c);
                        if (s.part.burning) s.part.burning = false;
                    } else if (fs.is(FluidTags.LAVA)) {
                        ignite(s.part);
                    }
                } else if (s.part.type.flammable) {
                    BlockState st = level.getBlockState(bp);
                    if (st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE) || st.is(Blocks.LAVA)) ignite(s.part);
                }
            }
        }
    }

    public void postStep() {
        detectGrabs();
        for (MPart p : parts) if (p.alive && p.pendingDamage > 0) applyDamage(p);
        tickFire();
        if (ticks % 2 == 0) entityContacts();
        arrowHits();
        cleanupDebris();
        if (structureDirty) restructure();
        if (core != null && core.base.body != null) {
            V3 c = core.base.worldCellCenter();
            entity.setPos(c.x, c.y - 0.5, c.z);
        }
    }

    // ================================================================================================================ damage

    public void addDamage(MPart.Sub s, double amount) {
        if (s == null || !s.part.alive) return;
        s.part.pendingDamage += amount;
    }

    private void applyDamage(MPart p) {
        double d = p.pendingDamage;
        p.pendingDamage = 0;
        if (d <= 0.05) return;
        p.health -= d;
        p.flash = 6;
        if (p.health <= 0) breakPart(p);
    }

    public void breakPart(MPart p) {
        if (!p.alive) return;
        if (p.isExplosive()) {
            explode(p);
            return;
        }
        V3 c = p.base.body != null ? p.base.worldCellCenter() : cellCenter(p);
        BlockPos bp = BlockPos.containing(c.x, c.y, c.z);
        if (p.type == PartType.BALLOON) {
            level.playSound(null, bp, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.4f, 2.0f);
            level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.05);
            destroyPart(p);
            return;
        }
        level.levelEvent(2001, bp, Block.getId(p.state));
        if (p.debris || p.charred) {
            destroyPart(p);
            return;
        }
        detachPart(p);
        p.health = Math.max(5, p.type.hp * 0.3);
    }

    public void explode(MPart p) {
        if (!p.alive) return;
        V3 c = p.base.body != null ? p.base.worldCellCenter() : cellCenter(p);
        double power = p.type.explosive * (p.type == PartType.BOMB ? p.cfg.slider("power") : 1.0);
        destroyPart(p);
        Explosions.explode(level, c, power, entity, ownerPlayer());
    }

    public void ignite(MPart p) {
        if (!p.alive || !p.type.flammable || p.burning) return;
        p.burning = true;
        p.burnTicks = 0;
    }

    private void tickFire() {
        for (MPart p : parts) {
            if (!p.alive || !p.burning) continue;
            p.burnTicks++;
            p.health -= p.type.burnRate;
            if (p.isExplosive() && p.fuse < 0) p.fuse = p.type == PartType.BOMB ? 30 : 14;
            if (p.base.body == null) continue;
            V3 c = p.base.worldCellCenter();
            if (p.burnTicks % 5 == 0) level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 2, 0.25, 0.25, 0.25, 0.01);
            if (p.burnTicks % 20 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.4, c.z, 2, 0.2, 0.2, 0.2, 0.01);
                for (int ni : neighbours.get(p.index)) {
                    MPart q = parts.get(ni);
                    if (q.alive && !q.burning && q.type.flammable && level.random.nextFloat() < 0.35f) ignite(q);
                }
            }
            if (p.health <= 0) {
                p.charred = true;
                p.burning = false;
                breakPart(p);
            } else if (p.burnTicks > 400 && level.random.nextFloat() < 0.01f) {
                p.burning = false;
            }
        }
    }

    // ================================================================================================================ structure

    public void destroyPart(MPart p) {
        if (!p.alive) return;
        p.alive = false;
        p.burning = false;
        removePartJoints(p);
        structureDirty = true;
    }

    public void detachPart(MPart p) {
        if (!p.alive || p.debris) return;
        p.debris = true;
        removePartJoints(p);
        structureDirty = true;
    }

    public void breakJoint(MPart p) {
        if (p.joint == null || p.jointBroken) return;
        p.jointBroken = true;
        removeMJoint(p.joint);
        p.joint = null;
        structureDirty = true;
    }

    private void removePartJoints(MPart p) {
        if (p.joint != null) {
            removeMJoint(p.joint);
            p.joint = null;
        }
        if (p.grab != null) {
            physics.world.removeJoint(p.grab);
            p.grab = null;
        }
        for (Connector c : connectors) {
            if ((c.from == p || c.to == p) && !c.broken) {
                c.broken = true;
                if (c.mj != null) removeMJoint(c.mj.joint);
            }
        }
    }

    private void removeMJoint(Joint j) {
        joints.removeIf(mj -> mj.joint == j);
        physics.world.removeJoint(j);
    }

    /** Called by the physics listener when a joint snapped. */
    public void onJointBroken(Joint j) {
        if (j.userData instanceof MPart p && p.joint == j) {
            p.jointBroken = true;
            p.joint = null;
        } else if (j.userData instanceof Connector c) {
            c.broken = true;
        }
        joints.removeIf(mj -> mj.joint == j);
        structureDirty = true;
        V3 a = j.a.pos;
        level.playSound(null, BlockPos.containing(a.x, a.y, a.z), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.5f, 1.4f);
    }

    /** Splits bodies that lost their rigid connections and moves debris into bodies of their own. */
    private void restructure() {
        structureDirty = false;
        PhysicsWorld w = physics.world;
        for (MPart p : parts) {
            if (p.alive) continue;
            for (MPart.Sub s : subs(p)) {
                if (s.body == null) continue;
                s.body.rb.removeCollider(s.collider);
                s.body.subs.remove(s);
                s.body = null;
            }
        }
        UF uf = new UF(parts.size() * 2);
        for (int[] e : edges) if (nodeAttached(e[0]) && nodeAttached(e[1])) uf.union(e[0], e[1]);
        List<MBody> created = new ArrayList<>();
        for (MBody b : new ArrayList<>(bodies)) {
            if (b.subs.isEmpty()) continue;
            Map<Integer, List<MPart.Sub>> groups = new LinkedHashMap<>();
            for (MPart.Sub s : b.subs) {
                int nd = nodeOf(s);
                int key = s.part.debris ? -nd - 1 : uf.find(nd);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
            if (groups.size() > 1) {
                List<List<MPart.Sub>> gl = new ArrayList<>(groups.values());
                gl.sort(Comparator.comparingInt((List<MPart.Sub> g) -> score(g)).reversed());
                for (int gi = 1; gi < gl.size(); gi++) {
                    List<MPart.Sub> g = gl.get(gi);
                    RigidBody nb = new RigidBody();
                    nb.pos = b.rb.pos;
                    nb.rot = b.rb.rot;
                    nb.vel = b.rb.vel;
                    nb.angVel = b.rb.angVel;
                    nb.island = island;
                    nb.linearDamping = b.rb.linearDamping;
                    nb.angularDamping = b.rb.angularDamping;
                    MBody mb = new MBody(nb);
                    boolean allDebris = true;
                    for (MPart.Sub s : g) {
                        b.rb.removeCollider(s.collider);
                        b.subs.remove(s);
                        nb.addCollider(s.collider);
                        mb.subs.add(s);
                        s.body = mb;
                        if (!s.part.debris) allDebris = false;
                    }
                    mb.debris = allDebris;
                    V3 shift = nb.recomputeMass();
                    for (MJoint mj : joints) {
                        if (mj.a != null && mj.a.body == mb) mj.joint.retarget(b.rb, nb, shift);
                        if (mj.b != null && mj.b.body == mb) mj.joint.retarget(b.rb, nb, shift);
                    }
                    for (MPart p : parts) {
                        if (p.grab != null && p.base.body == mb) p.grab.retarget(b.rb, nb, shift);
                    }
                    nb.updateColliders();
                    nb.computeAabb();
                    w.addBody(nb);
                    created.add(mb);
                }
            }
            V3 sb = b.rb.recomputeMass();
            w.notifyBodyShift(b.rb, sb);
            b.rb.updateColliders();
            b.rb.computeAabb();
            boolean allDebris = !b.subs.isEmpty();
            for (MPart.Sub s : b.subs) if (!s.part.debris) allDebris = false;
            b.debris = allDebris;
            w.wakeIsland(b.rb);
        }
        bodies.addAll(created);
        for (MBody b : new ArrayList<>(bodies)) {
            if (b.subs.isEmpty()) {
                w.removeBody(b.rb);
                bodies.remove(b);
            }
        }
        reindex();
        computeNoCollide();
        structureVersion++;
        needStructureSync = true;
    }

    private int score(List<MPart.Sub> g) {
        int s = 0;
        for (MPart.Sub x : g) {
            if (x.part.debris) continue;
            s += 1;
            if (x.part == core) s += 100000;
        }
        return s;
    }

    private void cleanupDebris() {
        int count = 0;
        for (MPart p : parts) if (p.alive && p.debris) count++;
        if (count == 0) return;
        int limit = Config.maxDebris();
        for (MPart p : parts) {
            if (!p.alive || !p.debris) continue;
            if (p.debrisAge > 20 * 45 || count > limit) {
                destroyPart(p);
                count--;
            }
        }
    }

    // ================================================================================================================ interactions

    private void detectGrabs() {
        List<MPart> grabbers = null;
        for (MPart p : parts) {
            if (p.type == PartType.GRABBER && p.alive && !p.debris && p.grab == null && p.grabCooldown == 0) {
                if (grabbers == null) grabbers = new ArrayList<>();
                grabbers.add(p);
            }
        }
        if (grabbers == null) return;
        for (Contact.Manifold m : physics.world.lastManifolds()) {
            for (MPart g : grabbers) {
                if (g.grab != null || m.pts.isEmpty()) continue;
                Collider mine = g.base.collider;
                RigidBody other;
                if (m.ca == mine) other = m.cb == null ? physics.world.ground : m.b;
                else if (m.cb == mine) other = m.a;
                else continue;
                if (other != physics.world.ground && other.userData instanceof MBody ob && bodies.contains(ob)) continue;
                V3 pt = m.pts.get(0).point;
                Joints.Fixed f = new Joints.Fixed(g.base.rb(), other, pt);
                f.breakForce = 2500 * Config.jointStrength();
                f.breakTorque = 1500 * Config.jointStrength();
                f.collideConnected = false;
                g.grab = f;
                physics.world.addJoint(f);
                level.playSound(null, BlockPos.containing(pt.x, pt.y, pt.z), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
        }
    }

    private void entityContacts() {
        boolean ownerImmune = Config.ownerImmune();
        for (MBody b : bodies) {
            RigidBody rb = b.rb;
            if (rb.sleeping || b.subs.isEmpty()) continue;
            double[] bb = rb.aabb();
            AABB box = new AABB(bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]).inflate(0.15);
            List<Entity> list = level.getEntities((Entity) null, box,
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !(e.getVehicle() instanceof MachineEntity));
            for (Entity e : list) {
                LivingEntity le = (LivingEntity) e;
                AABB eb = e.getBoundingBox();
                for (MPart.Sub s : b.subs) {
                    Collider c = s.collider;
                    if (!c.solid || !s.part.alive) continue;
                    if (c.maxX < eb.minX || c.minX > eb.maxX || c.maxY < eb.minY || c.minY > eb.maxY || c.maxZ < eb.minZ || c.minZ > eb.maxZ) continue;
                    V3 ec = new V3((eb.minX + eb.maxX) / 2, (eb.minY + eb.maxY) / 2, (eb.minZ + eb.maxZ) / 2);
                    if (!nearCollider(c, ec, (eb.maxX - eb.minX) / 2, (eb.maxY - eb.minY) / 2)) continue;
                    Vec3 dm = e.getDeltaMovement();
                    V3 rel = rb.pointVelocity(ec).sub(new V3(dm.x * 20, dm.y * 20, dm.z * 20));
                    double speed = rel.len();
                    double mul = s.part.type.damageMul;
                    boolean blade = s.part.active && (s.part.type == PartType.SAW_BLADE || s.part.type == PartType.DRILL);
                    double dmg = 0;
                    if (speed > 3.5) dmg = (speed - 3.5) * 0.9 * mul;
                    if (mul > 1 && speed > 1) dmg = Math.max(dmg, speed * 0.6 * mul);
                    if (blade) dmg = Math.max(dmg, 3 * mul);
                    if (s.part.burning) e.setSecondsOnFire(3);
                    boolean immune = ownerImmune && owner != null && e.getUUID().equals(owner);
                    if (dmg >= 1 && !immune) le.hurt(damageSource(), (float) Math.min(dmg, 60));
                    V3 away = ec.sub(c.wPos);
                    away = new V3(away.x, Math.max(0.1, away.y), away.z).norm();
                    V3 push = away.mul(0.12).add(rel.mul(0.03));
                    e.push(push.x, push.y, push.z);
                    e.hurtMarked = true;
                    rb.applyImpulse(rel.mul(-0.015), ec);
                    break;
                }
            }
        }
    }

    private static boolean nearCollider(Collider c, V3 p, double rx, double ry) {
        V3 l = c.wRot.invRotate(p.sub(c.wPos));
        double hx, hy, hz;
        if (c instanceof Collider.Box bx) {
            hx = bx.half.x;
            hy = bx.half.y;
            hz = bx.half.z;
        } else if (c instanceof Collider.Cylinder cy) {
            hx = cy.radius;
            hy = cy.halfHeight;
            hz = cy.radius;
        } else {
            double r = ((Collider.Sphere) c).radius;
            hx = hy = hz = r;
        }
        double m = Math.max(rx, ry) + 0.05;
        return Math.abs(l.x) < hx + m && Math.abs(l.y) < hy + m && Math.abs(l.z) < hz + m;
    }

    private void arrowHits() {
        if (bodies.isEmpty()) return;
        AABB all = boundingBox();
        if (all == null) return;
        for (AbstractArrow a : level.getEntitiesOfClass(AbstractArrow.class, all.inflate(4))) {
            Vec3 v = a.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;
            Entity ow = a.getOwner();
            if (ow != null && owner != null && ow.getUUID().equals(owner) && a.tickCount < 15) continue;
            V3 from = new V3(a.getX(), a.getY(), a.getZ());
            double len = v.length();
            V3 dir = new V3(v.x / len, v.y / len, v.z / len);
            double best = Double.MAX_VALUE;
            MPart.Sub hit = null;
            for (MBody b : bodies) {
                for (MPart.Sub s : b.subs) {
                    if (!s.collider.solid) continue;
                    double t = PhysicsWorld.rayCollider(from, dir, len + 0.3, s.collider);
                    if (t >= 0 && t < best) {
                        best = t;
                        hit = s;
                    }
                }
            }
            if (hit == null) continue;
            V3 pt = from.addScaled(dir, best);
            addDamage(hit, 2.0 + len * 1.5);
            hit.body.rb.applyImpulse(dir.mul(len * 0.4), pt);
            physics.world.wakeIsland(hit.body.rb);
            level.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 4, 0.1, 0.1, 0.1, 0.1);
            if (hit.part.type.flammable && a.isOnFire()) ignite(hit.part);
            a.discard();
        }
    }

    public AABB boundingBox() {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        boolean any = false;
        for (MBody b : bodies) {
            if (b.subs.isEmpty()) continue;
            double[] bb = b.rb.aabb();
            x0 = Math.min(x0, bb[0]);
            y0 = Math.min(y0, bb[1]);
            z0 = Math.min(z0, bb[2]);
            x1 = Math.max(x1, bb[3]);
            y1 = Math.max(y1, bb[4]);
            z1 = Math.max(z1, bb[5]);
            any = true;
        }
        return any ? new AABB(x0, y0, z0, x1, y1, z1) : null;
    }

    // ================================================================================================================ queries

    public MPart.Sub findSub(Collider c) {
        return c != null && c.userData instanceof MPart.Sub s && s.part.alive ? s : null;
    }

    public List<MPart> seats() {
        List<MPart> l = new ArrayList<>();
        for (MPart p : parts) if (p.type == PartType.SEAT && p.alive && !p.debris) l.add(p);
        return l;
    }

    public int aliveCount() {
        int n = 0;
        for (MPart p : parts) if (p.alive && !p.debris) n++;
        return n;
    }

    public double totalMass() {
        double m = 0;
        for (MBody b : bodies) if (!b.debris) m += b.rb.mass;
        return m;
    }

    public V3 corePos() {
        return core != null && core.base.body != null ? core.base.worldCellCenter() : V3.ZERO;
    }

    public List<List<Integer>> neighbours() { return neighbours; }

    // ================================================================================================================ sync

    public CompoundTag structureTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", structureVersion);
        t.putInt("n", bodies.size());
        ListTag subs = new ListTag();
        for (MPart p : parts) {
            if (!p.alive) continue;
            for (MPart.Sub s : subs(p)) {
                if (s.body == null) continue;
                CompoundTag c = new CompoundTag();
                c.putInt("s", Block.getId(s.renderState));
                c.putInt("b", s.body.index);
                V3 o = s.renderOffset();
                c.putFloat("x", (float) o.x);
                c.putFloat("y", (float) o.y);
                c.putFloat("z", (float) o.z);
                c.putInt("p", p.index);
                if (p.charred) c.putBoolean("c", true);
                subs.add(c);
            }
        }
        t.put("subs", subs);
        ListTag cons = new ListTag();
        for (Connector c : connectors) {
            if (c.broken || c.subA == null || c.subB == null || c.subA.body == null || c.subB.body == null) continue;
            CompoundTag ct = new CompoundTag();
            ct.putString("k", c.kind);
            ct.putInt("ba", c.subA.body.index);
            V3 oa = c.subA.renderOffset().add(c.offA);
            ct.putFloat("ax", (float) oa.x);
            ct.putFloat("ay", (float) oa.y);
            ct.putFloat("az", (float) oa.z);
            ct.putInt("bb", c.subB.body.index);
            V3 ob = c.subB.renderOffset().add(c.offB);
            ct.putFloat("bx", (float) ob.x);
            ct.putFloat("by", (float) ob.y);
            ct.putFloat("bz", (float) ob.z);
            cons.add(ct);
        }
        t.put("cons", cons);
        // key overview for the control screen: type.action -> keys, count
        Map<String, int[]> keysOf = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MPart p : parts) {
            if (!p.alive || p.debris) continue;
            for (PartType.KeySpec ks : p.type.keys) {
                if (p.type == PartType.SPINNING_BLOCK || p.type == PartType.SAW_BLADE || p.type == PartType.DRILL) {
                    if (p.cfg.toggle("automatic")) continue;
                }
                if (p.type.isPoweredWheel() && !p.cfg.toggle("powered")) continue;
                int[] k = p.cfg.keys(ks.id());
                if (k.length == 0) continue;
                String key = p.type.id() + "." + ks.id() + "#" + java.util.Arrays.toString(k);
                keysOf.put(key, k);
                counts.merge(key, 1, Integer::sum);
            }
        }
        for (Connector c : connectors) {
            if (c.broken) continue;
            if (c.kind.equals("spring")) {
                int[] k = c.cfg.contains("keys") ? c.cfg.getIntArray("keys") : new int[]{com.mcbesiege.part.Keys.L};
                String key = "spring.contract#" + java.util.Arrays.toString(k);
                keysOf.put(key, k);
                counts.merge(key, 1, Integer::sum);
            } else if (c.kind.equals("rope")) {
                int[] w = c.cfg.contains("wind") ? c.cfg.getIntArray("wind") : new int[]{com.mcbesiege.part.Keys.O};
                int[] u = c.cfg.contains("unwind") ? c.cfg.getIntArray("unwind") : new int[]{com.mcbesiege.part.Keys.P};
                keysOf.put("rope.wind#" + java.util.Arrays.toString(w), w);
                counts.merge("rope.wind#" + java.util.Arrays.toString(w), 1, Integer::sum);
                keysOf.put("rope.unwind#" + java.util.Arrays.toString(u), u);
                counts.merge("rope.unwind#" + java.util.Arrays.toString(u), 1, Integer::sum);
            }
        }
        ListTag help = new ListTag();
        for (Map.Entry<String, int[]> e : keysOf.entrySet()) {
            CompoundTag h = new CompoundTag();
            h.putString("a", e.getKey().substring(0, e.getKey().indexOf('#')));
            h.putIntArray("k", e.getValue());
            h.putInt("n", counts.get(e.getKey()));
            help.add(h);
        }
        t.put("help", help);
        t.putInt("total", parts.size());
        t.putInt("alive", aliveCount());
        return t;
    }

    /** Per-part visual flags for the state packet, indexed by part index. bit0 burning, bit1 flash, bit2 active. */
    public byte[] partFlags() {
        byte[] f = new byte[parts.size()];
        for (MPart p : parts) {
            int v = 0;
            if (p.burning) v |= 1;
            if (p.flash > 0) v |= 2;
            if (p.active) v |= 4;
            f[p.index] = (byte) v;
        }
        return f;
    }

    // ================================================================================================================ save / load

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("bp", blueprint.save());
        if (owner != null) t.putUUID("owner", owner);
        ListTag pl = new ListTag();
        for (MPart p : parts) {
            CompoundTag c = new CompoundTag();
            c.putBoolean("a", p.alive);
            c.putBoolean("d", p.debris);
            c.putBoolean("j", p.jointBroken);
            c.putBoolean("ch", p.charred);
            c.putDouble("h", p.health);
            c.putDouble("v", p.value);
            c.putBoolean("u", p.used);
            c.putInt("am", p.ammo);
            c.putDouble("fu", p.fuel);
            c.putBoolean("tg", p.toggled);
            for (MPart.Sub s : subs(p)) {
                if (s.body == null) continue;
                RigidBody rb = s.body.rb;
                V3 cc = s.worldCellCenter();
                String k = s.head ? "H" : "B";
                c.putDouble(k + "x", cc.x);
                c.putDouble(k + "y", cc.y);
                c.putDouble(k + "z", cc.z);
                c.putDouble(k + "qw", rb.rot.w);
                c.putDouble(k + "qx", rb.rot.x);
                c.putDouble(k + "qy", rb.rot.y);
                c.putDouble(k + "qz", rb.rot.z);
                V3 v = rb.pointVelocity(cc);
                c.putDouble(k + "vx", v.x);
                c.putDouble(k + "vy", v.y);
                c.putDouble(k + "vz", v.z);
            }
            pl.add(c);
        }
        t.put("parts", pl);
        ListTag cl = new ListTag();
        for (Connector c : connectors) {
            CompoundTag ct = new CompoundTag();
            ct.putBoolean("b", c.broken);
            cl.add(ct);
        }
        t.put("cons", cl);
        return t;
    }

    public static Machine load(MachineEntity e, ServerLevel level, CompoundTag t) {
        Blueprint bp = Blueprint.load(t.getCompound("bp"));
        UUID owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
        Machine m = new Machine(e, level, bp, owner);
        m.buildParts();
        ListTag pl = t.getList("parts", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(pl.size(), m.parts.size()); i++) {
            CompoundTag c = pl.getCompound(i);
            MPart p = m.parts.get(i);
            p.alive = c.getBoolean("a");
            p.debris = c.getBoolean("d");
            p.jointBroken = c.getBoolean("j");
            p.charred = c.getBoolean("ch");
            p.health = c.getDouble("h");
            p.value = c.getDouble("v");
            p.used = c.getBoolean("u");
            p.ammo = c.getInt("am");
            p.fuel = c.getDouble("fu");
            p.toggled = c.getBoolean("tg");
        }
        ListTag cl = t.getList("cons", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(cl.size(), m.connectors.size()); i++) m.connectors.get(i).broken = cl.getCompound(i).getBoolean("b");
        m.buildPhysics();
        // move every body to its saved pose
        for (MBody b : m.bodies) {
            MPart.Sub s = b.subs.get(0);
            CompoundTag c = pl.size() > s.part.index ? pl.getCompound(s.part.index) : new CompoundTag();
            String k = s.head ? "H" : "B";
            if (!c.contains(k + "x")) continue;
            Quat q = new Quat(c.getDouble(k + "qw"), c.getDouble(k + "qx"), c.getDouble(k + "qy"), c.getDouble(k + "qz")).normalize();
            V3 cell = new V3(c.getDouble(k + "x"), c.getDouble(k + "y"), c.getDouble(k + "z"));
            V3 pos = cell.sub(q.rotate(s.renderOffset()));
            b.rb.setTransform(pos, q);
            b.rb.vel = new V3(c.getDouble(k + "vx"), c.getDouble(k + "vy"), c.getDouble(k + "vz"));
        }
        return m;
    }

    // ================================================================================================================ util

    static final class UF {
        final int[] p;

        UF(int n) {
            p = new int[n];
            for (int i = 0; i < n; i++) p[i] = i;
        }

        int find(int x) {
            while (p[x] != x) {
                p[x] = p[p[x]];
                x = p[x];
            }
            return x;
        }

        void union(int a, int b) {
            a = find(a);
            b = find(b);
            if (a != b) p[a] = b;
        }
    }
}
