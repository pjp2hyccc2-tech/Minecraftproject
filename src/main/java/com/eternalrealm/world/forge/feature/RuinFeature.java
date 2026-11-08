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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * The broken walls of a small building: a cracked floor, walls of uneven height with a doorway, corner pillars, an arch
 * over the door, rubble around it and a few themed details (moss and lanterns, sand and pots, snow and ice, magma,
 * light rods on quartz, crying obsidian and soul lanterns).
 */
public final class RuinFeature extends Feature<VariantConfig> {
    public RuinFeature(Codec<VariantConfig> codec) {
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
        int halfX = 2 + random.nextInt(3);
        int halfZ = 2 + random.nextInt(3);
        if (!FeatureKit.dry(level, x0, z0) || !bounds.fits(x0, z0, Math.max(halfX, halfZ) + 3)
                || FeatureKit.roughness(level, x0, z0, Math.max(halfX, halfZ)) > 4) {
            return false;
        }
        Palette palette = Palette.of(variant);
        int floorY = FeatureKit.ground(level, x0, z0);
        Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                boolean edgeX = Math.abs(dx) == halfX;
                boolean edgeZ = Math.abs(dz) == halfZ;
                int ground = FeatureKit.ground(level, x, z);
                // foundation down to the ground, clear what stands above the floor
                for (int y = ground + 1; y < floorY; y++) {
                    FeatureKit.set(level, bounds, x, y, z, palette.wall(random));
                }
                for (int y = floorY + 1; y <= ground; y++) {
                    FeatureKit.set(level, bounds, x, y, z, Blocks.AIR.defaultBlockState());
                }
                if (!edgeX && !edgeZ) {
                    if (random.nextFloat() < 0.7F) {
                        FeatureKit.set(level, bounds, x, floorY, z, palette.floor(random));
                    }
                    FeatureKit.set(level, bounds, x, floorY + 1, z, Blocks.AIR.defaultBlockState());
                    continue;
                }
                boolean corner = edgeX && edgeZ;
                boolean doorway = isDoorway(door, dx, dz, halfX, halfZ);
                int height = corner ? 4 + random.nextInt(3) : doorway ? 0 : random.nextFloat() < 0.2F ? 0
                        : 1 + random.nextInt(4);
                FeatureKit.set(level, bounds, x, floorY, z, palette.wall(random));
                for (int dy = 1; dy <= height; dy++) {
                    FeatureKit.set(level, bounds, x, floorY + dy, z, corner ? palette.pillar() : palette.wall(random));
                }
                if (height > 0 && !corner && random.nextFloat() < 0.35F) {
                    FeatureKit.setIfOpen(level, bounds, x, floorY + height + 1, z, palette.slab());
                }
                if (corner) {
                    FeatureKit.setIfOpen(level, bounds, x, floorY + height + 1, z, palette.cornerTop(random));
                }
            }
        }
        // arch over the doorway: two upside-down stairs and a lintel
        int doorX = x0 + door.getStepX() * halfX;
        int doorZ = z0 + door.getStepZ() * halfZ;
        Direction side = door.getClockWise();
        if (random.nextFloat() < 0.7F) {
            for (int dy = 1; dy <= 3; dy++) {
                FeatureKit.set(level, bounds, doorX + side.getStepX() * 2, floorY + dy, doorZ + side.getStepZ() * 2,
                        palette.pillar());
                FeatureKit.set(level, bounds, doorX - side.getStepX() * 2, floorY + dy, doorZ - side.getStepZ() * 2,
                        palette.pillar());
            }
            FeatureKit.set(level, bounds, doorX + side.getStepX(), floorY + 3, doorZ + side.getStepZ(),
                    palette.stairs(side));
            FeatureKit.set(level, bounds, doorX - side.getStepX(), floorY + 3, doorZ - side.getStepZ(),
                    palette.stairs(side.getOpposite()));
            FeatureKit.set(level, bounds, doorX, floorY + 4, doorZ, palette.accent());
            for (int i = -2; i <= 2; i++) {
                FeatureKit.setIfOpen(level, bounds, doorX + side.getStepX() * i, floorY + 4 + (i == 0 ? 1 : 0),
                        doorZ + side.getStepZ() * i, palette.slab());
            }
        }
        // rubble around the walls
        for (int i = 0; i < 4 + random.nextInt(4); i++) {
            int x = x0 + random.nextInt(halfX * 2 + 5) - halfX - 2;
            int z = z0 + random.nextInt(halfZ * 2 + 5) - halfZ - 2;
            int y = FeatureKit.ground(level, x, z) + 1;
            if (FeatureKit.dry(level, x, z)) {
                FeatureKit.setIfOpen(level, bounds, x, y, z, random.nextBoolean() ? palette.slab() : palette.wall(random));
            }
        }
        palette.decorate(level, bounds, random, x0, floorY + 1, z0, halfX, halfZ);
        return true;
    }

    private static boolean isDoorway(Direction door, int dx, int dz, int halfX, int halfZ) {
        return switch (door) {
            case NORTH -> dz == -halfZ && Math.abs(dx) <= 1;
            case SOUTH -> dz == halfZ && Math.abs(dx) <= 1;
            case WEST -> dx == -halfX && Math.abs(dz) <= 1;
            default -> dx == halfX && Math.abs(dz) <= 1;
        };
    }

    /** Blocks of one ruin style. */
    private record Palette(String variant, Block[] walls, Block floorBlock, Block pillarBlock, Block slabBlock,
                           Block stairsBlock, Block accentBlock) {
        static Palette of(String variant) {
            return switch (variant) {
                case "sandstone" -> new Palette(variant, new Block[]{Blocks.SANDSTONE, Blocks.CUT_SANDSTONE,
                        Blocks.SMOOTH_SANDSTONE}, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_SLAB,
                        Blocks.SANDSTONE_STAIRS, Blocks.CHISELED_SANDSTONE);
                case "frost" -> new Palette(variant, new Block[]{Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                        Blocks.PACKED_ICE}, Blocks.STONE_BRICKS, Blocks.POLISHED_DIORITE, Blocks.STONE_BRICK_SLAB,
                        Blocks.STONE_BRICK_STAIRS, Blocks.BLUE_ICE);
                case "basalt" -> new Palette(variant, new Block[]{Blocks.POLISHED_BLACKSTONE_BRICKS,
                        Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE}, Blocks.POLISHED_BASALT,
                        Blocks.POLISHED_BASALT, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
                        Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.GILDED_BLACKSTONE);
                case "quartz" -> new Palette(variant, new Block[]{Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BLOCK,
                        Blocks.CALCITE}, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_SLAB,
                        Blocks.QUARTZ_STAIRS, Blocks.CHISELED_QUARTZ_BLOCK);
                case "abyss" -> new Palette(variant, new Block[]{Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
                        Blocks.DEEPSLATE_TILES}, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_BASALT,
                        Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.CRYING_OBSIDIAN);
                default -> new Palette(variant, new Block[]{Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
                        Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE}, Blocks.STONE_BRICKS,
                        Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS,
                        Blocks.CHISELED_STONE_BRICKS);
            };
        }

        BlockState wall(RandomSource random) {
            return walls[random.nextInt(walls.length)].defaultBlockState();
        }

        BlockState floor(RandomSource random) {
            return random.nextFloat() < 0.75F ? floorBlock.defaultBlockState() : wall(random);
        }

        BlockState pillar() {
            BlockState state = pillarBlock.defaultBlockState();
            return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, Direction.Axis.Y)
                    : state;
        }

        BlockState slab() {
            return slabBlock.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        }

        BlockState stairs(Direction facing) {
            return stairsBlock.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                    .setValue(BlockStateProperties.HALF, Half.TOP);
        }

        BlockState accent() {
            return accentBlock.defaultBlockState();
        }

        BlockState cornerTop(RandomSource random) {
            return switch (variant) {
                case "quartz" -> Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
                case "abyss" -> Blocks.SOUL_LANTERN.defaultBlockState();
                case "basalt" -> Blocks.MAGMA_BLOCK.defaultBlockState();
                case "frost" -> Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 2);
                case "sandstone" -> random.nextBoolean() ? Blocks.CHISELED_SANDSTONE.defaultBlockState()
                        : Blocks.SANDSTONE_SLAB.defaultBlockState();
                default -> random.nextBoolean() ? Blocks.LANTERN.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState();
            };
        }

        /** Themed details on the floor of the ruin. */
        void decorate(WorldGenLevel level, FeatureKit.Bounds bounds, RandomSource random, int x0, int y, int z0, int halfX,
                      int halfZ) {
            for (int i = 0; i < 3; i++) {
                int x = x0 + random.nextInt(halfX * 2 - 1) - halfX + 1;
                int z = z0 + random.nextInt(halfZ * 2 - 1) - halfZ + 1;
                BlockState detail = switch (variant) {
                    case "sandstone" -> i == 0 ? Blocks.DECORATED_POT.defaultBlockState() : Blocks.SANDSTONE_SLAB.defaultBlockState();
                    case "frost" -> Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1 + random.nextInt(3));
                    case "basalt" -> i == 0 ? Blocks.SOUL_CAMPFIRE.defaultBlockState() : Blocks.BLACKSTONE_SLAB.defaultBlockState();
                    case "quartz" -> i == 0 ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : Blocks.WHITE_CARPET.defaultBlockState();
                    case "abyss" -> i == 0 ? Blocks.SOUL_LANTERN.defaultBlockState() : Blocks.COBWEB.defaultBlockState();
                    default -> i == 0 ? Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState();
                };
                if (FeatureKit.get(level, x, y, z).isAir() && !FeatureKit.get(level, x, y - 1, z).isAir()) {
                    FeatureKit.set(level, bounds, x, y, z, detail);
                }
            }
        }
    }
}
