package com.sounan.entity;

import com.sounan.SounanConfig;
import com.sounan.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Base of every monster of the mountain: invulnerable, silent in the vanilla sense (all their sounds are played
 * deliberately by their own AI), with a synced animation pose for the models and the player it haunts.
 * Monsters spawned by the director are temporary (they are discarded when their scene is over or the world reloads).
 */
public abstract class SounanMonster extends PathfinderMob implements Animated {
    private static final EntityDataAccessor<Integer> POSE_ID = SynchedEntityData.defineId(SounanMonster.class, EntityDataSerializers.INT);
    private int poseStart;
    @Nullable protected UUID targetId;
    @Nullable private ServerPlayer targetRef;
    public int life;
    protected int stateTicks;
    /** Director-spawned: removed on reload. Residents (the sleepers) persist. */
    public boolean temporary = true;

    protected SounanMonster(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder baseAttributes(double speed, double follow) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, follow)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POSE_ID, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (POSE_ID.equals(key)) poseStart = tickCount;
    }

    @Override
    public int pose() {
        return puppetPose >= 0 ? puppetPose : entityData.get(POSE_ID);
    }

    @Override
    public float poseTime(float partialTick) {
        return puppetPose >= 0 ? puppetPoseTime : tickCount - poseStart + partialTick;
    }

    // client-side trailer actors: the pose and how far into it, set directly every frame
    private int puppetPose = -1;
    private float puppetPoseTime;

    public void puppetPose(int pose, float ticks) {
        puppetPose = pose;
        puppetPoseTime = ticks;
    }

    public int poseTicks() {
        return tickCount - poseStart;
    }

    public void setAnimPose(int pose) {
        if (pose() != pose) {
            entityData.set(POSE_ID, pose);
            poseStart = tickCount;
        }
    }

    // ------------------------------------------------------------------------------------------------ render
    /** How far (blocks) the model reaches outside the hitbox: arms, the crawl's splayed limbs, a lamp beam. */
    protected double cullMargin() {
        return 1.5;
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        // so a long arm or a head round a trunk doesn't pop out of view at the screen edge
        return getBoundingBox().inflate(cullMargin());
    }

    // ------------------------------------------------------------------------------------------------ target
    public void setTarget(ServerPlayer p) {
        targetId = p.getUUID();
        targetRef = p;
    }

    @Nullable
    public ServerPlayer target() {
        if (targetRef != null && !targetRef.isRemoved() && targetRef.isAlive() && targetRef.level() == level()) return targetRef;
        if (targetId == null || !(level() instanceof ServerLevel sl)) return null;
        Player p = sl.getPlayerByUUID(targetId);
        if (p instanceof ServerPlayer sp && sp.isAlive() && sp.level() == level()) {
            targetRef = sp;
            return sp;
        }
        return null;
    }

    /** Speed modifier so that the effective speed (attribute × modifier) is {@code effective}. */
    protected double mod(double effective) {
        double base = getAttributeValue(Attributes.MOVEMENT_SPEED);
        return effective * SounanConfig.monsterSpeed() / Math.max(0.01, base);
    }

    protected void faceTowards(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(yaw);
        yRotO = yaw;
        setYHeadRot(yaw);
        yHeadRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
    }

    protected void chase(ServerPlayer p, double effectiveSpeed) {
        double d = distanceTo(p);
        if (d < 3.5) {
            getMoveControl().setWantedPosition(p.getX(), p.getY(), p.getZ(), mod(effectiveSpeed));
        } else if (tickCount % 8 == 0 || getNavigation().isDone()) {
            getNavigation().moveTo(p, mod(effectiveSpeed));
        }
        getLookControl().setLookAt(p, 60.0f, 60.0f);
    }

    protected boolean touching(ServerPlayer p, double reach) {
        return distanceToSqr(p) < reach * reach && Math.abs(p.getY() - getY()) < 2.5;
    }

    protected void play(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getEyeY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Leaves the scene (and the world). */
    public void vanish() {
        discard();
    }

    public boolean hostileNow() {
        return false;
    }

    // ------------------------------------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        life++;
        stateTicks++;
        if (GameManager.cinematic || isNoAi()) return;
        ServerPlayer p = target();
        if (p == null && temporary && life > 40) {
            vanish();
            return;
        }
        serverTick(p);
    }

    /** Per-tick AI on the server; {@code p} may be null for residents without a target. */
    protected abstract void serverTick(@Nullable ServerPlayer p);

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Temporary", temporary);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        temporary = !tag.contains("Temporary") || tag.getBoolean("Temporary");
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
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
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }
}
