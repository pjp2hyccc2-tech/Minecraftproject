package com.eternalrealm.estate.forge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The labelled storage chest of the inn rooms (収納箱): an iron-bound walnut chest of 27 slots with a brass plate on
 * its front whose picture shows what it is for ({@link ChestLabel}). Sneaking and using it with an empty hand turns
 * the plate to the next label; the sorting desk puts things away by these labels. In an estate only those who may use
 * the room open it (estate protection); elsewhere it is an ordinary chest that keeps its contents when broken.
 */
public class StorageChestBlock extends BaseEntityBlock implements EstateProtection.UsableInEstates {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ChestLabel> LABEL = EnumProperty.create("label", ChestLabel.class);
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

    public StorageChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LABEL, ChestLabel.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LABEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageChestBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        boolean relabel = player.isSecondaryUseActive() && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (relabel) {
            ChestLabel next = ChestLabel.of(state.getValue(LABEL).label().next());
            level.setBlock(pos, state.setValue(LABEL, next), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
            player.displayClientMessage(Component.translatable("message.eternalrealm.estate.label",
                    Component.translatable(next.label().translationKey()).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest && player instanceof ServerPlayer) {
            player.openMenu(chest);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest) {
            chest.recheckOpen();
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (stack.hasCustomHoverName() && level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest) {
            chest.setCustomName(stack.getHoverName());
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                Containers.dropContents(level, pos, container);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** The label of the storage chest at {@code pos}, {@link ChestLabel#NONE} for anything else. */
    public static ChestLabel labelAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(LABEL) ? state.getValue(LABEL) : ChestLabel.NONE;
    }

    /** Gives the storage chest at {@code pos} {@code label} (nothing for other blocks). */
    public static void setLabel(Level level, BlockPos pos, ChestLabel label) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(LABEL) && state.getValue(LABEL) != label) {
            level.setBlock(pos, state.setValue(LABEL, label), Block.UPDATE_CLIENTS);
        }
    }
}
