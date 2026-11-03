package com.reisatsu.block;

import com.reisatsu.block.entity.AutoDoorBlockEntity;
import com.reisatsu.registry.ModBlockEntities;
import com.reisatsu.world.Fixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * One panel of an automatic sliding glass door, three blocks high ({@code part} bottom / middle / top). The block
 * entity on the bottom block animates the slide and (on the left panel of a pair) runs the sensor. The panel is
 * drawn by {@link com.reisatsu.client.render.block.AutoDoorRenderer}; the top block also shows the header rail
 * model. Glass: never blocks sight; no collision while open.
 */
public class AutoDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape[] PANEL = {
            Block.box(0, 0, 7, 16, 16, 9), Block.box(7, 0, 0, 9, 16, 16)};

    public AutoDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(Props.SIDE, Props.Side.LEFT)
                .setValue(Props.DOOR_PART, Props.DoorPart.BOTTOM).setValue(Props.OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Props.SIDE, Props.DOOR_PART, Props.OPEN);
    }

    /** Direction the panel slides when it opens. */
    public static Direction slideDir(BlockState state) {
        Direction f = state.getValue(FACING);
        return state.getValue(Props.SIDE) == Props.Side.LEFT ? f.getClockWise() : f.getCounterClockWise();
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(Props.DOOR_PART) == Props.DoorPart.TOP ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return PANEL[state.getValue(FACING).getAxis() == Direction.Axis.Z ? 0 : 1];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(Props.OPEN) ? Shapes.empty() : getShape(state, level, pos, ctx);
    }

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

    /** Mobs plan their way through: the doors open for them when they come near (unless switched off or locked). */
    @Override
    public @Nullable BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        if (state.getValue(Props.OPEN)) return BlockPathTypes.OPEN;
        if (level instanceof Level l && !l.isClientSide && l instanceof net.minecraft.server.level.ServerLevel sl
                && Fixtures.autoDoorsEnabled(sl)) return BlockPathTypes.OPEN;
        return BlockPathTypes.BLOCKED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return state.getValue(Props.OPEN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(Props.DOOR_PART) == Props.DoorPart.BOTTOM ? new AutoDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.AUTO_DOOR.get()) return null;
        return level.isClientSide
                ? (l, p, s, be) -> ((AutoDoorBlockEntity) be).clientTick()
                : (l, p, s, be) -> ((AutoDoorBlockEntity) be).serverTick();
    }

    /** Sets the {@code open} state of all three blocks of the panel whose bottom is at pos. */
    public static void setPanelOpen(Level level, BlockPos bottom, boolean open) {
        for (int dy = 0; dy < 3; dy++) {
            BlockPos p = bottom.above(dy);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof AutoDoorBlock && s.getValue(Props.OPEN) != open) level.setBlock(p, s.setValue(Props.OPEN, open), 3);
        }
    }
}
