package com.frostbound.block;

import com.frostbound.server.CityService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Control panel inside every city building; using it opens that building in the management screen. */
public class BuildingCoreBlock extends Block {
    public BuildingCoreBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            CityService.openBuildingAt(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
