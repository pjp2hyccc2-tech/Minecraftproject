package com.eternalrealm.world.forge.blueprint;

import com.eternalrealm.core.ModInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A voxel build exported by the capital's Python pipeline ({@code tools/capital/export.py}, docs/modules/capital.md
 * §6) into a resource folder {@code data/eternalrealm/<folder>/}: a manifest (palette, tiles, block entity data,
 * entities, fluid ticks, named spots) and run-length encoded 32x32 column tiles ({@code ERT1}: big-endian tile x/z,
 * then per column x-major a varint run count and (length, palette index) pairs from {@code minY} upwards). Palette
 * index 0 is "void" (leave the world as it is), 1 is air. All positions are local to the build's origin: the royal
 * capital ({@code capital/}, see {@code CapitalBlueprint}) and the estates ({@code estates/<kind>/<design>/}) use
 * this one format, loader and placer ({@link BlueprintPlacer}).
 */
public class Blueprint {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String VOID = "eternalrealm:void";

    public record Tile(int x, int z, ResourceLocation file) {
    }

    public record Spot(BlockPos pos, float yaw) {
    }

    public record BlockEntityData(BlockPos pos, String snbt) {
    }

    public record EntityData(String type, double x, double y, double z, float yaw, String snbt) {
    }

    private final String folder;
    private final String build;
    private final int tileSize;
    private final int minY;
    private final int maxY;
    private final List<String> palette;
    private final List<Tile> tiles;
    private final List<BlockEntityData> blockEntities;
    private final List<EntityData> entities;
    private final List<BlockPos> fluidTicks;
    private final Map<String, Spot> spots;

