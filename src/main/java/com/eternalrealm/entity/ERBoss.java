package com.eternalrealm.entity;

import com.eternalrealm.boss.BossRigData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
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
 * Without scripts it chases the nearest player and attacks with the slam (hit at the animation's impact frame, from
 * as far as its weapon lands). Never despawns, takes no fall, suffocation, drowning or cramming damage and cannot be
 * knocked back or pushed.
 *
 * <p>Bosses are big (about 6 to 10 blocks tall): later forms grow {@link BossRigData#FORM_GROWTH} per form (model and
 * hitbox), they step over obstacles up to two blocks high, turn at the pace of their weight instead of snapping round,
 * and their slams throw up dust and debris proportional to their size. Footsteps, impact shakes and weapon trails
 * are drawn by the client from the animations ({@code client.boss.BossBodyEffects}).</p>
 */
public class ERBoss extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_TELEGRAPHING = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.INT);
    /** Length in ticks of the defeat sequence being played (0: none); see {@link #startDefeat(int, int)}. */
    private static final EntityDataAccessor<Integer> DATA_DEFEAT_LENGTH = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.INT);
    /** Tick of the defeat sequence at which the body starts to dissolve into light. */
    private static final EntityDataAccessor<Integer> DATA_DISSOLVE_TICK = SynchedEntityData.defineId(ERBoss.class, EntityDataSerializers.INT);
    private static final int MAX_PHASE = 9;
    /** Highest phase look the art can have per boss ({@code <id>_p1} .. {@code <id>_p3}). */
    private static final int MAX_LOOK_PHASE = 3;
    /** Ticks the white flash of a transformation lasts on the client. */
    public static final int TRANSFORM_FLASH_TICKS = 24;
    /** Client: ticks the telegraph roar takes to blend in, and to blend out when an action takes over. */
    private static final float ROAR_BLEND_IN_TICKS = 6.0F;
    private static final int ROAR_BLEND_OUT_TICKS = 5;
    /** Client: ticks the idle takes to soften under an action (and to come back after it). */
    private static final float IDLE_BLEND_TICKS = 6.0F;

    public final AnimationState idleAnimation = new AnimationState();
    private final AnimationState[] actionStates = new AnimationState[BossAnimation.values().length];
    private final int[] clientTicksLeft = new int[BossAnimation.values().length];
    /** Client: tick at which each action last started. */
    private final int[] actionStartedAt = new int[BossAnimation.values().length];

    private final BossKind kind;
    /** Yaw the body is turning to at its own pace (NaN: none), see {@link #faceTowards}. */
    private float turnTo = Float.NaN;
    private boolean aiFrozen;
    private BossAnimation playing;
    private int playingTicksLeft;
    private LivingEntity pendingHitTarget;
    private int pendingHitTicks;
    private int deathTicks;
    private String cachedLook;
    private int cachedLookPhase = -1;
    /** Client: tick at which the current phase was received (transformation flash). */
    private int transformedAt = Integer.MIN_VALUE / 2;
    /** Client: tick at which the defeat sequence started. */
    private int defeatStartedAt = Integer.MIN_VALUE / 2;
    /** Client: tick at which the telegraph roar loop started, and at which it began to fade out (-1: it does not). */
    private int roarStartedAt;
    private int roarFadingSince = -1;
    /** Client: tick at which an action started or ended, and whether one plays (the idle softens under actions). */
    private int actionChangedAt = Integer.MIN_VALUE / 2;
    private boolean actionActive;

    public ERBoss(EntityType<? extends ERBoss> type, Level level, BossKind kind) {
        super(type, level);
        this.kind = kind;
        for (int i = 0; i < this.actionStates.length; i++) {
            this.actionStates[i] = new AnimationState();
        }
        this.xpReward = 100;
        setPersistenceRequired();
        // a giant steps over a block or two where a player would have to jump
        setMaxUpStep(Mth.clamp(0.6F + 0.12F * kind.height(), 1.0F, 2.0F));
        this.moveControl = new BossMoveControl(this, turnRate());
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
     * Look (texture and parts) the client uses: in phase {@code n} (from 1) the boss's form look {@code <id>_p<n>} if
     * the art has one (the highest available up to {@code n}, at most 3), otherwise the boss id. The final boss has
     * {@code final_p1}..{@code final_p3}.
     */
    public String lookKey() {
        int phase = getPhase();
        if (phase != this.cachedLookPhase || this.cachedLook == null) {
            this.cachedLookPhase = phase;
            this.cachedLook = lookFor(this.kind, phase);
        }
        return this.cachedLook;
    }

    /** The look of {@code kind} in {@code phase} (see {@link #lookKey()}). */
    public static String lookFor(BossKind kind, int phase) {
        java.util.List<String> looks = RigData.variants(kind.rig().id());
        for (int form = Math.min(MAX_LOOK_PHASE, phase); form >= 1; form--) {
            String candidate = kind.id() + "_p" + form;
            if (looks.contains(candidate)) {
                return candidate;
            }
        }
        return kind.id();
    }

    /** Model scale of the current look; every later form is a little bigger. */
    public float modelScale() {
        return RigData.scale(this.kind.rig().id(), lookKey()) * formGrowth();
    }

    /** Size factor of the current form against the first one (model and hitbox). */
    public float formGrowth() {
        return 1.0F + (float) BossRigData.FORM_GROWTH * Math.min(MAX_LOOK_PHASE, getPhase());
    }

    /** The hitbox grows with the form, like the model. */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(formGrowth());
    }

    /** Degrees per tick the body turns: heavy bodies turn slowly. */
    public float turnRate() {
        return switch (this.kind.rig()) {
            case KING, QUEEN -> 14.0F;
            case DRAGON -> 10.0F;
            case TREANT, COLOSSUS -> 8.0F;
        };
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
        return Math.max(1, Mth.ceil(seconds * 20.0F - 0.001F));
    }

    /** Ticks from the start of an action to its hit frame (boss actions are timed on whole ticks). */
    public int impactTicks(BossAnimation animation) {
        return Math.max(1, Math.round(RigData.impact(this.kind.rig().id(), lookKey(), animation.ordinal()) * 20.0F));
    }

    /**
     * Client: ticks since {@code animation} started, while it plays (-1 otherwise): the client times its own impact
     * effects (shakes, dust) on the animation's impact frame.
     */
    public int actionTicks(BossAnimation animation) {
        if (!animation.isAction() || !this.actionStates[animation.ordinal()].isStarted()) {
            return -1;
        }
        return this.tickCount - this.actionStartedAt[animation.ordinal()];
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

    /** Phase index (0-based). Bosses change parts and texture with it (phases 1, 2, 3) and grow a little. */
    public void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, Mth.clamp(phase, 0, MAX_PHASE));
        refreshDimensions();
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    /**
     * Starts the defeat sequence (server): the boss stops for good, becomes invulnerable, plays and holds its death
     * animation, and the clients flash and dissolve it ({@link #dissolve(float)}) from {@code dissolveTick} until
     * {@code lengthTicks}. The fight removes the body at the end.
     */
    public void startDefeat(int lengthTicks, int dissolveTick) {
        setAiFrozen(true);
        setTelegraphing(false);
        setShielded(false);
        setInvulnerable(true);
        setTarget(null);
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.entityData.set(DATA_DISSOLVE_TICK, Mth.clamp(dissolveTick, 0, lengthTicks));
        this.entityData.set(DATA_DEFEAT_LENGTH, Math.max(1, lengthTicks));
        playAnimation(BossAnimation.DEATH);
    }

    /** Whether the defeat sequence plays. */
    public boolean isDefeatPlaying() {
        return this.entityData.get(DATA_DEFEAT_LENGTH) > 0;
    }

    /** Client: 0..1 through the defeat sequence (0 when none). */
    public float defeatProgress(float partialTick) {
        int length = this.entityData.get(DATA_DEFEAT_LENGTH);
        if (length <= 0) {
            return 0.0F;
        }
        return Mth.clamp((this.tickCount - this.defeatStartedAt + partialTick) / length, 0.0F, 1.0F);
    }

    /** Client: 0 until the body starts to dissolve, then rising to 1 when it vanishes. */
    public float dissolve(float partialTick) {
        int length = this.entityData.get(DATA_DEFEAT_LENGTH);
        if (length <= 0) {
            return 0.0F;
        }
        int start = this.entityData.get(DATA_DISSOLVE_TICK);
        float elapsed = this.tickCount - this.defeatStartedAt + partialTick;
        return Mth.clamp((elapsed - start) / Math.max(1, length - start), 0.0F, 1.0F);
    }

    /** Client: weight of the telegraph roar loop (it blends in and out, see {@link #clientTick()}). */
    public float roarWeight(float partialTick) {
        float now = this.tickCount + partialTick;
        float in = Mth.clamp((now - this.roarStartedAt) / ROAR_BLEND_IN_TICKS, 0.0F, 1.0F);
        float out = this.roarFadingSince < 0 ? 1.0F
                : 1.0F - Mth.clamp((now - this.roarFadingSince) / ROAR_BLEND_OUT_TICKS, 0.0F, 1.0F);
        float weight = Math.min(in, out);
        return weight * weight * (3.0F - 2.0F * weight);
    }

    /** Client: weight of the idle loop: softened while an action plays, so breathing and looking around go on quietly. */
    public float idleWeight(float partialTick) {
        float blend = Mth.clamp((this.tickCount + partialTick - this.actionChangedAt) / IDLE_BLEND_TICKS, 0.0F, 1.0F);
        float acting = this.actionActive ? blend : 1.0F - blend;
        return 1.0F - 0.6F * acting;
    }

    /** Client: 1 right after a transformation, fading to 0 over {@link #TRANSFORM_FLASH_TICKS}. */
    public float transformFlash(float partialTick) {
        float elapsed = this.tickCount - this.transformedAt + partialTick;
        return elapsed < 0.0F || elapsed > TRANSFORM_FLASH_TICKS ? 0.0F : 1.0F - elapsed / TRANSFORM_FLASH_TICKS;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide()) {
            return;
        }
        if (DATA_PHASE.equals(key)) {
            refreshDimensions();
        }
        if (DATA_PHASE.equals(key) && getPhase() > 0 && this.tickCount > 1) {
            this.transformedAt = this.tickCount;
        } else if (DATA_DEFEAT_LENGTH.equals(key) && this.entityData.get(DATA_DEFEAT_LENGTH) > 0) {
            this.defeatStartedAt = this.tickCount;
        }
    }

    /** While frozen the boss neither moves nor attacks on its own (scripted abilities). */
    public void setAiFrozen(boolean frozen) {
        this.aiFrozen = frozen;
        if (frozen) {
            getNavigation().stop();
            this.pendingHitTarget = null;
        } else {
            this.turnTo = Float.NaN; // its own movement steers again
        }
    }

    public boolean isAiFrozen() {
        return this.aiFrozen;
    }

    /**
     * Turns the body and head towards a point at the boss's own pace ({@link #turnRate()} degrees per tick: a giant
     * does not snap round); the head looks there at once.
     */
    public void faceTowards(double x, double z) {
        this.turnTo = yawTowards(x, z);
        setYHeadRot(this.turnTo);
    }

    /** Turns the body and head towards a point instantly (a charge starts facing its strip). */
    public void faceTowardsNow(double x, double z) {
        float yaw = yawTowards(x, z);
        this.turnTo = Float.NaN;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private float yawTowards(double x, double z) {
        if (Math.abs(x - getX()) < 1.0E-4 && Math.abs(z - getZ()) < 1.0E-4) {
            return getYRot();
        }
        return (float) (Mth.atan2(z - getZ(), x - getX()) * (180.0 / Math.PI)) - 90.0F;
    }

    private void tickTurn() {
        if (Float.isNaN(this.turnTo)) {
            return;
        }
        float yaw = Mth.approachDegrees(getYRot(), this.turnTo, turnRate());
        setYRot(yaw);
        setYBodyRot(yaw);
        if (Math.abs(Mth.wrapDegrees(this.turnTo - yaw)) < 0.5F) {
            this.turnTo = Float.NaN;
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TELEGRAPHING, false);
        this.entityData.define(DATA_SHIELDED, false);
        this.entityData.define(DATA_ENRAGED, false);
        this.entityData.define(DATA_PHASE, 0);
        this.entityData.define(DATA_DEFEAT_LENGTH, 0);
        this.entityData.define(DATA_DISSOLVE_TICK, 0);
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

    /**
     * Distance from the centre at which the slam connects (plus half the target's width): where the blade, fists, claws
     * or jaws land in the slam animation, at least a block beyond the body.
     */
    public double meleeReach() {
        return Math.max(getBbWidth() * 0.5 + 1.0, RigData.reach(this.kind.rig().id(), lookKey()) * modelScale());
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
        if (level().isClientSide || this.playing != null || this.aiFrozen || isDeadOrDying() || isDefeatPlaying()) {
            return false;
        }
        playAnimation(BossAnimation.MELEE_SLAM);
        faceTowards(target.getX(), target.getZ()); // turns into the swing during the wind-up
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
        tickTurn();
        if (this.playing != null && --this.playingTicksLeft <= 0) {
            this.playing = isDefeatPlaying() ? BossAnimation.DEATH : null; // a defeated boss stays down
            this.playingTicksLeft = isDefeatPlaying() ? Integer.MAX_VALUE / 2 : 0;
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

    /** Dust, debris and a crack of light where the slam lands, as big as the boss. */
    private void impactEffects() {
        if (level() instanceof ServerLevel server) {
            Vec3 look = Vec3.directionFromRotation(0.0F, getYRot());
            double reach = Math.max(getBbWidth() * 0.5 + 0.5, meleeReach() * 0.85);
            double x = getX() + look.x * reach;
            double z = getZ() + look.z * reach;
            double size = Math.max(1.0, getBbWidth() * 0.35);
            server.sendParticles(ParticleTypes.EXPLOSION, x, getY() + 0.2, z, 1 + (int) size, 0.3 * size, 0.1,
                    0.3 * size, 0.0);
            server.sendParticles(ParticleTypes.CLOUD, x, getY() + 0.1, z, (int) (10 * size), 0.6 * size, 0.05,
                    0.6 * size, 0.06);
            BlockState floor = level().getBlockState(BlockPos.containing(x, getY() - 0.5, z));
            if (floor.getRenderShape() != RenderShape.INVISIBLE) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), x, getY() + 0.2, z,
                        (int) (18 * size), 0.5 * size, 0.15, 0.5 * size, 0.25);
            }
        }
        playSound(this.kind.sounds()[4], 1.6F + 0.2F * getBbWidth(), 0.7F + getRandom().nextFloat() * 0.2F);
    }

    private void clientTick() {
        if (isDefeatPlaying()) {
            float progress = defeatProgress(0.0F);
            float dissolve = dissolve(0.0F);
            for (int i = particles(0.35F + 0.6F * progress); i > 0; i--) {
                level().addParticle(ParticleTypes.END_ROD, getRandomX(0.9), getRandomY(), getRandomZ(0.9), 0.0,
                        0.04 + 0.08 * dissolve, 0.0);
            }
            for (int i = dissolve > 0.0F ? particles(0.8F) : 0; i > 0; i--) {
                level().addParticle(this.kind.themeParticle(), getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0.0,
                        0.06, 0.0);
            }
            return;
        }
        int phase = getPhase();
        for (int i = phase > 0 ? particles(0.12F * phase) : 0; i > 0; i--) {
            double angle = getRandom().nextDouble() * Math.PI * 2.0;
            double radius = getBbWidth() * 0.6 + 0.4;
            level().addParticle(this.kind.themeParticle(), getX() + Math.cos(angle) * radius, getY() + 0.1,
                    getZ() + Math.sin(angle) * radius, 0.0, 0.03 + 0.02 * phase, 0.0);
        }
        for (int i = phase >= 2 ? particles(0.15F * (phase - 1)) : 0; i > 0; i--) {
            level().addParticle(ParticleTypes.ENCHANT, getRandomX(0.7), getY() + getBbHeight() * getRandom().nextFloat(),
                    getRandomZ(0.7), 0.0, 0.2, 0.0);
        }
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
        boolean acting = false;
        for (BossAnimation animation : BossAnimation.values()) {
            int slot = animation.ordinal();
            if (this.clientTicksLeft[slot] > 0 && --this.clientTicksLeft[slot] == 0 && animation != BossAnimation.ROAR) {
                this.actionStates[slot].stop();
            }
            if (animation.isAction() && animation != BossAnimation.ROAR && this.actionStates[slot].isStarted()) {
                acting = true;
            }
        }
        if (acting != this.actionActive) {
            this.actionActive = acting;
            this.actionChangedAt = this.tickCount;
        }
        // the telegraph roar blends in, and blends out under the action that follows it instead of snapping away
        AnimationState roar = this.actionStates[BossAnimation.ROAR.ordinal()];
        boolean roaring = (isTelegraphing() && !acting) || this.clientTicksLeft[BossAnimation.ROAR.ordinal()] > 0;
        if (roaring) {
            if (!roar.isStarted()) {
                roar.start(this.tickCount);
                this.roarStartedAt = this.tickCount;
            }
            this.roarFadingSince = -1;
        } else if (roar.isStarted()) {
            if (this.roarFadingSince < 0) {
                this.roarFadingSince = this.tickCount;
            } else if (this.tickCount - this.roarFadingSince >= ROAR_BLEND_OUT_TICKS) {
                roar.stop();
                this.roarFadingSince = -1;
            }
        }
        ParticleOptions theme = this.kind.themeParticle();
        for (int i = isTelegraphing() ? particles(0.7F) : 0; i > 0; i--) {
            level().addParticle(theme, getRandomX(1.2), getRandomY(), getRandomZ(1.2), 0.0, 0.05, 0.0);
        }
        for (int i = isEnraged() ? particles(0.25F) : 0; i > 0; i--) {
            level().addParticle(ParticleTypes.FLAME, getRandomX(0.9), getY() + getBbHeight() * (0.6 + 0.4 * getRandom().nextFloat()),
                    getRandomZ(0.9), 0.0, 0.03, 0.0);
        }
        for (int i = isShielded() ? particles(0.4F) : 0; i > 0; i--) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(1.3), getRandomY(), getRandomZ(1.3), 0.0, 0.0, 0.0);
        }
    }

    /** Client: how many of a particle to spawn this tick for {@code perTick} around a 3-block-wide body (more for bigger). */
    private int particles(float perTick) {
        float amount = perTick * Math.max(1.0F, getBbWidth() / 3.0F);
        int count = (int) amount;
        return count + (getRandom().nextFloat() < amount - count ? 1 : 0);
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
                this.clientTicksLeft[other.ordinal()] = 0;
                if (other != BossAnimation.ROAR) { // the roar is not cut: it blends out under the new action
                    this.actionStates[other.ordinal()].stop();
                }
            }
        }
        if (animation == BossAnimation.ROAR) {
            AnimationState roar = this.actionStates[BossAnimation.ROAR.ordinal()];
            if (!roar.isStarted()) {
                roar.start(this.tickCount);
                this.roarStartedAt = this.tickCount;
            }
            this.roarFadingSince = -1;
            this.clientTicksLeft[animation.ordinal()] = durationTicks(animation) + 1;
        } else if (animation.isAction()) {
            this.actionStates[animation.ordinal()].start(this.tickCount);
            this.actionStartedAt[animation.ordinal()] = this.tickCount;
            // the death animation of a defeat holds its last pose until the body is removed
            this.clientTicksLeft[animation.ordinal()] = animation == BossAnimation.DEATH ? Integer.MAX_VALUE / 2
                    : durationTicks(animation) + 1;
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

    /** Wings, tails, swords and crowns reach well beyond the hitbox. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double side = getBbWidth() * 1.6 + 2.0;
        return getBoundingBox().inflate(side, getBbHeight() * 0.3 + 1.0, side);
    }

    /** The name floats above crowns and horns, which rise above the hitbox. */
    @Override
    public float getNameTagOffsetY() {
        return getBbHeight() * 1.15F + 0.5F;
    }

    /** Footsteps follow the walk cycle on the client (heavy steps with dust); the per-block step sound is silent. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
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
