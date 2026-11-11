package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** No data: it exists so the client can draw the swinging board of a {@link ShopSignBlock}. */
public class ShopSignBlockEntity extends BlockEntity {
    public ShopSignBlockEntity(BlockPos pos, BlockState state) {
        super(TownGimmickBlocks.SHOP_SIGN_ENTITY.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.3);
    }
}
