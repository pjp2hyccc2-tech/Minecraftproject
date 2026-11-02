package com.birdiecraft.course;

import com.birdiecraft.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/** Hand-written tree and decoration builders used by the course generator. */
public final class CourseDecorator {
    private CourseDecorator() {
    }

    // ------------------------------------------------------------------ trees

    public static void tree(BlockWriter w, CourseTheme.TreeStyle style, int x, int y, int z, Random r) {
        switch (style) {
            case OAK -> roundTree(w, x, y, z, r, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4 + r.nextInt(3), 2);
            case BIRCH -> roundTree(w, x, y, z, r, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5 + r.nextInt(3), 2);
            case MAPLE -> roundTree(w, x, y, z, r, Blocks.SPRUCE_LOG, ModBlocks.MAPLE_LEAVES.get(), 4 + r.nextInt(3), 3);
            case GINKGO -> coneTree(w, x, y, z, r, Blocks.OAK_LOG, ModBlocks.GINKGO_LEAVES.get(), 6 + r.nextInt(3), false);
            case SPRUCE -> coneTree(w, x, y, z, r, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, 7 + r.nextInt(3), false);
            case SNOWY_SPRUCE -> coneTree(w, x, y, z, r, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, 7 + r.nextInt(4), true);
            case CHERRY -> cherry(w, x, y, z, r);
            case PALM -> palm(w, x, y, z, r);
            case ACACIA -> acacia(w, x, y, z, r);
            case DARK_OAK -> darkOak(w, x, y, z, r);
            case GIANT_MUSHROOM -> mushroom(w, x, y, z, r);
            case CACTUS -> cactus(w, x, y, z, r);
            case BASALT_SPIRE -> spire(w, x, y, z, r);
            case DEAD -> deadTree(w, x, y, z, r);
        }
    }

    private static void trunk(BlockWriter w, int x, int y, int z, int h, Block log) {
        for (int i = 0; i < h; i++) w.set(x, y + i, z, log);
    }

