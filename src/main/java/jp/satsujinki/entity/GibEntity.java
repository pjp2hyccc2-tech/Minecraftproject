package jp.satsujinki.entity;

import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.registry.ModParticles;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** A flying chunk of a contestant: meat, organ, bone, head, arm or leg. Bounces, bleeds, then rots away. */
public class GibEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(GibEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(GibEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(GibEntity.class, EntityDataSerializers.INT);

    public static final int KIND_MEAT = 0, KIND_ORGAN = 1, KIND_BONE = 2, KIND_HEAD = 3, KIND_ARM = 4, KIND_LEG = 5;

    private boolean landed;
    /** Client-side spin axis, seeded from the id so every client sees the same tumble. */
    public final float spinX, spinY, spinZ;

    public GibEntity(EntityType<? extends GibEntity> type, Level level) {
        super(type, level);
        java.util.Random r = new java.util.Random(this.getId() * 31L + 7L);
        this.spinX = (r.nextFloat() - 0.5F) * 40.0F;
        this.spinY = (r.nextFloat() - 0.5F) * 40.0F;
        this.spinZ = (r.nextFloat() - 0.5F) * 40.0F;
    }

    public void setup(int kind, int skin, int life) {
        this.entityData.set(DATA_KIND, kind);
        this.entityData.set(DATA_SKIN, skin);
        this.entityData.set(DATA_LIFE, life);
    }

    public int getKind() {
        return this.entityData.get(DATA_KIND);
    }

    public int getSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    public boolean isResting() {
        return this.onGround() && this.getDeltaMovement().lengthSqr() < 0.002D;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, KIND_MEAT);
        this.entityData.define(DATA_SKIN, -1);
        this.entityData.define(DATA_LIFE, 300);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 before = this.getDeltaMovement().add(0.0D, -0.06D, 0.0D);
        this.setDeltaMovement(before);
        this.move(MoverType.SELF, before);
        Vec3 after = this.getDeltaMovement();
        double vx = after.x, vy = after.y, vz = after.z;
        if (this.horizontalCollision) {
            vx = -before.x * 0.4D;
            vz = -before.z * 0.4D;
        }
        if (this.verticalCollision && before.y < -0.12D) {
            vy = -before.y * 0.32D;
            onImpact(before.y);
        }
        double f = this.onGround() ? 0.6D : 0.98D;
        this.setDeltaMovement(vx * f, vy * 0.98D, vz * f);

        if (level().isClientSide) {
            if (before.lengthSqr() > 0.02D && this.random.nextInt(2) == 0) {
                level().addParticle(ModParticles.BLOOD_DROP.get(), getX(), getY() + 0.1D, getZ(),
                        (this.random.nextDouble() - 0.5D) * 0.04D, 0.0D, (this.random.nextDouble() - 0.5D) * 0.04D);
            }
        } else if (this.tickCount > getLife()) {
            this.discard();
        }
    }

    private void onImpact(double speed) {
        if (level().isClientSide) {
            int n = 3 + (int) (-speed * 6.0D);
            for (int i = 0; i < n; i++) {
                level().addParticle(ModParticles.BLOOD_DROP.get(), getX(), getY() + 0.05D, getZ(),
                        (this.random.nextDouble() - 0.5D) * 0.3D, this.random.nextDouble() * 0.2D, (this.random.nextDouble() - 0.5D) * 0.3D);
            }
            return;
        }
        if (!this.landed || speed < -0.4D) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.GORE_GIB_LAND.get(), SoundSource.HOSTILE,
                    0.6F, 0.8F + this.random.nextFloat() * 0.5F);
            if (SatsujinkiConfig.bloodDecals() && this.random.nextInt(3) == 0 && level() instanceof ServerLevel sl) {
                GoreHandler.splatter(sl, position(), 1, 0.2D);
            }
        }
        this.landed = true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_KIND, tag.getInt("Kind"));
        this.entityData.set(DATA_SKIN, tag.getInt("Skin"));
        this.entityData.set(DATA_LIFE, Math.max(20, tag.getInt("Life")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", getKind());
        tag.putInt("Skin", getSkin());
        tag.putInt("Life", getLife());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public static boolean isGib(Entity e) {
        return e instanceof GibEntity;
    }
}
