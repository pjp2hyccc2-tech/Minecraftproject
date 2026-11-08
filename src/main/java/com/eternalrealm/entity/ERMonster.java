package com.eternalrealm.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Base of the four enemy rigs. One entity type per rig; the synced variant ({@link #setVariant(String)}) selects
 * texture, scale (and hitbox), parts, sounds and behaviour.
 *
 * <p>Combat is animation driven: {@link #startMeleeAttack} / {@link #startRangedAttack} start a swing or cast
 * (chosen randomly between two attacks for melee) and the hit or bolt happens at the animation's impact frame
 * ({@link RigData#impact}), so players can read and dodge it. The mob does not move while an action plays.
 * Attributes (health, damage, armor, speed) are meant to be set by the spawner; the defaults are sane values for
 * testing. Enemies never despawn on their own: whoever spawns them removes them.</p>
 */
public abstract class ERMonster extends Monster {
    private static final EntityDataAccessor<String> DATA_VARIANT =
            SynchedEntityData.defineId(ERMonster.class, EntityDataSerializers.STRING);

    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState attack1Animation = new AnimationState();
    public final AnimationState attack2Animation = new AnimationState();
    public final AnimationState castAnimation = new AnimationState();
    public final AnimationState hurtAnimation = new AnimationState();
    public final AnimationState spawnAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();

    private final int[] clientTicksLeft = new int[EnemyAnim.values().length];
    private int lastHurtTime;

    private EnemyAnim action;
    private LivingEntity actionTarget;
    private int actionTick;
    private int actionImpactTick;
    private int actionEndTick;
    private boolean actionImpactDone;
    private int spawnLockTicks;
    private boolean spawnAnnounced;
    private int deathTicks;

    protected ERMonster(EntityType<? extends ERMonster> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    /** The rig of this entity type. Must not depend on instance fields (called during construction). */
    public abstract EnemyRig rig();

    // ------------------------------------------------------------------------------------------------ variant

    /** Variant ids of a rig; the first is the default. */
    public static List<String> variantsOf(EnemyRig rig) {
        return rig.variants();
    }

    /** Variant ids of a rig given by id ("humanoid", "beast", "golem", "flyer"); empty if unknown. */
    public static List<String> variantsOf(String rigId) {
        return RigData.variants(rigId);
    }

    public String getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    /**
     * Selects the look and behaviour. Server side; synced to clients. Also resizes the hitbox.
     *
     * @throws IllegalArgumentException if the rig has no such variant
     */
    public void setVariant(String id) {
        if (!variantsOf(rig()).contains(id)) {
            throw new IllegalArgumentException("unknown " + rig().id() + " variant: " + id);
        }
        this.entityData.set(DATA_VARIANT, id);
        refreshDimensions();
    }

    public EnemyVariant variant() {
        return EnemyVariant.of(rig(), getVariant());
    }

    /** Model and hitbox scale of the current variant. */
    public float variantScale() {
        return RigData.scale(rig().id(), getVariant());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, RigData.variants(rig().id()).isEmpty() ? "" : RigData.variants(rig().id()).get(0));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_VARIANT.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(variantScale());
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.eternalrealm.enemy." + getVariant());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Variant", getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("Variant");
        if (variantsOf(rig()).contains(id)) {
            setVariant(id);
        }
        this.spawnAnnounced = true;
    }

    // --------------------------------------------------------------------------------------------------- AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new EnemyCombatGoal(this));
        this.goalSelector.addGoal(5, wanderGoal());
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ERMonster.class, ERBoss.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Idle movement; called during construction. */
    protected Goal wanderGoal() {
        return new WaterAvoidingRandomStrollGoal(this, 0.8);
    }

    /** True while an attack, cast or the spawn animation plays: the mob stands still. */
    public boolean isActing() {
        return this.action != null || this.spawnLockTicks > 0;
    }

    /** The action animation currently playing (server side), or {@code null}. */
    public EnemyAnim currentAction() {
        return this.action;
    }

    public boolean isRangedAttacker() {
        return variant().ranged();
    }

    /** Distance from the center at which melee attacks connect (plus half the target's width). */
    public double meleeReach() {
        return getBbWidth() * 0.5 + 1.0 + 0.3 * variantScale();
    }

    public boolean inMeleeReach(LivingEntity target, double tolerance) {
        double reach = (meleeReach() + target.getBbWidth() * 0.5) * tolerance;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY() - getY();
        double vertical = Math.max(getBbHeight(), 2.0) + (variant().floats() ? 2.0 : 0.5);
        return dx * dx + dz * dz <= reach * reach && dy > -vertical && dy < vertical;
    }

    /** Starts one of the two melee attacks against {@code target}; the hit lands at the impact frame. */
    public boolean startMeleeAttack(LivingEntity target) {
        if (level().isClientSide || isActing() || isDeadOrDying()) {
            return false;
        }
        beginAction(getRandom().nextBoolean() ? EnemyAnim.ATTACK1 : EnemyAnim.ATTACK2, target);
        return true;
    }

    /** Starts the cast; an {@link EnemyBolt} is fired at the impact frame. */
    public boolean startRangedAttack(LivingEntity target) {
        if (level().isClientSide || isActing() || isDeadOrDying()) {
            return false;
        }
        beginAction(EnemyAnim.CAST, target);
        return true;
    }

    /** Plays an action animation for every client without any gameplay effect (scripted scenes, tests). */
    public void playAnimation(EnemyAnim anim) {
        if (!level().isClientSide && (anim == EnemyAnim.ATTACK1 || anim == EnemyAnim.ATTACK2
                || anim == EnemyAnim.CAST || anim == EnemyAnim.SPAWN)) {
            level().broadcastEntityEvent(this, anim.eventId());
        }
    }

    private void beginAction(EnemyAnim anim, LivingEntity target) {
        this.action = anim;
        this.actionTarget = target;
        this.actionTick = 0;
        this.actionImpactDone = false;
        this.actionImpactTick = Math.max(1, Mth.ceil(timing(anim, true) * 20.0F));
        this.actionEndTick = Math.max(this.actionImpactTick + 1, Mth.ceil(timing(anim, false) * 20.0F));
        getNavigation().stop();
        level().broadcastEntityEvent(this, anim.eventId());
    }

    private float timing(EnemyAnim anim, boolean impact) {
        return impact ? RigData.impact(rig().id(), getVariant(), anim.ordinal())
                : RigData.length(rig().id(), getVariant(), anim.ordinal());
    }

    private void tickAction() {
        if (this.spawnLockTicks > 0) {
            this.spawnLockTicks--;
        }
        if (this.action == null) {
            return;
        }
        this.actionTick++;
        LivingEntity target = this.actionTarget;
        if (target != null && target.isAlive()) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (!this.actionImpactDone && this.actionTick >= this.actionImpactTick) {
            this.actionImpactDone = true;
            if (target != null && target.isAlive()) {
                if (this.action == EnemyAnim.CAST) {
                    shootBolt(target);
                } else if (inMeleeReach(target, 1.35) && getSensing().hasLineOfSight(target)) {
                    doHurtTarget(target);
                }
            }
        }
        if (this.actionTick >= this.actionEndTick) {
            this.action = null;
            this.actionTarget = null;
        }
    }

    /** Fires one bolt at {@code target} from the eyes; damage = ATTACK_DAMAGE. */
    protected void shootBolt(LivingEntity target) {
        BoltKind kind = variant().bolt();
        EnemyBolt bolt = new EnemyBolt(level(), this, kind, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        double dx = target.getX() - bolt.getX();
        double dy = target.getY(0.5) - bolt.getY();
        double dz = target.getZ() - bolt.getZ();
        bolt.shoot(dx, dy, dz, 1.1F, 2.5F);
        level().addFreshEntity(bolt);
        playSound(kind.shootSound(), 1.0F, 0.9F + getRandom().nextFloat() * 0.2F);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof ERMonster || other instanceof ERBoss || super.isAlliedTo(other);
    }

    // ------------------------------------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!this.spawnAnnounced) {
            this.spawnAnnounced = true;
            this.spawnLockTicks = Mth.ceil(timing(EnemyAnim.SPAWN, false) * 20.0F);
            level().broadcastEntityEvent(this, EnemyAnim.SPAWN.eventId());
        }
        if (!isDeadOrDying()) {
            tickAction();
        }
    }

    private void clientTick() {
        if (isDeadOrDying()) {
            for (EnemyAnim anim : EnemyAnim.values()) {
                if (anim != EnemyAnim.DEATH) {
                    state(anim).stop();
                }
            }
            this.deathAnimation.startIfStopped(this.tickCount);
            return;
        }
        this.idleAnimation.startIfStopped(this.tickCount);
        if (this.hurtTime > this.lastHurtTime) {
            startClient(EnemyAnim.HURT);
        }
        this.lastHurtTime = this.hurtTime;
        for (EnemyAnim anim : EnemyAnim.values()) {
            int slot = anim.ordinal();
            if (this.clientTicksLeft[slot] > 0 && --this.clientTicksLeft[slot] == 0) {
                state(anim).stop();
            }
        }
        EnemyVariant variant = variant();
        ParticleOptions particle = variant.particle();
        if (particle != null && getRandom().nextFloat() < variant.particleChance()) {
            level().addParticle(particle, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0.0, 0.02, 0.0);
        }
    }

    /** The client animation state of a slot (WALK has none: it follows the movement). */
    public AnimationState state(EnemyAnim anim) {
        return switch (anim) {
            case IDLE, WALK -> this.idleAnimation;
            case ATTACK1 -> this.attack1Animation;
            case ATTACK2 -> this.attack2Animation;
            case CAST -> this.castAnimation;
            case HURT -> this.hurtAnimation;
            case SPAWN -> this.spawnAnimation;
            case DEATH -> this.deathAnimation;
        };
    }

    private void startClient(EnemyAnim anim) {
        if (anim == EnemyAnim.ATTACK1 || anim == EnemyAnim.ATTACK2 || anim == EnemyAnim.CAST) {
            for (EnemyAnim other : new EnemyAnim[]{EnemyAnim.ATTACK1, EnemyAnim.ATTACK2, EnemyAnim.CAST}) {
                state(other).stop();
                this.clientTicksLeft[other.ordinal()] = 0;
            }
        }
        state(anim).start(this.tickCount);
        this.clientTicksLeft[anim.ordinal()] = Mth.ceil(timing(anim, false) * 20.0F) + 1;
    }

    @Override
    public void handleEntityEvent(byte id) {
        EnemyAnim anim = EnemyAnim.byEvent(id);
        if (anim != null && anim != EnemyAnim.IDLE && anim != EnemyAnim.WALK && anim != EnemyAnim.DEATH) {
            startClient(anim);
            return;
        }
        super.handleEntityEvent(id);
    }

    // ------------------------------------------------------------------------------------------------ death

    /** Ticks the death animation lasts before the entity is removed. */
    public int deathDurationTicks() {
        return Mth.ceil(timing(EnemyAnim.DEATH, false) * 20.0F) + 2;
    }

    /**
     * Plays the death animation (its own counter instead of vanilla's {@code deathTime}, so the body neither tips
     * over nor stays red), then removes the entity with the usual poof.
     */
    @Override
    protected void tickDeath() {
        this.deathTicks++;
        if (this.deathTicks >= deathDurationTicks() && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(Entity.RemovalReason.KILLED);
        }
    }

    public int deathTicks() {
        return this.deathTicks;
    }

    // ------------------------------------------------------------------------------------------- properties

    @Override
    public boolean fireImmune() {
        return super.fireImmune() || variant().fireImmune();
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
    protected SoundEvent getAmbientSound() {
        return variant().sounds()[0];
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return variant().sounds()[1];
    }

    @Override
    protected SoundEvent getDeathSound() {
        return variant().sounds()[2];
    }
}
