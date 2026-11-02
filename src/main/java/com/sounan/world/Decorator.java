package com.sounan.world;

import com.sounan.block.FacingBlock;
import com.sounan.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;

/** Cedar forest and undergrowth. Deterministic (position hashes), clipped to the chunk being generated. */
public final class Decorator {
    private static final int CELL = 3;
    private static final double TREE_CHANCE = 0.66;

    private Decorator() {}

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static int hash(int x, int z, int salt) {
        return (int) mix(x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L + 0x2545F4914F6CDD1DL);
    }

    /** Uniform in [0, 1). */
    public static double rand(int x, int z, int salt) {
        return (hash(x, z, salt) >>> 1) / 2147483648.0;
    }

    public record Tree(int x, int z, int base, int height, int crownBase, double radius, boolean ribbon) {
        int top() {
            return base + height + 1;
        }
    }

    public static void decorate(ChunkSink sink, TerrainData t) {
        List<Tree> trees = collectTrees(sink, t);
        for (Tree tr : trees) trunk(sink, tr);
        for (Tree tr : trees) crown(sink, tr);
        boulders(sink, t);
        fallenLogs(sink, t);
        undergrowth(sink, t);
    }

    static boolean treeGround(int surface) {
        return surface == TerrainData.SURF_FOREST || surface == TerrainData.SURF_MOSS || surface == TerrainData.SURF_MOSSROCK
                || surface == TerrainData.SURF_ROCK || surface == TerrainData.SURF_DIRT;
    }

