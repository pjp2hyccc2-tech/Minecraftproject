package com.eternalrealm.world.forge.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** A half-buried boulder (a squashed ellipsoid) with moss, snow or nothing on top, by area. */
public final class BoulderFeature extends Feature<VariantConfig> {
    public BoulderFeature(Codec<VariantConfig> codec) {
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
        if (FeatureKit.keepOut(x0, z0)) {
            return false;
        }
        if (!FeatureKit.dry(level, x0, z0)) {
            return false;
        }
        int ground = FeatureKit.ground(level, x0, z0);
        double size = 1.4 + random.nextDouble() * (variant.equals("basalt") || variant.equals("deepslate") ? 2.0 : 1.6);
        double rx = size * (0.8 + random.nextDouble() * 0.4);
        double rz = size * (0.8 + random.nextDouble() * 0.4);
        double ry = size * (0.6 + random.nextDouble() * 0.3);
        double cy = ground + ry * 0.35;
        int reach = (int) Math.ceil(Math.max(rx, rz)) + 1;
        if (!bounds.fits(x0, z0, reach)) {
            return false;
        }
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int topY = Integer.MIN_VALUE;
                for (int y = (int) Math.floor(cy - ry) - 1; y <= (int) Math.ceil(cy + ry); y++) {
                    double ex = dx / rx;
                    double ey = (y + 0.5 - cy) / ry;
                    double ez = dz / rz;
                    if (ex * ex + ey * ey + ez * ez <= 1.0) {
                        FeatureKit.set(level, bounds, x0 + dx, y, z0 + dz, block(variant, random));
                        topY = y;
                    }
                }
                if (topY != Integer.MIN_VALUE) {
                    decorateTop(level, bounds, random, variant, x0 + dx, topY + 1, z0 + dz);
                }
            }
        }
        return true;
    }

    private static void decorateTop(WorldGenLevel level, FeatureKit.Bounds bounds, RandomSource random, String variant,
                                    int x, int y, int z) {
        if (!FeatureKit.get(level, x, y, z).isAir()) {
            return;
        }
        if (variant.equals("mossy") && random.nextFloat() < 0.55F) {
            FeatureKit.set(level, bounds, x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
        } else if (variant.equals("snowy")) {
            FeatureKit.set(level, bounds, x, y, z,
                    Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1 + random.nextInt(2)));
        }
    }

    private static BlockState block(String variant, RandomSource random) {
        float roll = random.nextFloat();
        return (switch (variant) {
            case "sandstone" -> roll < 0.55F ? Blocks.SANDSTONE : roll < 0.85F ? Blocks.SMOOTH_SANDSTONE : Blocks.TERRACOTTA;
            case "snowy" -> roll < 0.5F ? Blocks.STONE : roll < 0.7F ? Blocks.ANDESITE : roll < 0.85F ? Blocks.PACKED_ICE
                    : Blocks.COBBLESTONE;
            case "basalt" -> roll < 0.4F ? Blocks.BASALT : roll < 0.75F ? Blocks.BLACKSTONE : roll < 0.9F
                    ? Blocks.SMOOTH_BASALT : Blocks.MAGMA_BLOCK;
            case "calcite" -> roll < 0.7F ? Blocks.CALCITE : roll < 0.9F ? Blocks.DIORITE : Blocks.QUARTZ_BLOCK;
            case "deepslate" -> roll < 0.4F ? Blocks.COBBLED_DEEPSLATE : roll < 0.7F ? Blocks.DEEPSLATE : roll < 0.9F
                    ? Blocks.BLACKSTONE : roll < 0.95F ? Blocks.TUFF : Blocks.CRYING_OBSIDIAN;
            default -> roll < 0.4F ? Blocks.MOSSY_COBBLESTONE : roll < 0.65F ? Blocks.COBBLESTONE : roll < 0.85F
                    ? Blocks.STONE : Blocks.ANDESITE;
        }).defaultBlockState();
    }
}
