package com.huntcraft.quest.world;

import com.huntcraft.HuntCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A designed quest map tile (512x512 blocks, see tools/quest/qmap.py): per-column planes (height, area, biome,
 * surface palette, fluids, caves), sparse voxels (camp, landmarks, gathering points) and area / camp metadata.
 * Loaded once per map from {@code /data/huntcraft/quest_maps/<id>.nbt}; immutable afterwards (read by worldgen threads).
 * Tile coordinates: u = east, v = south, the region centre is (256, 256).
 */
public final class QuestMapData {
    public static final int SIZE = 512;
    public static final int HALF = 256;
    public static final int CLIMATE_NONE = 0, CLIMATE_COLD = 1, CLIMATE_HOT = 2, CLIMATE_SAFE = 3;
    public static final int FLUID_NONE = 0, FLUID_WATER = 1, FLUID_LAVA = 2, FLUID_FROZEN = 3;

    /** Numbered area of the map (MH style "Area 3"). Coordinates are tile coordinates. */
    public record Area(int n, String key, int x, int z, int r, boolean large, int climate, List<String> smalls) {
        public String nameKey() {
            return "area.huntcraft." + key;
        }
    }

    /** Base camp (tile coordinates). */
    public record Camp(BlockPos pos, float yaw, int radius, BlockPos supply, BlockPos delivery, BlockPos flag, BlockPos bed, List<BlockPos> arrivals) {
    }

    /** What lies outside the tile (between regions). */
    public record Outside(int floor, int top, int rock, int fluid, int level, int biome) {
    }

    private static final Map<String, QuestMapData> CACHE = new ConcurrentHashMap<>();

    public final String id;
    public final boolean fallback;
    public final int sea;
    public final int subDepth;
    public final byte[] height, zone, biome, top, sub, rock, ctop, fluid, level, clo, chi;
    public final BlockState[] spal;
    public final BlockState[] vpal;
    /** Voxels grouped by tile chunk (index (v >> 4) * 32 + (u >> 4)): packed (u << 18 | v << 9 | y) and palette index. */
    public final int[][] chunkVoxPos;
    public final int[][] chunkVoxState;
    public final int[] gatherPos;
    public final byte[] gatherKind;
    public final List<Area> areas;
    private final Map<Integer, Area> byNumber = new HashMap<>();
    public final List<String> biomeIds;
    public final int caveBiome;
    public final Camp camp;
    public final Outside outside;

