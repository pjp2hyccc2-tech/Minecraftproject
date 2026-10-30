package com.mcbesiege.machine;

import com.mcbesiege.physics.StaticBox;
import com.mcbesiege.physics.WorldCollisionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Turns Minecraft block collision shapes into static physics boxes. Full cubes are greedily merged into horizontal
 * slabs per layer (smooth driving across block seams); faces touching other full cubes are flagged as unexposed so
 * contacts are never pushed sideways through a floor.
 */
public final class McWorldGeometry implements WorldCollisionProvider {
    private final ServerLevel level;

    public McWorldGeometry(ServerLevel level) {
        this.level = level;
    }

    @Override
    public boolean isLoaded(double x, double y, double z) {
        return level.isLoaded(BlockPos.containing(x, y, z));
    }

    @Override
    public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, List<StaticBox> out) {
        int x0 = (int) Math.floor(minX), y0 = Math.max(level.getMinBuildHeight(), (int) Math.floor(minY));
        int z0 = (int) Math.floor(minZ), x1 = (int) Math.floor(maxX), y1 = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(maxY));
        int z1 = (int) Math.floor(maxZ);
        int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
        if (sx <= 0 || sy <= 0 || sz <= 0) return;
        if ((long) sx * sy * sz > 200_000) return; // absurd region (runaway body); skip
        // friction code per full cell, 0 = not a full cube
        int[] full = new int[sx * sy * sz];
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    mp.set(x0 + x, y0 + y, z0 + z);
                    if (!level.isLoaded(mp)) continue;
                    BlockState s = level.getBlockState(mp);
                    if (s.isAir()) continue;
                    VoxelShape shape = s.getCollisionShape(level, mp);
                    if (shape.isEmpty()) continue;
                    double fr = friction(s), re = restitution(s);
                    if (isFullCube(shape)) {
                        full[(y * sz + z) * sx + x] = code(fr, re);
                    } else {
                        for (AABB bb : shape.toAabbs()) {
                            out.add(new StaticBox(mp.getX() + bb.minX, mp.getY() + bb.minY, mp.getZ() + bb.minZ,
                                    mp.getX() + bb.maxX, mp.getY() + bb.maxY, mp.getZ() + bb.maxZ, fr, re, StaticBox.ALL));
                        }
                    }
                }
            }
        }
        // greedy merge per layer
        boolean[] used = new boolean[full.length];
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int idx = (y * sz + z) * sx + x;
                    int c = full[idx];
                    if (c == 0 || used[idx]) continue;
                    int w = 1;
                    while (x + w < sx && full[idx + w] == c && !used[idx + w]) w++;
                    int d = 1;
                    outer:
                    while (z + d < sz) {
                        for (int k = 0; k < w; k++) {
                            int j = (y * sz + z + d) * sx + x + k;
                            if (full[j] != c || used[j]) break outer;
                        }
                        d++;
                    }
                    for (int dz = 0; dz < d; dz++) for (int k = 0; k < w; k++) used[(y * sz + z + dz) * sx + x + k] = true;
                    int ax = x0 + x, ay = y0 + y, az = z0 + z;
                    int exposed = exposure(full, sx, sy, sz, x0, y0, z0, x, y, z, w, d);
                    out.add(new StaticBox(ax, ay, az, ax + w, ay + 1, az + d, frictionOf(c), restitutionOf(c), exposed));
                }
            }
        }
    }

    private int exposure(int[] full, int sx, int sy, int sz, int x0, int y0, int z0, int x, int y, int z, int w, int d) {
        int e = 0;
        // top / bottom
        boolean top = false, bot = false;
        for (int dz = 0; dz < d && !(top && bot); dz++) {
            for (int k = 0; k < w; k++) {
                if (!top && !solid(full, sx, sy, sz, x0, y0, z0, x + k, y + 1, z + dz)) top = true;
                if (!bot && !solid(full, sx, sy, sz, x0, y0, z0, x + k, y - 1, z + dz)) bot = true;
            }
        }
        if (top) e |= StaticBox.POS_Y;
        if (bot) e |= StaticBox.NEG_Y;
        boolean nx = false, px = false;
        for (int dz = 0; dz < d; dz++) {
            if (!nx && !solid(full, sx, sy, sz, x0, y0, z0, x - 1, y, z + dz)) nx = true;
            if (!px && !solid(full, sx, sy, sz, x0, y0, z0, x + w, y, z + dz)) px = true;
        }
        if (nx) e |= StaticBox.NEG_X;
        if (px) e |= StaticBox.POS_X;
        boolean nz = false, pz = false;
        for (int k = 0; k < w; k++) {
            if (!nz && !solid(full, sx, sy, sz, x0, y0, z0, x + k, y, z - 1)) nz = true;
            if (!pz && !solid(full, sx, sy, sz, x0, y0, z0, x + k, y, z + d)) pz = true;
        }
        if (nz) e |= StaticBox.NEG_Z;
        if (pz) e |= StaticBox.POS_Z;
        return e;
    }

    private boolean solid(int[] full, int sx, int sy, int sz, int x0, int y0, int z0, int x, int y, int z) {
        if (x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz) return full[(y * sz + z) * sx + x] != 0;
        BlockPos p = new BlockPos(x0 + x, y0 + y, z0 + z);
        if (!level.isLoaded(p)) return true;
        BlockState s = level.getBlockState(p);
        return !s.isAir() && isFullCube(s.getCollisionShape(level, p));
    }

    private static boolean isFullCube(VoxelShape shape) {
        AABB b = shape.bounds();
        return shape.toAabbs().size() == 1 && b.minX <= 0 && b.minY <= 0 && b.minZ <= 0 && b.maxX >= 1 && b.maxY >= 1 && b.maxZ >= 1;
    }

    static double friction(BlockState s) {
        if (s.is(Blocks.HONEY_BLOCK)) return 1.5;
        double slip = s.getBlock().getFriction();
        return Math.max(0.02, Math.min(1.2, 2.0 * (1.0 - slip)));
    }

    static double restitution(BlockState s) {
        return s.is(Blocks.SLIME_BLOCK) ? 0.85 : 0.0;
    }

    // pack friction (0..2, 0.01 steps) and restitution (0..1) into a non-zero int key
    private static int code(double fr, double re) {
        int f = (int) Math.round(fr * 100), r = (int) Math.round(re * 100);
        return 1 + f * 1000 + r;
    }

    private static double frictionOf(int c) { return ((c - 1) / 1000) / 100.0; }

    private static double restitutionOf(int c) { return ((c - 1) % 1000) / 100.0; }
}
