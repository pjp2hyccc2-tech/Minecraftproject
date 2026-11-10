package com.eternalrealm.world.field;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The hand-made set pieces of the field map (waystone shrines, bridges, border markers, camps, farms, the harbour
 * town...) as exported by {@code tools/fields} into {@code data/eternalrealm/fieldpieces}: a manifest (per piece its
 * palette, block entity data, fluid ticks, GameTest probes and terrain samples) and one gzip file per piece holding
 * chunk-aligned column tiles ({@code ERP1}). Palette index 0 keeps the terrain, 1 is air; positions are absolute.
 *
 * <p>Built once per server: every tile is indexed by its chunk, so the chunk generator finds the pieces of a chunk
 * with one lookup, and a per-column bit mask answers "is this column part of a piece" for the decoration features.
 * Pure, immutable after construction and safe to share between world generation threads.</p>
 */
public final class FieldPieceData {
    public static final int FORMAT = 1;
    public static final String VOID = "eternalrealm:void";
    public static final String AIR = "minecraft:air";
    private static final byte[] MAGIC = {'E', 'R', 'P', '1'};

    /** Opens a file of the export directory by its relative name ({@code pieces/<id>.bin}). */
    @FunctionalInterface
    public interface Files {
        InputStream open(String name) throws IOException;
    }

    public record BlockEntity(int x, int y, int z, String nbt) {
    }

    public record Probe(int x, int y, int z, String state) {
    }

    /** A terrain height the piece was fitted to: {@code floor(FieldTerrain.heightAt(x, z)) == top} when exported. */
    public record Sample(int x, int z, int top) {
    }

    /** Where a waystone's lower half was placed. */
    public record Waystone(String id, int x, int y, int z, String facing) {
    }

    /** One piece: its palette and its tiles (decompressed, decoded on demand). */
    public static final class Piece {
        private final int index;
        private final String id;
        private final String name;
        private final int field;
        private final String kind;
        private final int[] box;
        private final List<String> palette;
        private final List<BlockEntity> blockEntities;
        private final List<int[]> fluidTicks;
        private final List<Probe> probes;
        private final List<Sample> samples;
        private final byte[] data;
        private final List<Integer> tileOffsets = new ArrayList<>();

        Piece(int index, String id, String name, int field, String kind, int[] box, List<String> palette,
              List<BlockEntity> blockEntities, List<int[]> fluidTicks, List<Probe> probes, List<Sample> samples,
              byte[] data) {
            this.index = index;
            this.id = id;
            this.name = name;
            this.field = field;
            this.kind = kind;
            this.box = box;
            this.palette = List.copyOf(palette);
            this.blockEntities = List.copyOf(blockEntities);
            this.fluidTicks = List.copyOf(fluidTicks);
            this.probes = List.copyOf(probes);
            this.samples = List.copyOf(samples);
            this.data = data;
        }

        public int index() {
            return index;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public int field() {
            return field;
        }

        public String kind() {
            return kind;
        }

        /** {@code {x0, y0, z0, x1, y1, z1}}, inclusive. */
        public int[] box() {
            return box.clone();
        }

        public List<String> palette() {
            return palette;
        }

        public List<BlockEntity> blockEntities() {
            return blockEntities;
        }

        public List<int[]> fluidTicks() {
            return fluidTicks;
        }

        public List<Probe> probes() {
            return probes;
        }

        public List<Sample> samples() {
            return samples;
        }

        public int tileCount() {
            return tileOffsets.size();
        }

        /** Decodes the {@code n}-th tile of this piece. */
        public Tile tile(int n) {
            return Tile.decode(this, tileOffsets.get(n));
        }
    }

    /**
     * One chunk of a piece: palette indices {@code voxels[column * height + (y - minY)]} with {@code column =
     * localX * 16 + localZ}; {@code touched[column]} is false where the piece leaves the whole column alone;
     * {@code foundation[column]} is the palette index the column is extended down with where the terrain lies lower
     * than its lowest block (0 = never).
     */
    public record Tile(Piece piece, int chunkX, int chunkZ, int minY, int height, short[] voxels, short[] foundation,
                       boolean[] touched) {
        public int at(int column, int y) {
            int dy = y - minY;
            return dy < 0 || dy >= height ? 0 : voxels[column * height + dy];
        }

        static Tile decode(Piece piece, int offset) {
            Reader in = new Reader(piece.data, offset);
            int chunkX = in.int32();
            int chunkZ = in.int32();
            int minY = in.int32();
            int height = in.varint();
            short[] voxels = new short[256 * height];
            short[] foundation = new short[256];
            boolean[] touched = new boolean[256];
            int paletteSize = piece.palette.size();
            for (int column = 0; column < 256; column++) {
                int runs = in.varint();
                if (runs == 0) {
                    continue;
                }
                touched[column] = true;
                int found = in.varint();
                if (found >= paletteSize) {
                    throw new IllegalStateException(piece.id + ": bad foundation index " + found);
                }
                foundation[column] = (short) found;
                int y = 0;
                for (int r = 0; r < runs; r++) {
                    int length = in.varint();
                    int value = in.varint();
                    if (value >= paletteSize || length <= 0 || y + length > height) {
                        throw new IllegalStateException(piece.id + ": bad run in chunk " + chunkX + ", " + chunkZ);
                    }
                    if (value != 0) {
                        java.util.Arrays.fill(voxels, column * height + y, column * height + y + length, (short) value);
                    }
                    y += length;
                }
                if (y != height) {
                    throw new IllegalStateException(piece.id + ": column " + column + " covers " + y + " of " + height);
                }
            }
            return new Tile(piece, chunkX, chunkZ, minY, height, voxels, foundation, touched);
        }
    }

