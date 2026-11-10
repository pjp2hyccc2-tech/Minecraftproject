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

/**
 * A cluster of crystal shards bursting out of a small bed: amethyst in the snow hollows and mushroom glades, ice in the
 * snowfield, glowing magma in the volcano, quartz with light rods in the sky, crying obsidian in the void rifts.
 */
public final class CrystalClusterFeature extends Feature<VariantConfig> {
    public CrystalClusterFeature(Codec<VariantConfig> codec) {
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
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, 9)) {
            return false;
        }
        int ground = FeatureKit.ground(level, x0, z0);
        // bed
        int bedRadius = 2 + random.nextInt(2);
        for (int dx = -bedRadius; dx <= bedRadius; dx++) {
            for (int dz = -bedRadius; dz <= bedRadius; dz++) {
                if (dx * dx + dz * dz > bedRadius * bedRadius + 1) {
                    continue;
                }
                int y = FeatureKit.ground(level, x0 + dx, z0 + dz);
                if (FeatureKit.isTerrain(FeatureKit.get(level, x0 + dx, y, z0 + dz))) {
                    FeatureKit.set(level, bounds, x0 + dx, y, z0 + dz, bed(variant, random));
                }
            }
        }
        // shards: one tall central shard and several shorter leaning ones
        int shards = 3 + random.nextInt(4);
        for (int i = 0; i < shards; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double rise = i == 0 ? 1.0 : 0.45 + random.nextDouble() * 0.5;
            double spread = i == 0 ? 0.08 : Math.sqrt(1.0 - rise * rise) + 0.15;
            double dirX = Math.cos(angle) * spread;
            double dirZ = Math.sin(angle) * spread;
            double length = i == 0 ? 6 + random.nextInt(5) : 3 + random.nextInt(4);
            double startX = x0 + 0.5 + (i == 0 ? 0 : Math.cos(angle) * 0.8);
            double startZ = z0 + 0.5 + (i == 0 ? 0 : Math.sin(angle) * 0.8);
            double startY = ground + 0.5;
            int lastX = x0;
            int lastY = ground;
            int lastZ = z0;
            for (double t = 0.0; t <= length; t += 0.5) {
                double thickness = t < length * 0.5 ? (i == 0 ? 1.1 : 0.8) : 0.45;
                double px = startX + dirX * t;
                double py = startY + rise * t;
                double pz = startZ + dirZ * t;
                int r = (int) Math.ceil(thickness);
                for (int ox = -r; ox <= r; ox++) {
                    for (int oy = -r; oy <= r; oy++) {
                        for (int oz = -r; oz <= r; oz++) {
                            int bx = (int) Math.floor(px) + ox;
                            int by = (int) Math.floor(py) + oy;
                            int bz = (int) Math.floor(pz) + oz;
                            double ddx = bx + 0.5 - px;
                            double ddy = by + 0.5 - py;
                            double ddz = bz + 0.5 - pz;
                            if (ddx * ddx + ddy * ddy + ddz * ddz <= thickness * thickness + 0.25) {
                                FeatureKit.set(level, bounds, bx, by, bz, shard(variant, random));
                            }
                        }
                    }
                }
                lastX = (int) Math.floor(px);
                lastY = (int) Math.floor(py);
                lastZ = (int) Math.floor(pz);
            }
            FeatureKit.setIfOpen(level, bounds, lastX, lastY + 1, lastZ, tip(variant, random));
        }
        return true;
    }

    private static BlockState bed(String variant, RandomSource random) {
        return (switch (variant) {
            case "ice" -> random.nextBoolean() ? Blocks.PACKED_ICE : Blocks.SNOW_BLOCK;
            case "magma" -> random.nextFloat() < 0.7F ? Blocks.BLACKSTONE : Blocks.MAGMA_BLOCK;
            case "quartz" -> random.nextFloat() < 0.8F ? Blocks.CALCITE : Blocks.QUARTZ_BLOCK;
            case "void" -> random.nextFloat() < 0.6F ? Blocks.OBSIDIAN : Blocks.BLACKSTONE;
            default -> random.nextFloat() < 0.7F ? Blocks.CALCITE : Blocks.SMOOTH_BASALT;
        }).defaultBlockState();
    }

    private static BlockState shard(String variant, RandomSource random) {
        float roll = random.nextFloat();
        return switch (variant) {
            case "ice" -> (roll < 0.55F ? Blocks.PACKED_ICE : Blocks.BLUE_ICE).defaultBlockState();
            case "magma" -> (roll < 0.6F ? Blocks.MAGMA_BLOCK : roll < 0.85F ? Blocks.BLACKSTONE : Blocks.SHROOMLIGHT)
                    .defaultBlockState();
            case "quartz" -> roll < 0.5F ? Blocks.QUARTZ_BLOCK.defaultBlockState()
                    : roll < 0.8F ? FeatureKit.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y)
                    : Blocks.SMOOTH_QUARTZ.defaultBlockState();
            case "void" -> (roll < 0.6F ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState();
            default -> (roll < 0.85F ? Blocks.AMETHYST_BLOCK : Blocks.BUDDING_AMETHYST).defaultBlockState();
        };
    }

    private static BlockState tip(String variant, RandomSource random) {
        return switch (variant) {
            case "ice" -> Blocks.ICE.defaultBlockState();
            case "magma" -> Blocks.SHROOMLIGHT.defaultBlockState();
            case "quartz" -> Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
            default -> Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        };
    }
}
