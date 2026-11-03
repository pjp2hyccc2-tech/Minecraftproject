package com.reisatsu.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The medium's old dark-green kei car, wet, a dim dome light inside. Static; pose 0 CLOSED, 1 DOOR_OPEN. */
public class MediumCar extends Entity implements Animated {
    private static final EntityDataAccessor<Boolean> DATA_DOOR = SynchedEntityData.defineId(MediumCar.class, EntityDataSerializers.BOOLEAN);
    public static final int CLOSED = 0, DOOR_OPEN = 1;
    private int poseStart;

    public MediumCar(EntityType<? extends MediumCar> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_DOOR, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DOOR.equals(key)) poseStart = tickCount;
    }

    public boolean doorOpen() {
        return entityData.get(DATA_DOOR);
    }

    public void setDoorOpen(boolean open) {
        entityData.set(DATA_DOOR, open);
    }

    @Override
    public int pose() {
        return doorOpen() ? DOOR_OPEN : CLOSED;
    }

    @Override
    public float poseTime(float partialTick) {
        return tickCount - poseStart + partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }
}
