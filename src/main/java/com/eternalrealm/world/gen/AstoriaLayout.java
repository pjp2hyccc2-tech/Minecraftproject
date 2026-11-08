package com.eternalrealm.world.gen;

/**
 * Where each {@link AstoriaBiome} lies: the area rings of {@link AstoriaAreas} with slightly noisy borders, and
 * 3-4 sub-biomes per area chosen by low-frequency noise. Pure and deterministic (fixed seed, independent of the world
 * seed) so the realm has the same hand-made layout on every server; the terrain shape still comes from the world seed.
 */
public final class AstoriaLayout {
    public static final long SEED = 0x4153_5452_4941L;
    /** Largest distance (blocks) by which a biome border can move away from its exact ring. */
    public static final int MAX_BORDER_JITTER = 8;
    /** The meadow biome around the capital walls (jittered like the rings). */
    public static final int CAPITAL_RADIUS = 96;
    /** Centre of the dark tower (終焉の塔), the d8 entrance. */
    public static final int TOWER_X = 0;
    public static final int TOWER_Z = -2100;
    /** Radius of the darkest ground around the tower. */
    public static final int SPIRE_RADIUS = 160;

    private static final GenNoise BORDER_COARSE = new GenNoise(SEED);
    private static final GenNoise BORDER_FINE = new GenNoise(SEED + 1);
    private static final GenNoise PATCH = new GenNoise(SEED + 2);
    private static final GenNoise PATCH_ALT = new GenNoise(SEED + 3);
    private static final GenNoise OASIS = new GenNoise(SEED + 4);

    private AstoriaLayout() {
    }

    /** Offset in [-{@link #MAX_BORDER_JITTER}, {@link #MAX_BORDER_JITTER}] added to the distance at (x, z). */
    public static double borderJitter(double x, double z) {
        return 5.0 * BORDER_COARSE.sample(x / 61.0, z / 61.0) + 3.0 * BORDER_FINE.sample(x / 19.0, z / 19.0);
    }

    /** Distance from the capital as seen by the biome borders. */
    public static double jitteredDistance(double x, double z) {
        return Math.sqrt(x * x + z * z) + borderJitter(x, z);
    }

    /** Area of the biome at (x, z): equal to {@link AstoriaAreas#areaAt} except within a few blocks of a ring. */
    public static int biomeAreaAt(double x, double z) {
        return AstoriaAreas.areaAtDistance(jitteredDistance(x, z));
    }

    /** The biome at block (x, z) (biomes do not change with height). */
    public static AstoriaBiome biomeAt(int x, int z) {
        double distance = jitteredDistance(x, z);
        int area = AstoriaAreas.areaAtDistance(distance);
        double patch = PATCH.fractal(x / 260.0, z / 260.0, 2);
        double alt = PATCH_ALT.fractal(x / 190.0, z / 190.0, 2);
        return switch (area) {
            case 1 -> {
                if (distance < CAPITAL_RADIUS) {
                    yield AstoriaBiome.ROYAL_CAPITAL;
                }
                yield patch > 0.12 ? AstoriaBiome.ARCELIA_FLOWER_MEADOW
                        : patch < -0.12 ? AstoriaBiome.ARCELIA_FARMLANDS : AstoriaBiome.ARCELIA_PLAINS;
            }
            case 2 -> alt > 0.14 ? AstoriaBiome.ELDER_MUSHROOM_GLADE
                    : patch < -0.02 ? AstoriaBiome.ELDER_OLD_GROWTH : AstoriaBiome.ELDER_FOREST;
            case 3 -> {
                if (OASIS.sample(x / 72.0, z / 72.0) > 0.42) {
                    yield AstoriaBiome.SANDRIA_OASIS;
                }
                yield patch > 0.06 ? AstoriaBiome.SANDRIA_MESA : AstoriaBiome.SANDRIA_DUNES;
            }
            case 4 -> {
                if (alt > 0.16) {
                    yield AstoriaBiome.NORDIA_ICE_SPIKES;
                }
                if (alt < -0.16) {
                    yield AstoriaBiome.NORDIA_CRYSTAL_HOLLOW;
                }
                yield patch > 0.0 ? AstoriaBiome.NORDIA_TAIGA : AstoriaBiome.NORDIA_SNOWFIELD;
            }
            case 5 -> patch > 0.1 ? AstoriaBiome.DRAGONIA_CALDERA
                    : patch < -0.1 ? AstoriaBiome.DRAGONIA_BASALT_FIELDS : AstoriaBiome.DRAGONIA_ASHLANDS;
            case 6 -> alt > 0.1 ? AstoriaBiome.CELESTIA_CHERRY_GROVE
                    : alt < -0.1 ? AstoriaBiome.CELESTIA_WHITE_WOODS : AstoriaBiome.CELESTIA_MEADOW;
            default -> {
                double dx = x - TOWER_X;
                double dz = z - TOWER_Z;
                if (Math.sqrt(dx * dx + dz * dz) + borderJitter(z, x) < SPIRE_RADIUS) {
                    yield AstoriaBiome.ABYSS_SPIRE;
                }
                yield patch > 0.12 ? AstoriaBiome.ABYSS_VOID_RIFT
                        : patch < -0.12 ? AstoriaBiome.ABYSS_DEAD_FOREST : AstoriaBiome.ABYSS_WASTES;
            }
        };
    }
}