    private Blueprint(String folder, String build, int tileSize, int minY, int maxY, List<String> palette,
                      List<Tile> tiles, List<BlockEntityData> blockEntities, List<EntityData> entities,
                      List<BlockPos> fluidTicks, Map<String, Spot> spots) {
        this.folder = folder;
        this.build = build;
        this.tileSize = tileSize;
        this.minY = minY;
        this.maxY = maxY;
        this.palette = List.copyOf(palette);
        this.tiles = List.copyOf(tiles);
        this.blockEntities = List.copyOf(blockEntities);
        this.entities = List.copyOf(entities);
        this.fluidTicks = List.copyOf(fluidTicks);
        this.spots = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(spots));
    }

    /** A copy for subclasses that add their own conveniences (the capital). */
    protected Blueprint(Blueprint other) {
        this(other.folder, other.build, other.tileSize, other.minY, other.maxY, other.palette, other.tiles,
                other.blockEntities, other.entities, other.fluidTicks, other.spots);
    }

    /** The manifest of a blueprint folder ({@code <folder>/manifest.json} in the mod's data namespace). */
    public static ResourceLocation manifest(String folder) {
        return new ResourceLocation(ModInfo.MOD_ID, folder + "/manifest.json");
    }

    /**
     * The blueprint in {@code data/eternalrealm/<folder>/}, if the mod ships one; malformed data is logged and treated
     * as absent.
     */
    public static Optional<Blueprint> load(ResourceManager resources, String folder) {
        ResourceLocation location = manifest(folder);
        Optional<Resource> manifest = resources.getResource(location);
        if (manifest.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = new InputStreamReader(manifest.get().open(), StandardCharsets.UTF_8)) {
            return Optional.of(parse(JsonParser.parseReader(reader).getAsJsonObject(), folder));
        } catch (IOException | RuntimeException e) {
            LOG.error("The blueprint {} could not be read", location, e);
            return Optional.empty();
        }
    }

    /** Parses a manifest whose tile files are relative to {@code folder}. */
    public static Blueprint parse(JsonObject json, String folder) {
        if (json.get("format").getAsInt() != 1) {
            throw new IllegalArgumentException("unsupported blueprint format " + json.get("format"));
        }
        int tileSize = json.get("tileSize").getAsInt();
        int minY = json.get("minY").getAsInt();
        int maxY = json.get("maxY").getAsInt();
        if (tileSize <= 0 || tileSize > 256 || maxY < minY || maxY - minY > 1024) {
            throw new IllegalArgumentException("bad blueprint dimensions");
        }
        List<String> palette = new ArrayList<>();
        for (JsonElement state : json.getAsJsonArray("palette")) {
            palette.add(state.getAsString());
        }
        if (palette.size() < 2 || !VOID.equals(palette.get(0))) {
            throw new IllegalArgumentException("palette must start with " + VOID);
        }
        List<Tile> tiles = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("tiles")) {
            JsonObject tile = element.getAsJsonObject();
            tiles.add(new Tile(tile.get("x").getAsInt(), tile.get("z").getAsInt(),
                    new ResourceLocation(ModInfo.MOD_ID, folder + "/" + tile.get("file").getAsString())));
        }
        List<BlockEntityData> blockEntities = new ArrayList<>();
        for (JsonElement element : array(json, "blockEntities")) {
            JsonObject entry = element.getAsJsonObject();
            blockEntities.add(new BlockEntityData(pos(entry.getAsJsonArray("pos")), entry.get("nbt").getAsString()));
        }
        List<EntityData> entities = new ArrayList<>();
        for (JsonElement element : array(json, "entities")) {
            JsonObject entry = element.getAsJsonObject();
            JsonArray p = entry.getAsJsonArray("pos");
            entities.add(new EntityData(entry.get("type").getAsString(), p.get(0).getAsDouble(), p.get(1).getAsDouble(),
                    p.get(2).getAsDouble(), entry.has("yaw") ? entry.get("yaw").getAsFloat() : 0.0F,
                    entry.has("nbt") ? entry.get("nbt").getAsString() : "{}"));
        }
        List<BlockPos> fluidTicks = new ArrayList<>();
        for (JsonElement element : array(json, "fluidTicks")) {
            fluidTicks.add(pos(element.getAsJsonArray()));
        }
        Map<String, Spot> spots = new LinkedHashMap<>();
        if (json.has("spots")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("spots").entrySet()) {
                JsonObject spot = entry.getValue().getAsJsonObject();
                spots.put(entry.getKey(), new Spot(pos(spot.getAsJsonArray("pos")),
                        spot.has("yaw") ? spot.get("yaw").getAsFloat() : 0.0F));
            }
        }
        String build = json.has("build") ? json.get("build").getAsString() : Integer.toHexString(json.toString().hashCode());
        return new Blueprint(folder, build, tileSize, minY, maxY, palette, tiles, blockEntities, entities, fluidTicks,
                spots);
    }

    private static JsonArray array(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
    }

    private static BlockPos pos(JsonArray array) {
        return new BlockPos((int) Math.floor(array.get(0).getAsDouble()), (int) Math.floor(array.get(1).getAsDouble()),
                (int) Math.floor(array.get(2).getAsDouble()));
    }

    /**
     * Block states of the palette; {@code null} for void and for entries that do not parse (reported in
     * {@code problems}: they are left out rather than breaking the build).
     */
    public BlockState[] resolvePalette(HolderLookup<Block> blocks, List<String> problems) {
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 1; i < palette.size(); i++) {
            try {
                states[i] = BlockStateParser.parseForBlock(blocks, palette.get(i), false).blockState();
            } catch (Exception e) {
                problems.add(palette.get(i) + ": " + e.getMessage());
            }
        }
        return states;
    }

    /**
     * Palette indices of a tile, {@code [((x * size) + z) * height + (y - minY)]}.
     *
     * @throws IOException if the file is missing or malformed
     */
    public short[] decode(ResourceManager resources, Tile tile) throws IOException {
        Resource resource = resources.getResource(tile.file()).orElseThrow(() -> new IOException("missing " + tile.file()));
        int height = height();
        short[] voxels = new short[tileSize * tileSize * height];
        try (InputStream raw = resource.open(); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'E' || magic[1] != 'R' || magic[2] != 'T' || magic[3] != '1') {
                throw new IOException(tile.file() + ": not an ERT1 tile");
            }
            if (in.readInt() != tile.x() || in.readInt() != tile.z()) {
                throw new IOException(tile.file() + ": tile position does not match the manifest");
            }
            for (int column = 0; column < tileSize * tileSize; column++) {
                int runs = varint(in);
                int y = 0;
                for (int r = 0; r < runs; r++) {
                    int length = varint(in);
                    int index = varint(in);
                    if (index >= palette.size() || length < 0 || y + length > height) {
                        throw new IOException(tile.file() + ": bad run in column " + column);
                    }
                    if (index != 0) {
                        java.util.Arrays.fill(voxels, column * height + y, column * height + y + length, (short) index);
                    }
                    y += length;
                }
                if (y != height) {
                    throw new IOException(tile.file() + ": column " + column + " covers " + y + " of " + height);
                }
            }
        }
        return voxels;
    }

    private static int varint(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("varint too long");
    }

    /** The resource folder ({@code capital}, {@code estates/room/attic}). */
    public String folder() {
        return folder;
    }

    /** Content hash of the export: a new version of the build has another one. */
    public String build() {
        return build;
    }

    public int tileSize() {
        return tileSize;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public List<String> palette() {
        return palette;
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public List<BlockEntityData> blockEntities() {
        return blockEntities;
    }

    public List<EntityData> entities() {
        return entities;
    }

    public List<BlockPos> fluidTicks() {
        return fluidTicks;
    }

    public Map<String, Spot> spots() {
        return spots;
    }

    public Optional<Spot> spot(String name) {
        return Optional.ofNullable(spots.get(name));
    }

    /** Local x/z bounds of the tiles: {minX, minZ, maxX, maxZ} (inclusive); all zero without tiles. */
    public int[] tileBounds() {
        if (tiles.isEmpty()) {
            return new int[4];
        }
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (Tile tile : tiles) {
            x0 = Math.min(x0, tile.x() * tileSize);
            z0 = Math.min(z0, tile.z() * tileSize);
            x1 = Math.max(x1, tile.x() * tileSize + tileSize - 1);
            z1 = Math.max(z1, tile.z() * tileSize + tileSize - 1);
        }
        return new int[]{x0, z0, x1, z1};
    }
}
