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

/**
 * A small sunken pool whose surface sits at the lowest point of its rim, so the fluid never runs off:
 * an oasis with sand, grass, reeds, lily pads and palms; a lava pool ringed by obsidian and basalt; or a frozen pond
 * with an ice sheet, packed-ice shards and snow.
 */
public final class PoolFeature extends Feature<VariantConfig> {
    public PoolFeature(Codec<VariantConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<VariantConfig> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        String variant = context.config().variant();
        boolean lava = variant.equals("lava");
        boolean frozen = variant.equals("frozen");
        FeatureKit.Bounds bounds = FeatureKit.Bounds.of(level, origin);
        int x0 = origin.getX();
        int z0 = origin.getZ();
        if (FeatureKit.nearTower(x0, z0)) {
            return false;
        }
        double radius = lava ? 2.5 + random.nextDouble() * 2.0 : 3.5 + random.nextDouble() * 2.5;
        int reach = (int) Math.ceil(radius) + 3;
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, reach)
                || FeatureKit.roughness(level, x0, z0, (int) Math.ceil(radius) + 1) > 3) {
            return false;
        }
        int surface = FeatureKit.ground(level, x0, z0);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0 * i / 12.0;
            int rx = x0 + (int) Math.round(Math.cos(angle) * (radius + 1.5));
            int rz = z0 + (int) Math.round(Math.sin(angle) * (radius + 1.5));
            surface = Math.min(surface, FeatureKit.ground(level, rx, rz));
        }
        int maxDepth = lava ? 2 : 3;
        double phase = random.nextDouble() * Math.PI * 2.0;
        BlockState fluid = (lava ? Blocks.LAVA : Blocks.WATER).defaultBlockState();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                double dist = Math.sqrt(dx * dx + dz * dz) * (1.0 + 0.12 * Math.sin(Math.atan2(dz, dx) * 3.0 + phase));
                int ground = FeatureKit.ground(level, x, z);
                if (dist <= radius) {
                    int depth = 1 + (int) ((1.0 - dist / radius) * maxDepth);
                    for (int y = surface + 1; y <= ground; y++) {
                        FeatureKit.set(level, bounds, x, y, z, Blocks.AIR.defaultBlockState());
                    }
                    for (int y = surface - depth + 1; y <= surface; y++) {
                        BlockState state = frozen && y == surface ? Blocks.ICE.defaultBlockState() : fluid;
                        FeatureKit.set(level, bounds, x, y, z, state);
                    }
                    FeatureKit.set(level, bounds, x, surface - depth, z, bed(variant, random));
                } else if (dist <= radius + 2.2) {
                    for (int y = ground + 1; y < surface; y++) {
                        FeatureKit.set(level, bounds, x, y, z, fill(variant));
                    }
                    int top = Math.max(ground, surface);
                    if (top == surface || dist <= radius + 1.0) {
                        FeatureKit.set(level, bounds, x, top, z, rim(variant, random, dist - radius));
                    }
                }
            }
        }
        decorate(level, bounds, random, variant, x0, z0, radius, surface);
        return true;
    }

    private static void decorate(WorldGenLevel level, FeatureKit.Bounds bounds, RandomSource random, String variant,
                                 int x0, int z0, double radius, int surface) {
        int reach = (int) Math.ceil(radius) + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                BlockState here = FeatureKit.get(level, x, surface, z);
                boolean above = FeatureKit.get(level, x, surface + 1, z).isAir();
                if (!above) {
                    continue;
                }
                switch (variant) {
                    case "oasis" -> {
                        if (here.is(Blocks.WATER) && random.nextFloat() < 0.08F) {
                            FeatureKit.set(level, bounds, x, surface + 1, z, Blocks.LILY_PAD.defaultBlockState());
                        } else if ((here.is(Blocks.SAND) || here.is(Blocks.GRASS_BLOCK)) && nextToWater(level, x, surface, z)
                                && random.nextFloat() < 0.35F) {
                            int height = 2 + random.nextInt(2);
                            for (int i = 1; i <= height; i++) {
                                FeatureKit.set(level, bounds, x, surface + i, z, Blocks.SUGAR_CANE.defaultBlockState());
                            }
                        } else if (here.is(Blocks.GRASS_BLOCK) && random.nextFloat() < 0.4F) {
                            FeatureKit.set(level, bounds, x, surface + 1, z,
                                    (random.nextBoolean() ? Blocks.GRASS : Blocks.FERN).defaultBlockState());
                        }
                    }
                    case "frozen" -> {
                        if ((here.is(Blocks.SNOW_BLOCK) || here.is(Blocks.PACKED_ICE)) && random.nextFloat() < 0.5F) {
                            FeatureKit.set(level, bounds, x, surface + 1, z,
                                    Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1 + random.nextInt(2)));
                        }
                    }
                    default -> {
                        if (here.is(Blocks.BASALT) && random.nextFloat() < 0.1F) {
                            FeatureKit.set(level, bounds, x, surface + 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                        }
                    }
                }
            }
        }
        if (variant.equals("oasis")) {
            int palms = 1 + random.nextInt(2);
            for (int i = 0; i < palms; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                int px = x0 + (int) Math.round(Math.cos(angle) * (radius + 2.0));
                int pz = z0 + (int) Math.round(Math.sin(angle) * (radius + 2.0));
                int ground = FeatureKit.ground(level, px, pz);
                BlockState soil = FeatureKit.get(level, px, ground, pz);
                if ((soil.is(Blocks.SAND) || soil.is(Blocks.GRASS_BLOCK)) && FeatureKit.dry(level, px, pz)) {
                    TreeShapes.palm(level, bounds, random, px, ground + 1, pz);
                }
            }
        } else if (variant.equals("frozen")) {
            for (int i = 0; i < 1 + random.nextInt(3); i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                int sx = x0 + (int) Math.round(Math.cos(angle) * (radius + 1.6));
                int sz = z0 + (int) Math.round(Math.sin(angle) * (radius + 1.6));
                int ground = FeatureKit.ground(level, sx, sz);
                int height = 2 + random.nextInt(4);
                for (int y = 1; y <= height; y++) {
                    FeatureKit.set(level, bounds, sx, ground + y, sz,
                            (y == height ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState());
                }
            }
        }
    }

    private static boolean nextToWater(WorldGenLevel level, int x, int y, int z) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (FeatureKit.get(level, x + direction.getStepX(), y, z + direction.getStepZ()).is(Blocks.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static BlockState bed(String variant, RandomSource random) {
        return (switch (variant) {
            case "lava" -> random.nextFloat() < 0.7F ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE;
            case "frozen" -> random.nextFloat() < 0.6F ? Blocks.GRAVEL : Blocks.PACKED_ICE;
            default -> random.nextFloat() < 0.8F ? Blocks.SAND : Blocks.CLAY;
        }).defaultBlockState();
    }

    private static BlockState fill(String variant) {
        return (switch (variant) {
            case "lava" -> Blocks.BLACKSTONE;
            case "frozen" -> Blocks.PACKED_ICE;
            default -> Blocks.SAND;
        }).defaultBlockState();
    }

    private static BlockState rim(String variant, RandomSource random, double fromEdge) {
        Block block = switch (variant) {
            case "lava" -> fromEdge < 1.0 ? Blocks.OBSIDIAN : random.nextBoolean() ? Blocks.BASALT : Blocks.BLACKSTONE;
            case "frozen" -> fromEdge < 1.0 ? Blocks.PACKED_ICE : Blocks.SNOW_BLOCK;
            default -> fromEdge < 1.2 ? Blocks.SAND : Blocks.GRASS_BLOCK;
        };
        return block.defaultBlockState();
    }
}
