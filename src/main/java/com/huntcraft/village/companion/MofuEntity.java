package com.huntcraft.village.companion;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.Element;
import com.huntcraft.core.HunterDamage;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.village.HcAnimated;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.meal.MealSystem;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Mofu (cat-folk) companion. Follows its hunter (also across dimensions, see {@link CompanionManager}), fights the
 * hunter's target according to its support type, faints instead of dying and comes back 30 s later.
 * The persistent state lives in the hunter's data; entities are disposable views of that record.
 */
public class MofuEntity extends PathfinderMob implements HcAnimated {
    private static final EntityDataAccessor<Integer> DATA_PATTERN = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GEAR = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<java.util.Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final String[] ACTIONS = {"attack", "attack2", "throw", "horn", "cheer"};
    public static final String[] PATTERN_NAMES = {"white", "ginger", "grey", "black", "tabby", "calico"};

    public final AnimationState idleAnim = new AnimationState();
    public final AnimationState actionAnim = new AnimationState();
    @Nullable
    private String actionName;

    @Nullable
    private UUID ownerId;
    private int recordUid;
    private boolean testMode;
    @Nullable
    private LivingEntity combatTarget;
    private int attackCd;
    private int specialCd;
    private int retargetCd;
    private int regenCd;

    public MofuEntity(EntityType<? extends MofuEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0D).add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ARMOR, 2.0D).add(Attributes.ATTACK_DAMAGE, 3.0D).add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PATTERN, 0);
        this.entityData.define(DATA_TYPE, 0);
        this.entityData.define(DATA_LEVEL, 1);
        this.entityData.define(DATA_GEAR, 0);
        this.entityData.define(DATA_OWNER, java.util.Optional.empty());
    }

    /** Owner UUID as seen by clients (HUD). */
    @Nullable
    public UUID getSyncedOwner() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CombatGoal());
        this.goalSelector.addGoal(2, new FollowOwnerGoal());
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ state
    public int getPattern() {
        return Math.floorMod(this.entityData.get(DATA_PATTERN), MofuData.PATTERNS);
    }

    public MofuType getMofuType() {
        return MofuType.byId(this.entityData.get(DATA_TYPE));
    }

    public int getMofuLevel() {
        return Math.max(1, this.entityData.get(DATA_LEVEL));
    }

    public int getHelm() {
        return this.entityData.get(DATA_GEAR) & 0xF;
    }

    public int getArmorGear() {
        return (this.entityData.get(DATA_GEAR) >> 4) & 0xF;
    }

    public int getRecordUid() {
        return recordUid;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public void setTestMode(boolean test) {
        this.testMode = test;
    }

    /** Client-side preview / test setup without an owner. */
    public void setVisual(int pattern, MofuType type, int level, int helm, int armor) {
        this.entityData.set(DATA_PATTERN, pattern);
        this.entityData.set(DATA_TYPE, type.ordinal());
        this.entityData.set(DATA_LEVEL, Math.max(1, level));
        this.entityData.set(DATA_GEAR, (helm & 0xF) | ((armor & 0xF) << 4));
    }

    public void setup(@Nullable ServerPlayer owner, CompoundTag rec) {
        this.ownerId = owner == null ? null : owner.getUUID();
        this.entityData.set(DATA_OWNER, java.util.Optional.ofNullable(this.ownerId));
        this.recordUid = rec.getInt("uid");
        applyRecord(rec);
        this.setHealth(this.getMaxHealth());
    }

    public void applyRecord(CompoundTag rec) {
        int lv = MofuData.level(rec);
        boolean changed = lv != getMofuLevel();
        setVisual(rec.getInt("pat"), MofuData.type(rec), lv, rec.getInt("helm"), rec.getInt("armor"));
        this.setCustomName(MofuData.name(rec));
        if (changed || this.tickCount < 2) refreshStats();
    }

    public void refreshStats() {
        int lv = getMofuLevel();
        AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(MofuData.maxHealth(lv));
        AttributeInstance ar = this.getAttribute(Attributes.ARMOR);
        if (ar != null) ar.setBaseValue(MofuData.armor(lv) + getArmorGear() * 2.0F);
        if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());
    }

    @Nullable
    public ServerPlayer getOwner() {
        if (ownerId == null || this.level().getServer() == null) return null;
        return this.level().getServer().getPlayerList().getPlayer(ownerId);
    }

    public void setCombatTarget(@Nullable LivingEntity target) {
        this.combatTarget = target;
    }

    @Nullable
    public LivingEntity getCombatTarget() {
        return combatTarget;
    }

    // ------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnim.startIfStopped(this.tickCount);
            return;
        }
        ServerPlayer owner = getOwner();
        if (!testMode) {
            if (owner == null || owner.level() != this.level() || CompanionManager.current(ownerId) != this) {
                this.discard();
                return;
            }
        }
        if (attackCd > 0) attackCd--;
        if (specialCd > 0) specialCd--;
        if (--retargetCd <= 0) {
            retargetCd = 10;
            if (!testMode && owner != null) pickTarget(owner);
            else if (combatTarget != null && !isValidTarget(combatTarget, null)) combatTarget = null;
        }
        // healer horn
        if (getMofuType() == MofuType.HEALER && specialCd <= 0) {
            Player hurt = null;
            for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0D))) {
                if (p.isAlive() && !p.isSpectator() && p.getHealth() < p.getMaxHealth() * 0.5F) {
                    hurt = p;
                    break;
                }
            }
            if (hurt != null || (testMode && combatTarget != null && this.getHealth() < this.getMaxHealth() * 0.5F)) playHorn();
        }
        if (combatTarget == null && --regenCd <= 0) {
            regenCd = 40;
            if (this.getHealth() < this.getMaxHealth()) this.heal(1.0F);
        }
    }

    private void pickTarget(ServerPlayer owner) {
        if (combatTarget != null && isValidTarget(combatTarget, owner)) return;
        combatTarget = null;
        LivingEntity t = CompanionManager.ownerTarget(owner);
        if (t != null && isValidTarget(t, owner)) {
            combatTarget = t;
            return;
        }
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200 && isValidTarget(attacker, owner)) {
            combatTarget = attacker;
            return;
        }
        // nearest alert monster close to the hunter
        AABB box = owner.getBoundingBox().inflate(14.0D, 6.0D, 14.0D);
        double best = Double.MAX_VALUE;
        for (HuntMonster m : this.level().getEntitiesOfClass(HuntMonster.class, box)) {
            if (!isValidTarget(m, owner) || !(m.isHostile() || m.profile().large) || m.getTarget() == null) continue;
            double d = m.distanceToSqr(owner);
            if (d < best) {
                best = d;
                combatTarget = m;
            }
        }
    }

    public boolean isValidTarget(@Nullable LivingEntity e, @Nullable ServerPlayer owner) {
        if (e == null || !e.isAlive() || e == this || e.isRemoved()) return false;
        if (e instanceof Player || e instanceof MofuEntity || e instanceof VillageNpc) return false;
        if (e instanceof HuntMonster m && m.isCarcass()) return false;
        if (owner != null) {
            if (e.distanceToSqr(owner) > 32 * 32 || e.level() != owner.level()) return false;
            boolean fighting = e instanceof HuntMonster || e instanceof Enemy || e instanceof TrainingDummy
                    || CompanionManager.ownerTarget(owner) == e || owner.getLastHurtByMob() == e;
            return fighting;
        }
        return this.distanceToSqr(e) < 40 * 40;
    }

    // ------------------------------------------------------------------ attacks
    private DamageSource source(DamageType_KEY key, @Nullable Entity direct) {
        Holder<DamageType> h = this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key.key);
        ServerPlayer owner = getOwner();
        return new DamageSource(h, direct != null ? direct : this, owner != null ? owner : this);
    }

    /** Small helper enum so damage type keys stay in one place. */
    enum DamageType_KEY {
        MELEE(DamageTypes.MOB_ATTACK), BLAST(DamageTypes.EXPLOSION);
        final net.minecraft.resources.ResourceKey<DamageType> key;

        DamageType_KEY(net.minecraft.resources.ResourceKey<DamageType> key) {
            this.key = key;
        }
    }

    public DamageSource meleeSource() {
        return source(DamageType_KEY.MELEE, this);
    }

    public DamageSource blastSource(Entity bomb) {
        return source(DamageType_KEY.BLAST, bomb);
    }

    private float damageBoost() {
        ServerPlayer owner = getOwner();
        return owner != null && MealSystem.has(owner, com.huntcraft.village.meal.Meals.Skill.CHEER) ? 1.25F : 1.0F;
    }

    /** Squared distance from this mofu to the closest hitbox of the target. */
    public double reachSqr(LivingEntity t) {
        Vec3 me = this.position().add(0, 0.5, 0);
        double d = HuntMonster.boxDistSqr(t.getBoundingBox(), me);
        if (t instanceof HuntMonster m) {
            for (MonsterPart p : m.getMonsterParts()) d = Math.min(d, HuntMonster.boxDistSqr(p.getBoundingBox(), me));
        }
        return d;
    }

    private String nearestPart(HuntMonster m) {
        Vec3 me = this.position().add(0, 0.5, 0);
        String best = "body";
        double bd = HuntMonster.boxDistSqr(m.getBoundingBox(), me);
        for (MonsterPart p : m.getMonsterParts()) {
            double d = HuntMonster.boxDistSqr(p.getBoundingBox(), me);
            if (d < bd) {
                bd = d;
                best = p.def.name;
            }
        }
        return best;
    }

    public void meleeAttack(LivingEntity t) {
        playAction(this.random.nextBoolean() ? 0 : 1);
        float dmg = MofuData.meleeDamage(getMofuLevel(), getMofuType()) * damageBoost();
        if (this.random.nextFloat() < 0.15F) dmg *= 1.5F;
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (t.hurt(meleeSource(), dmg)) {
            onDealtDamage(t, dmg, DamageKind.CUT, Element.NONE);
            this.level().playSound(null, getX(), getY(), getZ(), VillageRegistry.MOFU_ATTACK.get(), SoundSource.NEUTRAL, 0.7F,
                    1.1F + this.random.nextFloat() * 0.3F);
            if (this.level() instanceof ServerLevel sl) {
                Vec3 c = t.getBoundingBox().getCenter();
                Vec3 hit = this.position().add(0, 0.6, 0).add(c.subtract(this.position()).normalize().scale(0.8));
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
            }
        }
        attackCd = getMofuType() == MofuType.FIGHTER ? 14 : 22;
    }

    /** Hook used by melee and bombs: monster part damage / rage / flinch build-up + damage number for the hunter. */
    public void onDealtDamage(LivingEntity t, float dmg, DamageKind kind, Element element) {
        if (t instanceof HuntMonster m && !m.isCarcass()) {
            float applied = dmg * 0.6F * m.defenseMultiplier();
            m.onHunterDamage(nearestPart(m), kind, applied, element, 0, getOwner());
            ServerPlayer owner = getOwner();
            if (owner != null) {
                Vec3 pos = t.position().add(0, t.getBbHeight() * 0.6, 0);
                HunterDamage.sendDamageNumber(owner, pos, applied, 0xFFC864, false, element);
            }
        }
    }

    public void throwBomb(LivingEntity t) {
        playAction(2);
        MofuBomb bomb = new MofuBomb(this.level(), this);
        bomb.setDamage(MofuData.bombDamage(getMofuLevel()) * damageBoost());
        Vec3 aim = t.getBoundingBox().getCenter();
        if (t instanceof HuntMonster m) {
            double bd = Double.MAX_VALUE;
            for (MonsterPart p : m.getMonsterParts()) {
                double d = p.distanceToSqr(this);
                if (d < bd) {
                    bd = d;
                    aim = p.getBoundingBox().getCenter();
                }
            }
        }
        double dx = aim.x - bomb.getX(), dz = aim.z - bomb.getZ(), dy = aim.y - bomb.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        bomb.shoot(dx, dy + dist * 0.22, dz, 0.75F + (float) Math.min(0.5, dist * 0.03), 2.0F);
        this.level().addFreshEntity(bomb);
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.6F, 0.7F);
        attackCd = 48;
    }

    public void playHorn() {
        playAction(3);
        this.level().playSound(null, getX(), getY(), getZ(), VillageRegistry.MOFU_HORN.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
        float frac = MofuData.healFraction(getMofuLevel());
        for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0D))) {
            if (!p.isAlive()) continue;
            p.heal(p.getMaxHealth() * frac);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.4, 0.5, 0.4, 0.05);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.6, 0.6, 0.6, 0.05);
            }
        }
        this.heal(this.getMaxHealth() * 0.3F);
        if (this.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.NOTE, getX(), getY() + 1.3, getZ(), 5, 0.4, 0.3, 0.4, 0.5);
        specialCd = Math.max(300, 700 - getMofuLevel() * 8);
    }

    public void playAction(int idx) {
        if (idx >= 0 && idx < ACTIONS.length) this.level().broadcastEntityEvent(this, (byte) (100 + idx));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= 100 && id < 100 + ACTIONS.length) {
            this.actionName = ACTIONS[id - 100];
            this.actionAnim.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------ damage / faint
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity src = source.getEntity();
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (src instanceof Player || src instanceof MofuEntity) return false;
            if (source.getDirectEntity() instanceof MofuBomb) return false;
        }
        if (source.is(DamageTypeTags.IS_FALL)) amount *= 0.3F;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide) return;
        this.setHealth(1.0F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 20, 0.3, 0.4, 0.3, 0.05);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
        this.level().playSound(null, getX(), getY(), getZ(), VillageRegistry.MOFU_FAINT.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (!testMode) CompanionManager.onFaint(this);
        this.discard();
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        if (e instanceof MofuEntity) return true;
        if (ownerId != null && ownerId.equals(e.getUUID())) return true;
        return super.isAlliedTo(e);
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** Teleports next to the owner (used when far away or stuck). */
    public void teleportNear(Entity owner) {
        for (int i = 0; i < 12; i++) {
            double ang = this.random.nextDouble() * Math.PI * 2;
            double r = 1.5 + this.random.nextDouble() * 1.5;
            double x = owner.getX() + Math.cos(ang) * r, z = owner.getZ() + Math.sin(ang) * r;
            for (int dy = 1; dy >= -2; dy--) {
                double y = Math.floor(owner.getY()) + dy;
                BlockPos below = BlockPos.containing(x, y - 1, z);
                AABB box = this.getDimensions(this.getPose()).makeBoundingBox(x, y, z);
                if (this.level().noCollision(this, box) && this.level().getBlockState(below).isSolidRender(this.level(), below)) {
                    this.moveTo(x, y, z, this.getYRot(), this.getXRot());
                    this.getNavigation().stop();
                    return;
                }
            }
        }
        this.moveTo(owner.getX(), owner.getY(), owner.getZ(), this.getYRot(), this.getXRot());
        this.getNavigation().stop();
    }

    // ------------------------------------------------------------------ hcmodel
    @Override
    public AnimationState hcIdle() {
        return idleAnim;
    }

    @Override
    public AnimationState hcAction() {
        return actionAnim;
    }

    @Override
    @Nullable
    public String hcActionName() {
        return actionName;
    }

    @Override
    public Set<String> hcShown() {
        return shownParts(getMofuType(), getHelm(), getArmorGear());
    }

    public static Set<String> shownParts(MofuType type, int helm, int armor) {
        Set<String> s = new HashSet<>();
        s.add(switch (type) {
            case FIGHTER -> "w_blade";
            case HEALER -> "w_horn";
            case BOMBER -> "w_bomb";
        });
        if (type == MofuType.BOMBER) s.add("pack");
        s.add(switch (armor) {
            case 1 -> "armor_leather";
            case 2 -> "armor_iron";
            default -> "vest";
        });
        if (helm == 1) s.add("bandana");
        else if (helm == 2) s.add("helm_iron");
        return s;
    }

    // ------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putInt("RecordUid", recordUid);
        tag.putBoolean("TestMode", testMode);
        tag.putInt("Pattern", getPattern());
        tag.putInt("MofuType", getMofuType().ordinal());
        tag.putInt("MofuLevel", getMofuLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        recordUid = tag.getInt("RecordUid");
        testMode = tag.getBoolean("TestMode");
        setVisual(tag.getInt("Pattern"), MofuType.byId(tag.getInt("MofuType")), tag.getInt("MofuLevel"), 0, 0);
    }

    // ------------------------------------------------------------------ goals
    /** Attack the current combat target according to the support type. */
    class CombatGoal extends Goal {
        private int repath;

        CombatGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return combatTarget != null && combatTarget.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = combatTarget;
            if (t == null) return;
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            double reach = reachSqr(t);
            boolean bomber = getMofuType() == MofuType.BOMBER;
            if (bomber && reach > 2.5 * 2.5) {
                if (reach > 9 * 9) {
                    if (--repath <= 0) {
                        repath = 10;
                        getNavigation().moveTo(t, 1.2D);
                    }
                } else {
                    getNavigation().stop();
                    if (attackCd <= 0 && hasLineOfSight(t)) throwBomb(t);
                }
                return;
            }
            if (reach > 1.4 * 1.4) {
                if (--repath <= 0) {
                    repath = 8;
                    getNavigation().moveTo(t, 1.3D);
                }
            } else {
                getNavigation().stop();
                if (attackCd <= 0) meleeAttack(t);
            }
        }
    }

    /** Stay close to the hunter. */
    class FollowOwnerGoal extends Goal {
        private int repath;

        FollowOwnerGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = getOwner();
            return o != null && combatTarget == null && distanceToSqr(o) > 5.5 * 5.5;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer o = getOwner();
            return o != null && combatTarget == null && distanceToSqr(o) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            ServerPlayer o = getOwner();
            if (o == null) return;
            getLookControl().setLookAt(o, 10.0F, getMaxHeadXRot());
            if (distanceToSqr(o) > 22 * 22) {
                teleportNear(o);
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                double speed = distanceToSqr(o) > 12 * 12 ? 1.45D : 1.15D;
                getNavigation().moveTo(o, speed);
            }
        }
    }

    /** Degrees helper for previews. */
    public static float wrap(float deg) {
        return Mth.wrapDegrees(deg);
    }
}
