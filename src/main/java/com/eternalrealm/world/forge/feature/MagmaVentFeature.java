package com.eternalrealm.world.forge.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A small volcanic cone of basalt and blackstone with a magma-crusted crater: a lava eye in the middle and a hidden
 * signal campfire beside it, so a column of smoke rises from the vent.
 */
public final class MagmaVentFeature extends Feature<NoneFeatureConfiguration> {
    public MagmaVentFeature(Codec<NoneFeatureConfiguration> codec) {
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
        int radius = 3 + random.nextInt(3);
        int height = 3 + random.nextInt(4);
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, radius + 2)
                || FeatureKit.roughness(level, x0, z0, radius) > 4) {
            return false;
        }
        int base = FeatureKit.ground(level, x0, z0);
        int craterTop = base + height;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.8) {
                    continue;
                }
                boolean crater = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                int top = crater ? craterTop : base + (int) Math.round(height * Math.pow(1.0 - dist / (radius + 1.0), 1.2));
                int ground = FeatureKit.ground(level, x0 + dx, z0 + dz);
                for (int y = ground + 1; y <= top; y++) {
                    FeatureKit.set(level, bounds, x0 + dx, y, z0 + dz, cone(random, top - y, crater));
                }
            }
        }
        // the crater: a lava eye, magma around it and a signal campfire in one corner for the smoke
        FeatureKit.set(level, bounds, x0, craterTop - 1, z0, Blocks.MAGMA_BLOCK.defaultBlockState());
        FeatureKit.set(level, bounds, x0, craterTop, z0, Blocks.LAVA.defaultBlockState());
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        FeatureKit.set(level, bounds, x0 + side.getStepX(), craterTop, z0 + side.getStepZ(),
                Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.SIGNAL_FIRE, true));
        for (Direction other : Direction.Plane.HORIZONTAL) {
            if (other != side) {
                FeatureKit.set(level, bounds, x0 + other.getStepX(), craterTop, z0 + other.getStepZ(),
                        Blocks.MAGMA_BLOCK.defaultBlockState());
            }
        }
        for (int cx = -1; cx <= 1; cx += 2) {
            for (int cz = -1; cz <= 1; cz += 2) {
                FeatureKit.set(level, bounds, x0 + cx, craterTop + 1, z0 + cz, Blocks.BLACKSTONE.defaultBlockState());
            }
        }
        // scattered magma and ash around the foot
        for (int i = 0; i < 6; i++) {
            int x = x0 + random.nextInt(radius * 2 + 5) - radius - 2;
            int z = z0 + random.nextInt(radius * 2 + 5) - radius - 2;
            int y = FeatureKit.ground(level, x, z);
            if (FeatureKit.isTerrain(FeatureKit.get(level, x, y, z))) {
                FeatureKit.set(level, bounds, x, y, z,
                        (random.nextBoolean() ? Blocks.MAGMA_BLOCK : Blocks.SMOOTH_BASALT).defaultBlockState());
            }
        }
        return true;
    }

    private static BlockState cone(RandomSource random, int belowTop, boolean crater) {
        float roll = random.nextFloat();
        if (belowTop == 0 && (crater || roll < 0.35F)) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (roll < 0.45F) {
            return Blocks.BASALT.defaultBlockState();
        }
        return (roll < 0.8F ? Blocks.BLACKSTONE : Blocks.SMOOTH_BASALT).defaultBlockState();
    }
}
