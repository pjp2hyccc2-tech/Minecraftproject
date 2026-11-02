package com.clockworkdiver.entity;

import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 時喰い (Time Eater): a clockwork beast that nests in an escapement and devours time.
 * Its shell only cracks under Just Tick hits. Charges chrono pulses, hatches rust mites and, below half
 * health, speeds up the clock's beat.
 */
public class TimeEater extends Monster {
    public static final int PULSE_CHARGE_TICKS = 30;
    public static final double PULSE_RADIUS = 7.0;

    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(TimeEater.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PULSE = SynchedEntityData.defineId(TimeEater.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.clockworkdiver.time_eater"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private int pulseCooldown = 140;
    private int summonCooldown = 220;

    public TimeEater(EntityType<? extends TimeEater> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ENRAGED, false);
        entityData.define(PULSE, -1);
    }

    public boolean isEnraged() {
        return entityData.get(ENRAGED);
    }

    /** Ticks into the current pulse charge, or -1. */
    public int pulseCharge() {
        return entityData.get(PULSE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (!isEnraged() && getHealth() < getMaxHealth() * 0.5f) enrage();

        int charge = pulseCharge();
        if (charge >= 0) {
            charge++;
            entityData.set(PULSE, charge);
            if (level() instanceof ServerLevel sl) {
                double r = PULSE_RADIUS * (charge / (double) PULSE_CHARGE_TICKS);
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Mth.TWO_PI + charge * 0.1;
                    sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r,
                            1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            if (charge >= PULSE_CHARGE_TICKS) {
                releasePulse();
                entityData.set(PULSE, -1);
                pulseCooldown = isEnraged() ? 130 : 190;
            }
        } else if (getTarget() != null && --pulseCooldown <= 0) {
            entityData.set(PULSE, 0);
            level().playSound(null, blockPosition(), ModSounds.BOSS_PULSE.get(), SoundSource.HOSTILE, 1.4f, 0.7f);
        }

        if (getTarget() != null && --summonCooldown <= 0) {
            summonMites();
            summonCooldown = isEnraged() ? 200 : 280;
        }
    }

    private void enrage() {
        entityData.set(ENRAGED, true);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.34);
        level().playSound(null, blockPosition(), ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 2.0f, 1.2f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        DiveManager.onBossEnraged(this);
    }

    private void releasePulse() {
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0f, 1.6f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.0, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(PULSE_RADIUS, 3.0, PULSE_RADIUS))) {
            if (p.isCreative() || p.isSpectator() || p.distanceTo(this) > PULSE_RADIUS) continue;
            p.hurt(damageSources().mobAttack(this), 4.0f);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
            Vec3 push = p.position().subtract(position()).normalize().scale(1.1);
            p.push(push.x, 0.35, push.z);
            p.hurtMarked = true;
        }
    }

    private void summonMites() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.playSound(null, blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.6f, 1.8f);
        for (int i = 0; i < 2; i++) {
            RustMite mite = ModEntities.RUST_MITE.get().create(sl);
            if (mite == null) continue;
            double a = random.nextDouble() * Mth.TWO_PI;
            mite.moveTo(getX() + Math.cos(a) * 2.5, getY() + 0.5, getZ() + Math.sin(a) * 2.5, random.nextFloat() * 360.0f, 0.0f);
            LivingEntity target = getTarget();
            if (target != null) mite.setTarget(target);
            sl.addFreshEntity(mite);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, mite.getX(), mite.getY() + 0.3, mite.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BOSS_ROAR.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6f;
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
    public boolean canChangeDimensions() {
        return false;
    }
}