    static List<Tree> collectTrees(ChunkSink sink, TerrainData t) {
        List<Tree> out = new ArrayList<>();
        List<int[]> ribbons = new ArrayList<>();
        for (int[] r : Pois.get().ribbons) {
            if (r[0] >= sink.minX - 4 && r[0] <= sink.maxX + 4 && r[1] >= sink.minZ - 4 && r[1] <= sink.maxZ + 4) {
                ribbons.add(r);
                out.add(makeTree(t, r[0], r[1], true));
            }
        }
        int gx0 = Math.floorDiv(sink.minX - 4, CELL), gx1 = Math.floorDiv(sink.maxX + 4, CELL);
        int gz0 = Math.floorDiv(sink.minZ - 4, CELL), gz1 = Math.floorDiv(sink.maxZ + 4, CELL);
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                int x = gx * CELL + Math.floorMod(hash(gx, gz, 1), CELL);
                int z = gz * CELL + Math.floorMod(hash(gx, gz, 2), CELL);
                double density = t.trees(x, z) / 255.0;
                if (density <= 0.0 || rand(gx, gz, 3) > density * TREE_CHANCE) continue;
                if (!treeGround(t.surface(x, z)) || t.water(x, z) > 0) continue;
                boolean clash = false;
                for (int[] r : ribbons) {
                    if (Math.abs(r[0] - x) <= 1 && Math.abs(r[1] - z) <= 1) {
                        clash = true;
                        break;
                    }
                }
                if (!clash) out.add(makeTree(t, x, z, false));
            }
        }
        return out;
    }

    public static Tree makeTree(TerrainData t, int x, int z, boolean ribbon) {
        int base = t.height(x, z);
        int h = 14 + Math.floorMod(hash(x, z, 11), 8);
        int crownBase = base + (int) (h * 0.46) + Math.floorMod(hash(x, z, 12), 3);
        double radius = 2.0 + rand(x, z, 13) * 1.1;
        return new Tree(x, z, base, h, crownBase, radius, ribbon);
    }

    static BlockState log(Direction.Axis axis) {
        return ModBlocks.CEDAR_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    public static BlockState leaves() {
        return ModBlocks.CEDAR_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 1);
    }

    static void trunk(ChunkSink sink, Tree tr) {
        if (!sink.contains(tr.x, tr.z)) return;
        BlockState log = log(Direction.Axis.Y);
        BlockState ribbon = ModBlocks.CEDAR_LOG_RIBBON.get().defaultBlockState();
        for (int y = tr.base + 1; y <= tr.base + tr.height; y++) {
            sink.set(tr.x, y, tr.z, tr.ribbon && y == tr.base + 2 ? ribbon : log);
        }
    }

    static void crown(ChunkSink sink, Tree tr) {
        if (!sink.touches(tr.x - 4, tr.z - 4, tr.x + 4, tr.z + 4)) return;
        BlockState leaves = leaves();
        int top = tr.top();
        int span = Math.max(1, top - tr.crownBase);
        for (int y = tr.crownBase; y <= top; y++) {
            double rel = (y - tr.crownBase) / (double) span;
            double r = tr.radius * Math.pow(1.0 - rel, 0.85) + 0.35;
            if (y == tr.crownBase) r *= 0.7;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    int x = tr.x + dx, z = tr.z + dz;
                    if (!sink.contains(x, z)) continue;
                    double jitter = (rand(x * 7 + y, z * 13 - y, 17) - 0.5) * 0.9;
                    if (dx * dx + dz * dz > r * r + jitter) continue;
                    if (rand(x + y * 31, z, 19) < 0.05) continue;
                    sink.soft(x, y, z, leaves);
                }
            }
        }
    }

    static void boulders(ChunkSink sink, TerrainData t) {
        final int cell = 11;
        int gx0 = Math.floorDiv(sink.minX - 3, cell), gx1 = Math.floorDiv(sink.maxX + 3, cell);
        int gz0 = Math.floorDiv(sink.minZ - 3, cell), gz1 = Math.floorDiv(sink.maxZ + 3, cell);
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                if (rand(gx, gz, 31) > 0.2) continue;
                int cx = gx * cell + Math.floorMod(hash(gx, gz, 32), cell);
                int cz = gz * cell + Math.floorMod(hash(gx, gz, 33), cell);
                if (t.trees(cx, cz) < 60 || t.water(cx, cz) > 0 || t.surface(cx, cz) == TerrainData.SURF_TRAIL) continue;
                int r = 1 + Math.floorMod(hash(gx, gz, 34), 2);
                int base = t.height(cx, cz);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int x = cx + dx, z = cz + dz;
                        if (!sink.contains(x, z) || t.surface(x, z) == TerrainData.SURF_TRAIL) continue;
                        int hh = r + 1 - Math.max(Math.abs(dx), Math.abs(dz)) - (rand(x, z, 35) < 0.4 ? 1 : 0);
                        int ground = t.height(x, z);
                        for (int y = ground + 1; y <= base + hh; y++) {
                            boolean moss = rand(x, z * 3 + y, 36) < 0.65;
                            sink.soft(x, y, z, (moss ? ModBlocks.MOSSY_ROCK : ModBlocks.MOUNTAIN_ROCK).get().defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    static void fallenLogs(ChunkSink sink, TerrainData t) {
        final int cell = 14;
        int gx0 = Math.floorDiv(sink.minX - 8, cell), gx1 = Math.floorDiv(sink.maxX + 8, cell);
        int gz0 = Math.floorDiv(sink.minZ - 8, cell), gz1 = Math.floorDiv(sink.maxZ + 8, cell);
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                if (rand(gx, gz, 41) > 0.24) continue;
                int sx = gx * cell + Math.floorMod(hash(gx, gz, 42), cell);
                int sz = gz * cell + Math.floorMod(hash(gx, gz, 43), cell);
                if (t.trees(sx, sz) < 80) continue;
                boolean alongX = (hash(gx, gz, 44) & 1) == 0;
                int len = 4 + Math.floorMod(hash(gx, gz, 45), 4);
                BlockState log = log(alongX ? Direction.Axis.X : Direction.Axis.Z);
                for (int k = 0; k < len; k++) {
                    int x = sx + (alongX ? k : 0), z = sz + (alongX ? 0 : k);
                    if (t.surface(x, z) == TerrainData.SURF_TRAIL || t.water(x, z) > 0) break;
                    sink.soft(x, t.height(x, z) + 1, z, log);
                }
            }
        }
    }

    static void undergrowth(ChunkSink sink, TerrainData t) {
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        BlockState fern = Blocks.FERN.defaultBlockState();
        BlockState bigFernLo = Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState bigFernHi = Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        for (int x = sink.minX; x <= sink.maxX; x++) {
            for (int z = sink.minZ; z <= sink.maxZ; z++) {
                if (t.water(x, z) > 0) continue;
                int s = t.surface(x, z);
                int y = t.height(x, z) + 1;
                if (!sink.isAir(x, y, z)) continue;
                double r = rand(x, z, 51);
                BlockState leaves = ModBlocks.FALLEN_LEAVES.get().defaultBlockState()
                        .setValue(FacingBlock.FACING, dirs[Math.floorMod(hash(x, z, 52), 4)]);
                if (s == TerrainData.SURF_FOREST || s == TerrainData.SURF_MOSS || s == TerrainData.SURF_DIRT) {
                    if (t.trees(x, z) == 0 && s == TerrainData.SURF_DIRT) {
                        // village yards: tall dead grass
                        if (r < 0.28) sink.set(x, y, z, Blocks.GRASS.defaultBlockState());
                        else if (r < 0.31 && sink.isAir(x, y + 1, z)) {
                            sink.set(x, y, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                            sink.set(x, y + 1, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                        }
                        continue;
                    }
                    if (r < 0.06) sink.set(x, y, z, fern);
                    else if (r < 0.075 && sink.isAir(x, y + 1, z)) {
                        sink.set(x, y, z, bigFernLo);
                        sink.set(x, y + 1, z, bigFernHi);
                    } else if (r < 0.08) sink.set(x, y, z, Blocks.DEAD_BUSH.defaultBlockState());
                    else if (r < 0.088 && s == TerrainData.SURF_MOSS) sink.set(x, y, z, Blocks.BROWN_MUSHROOM.defaultBlockState());
                    else if (r < 0.19) sink.set(x, y, z, leaves);
                    else if (r < 0.24 && s == TerrainData.SURF_MOSS) sink.set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (s == TerrainData.SURF_TRAIL) {
                    if (r < 0.05) sink.set(x, y, z, leaves);
                }
            }
        }
    }
}
