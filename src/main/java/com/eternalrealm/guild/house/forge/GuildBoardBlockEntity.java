package com.eternalrealm.guild.house.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The guild board's block entity: no data, only the anchor of the client's board renderer. */
public class GuildBoardBlockEntity extends BlockEntity {
    public GuildBoardBlockEntity(BlockPos pos, BlockState state) {
        super(GuildHouseBlocks.BOARD_ENTITY.get(), pos, state);
    }

    /** The board is three blocks wide and two high around its block. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(2.0);
    }
}
