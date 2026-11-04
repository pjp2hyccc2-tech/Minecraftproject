package jp.satsujinki.entity;

import jp.satsujinki.block.GuillotineBlock;
import jp.satsujinki.game.ContestantType;
import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.game.Lines;
import jp.satsujinki.game.Names;
import jp.satsujinki.game.TrapRegistry;
import jp.satsujinki.item.HostHandItem;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.registry.ModEntities;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * A death game contestant. Fear rises when the host approaches, when traps run nearby and when other
 * contestants die; it drives the state machine (calm → anxious → panic → cower/beg, fighters fight back).
 */
public class ContestantEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FEAR = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Component> DATA_SPEECH = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Integer> DATA_SPEECH_START = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPEECH_UNTIL = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NUMBER = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_READY = SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int ST_CALM = 0, ST_ANXIOUS = 1, ST_PANIC = 2, ST_COWER = 3, ST_FIGHT = 4, ST_GRABBED = 5, ST_LOCKED = 6, ST_THROWN = 7;

    public enum Voice { SCREAM, PANIC, BEG, HURT, FIGHT }

    // ---- server-side state
    private float fear;
    @Nullable private UUID holderId;
    @Nullable private UUID throwerId;
    private long thrownAt = -100000L;
    private int speechCooldown = 30;
    private int cowerTicks;
    @Nullable private Vec3 threatPos;
    private int threatTicks;
    private int trapLineCooldown;
    @Nullable private BlockPos lockPos;
    private float lockYaw;
    @Nullable private UUID roundId;
    /** Set by GoreHandler before a trap kill so the death event does not spawn generic remains. */
    public boolean goreHandled;
    @Nullable public KillStyle forcedStyle;

    public ContestantEntity(EntityType<? extends ContestantEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Nullable
    public static ContestantEntity spawnAt(ServerLevel level, Vec3 pos, @Nullable ContestantType type) {
        ContestantEntity c = ModEntities.CONTESTANT.get().create(level);
        if (c == null) return null;
        c.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0F, 0.0F);
        c.setup(type != null ? type : ContestantType.random(level.random), level.random);
        level.addFreshEntity(c);
        GoreHandler.fx(level, pos.x, pos.y + 1.0D, pos.z, GoreFxPacket.SPAWN_POOF, 1.0F);
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.SPAWN_POOF.get(), SoundSource.NEUTRAL, 0.8F, 0.9F + level.random.nextFloat() * 0.2F);
        return c;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TYPE, 0);
        this.entityData.define(DATA_SKIN, 0);
        this.entityData.define(DATA_FEAR, 0.0F);
        this.entityData.define(DATA_SPEECH, Component.empty());
        this.entityData.define(DATA_SPEECH_START, 0);
        this.entityData.define(DATA_SPEECH_UNTIL, 0);
        this.entityData.define(DATA_STATE, ST_CALM);
        this.entityData.define(DATA_NUMBER, 0);
        this.entityData.define(DATA_READY, false);
    }

    /** Server: picks name, number, skin and stats. */
    public void setup(ContestantType type, RandomSource r) {
        this.entityData.set(DATA_TYPE, type.ordinal());
        this.entityData.set(DATA_SKIN, type.ordinal() * ContestantType.SKINS_PER_TYPE + r.nextInt(ContestantType.SKINS_PER_TYPE));
        int number = 1 + r.nextInt(999);
        this.entityData.set(DATA_NUMBER, number);
        this.setCustomName(Component.literal(String.format("No.%03d %s", number, Names.random(type.female, r))));
        this.setCustomNameVisible(true);
        setBase(Attributes.MAX_HEALTH, type.health);
        setBase(Attributes.MOVEMENT_SPEED, type.speed);
        setBase(Attributes.ATTACK_DAMAGE, type.personality == ContestantType.Personality.FIGHTER ? 3.5D : 1.0D);
        this.setHealth(type.health);
        this.fear = type.personality.baseFear;
        this.entityData.set(DATA_FEAR, this.fear);
        this.entityData.set(DATA_READY, true);
        this.speechCooldown = 20 + r.nextInt(60);
        if (!level().isClientSide) say(Lines.intro(type, r), 80);
    }

    private void setBase(net.minecraft.world.entity.ai.attributes.Attribute attr, double value) {
        AttributeInstance inst = this.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HeldGoal(this));
        this.goalSelector.addGoal(2, new CowerGoal(this));
        this.goalSelector.addGoal(3, new FighterAttackGoal(this));
        this.goalSelector.addGoal(4, new PanicFleeGoal(this));
        this.goalSelector.addGoal(5, new AvoidHostGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new FighterRevengeGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> this.isFighter() && this.fear > 0.7F && !this.isHeldOrLocked()));
    }

    // ------------------------------------------------------------------ accessors

    public ContestantType getContestantType() {
        return ContestantType.byId(this.entityData.get(DATA_TYPE));
    }

    public int getSkin() {
        return Mth.clamp(this.entityData.get(DATA_SKIN), 0, ContestantType.skinCount() - 1);
    }

    public int getNumber() {
        return this.entityData.get(DATA_NUMBER);
    }

    public float getFear() {
        return level().isClientSide ? this.entityData.get(DATA_FEAR) : this.fear;
    }

    public void addFear(float amount) {
        this.fear = Mth.clamp(this.fear + amount * getContestantType().personality.fearGain, 0.0F, 1.0F);
        this.entityData.set(DATA_FEAR, this.fear);
    }

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    public boolean isFemale() {
        return getContestantType().female;
    }

    public boolean isFighter() {
        return getContestantType().personality == ContestantType.Personality.FIGHTER;
    }

    public boolean isHeld() {
        return this.holderId != null;
    }

    public boolean isLocked() {
        return this.lockPos != null;
    }

    public boolean isHeldOrLocked() {
        return this.holderId != null || this.lockPos != null || isThrownFlying();
    }

    public boolean isThrownFlying() {
        return level().getGameTime() - this.thrownAt < 30L && !onGround();
    }

    public boolean wasThrownRecently() {
        return level().getGameTime() - this.thrownAt < 200L;
    }

    @Nullable
    public UUID getThrowerId() {
        return this.throwerId;
    }

    @Nullable
    public UUID getRoundId() {
        return this.roundId;
    }

    public void setRoundId(@Nullable UUID id) {
        this.roundId = id;
    }

    @Nullable
    public Vec3 getThreatPos() {
        return this.threatPos;
    }

    public Component getSpeech() {
        return this.entityData.get(DATA_SPEECH);
    }

    public int getSpeechStart() {
        return this.entityData.get(DATA_SPEECH_START);
    }

    public int getSpeechUntil() {
        return this.entityData.get(DATA_SPEECH_UNTIL);
    }

    // ------------------------------------------------------------------ speech & voice

    public void say(Component line, int ticks) {
        long t = level().getGameTime();
        this.entityData.set(DATA_SPEECH, line);
        this.entityData.set(DATA_SPEECH_START, (int) t);
        this.entityData.set(DATA_SPEECH_UNTIL, (int) (t + ticks));
    }

    public void voice(Voice v) {
        boolean f = isFemale();
        SoundEvent s = switch (v) {
            case SCREAM -> f ? ModSounds.SCREAM_FEMALE.get() : ModSounds.SCREAM_MALE.get();
            case PANIC -> f ? ModSounds.PANIC_FEMALE.get() : ModSounds.PANIC_MALE.get();
            case BEG -> f ? ModSounds.BEG_FEMALE.get() : ModSounds.BEG_MALE.get();
            case HURT -> f ? ModSounds.HURT_FEMALE.get() : ModSounds.HURT_MALE.get();
            case FIGHT -> ModSounds.FIGHT.get();
        };
        float pitch = getContestantType().voicePitch * (0.94F + (getSkin() % 3) * 0.04F) + (this.random.nextFloat() - 0.5F) * 0.08F;
        level().playSound(null, getX(), getY() + 1.5D, getZ(), s, SoundSource.NEUTRAL, v == Voice.BEG ? 0.8F : 1.2F, pitch);
    }

    /** Immediate scream (traps call this when they catch the contestant). */
    public void screamNow() {
        say(Lines.random(Lines.Cat.PANIC, this.random), 40);
        voice(Voice.SCREAM);
        this.speechCooldown = 45;
    }

    // ------------------------------------------------------------------ host hand / guillotine

    public void grab(Player p) {
        this.holderId = p.getUUID();
        this.lockPos = null;
        this.getNavigation().stop();
        this.setTarget(null);
        this.fear = 1.0F;
        this.entityData.set(DATA_FEAR, 1.0F);
        this.entityData.set(DATA_STATE, ST_GRABBED);
        say(Lines.random(Lines.Cat.GRABBED, this.random), 60);
        voice(Voice.SCREAM);
        this.speechCooldown = 50;
    }

    public void release(@Nullable Player p, float strength) {
        this.holderId = null;
        this.setNoGravity(false);
        if (p != null && strength > 0.0F) {
            Vec3 look = p.getLookAngle();
            this.setDeltaMovement(look.x * strength, look.y * strength + 0.3D, look.z * strength);
            this.hasImpulse = true;
            this.throwerId = p.getUUID();
            this.thrownAt = level().getGameTime();
            this.fallDistance = 0.0F;
            say(Lines.random(Lines.Cat.THROWN, this.random), 40);
            voice(Voice.SCREAM);
            this.speechCooldown = 40;
        } else if (p != null) {
            this.setDeltaMovement(Vec3.ZERO);
            this.throwerId = p.getUUID();
            this.thrownAt = level().getGameTime();
        }
    }

    public void lockInto(BlockPos pos, float yaw) {
        this.lockPos = pos.immutable();
        this.lockYaw = yaw;
        this.holderId = null;
        this.getNavigation().stop();
        this.setTarget(null);
        this.entityData.set(DATA_STATE, ST_LOCKED);
        addFear(0.6F);
        say(Lines.random(Lines.Cat.LOCKED, this.random), 70);
        voice(Voice.PANIC);
        this.speechCooldown = 60;
    }

    public void unlock() {
        this.lockPos = null;
        this.setNoGravity(false);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void aiStep() {
        if (!level().isClientSide) serverPreMove();
        super.aiStep();
        if (!level().isClientSide) serverPostMove();
    }

    private void serverPreMove() {
        if (!this.entityData.get(DATA_READY)) setup(ContestantType.random(this.random), this.random);
        if (this.holderId != null) {
            Player p = level().getPlayerByUUID(this.holderId);
            if (p == null || !p.isAlive() || p.isSpectator() || !HostHandItem.isHolding(p, this) || distanceToSqr(p) > 144.0D) {
                if (p != null) HostHandItem.forgetPlayer(p);
                release(null, 0.0F);
            } else {
                // Hold point in front of the host's face. Looking up lifts the victim higher, so it can be
                // raised over a mixer rim or a railing even from the ground.
                Vec3 look = p.getLookAngle();
                Vec3 target = p.getEyePosition().add(look.scale(2.4D)).subtract(0.0D, getBbHeight() * 0.6D - Math.max(0.0D, look.y) * 1.2D, 0.0D);
                Vec3 d = target.subtract(position());
                this.setNoGravity(true);
                this.fallDistance = 0.0F;
                Vec3 step = d.scale(0.5D);
                if (step.lengthSqr() > 0.81D) step = step.normalize().scale(0.9D);
                if (step.lengthSqr() > 1.0E-6D && level().noCollision(this, getBoundingBox().move(step))) {
                    // The host's grip: glide straight toward the hand while the way is clear (never into blocks;
                    // a step is shorter than a full block plus the body width, so walls can't be passed through).
                    this.setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
                    this.setDeltaMovement(Vec3.ZERO);
                } else {
                    // Blocked: slide along whatever is in the way.
                    this.setDeltaMovement(d.scale(0.45D));
                }
                this.getLookControl().setLookAt(p, 60.0F, 60.0F);
                this.hasImpulse = true;
            }
        } else if (this.lockPos != null) {
            if (!(level().getBlockState(this.lockPos).getBlock() instanceof GuillotineBlock)) {
                unlock();
            } else {
                this.setNoGravity(true);
                this.setDeltaMovement(Vec3.ZERO);
                this.setPos(this.lockPos.getX() + 0.5D, this.lockPos.getY() + 0.1875D, this.lockPos.getZ() + 0.5D);
                this.setYRot(this.lockYaw);
                this.yBodyRot = this.lockYaw;
                this.yHeadRot = this.lockYaw;
                this.setXRot(15.0F);
            }
        } else if (this.isNoGravity()) {
            this.setNoGravity(false);
        }
    }

    private void serverPostMove() {
        tickFear();
        updateState();
        tickSpeech();
        if (this.threatTicks > 0 && --this.threatTicks == 0) this.threatPos = null;
    }

    private void tickFear() {
        ContestantType t = getContestantType();
        this.fear += (t.personality.baseFear - this.fear) * 0.002F;
        if (this.tickCount % 10 == 0) {
            Player host = level().getNearestPlayer(this, 7.0D);
            if (host != null && !host.isSpectator()) {
                ItemStack held = host.getMainHandItem();
                boolean menacing = HostHandItem.isHoldingItem(host) || held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem;
                this.fear += (menacing ? 0.05F : 0.012F) * t.personality.fearGain;
                this.threatPos = host.position();
                this.threatTicks = Math.max(this.threatTicks, 40);
            }
            if (TrapRegistry.isNearRunningTrap(level(), blockPosition(), 9)) {
                this.fear += 0.03F * t.personality.fearGain;
                if (this.trapLineCooldown <= 0 && this.random.nextInt(4) == 0) {
                    say(Lines.random(Lines.Cat.TRAP, this.random), 70);
                    this.trapLineCooldown = 500;
                    this.speechCooldown = Math.max(this.speechCooldown, 60);
                }
            }
        }
        if (this.trapLineCooldown > 0) this.trapLineCooldown--;
        this.fear = Mth.clamp(this.fear, 0.0F, 1.0F);
        if (Math.abs(this.fear - this.entityData.get(DATA_FEAR)) > 0.02F) this.entityData.set(DATA_FEAR, this.fear);
    }

    private void updateState() {
        int s;
        if (this.holderId != null) s = ST_GRABBED;
        else if (this.lockPos != null) s = ST_LOCKED;
        else if (isThrownFlying()) s = ST_THROWN;
        else if (isFighter() && getTarget() != null && getTarget().isAlive()) s = ST_FIGHT;
        else {
            if (this.cowerTicks > 0) this.cowerTicks--;
            else if (!isFighter() && this.fear >= 0.85F && this.random.nextInt(140) == 0) this.cowerTicks = 100 + this.random.nextInt(120);
            if (this.cowerTicks > 0) s = ST_COWER;
            else if (this.fear >= 0.55F) s = ST_PANIC;
            else if (this.fear >= 0.28F) s = ST_ANXIOUS;
            else s = ST_CALM;
        }
        if (s != getState()) {
            this.entityData.set(DATA_STATE, s);
            if (s >= ST_PANIC) this.speechCooldown = Math.min(this.speechCooldown, 8);
        }
    }

    private void tickSpeech() {
        if (--this.speechCooldown > 0) return;
        Lines.Cat cat;
        int next;
        Voice v = null;
        switch (getState()) {
            case ST_GRABBED -> { cat = Lines.Cat.GRABBED; next = 45 + this.random.nextInt(40); v = Voice.SCREAM; }
            case ST_LOCKED -> { cat = Lines.Cat.LOCKED; next = 60 + this.random.nextInt(40); v = Voice.BEG; }
            case ST_THROWN -> { cat = Lines.Cat.THROWN; next = 40; v = Voice.SCREAM; }
            case ST_FIGHT -> { cat = Lines.Cat.FIGHT; next = 80 + this.random.nextInt(60); v = Voice.FIGHT; }
            case ST_COWER -> {
                cat = getContestantType().personality == ContestantType.Personality.BRIBER && this.random.nextBoolean() ? Lines.Cat.BRIBE : Lines.Cat.BEG;
                next = 70 + this.random.nextInt(50);
                v = Voice.BEG;
            }
            case ST_PANIC -> { cat = Lines.Cat.PANIC; next = 50 + this.random.nextInt(70); v = this.random.nextInt(3) == 0 ? Voice.SCREAM : Voice.PANIC; }
            case ST_ANXIOUS -> { cat = Lines.Cat.ANXIOUS; next = 160 + this.random.nextInt(160); }
            default -> { cat = Lines.Cat.CALM; next = 240 + this.random.nextInt(260); }
        }
        say(Lines.random(cat, this.random), 70);
        if (v != null) voice(v);
        this.speechCooldown = next;
    }

    /** Called when someone dies nearby: everybody who saw it panics. */
    public static void alertWitnesses(ServerLevel level, Vec3 pos, @Nullable Entity victim) {
        List<ContestantEntity> list = level.getEntitiesOfClass(ContestantEntity.class,
                new AABB(pos.x - 22.0D, pos.y - 8.0D, pos.z - 22.0D, pos.x + 22.0D, pos.y + 8.0D, pos.z + 22.0D),
                e -> e != victim && e.isAlive());
        int spoken = 0;
        for (ContestantEntity c : list) {
            double d = Math.sqrt(c.distanceToSqr(pos));
            c.addFear((float) Math.max(0.25D, 0.8D - d * 0.025D));
            c.threatPos = pos;
            c.threatTicks = 140;
            if (spoken < 3 && c.random.nextFloat() < 0.75F && !c.isHeld()) {
                c.say(Lines.random(Lines.Cat.WITNESS, c.random), 70);
                c.voice(c.random.nextBoolean() ? Voice.PANIC : Voice.SCREAM);
                c.speechCooldown = 50 + c.random.nextInt(40);
                spoken++;
            }
        }
    }

    // ------------------------------------------------------------------ damage & death

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !level().isClientSide && isAlive()) {
            addFear(0.3F);
            Entity attacker = source.getEntity();
            if (attacker != null) {
                this.threatPos = attacker.position();
                this.threatTicks = 140;
            }
            if (this.random.nextFloat() < 0.6F) {
                say(Lines.random(Lines.Cat.HURT, this.random), 50);
                this.speechCooldown = 40;
            }
            GoreHandler.bleed(this, amount);
        }
        return result;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isFemale() ? ModSounds.HURT_FEMALE.get() : ModSounds.HURT_MALE.get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return isFemale() ? ModSounds.DEATH_FEMALE.get() : ModSounds.DEATH_MALE.get();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    /** No falling-over animation: the gore system replaces the body with a corpse / chunks at once. */
    @Override
    protected void tickDeath() {
        if (!level().isClientSide() && !this.isRemoved()) {
            this.remove(Entity.RemovalReason.KILLED);
        }
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
        tag.putInt("CType", this.entityData.get(DATA_TYPE));
        tag.putInt("Skin", this.entityData.get(DATA_SKIN));
        tag.putInt("Number", getNumber());
        tag.putFloat("Fear", this.fear);
        tag.putBoolean("Ready", this.entityData.get(DATA_READY));
        if (this.roundId != null) tag.putUUID("Round", this.roundId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_TYPE, Mth.clamp(tag.getInt("CType"), 0, ContestantType.values().length - 1));
        this.entityData.set(DATA_SKIN, Mth.clamp(tag.getInt("Skin"), 0, ContestantType.skinCount() - 1));
        this.entityData.set(DATA_NUMBER, tag.getInt("Number"));
        this.fear = tag.getFloat("Fear");
        this.entityData.set(DATA_FEAR, this.fear);
        this.entityData.set(DATA_READY, tag.getBoolean("Ready"));
        this.roundId = tag.hasUUID("Round") ? tag.getUUID("Round") : null;
    }

    // ------------------------------------------------------------------ goals

    /** Freezes the AI while grabbed, locked into a guillotine or flying after a throw. */
    static final class HeldGoal extends Goal {
        private final ContestantEntity c;

        HeldGoal(ContestantEntity c) {
            this.c = c;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.c.isHeldOrLocked();
        }

        @Override
        public void start() {
            this.c.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.c.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** Kneels and begs (timid types at very high fear). */
    static final class CowerGoal extends Goal {
        private final ContestantEntity c;

        CowerGoal(ContestantEntity c) {
            this.c = c;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.c.getState() == ST_COWER;
        }

        @Override
        public boolean canContinueToUse() {
            return this.c.getState() == ST_COWER;
        }

        @Override
        public void start() {
            this.c.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.c.getNavigation().stop();
            Player p = this.c.level().getNearestPlayer(this.c, 12.0D);
            if (p != null) this.c.getLookControl().setLookAt(p, 30.0F, 30.0F);
        }
    }

    /** Runs away from the latest threat when scared. */
    static final class PanicFleeGoal extends Goal {
        private final ContestantEntity c;
        @Nullable private Vec3 target;
        private int recalc;

        PanicFleeGoal(ContestantEntity c) {
            this.c = c;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.c.getState() != ST_PANIC) return false;
            return findTarget();
        }

        private boolean findTarget() {
            Vec3 threat = this.c.getThreatPos();
            Vec3 v = threat != null ? DefaultRandomPos.getPosAway(this.c, 12, 5, threat) : DefaultRandomPos.getPos(this.c, 10, 5);
            if (v == null) return false;
            this.target = v;
            return true;
        }

        @Override
        public void start() {
            this.recalc = 0;
            if (this.target != null) this.c.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, 1.5D);
        }

        @Override
        public boolean canContinueToUse() {
            return !this.c.getNavigation().isDone() && this.c.getState() == ST_PANIC;
        }

        @Override
        public void tick() {
            if (++this.recalc > 40) {
                this.recalc = 0;
                if (findTarget() && this.target != null) this.c.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, 1.5D);
            }
        }
    }

    static final class AvoidHostGoal extends AvoidEntityGoal<Player> {
        private final ContestantEntity contestant;

        AvoidHostGoal(ContestantEntity c) {
            super(c, Player.class, 6.0F, 0.9D, 1.35D);
            this.contestant = c;
        }

        @Override
        public boolean canUse() {
            return !this.contestant.isFighter() && this.contestant.getFear() >= 0.25F && this.contestant.getState() <= ST_PANIC && super.canUse();
        }
    }

    static final class FighterAttackGoal extends MeleeAttackGoal {
        private final ContestantEntity c;

        FighterAttackGoal(ContestantEntity c) {
            super(c, 1.3D, true);
            this.c = c;
        }

        @Override
        public boolean canUse() {
            return this.c.isFighter() && !this.c.isHeldOrLocked() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.c.isFighter() && !this.c.isHeldOrLocked() && super.canContinueToUse();
        }
    }

    static final class FighterRevengeGoal extends HurtByTargetGoal {
        private final ContestantEntity c;

        FighterRevengeGoal(ContestantEntity c) {
            super(c);
            this.c = c;
        }

        @Override
        public boolean canUse() {
            return this.c.isFighter() && super.canUse();
        }
    }

    /** Used by the guillotine to check its victim. */
    public boolean isLockedAt(BlockPos pos) {
        return this.lockPos != null && this.lockPos.equals(pos);
    }

    /** True for living entities a trap may grab (never players or armor stands). */
    public static boolean isTrapVictim(LivingEntity e) {
        return e.isAlive() && !(e instanceof Player) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }
}
