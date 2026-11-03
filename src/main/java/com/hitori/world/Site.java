package com.hitori.world;

/**
 * The geometry around コーポ白樺 (docs/DESIGN.md §2). North is −z. The building stands on X 0..44, Z 0..13 with its
 * outdoor corridor and stairs on the north (Z −3..−1, stairs at the west end) and its balconies on the south (Z 14..15),
 * facing a narrow street (Z 19..24) that ends at a small park in the east. A side path along the west of the building
 * leads from the street past the bicycle shelter to the stairs. Concrete block walls close the property; dark houses
 * stand across the street and behind. Invisible walls bound everything the player can reach.
 */
public final class Site {
    private Site() {}

    /** Top block of the ground (feet 65). */
    public static final int GROUND = 64;

    /** The building (outer walls). Units are 12 wide with shared walls: 201/101 x 0..11, 202 11..22, 203 22..33, 204 33..44. */
    public static final int BX1 = 0, BX2 = 44, BZ1 = 0, BZ2 = 13;
    public static final int[] UNIT_X0 = {0, 11, 22, 33};

    /** The property inside its block walls (the walls themselves are one further out). */
    public static final int PX1 = -9, PX2 = 46, PZ1 = -5, PZ2 = 16;
    /** The gap in the south wall where the side path meets the street. */
    public static final int GATE_X1 = -7, GATE_X2 = -3;
    /** The street: asphalt z 19..24 (gutter strips z 18 and 25), from the fog in the west to the park. */
    public static final int SX1 = -46, SX2 = 48, SZ1 = 19, SZ2 = 24;
    /** The small park at the street's east end. */
    public static final int KX1 = 49, KX2 = 63, KZ1 = 12, KZ2 = 31;

    public static boolean inBuilding(int x, int z) {
        return x >= BX1 && x <= BX2 && z >= BZ1 && z <= BZ2;
    }

    public static boolean inProperty(int x, int z) {
        return x >= PX1 && x <= PX2 && z >= PZ1 && z <= PZ2;
    }

    /** The street band including its gutter strips. */
    public static boolean inStreet(int x, int z) {
        return x >= SX1 && x <= SX2 && z >= SZ1 - 1 && z <= SZ2 + 1;
    }

    public static boolean inPark(int x, int z) {
        return x >= KX1 && x <= KX2 && z >= KZ1 && z <= KZ2;
    }

    /** Where the player may walk: the property (and its gate), the street, the park. */
    public static boolean playable(int x, int z) {
        if (inProperty(x, z) || inStreet(x, z) || inPark(x, z)) return true;
        return z == PZ2 + 1 && x >= GATE_X1 && x <= GATE_X2;
    }

    /** Columns just outside the playable area get invisible walls. */
    public static boolean boundary(int x, int z) {
        if (playable(x, z)) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) if ((dx != 0 || dz != 0) && playable(x + dx, z + dz)) return true;
        return false;
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
