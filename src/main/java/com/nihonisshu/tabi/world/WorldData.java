package com.nihonisshu.tabi.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.data.TabiData;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Hand-built areas exported by tools/build_world.py.
 * Binary layout (big endian, gzip): "TABI", version, palette(UTF strings), minCX, minCZ, wC, hC,
 * offsets[wC*hC] (int, -1 = not designed), dataLength, data.
 * Chunk data: 256 columns (index z*16+x), each: varint runCount, runs of (varint y+64, varint length, varint palette).
 * Below the first run the column is filled with stone (bedrock at the very bottom).
 */
public final class WorldData {
    private static WorldData instance;

    public final BlockState[] palette;
    public final int minCX, minCZ, wC, hC;
    private final int[] offsets;
    private final byte[] data;
    private final Map<Long, List<CompoundTag>> blockEntities = new HashMap<>();
    private final Map<Long, List<JsonObject>> npcs = new HashMap<>();

    private WorldData(BlockState[] palette, int minCX, int minCZ, int wC, int hC, int[] offsets, byte[] data) {
        this.palette = palette;
        this.minCX = minCX;
        this.minCZ = minCZ;
        this.wC = wC;
        this.hC = hC;
        this.offsets = offsets;
        this.data = data;
    }

    public static synchronized WorldData get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static WorldData load() {
        long t0 = System.currentTimeMillis();
        try (InputStream raw = TabiMod.class.getResourceAsStream("/data/tabi/tabi_world/japan.bin")) {
            if (raw == null) throw new IOException("japan.bin missing");
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'T' || magic[1] != 'A' || magic[2] != 'B' || magic[3] != 'I') throw new IOException("bad magic");
            int version = in.readInt();
            int n = in.readInt();
            BlockState[] palette = new BlockState[n];
            for (int i = 0; i < n; i++) {
                String s = in.readUTF();
                palette[i] = parse(s);
            }
            int minCX = in.readInt(), minCZ = in.readInt(), wC = in.readInt(), hC = in.readInt();
            int[] offsets = new int[wC * hC];
            for (int i = 0; i < offsets.length; i++) offsets[i] = in.readInt();
            int len = in.readInt();
            byte[] data = new byte[len];
            in.readFully(data);
            WorldData wd = new WorldData(palette, minCX, minCZ, wC, hC, offsets, data);
            wd.loadMeta();
            TabiMod.LOGGER.info("[tabi] world data v{} loaded: {} palette, {}x{} chunks, {} KB, {} ms", version, n, wC, hC, len / 1024, System.currentTimeMillis() - t0);
            return wd;
        } catch (Exception e) {
            TabiMod.LOGGER.error("[tabi] failed to load world data", e);
            return new WorldData(new BlockState[]{Blocks.STONE.defaultBlockState()}, 0, 0, 0, 0, new int[0], new byte[0]);
        }
    }

    private static BlockState parse(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
        } catch (CommandSyntaxException e) {
            TabiMod.LOGGER.warn("[tabi] unknown block state '{}', using stone", s);
            return Blocks.STONE.defaultBlockState();
        }
    }

    private void loadMeta() {
        JsonObject meta = TabiData.meta();
        if (meta == null) return;
        JsonArray signs = meta.getAsJsonArray("signs");
        if (signs != null) {
            for (JsonElement e : signs) {
                JsonObject o = e.getAsJsonObject();
                int x = o.get("x").getAsInt(), y = o.get("y").getAsInt(), z = o.get("z").getAsInt();
                CompoundTag tag = new CompoundTag();
                tag.putString("id", "tabi:text_panel");
                tag.putInt("x", x);
                tag.putInt("y", y);
                tag.putInt("z", z);
                ListTag lines = new ListTag();
                for (JsonElement l : o.getAsJsonArray("lines")) lines.add(StringTag.valueOf(l.getAsString()));
                tag.put("lines", lines);
                tag.putString("style", o.has("style") ? o.get("style").getAsString() : "white");
                tag.putInt("span", o.has("span") ? o.get("span").getAsInt() : 1);
                tag.putInt("color", o.has("color") ? o.get("color").getAsInt() : -1);
                tag.putBoolean("glow", o.has("glow") && o.get("glow").getAsBoolean());
                tag.putBoolean("back", o.has("back") && o.get("back").getAsBoolean());
                blockEntities.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), k -> new ArrayList<>()).add(tag);
            }
        }
        JsonArray list = meta.getAsJsonArray("npcs");
        if (list != null) {
            for (JsonElement e : list) {
                JsonObject o = e.getAsJsonObject();
                int x = (int) Math.floor(o.get("x").getAsDouble()), z = (int) Math.floor(o.get("z").getAsDouble());
                npcs.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), k -> new ArrayList<>()).add(o);
            }
        }
    }

    public List<CompoundTag> blockEntities(int cx, int cz) {
        return blockEntities.getOrDefault(ChunkPos.asLong(cx, cz), List.of());
    }

    public List<JsonObject> npcs(int cx, int cz) {
        return npcs.getOrDefault(ChunkPos.asLong(cx, cz), List.of());
    }

    /** returns byte offset of the chunk's data, or -1 if the chunk is procedural. */
    public int chunkOffset(int cx, int cz) {
        int i = cx - minCX, j = cz - minCZ;
        if (i < 0 || j < 0 || i >= wC || j >= hC) return -1;
        return offsets[j * wC + i];
    }

    public byte[] data() {
        return data;
    }

    /** simple varint reader over the data array */
    public static final class Cursor {
        private final byte[] d;
        public int pos;

        public Cursor(byte[] d, int pos) {
            this.d = d;
            this.pos = pos;
        }

        public int varint() {
            int v = 0, shift = 0;
            while (true) {
                int b = d[pos++] & 0xFF;
                v |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return v;
                shift += 7;
            }
        }
    }
}
