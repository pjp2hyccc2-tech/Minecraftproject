package com.huntcraft.quest.block;

import com.huntcraft.quest.GatherLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gathering point: right-click to gather map/rank-appropriate materials 1-3 times; then it is depleted
 * (CHARGES = 0, different model) and regrows after {@link #REGEN_TICKS}.
 */
public class GatheringBlock extends Block {
    public static final IntegerProperty CHARGES = IntegerProperty.create("charges", 0, 3);
    public static final int REGEN_TICKS = 20 * 60 * 4;
    private static final VoxelShape PLANT = Block.box(2, 0, 2, 14, 13, 14);
    private static final VoxelShape MUSHROOM = Block.box(3, 0, 3, 13, 10, 13);
    private static final VoxelShape ROCK = Shapes.or(Block.box(1, 0, 2, 15, 8, 14), Block.box(4, 8, 4, 12, 13, 12));
    private static final VoxelShape BONES = Block.box(1, 0, 1, 15, 6, 15);

    public final GatherKind kind;

    public GatheringBlock(GatherKind kind, Properties props) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(CHARGES, 3));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGES);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (kind) {
            case MINING -> ROCK;
            case BONE -> BONES;
            case MUSHROOM -> MUSHROOM;
            default -> PLANT;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) GatherLoot.gather(sp, sl, pos, state, this);
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(CHARGES) < 3) level.setBlock(pos, state.setValue(CHARGES, 3), 3);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (state.getValue(CHARGES) <= 0 || r.nextInt(4) != 0) return;
        double x = pos.getX() + 0.2 + r.nextDouble() * 0.6;
        double y = pos.getY() + 0.3 + r.nextDouble() * 0.7;
        double z = pos.getZ() + 0.2 + r.nextDouble() * 0.6;
        if (kind == GatherKind.BUG) level.addParticle(ParticleTypes.GLOW, x, y, z, (r.nextDouble() - 0.5) * 0.02, 0.01, (r.nextDouble() - 0.5) * 0.02);
        else level.addParticle(ParticleTypes.WAX_OFF, x, y, z, 0, 0.02, 0);
    }
}
