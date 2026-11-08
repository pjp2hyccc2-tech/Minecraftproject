package com.eternalrealm.world.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Prepares the ground of a code-built place: loads its chunks, levels a pad at a fixed height, eases the ground around
 * it back to the natural terrain and removes the trees and plants standing on it.
 */
final class TerrainShaper {
    /** Extra ring around the blend zone where trees (logs, leaves) are removed so nothing is left floating. */
    private static final int TREE_MARGIN = 8;

    private TerrainShaper() {
    }

    /** True if every chunk within {@code radius} of (cx, cz) is loaded already (nothing would be generated). */
    static boolean chunksLoaded(ServerLevel level, int cx, int cz, int radius) {
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Generates and loads every chunk within {@code radius} of (cx, cz) (synchronous, server thread). */
    static void loadChunks(ServerLevel level, int cx, int cz, int radius) {
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                level.getChunk(x, z);
            }
        }
    }

    /**
     * Levels the ground around (cx, cz): within {@code core} (square or round) the top is exactly {@code groundY};
     * between {@code core} and {@code blend} it eases back to the natural height.
     *
     * @param top    surface block of the levelled ground
     * @param filler blocks under the surface (and the surface under water)
     */
    static void level(ServerLevel level, int cx, int cz, int core, int blend, boolean square, int groundY, BlockState top,
                      BlockState filler) {
        level(level, cx, cz, core, blend, square, groundY, top, filler, (dx, dz) -> false);
    }

    /** Columns (offsets from the centre) that the levelling must leave untouched, e.g. an existing structure. */
    @FunctionalInterface
    interface Keep {
        boolean keep(int dx, int dz);
    }

    static void level(ServerLevel level, int cx, int cz, int core, int blend, boolean square, int groundY, BlockState top,
                      BlockState filler, Keep keep) {
        int outer = blend + TREE_MARGIN;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - outer; x <= cx + outer; x++) {
            for (int z = cz - outer; z <= cz + outer; z++) {
                int dx = x - cx;
                int dz = z - cz;
                if (keep.keep(dx, dz)) {
                    continue;
                }
                double dist = square ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.sqrt(dx * dx + dz * dz);
                int surfaceTop = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int terrainTop = surfaceTop;
                int waterTop = Integer.MIN_VALUE;
                while (terrainTop > level.getMinBuildHeight()) {
                    BlockState state = level.getBlockState(pos.set(x, terrainTop, z));
                    if (!state.getFluidState().isEmpty() && waterTop == Integer.MIN_VALUE) {
                        waterTop = terrainTop;
                    }
                    if (!isLoose(state)) {
                        break;
                    }
                    terrainTop--;
                }
                if (dist > blend) {
                    // outside the pad: only remove trees that might have been cut by the levelling
                    if (dist <= outer) {
                        for (int y = terrainTop + 1; y <= surfaceTop; y++) {
                            BlockState state = level.getBlockState(pos.set(x, y, z));
                            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.VINE)) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Painter.FLAGS);
                            }
                        }
                    }
                    continue;
                }
                boolean core0 = dist <= core;
                int target;
                if (core0) {
                    target = groundY;
                } else {
                    double t = (dist - core) / (double) (blend - core);
                    double eased = t * t * (3.0 - 2.0 * t);
                    target = (int) Math.round(groundY + (terrainTop - groundY) * eased);
                }
                // clear everything above the target (keep the sea in the blend zone)
                for (int y = target + 1; y <= Math.max(surfaceTop, target + 1); y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || (!core0 && !state.getFluidState().isEmpty() && isSafeFluid(state))) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Painter.FLAGS);
                }
                // fill up to the target
                for (int y = terrainTop + 1; y < target; y++) {
                    level.setBlock(pos.set(x, y, z), filler, Painter.FLAGS);
                }
                boolean underwater = !core0 && waterTop != Integer.MIN_VALUE && target < waterTop;
                if (target != terrainTop || core0) {
                    level.setBlock(pos.set(x, target, z), underwater ? filler : top, Painter.FLAGS);
                }
            }
        }
    }

    /** Blocks that are not part of the ground: plants, trees, snow layers, fluids. */
    static boolean isLoose(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES) || state.is(Blocks.VINE) || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.SHROOMLIGHT) || state.is(Blocks.COCOA) || state.is(Blocks.BEE_NEST)
                || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.COBWEB) || state.is(Blocks.END_ROD)
                || state.is(Blocks.HANGING_ROOTS) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO);
    }

    private static boolean isSafeFluid(BlockState state) {
        return state.is(Blocks.WATER);
    }
}
