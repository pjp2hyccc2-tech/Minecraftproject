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
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * A toppled column lying in the grass next to its broken stump (stone bricks, sandstone, quartz, blackstone), or a
 * mossy fallen log with mushrooms in the forest ({@code log}).
 */
public final class FallenPillarFeature extends Feature<VariantConfig> {
    public FallenPillarFeature(Codec<VariantConfig> codec) {
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
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, 11)) {
            return false;
        }
        boolean log = variant.equals("log");
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int length = log ? 4 + random.nextInt(5) : 5 + random.nextInt(5);
        int baseY = FeatureKit.ground(level, x0, z0) + 1;
        Block logBlock = FeatureKit.pick(random, Blocks.OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG);
        // stump
        int stumpHeight = log ? 1 + random.nextInt(2) : 2 + random.nextInt(3);
        for (int dy = -1; dy < stumpHeight; dy++) {
            BlockState stump = log ? FeatureKit.axis(logBlock, Direction.Axis.Y) : body(variant, random, Direction.Axis.Y);
            FeatureKit.set(level, bounds, x0, baseY + dy, z0, stump);
        }
        if (!log) {
            FeatureKit.setIfOpen(level, bounds, x0, baseY + stumpHeight, z0, slab(variant));
        }
        // the fallen part, starting two blocks away from the stump
        for (int i = 2; i < length + 2; i++) {
            int x = x0 + direction.getStepX() * i;
            int z = z0 + direction.getStepZ() * i;
            int ground = FeatureKit.ground(level, x, z);
            int y = Math.max(ground + 1, baseY - 1);
            if (y > baseY + 1 || !FeatureKit.isOpen(FeatureKit.get(level, x, y, z))) {
                continue; // buried in a slope: the column looks broken there
            }
            BlockState block = log ? FeatureKit.axis(logBlock, direction.getAxis())
                    : i == length + 1 ? capital(variant) : body(variant, random, direction.getAxis());
            FeatureKit.set(level, bounds, x, y, z, block);
            for (int fill = y - 1; fill > ground; fill--) {
                FeatureKit.set(level, bounds, x, fill, z, rubble(variant));
            }
            if (FeatureKit.get(level, x, y + 1, z).isAir()) {
                float roll = random.nextFloat();
                if (log && roll < 0.45F) {
                    FeatureKit.set(level, bounds, x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (log && roll < 0.6F) {
                    FeatureKit.set(level, bounds, x, y + 1, z,
                            (random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
                } else if (!log && roll < 0.2F && (variant.equals("stone") || variant.equals("quartz"))) {
                    FeatureKit.set(level, bounds, x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        // a few chips around the stump
        for (int i = 0; i < 3; i++) {
            int x = x0 + random.nextInt(5) - 2;
            int z = z0 + random.nextInt(5) - 2;
            int y = FeatureKit.ground(level, x, z) + 1;
            if (!log && FeatureKit.isOpen(FeatureKit.get(level, x, y, z)) && FeatureKit.dry(level, x, z)) {
                FeatureKit.set(level, bounds, x, y, z, slab(variant));
            }
        }
        return true;
    }

    private static BlockState body(String variant, RandomSource random, Direction.Axis axis) {
        float roll = random.nextFloat();
        return switch (variant) {
            case "sandstone" -> (roll < 0.5F ? Blocks.CUT_SANDSTONE : roll < 0.8F ? Blocks.SANDSTONE
                    : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
            case "quartz" -> roll < 0.85F ? FeatureKit.axis(Blocks.QUARTZ_PILLAR, axis) : Blocks.QUARTZ_BRICKS.defaultBlockState();
            case "blackstone" -> (roll < 0.55F ? Blocks.POLISHED_BLACKSTONE_BRICKS
                    : roll < 0.9F ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.GILDED_BLACKSTONE).defaultBlockState();
            default -> (roll < 0.45F ? Blocks.STONE_BRICKS : roll < 0.75F ? Blocks.MOSSY_STONE_BRICKS
                    : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
        };
    }

    private static BlockState capital(String variant) {
        return (switch (variant) {
            case "sandstone" -> Blocks.CHISELED_SANDSTONE;
            case "quartz" -> Blocks.CHISELED_QUARTZ_BLOCK;
            case "blackstone" -> Blocks.CHISELED_POLISHED_BLACKSTONE;
            default -> Blocks.CHISELED_STONE_BRICKS;
        }).defaultBlockState();
    }

    private static BlockState rubble(String variant) {
        return (switch (variant) {
            case "sandstone" -> Blocks.SANDSTONE;
            case "quartz" -> Blocks.CALCITE;
            case "blackstone" -> Blocks.BLACKSTONE;
            default -> Blocks.COBBLESTONE;
        }).defaultBlockState();
    }

    private static BlockState slab(String variant) {
        Block slab = switch (variant) {
            case "sandstone" -> Blocks.SANDSTONE_SLAB;
            case "quartz" -> Blocks.QUARTZ_SLAB;
            case "blackstone" -> Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
            default -> Blocks.MOSSY_STONE_BRICK_SLAB;
        };
        return slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
    }
}
