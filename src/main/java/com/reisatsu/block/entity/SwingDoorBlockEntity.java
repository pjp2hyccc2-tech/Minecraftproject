package com.reisatsu.block.entity;

import com.reisatsu.block.Props;
import com.reisatsu.block.SwingDoorBlock;
import com.reisatsu.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * The lower block of a kitchen swing door leaf. Client: a damped spring toward the target angle of the
 * {@code swing} state (0 shut, 1 swung in, 2 swung out), so the leaf flaps back and forth before it settles.
 * Server: shuts the leaf again once nobody has been in its cell for a moment.
 */
public class SwingDoorBlockEntity extends BlockEntity {
    public float angle, prevAngle, velocity;
    private int idle;

    public SwingDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWING_DOOR.get(), pos, state);
    }

    /** Called by the block while an entity is in the leaf's cells. */
    public void touched() {
        idle = 0;
    }

    public void clientTick() {
        prevAngle = angle;
        int swing = getBlockState().hasProperty(Props.SWING) ? getBlockState().getValue(Props.SWING) : 0;
        float target = swing == 1 ? 80.0f : swing == 2 ? -80.0f : 0.0f;
        // a stiff spring with little damping: the leaf overshoots and flaps a few times when it falls shut
        float k = swing == 0 ? 0.16f : 0.35f;
        float damping = swing == 0 ? 0.80f : 0.55f;
        velocity = (velocity + (target - angle) * k) * damping;
        angle += velocity;
        if (Math.abs(target - angle) < 0.05f && Math.abs(velocity) < 0.05f) {
            angle = target;
            velocity = 0;
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        BlockState s = getBlockState();
        if (!s.hasProperty(Props.SWING) || s.getValue(Props.SWING) == 0) {
            idle = 0;
            return;
        }
        if (++idle > 16) SwingDoorBlock.setSwing(sl, worldPosition, 0, true);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0, 0, 1.0).expandTowards(0, 2, 0);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
