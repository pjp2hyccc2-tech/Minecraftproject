package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Windmill sails of radius {@code Radius} blocks ({@code {Radius:7.0f}}), turning in the wind about an axle that
 * leaves the block's {@code facing} side; the client draws axle, hub and four lattice sails with canvas.
 */
public class WindmillSailsBlockEntity extends SyncedBlockEntity {
    private float radius = 7.0F;

    /** Client-side animation state (sail angle accumulated per frame, radians). */
    public double clientAngle;
    public double clientLastSeconds = Double.NaN;

    public WindmillSailsBlockEntity(BlockPos pos, BlockState state) {
        super(ERBlocks.WINDMILL_SAILS_ENTITY.get(), pos, state);
        clientAngle = (pos.asLong() & 1023) * 0.01;
    }

    public float radius() {
        return radius;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        radius = clampedFloat(tag, "Radius", radius, 2.0F, 12.0F);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Radius", radius);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(radius + 2.0);
    }
}
