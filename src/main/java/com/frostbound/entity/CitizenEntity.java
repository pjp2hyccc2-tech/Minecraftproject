package com.frostbound.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Visible stand-in for one simulated citizen near a player (level of detail: only citizens close to a
 * player get an entity; everybody else lives only in the simulation). Not saved, cannot be hurt; the
 * citizen tracker tells it where to walk.
 */
public class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    /** 0 normal, 1 cold, 2 sick, 3 hungry. */
    private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);

    public int citizenId = -1;
    private BlockPos anchor;
    private int wander;
    private int repath;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LOOK, 0);
        entityData.define(MOOD, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public int look() {
        return entityData.get(LOOK);
    }

    public void setLook(int look) {
        entityData.set(LOOK, Math.floorMod(look, 4));
    }

    public int mood() {
        return entityData.get(MOOD);
    }

    public void setMood(int mood) {
        if (mood() != mood) {
            entityData.set(MOOD, mood);
        }
    }

    /** Where this citizen should be, and how far it may wander around it. */
    public void setAnchor(BlockPos pos, int wanderRadius) {
        if (anchor == null || !anchor.equals(pos)) {
            anchor = pos;
            repath = 0;
        }
        wander = wanderRadius;
    }

    public BlockPos anchor() {
        return anchor;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (anchor == null || --repath > 0) {
            return;
        }
        repath = 40 + random.nextInt(60);
        double dx = anchor.getX() + 0.5 - getX();
        double dz = anchor.getZ() + 0.5 - getZ();
        double dist2 = dx * dx + dz * dz;
        double speed = mood() == 1 || mood() == 2 ? 0.7 : 0.9;
        if (dist2 > (wander + 2) * (double) (wander + 2)) {
            getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, speed);
        } else if (wander > 0 && random.nextInt(3) == 0) {
            int ox = random.nextInt(wander * 2 + 1) - wander;
            int oz = random.nextInt(wander * 2 + 1) - wander;
            getNavigation().moveTo(anchor.getX() + ox + 0.5, anchor.getY(), anchor.getZ() + oz + 0.5, 0.6);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 15 == 0) {
            int m = mood();
            if (m == 1) {
                level().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY() + 1.9, getZ(), 0, 0.02, 0);
            } else if (m == 2 && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SNEEZE, getX(), getY() + 1.6, getZ(), 0, 0, 0);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }
}
