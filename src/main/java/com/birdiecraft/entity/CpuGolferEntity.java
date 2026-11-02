package com.birdiecraft.entity;

import com.birdiecraft.golf.engine.Difficulty;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Visual stand-in for a CPU golfer: stands at its ball and plays a swing animation. */
public class CpuGolferEntity extends PathfinderMob {
    /** Entity event id that starts the swing animation on clients. */
    public static final byte EVENT_SWING = 101;
    public static final int SWING_TICKS = 34;
    /** Tick of the swing animation where the club meets the ball. */
    public static final int IMPACT_TICK = 19;

    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(CpuGolferEntity.class, EntityDataSerializers.INT);

    /** Client-side animation clock (tickCount when the swing started, or -1). */
    public int swingStart = -1;

    public CpuGolferEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        setSilent(true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SKIN, 0);
    }

    public int getSkin() {
        return this.entityData.get(SKIN);
    }

    public void setup(String name, int skin, Difficulty difficulty) {
        this.entityData.set(SKIN, skin);
        setCustomName(Component.literal(name).append(Component.literal(" [")).append(Component.translatable(difficulty.translationKey()))
                .append(Component.literal("]")));
        setCustomNameVisible(true);
    }

    public void placeAt(double x, double y, double z, float yaw) {
        moveTo(x, y, z, yaw, 0f);
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        this.yRotO = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRotO = yaw;
        teleportTo(x, y, z);
    }

    public void playSwing() {
        level().broadcastEntityEvent(this, EVENT_SWING);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SWING) {
            swingStart = this.tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** 0..1 progress of the swing animation, or -1 when idle. */
    public float swingProgress(float partialTick) {
        if (swingStart < 0) return -1f;
        float t = (this.tickCount - swingStart + partialTick) / SWING_TICKS;
        if (t > 1.6f) {
            swingStart = -1;
            return -1f;
        }
        return Math.min(1f, t);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", getSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SKIN, tag.getInt("Skin"));
    }
}
