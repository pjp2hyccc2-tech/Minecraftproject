package com.eternalrealm.world.forge.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Shared helpers of the アストリア features. Every write goes through {@link #set}, which refuses positions outside
 * {@link Bounds}: the feature's own chunk plus {@link #MARGIN} blocks. The world generation region only allows writes in
 * the chunk being decorated and its direct neighbours, so this keeps every feature safely inside it.
 */
public final class FeatureKit {
    /** Blocks a feature may reach beyond the chunk it is placed in (the region allows up to 16). */
    public static final int MARGIN = 12;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private FeatureKit() {
    }

    /** The writable box of one feature placement. */
    public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public static Bounds of(WorldGenLevel level, BlockPos origin) {
            int chunkX = origin.getX() & ~15;
            int chunkZ = origin.getZ() & ~15;
            return new Bounds(chunkX - MARGIN, chunkX + 15 + MARGIN, level.getMinBuildHeight() + 1,
                    level.getMaxBuildHeight() - 1, chunkZ - MARGIN, chunkZ + 15 + MARGIN);
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** True if a square of {@code radius} around (x, z) fits completely. */
        public boolean fits(int x, int z, int radius) {
            return x - radius >= minX && x + radius <= maxX && z - radius >= minZ && z + radius <= maxZ;
        }
    }

    public static boolean set(WorldGenLevel level, Bounds bounds, int x, int y, int z, BlockState state) {
        if (!bounds.contains(x, y, z)) {
            return false;
        }
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
        return true;
    }

    /** Sets the block only if the current one is air or replaceable (plants, snow, fluids). */
    public static boolean setIfOpen(WorldGenLevel level, Bounds bounds, int x, int y, int z, BlockState state) {
        if (!bounds.contains(x, y, z) || !isOpen(get(level, x, y, z))) {
            return false;
        }
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
        return true;
    }

    public static BlockState get(WorldGenLevel level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    /** y of the top solid (non-fluid) block of the column. */
    public static int ground(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    /** y of the top non-air block of the column (water counts). */
    public static int surface(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
    }

    /** True if no fluid lies on top of the ground of the column. */
    public static boolean dry(WorldGenLevel level, int x, int z) {
        return surface(level, x, z) == ground(level, x, z);
    }

    /** Air, plants, snow layers and fluids: things a feature may overwrite. */
    public static boolean isOpen(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES);
    }

    /** Natural solid ground (not wood, not leaves, not plants). */
    public static boolean isTerrain(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()
                && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
    }

    @SafeVarargs
    public static <T> T pick(RandomSource random, T... options) {
        return options[random.nextInt(options.length)];
    }

    /** The block turned along {@code axis} if it has an axis (logs, pillars, basalt), else its default state. */
    public static BlockState axis(Block block, Direction.Axis axis) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, axis) : state;
    }

    /** Leaves that never decay (the trees of these features are not built by the vanilla tree code). */
    public static BlockState leaves(Block block) {
        return block.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
    }

    /** Height difference between the highest and the lowest ground in a square around (x, z). */
    public static int roughness(WorldGenLevel level, int x, int z, int radius) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
            for (int dz = -radius; dz <= radius; dz += Math.max(1, radius)) {
                int y = ground(level, x + dx, z + dz);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return max - min;
    }
}
