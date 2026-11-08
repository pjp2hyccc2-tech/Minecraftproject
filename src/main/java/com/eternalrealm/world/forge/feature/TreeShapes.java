package com.eternalrealm.world.forge.feature;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Hand-shaped trees of アストリア (the vanilla trees are used too, through their placed features). Every method builds
 * one tree whose trunk starts at (x, y, z) (y = first block above the ground) and stays within 12 blocks of it.
 */
public final class TreeShapes {
    private TreeShapes() {
    }

    /** Ancient elder tree: 2x2 dark oak trunk, buttress roots, long branches with leaf clouds, vines and glowing lights. */
    public static void giant(WorldGenLevel level, FeatureKit.Bounds b, RandomSource random, int x, int y, int z) {
        int height = 18 + random.nextInt(10);
        BlockState wood = FeatureKit.axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
        BlockState leaves = FeatureKit.leaves(Blocks.DARK_OAK_LEAVES);
        for (int dy = -2; dy <= height; dy++) {
            for (int cx = 0; cx <= 1; cx++) {
                for (int cz = 0; cz <= 1; cz++) {
                    if (dy > height - 3 && (cx + cz) % 2 == 1) {
                        continue; // the top thins out
                    }
                    FeatureKit.set(level, b, x + cx, y + dy, z + cz, wood);
                }
            }
        }
        // buttress roots
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] d : directions) {
            if (random.nextFloat() < 0.25F) {
                continue;
            }
            int length = 2 + random.nextInt(3);
            for (int i = 1; i <= length; i++) {
                int rx = x + (d[0] > 0 ? 1 : 0) + d[0] * i;
                int rz = z + (d[1] > 0 ? 1 : 0) + d[1] * i;
                int ry = y + Math.max(-1, 1 - i);
                Direction.Axis axis = d[0] != 0 && d[1] == 0 ? Direction.Axis.X : d[1] != 0 && d[0] == 0
                        ? Direction.Axis.Z : Direction.Axis.Y;
                FeatureKit.set(level, b, rx, ry, rz, FeatureKit.axis(Blocks.DARK_OAK_LOG, axis));
                FeatureKit.set(level, b, rx, ry - 1, rz, FeatureKit.axis(Blocks.DARK_OAK_LOG, axis));
                if (i == length && random.nextBoolean()) {
                    FeatureKit.setIfOpen(level, b, rx, ry + 1, rz, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        // branches with leaf clouds
        double centerX = x + 1.0;
        double centerZ = z + 1.0;
        int branches = 4 + random.nextInt(2);
        double angle = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < branches; i++) {
            angle += Math.PI * 2.0 / branches + (random.nextDouble() - 0.5) * 0.6;
            int startY = y + (int) (height * (0.55 + random.nextDouble() * 0.3));
            int length = 4 + random.nextInt(3);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Direction.Axis axis = Math.abs(cos) > Math.abs(sin) ? Direction.Axis.X : Direction.Axis.Z;
            int endX = x;
            int endY = startY;
            int endZ = z;
            for (int step = 1; step <= length; step++) {
                endX = (int) Math.floor(centerX + cos * (step + 0.5));
                endZ = (int) Math.floor(centerZ + sin * (step + 0.5));
                endY = startY + step / 2;
                FeatureKit.set(level, b, endX, endY, endZ, FeatureKit.axis(Blocks.DARK_OAK_LOG, axis));
            }
            double radius = 2.5 + random.nextDouble();
            blob(level, b, random, endX + 0.5, endY + 1.0, endZ + 0.5, radius, radius * 0.6, radius, leaves);
            hangVines(level, b, random, endX, endY - 1, endZ, (int) Math.ceil(radius));
        }
        blob(level, b, random, centerX, y + height + 1.0, centerZ, 4.5, 2.8, 4.5, leaves);
        hangVines(level, b, random, x, y + height - 2, z, 4);
        for (int i = 0; i < 2; i++) {
            int lx = x + random.nextInt(5) - 2;
            int lz = z + random.nextInt(5) - 2;
            if (FeatureKit.get(level, lx, y + height, lz).is(Blocks.DARK_OAK_LEAVES)) {
                FeatureKit.set(level, b, lx, y + height, lz, Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
    }

    /** Desert palm: a curved jungle trunk, drooping fronds and cocoa pods. */
    public static void palm(WorldGenLevel level, FeatureKit.Bounds b, RandomSource random, int x, int y, int z) {
        int height = 6 + random.nextInt(4);
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockState log = FeatureKit.axis(Blocks.JUNGLE_LOG, Direction.Axis.Y);
        BlockState leaves = FeatureKit.leaves(Blocks.JUNGLE_LEAVES);
        int tx = x;
        int tz = z;
        for (int i = 0; i < height; i++) {
            if (i == 3 || i == 6) {
                tx += lean.getStepX();
                tz += lean.getStepZ();
            }
            FeatureKit.set(level, b, tx, y + i, tz, log);
        }
        int top = y + height;
        FeatureKit.set(level, b, tx, top, tz, leaves);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : directions) {
            boolean diagonal = d[0] != 0 && d[1] != 0;
            int length = diagonal ? 3 : 4;
            for (int j = 1; j <= length; j++) {
                int drop = j >= length - (diagonal ? 0 : 1) ? -1 : 0;
                FeatureKit.setIfOpen(level, b, tx + d[0] * j, top + drop, tz + d[1] * j, leaves);
            }
        }
        // cocoa pods hang on the last trunk block below the crown, facing the trunk
        for (Direction side : new Direction[]{Direction.NORTH, Direction.EAST}) {
            if (random.nextBoolean()) {
                FeatureKit.setIfOpen(level, b, tx + side.getStepX(), top - 1, tz + side.getStepZ(),
                        Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, side.getOpposite())
                                .setValue(BlockStateProperties.AGE_2, 2));
            }
        }
    }

    /** Sky tree with white bark and pale leaves (tinted by the biome) and glowing light rods hanging below. */
    public static void white(WorldGenLevel level, FeatureKit.Bounds b, RandomSource random, int x, int y, int z) {
        int height = 7 + random.nextInt(4);
        BlockState log = FeatureKit.axis(Blocks.BIRCH_LOG, Direction.Axis.Y);
        BlockState leaves = FeatureKit.leaves(Blocks.OAK_LEAVES);
        for (int i = 0; i < height; i++) {
            FeatureKit.set(level, b, x, y + i, z, log);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (random.nextFloat() < 0.5F) {
                int by = y + height - 3 + random.nextInt(2);
                FeatureKit.set(level, b, x + side.getStepX(), by, z + side.getStepZ(),
                        FeatureKit.axis(Blocks.STRIPPED_BIRCH_LOG, side.getAxis()));
                FeatureKit.set(level, b, x + side.getStepX() * 2, by + 1, z + side.getStepZ() * 2,
                        FeatureKit.axis(Blocks.STRIPPED_BIRCH_LOG, side.getAxis()));
            }
        }
        double radius = 3.2 + random.nextDouble() * 0.8;
        blob(level, b, random, x + 0.5, y + height, z + 0.5, radius, 2.6, radius, leaves);
        blob(level, b, random, x + 0.5 + random.nextInt(3) - 1, y + height + 2.0, z + 0.5 + random.nextInt(3) - 1, 2.2, 1.6,
                2.2, leaves);
        for (int i = 0; i < 4; i++) {
            int px = x + random.nextInt(7) - 3;
            int pz = z + random.nextInt(7) - 3;
            for (int py = y + height + 2; py > y + height - 4; py--) {
                if (FeatureKit.get(level, px, py, pz).is(Blocks.OAK_LEAVES) && FeatureKit.get(level, px, py - 1, pz).isAir()) {
                    FeatureKit.set(level, b, px, py - 1, pz,
                            Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
                    break;
                }
            }
        }
    }

    /** Small cherry tree with pink petals around its foot. */
    public static void cherry(WorldGenLevel level, FeatureKit.Bounds b, RandomSource random, int x, int y, int z) {
        int height = 4 + random.nextInt(3);
        for (int i = 0; i < height; i++) {
            FeatureKit.set(level, b, x, y + i, z, FeatureKit.axis(Blocks.CHERRY_LOG, Direction.Axis.Y));
        }
        double radius = 2.6 + random.nextDouble() * 0.8;
        blob(level, b, random, x + 0.5, y + height, z + 0.5, radius, 1.9, radius, FeatureKit.leaves(Blocks.CHERRY_LEAVES));
        for (int i = 0; i < 6; i++) {
            int px = x + random.nextInt(7) - 3;
            int pz = z + random.nextInt(7) - 3;
            int py = FeatureKit.ground(level, px, pz);
            if (FeatureKit.get(level, px, py, pz).is(Blocks.GRASS_BLOCK) && FeatureKit.get(level, px, py + 1, pz).isAir()) {
                FeatureKit.set(level, b, px, py + 1, pz, Blocks.PINK_PETALS.defaultBlockState()
                        .setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + random.nextInt(4))
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
            }
        }
    }

    /** Leafless dead tree: charred (volcano), ashen (wasteland) or bleached bone-white (desert). */
    public static void dead(WorldGenLevel level, FeatureKit.Bounds b, RandomSource random, int x, int y, int z, String kind) {
        Block trunk = switch (kind) {
            case "charred" -> Blocks.DARK_OAK_WOOD;
            case "bleached" -> Blocks.STRIPPED_BIRCH_LOG;
            default -> Blocks.STRIPPED_DARK_OAK_LOG;
        };
        Block branch = switch (kind) {
            case "charred" -> Blocks.DARK_OAK_LOG;
            case "bleached" -> Blocks.BONE_BLOCK;
            default -> Blocks.DARK_OAK_LOG;
        };
        int height = (kind.equals("ashen") ? 5 : 4) + random.nextInt(5);
        for (int i = -1; i < height; i++) {
            FeatureKit.set(level, b, x, y + i, z, FeatureKit.axis(trunk, Direction.Axis.Y));
        }
        if (kind.equals("charred") && random.nextBoolean()) {
            FeatureKit.set(level, b, x, y - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
        }
        int branches = 2 + random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            int dx = random.nextInt(3) - 1;
            int dz = random.nextInt(3) - 1;
            if (dx == 0 && dz == 0) {
                dx = 1;
            }
            Direction.Axis axis = dz == 0 ? Direction.Axis.X : dx == 0 ? Direction.Axis.Z : Direction.Axis.Y;
            int bx = x;
            int by = y + height / 2 + random.nextInt(Math.max(1, height / 2));
            int bz = z;
            int length = 2 + random.nextInt(3);
            for (int step = 0; step < length; step++) {
                bx += dx;
                bz += dz;
                by += step % 2 == 0 ? 1 : 0;
                FeatureKit.set(level, b, bx, by, bz, FeatureKit.axis(branch, axis));
            }
            BlockState twig = switch (kind) {
                case "ashen" -> random.nextFloat() < 0.3F ? Blocks.COBWEB.defaultBlockState()
                        : Blocks.MANGROVE_ROOTS.defaultBlockState();
                case "bleached" -> FeatureKit.axis(Blocks.BONE_BLOCK, Direction.Axis.Y);
                default -> FeatureKit.axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
            };
            FeatureKit.setIfOpen(level, b, bx, by + 1, bz, twig);
        }
    }

    /** Ragged ellipsoid of leaves in the open blocks around a centre. */
    public static void blob(WorldGenLevel level, FeatureKit.Bounds b, RandomSource random, double cx, double cy, double cz,
                            double rx, double ry, double rz, BlockState leaves) {
        for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++) {
            for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++) {
                for (int z = (int) Math.floor(cz - rz); z <= (int) Math.ceil(cz + rz); z++) {
                    double ex = (x + 0.5 - cx) / rx;
                    double ey = (y + 0.5 - cy) / ry;
                    double ez = (z + 0.5 - cz) / rz;
                    double d = ex * ex + ey * ey + ez * ez;
                    if (d <= 1.0 && (d < 0.7 || random.nextFloat() < 0.55F)) {
                        FeatureKit.setIfOpen(level, b, x, y, z, leaves);
                    }
                }
            }
        }
    }

    /** Single vines under the lowest leaves (attached to the leaf above, so they stay put). */
    private static void hangVines(WorldGenLevel level, FeatureKit.Bounds b, RandomSource random, int x, int y, int z,
                                  int radius) {
        for (int i = 0; i < radius * 2; i++) {
            int vx = x + random.nextInt(radius * 2 + 1) - radius;
            int vz = z + random.nextInt(radius * 2 + 1) - radius;
            for (int vy = y + 3; vy >= y - 1; vy--) {
                if (FeatureKit.get(level, vx, vy, vz).is(Blocks.DARK_OAK_LEAVES)
                        && FeatureKit.get(level, vx, vy - 1, vz).isAir()) {
                    FeatureKit.set(level, b, vx, vy - 1, vz, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.UP, true));
                    break;
                }
            }
        }
    }
}
