package com.huntcraft.entity.misc;

import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterDamage;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.registry.HCEntities;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Generic monster projectile. The {@link Kind} decides visuals (renderer texture + particle trail);
 * damage/element/explosion come from the {@link ProjectileSpec} on the server.
 */
public class MonsterProjectile extends Projectile {
    public enum Kind {
        FIREBALL, ICE_SHARD, THUNDER_ORB, WATER_JET, DRAGON_ORB, ROCK, POISON_SPIT, MUD, SPIKE, FROST_ORB, LAVA_BLOB
    }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(MonsterProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(MonsterProjectile.class, EntityDataSerializers.FLOAT);

    private float damage = 8;
    private Element element = Element.NONE;
    private float explosionRadius = 0;
    private float gravity = 0;
    private int lifetime = 80;
    private int lingerTicks = 0;
    private boolean homing = false;
    @Nullable
    private LivingEntity homingTarget;

    public MonsterProjectile(EntityType<? extends MonsterProjectile> type, Level level) {
        super(type, level);
    }

    public MonsterProjectile(Level level, HuntMonster owner, ProjectileSpec spec, Vec3 dir, @Nullable LivingEntity target) {
        super(HCEntities.MONSTER_PROJECTILE.get(), level);
        this.setOwner(owner);
        this.entityData.set(DATA_KIND, spec.kind.ordinal());
        this.entityData.set(DATA_SIZE, spec.size * owner.getMonsterScale());
        this.damage = spec.damage * owner.damageMultiplier();
        this.element = spec.element;
        this.explosionRadius = spec.explosionRadius * owner.getMonsterScale();
        this.gravity = spec.gravity;
        this.lifetime = spec.lifetime;
        this.lingerTicks = spec.lingerTicks;
        this.homing = spec.homing;
        this.homingTarget = target;
        this.setDeltaMovement(dir.scale(spec.speed));
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, 0);
        this.entityData.define(DATA_SIZE, 1.0F);
    }

    public Kind getKind() {
        int k = this.entityData.get(DATA_KIND);
        Kind[] v = Kind.values();
        return v[Math.max(0, Math.min(v.length - 1, k))];
    }

    public float getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getSize());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) refreshDimensions();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && this.tickCount > lifetime) {
            impact(position());
            return;
        }
        if (homing && homingTarget != null && homingTarget.isAlive() && tickCount < 40) {
            Vec3 want = homingTarget.position().add(0, homingTarget.getBbHeight() * 0.5, 0).subtract(position()).normalize().scale(getDeltaMovement().length());
            setDeltaMovement(getDeltaMovement().lerp(want, 0.08));
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !level().isClientSide) {
            this.onHit(hit);
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (gravity != 0) setDeltaMovement(v.x * 0.99, v.y - gravity, v.z * 0.99);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (level().isClientSide) spawnTrail();
    }

    private void spawnTrail() {
        ParticleOptions p = trailParticle(getKind());
        float s = getSize();
        for (int i = 0; i < 2 + (int) (s * 2); i++) {
            level().addParticle(p, getX() + (random.nextDouble() - 0.5) * s * 0.6, getY() + getBbHeight() * 0.5 + (random.nextDouble() - 0.5) * s * 0.6,
                    getZ() + (random.nextDouble() - 0.5) * s * 0.6, 0, 0.01, 0);
        }
    }

    public static ParticleOptions trailParticle(Kind kind) {
        return switch (kind) {
            case FIREBALL, LAVA_BLOB -> ParticleTypes.FLAME;
            case ICE_SHARD, FROST_ORB -> ParticleTypes.SNOWFLAKE;
            case THUNDER_ORB -> ParticleTypes.ELECTRIC_SPARK;
            case WATER_JET -> ParticleTypes.SPLASH;
            case DRAGON_ORB -> ParticleTypes.DRAGON_BREATH;
            case ROCK -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case POISON_SPIT -> new DustParticleOptions(new Vector3f(0.6F, 0.2F, 0.8F), 1.2F);
            case MUD -> new DustParticleOptions(new Vector3f(0.4F, 0.28F, 0.15F), 1.5F);
            case SPIKE -> ParticleTypes.CRIT;
        };
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e instanceof HuntMonster || e instanceof MonsterPart || e instanceof MonsterProjectile) return false;
        return super.canHitEntity(e) && e instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity e = result.getEntity();
        if (explosionRadius <= 0 && e instanceof LivingEntity le && getOwner() instanceof LivingEntity owner) {
            MonsterDamage.deal(this, owner, le, damage, element, 0.6F, 0);
        }
        impact(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        impact(result.getLocation());
    }

    private void impact(Vec3 pos) {
        if (level().isClientSide || isRemoved()) return;
        ServerLevel sl = (ServerLevel) level();
        ParticleOptions trail = trailParticle(getKind());
        if (explosionRadius > 0) {
            sl.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 2, explosionRadius * 0.3, 0.3, explosionRadius * 0.3, 0);
            sl.sendParticles(trail, pos.x, pos.y, pos.z, 40, explosionRadius * 0.5, 0.5, explosionRadius * 0.5, 0.05);
            sl.playSound(null, pos.x, pos.y, pos.z, HCSounds.EXPLOSION_SMALL.get(), SoundSource.HOSTILE, 1.5F, 0.9F + random.nextFloat() * 0.2F);
            if (getOwner() instanceof LivingEntity owner) {
                for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(explosionRadius), this::canHitEntity)) {
                    if (le.position().distanceTo(pos) <= explosionRadius + le.getBbWidth()) {
                        MonsterDamage.deal(this, owner, le, damage, element, 1.0F, 0);
                    }
                }
            }
        } else {
            sl.sendParticles(trail, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.05);
        }
        if (lingerTicks > 0 && getOwner() instanceof HuntMonster owner) {
            HazardEntity.Kind hk = switch (getKind()) {
                case ICE_SHARD, FROST_ORB -> HazardEntity.Kind.ICE_FIELD;
                case POISON_SPIT -> HazardEntity.Kind.POISON_CLOUD;
                case DRAGON_ORB -> HazardEntity.Kind.DRAGON_PILLAR;
                default -> HazardEntity.Kind.FIRE_POOL;
            };
            HazardEntity.spawn(owner, pos, hk, Math.max(1.5F, explosionRadius), 0, lingerTicks, damage * 0.25F, element);
        }
        discard();
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", this.entityData.get(DATA_KIND));
        tag.putFloat("Damage", damage);
        tag.putInt("Life", lifetime);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_KIND, tag.getInt("Kind"));
        damage = tag.getFloat("Damage");
        lifetime = tag.getInt("Life");
    }
}
