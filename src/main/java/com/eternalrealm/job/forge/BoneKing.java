package com.eternalrealm.job.forge;

import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageRoll;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.job.fx.BoneKingFigure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 骸骨の騎士王: the giant skeleton knight-king the necromancer's finale raises (owner, 2026-11-15: 「周囲から魔素を収束させて
 * 巨大な骸骨の騎士王を出現させて共闘させる」). It rises from the ground where the mana gathered, fights the enemies around its
 * summoner with its greatsword (each strike deals the skill's damage share with the summoner's own stats, credited to
 * the summoner) and crumbles back into mana when its strikes are spent or its time is up. Nothing can hurt it, nobody
 * bumps into it, it never despawns on its own and is never saved. Its body is drawn on the client from
 * {@link BoneKingFigure} (the moments below are synchronised as game times).
 */
public class BoneKing extends PathfinderMob implements OwnableEntity {
    /** Game time it was raised (the client derives its age from it). */
    private static final EntityDataAccessor<Integer> RAISED = SynchedEntityData.defineId(BoneKing.class,
            EntityDataSerializers.INT);
    /** Game time its current swing began, or -1. */
    private static final EntityDataAccessor<Integer> SWING = SynchedEntityData.defineId(BoneKing.class,
            EntityDataSerializers.INT);
    /** Which swing: 0 an overhead cleave, 1 a wide sweep. */
    private static final EntityDataAccessor<Byte> SWING_KIND = SynchedEntityData.defineId(BoneKing.class,
            EntityDataSerializers.BYTE);
    /** Game time it began to crumble, or -1. */
    private static final EntityDataAccessor<Integer> FADING = SynchedEntityData.defineId(BoneKing.class,
            EntityDataSerializers.INT);

    /** Enemies farther than this from the summoner are left alone. */
    private static final double HUNT_RANGE = 14.0;
    private static final double SWING_REACH = 4.2;
    private static final double IMPACT_RADIUS = 3.8;
    private static final double IMPACT_AHEAD = 2.2;
    private static final double LEASH = 40.0;

    @Nullable
    private UUID owner;
    private CombatStats stats;
    private WeaponProfile weapon;
    private DamageType type = DamageType.MAGIC;
    private SkillTheme theme = SkillTheme.SHADOW;
    private double damageScale = 1.0;
    private double multiplier = 1.0;
    private int strikesLeft;
    private int lifeTicks = 400;
    private int nextSwing;
    private int impactAt = -1;
    private int swings;
    @Nullable
    private LivingEntity target;

    public BoneKing(EntityType<? extends BoneKing> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 500.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(RAISED, 0);
        entityData.define(SWING, -1);
        entityData.define(SWING_KIND, (byte) 0);
        entityData.define(FADING, -1);
    }

    /**
     * Raises the king for {@code owner} a few blocks ahead of it (a king it raised earlier crumbles first). Its strikes
     * use the summoner's stats, held weapon and the skill's damage scale of this moment.
     */
    public static void summon(ServerPlayer owner, CombatStats stats, WeaponProfile weapon, DamageType type,
                              SkillTheme theme, double damageScale, SkillEffect.Summon summon) {
        if (!"bone_king".equals(summon.kind()) || weapon == null || !owner.isAlive()) {
            return;
        }
        ServerLevel level = owner.serverLevel();
        for (BoneKing old : level.getEntitiesOfClass(BoneKing.class, owner.getBoundingBox().inflate(64.0),
                king -> owner.getUUID().equals(king.owner))) {
            old.crumble();
        }
        BoneKing king = JobEntities.BONE_KING.get().create(level);
        if (king == null) {
            return;
        }
        double yaw = Math.toRadians(owner.getYRot());
        Vec3 ahead = owner.position().add(-Math.sin(yaw) * 3.5, 0.0, Math.cos(yaw) * 3.5);
        double ground = SkillVisuals.groundY(level, ahead.add(0.0, 1.0, 0.0));
        king.moveTo(ahead.x, ground, ahead.z, owner.getYRot(), 0.0F);
        king.yBodyRot = owner.getYRot();
        king.yHeadRot = owner.getYRot();
        king.owner = owner.getUUID();
        king.stats = stats;
        king.weapon = weapon;
        king.type = type;
        king.theme = theme;
        king.damageScale = damageScale;
        king.multiplier = summon.multiplier();
        king.strikesLeft = summon.strikes();
        king.lifeTicks = summon.ticks();
        king.nextSwing = BoneKingFigure.RISE_TICKS;
        king.entityData.set(RAISED, (int) level.getGameTime());
        level.addFreshEntity(king);
    }

