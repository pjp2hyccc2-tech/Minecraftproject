package com.reisatsu.world;

/**
 * The geometry of the site around HIDAMARI (docs/DESIGN.md §2): the national road in front (south) of the parking
 * lot, bending north at the deadly curve east of the restaurant, the property with its yards, and the playable area.
 * <p>
 * The road is described by one cross coordinate {@link #rho}: the distance from the curve's centre, which on the
 * straight stretches is simply the distance from the centre line's parallel through the centre. Lanes are
 * 14 ≤ ρ &lt; 23 (centre line at ρ 18.5), the shoulder 23..24, the guardrail 24..25, the inner curb 13..14.
 */
public final class Site {
    private Site() {}

    /** Centre of the curve. */
    public static final double CX = 60.0, CZ = 36.0;
    public static final double RHO_CURB = 13.0, RHO_LANE = 14.0, RHO_CENTRE = 18.5, RHO_SHOULDER = 23.0, RHO_RAIL = 24.0, RHO_END = 25.0;
    /** Where the road vanishes into the fog (invisible walls). */
    public static final int ROAD_WEST_END = -40, ROAD_NORTH_END = 28;

    /** Parking lot (asphalt) and the apron in front of the building. */
    public static final int LOT_X1 = -9, LOT_X2 = 48, LOT_Z1 = 34, LOT_Z2 = 48;
    /** The property north of the lot: building, staff path (west), side strip (east), back yard (north). */
    public static final int PROP_X1 = -9, PROP_X2 = 46, PROP_Z1 = -11, PROP_Z2 = 33;

    public static double rho(double x, double z) {
        double dx = x - CX, dz = z - CZ;
        if (dx < 0) return dz;
        if (dz < 0) return dx;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** ρ of a block column (its centre). */
    public static double rho(int x, int z) {
        return rho(x + 0.5, z + 0.5);
    }

    /** Angle on the curve in degrees: 90 where the straight road from the west meets it, 0 where the road turns north. */
    public static double angle(int x, int z) {
        double dx = x + 0.5 - CX, dz = z + 0.5 - CZ;
        if (dx < 0) return 90.0;
        if (dz < 0) return 0.0;
        return Math.toDegrees(Math.atan2(dz, dx));
    }

    /** True for columns of the road band (curb to guardrail) within the fog walls. */
    public static boolean roadBand(int x, int z) {
        double r = rho(x, z);
        return r >= RHO_CURB && r < RHO_END && x >= ROAD_WEST_END && z >= ROAD_NORTH_END;
    }

    /** True for the road band anywhere (beyond the fog walls too): the road goes on. */
    public static boolean roadAnywhere(int x, int z) {
        double r = rho(x, z);
        return r >= RHO_CURB && r < RHO_END;
    }

    public static boolean inLot(int x, int z) {
        return x >= LOT_X1 && x <= LOT_X2 && z >= LOT_Z1 && z <= LOT_Z2 + 1;
    }

    public static boolean inProperty(int x, int z) {
        return x >= PROP_X1 && x <= PROP_X2 && z >= PROP_Z1 && z <= PROP_Z2;
    }

    /** Where the player may walk: the property, the lot and the road to the fog walls. */
    public static boolean playable(int x, int z) {
        return inProperty(x, z) || inLot(x, z) || roadBand(x, z);
    }

    /** Columns just outside the playable area get invisible barrier walls. */
    public static boolean boundary(int x, int z) {
        if (playable(x, z)) return false;
        return playable(x + 1, z) || playable(x - 1, z) || playable(x, z + 1) || playable(x, z - 1);
    }

    /** Direction from the road toward the curve's centre side (for guardrails, chevrons): the "inner" side. */
    public static net.minecraft.core.Direction inward(int x, int z) {
        double dx = x + 0.5 - CX, dz = z + 0.5 - CZ;
        if (dx < 0) return net.minecraft.core.Direction.NORTH;
        if (dz < 0) return net.minecraft.core.Direction.WEST;
        return Math.abs(dx) > Math.abs(dz) ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.NORTH;
    }

    /** Axis the road runs along at this column (for painted lines). */
    public static net.minecraft.core.Direction.Axis roadAxis(int x, int z) {
        double dx = x + 0.5 - CX, dz = z + 0.5 - CZ;
        if (dx < 0) return net.minecraft.core.Direction.Axis.X;
        if (dz < 0) return net.minecraft.core.Direction.Axis.Z;
        return Math.abs(dx) > Math.abs(dz) ? net.minecraft.core.Direction.Axis.Z : net.minecraft.core.Direction.Axis.X;
    }

    /** Integer hash (deterministic, seed independent; a 64-bit finaliser, so neighbouring inputs are unrelated). */
    public static int hash(int x, int z, int salt) {
        long k = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        k ^= k >>> 33;
        k *= 0xFF51AFD7ED558CCDL;
        k ^= k >>> 33;
        k *= 0xC4CEB9FE1A85EC53L;
        k ^= k >>> 33;
        return (int) k;
    }

    /** 0..1 */
    public static double rand(int x, int z, int salt) {
        return (hash(x, z, salt) >>> 8) / (double) (1 << 24);
    }

    /** Smooth value noise 0..1 with the given cell size. */
    public static double noise(double x, double z, double cell, int salt) {
        double fx = x / cell, fz = z / cell;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = rand(ix, iz, salt), b = rand(ix + 1, iz, salt), c = rand(ix, iz + 1, salt), d = rand(ix + 1, iz + 1, salt);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }
}