    /** A tile of some piece lying in a chunk. */
    public record TileRef(Piece piece, int tile) {
        public Tile decode() {
            return piece.tile(tile);
        }
    }

    private final String build;
    private final List<Piece> pieces;
    private final List<Waystone> waystones;
    private final Map<Long, List<TileRef>> byChunk;
    /** Per chunk, 256 bits: the columns some piece writes (other than "keep"). */
    private final Map<Long, long[]> covered;

    private FieldPieceData(String build, List<Piece> pieces, List<Waystone> waystones, Map<Long, List<TileRef>> byChunk,
                           Map<Long, long[]> covered) {
        this.build = build;
        this.pieces = List.copyOf(pieces);
        this.waystones = List.copyOf(waystones);
        this.byChunk = byChunk;
        this.covered = covered;
    }

    /** An empty set (no pieces): what the generator uses when the data is missing or broken. */
    public static FieldPieceData empty() {
        return new FieldPieceData("", List.of(), List.of(), Map.of(), Map.of());
    }

    /**
     * Reads the manifest and every piece file.
     *
     * @throws IOException              if a file is missing or unreadable
     * @throws IllegalArgumentException if the data is malformed
     */
    public static FieldPieceData read(JsonObject manifest, Files files) throws IOException {
        if (manifest.get("format").getAsInt() != FORMAT) {
            throw new IllegalArgumentException("unsupported field piece format " + manifest.get("format"));
        }
        List<Piece> pieces = new ArrayList<>();
        Map<Long, List<TileRef>> byChunk = new HashMap<>();
        Map<Long, long[]> covered = new HashMap<>();
        int index = 0;
        for (JsonElement element : manifest.getAsJsonArray("pieces")) {
            JsonObject p = element.getAsJsonObject();
            String id = p.get("id").getAsString();
            List<String> palette = new ArrayList<>();
            for (JsonElement state : p.getAsJsonArray("palette")) {
                palette.add(state.getAsString());
            }
            if (palette.size() < 2 || !VOID.equals(palette.get(0)) || !AIR.equals(palette.get(1))) {
                throw new IllegalArgumentException(id + ": the palette must start with " + VOID + ", " + AIR);
            }
            List<BlockEntity> blockEntities = new ArrayList<>();
            for (JsonElement e : array(p, "blockEntities")) {
                JsonObject be = e.getAsJsonObject();
                int[] pos = ints(be.getAsJsonArray("pos"));
                blockEntities.add(new BlockEntity(pos[0], pos[1], pos[2], be.get("nbt").getAsString()));
            }
            List<int[]> fluidTicks = new ArrayList<>();
            for (JsonElement e : array(p, "fluidTicks")) {
                fluidTicks.add(ints(e.getAsJsonArray()));
            }
            List<Probe> probes = new ArrayList<>();
            for (JsonElement e : array(p, "probes")) {
                JsonObject probe = e.getAsJsonObject();
                int[] pos = ints(probe.getAsJsonArray("pos"));
                probes.add(new Probe(pos[0], pos[1], pos[2], probe.get("state").getAsString()));
            }
            List<Sample> samples = new ArrayList<>();
            for (JsonElement e : array(p, "samples")) {
                int[] s = ints(e.getAsJsonArray());
                samples.add(new Sample(s[0], s[1], s[2]));
            }
            byte[] data;
            try (InputStream raw = files.open(p.get("file").getAsString());
                 InputStream in = new GZIPInputStream(raw)) {
                data = readAll(in);
            }
            Piece piece = new Piece(index++, id, p.has("name") ? p.get("name").getAsString() : id,
                    p.has("field") ? p.get("field").getAsInt() : 0, p.has("kind") ? p.get("kind").getAsString() : "",
                    ints(p.getAsJsonArray("box")), palette, blockEntities, fluidTicks, probes, samples, data);
            scan(piece, byChunk, covered);
            pieces.add(piece);
        }
        List<Waystone> waystones = new ArrayList<>();
        for (JsonElement e : array(manifest, "waystones")) {
            JsonObject w = e.getAsJsonObject();
            int[] pos = ints(w.getAsJsonArray("pos"));
            waystones.add(new Waystone(w.get("id").getAsString(), pos[0], pos[1], pos[2], w.get("facing").getAsString()));
        }
        for (Map.Entry<Long, List<TileRef>> entry : byChunk.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        String build = manifest.has("build") ? manifest.get("build").getAsString() : "";
        return new FieldPieceData(build, pieces, waystones, Map.copyOf(byChunk), Map.copyOf(covered));
    }

    /** Walks a piece's tiles once: records their offsets, indexes them by chunk and marks the touched columns. */
    private static void scan(Piece piece, Map<Long, List<TileRef>> byChunk, Map<Long, long[]> covered) {
        Reader in = new Reader(piece.data, 0);
        for (byte b : MAGIC) {
            if (in.u8() != b) {
                throw new IllegalArgumentException(piece.id + ": not an ERP1 piece");
            }
        }
        int tiles = in.varint();
        for (int t = 0; t < tiles; t++) {
            int offset = in.pos;
            int chunkX = in.int32();
            int chunkZ = in.int32();
            in.int32();
            int height = in.varint();
            long key = chunkKey(chunkX, chunkZ);
            long[] mask = covered.computeIfAbsent(key, k -> new long[4]);
            for (int column = 0; column < 256; column++) {
                int runs = in.varint();
                if (runs == 0) {
                    continue;
                }
                in.varint();
                int y = 0;
                boolean writes = false;
                for (int r = 0; r < runs; r++) {
                    y += in.varint();
                    writes |= in.varint() != 0;
                }
                if (y != height) {
                    throw new IllegalArgumentException(piece.id + ": malformed column in chunk " + chunkX + ", " + chunkZ);
                }
                if (writes) {
                    mask[column >> 6] |= 1L << (column & 63);
                }
            }
            piece.tileOffsets.add(offset);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(new TileRef(piece, piece.tileOffsets.size() - 1));
        }
        if (in.pos != piece.data.length) {
            throw new IllegalArgumentException(piece.id + ": " + (piece.data.length - in.pos) + " trailing bytes");
        }
    }

    // ------------------------------------------------------------------------------------------------ queries

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public String build() {
        return build;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public List<Waystone> waystones() {
        return waystones;
    }

    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    /** The tiles of every piece in chunk (chunkX, chunkZ), in manifest order (later pieces win). */
    public List<TileRef> tilesAt(int chunkX, int chunkZ) {
        return byChunk.getOrDefault(chunkKey(chunkX, chunkZ), Collections.emptyList());
    }

    /** Number of chunks that contain a piece. */
    public int chunkCount() {
        return byChunk.size();
    }

    /** Whether a piece writes anything in column (x, z). */
    public boolean covers(int x, int z) {
        long[] mask = covered.get(chunkKey(x >> 4, z >> 4));
        if (mask == null) {
            return false;
        }
        int column = (x & 15) * 16 + (z & 15);
        return (mask[column >> 6] & (1L << (column & 63))) != 0;
    }

    /** Whether a piece writes anything within {@code radius} blocks (square) of column (x, z). */
    public boolean near(int x, int z, int radius) {
        int x0 = x - radius;
        int x1 = x + radius;
        int z0 = z - radius;
        int z1 = z + radius;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                long[] mask = covered.get(chunkKey(cx, cz));
                if (mask == null) {
                    continue;
                }
                int lx0 = Math.max(x0, cx << 4) - (cx << 4);
                int lx1 = Math.min(x1, (cx << 4) + 15) - (cx << 4);
                int lz0 = Math.max(z0, cz << 4) - (cz << 4);
                int lz1 = Math.min(z1, (cz << 4) + 15) - (cz << 4);
                for (int lx = lx0; lx <= lx1; lx++) {
                    for (int lz = lz0; lz <= lz1; lz++) {
                        int column = lx * 16 + lz;
                        if ((mask[column >> 6] & (1L << (column & 63))) != 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** The nearest exported waystone to (x, z) within {@code radius} blocks, or null. */
    public Waystone waystoneNear(int x, int z, int radius) {
        Waystone best = null;
        double bestD = (double) radius * radius;
        for (Waystone w : waystones) {
            double d = (double) (w.x() - x) * (w.x() - x) + (double) (w.z() - z) * (w.z() - z);
            if (d <= bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static JsonArray array(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        byte[] buffer = new byte[1 << 16];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Big-endian ints and LEB128 varints over a byte array. */
    private static final class Reader {
        private final byte[] data;
        private int pos;

        Reader(byte[] data, int pos) {
            this.data = data;
            this.pos = pos;
        }

        int u8() {
            if (pos >= data.length) {
                throw new IllegalArgumentException("unexpected end of field piece data");
            }
            return data[pos++] & 0xFF;
        }

        int int32() {
            return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8();
        }

        int varint() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = u8();
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("varint too long");
        }
    }
}
