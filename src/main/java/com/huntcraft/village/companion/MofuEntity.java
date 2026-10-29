package com.huntcraft.village.companion;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.Element;
import com.huntcraft.core.HunterDamage;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.registry.HCEffects;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mofu (cat-folk) companion. Follows its hunter (also across dimensions, see {@link CompanionManager}), fights the
 * hunter's target according to its support type and skills, faints instead of dying and comes back 30 s later.
 * The persistent state lives in the hunter's data; entities are disposable views of that record.
 */
public class MofuEntity extends PathfinderMob implements HcAnimated {
    private static final EntityDataAccessor<Integer> DATA_PATTERN = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    /** Gear tiers + 1 packed in 3 nibbles: weapon | helm << 4 | armor << 8 (0 = none). */
    private static final EntityDataAccessor<Integer> DATA_GEAR = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(MofuEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final String[] ACTIONS = {"attack", "attack2", "throw", "horn", "cheer", "dance"};
    public static final String[] PATTERN_NAMES = {"white", "ginger", "grey", "black", "tabby", "calico"};

    public final AnimationState idleAnim = new AnimationState();
    public final AnimationState actionAnim = new AnimationState();
    @Nullable
    private String actionName;

    @Nullable
    private UUID ownerId;
    /** Direct owner reference (GameTests use fake players that are not in the player list). */
    @Nullable
    private ServerPlayer ownerRef;
    private int recordUid;
    /** Not managed by {@link CompanionManager} (GameTests, previews). */
    private boolean testMode;
    @Nullable
    private LivingEntity combatTarget;
    private int attackCd;
    private int specialCd;
    private int flashCd;
    private int reviveCd;
    private int retargetCd;
    private int regenCd;
    private int comboTicks = -1;
    private int danceTicks;
    private boolean gutsUsed;

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
        this.entityData.define(DATA_OWNER, Optional.empty());
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

    /** Equipped tier index of a slot (-1 = none), from synced data. */
    public int getGear(MofuGear.Slot slot) {
        int shift = slot.ordinal() * 4;
        return ((this.entityData.get(DATA_GEAR) >> shift) & 0xF) - 1;
    }

    public boolean hasSkill(MofuSkill s) {
        return s.has(getMofuType(), getMofuLevel());
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

    /** Visual / stat setup without a record (tests, previews). */
    public void setVisual(int pattern, MofuType type, int level, int weapon, int helm, int armor) {
        this.entityData.set(DATA_PATTERN, pattern);
        this.entityData.set(DATA_TYPE, type.ordinal());
        this.entityData.set(DATA_LEVEL, Math.max(1, level));
        int packed = ((weapon + 1) & 0xF) | (((helm + 1) & 0xF) << 4) | (((armor + 1) & 0xF) << 8);
        this.entityData.set(DATA_GEAR, packed);
    }

    public void setup(@Nullable ServerPlayer owner, CompoundTag rec) {
        this.ownerId = owner == null ? null : owner.getUUID();
        this.ownerRef = owner;
        this.entityData.set(DATA_OWNER, Optional.ofNullable(this.ownerId));
        this.recordUid = rec.getInt("uid");
        applyRecord(rec);
        refreshStats();
        this.setHealth(this.getMaxHealth());
    }

    public void applyRecord(CompoundTag rec) {
        int lv = MofuData.level(rec);
        int gearBefore = this.entityData.get(DATA_GEAR);
        boolean changed = lv != getMofuLevel() || MofuData.type(rec) != getMofuType();
        setVisual(rec.getInt("pat"), MofuData.type(rec), lv, MofuGear.equipped(rec, MofuGear.Slot.WEAPON),
                MofuGear.equipped(rec, MofuGear.Slot.HELM), MofuGear.equipped(rec, MofuGear.Slot.ARMOR));
        this.setCustomName(MofuData.name(rec));
        this.setCustomNameVisible(true);
        if (changed || gearBefore != this.entityData.get(DATA_GEAR) || this.tickCount < 2) refreshStats();
    }

    public void refreshStats() {
        int lv = getMofuLevel();
        MofuType t = getMofuType();
        AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(MofuData.maxHealth(t, lv, getGear(MofuGear.Slot.ARMOR)));
        AttributeInstance ar = this.getAttribute(Attributes.ARMOR);
        if (ar != null) ar.setBaseValue(MofuData.armor(t, lv, getGear(MofuGear.Slot.HELM), getGear(MofuGear.Slot.ARMOR)));
        if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());
    }

    @Nullable
    public ServerPlayer getOwner() {
        if (ownerRef != null && !ownerRef.isRemoved()) return ownerRef;
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
        if (!testMode && (owner == null || owner.level() != this.level() || CompanionManager.current(ownerId) != this)) {
            this.discard();
            return;
        }
        if (attackCd > 0) attackCd--;
        if (specialCd > 0) specialCd--;
        if (flashCd > 0) flashCd--;
        if (reviveCd > 0) reviveCd--;
        if (--retargetCd <= 0) {
            retargetCd = 10;
            if (owner != null && !testMode) pickTarget(owner);
            else if (combatTarget != null && !isValidTarget(combatTarget, null)) combatTarget = null;
        }
        // delayed second strike (COMBO) and blade dance flurry
        if (comboTicks >= 0 && --comboTicks < 0 && combatTarget != null && reachSqr(combatTarget) < 2.5 * 2.5) strike(combatTarget, 0.7F, 1);
        if (danceTicks > 0) {
            danceTicks--;
            if (danceTicks % 4 == 0 && combatTarget != null && reachSqr(combatTarget) < 2.5 * 2.5) strike(combatTarget, 0.6F, 1);
        }
        if (getMofuType() == MofuType.HEALER) healerTick();
        if (combatTarget == null && --regenCd <= 0) {
            regenCd = 40;
            if (this.getHealth() < this.getMaxHealth()) this.heal(1.0F);
        }
    }

    private void healerTick() {
        Player hurt = null;
        Player critical = null;
        for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0D))) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p.getHealth() < p.getMaxHealth() * 0.25F) critical = p;
            if (p.getHealth() < p.getMaxHealth() * 0.5F || (hasSkill(MofuSkill.SOOTHING) && hasBadStatus(p))) hurt = p;
        }
        if (critical != null && hasSkill(MofuSkill.REVIVAL) && reviveCd <= 0) {
            playHorn(true);
            return;
        }
        if (specialCd > 0) return;
        if (hurt != null || (testMode && combatTarget != null && this.getHealth() < this.getMaxHealth() * 0.5F)) playHorn(false);
    }

    private static boolean hasBadStatus(Player p) {
        for (MobEffect e : badEffects()) if (p.hasEffect(e)) return true;
        return false;
    }

    private static MobEffect[] badEffects() {
        return new MobEffect[]{HCEffects.FIREBLIGHT.get(), HCEffects.ICEBLIGHT.get(), HCEffects.THUNDERBLIGHT.get(), HCEffects.WATERBLIGHT.get(),
                HCEffects.DRAGONBLIGHT.get(), HCEffects.POISON.get(), MobEffects.POISON};
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
            if (e.level() != owner.level() || e.distanceToSqr(owner) > 32 * 32) return false;
            return e instanceof HuntMonster || e instanceof Enemy || e instanceof TrainingDummy
                    || CompanionManager.ownerTarget(owner) == e || owner.getLastHurtByMob() == e;
        }
        return this.distanceToSqr(e) < 40 * 40;
    }

    // ------------------------------------------------------------------ attacks
    private DamageSource source(ResourceKey<DamageType> key, @Nullable Entity direct) {
        Holder<DamageType> h = this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        ServerPlayer owner = getOwner();
        return new DamageSource(h, direct != null ? direct : this, owner != null ? owner : this);
    }

    public DamageSource meleeSource() {
        return source(DamageTypes.MOB_ATTACK, this);
    }

    public DamageSource blastSource(Entity bomb) {
        return source(DamageTypes.EXPLOSION, bomb);
    }

    /** Meal "cheer" skill, fighter rampage and similar multipliers. */
    private float damageBoost() {
        float k = 1.0F;
        ServerPlayer owner = getOwner();
        if (owner != null && MealSystem.has(owner, com.huntcraft.village.meal.Ingredients.Skill.CHEER)) k *= 1.25F;
        if (owner != null && hasSkill(MofuSkill.RAMPAGE) && owner.getHealth() < owner.getMaxHealth() * 0.5F) k *= 1.2F;
        return k;
    }

    public Element weaponElement() {
        int w = getGear(MofuGear.Slot.WEAPON);
        return w < 0 ? Element.NONE : MofuGear.Tier.byIndex(w).element;
    }

    /** Elemental bonus damage against a target (scaled by the monster's weakness stars). */
    public float elementBonus(LivingEntity t) {
        int w = getGear(MofuGear.Slot.WEAPON);
        if (w < 0) return 0;
        MofuGear.Tier tier = MofuGear.Tier.byIndex(w);
        if (tier.element == Element.NONE || tier.elemVal <= 0) return 0;
        int stars = t instanceof HuntMonster m ? m.profile().weakness.getOrDefault(tier.element, 0) : 1;
        return tier.elemVal * (0.4F + 0.35F * stars);
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
        strike(t, 1.0F, 0);
        if (hasSkill(MofuSkill.COMBO) && this.random.nextFloat() < 0.3F) comboTicks = 5;
        if (hasSkill(MofuSkill.BLADE_DANCE) && specialCd <= 0) {
            danceTicks = 20;
            specialCd = 400;
            playAction(5);
        }
        attackCd = getMofuType() == MofuType.FIGHTER ? 14 : 22;
    }

    /** One melee hit (mult = damage multiplier). */
    private void strike(LivingEntity t, float mult, int anim) {
        float dmg = MofuData.meleeDamage(getMofuType(), getMofuLevel(), getGear(MofuGear.Slot.WEAPON)) * mult * damageBoost();
        if (this.random.nextFloat() < 0.15F) dmg *= 1.5F;
        dmg += elementBonus(t) * mult;
        this.swing(InteractionHand.MAIN_HAND);
        t.invulnerableTime = 0;
        if (t.hurt(meleeSource(), dmg)) {
            onDealtDamage(t, dmg, DamageKind.CUT, weaponElement());
            this.level().playSound(null, getX(), getY(), getZ(), VillageRegistry.MOFU_ATTACK.get(), SoundSource.NEUTRAL, 0.7F,
                    1.1F + this.random.nextFloat() * 0.3F);
            if (this.level() instanceof ServerLevel sl) {
                Vec3 c = t.getBoundingBox().getCenter();
                Vec3 hit = this.position().add(0, 0.6, 0).add(c.subtract(this.position()).normalize().scale(0.8));
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
                if (weaponElement() == Element.FIRE) sl.sendParticles(ParticleTypes.FLAME, hit.x, hit.y, hit.z, 4, 0.2, 0.2, 0.2, 0.02);
                else if (weaponElement() == Element.THUNDER) sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 6, 0.2, 0.2, 0.2, 0.1);
                else if (weaponElement() == Element.ICE) sl.sendParticles(ParticleTypes.SNOWFLAKE, hit.x, hit.y, hit.z, 5, 0.2, 0.2, 0.2, 0.02);
                else if (weaponElement() == Element.DRAGON) sl.sendParticles(ParticleTypes.DRAGON_BREATH, hit.x, hit.y, hit.z, 5, 0.2, 0.2, 0.2, 0.01);
                else if (weaponElement() == Element.WATER) sl.sendParticles(ParticleTypes.SPLASH, hit.x, hit.y, hit.z, 8, 0.2, 0.2, 0.2, 0.05);
            }
        }
        if (anim > 0) playAction(this.random.nextBoolean() ? 0 : 1);
    }

    /** Hook used by melee and bombs: monster part damage / rage / flinch build-up + damage number for the hunter. */
    public void onDealtDamage(LivingEntity t, float dmg, DamageKind kind, Element element) {
        if (t instanceof HuntMonster m && !m.isCarcass()) {
            float applied = dmg * 0.6F * m.defenseMultiplier();
            m.onHunterDamage(nearestPart(m), kind, applied, element, 0, getOwner());
            ServerPlayer owner = getOwner();
            if (owner != null) {
                Vec3 pos = t.position().add(0, t.getBbHeight() * 0.6, 0);
                int color = element == Element.NONE ? 0xFFC864 : element.color;
                HunterDamage.sendDamageNumber(owner, pos, applied, color, false, element);
            }
        }
    }

    public void throwBomb(LivingEntity t) {
        playAction(2);
        int n = hasSkill(MofuSkill.CARPET_BOMB) && this.random.nextFloat() < 0.35F ? 3 : 1;
        boolean flash = hasSkill(MofuSkill.FLASH_BARREL) && flashCd <= 0 && t instanceof HuntMonster hm && !hm.isBlinded();
        for (int i = 0; i < n; i++) launchBomb(t, (i - (n - 1) / 2.0F) * 1.6F, flash && i == 0);
        if (flash) flashCd = 1200;
        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.6F, 0.7F);
        attackCd = 48;
    }

    private void launchBomb(LivingEntity t, float sideOffset, boolean flash) {
        MofuBomb bomb = new MofuBomb(this.level(), this);
        bomb.setDamage(MofuData.bombDamage(getMofuType(), getMofuLevel(), getGear(MofuGear.Slot.WEAPON)) * damageBoost() + elementBonus(t));
        bomb.setElement(weaponElement());
        bomb.setRadius(hasSkill(MofuSkill.BIG_BARREL) ? 3.6 : 2.6);
        bomb.setFlash(flash);
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
        Vec3 side = new Vec3(aim.z - getZ(), 0, getX() - aim.x);
        if (side.lengthSqr() > 1e-4) aim = aim.add(side.normalize().scale(sideOffset));
        double dx = aim.x - bomb.getX(), dz = aim.z - bomb.getZ(), dy = aim.y - bomb.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        bomb.shoot(dx, dy + dist * 0.22, dz, 0.75F + (float) Math.min(0.5, dist * 0.03), 2.0F);
        this.level().addFreshEntity(bomb);
    }

    public void playHorn() {
        playHorn(false);
    }

    /** Healing horn. {@code revival} = full heal (REVIVAL skill). */
    public void playHorn(boolean revival) {
        playAction(3);
        this.level().playSound(null, getX(), getY(), getZ(), VillageRegistry.MOFU_HORN.get(), SoundSource.NEUTRAL, 1.2F, revival ? 1.2F : 1.0F);
        float frac = revival ? 1.0F : MofuData.healFraction(getMofuType(), getMofuLevel());
        for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(10.0D))) {
            if (!p.isAlive() || p.isSpectator()) continue;
            p.heal(p.getMaxHealth() * frac);
            if (hasSkill(MofuSkill.SOOTHING)) {
                for (MobEffect e : badEffects()) p.removeEffect(e);
            }
            if (hasSkill(MofuSkill.ENCORE)) CompanionManager.grantEncore(p, 600);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), revival ? 14 : 6, 0.4, 0.5, 0.4, 0.05);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.6, 0.6, 0.6, 0.05);
            }
        }
        this.heal(this.getMaxHealth() * 0.3F);
        if (this.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.NOTE, getX(), getY() + 1.3, getZ(), 5, 0.4, 0.3, 0.4, 0.5);
        if (revival) reviveCd = 6000;
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
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && hasSkill(MofuSkill.GUTS) && !gutsUsed) {
            gutsUsed = true;
            this.setHealth(Math.max(1.0F, this.getMaxHealth() * 0.2F));
            this.dead = false;
            playAction(4);
            if (this.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 0.6, getZ(), 16, 0.3, 0.4, 0.3, 0.2);
            return;
        }
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
        return shownParts(getMofuType(), getGear(MofuGear.Slot.WEAPON), getGear(MofuGear.Slot.HELM), getGear(MofuGear.Slot.ARMOR));
    }

    /** Bones of the mofu + mofu_gear models shown for a companion. */
    public static Set<String> shownParts(MofuType type, int weapon, int helm, int armor) {
        Set<String> s = new HashSet<>();
        s.add("scarf");
        if (weapon >= 0) {
            s.add(switch (type) {
                case FIGHTER -> "w_blade";
                case HEALER -> "w_horn";
                case BOMBER -> "w_bomb";
            });
            if (type == MofuType.BOMBER) s.add("pack");
        }
        if (helm >= 0) s.add("helm");
        if (armor >= 0) s.add("armor");
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
        tag.putInt("MofuGear", this.entityData.get(DATA_GEAR));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        recordUid = tag.getInt("RecordUid");
        testMode = tag.getBoolean("TestMode");
        this.entityData.set(DATA_PATTERN, tag.getInt("Pattern"));
        this.entityData.set(DATA_TYPE, tag.getInt("MofuType"));
        this.entityData.set(DATA_LEVEL, Math.max(1, tag.getInt("MofuLevel")));
        this.entityData.set(DATA_GEAR, tag.getInt("MofuGear"));
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
}