    private static void roundTree(BlockWriter w, int x, int y, int z, Random r, Block log, Block leaves, int h, int radius) {
        BlockState lv = BlockWriter.leaves(leaves);
        int top = y + h;
        for (int dy = -2; dy <= 1; dy++) {
            int rad = dy <= -1 ? radius : radius - 1;
            if (rad < 1) rad = 1;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad;
                    if (corner && (dy == 1 || r.nextInt(3) > 0)) continue;
                    if (dx * dx + dz * dz > rad * rad + 1) continue;
                    w.setIfFree(x + dx, top + dy, z + dz, lv);
                }
            }
        }
        trunk(w, x, y, z, h, log);
    }

    private static void coneTree(BlockWriter w, int x, int y, int z, Random r, Block log, Block leaves, int h, boolean snowy) {
        BlockState lv = BlockWriter.leaves(leaves);
        int top = y + h;
        int layer = 0;
        for (int yy = top; yy >= y + 2; yy--) {
            int rad = layer == 0 ? 0 : (layer % 2 == 1 ? 1 : Math.min(3, 1 + layer / 2));
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > rad + (rad >= 2 ? 1 : 0)) continue;
                    if (w.setIfFree(x + dx, yy, z + dz, lv) && snowy && r.nextInt(3) == 0) {
                        w.setIfFree(x + dx, yy + 1, z + dz, Blocks.SNOW.defaultBlockState());
                    }
                }
            }
            layer++;
        }
        w.setIfFree(x, top + 1, z, lv);
        if (snowy) w.setIfFree(x, top + 2, z, Blocks.SNOW.defaultBlockState());
        trunk(w, x, y, z, h, log);
    }

    private static void cherry(BlockWriter w, int x, int y, int z, Random r) {
        BlockState lv = BlockWriter.leaves(Blocks.CHERRY_LEAVES);
        int h = 4 + r.nextInt(2);
        Direction lean = Direction.from2DDataValue(r.nextInt(4));
        trunk(w, x, y, z, h - 1, Blocks.CHERRY_LOG);
        int tx = x + lean.getStepX();
        int tz = z + lean.getStepZ();
        int top = y + h;
        w.set(tx, top - 1, tz, Blocks.CHERRY_LOG.defaultBlockState());
        for (int dy = -1; dy <= 1; dy++) {
            int rad = dy == 1 ? 2 : 3;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rad + 0.3) continue;
                    if (d > rad - 0.7 && r.nextInt(3) == 0) continue;
                    w.setIfFree(tx + dx, top + dy, tz + dz, lv);
                }
            }
        }
        // hanging petals
        for (int i = 0; i < 6; i++) {
            int dx = r.nextInt(5) - 2;
            int dz = r.nextInt(5) - 2;
            w.setIfFree(tx + dx, top - 2, tz + dz, lv);
        }
    }

    private static void palm(BlockWriter w, int x, int y, int z, Random r) {
        BlockState lv = BlockWriter.leaves(Blocks.JUNGLE_LEAVES);
        int h = 5 + r.nextInt(4);
        Direction bend = Direction.from2DDataValue(r.nextInt(4));
        int bendAt = h / 2 + 1;
        int cx = x;
        int cz = z;
        for (int i = 0; i < h; i++) {
            if (i == bendAt) {
                cx += bend.getStepX();
                cz += bend.getStepZ();
            }
            w.set(cx, y + i, cz, Blocks.JUNGLE_LOG);
        }
        int top = y + h;
        w.setIfFree(cx, top, cz, lv);
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] a : arms) {
            for (int s = 1; s <= 3; s++) {
                int yy = s == 3 ? top - 1 : top;
                w.setIfFree(cx + a[0] * s, yy, cz + a[1] * s, lv);
            }
        }
        int[][] diag = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] a : diag) {
            w.setIfFree(cx + a[0], top, cz + a[1], lv);
            w.setIfFree(cx + a[0] * 2, top - 1, cz + a[1] * 2, lv);
        }
    }

    private static void acacia(BlockWriter w, int x, int y, int z, Random r) {
        BlockState lv = BlockWriter.leaves(Blocks.ACACIA_LEAVES);
        int h = 3 + r.nextInt(2);
        trunk(w, x, y, z, h, Blocks.ACACIA_LOG);
        Direction d = Direction.from2DDataValue(r.nextInt(4));
        int bx = x;
        int bz = z;
        int by = y + h;
        for (int i = 0; i < 2; i++) {
            bx += d.getStepX();
            bz += d.getStepZ();
            w.set(bx, by + i, bz, Blocks.ACACIA_LOG);
        }
        int top = by + 2;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                w.setIfFree(bx + dx, top, bz + dz, lv);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) w.setIfFree(bx + dx, top + 1, bz + dz, lv);
            }
        }
    }

    private static void darkOak(BlockWriter w, int x, int y, int z, Random r) {
        BlockState lv = BlockWriter.leaves(Blocks.DARK_OAK_LEAVES);
        int h = 5 + r.nextInt(3);
        for (int i = 0; i < h; i++) {
            w.set(x, y + i, z, Blocks.DARK_OAK_LOG);
            w.set(x + 1, y + i, z, Blocks.DARK_OAK_LOG);
            w.set(x, y + i, z + 1, Blocks.DARK_OAK_LOG);
            w.set(x + 1, y + i, z + 1, Blocks.DARK_OAK_LOG);
        }
        int top = y + h;
        for (int dy = -1; dy <= 1; dy++) {
            int rad = dy == 1 ? 2 : 4;
            for (int dx = -rad; dx <= rad + 1; dx++) {
                for (int dz = -rad; dz <= rad + 1; dz++) {
                    double ddx = dx - 0.5;
                    double ddz = dz - 0.5;
                    if (ddx * ddx + ddz * ddz > rad * rad + 0.5) continue;
                    if (r.nextInt(7) == 0) continue;
                    w.setIfFree(x + dx, top + dy, z + dz, lv);
                }
            }
        }
    }

    private static void mushroom(BlockWriter w, int x, int y, int z, Random r) {
        int h = 4 + r.nextInt(3);
        trunk(w, x, y, z, h, Blocks.MUSHROOM_STEM);
        int top = y + h;
        if (r.nextBoolean()) {
            BlockState cap = Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        w.setIfFree(x + dx, top - 1, z + dz, cap);
                        w.setIfFree(x + dx, top - 2, z + dz, cap);
                    }
                    w.setIfFree(x + dx, top, z + dz, cap);
                }
            }
        } else {
            BlockState cap = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                    w.setIfFree(x + dx, top, z + dz, cap);
                }
            }
        }
    }

    private static void cactus(BlockWriter w, int x, int y, int z, Random r) {
        int h = 1 + r.nextInt(3);
        for (int i = 0; i < h; i++) w.setIfFree(x, y + i, z, Blocks.CACTUS.defaultBlockState());
    }

    private static void spire(BlockWriter w, int x, int y, int z, Random r) {
        int h = 3 + r.nextInt(5);
        trunk(w, x, y, z, h, Blocks.BASALT);
        if (r.nextBoolean()) w.setIfFree(x + 1, y, z, Blocks.BLACKSTONE.defaultBlockState());
        if (r.nextBoolean()) w.setIfFree(x, y, z + 1, Blocks.BASALT.defaultBlockState());
    }

    private static void deadTree(BlockWriter w, int x, int y, int z, Random r) {
        int h = 4 + r.nextInt(3);
        trunk(w, x, y, z, h, Blocks.BASALT);
        BlockState branchX = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState branchZ = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        int by = y + h - 2;
        if (r.nextBoolean()) {
            w.setIfFree(x + 1, by, z, branchX);
            w.setIfFree(x + 2, by + 1, z, branchX);
        } else {
            w.setIfFree(x - 1, by, z, branchX);
            w.setIfFree(x - 2, by + 1, z, branchX);
        }
        if (r.nextBoolean()) w.setIfFree(x, by + 1, z + 1, branchZ);
    }

    // ------------------------------------------------------------------ decorations

    private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM};

    /** Places a decoration standing on the ground at (x, y-1, z); y is the first air block. */
    public static void decor(BlockWriter w, CourseTheme.Decor decor, int x, int y, int z, Random r) {
        switch (decor) {
            case FLOWERS -> w.setIfFree(x, y, z, FLOWERS[r.nextInt(FLOWERS.length)].defaultBlockState());
            case TALL_GRASS -> w.setIfFree(x, y, z, (r.nextBoolean() ? Blocks.GRASS : Blocks.FERN).defaultBlockState());
            case FERNS -> w.setIfFree(x, y, z, Blocks.FERN.defaultBlockState());
            case DEAD_BUSH -> w.setIfFree(x, y, z, Blocks.DEAD_BUSH.defaultBlockState());
            case MUSHROOMS -> w.setIfFree(x, y, z, (r.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
            case PETALS -> w.setIfFree(x, y, z, Blocks.PINK_PETALS.defaultBlockState());
            case DUNES -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        w.setIfFree(x + dx, y, z + dz, Blocks.SAND.defaultBlockState());
                    }
                }
                w.setIfFree(x, y + 1, z, Blocks.SAND.defaultBlockState());
                w.setIfFree(x, y + 2, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
            case BOULDERS -> {
                Block[] rocks = {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE, Blocks.STONE};
                w.setIfFree(x, y, z, rocks[r.nextInt(rocks.length)].defaultBlockState());
                if (r.nextBoolean()) w.setIfFree(x + 1, y, z, rocks[r.nextInt(rocks.length)].defaultBlockState());
                if (r.nextBoolean()) w.setIfFree(x, y, z + 1, rocks[r.nextInt(rocks.length)].defaultBlockState());
                if (r.nextInt(3) == 0) w.setIfFree(x, y + 1, z, rocks[r.nextInt(rocks.length)].defaultBlockState());
            }
            case LANTERNS -> {
                w.setIfFree(x, y, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                w.setIfFree(x, y + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                w.setIfFree(x, y + 2, z, Blocks.LANTERN.defaultBlockState());
                w.setIfFree(x, y + 3, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
            }
            case MESAS -> {
                Block[] bands = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.RED_TERRACOTTA};
                int h = 3 + r.nextInt(6);
                int size = r.nextBoolean() ? 1 : 2;
                for (int i = 0; i < h; i++) {
                    Block b = bands[(i + r.nextInt(2)) % bands.length];
                    for (int dx = 0; dx < size; dx++) {
                        for (int dz = 0; dz < size; dz++) {
                            w.setIfFree(x + dx, y + i, z + dz, b.defaultBlockState());
                        }
                    }
                }
            }
            case ICE_SPIKES -> {
                int h = 3 + r.nextInt(5);
                for (int i = 0; i < h; i++) w.setIfFree(x, y + i, z, Blocks.PACKED_ICE.defaultBlockState());
                w.setIfFree(x + 1, y, z, Blocks.PACKED_ICE.defaultBlockState());
                w.setIfFree(x - 1, y, z, Blocks.PACKED_ICE.defaultBlockState());
                w.setIfFree(x, y, z + 1, Blocks.PACKED_ICE.defaultBlockState());
                w.setIfFree(x, y, z - 1, Blocks.PACKED_ICE.defaultBlockState());
            }
            case MAGMA -> w.set(x, y - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
            case HEDGES -> {
                BlockState lv = BlockWriter.leaves(Blocks.OAK_LEAVES);
                w.setIfFree(x, y, z, lv);
                w.setIfFree(x, y + 1, z, lv);
            }
        }
    }
}
