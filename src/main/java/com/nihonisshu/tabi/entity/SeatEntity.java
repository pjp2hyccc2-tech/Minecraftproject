package com.nihonisshu.tabi.entity;

import com.nihonisshu.tabi.registry.TabiEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

/** Invisible entity used to sit on chairs, benches and zabuton. Removes itself when empty. */
public class SeatEntity extends Entity {
    private BlockPos seatPos = BlockPos.ZERO;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void sit(Level level, BlockPos pos, double height, Direction facing, Player player) {
        List<SeatEntity> existing = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        if (!existing.isEmpty() && existing.get(0).isVehicle()) return;
        SeatEntity seat = TabiEntities.SEAT.get().create(level);
        if (seat == null) return;
        seat.seatPos = pos;
        seat.setPos(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
        seat.setYRot(facing.toYRot());
        level.addFreshEntity(seat);
        player.startRiding(seat, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (!isVehicle() || level().isEmptyBlock(seatPos)) {
                discard();
            }
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 v = super.getDismountLocationForPassenger(passenger);
        return new Vec3(v.x, Math.max(v.y, seatPos.getY() + 1.0), v.z);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        seatPos = BlockPos.of(tag.getLong("seat"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("seat", seatPos.asLong());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
