package com.eternalrealm.world.forge.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A fenced field of the kingdom's farmlands: levelled farmland with one crop (wheat, carrots, potatoes or beetroots)
 * watered by a channel down the middle, a gate, lanterns on the corner posts, a scarecrow, hay bales and a composter.
 */
public final class FarmPlotFeature extends Feature<NoneFeatureConfiguration> {
    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};

    public FarmPlotFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        FeatureKit.Bounds bounds = FeatureKit.Bounds.of(level, origin);
        int x0 = origin.getX();
        int z0 = origin.getZ();
        if (FeatureKit.nearTower(x0, z0)) {
            return false;
        }
        int halfX = 4;
        int halfZ = 3 + random.nextInt(2);
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, 8) || FeatureKit.roughness(level, x0, z0, 5) > 2) {
            return false;
        }
        int y = FeatureKit.ground(level, x0, z0);
        for (int dx = -halfX - 1; dx <= halfX + 1; dx++) {
            for (int dz = -halfZ - 1; dz <= halfZ + 1; dz++) {
                if (!FeatureKit.dry(level, x0 + dx, z0 + dz)
                        || Math.abs(FeatureKit.ground(level, x0 + dx, z0 + dz) - y) > 2) {
                    return false;
                }
            }
        }
        Block crop = CROPS[random.nextInt(CROPS.length)];
        // level the plot and its fence ring
        for (int dx = -halfX - 1; dx <= halfX + 1; dx++) {
            for (int dz = -halfZ - 1; dz <= halfZ + 1; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int ground = FeatureKit.ground(level, x, z);
                for (int fill = ground + 1; fill < y; fill++) {
                    FeatureKit.set(level, bounds, x, fill, z, Blocks.DIRT.defaultBlockState());
                }
                for (int clear = y + 1; clear <= Math.max(ground, y) + 2; clear++) {
                    FeatureKit.set(level, bounds, x, clear, z, Blocks.AIR.defaultBlockState());
                }
                boolean ring = Math.abs(dx) == halfX + 1 || Math.abs(dz) == halfZ + 1;
                if (ring) {
                    FeatureKit.set(level, bounds, x, y, z, Blocks.DIRT_PATH.defaultBlockState());
                    continue;
                }
                FeatureKit.set(level, bounds, x, y - 1, z, Blocks.DIRT.defaultBlockState());
                if (dx == 0) {
                    FeatureKit.set(level, bounds, x, y, z, Blocks.WATER.defaultBlockState());
                } else {
                    FeatureKit.set(level, bounds, x, y, z,
                            Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
                    FeatureKit.set(level, bounds, x, y + 1, z, crop(crop, random));
                }
            }
        }
        // fence ring with a gate on the north side
        for (int dx = -halfX - 1; dx <= halfX + 1; dx++) {
            for (int dz = -halfZ - 1; dz <= halfZ + 1; dz++) {
                boolean ring = Math.abs(dx) == halfX + 1 || Math.abs(dz) == halfZ + 1;
                if (!ring) {
                    continue;
                }
                if (dx == 0 && dz == -halfZ - 1) {
                    FeatureKit.set(level, bounds, x0, y + 1, z0 + dz, Blocks.OAK_FENCE_GATE.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
                    continue;
                }
                BlockState fence = Blocks.OAK_FENCE.defaultBlockState()
                        .setValue(BlockStateProperties.NORTH, isRing(dx, dz - 1, halfX, halfZ))
                        .setValue(BlockStateProperties.SOUTH, isRing(dx, dz + 1, halfX, halfZ))
                        .setValue(BlockStateProperties.WEST, isRing(dx - 1, dz, halfX, halfZ))
                        .setValue(BlockStateProperties.EAST, isRing(dx + 1, dz, halfX, halfZ));
                FeatureKit.set(level, bounds, x0 + dx, y + 1, z0 + dz, fence);
                boolean corner = Math.abs(dx) == halfX + 1 && Math.abs(dz) == halfZ + 1;
                if (corner && dx * dz > 0) {
                    FeatureKit.set(level, bounds, x0 + dx, y + 2, z0 + dz, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        // scarecrow, hay and a composter outside the fence
        int sx = x0 + halfX + 3;
        int sy = FeatureKit.ground(level, sx, z0) + 1;
        if (FeatureKit.dry(level, sx, z0) && Math.abs(sy - 1 - y) <= 2) {
            FeatureKit.set(level, bounds, sx, sy, z0, Blocks.OAK_FENCE.defaultBlockState());
            FeatureKit.set(level, bounds, sx, sy + 1, z0, FeatureKit.axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
            FeatureKit.set(level, bounds, sx, sy + 2, z0, Blocks.CARVED_PUMPKIN.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        }
        int hx = x0 - halfX - 3;
        int hy = FeatureKit.ground(level, hx, z0 - 1) + 1;
        if (FeatureKit.dry(level, hx, z0 - 1) && Math.abs(hy - 1 - y) <= 2) {
            FeatureKit.set(level, bounds, hx, hy, z0 - 1, FeatureKit.axis(Blocks.HAY_BLOCK, Direction.Axis.X));
            FeatureKit.set(level, bounds, hx, hy, z0, FeatureKit.axis(Blocks.HAY_BLOCK, Direction.Axis.X));
            FeatureKit.setIfOpen(level, bounds, hx, hy + 1, z0 - 1, FeatureKit.axis(Blocks.HAY_BLOCK, Direction.Axis.Z));
            FeatureKit.setIfOpen(level, bounds, hx, hy, z0 + 1, Blocks.COMPOSTER.defaultBlockState());
        }
        return true;
    }

    private static boolean isRing(int dx, int dz, int halfX, int halfZ) {
        boolean inside = Math.abs(dx) <= halfX + 1 && Math.abs(dz) <= halfZ + 1;
        return inside && (Math.abs(dx) == halfX + 1 || Math.abs(dz) == halfZ + 1);
    }

    private static BlockState crop(Block crop, RandomSource random) {
        if (crop == Blocks.BEETROOTS) {
            return crop.defaultBlockState().setValue(BlockStateProperties.AGE_3, 1 + random.nextInt(3));
        }
        return crop.defaultBlockState().setValue(BlockStateProperties.AGE_7, 3 + random.nextInt(5));
    }
}
