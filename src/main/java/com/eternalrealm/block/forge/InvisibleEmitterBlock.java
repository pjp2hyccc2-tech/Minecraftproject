package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * An invisible, walk-through marker whose client ticker emits an effect (chimney smoke, fountain water). Like the
 * light shaft, only a player holding its item can see its outline and break it.
 */
public abstract class InvisibleEmitterBlock<E extends BlockEntity> extends Block implements EntityBlock {
    private static final VoxelShape HELD_SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
    private final Supplier<BlockEntityType<E>> type;
    private final BlockEntityTicker<E> clientTicker;

    protected InvisibleEmitterBlock(Properties properties, Supplier<BlockEntityType<E>> type,
                                    BlockEntityTicker<E> clientTicker) {
        super(properties);
        this.type = type;
        this.clientTicker = clientTicker;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (!level.isClientSide() || blockEntityType != type.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) clientTicker;
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isHoldingItem(asItem()) ? HELD_SHAPE : Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
