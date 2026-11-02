package com.clockworkdiver.entity;

import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 歯車小鬼: a mischievous gremlin that steals gear teeth and pelts you with bolts. */
public class CogGremlin extends Monster {
    private static final EntityDataAccessor<Boolean> HAS_TOOTH = SynchedEntityData.defineId(CogGremlin.class, EntityDataSerializers.BOOLEAN);
    private int boltCooldown = 50;

    public CogGremlin(EntityType<? extends CogGremlin> type, Level level) {
        super(type, level);
        this.xpReward = 4;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HAS_TOOTH, false);
    }

    public boolean hasTooth() {
        return entityData.get(HAS_TOOTH);
    }

    public void setHasTooth(boolean tooth) {
        entityData.set(HAS_TOOTH, tooth);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            if (boltCooldown > 0) boltCooldown--;
            double dist = distanceTo(target);
            if (boltCooldown <= 0 && dist > 4.5 && dist < 18.0 && hasLineOfSight(target)) {
                throwBolt(target);
                boltCooldown = 55 + random.nextInt(35);
            }
        }
    }

    private void throwBolt(LivingEntity target) {
        BoltProjectile bolt = new BoltProjectile(level(), this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.4) - bolt.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        bolt.shoot(dx, dy + horizontal * 0.12, dz, 1.1f, 5.0f);
        level().addFreshEntity(bolt);
        playSound(ModSounds.GREMLIN.get(), 0.8f, 1.1f + random.nextFloat() * 0.3f);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (hasTooth()) {
            ItemEntity item = spawnAtLocation(new ItemStack(ModItems.GEAR_TOOTH.get()));
            if (item != null) {
                item.setGlowingTag(true);
                item.setExtendedLifetime();
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GREMLIN.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HasTooth", hasTooth());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHasTooth(tag.getBoolean("HasTooth"));
    }
}
