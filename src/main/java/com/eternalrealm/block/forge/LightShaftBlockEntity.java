package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

/**
 * The shape of a sunbeam: it leaves the emitter's block towards the block's {@code facing}, tilted down by
 * {@code Pitch} degrees, with a {@code Width} x {@code Height} cross-section (blocks), {@code Length} blocks long.
 * Saved as {@code {Length:14.0f,Width:2.0f,Height:4.0f,Pitch:38.0f}} (the capital build writes exactly this).
 */
public class LightShaftBlockEntity extends BlockEntity {
    /** Set by the client so loaded beams can drift dust; a no-op on servers. */
    public static Consumer<LightShaftBlockEntity> clientLoaded = beam -> {
    };

    private float length = 10.0F;
    private float width = 1.0F;
    private float height = 3.0F;
    private float pitch = 40.0F;

    public LightShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ERBlocks.LIGHT_SHAFT_ENTITY.get(), pos, state);
    }

    public float length() {
        return length;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float pitch() {
        return pitch;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Length")) {
            length = Mth.clamp(tag.getFloat("Length"), 1.0F, 48.0F);
        }
        if (tag.contains("Width")) {
            width = Mth.clamp(tag.getFloat("Width"), 0.25F, 16.0F);
        }
        if (tag.contains("Height")) {
            height = Mth.clamp(tag.getFloat("Height"), 0.25F, 16.0F);
        }
        if (tag.contains("Pitch")) {
            pitch = Mth.clamp(tag.getFloat("Pitch"), 0.0F, 89.0F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Length", length);
        tag.putFloat("Width", width);
        tag.putFloat("Height", height);
        tag.putFloat("Pitch", pitch);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            clientLoaded.accept(this);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        double reach = length + Math.max(width, height) + 1.0;
        return new AABB(worldPosition).inflate(reach);
    }
}
