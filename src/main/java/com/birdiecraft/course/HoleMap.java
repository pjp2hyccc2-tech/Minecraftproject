package com.birdiecraft.course;

import com.birdiecraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Top-down overview of one hole, sampled on the server and sent to clients for the HUD map.
 * Rows run from behind the tee (row 0) to beyond the cup; columns are lateral.
 */
public final class HoleMap {
    public static final byte UNKNOWN = 0, OB = 1, OB_SAND = 2, ROUGH = 3, DEEP = 4, FAIRWAY = 5, FAIRWAY_DARK = 6,
            FRINGE = 7, GREEN = 8, BUNKER = 9, WATER = 10, LAVA = 11, TEE = 12, TREE = 13, PATH = 14, CUP = 15,
            STAKE = 16, ICE = 17, SNOW = 18, ROCK = 19;

    public static final int CELL = 2;
    public static final int COLS = 44;

    public final double teeX, teeZ, axisX, axisZ;
    public final int rowStart;
    public final int cols, rows;
    public final byte[] codes;
    public final byte[] heights;

    public HoleMap(double teeX, double teeZ, double axisX, double axisZ, int rowStart, int cols, int rows, byte[] codes, byte[] heights) {
        this.teeX = teeX;
        this.teeZ = teeZ;
        this.axisX = axisX;
        this.axisZ = axisZ;
        this.rowStart = rowStart;
        this.cols = cols;
        this.rows = rows;
        this.codes = codes;
        this.heights = heights;
    }

    /** Converts a world position into map coordinates {col, row} (fractional). */
    public double[] toMap(double x, double z) {
        double dx = x - teeX;
        double dz = z - teeZ;
        double along = dx * axisX + dz * axisZ;
        double lateral = dx * (-axisZ) + dz * axisX;
        double col = lateral / CELL + cols / 2.0;
        double row = (along - rowStart) / CELL;
        return new double[]{col, row};
    }

    public static HoleMap build(ServerLevel level, Hole hole) {
        double tx = hole.teeX();
        double tz = hole.teeZ();
        double dx = hole.cupX() - tx;
        double dz = hole.cupZ() - tz;
        double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        double ax = dx / len;
        double az = dz / len;
        int rowStart = -12;
        int rows = (int) Math.ceil((len + 30) / CELL);
        int cols = COLS;
        byte[] codes = new byte[cols * rows];
        byte[] heights = new byte[cols * rows];
        int teeY = hole.tee.getY();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int r = 0; r < rows; r++) {
            double along = rowStart + (r + 0.5) * CELL;
            for (int c = 0; c < cols; c++) {
                double lateral = (c + 0.5 - cols / 2.0) * CELL;
                int x = (int) Math.floor(tx + ax * along - az * lateral);
                int z = (int) Math.floor(tz + az * along + ax * lateral);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                p.set(x, top, z);
                BlockState s = level.getBlockState(p);
                codes[r * cols + c] = classify(s);
                heights[r * cols + c] = (byte) Math.max(-8, Math.min(7, top - teeY));
            }
        }
        return new HoleMap(tx, tz, ax, az, rowStart, cols, rows, codes, heights);
    }

    private static byte classify(BlockState s) {
        Block b = s.getBlock();
        FluidState f = s.getFluidState();
        if (f.is(FluidTags.LAVA)) return LAVA;
        if (f.is(FluidTags.WATER)) return WATER;
        if (s.is(BlockTags.LEAVES)) return TREE;
        if (b == ModBlocks.FAIRWAY.get()) return FAIRWAY;
        if (b == ModBlocks.FAIRWAY_DARK.get()) return FAIRWAY_DARK;
        if (b == ModBlocks.GREEN.get()) return GREEN;
        if (b == ModBlocks.GOLF_CUP.get() || b == ModBlocks.GOLF_FLAG.get()) return CUP;
        if (b == ModBlocks.FRINGE.get()) return FRINGE;
        if (b == ModBlocks.TEE_BOX.get() || b == ModBlocks.TEE_MARKER.get()) return TEE;
        if (b == ModBlocks.ROUGH.get() || b == ModBlocks.ROUGH_DRY.get() || b == ModBlocks.ROUGH_SNOW.get() || b == ModBlocks.ROUGH_ASH.get()) return ROUGH;
        if (b == ModBlocks.DEEP_ROUGH.get()) return DEEP;
        if (b == ModBlocks.BUNKER_SAND.get() || b == ModBlocks.BUNKER_WHITE.get() || b == ModBlocks.BUNKER_RED.get() || b == ModBlocks.BUNKER_ASH.get()) return BUNKER;
        if (b == ModBlocks.CART_PATH.get()) return PATH;
        if (b == ModBlocks.OB_STAKE.get()) return STAKE;
        if (s.is(BlockTags.ICE)) return ICE;
        if (s.is(BlockTags.SAND)) return OB_SAND;
        if (s.is(BlockTags.LOGS)) return TREE;
        if (b == net.minecraft.world.level.block.Blocks.SNOW_BLOCK || b == net.minecraft.world.level.block.Blocks.SNOW) return SNOW;
        if (s.is(BlockTags.DIRT)) return OB;
        if (s.isAir()) return UNKNOWN;
        return ROCK;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(teeX);
        buf.writeDouble(teeZ);
        buf.writeDouble(axisX);
        buf.writeDouble(axisZ);
        buf.writeVarInt(rowStart);
        buf.writeVarInt(cols);
        buf.writeVarInt(rows);
        buf.writeByteArray(codes);
        buf.writeByteArray(heights);
    }

    public static HoleMap read(FriendlyByteBuf buf) {
        double tx = buf.readDouble();
        double tz = buf.readDouble();
        double ax = buf.readDouble();
        double az = buf.readDouble();
        int rs = buf.readVarInt();
        int cols = buf.readVarInt();
        int rows = buf.readVarInt();
        byte[] codes = buf.readByteArray();
        byte[] heights = buf.readByteArray();
        return new HoleMap(tx, tz, ax, az, rs, cols, rows, codes, heights);
    }
}
