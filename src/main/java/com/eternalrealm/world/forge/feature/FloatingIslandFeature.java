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
import net.minecraft.world.level.material.Fluids;

/**
 * A floating island of セレスティア: an irregular grassy top over an inverted cone of stone and calcite with hanging
 * roots, flowers, a tree (white, cherry) or crystals, and often a waterfall pouring from its edge down to the ground.
 */
public final class FloatingIslandFeature extends Feature<VariantConfig> {
    private static final Block[] FLOWERS = {Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.LILY_OF_THE_VALLEY,
            Blocks.CORNFLOWER, Blocks.DANDELION, Blocks.WHITE_TULIP};

    public FloatingIslandFeature(Codec<VariantConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<VariantConfig> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        String variant = context.config().variant();
        FeatureKit.Bounds bounds = FeatureKit.Bounds.of(level, origin);
        int x0 = origin.getX();
        int z0 = origin.getZ();
        if (FeatureKit.nearTower(x0, z0)) {
            return false;
        }
        int radius = 5 + random.nextInt(5);
        if (!bounds.fits(x0, z0, 12)) {
            return false;
        }
        int highest = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                highest = Math.max(highest, FeatureKit.surface(level, x0 + dx, z0 + dz));
            }
        }
        int depth = (int) (radius * 0.9) + 3;
        int top = highest + 20 + random.nextInt(30) + depth;
        if (top + 16 >= bounds.maxY()) {
            return false;
        }
        double phase = random.nextDouble() * Math.PI * 2.0;
        double phase2 = random.nextDouble() * Math.PI * 2.0;
        int reach = radius + 2;
        int[][] tops = new int[reach * 2 + 1][reach * 2 + 1];
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                tops[dx + reach][dz + reach] = Integer.MIN_VALUE;
                double angle = Math.atan2(dz, dx);
                double edge = radius * (1.0 + 0.15 * Math.sin(angle * 3.0 + phase) + 0.08 * Math.sin(angle * 5.0 + phase2));
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > edge) {
                    continue;
                }
                double t = dist / edge;
                int columnTop = top + (t < 0.4 ? 1 : 0);
                int columnDepth = (int) Math.round((1.0 - t * t) * depth) + 1;
                if (t < 0.5 && random.nextFloat() < 0.3F) {
                    columnDepth += 1 + random.nextInt(3);
                }
                for (int y = columnTop - columnDepth; y <= columnTop; y++) {
                    BlockState state = y == columnTop ? top(variant, random) : y >= columnTop - 2 ? Blocks.DIRT.defaultBlockState()
                            : core(variant, random);
                    FeatureKit.set(level, bounds, x0 + dx, y, z0 + dz, state);
                }
                if (random.nextFloat() < 0.18F) {
                    FeatureKit.set(level, bounds, x0 + dx, columnTop - columnDepth - 1, z0 + dz,
                            Blocks.HANGING_ROOTS.defaultBlockState());
                }
                tops[dx + reach][dz + reach] = columnTop;
            }
        }
        // ground cover
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int y = tops[dx + reach][dz + reach];
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                cover(level, bounds, random, variant, x0 + dx, y, z0 + dz);
            }
        }
        // tree or crystals near the middle
        int tx = x0 + random.nextInt(3) - 1;
        int tz = z0 + random.nextInt(3) - 1;
        int treeBase = top + 2;
        if (FeatureKit.get(level, tx, treeBase - 1, tz).is(Blocks.GRASS_BLOCK)) {
            FeatureKit.set(level, bounds, tx, treeBase, tz, Blocks.AIR.defaultBlockState());
            switch (variant) {
                case "cherry" -> TreeShapes.cherry(level, bounds, random, tx, treeBase, tz);
                case "crystal" -> {
                    if (random.nextBoolean()) {
                        TreeShapes.white(level, bounds, random, tx, treeBase, tz);
                    }
                }
                default -> {
                    if (random.nextFloat() < 0.7F) {
                        TreeShapes.white(level, bounds, random, tx, treeBase, tz);
                    }
                }
            }
        }
        // waterfall from the rim
        if (random.nextFloat() < 0.45F) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            for (int r = radius + 1; r > 1; r--) {
                int wx = (int) Math.round(Math.cos(angle) * r);
                int wz = (int) Math.round(Math.sin(angle) * r);
                if (Math.abs(wx) > reach || Math.abs(wz) > reach) {
                    continue;
                }
                int y = tops[wx + reach][wz + reach];
                if (y != Integer.MIN_VALUE) {
                    BlockPos source = new BlockPos(x0 + wx, y, z0 + wz);
                    if (FeatureKit.set(level, bounds, source.getX(), y, source.getZ(), Blocks.WATER.defaultBlockState())) {
                        FeatureKit.set(level, bounds, source.getX(), y + 1, source.getZ(), Blocks.AIR.defaultBlockState());
                        level.scheduleTick(source, Fluids.WATER, 0);
                    }
                    break;
                }
            }
        }
        return true;
    }

    private static void cover(WorldGenLevel level, FeatureKit.Bounds bounds, RandomSource random, String variant, int x,
                              int y, int z) {
        if (!FeatureKit.get(level, x, y + 1, z).isAir()) {
            return;
        }
        BlockState ground = FeatureKit.get(level, x, y, z);
        float roll = random.nextFloat();
        if (ground.is(Blocks.GRASS_BLOCK)) {
            if (variant.equals("cherry") && roll < 0.3F) {
                FeatureKit.set(level, bounds, x, y + 1, z, Blocks.PINK_PETALS.defaultBlockState()
                        .setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + random.nextInt(4))
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
            } else if (roll < 0.14F) {
                FeatureKit.set(level, bounds, x, y + 1, z, FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState());
            } else if (roll < 0.4F) {
                FeatureKit.set(level, bounds, x, y + 1, z, Blocks.GRASS.defaultBlockState());
            }
        } else if (ground.is(Blocks.CALCITE) && roll < 0.25F) {
            BlockState crystal = random.nextBoolean()
                    ? Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP)
                    : Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
            FeatureKit.set(level, bounds, x, y + 1, z, crystal);
        }
    }

    private static BlockState top(String variant, RandomSource random) {
        if (variant.equals("crystal") && random.nextFloat() < 0.4F) {
            return Blocks.CALCITE.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }

    private static BlockState core(String variant, RandomSource random) {
        float roll = random.nextFloat();
        if (variant.equals("crystal")) {
            return (roll < 0.5F ? Blocks.CALCITE : roll < 0.65F ? Blocks.AMETHYST_BLOCK : roll < 0.8F ? Blocks.QUARTZ_BLOCK
                    : Blocks.DIORITE).defaultBlockState();
        }
        return (roll < 0.5F ? Blocks.STONE : roll < 0.75F ? Blocks.ANDESITE : Blocks.CALCITE).defaultBlockState();
    }
}
