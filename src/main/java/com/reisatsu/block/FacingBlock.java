package com.reisatsu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A horizontally facing block with a box shape given for the NORTH orientation (the front faces north,
 * i.e. toward the player who placed it); the shape is rotated for the other directions.
 */
public class FacingBlock extends HorizontalDirectionalBlock {
    private final VoxelShape[] shapes = new VoxelShape[4];

    /** Box in pixels (0..16) for a block facing NORTH. */
    public FacingBlock(Properties properties, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = rotate(x1, y1, z1, x2, y2, z2, d);
        }
    }

    public FacingBlock(Properties properties) {
        this(properties, 0, 0, 0, 16, 16, 16);
    }

    static VoxelShape rotate(double x1, double y1, double z1, double x2, double y2, double z2, Direction d) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /**
     * Furniture with a partial shape must be an obstacle to mobs. The vanilla default treats every block whose
     * collision is not a full cube as walkable, so the pathfinder walked the bride straight into stone lanterns
     * and chests of drawers and she stood there pushing against them.
     */
    public static boolean openForPaths(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        if (type == PathComputationType.WATER) return level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
        VoxelShape shape = state.getCollisionShape(level, pos);
        // decals, futons and low tables stay walkable (one auto-step); anything taller blocks the path
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return openForPaths(state, level, pos, type);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape s = shapes[state.getValue(FACING).get2DDataValue()];
        return s == null ? Shapes.block() : s;
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
