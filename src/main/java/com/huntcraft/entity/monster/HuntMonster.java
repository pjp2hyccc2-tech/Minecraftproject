package com.huntcraft.entity.monster;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.Element;
import com.huntcraft.core.HCEvents;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SafeZones;
import com.huntcraft.core.SkillType;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.monster.ai.HeavyMoveControl;
import com.huntcraft.entity.monster.ai.MonsterBrainGoal;
import com.huntcraft.entity.monster.ai.MonsterWanderGoal;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.registry.HCEffects;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class of every HuntCraft monster (small and large).
 * <p>
 * Provides: synced action animations, size/rank, multipart hitboxes, hitzones, part breaks, enrage,
 * fatigue, status ailments (poison/para/sleep/blast/KO), traps & capture, carving (large), safe-zone
 * avoidance and a set of helpers used by {@link com.huntcraft.entity.monster.attack.TimelineAttack}.
 */
public abstract class HuntMonster extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(HuntMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(HuntMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(HuntMonster.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BROKEN = SynchedEntityData.defineId(HuntMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_RANK = SynchedEntityData.defineId(HuntMonster.class, EntityDataSerializers.BYTE);

    public static final int FLAG_ENRAGED = 1;
    public static final int FLAG_TIRED = 1 << 1;
    public static final int FLAG_FLYING = 1 << 2;
    public static final int FLAG_LIMPING = 1 << 3;
    public static final int FLAG_SLEEPING = 1 << 4;
    public static final int FLAG_TRAPPED = 1 << 5;
    public static final int FLAG_STUNNED = 1 << 6;
    public static final int FLAG_PARALYZED = 1 << 7;
    public static final int FLAG_CARCASS = 1 << 8;
    public static final int FLAG_RUNNING = 1 << 9;
    public static final int FLAG_CAPTURABLE = 1 << 10;
    public static final int FLAG_BLINDED = 1 << 11;
    public static final int FLAG_ALERT = 1 << 12;
    public static final int FLAG_PAINTED = 1 << 13;

    private static final int NO_ANIM = 255;
    private static final Map<Class<?>, Map<String, Integer>> ANIM_INDEX_CACHE = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ client animation state
    public final AnimationState actionAnim = new AnimationState();
    public final AnimationState idleAnim = new AnimationState();
    public final AnimationState stateAnim = new AnimationState();
    public final AnimationState flyAnim = new AnimationState();
    private int clientAnimIndex = NO_ANIM;
    private String clientStateName = null;

    // ------------------------------------------------------------------ server state
    protected final List<MonsterAttack> attacks;
    @Nullable
    protected MonsterAttack currentAttack;
    @Nullable
    protected LivingEntity attackTarget;
    protected int actionTicks;
    protected int actionDuration;
    private int animSeq;
    protected final Set<Integer> hitThisAction = new HashSet<>();
    private String currentHitPart = "body";

    protected final Map<String, Float> partDamage = new HashMap<>();
    protected float rageMeter;
    protected float flinchMeter;
    protected int enrageTicks;
    protected int tiredTicks;
    protected int fatigueClock;
    protected final float[] statusBuild = new float[6];
    protected final int[] statusProcs = new int[6];
    protected int lockTicks;
    protected int trapKind;
    protected int tranqHits;
    protected int carcassTicks;
    protected int paintTicks;
    protected int poisonTicks;
    protected final Map<UUID, Integer> carvesTaken = new HashMap<>();
    protected final Map<UUID, Float> damageByPlayer = new HashMap<>();
    @Nullable
    protected String questId;
    @Nullable
    protected BlockPos homePos;
    protected boolean hasRoared;
    protected int combatTicks;
    protected final MonsterPart[] parts;

    protected HuntMonster(EntityType<? extends HuntMonster> type, Level level) {
        super(type, level);
        MonsterProfile prof = profile();
        List<MonsterPart> list = new ArrayList<>();
        for (PartDef def : prof.hitboxParts) list.add(new MonsterPart(this, def));
        this.parts = list.toArray(new MonsterPart[0]);
        this.attacks = new ArrayList<>(createAttacks());
        this.moveControl = new HeavyMoveControl(this);
        this.xpReward = prof.large ? 50 : 5;
        if (prof.large) {
            this.setMaxUpStep(1.6F);
            this.noCulling = true;
        }
    }

    // ================================================================== abstract API
    /** Static species profile (keep in a static final field). */
    public abstract MonsterProfile profile();

    /**
     * Animation names that can be played as actions, by index. Must include every attack's anim name
     * plus the standard ones you use ("roar", "flinch", ...). Max 254 entries.
     */
    public abstract String[] animationNames();

    /** Attacks available to this monster (created once per instance). */
    protected List<MonsterAttack> createAttacks() {
        return List.of();
    }

    /** True for monsters that attack hunters on sight. */
    public boolean isHostile() {
        return true;
    }

    /** Turn speed in degrees per tick. Large monsters should be slow (4-7). */
    public float getTurnSpeed() {
        return profile().large ? 6.0F : 20.0F;
    }

    public double getWalkSpeedModifier() {
        return 0.8;
    }

    public double getRunSpeedModifier() {
        return 1.5;
    }

    /** Ticks between attacks (lower when enraged). */
    public int getAttackGap() {
        int base = profile().large ? 18 : 25;
        if (isEnraged()) base = base * 6 / 10;
        if (isTired()) base = base * 3 / 2;
        return base;
    }

    // ================================================================== synced data
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIM, NO_ANIM);
        this.entityData.define(DATA_FLAGS, 0);
        this.entityData.define(DATA_SCALE, 1.0F);
        this.entityData.define(DATA_BROKEN, 0);
        this.entityData.define(DATA_RANK, (byte) 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key)) {
            this.refreshDimensions();
            if (parts != null) for (MonsterPart p : parts) p.refreshDimensions();
        }
        if (DATA_ANIM.equals(key) && this.level().isClientSide) {
            int idx = this.entityData.get(DATA_ANIM) & 0xFF;
            this.clientAnimIndex = idx;
            if (idx == NO_ANIM) this.actionAnim.stop();
            else this.actionAnim.start(this.tickCount);
        }
    }

    public boolean getFlag(int flag) {
        return (this.entityData.get(DATA_FLAGS) & flag) != 0;
    }

    public void setFlag(int flag, boolean value) {
        int f = this.entityData.get(DATA_FLAGS);
        int n = value ? (f | flag) : (f & ~flag);
        if (n != f) this.entityData.set(DATA_FLAGS, n);
    }

    public float getMonsterScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setMonsterScale(float s) {
        this.entityData.set(DATA_SCALE, Mth.clamp(s, 0.3F, 3.0F));
        this.refreshDimensions();
        if (parts != null) for (MonsterPart p : parts) p.refreshDimensions();
    }

    /** Random size following MH crowns: rare minis / golds at the edges. */
    public void rollScale(RandomSource random) {
        MonsterProfile p = profile();
        float t = (float) Mth.clamp(0.5 + random.nextGaussian() * 0.18, 0, 1);
        if (random.nextFloat() < 0.04F) t = random.nextBoolean() ? 0.0F : 1.0F;
        setMonsterScale(p.minScale + (p.maxScale - p.minScale) * t);
    }

    public Rank getRank() {
        return Rank.byOrdinal(this.entityData.get(DATA_RANK));
    }

    public void setRank(Rank rank) {
        this.entityData.set(DATA_RANK, (byte) rank.ordinal());
        AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(Math.min(1024.0, rank == Rank.HIGH ? profile().healthHigh : profile().healthLow));
            this.setHealth(this.getMaxHealth());
        }
    }

    /** Multiplier applied to all outgoing damage (rank + enrage). */
    public float damageMultiplier() {
        float m = getRank() == Rank.HIGH ? profile().damageHighMult : 1.0F;
        if (isEnraged()) m *= 1.25F;
        return m;
    }

    /** Multiplier applied to all incoming damage (high rank defense rate, sleep bonus...). */
    public float defenseMultiplier() {
        return getRank() == Rank.HIGH ? profile().defenseHigh : 1.0F;
    }

    public int getBrokenMask() {
        return this.entityData.get(DATA_BROKEN);
    }

    public boolean isPartBroken(String partName) {
        int i = partIndex(partName);
        return i >= 0 && (getBrokenMask() & (1 << i)) != 0;
    }

    /** Index of a part among the profile's parts (for the broken bitmask). */
    public int partIndex(String partName) {
        int i = 0;
        for (String n : profile().parts.keySet()) {
            if (n.equals(partName)) return i;
            i++;
        }
        return -1;
    }

    // ------------------------------------------------------------------ state shortcuts
    public boolean isEnraged() { return getFlag(FLAG_ENRAGED); }
    public boolean isTired() { return getFlag(FLAG_TIRED); }
    public boolean isFlying() { return getFlag(FLAG_FLYING); }
    public boolean isLimping() { return getFlag(FLAG_LIMPING); }
    public boolean isSleepingState() { return getFlag(FLAG_SLEEPING); }
    public boolean isTrapped() { return getFlag(FLAG_TRAPPED); }
    public boolean isStunned() { return getFlag(FLAG_STUNNED); }
    public boolean isParalyzed() { return getFlag(FLAG_PARALYZED); }
    public boolean isCarcass() { return getFlag(FLAG_CARCASS); }
    public boolean isRunning() { return getFlag(FLAG_RUNNING); }
    public boolean isBlinded() { return getFlag(FLAG_BLINDED); }
    public boolean isPainted() { return getFlag(FLAG_PAINTED); }

    public boolean isCapturable() {
        return profile().large && !isCarcass() && getHealth() <= getMaxHealth() * profile().captureThreshold;
    }

    /** Locked by a status (cannot act). */
    public boolean isLocked() {
        return isCarcass() || isTrapped() || isStunned() || isParalyzed() || isSleepingState() || isBlinded();
    }

    /** May the brain start new actions now? */
    public boolean canAct() {
        return !isLocked() && actionTicks <= 0;
    }

    public void setRunning(boolean running) {
        setFlag(FLAG_RUNNING, running);
    }

    public void setFlying(boolean flying) {
        setFlag(FLAG_FLYING, flying);
        this.setNoGravity(flying);
        if (!flying) this.fallDistance = 0;
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || isLocked();
    }

    // ================================================================== actions / animation
    private Map<String, Integer> animIndexMap() {
        return ANIM_INDEX_CACHE.computeIfAbsent(getClass(), c -> {
            Map<String, Integer> m = new HashMap<>();
            String[] names = animationNames();
            for (int i = 0; i < names.length && i < NO_ANIM; i++) m.put(names[i], i);
            return m;
        });
    }

    public int animIndex(String name) {
        Integer i = animIndexMap().get(name);
        return i == null ? NO_ANIM : i;
    }

    /** Client: current action animation name or null. */
    @Nullable
    public String getActionAnimName() {
        if (clientAnimIndex == NO_ANIM) return null;
        String[] names = animationNames();
        return clientAnimIndex < names.length ? names[clientAnimIndex] : null;
    }

    /** Client: looping/holding state animation (death, trapped, sleep, stun, tired) or null. */
    @Nullable
    public String getStateAnimName() {
        return clientStateName;
    }

    @Nullable
    protected String computeStateAnim() {
        if (isCarcass()) return "death";
        if (isTrapped()) return "trapped";
        if (isSleepingState()) return "sleep";
        if (isStunned() || isParalyzed() || isBlinded()) return "stun";
        return null;
    }

    private void sendAnim(int index) {
        animSeq = (animSeq + 1) & 0xFFFFFF;
        this.entityData.set(DATA_ANIM, (animSeq << 8) | (index & 0xFF));
    }

    /** Plays a non-attack action animation for `ticks` ticks (server). */
    public void playAction(String anim, int ticks) {
        if (currentAttack != null) {
            currentAttack.end(this);
            currentAttack = null;
        }
        actionTicks = ticks;
        actionDuration = ticks;
        hitThisAction.clear();
        sendAnim(animIndex(anim));
    }

    public void startAttack(MonsterAttack attack, @Nullable LivingEntity target) {
        if (currentAttack != null) currentAttack.end(this);
        currentAttack = attack;
        attackTarget = target;
        actionTicks = attack.duration;
        actionDuration = attack.duration;
        attack.lastUsedTick = this.tickCount;
        hitThisAction.clear();
        sendAnim(animIndex(attack.anim));
        this.getNavigation().stop();
        attack.start(this, target);
    }

    public void stopAction() {
        if (currentAttack != null) {
            currentAttack.end(this);
            currentAttack = null;
        }
        actionTicks = 0;
        hitThisAction.clear();
        sendAnim(NO_ANIM);
    }

    public boolean isActing() {
        return actionTicks > 0;
    }

    @Nullable
    public MonsterAttack getCurrentAttack() {
        return currentAttack;
    }

    public List<MonsterAttack> getAttacks() {
        return attacks;
    }

    protected void tickAction() {
        if (actionTicks <= 0) return;
        int t = actionDuration - actionTicks;
        if (currentAttack != null) {
            LivingEntity target = attackTarget != null && attackTarget.isAlive() ? attackTarget : getTarget();
            currentAttack.tick(this, target, t);
        }
        actionTicks--;
        if (actionTicks <= 0) {
            if (currentAttack != null) {
                currentAttack.end(this);
                currentAttack = null;
            }
            sendAnim(NO_ANIM);
        }
    }

    // ================================================================== geometry helpers
    /** World position of a local offset (side + = right, up, forward + = facing), scaled by size. */
    public Vec3 localPos(double side, double up, double forward) {
        float s = getMonsterScale();
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        return new Vec3(getX() + (fx * forward + rx * side) * s, getY() + up * s, getZ() + (fz * forward + rz * side) * s);
    }

    /** Signed angle (deg) from facing to a point: 0 = front, + = right. */
    public float angleTo(Vec3 point) {
        double dx = point.x - getX(), dz = point.z - getZ();
        float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        return Mth.wrapDegrees(target - this.getYRot());
    }

    public void turnTowards(Vec3 point, float maxDeg) {
        double dx = point.x - getX(), dz = point.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.getYRot(), target, maxDeg);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** Sets velocity along facing (blocks/tick, scaled by size^0.5 so big monsters cover more ground). */
    public void moveLocal(double forwardSpeed, double upSpeed) {
        float s = (float) Math.sqrt(getMonsterScale());
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 v = new Vec3(-Mth.sin(yaw) * forwardSpeed * s, upSpeed != 0 ? upSpeed : this.getDeltaMovement().y, Mth.cos(yaw) * forwardSpeed * s);
        this.setDeltaMovement(v);
        this.hasImpulse = true;
    }

    public void lungeTowards(Vec3 point, double speed) {
        Vec3 d = new Vec3(point.x - getX(), 0, point.z - getZ());
        if (d.lengthSqr() < 1.0) return;
        d = d.normalize().scale(speed * Math.sqrt(getMonsterScale()));
        this.setDeltaMovement(d.x, this.getDeltaMovement().y, d.z);
        this.hasImpulse = true;
    }

    // ================================================================== offensive helpers
    /** Damages every valid target within a sphere (each entity once per action). */
    public void hitSphere(Vec3 center, double radius, float damage, Element element, float knockback, int flags) {
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidHitTarget)) {
            if (boxDistSqr(e.getBoundingBox(), center) <= radius * radius) {
                hitEntity(e, damage, element, knockback, flags);
            }
        }
    }

    /** Damages targets in an arc (angles relative to facing, deg). */
    public void hitArc(double innerR, double outerR, float fromDeg, float toDeg, double height, float damage, Element element, float knockback, int flags) {
        AABB box = getBoundingBox().inflate(outerR, height, outerR);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidHitTarget)) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() * 0.5;
            if (dist > outerR || dist < innerR - 0.5) continue;
            if (e.getY() > getY() + height || e.getY() + e.getBbHeight() < getY() - 1) continue;
            float a = angleTo(e.position());
            if (angleInRange(a, fromDeg, toDeg)) hitEntity(e, damage, element, knockback, flags);
        }
    }

    public static double boxDistSqr(AABB b, Vec3 p) {
        double dx = Math.max(0, Math.max(b.minX - p.x, p.x - b.maxX));
        double dy = Math.max(0, Math.max(b.minY - p.y, p.y - b.maxY));
        double dz = Math.max(0, Math.max(b.minZ - p.z, p.z - b.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean angleInRange(float a, float from, float to) {
        // from..to may exceed +-180 (e.g. 0..360 for a full spin)
        if (to - from >= 360) return true;
        for (int k = -1; k <= 1; k++) {
            float aa = a + 360 * k;
            if (aa >= from && aa <= to) return true;
        }
        return false;
    }

    public boolean isValidHitTarget(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof HuntMonster) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** Applies a monster hit (once per action) through {@link MonsterDamage}. */
    public boolean hitEntity(LivingEntity e, float damage, Element element, float knockback, int flags) {
        if (!hitThisAction.add(e.getId())) return false;
        return MonsterDamage.deal(this, this, e, damage * damageMultiplier(), element, knockback, flags);
    }

    public void shootProjectile(Vec3 from, @Nullable LivingEntity target, ProjectileSpec spec) {
        if (level().isClientSide) return;
        Vec3 aim;
        if (target != null) {
            aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from);
        } else {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            aim = new Vec3(-Mth.sin(yaw), -0.2, Mth.cos(yaw));
        }
        aim = aim.normalize();
        int n = Math.max(1, spec.count);
        for (int i = 0; i < n; i++) {
            float off = n == 1 ? 0 : (i - (n - 1) / 2.0F) * spec.spreadDeg;
            Vec3 dir = aim.yRot(off * Mth.DEG_TO_RAD);
            MonsterProjectile p = new MonsterProjectile(level(), this, spec, dir, target);
            p.setPos(from.x, from.y, from.z);
            level().addFreshEntity(p);
        }
    }

    /** One tick of a cone breath: damage within the cone and spawn particles. */
    public void breathCone(Vec3 origin, float length, float coneDeg, float damage, Element element, ParticleOptions particle, int step) {
        if (!(level() instanceof ServerLevel sl)) return;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        LivingEntity target = getTarget();
        Vec3 dir;
        if (target != null && Math.abs(angleTo(target.position())) < 45) {
            dir = target.position().add(0, 0.6, 0).subtract(origin).normalize();
        } else {
            dir = new Vec3(-Mth.sin(yaw), -0.35, Mth.cos(yaw)).normalize();
        }
        RandomSource r = getRandom();
        for (int i = 0; i < 14; i++) {
            double d = r.nextDouble() * length;
            double spread = Math.tan(Math.toRadians(coneDeg / 2.0)) * d;
            Vec3 p = origin.add(dir.scale(d)).add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread * 0.6, (r.nextDouble() - 0.5) * spread);
            sl.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0.01);
        }
        if (step % 3 == 0) {
            hitThisAction.clear(); // breath hits repeatedly (every 3 ticks)
            double cos = Math.cos(Math.toRadians(coneDeg / 2.0));
            AABB box = new AABB(origin, origin).inflate(length);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidHitTarget)) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
                double dist = to.length();
                if (dist > length + e.getBbWidth()) continue;
                if (dist < 1.5 || to.normalize().dot(dir) >= cos) hitEntity(e, damage, element, 0.3F, 0);
            }
        }
    }

    /** Roar: hunters within radius without Earplugs cover their ears. */
    public void doRoar(float radius) {
        playSound(profile().large ? HCSounds.ROAR_LARGE.get() : HCSounds.SMALL_CALL.get(), 3.0F, 0.9F + getRandom().nextFloat() * 0.15F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + getBbHeight() * 0.7, getZ(), 1, 0, 0, 0, 0);
            for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius))) {
                if (p.isCreative() || p.isSpectator()) continue;
                if (HunterSkills.level(p, SkillType.EARPLUGS) >= (profile().large ? 2 : 1)) continue;
                if (com.huntcraft.data.HunterData.get(p).isInvulnerable()) continue;
                p.addEffect(new MobEffectInstance(HCEffects.ROAR.get(), profile().large ? 40 : 20, 0, false, true));
            }
        }
    }

    /** Ground tremor: briefly staggers grounded hunters. */
    public void doTremor(float radius) {
        playSound(HCSounds.STOMP.get(), 2.5F, 0.6F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 20, radius * 0.4, 0.1, radius * 0.4, 0.01);
            for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 2, radius))) {
                if (!p.onGround() || p.isCreative() || HunterSkills.level(p, SkillType.TREMOR_RES) > 0) continue;
                if (com.huntcraft.data.HunterData.get(p).isInvulnerable()) continue;
                p.addEffect(new MobEffectInstance(HCEffects.STUN.get(), 15, 0, false, false));
            }
            com.huntcraft.core.CameraShake.shake(sl, position(), radius * 2, 0.6F, 10);
        }
    }

    /** Wind pressure from wing beats: pushes hunters back. */
    public void doWindPressure(float radius) {
        playSound(HCSounds.WING_FLAP.get(), 2.0F, 0.8F);
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 3, radius))) {
            if (p.isCreative() || HunterSkills.level(p, SkillType.WINDPROOF) >= (isFlying() ? 1 : 2)) continue;
            MonsterDamage.applyKnockback(position(), p, 0.6F, false);
        }
    }

    public void burstParticles(ParticleOptions particle, Vec3 pos, int count, double spread) {
        if (level() instanceof ServerLevel sl) sl.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02);
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!this.isSilent()) level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ================================================================== taking damage
    public String getCurrentHitPart() {
        return currentHitPart;
    }

    public boolean hurtFromPart(MonsterPart part, DamageSource source, float amount) {
        currentHitPart = part.def.name;
        boolean r = this.hurt(source, amount);
        currentHitPart = "body";
        return r;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isCarcass() && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) return true;
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isCarcass() && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) return false;
        if (source.getEntity() instanceof Player) this.invulnerableTime = 0;
        if (SafeZones.isInSafeZone(level(), position()) && source.getEntity() instanceof Player) {
            // still hurtable; safe zones only stop monsters attacking
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof Player player && !level().isClientSide) {
            setFlag(FLAG_ALERT, true);
            if (getTarget() == null) setTarget(player);
        }
        return hurt;
    }

    /**
     * Called by the core damage pipeline after the final damage of a hunter hit was computed.
     *
     * @param partName  part that was hit
     * @param kind      cut / blunt / shot
     * @param damage    final damage that will be applied
     * @param element   weapon element/status
     * @param statusAmt status buildup (for status weapons) or 0
     */
    public void onHunterDamage(String partName, DamageKind kind, float damage, Element element, float statusAmt, @Nullable Player player) {
        if (level().isClientSide || isCarcass()) return;
        combatTicks = 0;
        if (player != null) damageByPlayer.merge(player.getUUID(), damage, Float::sum);
        float maxHp = getMaxHealth();
        // part damage
        PartDef part = profile().part(partName);
        if (part != null && part.isBreakable() && !isPartBroken(part.name)) {
            if (!part.severable || kind == DamageKind.CUT) {
                float total = partDamage.getOrDefault(part.name, 0F) + damage;
                partDamage.put(part.name, total);
                float threshold = part.breakThreshold * (getRank() == Rank.HIGH ? profile().healthHigh / profile().healthLow : 1.0F);
                if (total >= threshold) breakPart(part, player);
            }
        }
        // KO / exhaust
        if (kind == DamageKind.BLUNT) {
            if (part != null && part.koZone) addStatus(4, damage * 0.6F, player);
            addStatus(5, damage * 0.3F, player);
        }
        if (element.isStatus() && statusAmt > 0) {
            int idx = switch (element) {
                case POISON -> 0;
                case PARALYSIS -> 1;
                case SLEEP -> 2;
                default -> 3;
            };
            addStatus(idx, statusAmt, player);
        }
        // sleeping monsters wake up on hit
        if (isSleepingState()) {
            setFlag(FLAG_SLEEPING, false);
            lockTicks = 0;
        }
        // rage
        if (!isEnraged()) {
            rageMeter += damage;
            if (rageMeter >= maxHp * profile().rageThreshold) enrage();
        }
        // flinch
        flinchMeter += damage;
        if (flinchMeter >= maxHp * profile().flinchThreshold && !isLocked()) {
            flinchMeter = 0;
            onFlinch();
        }
    }

    /** Status buildup: 0 poison, 1 paralysis, 2 sleep, 3 blast, 4 KO, 5 exhaust. */
    public void addStatus(int idx, float amount, @Nullable Player player) {
        StatusThresholds st = profile().status;
        float threshold = st.base(idx) * Math.min(st.max(), 1.0F + (st.increment() - 1.0F) * statusProcs[idx]);
        statusBuild[idx] += amount;
        if (statusBuild[idx] < threshold) return;
        statusBuild[idx] = 0;
        statusProcs[idx]++;
        switch (idx) {
            case 0 -> { poisonTicks = 300; burstParticles(ParticleTypes.WITCH, position().add(0, getBbHeight() * 0.7, 0), 30, 1.0); }
            case 1 -> { lock(FLAG_PARALYZED, profile().large ? 120 : 160); burstParticles(ParticleTypes.ELECTRIC_SPARK, position().add(0, getBbHeight() * 0.6, 0), 40, 1.2); }
            case 2 -> { lock(FLAG_SLEEPING, 600); }
            case 3 -> {
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0, 0, 0, 0);
                }
                playSound(HCSounds.EXPLOSION_SMALL.get(), 2.0F, 1.0F);
                this.invulnerableTime = 0;
                float blast = profile().large ? 60F : 20F;
                this.hurt(player != null ? damageSources().explosion(player, player) : damageSources().generic(), blast);
            }
            case 4 -> { lock(FLAG_STUNNED, profile().large ? 140 : 100); playSound(HCSounds.KO.get(), 2.0F, 1.0F); }
            case 5 -> { tiredTicks = Math.max(tiredTicks, 600); setFlag(FLAG_TIRED, true); }
            default -> { }
        }
    }

    /** Lock in a status state for `ticks` ticks (stops current action). */
    public void lock(int flag, int ticks) {
        stopAction();
        setFlag(FLAG_STUNNED | FLAG_PARALYZED | FLAG_SLEEPING | FLAG_BLINDED, false);
        setFlag(flag, true);
        lockTicks = Math.max(lockTicks, ticks);
        if (isFlying()) setFlying(false);
        this.getNavigation().stop();
    }

    protected void onFlinch() {
        if (animIndex("flinch") != NO_ANIM) playAction("flinch", 24);
        playSound(HCSounds.HURT_LARGE.get(), 2.0F, 1.0F);
    }

    public void enrage() {
        if (isCarcass()) return;
        setFlag(FLAG_ENRAGED, true);
        enrageTicks = 1200 + getRandom().nextInt(600);
        rageMeter = 0;
        playSound(HCSounds.ENRAGE.get(), 2.0F, 1.0F);
        if (canAct() || isActing()) {
            if (animIndex("roar") != NO_ANIM) playAction("roar", 40);
            doRoar(12.0F);
        }
        onEnrage();
    }

    protected void onEnrage() {
    }

    public void breakPart(PartDef part, @Nullable Player player) {
        int i = partIndex(part.name);
        if (i < 0) return;
        this.entityData.set(DATA_BROKEN, getBrokenMask() | (1 << i));
        playSound(part.severable ? HCSounds.TAIL_CUT.get() : HCSounds.PART_BREAK.get(), 2.5F, 1.0F);
        if (level() instanceof ServerLevel sl) {
            Vec3 pos = localPos(part.side, part.up + part.height * 0.5, part.forward);
            sl.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 40, 0.6, 0.6, 0.6, 0.4);
            sl.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 15, 0.4, 0.4, 0.4, 0.05);
        }
        if (part.severable && !profile().tailCarves.isEmpty()) {
            // severed tail: drops its carve rewards on the ground at the tail position
            Vec3 pos = localPos(part.side, part.up, part.forward);
            int n = 1 + (player != null ? HunterSkills.level(player, SkillType.CARVING) : 0);
            for (int k = 0; k < n; k++) {
                ItemStack st = profile().tailCarves.roll(getRandom(), getRank());
                if (!st.isEmpty()) level().addFreshEntity(new ItemEntity(level(), pos.x, pos.y + 0.5, pos.z, st));
            }
        }
        if (!isLocked() && animIndex("flinch") != NO_ANIM) playAction("flinch", 24);
        MinecraftForge.EVENT_BUS.post(new HCEvents.MonsterPartBrokenEvent(this, part.name, player));
        onPartBroken(part, player);
    }

    protected void onPartBroken(PartDef part, @Nullable Player player) {
    }

    // ================================================================== traps / capture / items
    /** Called by traps. kind: 0 pitfall, 1 shock. */
    public boolean trap(int kind, int ticks) {
        if (!profile().large || isCarcass() || isFlying() || isTrapped()) return false;
        trapKind = kind;
        lock(FLAG_TRAPPED, ticks);
        playSound(HCSounds.TRAP_TRIGGER.get(), 2.0F, 1.0F);
        return true;
    }

    /** Tranquilizer bomb hit. Returns true if this captured the monster. */
    public boolean onTranqHit(@Nullable Player player) {
        if (!(isTrapped() || isSleepingState()) || !isCapturable()) return false;
        tranqHits++;
        burstParticles(ParticleTypes.EFFECT, position().add(0, getBbHeight() * 0.8, 0), 20, 0.8);
        if (tranqHits >= 2) {
            capture(player);
            return true;
        }
        return false;
    }

    public void capture(@Nullable Player player) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5, getZ(), 80, getBbWidth() * 0.5, getBbHeight() * 0.5, getBbWidth() * 0.5, 0.05);
        }
        playSound(HCSounds.CAPTURE.get(), 2.0F, 1.0F);
        MinecraftForge.EVENT_BUS.post(new HCEvents.MonsterHuntedEvent(this, player, true));
        this.discard();
    }

    /** Flash bomb: flying monsters fall, all monsters flail for a while. */
    public void blind(int ticks) {
        if (isCarcass()) return;
        if (isFlying()) {
            setFlying(false);
            setDeltaMovement(getDeltaMovement().add(0, -0.6, 0));
        }
        lock(FLAG_BLINDED, ticks);
    }

    public void paint(int ticks) {
        paintTicks = Math.max(paintTicks, ticks);
        setFlag(FLAG_PAINTED, true);
    }

    /** Dung bomb: leave the area (move home position far away). */
    public void repel() {
        if (isCarcass()) return;
        setTarget(null);
        setFlag(FLAG_ALERT, false);
        double ang = getRandom().nextDouble() * Math.PI * 2;
        homePos = BlockPos.containing(getX() + Math.cos(ang) * 60, getY(), getZ() + Math.sin(ang) * 60);
        hasRoared = false;
    }

    // ================================================================== death / carving
    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) return;
        if (profile().large) {
            if (!isCarcass()) becomeCarcass(source);
            return;
        }
        super.die(source);
        Player killer = source.getEntity() instanceof Player p ? p : null;
        MinecraftForge.EVENT_BUS.post(new HCEvents.MonsterHuntedEvent(this, killer, false));
    }

    protected void becomeCarcass(DamageSource source) {
        stopAction();
        setFlag(FLAG_ENRAGED | FLAG_TIRED | FLAG_TRAPPED | FLAG_STUNNED | FLAG_PARALYZED | FLAG_SLEEPING | FLAG_BLINDED | FLAG_RUNNING | FLAG_LIMPING, false);
        if (isFlying()) setFlying(false);
        setFlag(FLAG_CARCASS, true);
        this.setHealth(1.0F);
        this.setTarget(null);
        this.getNavigation().stop();
        this.carcassTicks = 20 * 150;
        playSound(HCSounds.DEATH_LARGE.get(), 3.0F, 1.0F);
        Player killer = source.getEntity() instanceof Player p ? p : null;
        MinecraftForge.EVENT_BUS.post(new HCEvents.MonsterHuntedEvent(this, killer, false));
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (profile().large) return;
        int n = profile().carveCount;
        if (source.getEntity() instanceof Player p) n += HunterSkills.level(p, SkillType.CARVING) > 0 ? 1 : 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = profile().carves.roll(getRandom(), getRank());
            if (!st.isEmpty()) this.spawnAtLocation(st);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isCarcass() && hand == InteractionHand.MAIN_HAND) {
            if (!level().isClientSide) carve(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    public int carvesAllowed(Player player) {
        return profile().carveCount + HunterSkills.level(player, SkillType.CARVING) + com.huntcraft.core.HuntHooks.extraCarves(player);
    }

    public void carve(Player player) {
        int taken = carvesTaken.getOrDefault(player.getUUID(), 0);
        if (taken >= carvesAllowed(player)) {
            player.displayClientMessage(Component.translatable("message.huntcraft.carve_done"), true);
            return;
        }
        carvesTaken.put(player.getUUID(), taken + 1);
        ItemStack st = profile().carves.roll(getRandom(), getRank());
        if (!st.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.huntcraft.carved", st.getHoverName(), st.getCount()), true);
            if (!player.getInventory().add(st)) player.drop(st, false);
        }
        playSound(HCSounds.CARVE.get(), 1.0F, 0.9F + getRandom().nextFloat() * 0.2F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        }
        if (taken + 1 >= carvesAllowed(player)) {
            player.displayClientMessage(Component.translatable("message.huntcraft.carve_done"), true);
        }
    }

    // ================================================================== tick
    @Override
    public void aiStep() {
        super.aiStep();
        updateParts();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    protected void clientTick() {
        this.idleAnim.startIfStopped(this.tickCount);
        this.flyAnim.animateWhen(isFlying(), this.tickCount);
        String state = computeStateAnim();
        if (!java.util.Objects.equals(state, clientStateName)) {
            clientStateName = state;
            if (state == null) stateAnim.stop();
            else stateAnim.start(this.tickCount);
        }
        RandomSource r = getRandom();
        if (isEnraged() && tickCount % 4 == 0) {
            Vec3 m = mouthPos();
            level().addParticle(ParticleTypes.SMOKE, m.x, m.y, m.z, (r.nextDouble() - 0.5) * 0.05, 0.05, (r.nextDouble() - 0.5) * 0.05);
        }
        if (isTired() && tickCount % 10 == 0) {
            Vec3 m = mouthPos();
            level().addParticle(ParticleTypes.DRIPPING_WATER, m.x, m.y - 0.2, m.z, 0, 0, 0);
        }
        if ((isStunned() || isBlinded()) && tickCount % 5 == 0) {
            Vec3 m = mouthPos();
            level().addParticle(ParticleTypes.ENCHANTED_HIT, m.x + (r.nextDouble() - 0.5), m.y + 0.6, m.z + (r.nextDouble() - 0.5), 0, 0.05, 0);
        }
        if (isSleepingState() && tickCount % 30 == 0) {
            Vec3 m = mouthPos();
            level().addParticle(ParticleTypes.NOTE, m.x, m.y + 0.8, m.z, 0.2, 0, 0);
        }
    }

    /** Approximate mouth position (head part if present). Used for particles. */
    public Vec3 mouthPos() {
        PartDef head = profile().parts.get("head");
        if (head != null) return localPos(head.side, head.up + head.height * 0.5, head.forward + head.width * 0.4);
        return position().add(0, getBbHeight() * 0.8, 0);
    }

    protected void serverTick() {
        // safe zones: monsters are pushed out and forget their target
        if (tickCount % 5 == 0 && SafeZones.isInSafeZone(level(), position())) {
            SafeZones.pushOut(this);
        }
        if (isCarcass()) {
            this.setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            if (--carcassTicks <= 0) {
                if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 40, getBbWidth() * 0.4, 1, getBbWidth() * 0.4, 0.02);
                discard();
            }
            return;
        }
        // locks
        if (lockTicks > 0) {
            lockTicks--;
            if (isTrapped() && trapKind == 1 && tickCount % 4 == 0) {
                burstParticles(ParticleTypes.ELECTRIC_SPARK, position().add(0, getBbHeight() * 0.4, 0), 6, getBbWidth() * 0.4);
            }
            if (lockTicks <= 0) {
                boolean wasTrapped = isTrapped();
                setFlag(FLAG_TRAPPED | FLAG_STUNNED | FLAG_PARALYZED | FLAG_SLEEPING | FLAG_BLINDED, false);
                tranqHits = 0;
                if (wasTrapped && !isEnraged()) enrage();
            }
        }
        // sleeping heals slowly
        if (isSleepingState() && tickCount % 20 == 0) heal(getMaxHealth() * 0.005F);
        // poison
        if (poisonTicks > 0) {
            poisonTicks--;
            if (poisonTicks % 20 == 0) {
                this.invulnerableTime = 0;
                hurt(damageSources().magic(), profile().large ? 6.0F : 2.0F);
            }
        }
        if (paintTicks > 0 && --paintTicks <= 0) setFlag(FLAG_PAINTED, false);
        // enrage / fatigue
        if (isEnraged() && --enrageTicks <= 0) {
            setFlag(FLAG_ENRAGED, false);
            if (getRandom().nextFloat() < 0.5F) {
                tiredTicks = 500;
                setFlag(FLAG_TIRED, true);
                playSound(HCSounds.TIRED.get(), 1.5F, 1.0F);
            }
        }
        if (isTired() && --tiredTicks <= 0) setFlag(FLAG_TIRED, false);
        if (getTarget() != null) {
            if (++fatigueClock > 20 * 240) {
                fatigueClock = 0;
                if (!isEnraged()) {
                    tiredTicks = 500;
                    setFlag(FLAG_TIRED, true);
                }
            }
        }
        // limping / capture indicator
        setFlag(FLAG_CAPTURABLE, isCapturable());
        setFlag(FLAG_LIMPING, profile().large && getHealth() <= getMaxHealth() * 0.2F);
        combatTicks++;
        if (getTarget() == null && combatTicks > 400 && getFlag(FLAG_ALERT)) {
            setFlag(FLAG_ALERT, false);
            hasRoared = false;
        }
        if (isLocked()) {
            this.getNavigation().stop();
            this.setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
        tickAction();
        customServerTick();
    }

    /** Per-monster extra server logic (called every tick when not a carcass). */
    protected void customServerTick() {
    }

    // ================================================================== parts / dimensions
    protected void updateParts() {
        if (parts == null) return;
        for (MonsterPart part : parts) {
            PartDef d = part.def;
            Vec3 old = part.position();
            Vec3 p = localPos(d.side, d.up, d.forward);
            part.setPos(p.x, p.y, p.z);
            part.xo = old.x;
            part.yo = old.y;
            part.zo = old.z;
            part.xOld = old.x;
            part.yOld = old.y;
            part.zOld = old.z;
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return parts != null && parts.length > 0;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    public MonsterPart[] getMonsterParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getMonsterScale());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(getBbWidth() * 2.0 + 4.0, getBbHeight(), getBbWidth() * 2.0 + 4.0);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                HuntMonster.this.yBodyRot = HuntMonster.this.getYRot();
                HuntMonster.this.yHeadRot = Mth.clamp(Mth.wrapDegrees(HuntMonster.this.yHeadRot - HuntMonster.this.yBodyRot), -getMaxHeadYRot(), getMaxHeadYRot()) + HuntMonster.this.yBodyRot;
            }
        };
    }

    @Override
    public int getMaxHeadYRot() {
        return profile().large ? 35 : 60;
    }

    @Override
    public int getMaxHeadXRot() {
        return profile().large ? 25 : 40;
    }

    // ================================================================== AI hooks
    /**
     * Monster-specific combat decisions, called every brain tick when not acting (before attack selection).
     * Return true to skip the default logic this tick (e.g. after starting a take-off).
     */
    public boolean brainTick(LivingEntity target) {
        return false;
    }

    /**
     * Movement while no attack is available (or during the pause between attacks when `waiting`).
     * Default: approach (run when far), otherwise turn to face the target.
     */
    public void combatMove(LivingEntity target, double dist, float angle, boolean waiting) {
        if (isFlying()) {
            flyTowards(target.position().add(0, 7.0 * getMonsterScale(), 0), 0.45);
            return;
        }
        float scale = getMonsterScale();
        double close = (profile().large ? 3.5 : 1.2) * scale;
        if (dist > close) {
            boolean run = dist > 10 * scale && !waiting;
            setRunning(run);
            double speed = run ? getRunSpeedModifier() : getWalkSpeedModifier() * (waiting ? 0.6 : 1.0);
            if (profile().large || getBbWidth() > 1.6F) {
                getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
            } else {
                getNavigation().moveTo(target, speed);
            }
        } else {
            setRunning(false);
            getNavigation().stop();
            if (Math.abs(angle) > 10) turnTowards(target.position(), getTurnSpeed());
        }
    }

    /** Direct flight movement toward a point (used while FLYING). */
    public void flyTowards(Vec3 pos, double speed) {
        Vec3 d = pos.subtract(position());
        double len = d.length();
        if (len < 0.5) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        Vec3 v = d.scale(Math.min(speed, len) / len);
        setDeltaMovement(getDeltaMovement().scale(0.85).add(v.scale(0.15 * 4)));
        if (getDeltaMovement().length() > speed) setDeltaMovement(getDeltaMovement().normalize().scale(speed));
        turnTowards(pos, getTurnSpeed() * 1.5F);
        this.hasImpulse = true;
    }

    // ================================================================== AI
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MonsterBrainGoal(this));
        this.goalSelector.addGoal(6, new MonsterWanderGoal(this, profile().large ? 28 : 12));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, HuntMonster.class));
        if (isHostile()) {
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                    p -> !SafeZones.isInSafeZone(p.level(), p.position()) && !isLocked()));
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && SafeZones.isInSafeZone(target.level(), target.position())) target = null;
        super.setTarget(target);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ================================================================== quest / home
    @Nullable
    public String getQuestId() {
        return questId;
    }

    public void setQuestId(@Nullable String id) {
        this.questId = id;
    }

    @Nullable
    public BlockPos getHomePos() {
        return homePos;
    }

    public void setHomePos(@Nullable BlockPos pos) {
        this.homePos = pos;
    }

    public boolean hasRoared() {
        return hasRoared;
    }

    public void setHasRoared(boolean b) {
        this.hasRoared = b;
    }

    public Map<UUID, Float> getDamageByPlayer() {
        return damageByPlayer;
    }

    // ================================================================== save / load
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("HCScale", getMonsterScale());
        tag.putByte("HCRank", (byte) getRank().ordinal());
        tag.putInt("HCBroken", getBrokenMask());
        tag.putInt("HCFlags", this.entityData.get(DATA_FLAGS) & (FLAG_CARCASS | FLAG_ENRAGED | FLAG_TIRED));
        tag.putInt("HCCarcass", carcassTicks);
        tag.putInt("HCEnrage", enrageTicks);
        tag.putInt("HCTired", tiredTicks);
        tag.putFloat("HCRage", rageMeter);
        CompoundTag pd = new CompoundTag();
        partDamage.forEach(pd::putFloat);
        tag.put("HCPartDmg", pd);
        if (questId != null) tag.putString("HCQuest", questId);
        if (homePos != null) tag.put("HCHome", NbtUtils.writeBlockPos(homePos));
        tag.putBoolean("HCRoared", hasRoared);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HCScale")) setMonsterScale(tag.getFloat("HCScale"));
        if (tag.contains("HCRank")) {
            float hp = getHealth();
            setRank(Rank.byOrdinal(tag.getByte("HCRank")));
            if (hp > 0) setHealth(hp);
        }
        this.entityData.set(DATA_BROKEN, tag.getInt("HCBroken"));
        this.entityData.set(DATA_FLAGS, tag.getInt("HCFlags"));
        carcassTicks = tag.getInt("HCCarcass");
        enrageTicks = tag.getInt("HCEnrage");
        tiredTicks = tag.getInt("HCTired");
        rageMeter = tag.getFloat("HCRage");
        partDamage.clear();
        CompoundTag pd = tag.getCompound("HCPartDmg");
        for (String k : pd.getAllKeys()) partDamage.put(k, pd.getFloat(k));
        questId = tag.contains("HCQuest") ? tag.getString("HCQuest") : null;
        homePos = tag.contains("HCHome") ? NbtUtils.readBlockPos(tag.getCompound("HCHome")) : null;
        hasRoared = tag.getBoolean("HCRoared");
    }

    /** Helper for subclasses: standard attribute builder. */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder baseAttributes(double health, double speed, double knockbackResist) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.KNOCKBACK_RESISTANCE, knockbackResist)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 0.0);
    }
}
