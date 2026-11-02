package com.meikon.entity;

import com.meikon.game.GameManager;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 市松人形 — an Ichimatsu doll. While anyone is looking at it, it does not move (only its head turns, slowly,
 * to follow you). Look away and it comes closer. If it reaches you unseen it screams, snuffs your lantern and
 * calls the bride. Dolls sleep until the second phase; dolls placed by the director never walk.
 */
public class Ichimatsu extends Monster {
    @Nullable private BlockPos home;
    private boolean decoration;
    private int moveSound;
    private int cooldown;
    private boolean wasObserved;
    private float lastHeadYaw;

    public Ichimatsu(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setHome(BlockPos pos, boolean decoration) {
        this.home = pos.immutable();
        this.decoration = decoration;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (cooldown > 0) cooldown--;
        if (tickCount % 2 != 0) return;
        ServerPlayer observer = GameManager.dollObserver(level, this);
        boolean observed = observer != null;
        ServerPlayer target = GameManager.nearestHuntable(level, position(), 16.0);
        if (observed) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            getLookControl().setLookAt(observer, 2.5f, 2.5f);
            if (Math.abs(getYHeadRot() - lastHeadYaw) > 25.0f) {
                playSound(ModSounds.DOLL_HEAD_TURN.get(), 0.5f, 0.9f + random.nextFloat() * 0.2f);
                lastHeadYaw = getYHeadRot();
            }
            wasObserved = true;
            return;
        }
        if (wasObserved) {
            wasObserved = false;
            lastHeadYaw = getYHeadRot();
        }
        boolean awake = !decoration && GameManager.dollsAwake() && cooldown <= 0 && !GameManager.debugPeaceful;
        if (!awake || target == null) {
            getNavigation().stop();
            if (target != null) getLookControl().setLookAt(target, 4f, 4f);
            return;
        }
        getNavigation().moveTo(target, 1.2 * GameManager.dollSpeedFactor());
        if (--moveSound <= 0) {
            playSound(ModSounds.DOLL_MOVE.get(), 0.75f, 0.85f + random.nextFloat() * 0.3f);
            moveSound = 6 + random.nextInt(8);
        }
        if (distanceTo(target) < 1.15) {
            GameManager.dollAttack(target, this);
            cooldown = 20 * 40;
            getNavigation().stop();
            if (home != null) teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putBoolean("Decoration", decoration);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        decoration = tag.getBoolean("Decoration");
    }
}
