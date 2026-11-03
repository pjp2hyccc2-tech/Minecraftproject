package com.hitori.client.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Occlusion between the listener and a sound (docs/DESIGN.md §10): a voxel walk (Amanatides-Woo DDA) along the segment
 * that counts the blocks in between. Opaque full blocks count 1; door-like blocks count by their {@code open} state -
 * a boolean {@code open} (vanilla doors, the balcony door): shut 0.5 (1 if it renders solid), open 0; an integer
 * {@code open} (the fusuma and the closet's oshiire fusuma: paper on a lattice): 0 shut = 0.4, 1 a crack = 0.2, 2 open
 * = 0; glass, furniture and everything not opaque count nothing. The sound's own block never counts; the listener's
 * own block counts only when it is a shut door-like block. Main (client) thread only: it reads the level.
 */
final class AcousticsOcclusion {
    /** 8 units x -3 dB = the -24 dB cap of the design. */
    static final float CAP = 8.0f;
    private static final double MAX_DIST = 160.0;
    private static final Map<Block, Property<?>> OPEN = new IdentityHashMap<>();
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
            if (!own.isAir() && openProperty(own) != null) sum += weight(level, p, own);
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
        Property<?> open = openProperty(s);
        if (open instanceof BooleanProperty bp) {
            if (s.getValue(bp)) return 0f;
            return s.isSolidRender(level, p) ? 1f : 0.5f;
        }
        if (open instanceof IntegerProperty ip) {
            int v = s.getValue(ip);
            return v <= 0 ? 0.4f : (v == 1 ? 0.2f : 0f);
        }
        if (s.getBlock() instanceof DoorBlock) return 0.5f;
        return s.isSolidRender(level, p) ? 1f : 0f;
    }

    /** The block's {@code open} property (boolean or integer, any owner), cached per block; null if it has none. */
    static Property<?> openProperty(BlockState s) {
        Block b = s.getBlock();
        Property<?> cached = OPEN.get(b);
        if (cached == null) {
            cached = NONE;
            for (Property<?> pr : s.getProperties()) {
                if ("open".equals(pr.getName()) && (pr instanceof BooleanProperty || pr instanceof IntegerProperty)) {
                    cached = pr;
                    break;
                }
            }
            OPEN.put(b, cached);
        }
        return cached == NONE ? null : cached;
    }
}
