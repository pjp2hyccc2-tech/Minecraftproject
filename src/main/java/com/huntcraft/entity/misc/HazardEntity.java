package com.huntcraft.entity.misc;

import com.huntcraft.core.CameraShake;
import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterDamage;
import com.huntcraft.registry.HCEntities;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Area hazard: shows a warning during warm-up, then triggers (strike/explosion) and optionally lingers.
 * Visuals are pure particles (no model).
 */
public class HazardEntity extends Entity {
    public enum Kind {
        /** Lightning bolt after warm-up. */
        LIGHTNING,
        /** Burning ground. */
        FIRE_POOL,
        /** Freezing ground (slows). */
        ICE_FIELD,
        /** Big fiery impact from the sky after warm-up. */
        METEOR,
        /** Falling rocks after warm-up. */
        ROCKFALL,
        /** Water geyser launching upward. */
        GEYSER,
        /** Dragon energy pillar. */
        DRAGON_PILLAR,
        /** Lingering poison gas. */
        POISON_CLOUD,
        /** Ice spikes erupting from the ground after warm-up. */
        ICE_SPIKES
    }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(HazardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(HazardEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_WARMUP = SynchedEntityData.defineId(HazardEntity.class, EntityDataSerializers.INT);

    private int active = 40;
    private float damage = 10;
    private Element element = Element.NONE;
    private UUID ownerId;
    private final Map<Integer, Integer> lastHit = new HashMap<>();

    public HazardEntity(EntityType<? extends HazardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HazardEntity spawn(HuntMonster owner, Vec3 pos, Kind kind, float radius, int warmup, int active, float damage, Element element) {
        HazardEntity h = new HazardEntity(HCEntities.HAZARD.get(), owner.level());
        h.setPos(pos.x, pos.y, pos.z);
        h.entityData.set(DATA_KIND, kind.ordinal());
        h.entityData.set(DATA_RADIUS, radius);
        h.entityData.set(DATA_WARMUP, warmup);
        h.active = active;
        h.damage = damage;
        h.element = element;
        h.ownerId = owner.getUUID();
        owner.level().addFreshEntity(h);
        return h;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, 0);
        this.entityData.define(DATA_RADIUS, 2.0F);
        this.entityData.define(DATA_WARMUP, 0);
    }

    public Kind getKind() {
        Kind[] v = Kind.values();
        return v[Math.max(0, Math.min(v.length - 1, this.entityData.get(DATA_KIND)))];
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public int getWarmup() {
        return this.entityData.get(DATA_WARMUP);
    }

    @Override
    public void tick() {
        super.tick();
        int warm = getWarmup();
        if (level().isClientSide) {
            clientParticles(warm);
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (tickCount < warm) return;
        if (tickCount == warm) trigger(sl);
        if (lingers()) {
            if (tickCount % 10 == 0) damageInside(sl, damage, 0.1F);
        }
        if (tickCount >= warm + Math.max(1, active)) discard();
    }

    private boolean lingers() {
        Kind k = getKind();
        return k == Kind.FIRE_POOL || k == Kind.ICE_FIELD || k == Kind.POISON_CLOUD || k == Kind.DRAGON_PILLAR;
    }

    private void trigger(ServerLevel sl) {
        Kind k = getKind();
        float r = getRadius();
        switch (k) {
            case LIGHTNING -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    bolt.moveTo(getX(), getY(), getZ());
                    bolt.setVisualOnly(true);
                    sl.addFreshEntity(bolt);
                }
                sl.playSound(null, getX(), getY(), getZ(), HCSounds.THUNDER_CRACK.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
                damageInside(sl, damage, 1.2F);
            }
            case METEOR, ROCKFALL -> {
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
                sl.sendParticles(k == Kind.METEOR ? ParticleTypes.FLAME : ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 60, r * 0.5, 0.5, r * 0.5, 0.1);
                sl.playSound(null, getX(), getY(), getZ(), HCSounds.EXPLOSION_SMALL.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
                CameraShake.shake(sl, position(), r * 4, 0.8F, 12);
                damageInside(sl, damage, 1.8F);
            }
            case GEYSER -> {
                sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 120, r * 0.4, 1.5, r * 0.4, 0.3);
                sl.playSound(null, getX(), getY(), getZ(), HCSounds.WATER_JET.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
                damageInside(sl, damage, 2.5F);
            }
            case ICE_SPIKES -> {
                sl.sendParticles(ParticleTypes.ITEM_SNOWBALL, getX(), getY() + 1, getZ(), 80, r * 0.5, 1.0, r * 0.5, 0.2);
                sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1, getZ(), 60, r * 0.5, 1.2, r * 0.5, 0.05);
                sl.playSound(null, getX(), getY(), getZ(), HCSounds.ICE_BREATH.get(), SoundSource.HOSTILE, 2.0F, 1.4F);
                damageInside(sl, damage, 1.5F);
            }
            default -> damageInside(sl, damage, 0.2F);
        }
    }

    private void damageInside(ServerLevel sl, float dmg, float knock) {
        Entity owner = ownerId == null ? null : sl.getEntity(ownerId);
        LivingEntity attacker = owner instanceof LivingEntity le ? le : null;
        float r = getRadius();
        AABB box = new AABB(getX() - r, getY() - 1, getZ() - r, getX() + r, getY() + (getKind() == Kind.DRAGON_PILLAR || getKind() == Kind.LIGHTNING ? 8 : 3), getZ() + r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof HuntMonster) && e.isAlive())) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            if (dx * dx + dz * dz > (r + e.getBbWidth() * 0.5) * (r + e.getBbWidth() * 0.5)) continue;
            Integer last = lastHit.get(e.getId());
            if (last != null && tickCount - last < 10) continue;
            lastHit.put(e.getId(), tickCount);
            if (attacker != null) {
                MonsterDamage.deal(this, attacker, e, dmg, element, knock, 0);
            } else {
                e.hurt(damageSources().magic(), dmg);
            }
        }
    }