    private QuestMapData(String id, CompoundTag t, boolean fallback) {
        this.id = id;
        this.fallback = fallback;
        this.sea = t.getInt("sea");
        this.subDepth = Math.max(1, t.contains("subDepth") ? t.getInt("subDepth") : 3);
        int n = SIZE * SIZE;
        this.height = plane(t, "height", n);
        this.zone = plane(t, "zone", n);
        this.biome = plane(t, "biome", n);
        this.top = plane(t, "top", n);
        this.sub = plane(t, "sub", n);
        this.rock = plane(t, "rock", n);
        this.ctop = plane(t, "ctop", n);
        this.fluid = plane(t, "fluid", n);
        this.level = plane(t, "level", n);
        this.clo = plane(t, "clo", n);
        this.chi = plane(t, "chi", n);
        this.spal = palette(t.getList("spal", Tag.TAG_COMPOUND));
        this.vpal = palette(t.getList("vpal", Tag.TAG_COMPOUND));
        // voxels -> per chunk
        int[] vpos = t.getIntArray("vpos");
        int[] vst = t.getIntArray("vstate");
        int[] counts = new int[32 * 32];
        int nv = Math.min(vpos.length, vst.length);
        for (int i = 0; i < nv; i++) counts[chunkIndex(vpos[i])]++;
        chunkVoxPos = new int[32 * 32][];
        chunkVoxState = new int[32 * 32][];
        for (int c = 0; c < counts.length; c++) {
            chunkVoxPos[c] = new int[counts[c]];
            chunkVoxState[c] = new int[counts[c]];
        }
        int[] fill = new int[32 * 32];
        for (int i = 0; i < nv; i++) {
            int c = chunkIndex(vpos[i]);
            chunkVoxPos[c][fill[c]] = vpos[i];
            chunkVoxState[c][fill[c]] = Math.max(0, Math.min(vpal.length - 1, vst[i]));
            fill[c]++;
        }
        this.gatherPos = t.getIntArray("gpos");
        this.gatherKind = t.getByteArray("gkind");
        List<Area> list = new ArrayList<>();
        ListTag al = t.getList("areas", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) {
            CompoundTag a = al.getCompound(i);
            List<String> smalls = new ArrayList<>();
            ListTag sl = a.getList("smalls", Tag.TAG_STRING);
            for (int k = 0; k < sl.size(); k++) smalls.add(sl.getString(k));
            Area area = new Area(a.getInt("n"), a.getString("key"), a.getInt("x"), a.getInt("z"), Math.max(4, a.getInt("r")),
                    a.getBoolean("large"), a.getInt("climate"), Collections.unmodifiableList(smalls));
            list.add(area);
            byNumber.put(area.n(), area);
        }
        this.areas = Collections.unmodifiableList(list);
        List<String> bids = new ArrayList<>();
        ListTag bl = t.getList("biomes", Tag.TAG_STRING);
        for (int i = 0; i < bl.size(); i++) bids.add(bl.getString(i));
        this.biomeIds = Collections.unmodifiableList(bids);
        this.caveBiome = t.contains("caveBiome") ? t.getInt("caveBiome") : -1;
        CompoundTag c = t.getCompound("camp");
        List<BlockPos> arrivals = new ArrayList<>();
        for (int p : c.getIntArray("arrivals")) arrivals.add(unpack(p));
        if (arrivals.isEmpty()) arrivals.add(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z") + 4));
        this.camp = new Camp(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")), (float) c.getDouble("yaw"), Math.max(8, c.getInt("r")),
                pos3(c.getIntArray("supply")), pos3(c.getIntArray("delivery")), pos3(c.getIntArray("flag")), pos3(c.getIntArray("bed")),
                Collections.unmodifiableList(arrivals));
        CompoundTag o = t.getCompound("outside");
        this.outside = new Outside(o.getInt("floor"), o.getInt("top"), o.getInt("rock"), o.getInt("fluid"), o.getInt("level"), o.getInt("biome"));
    }

    private static int chunkIndex(int packed) {
        int u = (packed >>> 18) & 511, v = (packed >>> 9) & 511;
        return (v >> 4) * 32 + (u >> 4);
    }

    public static BlockPos unpack(int packed) {
        return new BlockPos((packed >>> 18) & 511, packed & 511, (packed >>> 9) & 511);
    }

    private static BlockPos pos3(int[] a) {
        return a.length >= 3 ? new BlockPos(a[0], a[1], a[2]) : BlockPos.ZERO;
    }

    private static byte[] plane(CompoundTag t, String key, int n) {
        byte[] b = t.getByteArray(key);
        if (b.length == n) return b;
        byte[] out = new byte[n];
        System.arraycopy(b, 0, out, 0, Math.min(n, b.length));
        return out;
    }

    private static BlockState[] palette(ListTag list) {
        BlockState[] out = new BlockState[Math.max(1, list.size())];
        out[0] = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < list.size(); i++) {
            BlockState s;
            try {
                s = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), list.getCompound(i));
            } catch (RuntimeException e) {
                HuntCraft.LOGGER.warn("Quest map palette entry {} unreadable", list.getCompound(i), e);
                s = Blocks.STONE.defaultBlockState();
            }
            out[i] = s;
        }
        return out;
    }

    // ------------------------------------------------------------------ loading
    public static QuestMapData get(String id) {
        return CACHE.computeIfAbsent(id, QuestMapData::load);
    }

    @Nullable
    public static QuestMapData getIfLoaded(String id) {
        return CACHE.get(id);
    }

    private static QuestMapData load(String id) {
        String path = "/data/" + HuntCraft.MODID + "/quest_maps/" + id + ".nbt";
        try (InputStream in = QuestMapData.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            CompoundTag tag = NbtIo.readCompressed(in);
            QuestMapData d = new QuestMapData(id, tag, false);
            HuntCraft.LOGGER.info("Quest map {} loaded: {} areas, {} gathering points, camp at {}", id, d.areas.size(), d.gatherPos.length, d.camp.pos());
            return d;
        } catch (Exception e) {
            HuntCraft.LOGGER.error("Could not load quest map data {} - using a flat fallback map", id, e);
            return new QuestMapData(id, fallbackTag(), true);
        }
    }

    /** Flat grass field with one area and a camp (only used if the data file is missing / broken). */
    private static CompoundTag fallbackTag() {
        CompoundTag t = new CompoundTag();
        int n = SIZE * SIZE;
        byte[] h = new byte[n];
        byte[] z = new byte[n];
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                boolean wall = u < 32 || u > 480 || v < 32 || v > 480;
                h[v * SIZE + u] = (byte) (wall ? 120 : 64);
                z[v * SIZE + u] = (byte) (wall ? 0 : 1);
            }
        }
        t.putByteArray("height", h);
        t.putByteArray("zone", z);
        t.putInt("sea", 63);
        ListTag spal = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        spal.add(stone);
        t.put("spal", spal);
        ListTag areas = new ListTag();
        CompoundTag a = new CompoundTag();
        a.putInt("n", 1);
        a.putString("key", "fallback");
        a.putInt("x", 256);
        a.putInt("z", 256);
        a.putInt("r", 60);
        a.putBoolean("large", true);
        areas.add(a);
        t.put("areas", areas);
        CompoundTag c = new CompoundTag();
        c.putInt("x", 256);
        c.putInt("y", 65);
        c.putInt("z", 400);
        c.putInt("r", 16);
        t.put("camp", c);
        t.put("outside", new CompoundTag());
        return t;
    }

    // ------------------------------------------------------------------ queries (tile coordinates)
    public static boolean inTile(int u, int v) {
        return u >= 0 && v >= 0 && u < SIZE && v < SIZE;
    }

    public static int idx(int u, int v) {
        return v * SIZE + u;
    }

    public int height(int u, int v) {
        return height[idx(u, v)] & 255;
    }

    public int zone(int u, int v) {
        return inTile(u, v) ? zone[idx(u, v)] & 255 : 0;
    }

    public int fluid(int u, int v) {
        return fluid[idx(u, v)] & 255;
    }

    public int fluidLevel(int u, int v) {
        return level[idx(u, v)] & 255;
    }

    public boolean isCave(int u, int v) {
        return (clo[idx(u, v)] & 255) > 0;
    }

    /** Standing y (first free block above the terrain, fluids included). */
    public int standY(int u, int v) {
        int i = idx(u, v);
        int y = height[i] & 255;
        if (fluid[i] != 0) y = Math.max(y, level[i] & 255);
        return y + 1;
    }

    /** Max height difference to the 8 neighbours (terrain only). */
    public int roughness(int u, int v) {
        int h = height(u, v);
        int r = 0;
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                int nu = u + du, nv = v + dv;
                if (inTile(nu, nv)) r = Math.max(r, Math.abs(height(nu, nv) - h));
            }
        }
        return r;
    }

    @Nullable
    public Area area(int n) {
        return byNumber.get(n);
    }

    public int gatherCount() {
        return gatherPos.length;
    }
}
