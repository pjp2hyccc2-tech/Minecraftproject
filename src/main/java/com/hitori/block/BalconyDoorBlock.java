package com.hitori.block;

import com.hitori.registry.ModSounds;
import com.hitori.world.FixtureEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The aluminium sliding glass doors to the balcony (掃き出し窓), two blocks high. Closed, the pane stands on the outer
 * track in the middle of the wall; open, it has slid 12 px toward the model's +x on the inner track (over its
 * neighbour), leaving a 12 px gap. The models are the world's ({@code tools/gen_assets.py}), textures
 * {@code balcony_door_bottom} / {@code _top} (translucent).
 */
public class BalconyDoorBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public BalconyDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(Props.OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, Props.OPEN);
    }

    VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction f = state.getValue(FACING);
        VoxelShape s = state.getValue(Props.OPEN) ? FacingBlock.rotate(12, 0, 7, 16, 16, 10, f) : FacingBlock.rotate(0, 0, 7, 16, 16, 9, f);
        BlockState n = level.getBlockState(pos.relative(f.getCounterClockWise()));
        if (n.is(this) && n.getValue(FACING) == f && n.getValue(Props.OPEN)) s = Shapes.or(s, FacingBlock.rotate(0, 0, 8.5, 12, 16, 10, f));
        return s;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape(state, level, pos);
    }

    /** Glass: never hides anything from sight. */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND && state.getValue(Props.OPEN);
    }

    /** Opens or shuts one pane (both halves). Returns true if it changed. */
    public static boolean set(Level level, BlockPos pos, boolean open, boolean sound) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof BalconyDoorBlock)) return false;
        BlockPos lower = s.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        boolean changed = false;
        for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
            BlockState ps = level.getBlockState(p);
            if (ps.getBlock() instanceof BalconyDoorBlock && ps.getValue(Props.OPEN) != open) {
                level.setBlock(p, ps.setValue(Props.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                changed = true;
            }
        }
        if (changed && sound) level.playSound(null, lower, ModSounds.DOOR_BATH_FOLD.get(), SoundSource.BLOCKS, 0.8f, open ? 0.78f : 0.7f);
        return changed;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            set(level, pos, !state.getValue(Props.OPEN), true);
            if (level instanceof ServerLevel sl) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), player.isShiftKeyDown() ? 2.0f : 6.0f, "balcony_door", sp);
            FixtureEvents.LISTENER.interact(sp, "balcony_door", state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos p = ctx.getClickedPos();
        if (p.getY() >= ctx.getLevel().getMaxBuildHeight() - 1 || !ctx.getLevel().getBlockState(p.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        if (level.getBlockState(other).is(this)) level.setBlock(other, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }
}
