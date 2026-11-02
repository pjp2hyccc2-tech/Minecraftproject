package com.sounan.world;

import com.sounan.Sounan;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * The baked mountain (tools/terrain/terrain.py): per column surface height, surface type, tree density, water level and
 * area id for X, Z in -256..255. Read-only after loading; shared by the chunk generator (worker threads), the server
 * game logic and the client (area music, phone map).
 */
public final class TerrainData {
    public static final int SURF_FOREST = 0, SURF_MOSS = 1, SURF_TRAIL = 2, SURF_ROCK = 3, SURF_STONES = 4, SURF_GRAVEL = 5,
            SURF_ASPHALT = 6, SURF_DIRT = 7, SURF_MOSSROCK = 8;
    public static final int AREA_OUT = 0, AREA_RAKURAKU = 1, AREA_SAWA = 2, AREA_GAKE = 3, AREA_SUGI = 4, AREA_HAISON = 5,
            AREA_HOKORA = 6, AREA_TSURIBASHI = 7, AREA_KOYA = 8, AREA_TETTO = 9, AREA_TUNNEL = 10, AREA_TOZANGUCHI = 11;
    public static final String[] AREA_KEYS = {"out", "rakuraku", "sawa", "gake", "sugi", "haison", "hokora", "tsuribashi",
            "koya", "tetto", "tunnel", "tozanguchi"};
    /** Height of the impassable wall outside the baked area. */
    public static final int OUTSIDE_HEIGHT = 236;

    private static volatile TerrainData instance;

    public final int size;
    public final int origin;
    private final byte[] height, surface, trees, water, area;

    private TerrainData(int size, int origin, byte[] height, byte[] surface, byte[] trees, byte[] water, byte[] area) {
        this.size = size;
        this.origin = origin;
        this.height = height;
        this.surface = surface;
        this.trees = trees;
        this.water = water;
        this.area = area;
    }

    public static TerrainData get() {
        TerrainData t = instance;
        if (t == null) {
            synchronized (TerrainData.class) {
                t = instance;
                if (t == null) {
                    t = load();
                    instance = t;
                }
            }
        }
        return t;
    }

    private static TerrainData load() {
        try (InputStream raw = TerrainData.class.getResourceAsStream("/data/sounan/terrain/terrain.bin.gz")) {
            if (raw == null) throw new IllegalStateException("terrain.bin.gz is missing from the jar");
            DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'S' || magic[1] != 'N' || magic[2] != 'T' || magic[3] != 'R') {
                throw new IllegalStateException("bad terrain magic");
            }
            int size = in.readUnsignedShort();
            int origin = in.readShort();
            byte[][] planes = new byte[5][size * size];
            for (byte[] plane : planes) in.readFully(plane);
            Sounan.LOGGER.info("[sounan] mountain terrain loaded: {}x{} from {}", size, size, origin);
            return new TerrainData(size, origin, planes[0], planes[1], planes[2], planes[3], planes[4]);
        } catch (Exception e) {
            throw new IllegalStateException("[sounan] could not load the mountain terrain", e);
        }
    }

    public boolean inside(int x, int z) {
        int i = x - origin;
        int j = z - origin;
        return i >= 0 && j >= 0 && i < size && j < size;
    }

    private int index(int x, int z) {
        return (z - origin) * size + (x - origin);
    }

    /** Y of the top solid block of the column. */
    public int height(int x, int z) {
        if (!inside(x, z)) return OUTSIDE_HEIGHT;
        return height[index(x, z)] & 0xFF;
    }

    public int surface(int x, int z) {
        if (!inside(x, z)) return SURF_ROCK;
        return surface[index(x, z)] & 0xFF;
    }

    /** Tree density 0..255. */
    public int trees(int x, int z) {
        if (!inside(x, z)) return 0;
        return trees[index(x, z)] & 0xFF;
    }

    /** Water surface Y, or 0 for none. */
    public int water(int x, int z) {
        if (!inside(x, z)) return 0;
        return water[index(x, z)] & 0xFF;
    }

    public int area(int x, int z) {
        if (!inside(x, z)) return AREA_OUT;
        return area[index(x, z)] & 0xFF;
    }

    public int area(double x, double z) {
        return area((int) Math.floor(x), (int) Math.floor(z));
    }

    /** Largest height difference to the four neighbours (steep columns get rock instead of soil). */
    public int steepness(int x, int z) {
        int h = height(x, z);
        int m = Math.abs(height(x + 1, z) - h);
        m = Math.max(m, Math.abs(height(x - 1, z) - h));
        m = Math.max(m, Math.abs(height(x, z + 1) - h));
        m = Math.max(m, Math.abs(height(x, z - 1) - h));
        return m;
    }

    public static String areaKey(int area) {
        return area >= 0 && area < AREA_KEYS.length ? AREA_KEYS[area] : "out";
    }
}
