package com.huntcraft.entity.misc;

import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.registry.HCEntities;
import com.huntcraft.registry.HCItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Placed pitfall / shock trap. Catches large monsters that walk over it. */
public class TrapEntity extends Entity {
    public enum TrapKind { PITFALL, SHOCK }

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(TrapEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_TRIGGERED = SynchedEntityData.defineId(TrapEntity.class, EntityDataSerializers.BOOLEAN);

    private int life = 20 * 180;
    private int triggeredTicks = 0;
    private HuntMonster caught;

    public TrapEntity(EntityType<? extends TrapEntity> type, Level level) {
        super(type, level);
    }

    public static TrapEntity create(Level level, TrapKind kind, double x, double y, double z) {
        TrapEntity t = new TrapEntity(HCEntities.TRAP.get(), level);
        t.setPos(x, y, z);
        t.entityData.set(DATA_KIND, kind.ordinal());
        return t;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, 0);
        this.entityData.define(DATA_TRIGGERED, false);
    }

    public TrapKind getKind() {
        return this.entityData.get(DATA_KIND) == 1 ? TrapKind.SHOCK : TrapKind.PITFALL;
    }

    public boolean isTriggered() {
        return this.entityData.get(DATA_TRIGGERED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround() && !isTriggered()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        }
        if (level().isClientSide) {
            if (getKind() == TrapKind.SHOCK && tickCount % (isTriggered() ? 2 : 10) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - 0.5), getY() + 0.3, getZ() + (random.nextDouble() - 0.5), 0, 0.05, 0);
            }
            return;
        }
        if (isTriggered()) {
            triggeredTicks++;
            if (caught == null || !caught.isAlive() || !caught.isTrapped() || triggeredTicks > 20 * 30) discard();
            return;
        }
        if (--life <= 0) {
            discard();
            return;
        }
        for (HuntMonster m : level().getEntitiesOfClass(HuntMonster.class, getBoundingBox().inflate(0.5, 1.0, 0.5))) {
            if (!m.profile().large || m.isFlying() || m.isCarcass()) continue;
            int ticks = getKind() == TrapKind.PITFALL ? 200 : 160;
            if (m.trap(getKind().ordinal(), ticks)) {
                caught = m;
                this.entityData.set(DATA_TRIGGERED, true);
                m.setPos(getX(), m.getY(), getZ());
                break;
            }
        }
    }

    @Override
    public boolean isPickable() {
        return !isTriggered();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isTriggered() && player.isShiftKeyDown()) {
            if (!level().isClientSide) {
                ItemStack st = new ItemStack(getKind() == TrapKind.SHOCK ? HCItems.SHOCK_TRAP.get() : HCItems.PITFALL_TRAP.get());
                if (!player.getInventory().add(st)) player.drop(st, false);
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_KIND, tag.getInt("Kind"));
        this.life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", this.entityData.get(DATA_KIND));
        tag.putInt("Life", life);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public static boolean isTrapNear(Level level, net.minecraft.world.phys.Vec3 pos, double r) {
        return !level.getEntitiesOfClass(TrapEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(r)).isEmpty();
    }

    public Entity self() {
        return this;
    }
}
