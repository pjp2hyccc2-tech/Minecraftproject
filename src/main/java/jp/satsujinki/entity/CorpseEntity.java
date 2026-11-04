package jp.satsujinki.entity;

import jp.satsujinki.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** A dead contestant (lying, flattened, headless, cut in half or impaled). Temporary, never saved. */
public class CorpseEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STYLE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);

    public static final int STYLE_LYING = 0, STYLE_FLAT = 1, STYLE_HEADLESS = 2, STYLE_TOP_HALF = 3, STYLE_BOTTOM_HALF = 4, STYLE_IMPALED = 5;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
    }

    public void setup(int skin, int style, int lifeTicks) {
        this.entityData.set(DATA_SKIN, skin);
        this.entityData.set(DATA_STYLE, style);
        this.entityData.set(DATA_LIFE, lifeTicks);
    }

    public int getSkin() {
        return this.entityData.get(DATA_SKIN);
    }

    public int getStyle() {
        return this.entityData.get(DATA_STYLE);
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SKIN, 0);
        this.entityData.define(DATA_STYLE, STYLE_LYING);
        this.entityData.define(DATA_LIFE, 1800);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (!this.isNoGravity()) v = v.add(0.0D, -0.05D, 0.0D);
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, this.getDeltaMovement());
        double friction = this.onGround() ? 0.55D : 0.96D;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 0.98D, friction));

        if (level().isClientSide) {
            clientBleed();
        } else if (this.tickCount > getLife()) {
            this.discard();
        }
    }

    private void clientBleed() {
        int style = getStyle();
        int age = this.tickCount;
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad), fz = Mth.cos(yawRad);
        if (style == STYLE_HEADLESS && age < 90) {
            // arterial fountain from the neck
            double nx = getX() + fx * 0.42D, nz = getZ() + fz * 0.42D, ny = getY() + 0.2D;
            int n = age < 30 ? 3 : 1;
            for (int i = 0; i < n; i++) {
                float pulse = (age % 12) < 6 ? 1.0F : 0.55F;
                level().addParticle(ModParticles.BLOOD_DROP.get(), nx, ny, nz,
                        fx * 0.12D * pulse + (this.random.nextDouble() - 0.5D) * 0.08D,
                        0.22D * pulse + this.random.nextDouble() * 0.12D,
                        fz * 0.12D * pulse + (this.random.nextDouble() - 0.5D) * 0.08D);
            }
        } else if ((style == STYLE_TOP_HALF || style == STYLE_BOTTOM_HALF) && age < 70 && age % 2 == 0) {
            double off = style == STYLE_TOP_HALF ? -0.35D : 0.35D;
            level().addParticle(ModParticles.BLOOD_DROP.get(), getX() + fx * off, getY() + 0.15D, getZ() + fz * off,
                    (this.random.nextDouble() - 0.5D) * 0.15D, 0.12D + this.random.nextDouble() * 0.1D, (this.random.nextDouble() - 0.5D) * 0.15D);
        } else if (age % 25 == 0 && age < 600 && this.random.nextInt(3) == 0) {
            level().addParticle(ModParticles.BLOOD_DROP.get(), getX() + (this.random.nextDouble() - 0.5D) * 0.6D, getY() + 0.1D,
                    getZ() + (this.random.nextDouble() - 0.5D) * 0.6D, 0.0D, 0.02D, 0.0D);
        }
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
        this.entityData.set(DATA_SKIN, tag.getInt("Skin"));
        this.entityData.set(DATA_STYLE, tag.getInt("Style"));
        this.entityData.set(DATA_LIFE, Math.max(20, tag.getInt("Life")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Skin", getSkin());
        tag.putInt("Style", getStyle());
        tag.putInt("Life", getLife());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }

    public static boolean isCorpse(Entity e) {
        return e instanceof CorpseEntity;
    }
}
