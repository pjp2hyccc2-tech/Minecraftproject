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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
 * Sliding paper doors, two blocks high ({@code half}), {@code open} 0 shut / 1 a crack / 2 open (art models
 * {@code <id>_<half>_<open>}, facing north at rest). Every panel of a row slides toward +x of its model (east at
 * facing north) by 4 px (crack) or a whole block (open), so a row of panels opens at its −x end and the last panel
 * disappears into the wall: the collision of a block is its own panel's part inside the cell plus the part of the
 * neighbouring panel that slid in.
 * <ul>
 * <li>{@code fusuma} (ROOM): the room's sliding doors, set into a wall line (panel in the middle of the cell).
 * Right-click opens / shuts the whole row.</li>
 * <li>{@code oshiire_fusuma} (CLOSET): the closet's doors, panel at the model's front edge (z 0.25..1.75), standing
 * in the closet's own column facing out of it. Right-click from outside asks the game to hide the player
 * ({@link FixtureEvents.Listener#hide}; the row shuts behind them), from inside to let them out
 * ({@link FixtureEvents.Listener#unhide}; the row opens). Without the game it is an ordinary sliding door.</li>
 * </ul>
 */
public class SlidingDoorBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public enum Kind { ROOM, CLOSET }

    public final Kind kind;

    public SlidingDoorBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(Props.OPEN3, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, Props.OPEN3);
    }

    // ------------------------------------------------------------------------------------------------ geometry
    /** How far the panel has slid along the model's +x (pixels). */
    static int shift(int open) {
        return open == 0 ? 0 : open == 1 ? 4 : 16;
    }

    /** Panel depth range (model z, pixels) at facing north. */
    double z1() {
        return kind == Kind.CLOSET ? 0.0 : 6.5;
    }

    double z2() {
        return kind == Kind.CLOSET ? 2.0 : 9.5;
    }

    /** The neighbour whose panel slides into this cell (model −x). */
    static BlockPos feeder(BlockPos pos, Direction facing) {
        return pos.relative(facing.getCounterClockWise());
    }

    boolean fedFrom(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState n = level.getBlockState(feeder(pos, state.getValue(FACING)));
        return n.is(this) && n.getValue(FACING) == state.getValue(FACING) && n.getValue(Props.OPEN3) > 0;
    }

    /** Occupied part of the panel plane inside this cell. */
    VoxelShape panelShape(BlockState state, BlockGetter level, BlockPos pos) {
        int s = shift(state.getValue(Props.OPEN3));
        Direction f = state.getValue(FACING);
        VoxelShape shape = Shapes.empty();
        if (s < 16) shape = Shapes.or(shape, FacingBlock.rotate(s, 0, z1(), 16, 16, z2(), f));
        if (s > 0 && fedFrom(level, pos, state)) {
            int ns = shift(level.getBlockState(feeder(pos, f)).getValue(Props.OPEN3));
            shape = Shapes.or(shape, FacingBlock.rotate(0, 0, z1(), Math.min(16, ns), 16, z2(), f));
        }
        return shape;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return panelShape(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return panelShape(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return panelShape(state, level, pos);
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
        return type == PathComputationType.LAND && panelShape(state, level, pos).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ rows
    /** The lower block of every panel in the row of this one (contiguous, same block and facing, at most 6). */
    public static java.util.List<BlockPos> row(Level level, BlockPos pos) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof SlidingDoorBlock)) return out;
        BlockPos lower = s.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        Direction f = s.getValue(FACING);
        Direction side = f.getClockWise();
        BlockPos start = lower;
        for (int i = 0; i < 6; i++) {
            BlockPos n = start.relative(side.getOpposite());
            BlockState ns = level.getBlockState(n);
            if (!ns.is(s.getBlock()) || ns.getValue(FACING) != f) break;
            start = n;
        }
        BlockPos p = start;
        for (int i = 0; i < 6; i++) {
            BlockState ps = level.getBlockState(p);
            if (!ps.is(s.getBlock()) || ps.getValue(FACING) != f) break;
            out.add(p.immutable());
            p = p.relative(side);
        }
        return out;
    }

    /** Sets every panel of the row (both halves). Returns true if anything changed. */
    public static boolean setRow(Level level, BlockPos pos, int open) {
        boolean changed = false;
        for (BlockPos lower : row(level, pos)) {
            for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof SlidingDoorBlock && s.getValue(Props.OPEN3) != open) {
                    level.setBlock(p, s.setValue(Props.OPEN3, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static int openOf(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof SlidingDoorBlock ? s.getValue(Props.OPEN3) : 0;
    }

    /** The slide sound of the panels (crack = the short 2 px sound). */
    public static void slideSound(Level level, BlockPos pos, int open, boolean careful) {
        float vol = careful ? 0.4f : 0.85f;
        if (open == 1) level.playSound(null, pos, ModSounds.DOOR_FUSUMA_CRACK.get(), SoundSource.BLOCKS, vol, 1.0f);
        else level.playSound(null, pos, ModSounds.DOOR_FUSUMA.get(), SoundSource.BLOCKS, vol, (open == 2 ? 1.0f : 0.92f) + level.random.nextFloat() * 0.06f);
    }

    // ------------------------------------------------------------------------------------------------ use
    /** True if the entity is on the closet side of this panel (behind its plane, against the facing). */
    public boolean isInside(BlockState state, BlockPos pos, Vec3 at) {
        Direction f = state.getValue(FACING);
        double d = (at.x - (pos.getX() + 0.5)) * f.getStepX() + (at.z - (pos.getZ() + 0.5)) * f.getStepZ();
        return d < 0.35;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level.isClientSide);
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        boolean careful = player.isShiftKeyDown();
        if (kind == Kind.CLOSET) {
            if (isInside(state, lower, player.position())) {
                FixtureEvents.LISTENER.unhide(sp, lower);
                boolean letOut = !isInside(state, lower, player.position());
                // the game let the player out (it plays the slide and the noise): the doors stand open behind them
                setRow(level, lower, 2);
                if (!letOut) {
                    slideSound(level, lower, 2, careful);
                    noise(level, lower, careful ? 3.0f : 6.0f, sp);
                }
                FixtureEvents.LISTENER.interact(sp, "closet_fusuma", lower);
                return InteractionResult.SUCCESS;
            }
            if (FixtureEvents.LISTENER.hide(sp, lower) && isInside(state, lower, player.position())) {
                // the game took the player in (with the slide and the noise): the doors are shut behind them
                setRow(level, lower, 0);
                return InteractionResult.SUCCESS;
            }
        }
        int open = state.getValue(Props.OPEN3) == 2 ? 0 : 2;
        setRow(level, lower, open);
        slideSound(level, lower, open, careful);
        noise(level, lower, careful ? 2.0f : 6.0f, sp);
        FixtureEvents.LISTENER.interact(sp, kind == Kind.CLOSET ? "closet_fusuma" : "fusuma", lower);
        return InteractionResult.SUCCESS;
    }

    static void noise(Level level, BlockPos pos, float r, @Nullable net.minecraft.world.entity.Entity source) {
        if (level instanceof ServerLevel sl) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), r, "fusuma", source);
    }

    // ------------------------------------------------------------------------------------------------ placing (creative)
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

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
