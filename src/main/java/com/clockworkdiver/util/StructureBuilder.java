package com.clockworkdiver.util;

import com.clockworkdiver.block.MechanismBlockEntity;
import com.clockworkdiver.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;

/** Places blocks relative to an origin quickly (no neighbour updates) and fixes connecting blocks afterwards. */
public final class StructureBuilder {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public final ServerLevel level;
    public final BlockPos origin;
    private final List<BlockPos> shapeFix = new ArrayList<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    public int placed;

    public StructureBuilder(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
    }

    public BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    public void set(int x, int y, int z, BlockState state) {
        cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        level.setBlock(cursor, state, FLAGS);
        placed++;
    }

    public void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    /** Places a block whose look depends on its neighbours (panes, fences, walls) and fixes it in {@link #finish()}. */
    public void setConnected(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
        shapeFix.add(abs(x, y, z));
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(abs(x, y, z));
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                    set(x, y, z, state);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
    }

    /** Places a mechanism hub and returns its block entity for configuration (call {@code sync()} afterwards). */
    public MechanismBlockEntity mechanism(int x, int y, int z) {
        set(x, y, z, ModBlocks.MECHANISM.get().defaultBlockState());
        BlockEntity be = level.getBlockEntity(abs(x, y, z));
        if (be instanceof MechanismBlockEntity m) return m;
        throw new IllegalStateException("Mechanism block entity missing at " + abs(x, y, z));
    }

    /** Sets every non-air block in the box to air, skipping empty chunk sections. */
    public void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        int ax = origin.getX() + Math.min(x0, x1), bx = origin.getX() + Math.max(x0, x1);
        int ay = origin.getY() + Math.min(y0, y1), by = origin.getY() + Math.max(y0, y1);
        int az = origin.getZ() + Math.min(z0, z1), bz = origin.getZ() + Math.max(z0, z1);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = ax >> 4; cx <= bx >> 4; cx++) {
            for (int cz = az >> 4; cz <= bz >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) continue;
                    int sy = chunk.getSectionYFromSectionIndex(si) << 4;
                    if (sy + 15 < ay || sy > by) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        int y = sy + ly;
                        if (y < ay || y > by) continue;
                        for (int lx = 0; lx < 16; lx++) {
                            int x = (cx << 4) + lx;
                            if (x < ax || x > bx) continue;
                            for (int lz = 0; lz < 16; lz++) {
                                int z = (cz << 4) + lz;
                                if (z < az || z > bz) continue;
                                if (!section.getBlockState(lx, ly, lz).isAir()) {
                                    m.set(x, y, z);
                                    level.setBlock(m, air, FLAGS);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Lets panes, fences and similar blocks connect to their neighbours. */
    public void finish() {
        for (BlockPos pos : shapeFix) {
            BlockState state = level.getBlockState(pos);
            BlockState fixed = Block.updateFromNeighbourShapes(state, level, pos);
            if (fixed != state) level.setBlock(pos, fixed, FLAGS);
        }
        shapeFix.clear();
    }
}
