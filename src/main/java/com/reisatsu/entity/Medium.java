package com.reisatsu.entity;

import com.reisatsu.game.GameManager;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 霊媒師 八雲トキ: waits by her car in the parking lot, turns to whoever comes near, takes the photos and tells their
 * stories (right-click), and at the end walks in to table 7 for the exorcism. A living person: never a spirit.
 */
public class Medium extends PathfinderMob implements Animated {
    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(Medium.class, EntityDataSerializers.INT);
    public static final int IDLE = 0, TALK = 1, PRAY = 2, WALK = 3, KNEEL = 4, BOW = 5;

    private int poseStart;
    private int talkTicks;
    private int murmurIn;
    /** Driven by the story (the exorcism): no idle turning. */
    public boolean scripted;
    @Nullable private Vec3 walkGoal;
    private double walkSpeed = 0.06;
    @Nullable private List<Vec3> path;
    private int pathIndex;

    public Medium(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoGravity(true);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 64.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_POSE, IDLE);
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

    public void setAnimPose(int pose) {
        if (entityData.get(DATA_POSE) != pose) {
            entityData.set(DATA_POSE, pose);
            poseStart = tickCount;
        }
    }

    /** She speaks for a while (TALK pose, murmurs under the subtitles). */
    public void talk(int ticks) {
        talkTicks = Math.max(talkTicks, ticks);
        murmurIn = 0;
    }

    public void walkTo(@Nullable Vec3 goal, double speed) {
        walkGoal = goal;
        walkSpeed = speed;
        path = null;
        if (goal != null) {
            try {
                Path p = getNavigation().createPath(BlockPos.containing(goal), 0);
                if (p != null) {
                    path = new ArrayList<>();
                    for (int i = 0; i < p.getNodeCount(); i++) {
                        BlockPos n = p.getNodePos(i);
                        path.add(new Vec3(n.getX() + 0.5, Layout.FEET_Y, n.getZ() + 0.5));
                    }
                }
            } catch (Exception ignored) {
                path = null;
            }
            pathIndex = 0;
        }
    }

    public boolean arrived() {
        return walkGoal == null;
    }

    @Override
    public void tick() {
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        setOnGround(true);
        if (level().isClientSide) return;
        if (walkGoal != null) {
            setAnimPose(WALK);
            Vec3 node = path != null && pathIndex < path.size() - 1 ? path.get(pathIndex) : walkGoal;
            if (glide(node)) {
                if (node == walkGoal) {
                    walkGoal = null;
                    setAnimPose(IDLE);
                } else {
                    pathIndex++;
                }
            }
            if (tickCount % 9 == 0) {
                level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.WOOL_STEP, SoundSource.NEUTRAL, 0.25f, 1.2f);
            }
            return;
        }
        if (scripted) return;
        if (talkTicks > 0) {
            talkTicks--;
            setAnimPose(TALK);
            if (--murmurIn <= 0) {
                play(ModSounds.MEDIUM_VOICE.get(), 0.55f, 0.95f + random.nextFloat() * 0.1f);
                murmurIn = 28 + random.nextInt(24);
            }
        } else if (pose() == TALK) {
            setAnimPose(IDLE);
        }
        Player near = level().getNearestPlayer(this, 9.0);
        if (near != null && !near.isSpectator()) {
            Vec3 eye = near.getEyePosition();
            double dx = eye.x - getX(), dz = eye.z - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            float diff = Mth.wrapDegrees(yaw - getYRot());
            setYRot(getYRot() + Mth.clamp(diff, -6.0f, 6.0f));
            yBodyRot = getYRot();
            setYHeadRot(yaw);
            double dy = eye.y - getEyeY();
            setXRot((float) Mth.clamp(-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -30, 30));
        }
    }

    private boolean glide(Vec3 to) {
        double dx = to.x - getX(), dz = to.z - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-4) return true;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float diff = Mth.wrapDegrees(yaw - getYRot());
        float n = getYRot() + Mth.clamp(diff, -15.0f, 15.0f);
        setYRot(n);
        yBodyRot = n;
        setYHeadRot(n);
        setXRot(8.0f);
        if (d <= walkSpeed) {
            setPos(to.x, Layout.FEET_Y, to.z);
            return true;
        }
        setPos(getX() + dx / d * walkSpeed, Layout.FEET_Y, getZ() + dz / d * walkSpeed);
        return false;
    }

    public void faceYaw(float yaw) {
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getEyeY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp && level() instanceof ServerLevel) {
            GameManager.talkToMedium(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                Medium.this.yBodyRot = Medium.this.getYRot();
            }
        };
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GhostNavigation(this, level);
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isInWall() {
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
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }
}
