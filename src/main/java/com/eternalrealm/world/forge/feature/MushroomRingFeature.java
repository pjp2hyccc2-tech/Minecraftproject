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

/** A fairy ring: a circle of red and brown mushrooms on mycelium around a small toadstool with a glowing heart. */
public final class MushroomRingFeature extends Feature<NoneFeatureConfiguration> {
    public MushroomRingFeature(Codec<NoneFeatureConfiguration> codec) {
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
        double radius = 3.0 + random.nextInt(3);
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, (int) radius + 2)) {
            return false;
        }
        int steps = (int) (radius * 7);
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            int x = x0 + (int) Math.round(Math.cos(angle) * radius);
            int z = z0 + (int) Math.round(Math.sin(angle) * radius);
            int y = FeatureKit.ground(level, x, z);
            if (!FeatureKit.dry(level, x, z) || !FeatureKit.isTerrain(FeatureKit.get(level, x, y, z))) {
                continue;
            }
            FeatureKit.set(level, bounds, x, y, z, Blocks.MYCELIUM.defaultBlockState());
            if (random.nextFloat() < 0.8F) {
                FeatureKit.setIfOpen(level, bounds, x, y + 1, z,
                        (random.nextFloat() < 0.6F ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
            }
        }
        // the toadstool in the middle
        int y = FeatureKit.ground(level, x0, z0);
        FeatureKit.set(level, bounds, x0, y, z0, Blocks.MYCELIUM.defaultBlockState());
        int stem = 2 + random.nextInt(2);
        for (int i = 1; i <= stem; i++) {
            FeatureKit.set(level, bounds, x0, y + i, z0, Blocks.MUSHROOM_STEM.defaultBlockState());
        }
        BlockState cap = Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                FeatureKit.setIfOpen(level, bounds, x0 + dx, y + stem + 1, z0 + dz, cap);
            }
        }
        FeatureKit.set(level, bounds, x0, y + stem + 1, z0, Blocks.SHROOMLIGHT.defaultBlockState());
        FeatureKit.setIfOpen(level, bounds, x0, y + stem + 2, z0, cap);
        return true;
    }
}
