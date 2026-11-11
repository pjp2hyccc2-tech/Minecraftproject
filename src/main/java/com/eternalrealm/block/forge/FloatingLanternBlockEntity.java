package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No data: it exists so the client can draw a {@link FloatingLanternBlock} bobbing on the water. */
public class FloatingLanternBlockEntity extends BlockEntity {
    /** Client: height of the water surface below, relative to the block's floor (NaN = not looked at yet). */
    public double clientWater = Double.NaN;
    public long clientWaterAt = Long.MIN_VALUE;

    public FloatingLanternBlockEntity(BlockPos pos, BlockState state) {
        super(TownGimmickBlocks.FLOATING_LANTERN_ENTITY.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.6, 0.6, 0.6);
    }
}
