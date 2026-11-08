package com.eternalrealm.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A boss ({@code eternalrealm:boss_b1} .. {@code boss_b8}, {@code boss_final}). The integrator drives the fight
 * with the boss module's {@code BossBrain} and uses this API for the visuals:
 * <ul>
 *     <li>{@link #playAnimation(BossAnimation)}: one-shot actions (slam, sweep, cast, charge, summon, phase change,
 *         stagger, death) and the roar; the boss stands still while one plays.</li>
 *     <li>{@link #setTelegraphing}, {@link #setShielded}, {@link #setEnraged}, {@link #setPhase}: continuous states
 *         (wind-up loop and glow, shield overlay, red emissive eyes/runes, phase looks of the final boss).</li>
 *     <li>{@link #setAiFrozen(boolean)}: stop moving and attacking while a scripted ability plays.</li>
 * </ul>
 * Without scripts it chases the nearest player and attacks with the slam (hit at the animation's impact frame).
 * Never despawns, takes no fall, suffocation, drowning or cramming damage and cannot be knocked back or pushed.
 */
public class ERBoss extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_TELEGRAPHING = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.INT);
    private static final int MAX_PHASE = 9;

    public final AnimationState idleAnimation = new AnimationState();
    private final AnimationState[] actionStates = new AnimationState[BossAnimation.values().length];
    private final int[] clientTicksLeft = new int[BossAnimation.values().length];

    private final BossKind kind;
    private boolean aiFrozen;
    private BossAnimation playing;
    private int playingTicksLeft;
    private LivingEntity pendingHitTarget;
    private int pendingHitTicks;
    private int deathTicks;

    public ERBoss(EntityType<? extends ERBoss> type, Level level, BossKind kind) {
        super(type, level);
        this.kind = kind;
        for (int i = 0; i < this.actionStates.length; i++) {
            this.actionStates[i] = new AnimationState();
        }
        this.xpReward = 100;
        setPersistenceRequired();
    }

    // ---------------------------------------------------------------------------------------------- identity

    /** "b1".."b8" or "final" (the boss module's ids). */
    public String bossId() {
        return this.kind.id();
    }

    public BossKind kind() {
        return this.kind;
    }

    /**
     * Look (texture and parts) the client uses: the boss id, or {@code final_p1}..{@code final_p3} for the phases of
     * the final boss.
     */
    public String lookKey() {
        int phase = getPhase();
        if (this.kind == BossKind.FINAL && phase > 0) {
            return "final_p" + Math.min(3, phase);
        }
        return bossId();
    }

    /** Model scale of the current look. */
    public float modelScale() {
        return RigData.scale(this.kind.rig().id(), lookKey());
    }

    // -------------------------------------------------------------------------------------------- animations

    /**
     * Plays {@code animation} on every client (server side). IDLE and WALK stop the current action. ROAR plays two
     * wind-up cycles (use {@link #setTelegraphing} to hold it). While an action plays the boss stands still.
     */
    public void playAnimation(BossAnimation animation) {
        if (level().isClientSide) {
            return;
        }
        this.pendingHitTarget = null;
        if (animation.isAction()) {
            this.playing = animation;
            this.playingTicksLeft = durationTicks(animation);
            getNavigation().stop();
        } else {
            this.playing = null;
            this.playingTicksLeft = 0;
        }
        level().broadcastEntityEvent(this, animation.eventId());
        playActionSound(animation);
    }

    /** The action playing on the server, if any. */
    public Optional<BossAnimation> playingAnimation() {
        return Optional.ofNullable(this.playing);
    }

    /** Ticks until the current action ends (0 if none). */
    public int animationTicksLeft() {
        return this.playing == null ? 0 : this.playingTicksLeft;
    }

    /** Length of an action in ticks (ROAR: two cycles of the loop, at least 2 seconds). */
    public int durationTicks(BossAnimation animation) {
        float seconds = RigData.length(this.kind.rig().id(), lookKey(), animation.ordinal());
        if (animation == BossAnimation.ROAR) {
            seconds = Math.max(2.0F, seconds * 2.0F);
        }
        return Math.max(1, Mth.ceil(seconds * 20.0F));
    }

    /** Ticks from the start of an action to its hit frame. */
    public int impactTicks(BossAnimation animation) {
        return Math.max(1, Mth.ceil(RigData.impact(this.kind.rig().id(), lookKey(), animation.ordinal()) * 20.0F));
    }

    /** Client animation state of an animation (IDLE and WALK share the idle state). */
    public AnimationState animationState(BossAnimation animation) {
        return animation.isAction() ? this.actionStates[animation.ordinal()] : this.idleAnimation;
    }

    private void playActionSound(BossAnimation animation) {
        SoundEvent[] sounds = this.kind.sounds();
        SoundEvent sound = switch (animation) {
            case ROAR, PHASE_CHANGE -> sounds[3];
            case MELEE_SLAM, MELEE_SWEEP -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case CAST -> SoundEvents.EVOKER_CAST_SPELL;
            case SUMMON -> SoundEvents.EVOKER_PREPARE_SUMMON;
            case STAGGER -> sounds[1];
            default -> null;
        };
        if (sound != null) {
            playSound(sound, 2.0F, 0.9F + getRandom().nextFloat() * 0.2F);
        }
    }

    // -------------------------------------------------------------------------------------------- states

    public void setTelegraphing(boolean telegraphing) {
        this.entityData.set(DATA_TELEGRAPHING, telegraphing);
    }

    public boolean isTelegraphing() {
        return this.entityData.get(DATA_TELEGRAPHING);
    }

    /** Visual only (shield overlay); the immunity itself is the caller's rule. */
    public void setShielded(boolean shielded) {
        this.entityData.set(DATA_SHIELDED, shielded);
    }

    public boolean isShielded() {
        return this.entityData.get(DATA_SHIELDED);
    }

    /** Red emissive eyes and runes, angry particles. */
    public void setEnraged(boolean enraged) {
        this.entityData.set(DATA_ENRAGED, enraged);
    }

    public boolean isEnraged() {
        return this.entityData.get(DATA_ENRAGED);
    }

    /** Phase index (0-based). The final boss changes parts and texture with it (phases 1, 2, 3). */
    public void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, Mth.clamp(phase, 0, MAX_PHASE));
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    /** While frozen the boss neither moves nor attacks on its own (scripted abilities). */
    public void setAiFrozen(boolean frozen) {
        this.aiFrozen = frozen;
        if (frozen) {
            getNavigation().stop();
            this.pendingHitTarget = null;
        }
    }

    public boolean isAiFrozen() {
        return this.aiFrozen;
    }

    /** Turns the body and head towards a point instantly (useful while frozen). */
    public void faceTowards(double x, double z) {
        float yaw = (float) (Mth.atan2(z - getZ(), x - getX()) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TELEGRAPHING, false);
        this.entityData.define(DATA_SHIELDED, false);
        this.entityData.define(DATA_ENRAGED, false);
        this.entityData.define(DATA_PHASE, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BossPhase", getPhase());
        tag.putBoolean("BossEnraged", isEnraged());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPhase(tag.getInt("BossPhase"));
        setEnraged(tag.getBoolean("BossEnraged"));
    }

    // -------------------------------------------------------------------------------------------------- AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new BossMeleeGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ERMonster.class, ERBoss.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Distance from the center at which the slam connects (plus half the target's width). */
    public double meleeReach() {
        return getBbWidth() * 0.5 + 2.2;
    }

    public boolean inMeleeReach(LivingEntity target, double tolerance) {
        double reach = (meleeReach() + target.getBbWidth() * 0.5) * tolerance;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY() - getY();
        return dx * dx + dz * dz <= reach * reach && dy > -2.5 && dy < getBbHeight();
    }

    /** Starts the AI's slam at {@code target}; the hit lands at the slam's impact frame. */
    public boolean startSlam(LivingEntity target) {
        if (level().isClientSide || this.playing != null || this.aiFrozen || isDeadOrDying()) {
            return false;
        }
        playAnimation(BossAnimation.MELEE_SLAM);
        this.pendingHitTarget = target;
        this.pendingHitTicks = impactTicks(BossAnimation.MELEE_SLAM);
        return true;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.aiFrozen || this.playing != null;
    }

    // ------------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (this.playing != null && --this.playingTicksLeft <= 0) {
            this.playing = null;
        }
        if (this.pendingHitTarget != null && --this.pendingHitTicks <= 0) {
            LivingEntity target = this.pendingHitTarget;
            this.pendingHitTarget = null;
            impactEffects();
            if (target.isAlive() && inMeleeReach(target, 1.3)) {
                doHurtTarget(target);
            }
        }
    }

    private void impactEffects() {
        if (level() instanceof ServerLevel server) {
            Vec3 look = Vec3.directionFromRotation(0.0F, getYRot());
            double reach = getBbWidth() * 0.5 + 1.5;
            server.sendParticles(ParticleTypes.EXPLOSION, getX() + look.x * reach, getY() + 0.2, getZ() + look.z * reach,
                    2, 0.4, 0.1, 0.4, 0.0);
            server.sendParticles(ParticleTypes.CLOUD, getX() + look.x * reach, getY() + 0.1, getZ() + look.z * reach,
                    12, 0.8, 0.05, 0.8, 0.05);
        }
        playSound(this.kind.sounds()[4], 1.6F, 0.8F + getRandom().nextFloat() * 0.2F);
    }

    private void clientTick() {
        if (isDeadOrDying()) {
            for (BossAnimation animation : BossAnimation.values()) {
                if (animation != BossAnimation.DEATH) {
                    animationState(animation).stop();
                }
            }
            this.actionStates[BossAnimation.DEATH.ordinal()].startIfStopped(this.tickCount);
            if (getRandom().nextFloat() < 0.6F) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0.0, 0.05, 0.0);
            }
            return;
        }
        this.idleAnimation.startIfStopped(this.tickCount);
        boolean actionActive = false;
        for (BossAnimation animation : BossAnimation.values()) {
            int slot = animation.ordinal();
            if (this.clientTicksLeft[slot] > 0 && --this.clientTicksLeft[slot] == 0) {
                this.actionStates[slot].stop();
            }
            if (animation.isAction() && animation != BossAnimation.ROAR && this.actionStates[slot].isStarted()) {
                actionActive = true;
            }
        }
        AnimationState roar = this.actionStates[BossAnimation.ROAR.ordinal()];
        if (isTelegraphing() && !actionActive) {
            roar.startIfStopped(this.tickCount);
        } else if (this.clientTicksLeft[BossAnimation.ROAR.ordinal()] <= 0) {
            roar.stop();
        }
        ParticleOptions theme = this.kind.themeParticle();
        if (isTelegraphing() && getRandom().nextFloat() < 0.7F) {
            level().addParticle(theme, getRandomX(1.2), getRandomY(), getRandomZ(1.2), 0.0, 0.05, 0.0);
        }
        if (isEnraged() && getRandom().nextFloat() < 0.25F) {
            level().addParticle(ParticleTypes.FLAME, getRandomX(0.9), getY() + getBbHeight() * (0.6 + 0.4 * getRandom().nextFloat()),
                    getRandomZ(0.9), 0.0, 0.03, 0.0);
        }
        if (isShielded() && getRandom().nextFloat() < 0.4F) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(1.3), getRandomY(), getRandomZ(1.3), 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        BossAnimation animation = BossAnimation.byEvent(id);
        if (animation == null) {
            super.handleEntityEvent(id);
            return;
        }
        for (BossAnimation other : BossAnimation.values()) {
            if (other.isAction() && other != BossAnimation.DEATH) {
                this.actionStates[other.ordinal()].stop();
                this.clientTicksLeft[other.ordinal()] = 0;
            }
        }
        if (animation.isAction()) {
            this.actionStates[animation.ordinal()].start(this.tickCount);
            this.clientTicksLeft[animation.ordinal()] = durationTicks(animation) + 1;
        }
    }

    // ----------------------------------------------------------------------------------------------- death

    /** Ticks the death animation lasts before the boss is removed. */
    public int deathDurationTicks() {
        return Mth.ceil(RigData.length(this.kind.rig().id(), lookKey(), BossAnimation.DEATH.ordinal()) * 20.0F) + 4;
    }

    /** Dramatic death: the death animation with smoke and bursts, then a final explosion and removal. */
    @Override
    protected void tickDeath() {
        this.deathTicks++;
        if (level().isClientSide()) {
            return;
        }
        int total = deathDurationTicks();
        if (level() instanceof ServerLevel server) {
            if (this.deathTicks % 6 == 0) {
                server.sendParticles(ParticleTypes.EXPLOSION, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (this.deathTicks % 3 == 0) {
                server.sendParticles(this.kind.themeParticle(), getX(), getY() + getBbHeight() * 0.5, getZ(), 6,
                        getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
            }
            if (this.deathTicks >= total && !isRemoved()) {
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + getBbHeight() * 0.4, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 30,
                        getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.05);
            }
        }
        if (this.deathTicks >= total && !isRemoved()) {
            playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.7F);
            level().broadcastEntityEvent(this, (byte) 60);
            remove(Entity.RemovalReason.KILLED);
        }
    }

    public int deathTicks() {
        return this.deathTicks;
    }

    // ------------------------------------------------------------------------------------------ properties

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        // bosses are removed by the fight logic only
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof ERMonster || other instanceof ERBoss || super.isAlliedTo(other);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(getBbWidth() * 0.8 + 1.0, 1.0, getBbWidth() * 0.8 + 1.0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.kind.sounds()[0];
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.kind.sounds()[1];
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.kind.sounds()[2];
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }
}
