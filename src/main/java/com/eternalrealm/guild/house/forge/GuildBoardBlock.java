package com.eternalrealm.guild.house.forge;

import com.eternalrealm.estate.forge.EstateProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The guild board (ギルド掲示板) in every guild house: a framed cork board three blocks wide and two high on the
 * wall, its block at the bottom centre ({@code facing} = the side it faces). The client draws the message of the day and the notes of the viewer's own guild on it (only members are
 * ever inside); using it opens the board screen. Carries no data of its own.
 */
public class GuildBoardBlock extends HorizontalDirectionalBlock implements EntityBlock,
        EstateProtection.UsableInEstates {
    /** The board spans three blocks across and two up, its block at the bottom centre, two pixels off the wall. */
    private static final VoxelShape NORTH = Block.box(-16.0, 0.0, 14.0, 32.0, 32.0, 16.0);
    private static final VoxelShape SOUTH = Block.box(-16.0, 0.0, 0.0, 32.0, 32.0, 2.0);
    private static final VoxelShape WEST = Block.box(14.0, 0.0, -16.0, 16.0, 32.0, 32.0);
    private static final VoxelShape EAST = Block.box(0.0, 0.0, -16.0, 2.0, 32.0, 32.0);

    public GuildBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face
                : context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            GuildHouseUi.openBoard(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuildBoardBlockEntity(pos, state);
    }
}
