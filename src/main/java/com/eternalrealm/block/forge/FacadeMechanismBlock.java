package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * A full block set into a wall whose moving part (clock dial, windmill sails, water wheel) the client draws in front
 * of its {@code facing} side. Placed by hand it faces the player.
 */
public class FacadeMechanismBlock<E extends BlockEntity> extends HorizontalDirectionalBlock implements EntityBlock {
    private final Supplier<BlockEntityType<E>> type;
    private final BlockEntityType.BlockEntitySupplier<E> factory;
    @Nullable
    private final BlockEntityTicker<E> clientTicker;

    public FacadeMechanismBlock(Properties properties, Supplier<BlockEntityType<E>> type,
                                BlockEntityType.BlockEntitySupplier<E> factory,
                                @Nullable BlockEntityTicker<E> clientTicker) {
        super(properties);
        this.type = type;
        this.factory = factory;
        this.clientTicker = clientTicker;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.create(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (!level.isClientSide() || clientTicker == null || blockEntityType != type.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) clientTicker;
    }
}
