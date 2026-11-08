package com.eternalrealm.world.forge.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A crack into the void: a narrow, meandering chasm, deepest in the middle, lined with obsidian, crying obsidian and
 * blackstone, with end stone and obsidian shards scattered along its lips.
 */
public final class VoidCrackFeature extends Feature<NoneFeatureConfiguration> {
    private static final int SPAN = 12;

    public VoidCrackFeature(Codec<NoneFeatureConfiguration> codec) {
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
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, SPAN)) {
            return false;
        }
        // original ground heights, so overlapping steps of the path do not dig deeper and deeper
        int[][] ground = new int[SPAN * 2 + 1][SPAN * 2 + 1];
        for (int dx = -SPAN; dx <= SPAN; dx++) {
            for (int dz = -SPAN; dz <= SPAN; dz++) {
                ground[dx + SPAN][dz + SPAN] = FeatureKit.ground(level, x0 + dx, z0 + dz);
            }
        }
        double angle = random.nextDouble() * Math.PI;
        double half = 6.0 + random.nextInt(5);
        double phase = random.nextDouble() * Math.PI * 2.0;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (double t = -half; t <= half; t += 0.5) {
            double wobble = 1.4 * Math.sin(t * 0.55 + phase);
            double cx = x0 + 0.5 + cos * t - sin * wobble;
            double cz = z0 + 0.5 + sin * t + cos * wobble;
            double f = 1.0 - (t / half) * (t / half);
            double width = 0.6 + 1.3 * f;
            int depth = 4 + (int) (16.0 * f);
            int r = (int) Math.ceil(width + 1.0);
            for (int bx = (int) Math.floor(cx) - r; bx <= (int) Math.floor(cx) + r; bx++) {
                for (int bz = (int) Math.floor(cz) - r; bz <= (int) Math.floor(cz) + r; bz++) {
                    int gx = bx - x0 + SPAN;
                    int gz = bz - z0 + SPAN;
                    if (gx < 0 || gz < 0 || gx > SPAN * 2 || gz > SPAN * 2) {
                        continue;
                    }
                    double dd = Math.sqrt((bx + 0.5 - cx) * (bx + 0.5 - cx) + (bz + 0.5 - cz) * (bz + 0.5 - cz));
                    int top = ground[gx][gz];
                    int bottom = Math.max(bounds.minY() + 4, top - depth);
                    if (dd <= width) {
                        for (int y = top; y > bottom; y--) {
                            FeatureKit.set(level, bounds, bx, y, bz, Blocks.AIR.defaultBlockState());
                        }
                        if (FeatureKit.isTerrain(FeatureKit.get(level, bx, bottom, bz))) {
                            FeatureKit.set(level, bounds, bx, bottom, bz, lining(random));
                        }
                    } else if (dd <= width + 1.0) {
                        for (int y = top; y >= bottom; y--) {
                            if (FeatureKit.isTerrain(FeatureKit.get(level, bx, y, bz))) {
                                FeatureKit.set(level, bounds, bx, y, bz, lining(random));
                            }
                        }
                    }
                }
            }
        }
        // shards along the lips
        for (int i = 0; i < 8; i++) {
            double t = (random.nextDouble() * 2.0 - 1.0) * half;
            double side = (random.nextBoolean() ? 1.0 : -1.0) * (2.5 + random.nextDouble() * 2.0);
            int x = (int) Math.floor(x0 + 0.5 + cos * t - sin * side);
            int z = (int) Math.floor(z0 + 0.5 + sin * t + cos * side);
            int y = FeatureKit.ground(level, x, z);
            if (FeatureKit.isTerrain(FeatureKit.get(level, x, y, z))) {
                FeatureKit.setIfOpen(level, bounds, x, y + 1, z,
                        (random.nextBoolean() ? Blocks.OBSIDIAN : Blocks.END_STONE).defaultBlockState());
            }
        }
        return true;
    }

    private static BlockState lining(RandomSource random) {
        float roll = random.nextFloat();
        return (roll < 0.45F ? Blocks.OBSIDIAN : roll < 0.75F ? Blocks.BLACKSTONE : Blocks.CRYING_OBSIDIAN).defaultBlockState();
    }
}
