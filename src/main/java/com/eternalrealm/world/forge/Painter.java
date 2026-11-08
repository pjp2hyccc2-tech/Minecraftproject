package com.eternalrealm.world.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * Places blocks of a code-built structure in a local frame: x to the right, y up, z towards the front, turned by a
 * {@link Rotation} around a world origin (block states are rotated too). Blocks are set without neighbour updates;
 * {@link #finish()} then lets fences, walls, panes and stairs connect to what was built around them.
 */
final class Painter {
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final BlockPos origin;
    private final Rotation rotation;
    private final List<BlockPos> reshape;

    Painter(ServerLevel level, BlockPos origin, Rotation rotation) {
        this(level, origin, rotation, new ArrayList<>());
    }

    private Painter(ServerLevel level, BlockPos origin, Rotation rotation, List<BlockPos> reshape) {
        this.level = level;
        this.origin = origin;
        this.rotation = rotation;
        this.reshape = reshape;
    }

    /** A painter whose origin is a local position of this one, turned further by {@code turn}. */
    Painter child(int x, int y, int z, Rotation turn) {
        return new Painter(level, world(x, y, z), rotation.getRotated(turn), reshape);
    }

    ServerLevel level() {
        return level;
    }

    BlockPos world(int x, int y, int z) {
        BlockPos turned = new BlockPos(x, y, z).rotate(rotation);
        return origin.offset(turned.getX(), y, turned.getZ());
    }

    /** World direction of a local direction. */
    Direction turn(Direction local) {
        return rotation.rotate(local);
    }

    void set(int x, int y, int z, BlockState state) {
        BlockPos pos = world(x, y, z);
        BlockState turned = state.rotate(rotation);
        level.setBlock(pos, turned, FLAGS);
        Block block = turned.getBlock();
        if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock
                || block instanceof FenceGateBlock) {
            reshape.add(pos);
        }
    }

    void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(world(x, y, z));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState());
    }

    void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
    }

    /** Walls of a box (no floor or ceiling). */
    void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        fill(x1, y1, z1, x2, y2, z1, state);
        fill(x1, y1, z2, x2, y2, z2, state);
        fill(x1, y1, z1, x1, y2, z2, state);
        fill(x2, y1, z1, x2, y2, z2, state);
    }

    /** A door (two halves) facing {@code facing} in the local frame. */
    void door(int x, int y, int z, Block door, Direction facing, boolean rightHinge) {
        BlockState lower = door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.DOOR_HINGE, rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** A bed whose foot is at (x, y, z) and whose head lies one block towards {@code facing}. */
    void bed(int x, int y, int z, Block bed, Direction facing) {
        BlockState foot = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT);
        set(x, y, z, foot);
        set(x + facing.getStepX(), y, z + facing.getStepZ(),
                foot.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
    }

    /** Lets connecting blocks (fences, walls, panes, bars, stairs, gates) take their final shape. */
    void finish() {
        for (BlockPos pos : reshape) {
            BlockState state = level.getBlockState(pos);
            BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
            if (updated != state) {
                level.setBlock(pos, updated, FLAGS);
            }
        }
        reshape.clear();
    }

    // ------------------------------------------------------------------------------------------- block state helpers

    static BlockState stairs(Block block, Direction facing, Half half) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, half);
    }

    static BlockState stairs(Block block, Direction facing) {
        return stairs(block, facing, Half.BOTTOM);
    }

    static BlockState slab(Block block, SlabType type) {
        return block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, type);
    }

    /** The block turned along {@code axis} if it has an axis (logs, pillars, chains), else its default state. */
    static BlockState axis(Block block, Direction.Axis axis) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, axis) : state;
    }

    static BlockState facing(Block block, Direction facing) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    static BlockState hanging(Block lantern) {
        return lantern.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
    }

    static BlockState trapdoor(Block block, Direction facing, boolean open, Half half) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.OPEN, open).setValue(BlockStateProperties.HALF, half);
    }

    static BlockState wallTorch(Direction facing) {
        return Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }
}
