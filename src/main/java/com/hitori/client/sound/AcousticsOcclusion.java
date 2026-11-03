package com.hitori.client.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Occlusion between the listener and a sound (docs/DESIGN.md §10): a voxel walk (Amanatides-Woo DDA) along the segment
 * that counts the blocks in between. Opaque full blocks count 1, closed doors (anything with an {@code open} state, the
 * kitchen swing doors' {@code swing} = 0) count half, open doors, glass, the mirror and furniture count nothing. The
 * sound's own block never counts; the listener's own block counts only when it is a closed door-like block (hiding in a
 * locker). Main (client) thread only: it reads the level.
 */
final class AcousticsOcclusion {
    /** 8 units x -3 dB = the -24 dB cap of the design. */
    static final float CAP = 8.0f;
    private static final double MAX_DIST = 160.0;
    private static final Map<Block, IntegerProperty> SWING = new IdentityHashMap<>();
    private static final IntegerProperty NONE = IntegerProperty.create("none", 0, 1);

    private AcousticsOcclusion() {}

    static float march(BlockGetter level, Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double len2 = dx * dx + dy * dy + dz * dz;
        if (len2 < 1.0e-6 || len2 > MAX_DIST * MAX_DIST) return 0f;
        int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
        int ex = Mth.floor(to.x), ey = Mth.floor(to.y), ez = Mth.floor(to.z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        float sum = 0f;
        if (x != ex || y != ey || z != ez) {
            BlockState own = level.getBlockState(p);
            if (!own.isAir() && own.hasProperty(BlockStateProperties.OPEN)) sum += weight(level, p, own);
        }
        int sx = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int sy = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int sz = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
        double tdx = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tdy = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tdz = sz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : (sx > 0 ? (x + 1 - from.x) : (from.x - x)) * tdx;
        double ty = sy == 0 ? Double.POSITIVE_INFINITY : (sy > 0 ? (y + 1 - from.y) : (from.y - y)) * tdy;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : (sz > 0 ? (z + 1 - from.z) : (from.z - z)) * tdz;
        for (int i = 0; i < 640; i++) {
            if (tx <= ty && tx <= tz) {
                if (tx >= 1.0) break;
                x += sx;
                tx += tdx;
            } else if (ty <= tz) {
                if (ty >= 1.0) break;
                y += sy;
                ty += tdy;
            } else {
                if (tz >= 1.0) break;
                z += sz;
                tz += tdz;
            }
            if (x == ex && y == ey && z == ez) break;
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            sum += weight(level, p, s);
            if (sum >= CAP) return CAP;
        }
        return sum;
    }

    static float weight(BlockGetter level, BlockPos p, BlockState s) {
        if (s.hasProperty(BlockStateProperties.OPEN)) {
            if (s.getValue(BlockStateProperties.OPEN)) return 0f;
            return s.isSolidRender(level, p) ? 1f : 0.5f;
        }
        if (s.getBlock() instanceof DoorBlock) return 0.5f;
        IntegerProperty swing = swing(s);
        if (swing != null) return s.getValue(swing) == 0 ? 0.5f : 0f;
        return s.isSolidRender(level, p) ? 1f : 0f;
    }

    /** The kitchen swing door's {@code swing} property (0 shut, 1/2 open), cached per block. */
    private static IntegerProperty swing(BlockState s) {
        Block b = s.getBlock();
        IntegerProperty cached = SWING.get(b);
        if (cached == null) {
            cached = NONE;
            for (Property<?> pr : s.getProperties()) {
                if ("swing".equals(pr.getName()) && pr instanceof IntegerProperty ip) {
                    cached = ip;
                    break;
                }
            }
            SWING.put(b, cached);
        }
        return cached == NONE ? null : cached;
    }
}