    private void clientParticles(int warm) {
        float r = getRadius();
        Kind k = getKind();
        if (tickCount < warm) {
            // warning ring
            int n = 10;
            for (int i = 0; i < n; i++) {
                double a = (tickCount * 0.15) + i * Math.PI * 2 / n;
                level().addParticle(warningParticle(k), getX() + Math.cos(a) * r, getY() + 0.15, getZ() + Math.sin(a) * r, 0, 0.02, 0);
            }
            if (k == Kind.METEOR && tickCount > warm - 20) {
                double h = (warm - tickCount) * 1.5;
                level().addParticle(ParticleTypes.LAVA, getX(), getY() + h, getZ(), 0, -0.5, 0);
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + h, getZ(), 0, 0, 0);
            }
            return;
        }
        if (!lingers()) return;
        for (int i = 0; i < 4 + r * 2; i++) {
            double dx = (random.nextDouble() * 2 - 1) * r, dz = (random.nextDouble() * 2 - 1) * r;
            if (dx * dx + dz * dz > r * r) continue;
            double y = k == Kind.DRAGON_PILLAR ? random.nextDouble() * 6 : 0.1 + random.nextDouble() * 0.4;
            level().addParticle(lingerParticle(k), getX() + dx, getY() + y, getZ() + dz, 0, 0.03, 0);
        }
    }

    private static ParticleOptions warningParticle(Kind k) {
        return switch (k) {
            case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
            case ICE_FIELD, ICE_SPIKES -> ParticleTypes.SNOWFLAKE;
            case GEYSER -> ParticleTypes.BUBBLE_POP;
            case DRAGON_PILLAR -> ParticleTypes.DRAGON_BREATH;
            case POISON_CLOUD -> ParticleTypes.WITCH;
            case ROCKFALL -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            default -> ParticleTypes.FLAME;
        };
    }

    private static ParticleOptions lingerParticle(Kind k) {
        return switch (k) {
            case ICE_FIELD -> ParticleTypes.SNOWFLAKE;
            case POISON_CLOUD -> new DustParticleOptions(new Vector3f(0.55F, 0.2F, 0.75F), 2.0F);
            case DRAGON_PILLAR -> ParticleTypes.DRAGON_BREATH;
            default -> ParticleTypes.FLAME;
        };
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
