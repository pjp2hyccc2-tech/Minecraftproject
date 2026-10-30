package com.mcbesiege.entity;

import com.mcbesiege.machine.LevelPhysics;
import com.mcbesiege.physics.Collider;
import com.mcbesiege.physics.Quat;
import com.mcbesiege.physics.RigidBody;
import com.mcbesiege.physics.V3;
import com.mcbesiege.registry.ModEntities;
import com.mcbesiege.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Iron cannonball: a sphere rigid body in the shared physics world (hits machines, smashes blocks, knocks mobs over). */
public class CannonballEntity extends Entity implements ItemSupplier {
    private RigidBody body;
    private UUID owner;
    private int life;
    private int slowTicks;
    private double power = 1;
    private final Set<Integer> hitEntities = new HashSet<>();

    public CannonballEntity(EntityType<? extends CannonballEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, V3 pos, V3 vel, ServerPlayer owner, double mass, double power) {
        CannonballEntity e = new CannonballEntity(ModEntities.CANNONBALL.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.owner = owner == null ? null : owner.getUUID();
        e.power = power;
        RigidBody rb = new RigidBody();
        Collider.Sphere s = new Collider.Sphere(0.25, V3.ZERO, mass);
        s.friction = 0.6;
        s.restitution = 0.15;
        s.ccd = true;
        s.userData = e;
        rb.addCollider(s);
        rb.recomputeMass();
        rb.setTransform(pos, Quat.IDENTITY);
        rb.vel = vel;
        rb.userData = e;
        rb.linearDamping = 0.01;
        rb.angularDamping = 0.5;
        e.body = rb;
        e.setDeltaMovement(vel.x / 20, vel.y / 20, vel.z / 20);
        level.addFreshEntity(e);
        LevelPhysics lp = LevelPhysics.get(level);
        lp.world.addBody(rb);
        lp.balls.add(e);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && body == null) discard();
        if (level().isClientSide && getDeltaMovement().lengthSqr() > 0.25) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    public void preStep() {
        if (body == null) return;
        ServerLevel level = (ServerLevel) level();
        V3 v = body.vel;
        double speed = v.len();
        if (speed < 3) return;
        V3 p = body.pos, q = p.addScaled(v, 0.05);
        AABB box = new AABB(Math.min(p.x, q.x), Math.min(p.y, q.y), Math.min(p.z, q.z), Math.max(p.x, q.x), Math.max(p.y, q.y), Math.max(p.z, q.z)).inflate(0.4);
        for (Entity e : level.getEntities(this, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            if (!hitEntities.add(e.getId())) continue;
            if (owner != null && e.getUUID().equals(owner) && life < 10) continue;
            float dmg = (float) Math.min(60, speed * 0.7 * Math.max(0.5, power));
            e.hurt(damageSource(level), dmg);
            V3 kb = v.norm().mul(Math.min(2.0, speed * 0.05));
            e.push(kb.x, kb.y + 0.2, kb.z);
            e.hurtMarked = true;
            body.vel = body.vel.mul(0.75);
        }
    }

    private DamageSource damageSource(ServerLevel level) {
        Entity ow = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        return level.damageSources().thrown(this, ow);
    }

    public void postStep() {
        if (body == null) return;
        V3 p = body.pos;
        setPos(p.x, p.y, p.z);
        setDeltaMovement(body.vel.x / 20, body.vel.y / 20, body.vel.z / 20);
        life++;
        if (body.vel.lenSq() < 1.0) slowTicks++;
        else slowTicks = 0;
        if (life > 400 || slowTicks > 60 || p.y < level().getMinBuildHeight() - 16) discard();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide && body != null) {
            LevelPhysics lp = LevelPhysics.get((ServerLevel) level());
            lp.world.removeBody(body);
            lp.balls.remove(this);
            body = null;
        }
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.CANNONBALL.get());
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
