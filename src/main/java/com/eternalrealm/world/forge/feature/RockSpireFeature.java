package com.eternalrealm.world.forge.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * A tapering, slightly leaning rock spire rooted a few blocks into the ground: sandstone needles in the dunes, banded
 * terracotta pillars on the mesa, packed-ice fangs, basalt columns, obsidian spikes, calcite towers in the sky and the
 * dark spires of the wasteland.
 */
public final class RockSpireFeature extends Feature<VariantConfig> {
    private static final Block[] BANDS = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA,
            Blocks.TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.WHITE_TERRACOTTA,
            Blocks.ORANGE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.RED_TERRACOTTA};

    public RockSpireFeature(Codec<VariantConfig> codec) {
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
        if (!FeatureKit.dry(level, x0, z0)) {
            return false;
        }
        int base = FeatureKit.ground(level, x0, z0);
        boolean slender = variant.equals("dark") || variant.equals("obsidian") || variant.equals("ice");
        int height = slender ? 14 + random.nextInt(16) : variant.equals("terracotta") ? 10 + random.nextInt(18)
                : 8 + random.nextInt(13);
        double radius = slender ? 1.3 + random.nextDouble() * 1.2 : 1.8 + random.nextDouble() * 1.7;
        double leanX = (random.nextDouble() - 0.5) * 0.18;
        double leanZ = (random.nextDouble() - 0.5) * 0.18;
        int reach = (int) Math.ceil(radius + (Math.abs(leanX) + Math.abs(leanZ)) * height) + 1;
        if (!bounds.fits(x0, z0, reach) || base + height + 2 >= bounds.maxY()) {
            return false;
        }
        for (int dy = -3; dy <= height; dy++) {
            double t = Math.max(0, dy) / (double) height;
            double r = radius * Math.pow(1.0 - t, 0.8) + 0.3;
            double cx = x0 + 0.5 + leanX * Math.max(0, dy);
            double cz = z0 + 0.5 + leanZ * Math.max(0, dy);
            int ir = (int) Math.ceil(r);
            for (int x = (int) Math.floor(cx) - ir; x <= (int) Math.floor(cx) + ir; x++) {
                for (int z = (int) Math.floor(cz) - ir; z <= (int) Math.floor(cz) + ir; z++) {
                    double ddx = x + 0.5 - cx;
                    double ddz = z + 0.5 - cz;
                    if (ddx * ddx + ddz * ddz <= r * r) {
                        int y = base + dy;
                        FeatureKit.set(level, bounds, x, y, z, block(variant, random, y, dy, height));
                    }
                }
            }
        }
        return true;
    }

    private static BlockState block(String variant, RandomSource random, int y, int dy, int height) {
        float roll = random.nextFloat();
        boolean top = dy >= height - 1;
        return switch (variant) {
            case "sandstone" -> (Math.floorMod(y, 5) == 0 ? Blocks.CUT_SANDSTONE
                    : roll < 0.7F ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
            case "terracotta" -> top ? Blocks.RED_SAND.defaultBlockState()
                    : BANDS[Math.floorMod(y, BANDS.length)].defaultBlockState();
            case "ice" -> top ? Blocks.SNOW_BLOCK.defaultBlockState()
                    : (roll < 0.7F ? Blocks.PACKED_ICE : roll < 0.92F ? Blocks.BLUE_ICE : Blocks.SNOW_BLOCK).defaultBlockState();
            case "basalt" -> roll < 0.6F ? FeatureKit.axis(Blocks.BASALT, Direction.Axis.Y)
                    : (roll < 0.8F ? Blocks.SMOOTH_BASALT : roll < 0.95F ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK).defaultBlockState();
            case "obsidian" -> (dy < 2 ? (roll < 0.7F ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK)
                    : roll < 0.65F ? Blocks.OBSIDIAN : roll < 0.8F ? Blocks.CRYING_OBSIDIAN : Blocks.BLACKSTONE).defaultBlockState();
            case "calcite" -> (top ? Blocks.QUARTZ_BLOCK
                    : roll < 0.65F ? Blocks.CALCITE : roll < 0.85F ? Blocks.DIORITE : Blocks.SMOOTH_QUARTZ).defaultBlockState();
            case "dark" -> (top ? Blocks.CRYING_OBSIDIAN
                    : roll < 0.3F ? Blocks.DEEPSLATE : roll < 0.5F ? Blocks.COBBLED_DEEPSLATE : roll < 0.75F ? Blocks.BLACKSTONE
                    : roll < 0.9F ? Blocks.OBSIDIAN : Blocks.CRYING_OBSIDIAN).defaultBlockState();
            default -> (roll < 0.6F ? Blocks.STONE : roll < 0.8F ? Blocks.ANDESITE : roll < 0.9F ? Blocks.COBBLESTONE
                    : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
        };
    }
}
