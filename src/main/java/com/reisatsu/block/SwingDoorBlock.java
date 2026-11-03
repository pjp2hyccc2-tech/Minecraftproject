package com.reisatsu.block;

import com.reisatsu.block.entity.SwingDoorBlockEntity;
import com.reisatsu.registry.ModBlockEntities;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.FixtureEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A leaf of the kitchen's double swing doors (2 high). Anything walking through pushes it open away from itself;
 * it flaps shut behind (noise 9). No collision; while shut it blocks sight. The leaf is drawn by
 * {@link com.reisatsu.client.render.block.SwingDoorRenderer} from the lower block's entity.
 */
public class SwingDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape[] PANEL = {Block.box(0, 0, 7, 16, 16, 9), Block.box(7, 0, 0, 9, 16, 16)};

    public SwingDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(Props.SIDE, Props.Side.LEFT).setValue(Props.SWING, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, Props.SIDE, Props.SWING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PANEL[state.getValue(FACING).getAxis() == Direction.Axis.Z ? 0 : 1];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(Props.SWING) == 0 ? getShape(state, level, pos, ctx) : Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity) || entity.isSpectator()) return;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (level.getBlockEntity(lower) instanceof SwingDoorBlockEntity be) be.touched();
        if (state.getValue(Props.SWING) != 0) return;
        Direction f = state.getValue(FACING);
        Vec3 c = Vec3.atCenterOf(pos);
        double d = (entity.getX() - c.x) * f.getStepX() + (entity.getZ() - c.z) * f.getStepZ();
        // pushed from the front: it swings to the back ("in"), from the back: to the front ("out")
        int swing = d >= 0 ? 1 : 2;
        setSwing((ServerLevel) level, lower, swing, true);
        FixtureEvents.LISTENER.noise((ServerLevel) level, c, 9.0f, "swing_door", entity);
    }

    /** Sets the swing of both halves of the leaf whose lower block is at lower. */
    public static void setSwing(Level level, BlockPos lower, int swing, boolean sound) {
        BlockState s = level.getBlockState(lower);
        if (!(s.getBlock() instanceof SwingDoorBlock)) return;
        if (s.getValue(HALF) == DoubleBlockHalf.UPPER) {
            lower = lower.below();
            s = level.getBlockState(lower);
            if (!(s.getBlock() instanceof SwingDoorBlock)) return;
        }
        if (s.getValue(Props.SWING) == swing) return;
        level.setBlock(lower, s.setValue(Props.SWING, swing), 3);
        BlockState up = level.getBlockState(lower.above());
        if (up.getBlock() instanceof SwingDoorBlock) level.setBlock(lower.above(), up.setValue(Props.SWING, swing), 3);
        if (sound) {
            level.playSound(null, lower, ModSounds.DOOR_SWING.get(), SoundSource.BLOCKS, swing == 0 ? 0.6f : 0.9f,
                    0.95f + level.getRandom().nextFloat() * 0.1f);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new SwingDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.SWING_DOOR.get()) return null;
        return level.isClientSide
                ? (l, p, s, be) -> ((SwingDoorBlockEntity) be).clientTick()
                : (l, p, s, be) -> ((SwingDoorBlockEntity) be).serverTick();
    }
}
