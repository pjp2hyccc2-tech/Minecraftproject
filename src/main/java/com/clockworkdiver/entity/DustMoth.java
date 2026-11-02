package com.clockworkdiver.entity;

import com.clockworkdiver.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 埃蛾: a dusty moth that circles above you and swoops down. */
public class DustMoth extends Monster {
    private int diveCooldown = 60;
    private int diveTicks;
    private int attackCooldown;
    private final float orbitSeed;

    public DustMoth(EntityType<? extends DustMoth> type, Level level) {
        super(type, level);
        this.xpReward = 3;
        this.orbitSeed = level.random.nextFloat() * Mth.TWO_PI;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (attackCooldown > 0) attackCooldown--;
        LivingEntity target = getTarget();
        Vec3 desired;
        double speed;
        if (target == null || !target.isAlive()) {
            desired = position().add(Math.sin(tickCount * 0.05 + orbitSeed) * 2.0, Math.sin(tickCount * 0.03) * 0.5, Math.cos(tickCount * 0.05 + orbitSeed) * 2.0);
            speed = 0.12;
        } else if (diveTicks > 0) {
            diveTicks--;
            desired = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
            speed = 0.5;
            if (attackCooldown == 0 && distanceToSqr(target) < 2.2) {
                doHurtTarget(target);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
                attackCooldown = 20;
                diveTicks = 0;
                diveCooldown = 70 + random.nextInt(50);
            }
        } else {
            double angle = tickCount * 0.045 + orbitSeed;
            desired = target.position().add(Math.cos(angle) * 4.0, 3.0 + Math.sin(tickCount * 0.1) * 0.6, Math.sin(angle) * 4.0);
            speed = 0.25;
            if (--diveCooldown <= 0) {
                diveTicks = 30;
                playSound(ModSounds.MOTH.get(), 0.9f, 0.9f + random.nextFloat() * 0.3f);
            }
        }
        Vec3 delta = desired.subtract(position());
        double len = delta.length();
        if (len > 0.05) {
            Vec3 v = getDeltaMovement().scale(0.82).add(delta.scale(1.0 / len).scale(speed * 0.3));
            setDeltaMovement(v);
            if (v.horizontalDistanceSqr() > 1.0E-4) {
                float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0f;
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MOTH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
