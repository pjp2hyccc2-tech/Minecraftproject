package com.hitori.block;

import com.hitori.registry.ModSounds;
import com.hitori.world.FixtureEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The deep square bathtub (1×2): {@code part} head / foot like a vanilla bed (the head lies in the facing direction
 * from the foot), {@code water} 0 empty / 1 half / 2 full, {@code lid}. Hollow: a floor and three walls per block
 * (open toward the other half), so the doll can lie in the water; with the lid on it is closed on top.
 * Art models {@code bathtub_<part>_<water>} and {@code bathtub_<part>_lid}.
 */
public class BathtubBlock extends FacingBlock {
    /** Rim height (pixels). */
    public static final double RIM = 15.0;
    private final VoxelShape[] hollow = new VoxelShape[8];

    public BathtubBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(Props.PART, Props.Part.HEAD).setValue(Props.WATER, 0).setValue(Props.LID, false));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (Props.Part part : Props.Part.values()) {
                // at facing north the head is the northern block: its open side is south; the foot's is north
                VoxelShape s = rotate(0, 0, 0, 16, 2, 16, d);
                s = Shapes.or(s, rotate(0, 0, 0, 1.5, RIM, 16, d), rotate(14.5, 0, 0, 16, RIM, 16, d));
                s = Shapes.or(s, part == Props.Part.HEAD ? rotate(0, 0, 0, 16, RIM, 1.5, d) : rotate(0, 0, 14.5, 16, RIM, 16, d));
                hollow[d.get2DDataValue() * 2 + part.ordinal()] = s;
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Props.PART, Props.WATER, Props.LID);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Block.box(0, 0, 0, 16, RIM, 16);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (state.getValue(Props.LID)) return Block.box(0, 0, 0, 16, RIM, 16);
        return hollow[state.getValue(FACING).get2DDataValue() * 2 + state.getValue(Props.PART).ordinal()];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getCollisionShape(state, level, pos, ctx);
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
        return false;
    }

    /** The other half of the tub. */
    public static BlockPos other(BlockState state, BlockPos pos) {
        Direction f = state.getValue(FACING);
        return state.getValue(Props.PART) == Props.Part.HEAD ? pos.relative(f.getOpposite()) : pos.relative(f);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.getMainHandItem().isEmpty() && player.isShiftKeyDown()) {
                // sneak + empty hand: the lid on / off
                boolean lid = !state.getValue(Props.LID);
                setBoth(level, pos, state.getValue(Props.WATER), lid);
                level.playSound(null, pos, ModSounds.FIXTURE_WATER_SLOSH.get(), SoundSource.BLOCKS, 0.4f, 1.4f);
            }
            FixtureEvents.LISTENER.interact(sp, "bathtub", pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sets water and lid on both halves. */
    public static void setBoth(Level level, BlockPos pos, int water, boolean lid) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof BathtubBlock)) return;
        for (BlockPos p : new BlockPos[]{pos, other(s, pos)}) {
            BlockState ps = level.getBlockState(p);
            if (!(ps.getBlock() instanceof BathtubBlock)) continue;
            BlockState ns = ps.setValue(Props.WATER, water).setValue(Props.LID, lid);
            if (ns != ps) level.setBlock(p, ns, 3);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        // a drip into the full tub now and then (the tap above leaks)
        if (state.getValue(Props.WATER) > 0 && !state.getValue(Props.LID) && r.nextInt(10) == 0) {
            double y = pos.getY() + (state.getValue(Props.WATER) == 2 ? 0.86 : 0.5);
            level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.2 + r.nextDouble() * 0.6, y, pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.02, 0);
        }
    }
}
