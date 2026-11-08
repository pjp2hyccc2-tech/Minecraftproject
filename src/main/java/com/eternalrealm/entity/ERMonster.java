package com.eternalrealm.entity;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.combat.forge.TelegraphFx;
import com.eternalrealm.combat.forge.Telegraphs;
import com.eternalrealm.monster.SpecialMove;
import com.eternalrealm.monster.SpecialMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
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
 *
 * <p>Strong monsters ({@link #setSpecialMoves}, decided by {@link SpecialMoves}) also use telegraphed special moves: a
 * ground marker ({@link Telegraphs}) shows where the hit lands while the attack animation winds up in slow motion
 * (entity events from {@link #SPECIAL_EVENT_BASE}); the hit lands when the marker is full, on whoever is inside it.</p>
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

    /** Entity events {@code SPECIAL_EVENT_BASE + slot * 16 + bucket}: wind-up of ATTACK1/ATTACK2/CAST (slot 0..2). */
    public static final int SPECIAL_EVENT_BASE = 72;
    private static final int DASH_TICKS = 4;

    private final int[] clientTicksLeft = new int[EnemyAnim.values().length];
    /** Playback speed of each animation slot (slowed during a special move's wind-up). */
    private final float[] clientSpeed = new float[EnemyAnim.values().length];
    private final int[] clientSlowTicks = new int[EnemyAnim.values().length];
    private int lastHurtTime;

    private int specialMoves;
    private SpecialMove special;
    private Telegraph specialTelegraph;
    private int specialMarker = -1;
    private float specialYaw;
    private int specialReadyTick;
    private int specialIndex;
    private boolean specialHitting;
    private Vec3 dashStep = Vec3.ZERO;
    private int dashTicks;

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
        Arrays.fill(this.clientSpeed, 1.0F);
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
        tag.putInt("SpecialMoves", this.specialMoves);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("Variant");
        if (variantsOf(rig()).contains(id)) {
            setVariant(id);
        }
        this.specialMoves = Mth.clamp(tag.getInt("SpecialMoves"), 0, 2);
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
        if (this.dashTicks > 0) {
            this.dashTicks--;
            move(MoverType.SELF, this.dashStep);
        }
        if (this.action == null) {
            return;
        }
        this.actionTick++;
        LivingEntity target = this.actionTarget;
        if (this.special != null && !this.actionImpactDone) {
            holdSpecialFacing();
            if (this.actionTick % 3 == 0 && level() instanceof ServerLevel server) {
                server.sendParticles(TelegraphFx.secondary(this.specialTelegraph.style()), getX(),
                        getY() + getBbHeight() * 0.6, getZ(), 2, getBbWidth() * 0.4, getBbHeight() * 0.3,
                        getBbWidth() * 0.4, 0.02);
            }
        } else if (target != null && target.isAlive()) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (!this.actionImpactDone && this.actionTick >= this.actionImpactTick) {
            this.actionImpactDone = true;
            if (this.special != null) {
                executeSpecial();
            } else if (target != null && target.isAlive()) {
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
            this.special = null;
            this.specialTelegraph = null;
        }
    }

    // ------------------------------------------------------------------------------------------ special moves

    /** How many of its body's special moves this monster uses (0 = none, 1 = veteran, 2 = elite). Server side. */
    public void setSpecialMoves(int count) {
        this.specialMoves = Mth.clamp(count, 0, 2);
    }

    public int specialMoveCount() {
        return this.specialMoves;
    }

    /** The special moves this monster uses, signature move first. */
    public List<SpecialMove> specialMoves() {
        return SpecialMoves.movesOf(rig().id(), getVariant(), this.specialMoves);
    }

    /** The special move winding up or hitting right now, or {@code null}. */
    public SpecialMove currentSpecialMove() {
        return this.special;
    }

    /** The ground marker of the current special move, or {@code null}. */
    public Telegraph currentSpecialTelegraph() {
        return this.specialTelegraph;
    }

    /** No special move before {@code ticks} from now (a fight just started). */
    public void delaySpecialMoves(int ticks) {
        this.specialReadyTick = Math.max(this.specialReadyTick, this.tickCount + ticks);
    }

    /** Damage multiplier of the hit being dealt right now: a special move's while it lands, otherwise 1. */
    public double hitMultiplier() {
        return this.specialHitting && this.special != null ? this.special.damageMultiplier() : 1.0;
    }

    /** Client: playback speed of an animation slot (below 1 while a special move winds up). */
    public float animationSpeed(EnemyAnim anim) {
        return this.clientSpeed[anim.ordinal()];
    }

    /**
     * Uses a special move against {@code target} if this monster has one that is ready and fits the distance.
     * Called by the combat goal every tick it is free to act.
     */
    public boolean trySpecialMove(LivingEntity target) {
        if (this.specialMoves <= 0 || level().isClientSide || isActing() || isDeadOrDying()
                || this.tickCount < this.specialReadyTick || !(level() instanceof ServerLevel server)
                || !specialMovesEnabled()) {
            return false;
        }
        List<SpecialMove> moves = specialMoves();
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        for (int i = 0; i < moves.size(); i++) {
            SpecialMove move = moves.get((this.specialIndex + i) % moves.size());
            if (move.inRange(distance)) {
                this.specialIndex = (this.specialIndex + i + 1) % moves.size();
                startSpecial(server, move, target);
                return true;
            }
        }
        return false;
    }

    /** Starts {@code move} against {@code target} now, whatever the range and cooldown (tests, scripted scenes). */
    public boolean startSpecialMove(SpecialMove move, LivingEntity target) {
        if (level().isClientSide || isActing() || isDeadOrDying() || !(level() instanceof ServerLevel server)) {
            return false;
        }
        startSpecial(server, move, target);
        return true;
    }

    private static boolean specialMovesEnabled() {
        try {
            return com.eternalrealm.config.ERServerConfig.SPECIAL_MOVES.get();
        } catch (IllegalStateException e) {
            return true; // config not loaded (tests)
        }
    }

    private void startSpecial(ServerLevel server, SpecialMove move, LivingEntity target) {
        Telegraph telegraph = move.telegraph(SpecialMoves.style(getVariant()), getX(), groundY(getX(), getY(), getZ()),
                getZ(), target.getX(), groundY(target.getX(), target.getY(), target.getZ()), target.getZ());
        EnemyAnim anim = switch (move.swing()) {
            case ATTACK1 -> EnemyAnim.ATTACK1;
            case ATTACK2 -> EnemyAnim.ATTACK2;
            case CAST -> EnemyAnim.CAST;
        };
        this.special = move;
        this.specialTelegraph = telegraph;
        this.specialMarker = Telegraphs.show(server, telegraph);
        this.specialYaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - getX()), target.getZ() - getZ()));
        this.action = anim;
        this.actionTarget = target;
        this.actionTick = 0;
        this.actionImpactDone = false;
        this.actionImpactTick = move.windupTicks();
        this.actionEndTick = move.windupTicks()
                + Math.max(DASH_TICKS + 1, Mth.ceil((timing(anim, false) - timing(anim, true)) * 20.0F));
        getNavigation().stop();
        holdSpecialFacing();
        level().broadcastEntityEvent(this, specialEvent(anim, move.windupTicks()));
        TelegraphFx.warnSound(server, telegraph, 0.9F);
        this.specialReadyTick = this.tickCount + move.windupTicks() + SpecialMoves.cooldownTicks(this.specialMoves)
                + getRandom().nextInt(40);
    }

    /** Entity event of a wind-up: which attack animation, and its length in 2-tick steps from 10 ticks. */
    static byte specialEvent(EnemyAnim anim, int windupTicks) {
        int slot = anim == EnemyAnim.ATTACK1 ? 0 : anim == EnemyAnim.ATTACK2 ? 1 : 2;
        int bucket = Mth.clamp(Math.round((windupTicks - 10) / 2.0F), 0, 15);
        return (byte) (SPECIAL_EVENT_BASE + slot * 16 + bucket);
    }

    private void holdSpecialFacing() {
        setYRot(this.specialYaw);
        this.yBodyRot = this.specialYaw;
        this.yHeadRot = this.specialYaw;
    }

    /** Feet height of the ground under a position (fliers hover above it): the first solid block within 12 below. */
    private double groundY(double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y + 0.01), Mth.floor(z));
        for (int i = 0; i < 12; i++) {
            pos.move(0, -1, 0);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return pos.getY() + 1.0;
            }
        }
        return y;
    }

    private void executeSpecial() {
        SpecialMove move = this.special;
        Telegraph telegraph = this.specialTelegraph;
        if (!(level() instanceof ServerLevel server) || move == null || telegraph == null) {
            return;
        }
        if (this.specialMarker >= 0) {
            Telegraphs.fire(server, this.specialMarker);
            this.specialMarker = -1;
        }
        double reach = telegraph.boundingRadius() + 1.0;
        AABB area = new AABB(telegraph.x() - reach, telegraph.y() - 3.0, telegraph.z() - reach, telegraph.x() + reach,
                telegraph.y() + 4.0, telegraph.z() + reach);
        List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class, area,
                entity -> canSpecialHit(entity) && telegraph.contains(entity.getX(), entity.getY(), entity.getZ()));
        float damage = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * move.damageMultiplier());
        this.specialHitting = true;
        try {
            for (LivingEntity victim : victims) {
                if (victim.hurt(damageSources().mobAttack(this), damage)) {
                    afflict(victim, telegraph.style());
                }
            }
        } finally {
            this.specialHitting = false;
        }
        TelegraphFx.burst(server, telegraph);
        TelegraphFx.impactSound(server, telegraph, 1.0F);
        if (move.dashes()) {
            double toX;
            double toZ;
            if (move == SpecialMove.POUNCE) {
                toX = telegraph.x();
                toZ = telegraph.z();
            } else {
                double radians = Math.toRadians(telegraph.yaw());
                double length = Math.max(0.0, telegraph.a() - 1.0);
                toX = telegraph.x() - Math.sin(radians) * length;
                toZ = telegraph.z() + Math.cos(radians) * length;
            }
            this.dashStep = new Vec3((toX - getX()) / DASH_TICKS, 0.0, (toZ - getZ()) / DASH_TICKS);
            this.dashTicks = DASH_TICKS;
        }
    }

    /** Players (not spectators or creative), the monster's target, and anything that is not another monster. */
    private boolean canSpecialHit(LivingEntity entity) {
        if (entity == this || !entity.isAlive() || isAlliedTo(entity) || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Player player) {
            return !player.isSpectator() && !player.isCreative();
        }
        return entity == getTarget() || !(entity instanceof Enemy);
    }

    private static void afflict(LivingEntity victim, Telegraph.Style style) {
        switch (style) {
            case FIRE -> victim.setSecondsOnFire(3);
            case FROST -> {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 80));
            }
            case DARK -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
            case MAGIC -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
            default -> {
                // physical: the hit and its knockback
            }
        }
    }

    /** Calls the current special move off (death, removal): its marker disappears. */
    private void cancelSpecial() {
        if (this.specialMarker >= 0 && level() instanceof ServerLevel server) {
            Telegraphs.cancel(server, this.specialMarker);
        }
        this.specialMarker = -1;
        this.special = null;
        this.specialTelegraph = null;
        this.dashTicks = 0;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) {
            cancelSpecial();
        }
        super.remove(reason);
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
        } else if (this.specialMarker >= 0) {
            cancelSpecial();
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
            if (this.clientSlowTicks[slot] > 0 && --this.clientSlowTicks[slot] == 0) {
                this.clientSpeed[slot] = 1.0F;
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
                this.clientSpeed[other.ordinal()] = 1.0F;
                this.clientSlowTicks[other.ordinal()] = 0;
            }
        }
        state(anim).start(this.tickCount);
        this.clientTicksLeft[anim.ordinal()] = Mth.ceil(timing(anim, false) * 20.0F) + 1;
    }

    /** A special move winds up: the attack plays slowed down so that its hit frame lands after {@code windup}. */
    private void startClientWindup(EnemyAnim anim, int windup) {
        startClient(anim);
        int slot = anim.ordinal();
        float impactTicks = timing(anim, true) * 20.0F;
        this.clientSpeed[slot] = Mth.clamp(impactTicks / windup, 0.05F, 1.0F);
        this.clientSlowTicks[slot] = windup;
        this.clientTicksLeft[slot] = windup + Mth.ceil((timing(anim, false) - timing(anim, true)) * 20.0F) + 1;
    }

    @Override
    public void handleEntityEvent(byte id) {
        int windup = id - SPECIAL_EVENT_BASE;
        if (windup >= 0 && windup < 48) {
            EnemyAnim anim = windup < 16 ? EnemyAnim.ATTACK1 : windup < 32 ? EnemyAnim.ATTACK2 : EnemyAnim.CAST;
            startClientWindup(anim, 10 + 2 * (windup % 16));
            return;
        }
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
