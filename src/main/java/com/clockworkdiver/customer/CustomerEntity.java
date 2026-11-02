package com.clockworkdiver.customer;

import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A customer (依頼人) visiting the player's workshop. Walks in from the street to the counter, hands over
 * a clock, waits for the repair and leaves happily once it is delivered.
 */
public class CustomerEntity extends PathfinderMob {
    public static final int ARRIVING = 0;
    public static final int WAITING = 1;
    public static final int WAITING_REPAIR = 2;
    public static final int LEAVING = 3;

    private static final EntityDataAccessor<String> PROFILE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID owner;
    @Nullable
    private BlockPos counterPos;
    @Nullable
    private BlockPos exitPos;
    private int stateTicks;

    public CustomerEntity(EntityType<? extends CustomerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PROFILE, CustomerProfile.GREGORY.id);
        entityData.define(STATE, ARRIVING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0f, 1.0f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public void setup(UUID owner, CustomerProfile profile, BlockPos counter, BlockPos exit) {
        this.owner = owner;
        this.counterPos = counter;
        this.exitPos = exit;
        entityData.set(PROFILE, profile.id);
        setCustomName(Component.translatable(profile.nameKey()));
        setCustomNameVisible(true);
    }

    public CustomerProfile profile() {
        return CustomerProfile.byId(entityData.get(PROFILE));
    }

    public int state() {
        return entityData.get(STATE);
    }

    public void setState(int state) {
        entityData.set(STATE, state);
        stateTicks = 0;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Nullable
    public BlockPos counterPos() {
        return counterPos;
    }

    public void leave() {
        setState(LEAVING);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        stateTicks++;
        int state = state();
        if (state == ARRIVING && counterPos != null) {
            double d = distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5);
            if (d < 1.7) {
                getNavigation().stop();
                setYRot(180.0f);
                yBodyRot = 180.0f;
                yHeadRot = 180.0f;
                setState(WAITING);
                WorkshopManager.onCustomerArrived(this);
            } else if (stateTicks > 500) {
                // Stuck somewhere: step to the counter.
                moveTo(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5, 180.0f, 0.0f);
            } else if (stateTicks % 20 == 1 || getNavigation().isDone()) {
                walkTo(counterPos, 0.75);
            }
        } else if ((state == WAITING || state == WAITING_REPAIR) && counterPos != null) {
            double d = distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5);
            if (d > 4.0 && stateTicks % 20 == 0) walkTo(counterPos, 0.6);
        } else if (state == LEAVING) {
            if (exitPos == null || stateTicks > 400
                    || distanceToSqr(exitPos.getX() + 0.5, exitPos.getY(), exitPos.getZ() + 0.5) < 2.5) {
                discard();
            } else if (stateTicks % 20 == 1 || getNavigation().isDone()) {
                walkTo(exitPos, 0.8);
            }
        }
    }

    /** Paths to the exact block (vanilla moveTo stops within a block of the target). */
    private void walkTo(BlockPos target, double speed) {
        Path path = getNavigation().createPath(target, 0);
        if (path != null) getNavigation().moveTo(path, speed);
        else getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            WorkshopManager.onCustomerInteract(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        if (counterPos != null) tag.put("Counter", NbtUtils.writeBlockPos(counterPos));
        if (exitPos != null) tag.put("Exit", NbtUtils.writeBlockPos(exitPos));
        tag.putString("Profile", entityData.get(PROFILE));
        tag.putInt("CustomerState", state());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        counterPos = tag.contains("Counter") ? NbtUtils.readBlockPos(tag.getCompound("Counter")) : null;
        exitPos = tag.contains("Exit") ? NbtUtils.readBlockPos(tag.getCompound("Exit")) : null;
        if (tag.contains("Profile")) entityData.set(PROFILE, tag.getString("Profile"));
        entityData.set(STATE, tag.getInt("CustomerState"));
    }
}
