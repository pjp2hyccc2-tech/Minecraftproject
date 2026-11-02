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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
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
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(TimeEater.class, EntityDataSerializers.INT);

    /** Variants (same numbers as the Defect.BOSS_* constants). */
    public static final int NORMAL = 0;
    /** 黒殻の時喰い: black shell, blinks next to its prey. */
    public static final int SHADOW = 1;
    /** 女王の影: the Queen's shade, calls dust moths. */
    public static final int SHADE = 2;
    /** 時喰いの女王: three phases; races the beat, then turns it inside out. */
    public static final int QUEEN = 3;
    private int queenPhase = 1;

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
        entityData.define(VARIANT, NORMAL);
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        entityData.set(VARIANT, Math.max(0, Math.min(QUEEN, variant)));
        applyVariant();
    }

    private void applyVariant() {
        int v = variant();
        String key = switch (v) {
            case SHADOW -> "entity.clockworkdiver.time_eater.shadow";
            case SHADE -> "entity.clockworkdiver.time_eater.shade";
            case QUEEN -> "entity.clockworkdiver.time_eater.queen";
            default -> "entity.clockworkdiver.time_eater";
        };
        bossEvent.setName(Component.translatable(key));
        bossEvent.setColor(v == SHADOW ? BossEvent.BossBarColor.RED : v >= SHADE ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.YELLOW);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(v == SHADOW ? 0.29 : v == QUEEN ? 0.24 : 0.26);
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(v == QUEEN ? 11.0 : v >= SHADOW ? 9.0 : 8.0);
        refreshDimensions();
    }

    /** Model scale relative to the ordinary Time Eater. */
    public static float scaleOf(int variant) {
        return switch (variant) {
            case SHADE -> 1.12f;
            case QUEEN -> 1.55f;
            default -> 1.0f;
        };
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(scaleOf(variant()));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (VARIANT.equals(key)) refreshDimensions();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putInt("QueenPhase", queenPhase);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, tag.getInt("Variant"));
        queenPhase = Math.max(1, tag.getInt("QueenPhase"));
        applyVariant();
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
        if (variant() == QUEEN) {
            if (queenPhase < 2 && getHealth() < getMaxHealth() * 0.66f) queenPhase(2);
            if (queenPhase < 3 && getHealth() < getMaxHealth() * 0.33f) queenPhase(3);
        } else if (!isEnraged() && getHealth() < getMaxHealth() * (variant() == SHADOW ? 0.6f : 0.5f)) {
            enrage();
        }

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
                pulseCooldown = variant() == QUEEN ? (queenPhase >= 3 ? 90 : 140) : isEnraged() ? 130 : 190;
                if (variant() == SHADOW) blink();
            }
        } else if (getTarget() != null && --pulseCooldown <= 0) {
            entityData.set(PULSE, 0);
            level().playSound(null, blockPosition(), ModSounds.BOSS_PULSE.get(), SoundSource.HOSTILE, 1.4f, 0.7f);
        }

        if (getTarget() != null && --summonCooldown <= 0) {
            summonMites();
            summonCooldown = switch (variant()) {
                case SHADOW -> isEnraged() ? 160 : 220;
                case QUEEN -> queenPhase >= 2 ? 170 : 230;
                default -> isEnraged() ? 200 : 280;
            };
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

    private void queenPhase(int phase) {
        queenPhase = phase;
        entityData.set(ENRAGED, phase >= 3);
        level().playSound(null, blockPosition(), ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 2.5f, phase == 2 ? 1.0f : 0.7f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2.0, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 80, 1.5, 1.5, 1.5, 0.1);
        }
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(phase >= 3 ? 0.31 : 0.27);
        DiveManager.onBossPhase(this, phase);
    }

    /** The black-shelled Time Eater slips through the gears and reappears beside its prey. */
    private void blink() {
        LivingEntity target = getTarget();
        if (target == null || !(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 0.8, 0.8, 0.8, 0.02);
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            if (randomTeleport(target.getX() + Math.cos(a) * 3.5, target.getY(), target.getZ() + Math.sin(a) * 3.5, false)) {
                sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.6f);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 0.8, 0.8, 0.8, 0.02);
                return;
            }
        }
    }

    private void releasePulse() {
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0f, 1.6f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.0, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        double radius = pulseRadius();
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 3.0, radius))) {
            if (p.isCreative() || p.isSpectator() || p.distanceTo(this) > radius) continue;
            p.hurt(damageSources().mobAttack(this), 4.0f);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
            Vec3 push = p.position().subtract(position()).normalize().scale(1.1);
            p.push(push.x, 0.35, push.z);
            p.hurtMarked = true;
        }
    }

    public double pulseRadius() {
        return switch (variant()) {
            case SHADE -> 8.0;
            case QUEEN -> 9.0;
            default -> PULSE_RADIUS;
        };
    }

    private void summonMites() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.playSound(null, blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.6f, 1.8f);
        int v = variant();
        for (int i = 0; i < (v == QUEEN ? 3 : 2); i++) {
            EntityType<? extends Mob> kind = ModEntities.RUST_MITE.get();
            if (v == SHADE || (v == QUEEN && queenPhase >= 2 && i == 2)) kind = ModEntities.DUST_MOTH.get();
            else if (v == QUEEN && i == 1) kind = ModEntities.COG_GREMLIN.get();
            Mob mite = kind.create(sl);
            if (mite == null) continue;
            double a = random.nextDouble() * Mth.TWO_PI;
            mite.moveTo(getX() + Math.cos(a) * 2.5, getY() + (kind == ModEntities.DUST_MOTH.get() ? 3.5 : 0.5), getZ() + Math.sin(a) * 2.5,
                    random.nextFloat() * 360.0f, 0.0f);
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
