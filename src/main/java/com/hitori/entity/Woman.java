package com.hitori.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 女 (docs/DESIGN.md §3.7, §5): the woman who is only ever in a camera's feed — long wet black hair, a stained white
 * nightdress, too-long fingers — standing in a corner facing the wall, or the lens. The client renders her to camera
 * views only. Each time you come back to her feed she is closer to the lens. She never attacks. The director places
 * and moves her ({@code game/Director}); she does nothing by herself.
 */
public class Woman extends PathfinderMob implements Animated {
    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(Woman.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CAMERA = SynchedEntityData.defineId(Woman.class, EntityDataSerializers.INT);
    public static final int STAND = 0, FACE = 1, CLOSE = 2, GONE = 3;

    private int poseStart;
    /** How many times she has come closer (server). */
    public int stage;
    /** Ticks left before she fades away by herself (server, -1 = stays). */
    public int lifeTicks = -1;
    /** Ticks the player has watched her feed at this stage (server): she comes closer only after being seen. */
    public int seenTicks;

    public Woman(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoGravity(true);
        noPhysics = true;
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_POSE, STAND);
        entityData.define(DATA_CAMERA, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_POSE.equals(key)) poseStart = tickCount;
    }

    @Override
    public int pose() {
        return entityData.get(DATA_POSE);
    }

    @Override
    public float poseTime(float partialTick) {
        return tickCount - poseStart + partialTick;
    }

    @Override
    public float glitch(float partialTick) {
        return pose() == CLOSE ? 0.6f : 0.15f;
    }

    public void setAnimPose(int pose) {
        if (entityData.get(DATA_POSE) != pose) {
            entityData.set(DATA_POSE, pose);
            poseStart = tickCount;
        }
    }

    /** The camera (number) whose feed she haunts. */
    public int haunts() {
        return entityData.get(DATA_CAMERA);
    }

    public void setHaunts(int camera) {
        entityData.set(DATA_CAMERA, camera);
    }

    /** Stands at a point facing a point (the lens, or the wall). */
    public void stand(Vec3 at, Vec3 face, int pose) {
        double dx = face.x - at.x, dz = face.z - at.z;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        moveTo(at.x, at.y, at.z, yaw, 0.0f);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        setYHeadRot(yaw);
        yHeadRotO = yaw;
        setAnimPose(pose);
    }

    @Override
    public void tick() {
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        setOnGround(true);
        if (level().isClientSide) return;
        if (lifeTicks > 0 && --lifeTicks == 0) discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(0.8);
    }
}