    // ------------------------------------------------------------------ the client's view

    /** Ticks since it was raised (the client's clock is the level's game time). */
    public float age(float partial) {
        return (float) (level().getGameTime() - entityData.get(RAISED)) + partial;
    }

    /** Ticks into the current swing, or -1. */
    public float swingAge(float partial) {
        int at = entityData.get(SWING);
        if (at < 0) {
            return -1.0F;
        }
        float age = (float) (level().getGameTime() - at) + partial;
        return age > BoneKingFigure.SWING_TICKS ? -1.0F : age;
    }

    public int swingKind() {
        return entityData.get(SWING_KIND);
    }

    /** Ticks since it began to crumble, or -1. */
    public float fadeAge(float partial) {
        int at = entityData.get(FADING);
        return at < 0 ? -1.0F : (float) (level().getGameTime() - at) + partial;
    }

    // ------------------------------------------------------------------ the server's fight

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        int age = (int) (level().getGameTime() - entityData.get(RAISED));
        int fading = entityData.get(FADING);
        if (fading >= 0) {
            getNavigation().stop();
            if (level().getGameTime() - fading >= BoneKingFigure.FADE_TICKS) {
                discard();
            }
            return;
        }
        ServerPlayer summoner = owner();
        if (summoner == null || !summoner.isAlive() || summoner.level() != level()
                || summoner.distanceToSqr(this) > LEASH * LEASH || stats == null || age >= lifeTicks) {
            crumble();
            return;
        }
        if (age < BoneKingFigure.RISE_TICKS) {
            getNavigation().stop();
            return;
        }
        if (impactAt >= 0 && age >= impactAt) {
            impactAt = -1;
            strike(summoner);
            if (strikesLeft <= 0) {
                nextSwing = age + BoneKingFigure.SWING_TICKS;
            }
        }
        if (strikesLeft <= 0 && impactAt < 0) {
            if (age >= nextSwing) {
                crumble();
            }
            return;
        }
        if (age % 10 == 0 || target == null || !target.isAlive()) {
            target = choose(summoner);
        }
        if (impactAt >= 0) {
            getNavigation().stop();
            if (target != null) {
                face(target);
            }
            return;
        }
        if (target != null) {
            double distance = Math.sqrt(horizontalDistanceSqr(target));
            face(target);
            if (distance <= SWING_REACH && age >= nextSwing) {
                getNavigation().stop();
                int kind = swings % 2;
                swings++;
                entityData.set(SWING, (int) level().getGameTime());
                entityData.set(SWING_KIND, (byte) kind);
                impactAt = age + BoneKingFigure.IMPACT_TICK;
                nextSwing = age + BoneKingFigure.SWING_INTERVAL;
                level().playSound(null, getX(), getY() + 2.0, getZ(), SoundEvents.WITHER_SKELETON_AMBIENT,
                        SoundSource.PLAYERS, 1.2F, 0.5F);
            } else if (distance > SWING_REACH - 0.8) {
                getNavigation().moveTo(target, 1.15);
            }
        } else {
            // beside the summoner, a little behind on its weapon side
            double yaw = Math.toRadians(summoner.getYRot());
            Vec3 spot = summoner.position().add(-Math.sin(yaw) * -2.0 - Math.cos(yaw) * 2.5, 0.0,
                    Math.cos(yaw) * -2.0 - Math.sin(yaw) * 2.5);
            if (distanceToSqr(spot) > 4.0) {
                getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            } else {
                getNavigation().stop();
                setYRot(summoner.getYRot());
                yBodyRot = summoner.getYRot();
            }
        }
    }

    private double horizontalDistanceSqr(Entity other) {
        double dx = other.getX() - getX();
        double dz = other.getZ() - getZ();
        return dx * dx + dz * dz;
    }

    private void face(Entity other) {
        double dx = other.getX() - getX();
        double dz = other.getZ() - getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** The enemy to fight: what the summoner fights, else the nearest enemy near the summoner. */
    @Nullable
    private LivingEntity choose(ServerPlayer summoner) {
        LivingEntity fought = summoner.getLastHurtMob();
        if (fought != null && fought.isAlive() && CombatHits.isValidTarget(summoner, fought)
                && fought.distanceToSqr(summoner) <= HUNT_RANGE * HUNT_RANGE
                && summoner.tickCount - summoner.getLastHurtMobTimestamp() < 200) {
            return fought;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB area = summoner.getBoundingBox().inflate(HUNT_RANGE, 6.0, HUNT_RANGE);
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity instanceof Enemy && CombatHits.isValidTarget(summoner, entity))) {
            double distance = candidate.distanceToSqr(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** The greatsword lands: every enemy in a disc ahead of the king takes one strike. */
    private void strike(ServerPlayer summoner) {
        strikesLeft--;
        ServerLevel level = (ServerLevel) level();
        double yaw = Math.toRadians(getYRot());
        Vec3 at = position().add(-Math.sin(yaw) * IMPACT_AHEAD, 0.0, Math.cos(yaw) * IMPACT_AHEAD);
        HitFrame frame = new HitFrame(0, HitShape.circle(IMPACT_RADIUS), multiplier, 0.6);
        AABB area = new AABB(at, at).inflate(IMPACT_RADIUS, 3.0, IMPACT_RADIUS);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != this && CombatHits.isValidTarget(summoner, entity))) {
            double dx = victim.getX() - at.x;
            double dz = victim.getZ() - at.z;
            if (dx * dx + dz * dz > IMPACT_RADIUS * IMPACT_RADIUS) {
                continue;
            }
            DamageRoll roll = CombatHits.roll(summoner, stats, weapon, frame, damageScale, type,
                    getRandom().nextDouble());
            CombatHits.areaHit(summoner, victim, roll, type);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.6, 0.1, 0.6, 0.0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 40, IMPACT_RADIUS * 0.45, 0.15,
                IMPACT_RADIUS * 0.45, 0.04);
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.5, at.z, 12, 1.2, 0.4, 1.2, 0.03);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
                    at.x, at.y + 0.1, at.z, 30, IMPACT_RADIUS * 0.4, 0.1, IMPACT_RADIUS * 0.4, 0.15);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.55F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.5F, 0.8F);
    }

    /** Begins to crumble back into mana (gone after {@link BoneKingFigure#FADE_TICKS}). */
    void crumble() {
        if (level().isClientSide() || entityData.get(FADING) >= 0) {
            return;
        }
        entityData.set(FADING, (int) level().getGameTime());
        impactAt = -1;
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 2.0, getZ(), 40, 0.8, 1.4, 0.8, 0.04);
        level.playSound(null, getX(), getY() + 2.0, getZ(), SoundEvents.SKELETON_DEATH, SoundSource.PLAYERS, 1.0F,
                0.5F);
    }

    @Nullable
    private ServerPlayer owner() {
        if (owner == null || !(level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(owner);
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return owner;
    }

    // ------------------------------------------------------------------ an untouchable spirit

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SKELETON_STEP, 0.6F, 0.5F);
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }
}
