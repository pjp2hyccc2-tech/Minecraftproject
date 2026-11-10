package com.eternalrealm.world.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.field.FieldPieceData;
import com.eternalrealm.world.field.FieldTerrain;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Stamps the field set pieces ({@link FieldPieceData}, exported by {@code tools/fields}) into アストリア while its chunks
 * generate: {@link #stampTerrain} right after the terrain fill (blocks, foundations down to the terrain, block entity
 * data, decoration entities such as minecarts, fluid ticks, heightmaps) and {@link #restampAfterDecoration} once the chunk's own decoration ran (what trees or
 * flowers grew into a piece's air and blocks is put back). Decoration features ask {@link #covers}/{@link #near} to
 * leave the pieces alone; the capital's terrain blend skips them too.
 *
 * <p>The data is read once per server (at {@code ServerAboutToStartEvent}, or lazily by the first chunk), its palettes
 * resolved against the block registry: unknown blocks (a block of another branch, a typo) are skipped with one warning,
 * never a crash. Everything here is safe to call from world generation threads.</p>
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID)
public final class FieldPieces {
    private static final Logger LOG = LogUtils.getLogger();
    public static final ResourceLocation MANIFEST = new ResourceLocation(ModInfo.MOD_ID, "fieldpieces/manifest.json");
    private static final String DUMMY = "DUMMY";

    /** The data of one server with its palettes resolved. */
    public static final class Loaded {
        final FieldPieceData data;
        final BlockState[][] palettes;
        final Map<Long, List<FieldPieceData.BlockEntity>> blockEntities;
        final Map<Long, List<int[]>> fluidTicks;
        final Map<Long, List<FieldPieceData.Entity>> entities;

        Loaded(FieldPieceData data) {
            this.data = data;
            List<FieldPieceData.Piece> pieces = data.pieces();
            this.palettes = new BlockState[pieces.size()][];
            this.blockEntities = new HashMap<>();
            this.fluidTicks = new HashMap<>();
            this.entities = new HashMap<>();
            TreeSet<String> unknown = new TreeSet<>();
            for (FieldPieceData.Piece piece : pieces) {
                List<String> palette = piece.palette();
                BlockState[] states = new BlockState[palette.size()];
                for (int i = 2; i < palette.size(); i++) {
                    try {
                        states[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), palette.get(i),
                                false).blockState();
                    } catch (Exception e) {
                        unknown.add(palette.get(i).split("\\[", 2)[0]);
                    }
                }
                states[1] = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                palettes[piece.index()] = states;
                for (FieldPieceData.BlockEntity be : piece.blockEntities()) {
                    blockEntities.computeIfAbsent(FieldPieceData.chunkKey(be.x() >> 4, be.z() >> 4),
                            k -> new ArrayList<>()).add(be);
                }
                for (FieldPieceData.Entity entity : piece.entities()) {
                    entities.computeIfAbsent(FieldPieceData.chunkKey((int) Math.floor(entity.x()) >> 4,
                            (int) Math.floor(entity.z()) >> 4), k -> new ArrayList<>()).add(entity);
                }
                for (int[] tick : piece.fluidTicks()) {
                    fluidTicks.computeIfAbsent(FieldPieceData.chunkKey(tick[0] >> 4, tick[2] >> 4),
                            k -> new ArrayList<>()).add(tick);
                }
            }
            if (!unknown.isEmpty()) {
                LOG.warn("Field set pieces: {} unknown block(s) are left out: {}", unknown.size(), unknown);
            }
        }

        public FieldPieceData data() {
            return data;
        }
    }

    private static volatile Loaded loaded;
    private static volatile MinecraftServer loadedFor;

    private FieldPieces() {
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        loaded = null;
        loadedFor = null;
    }

    /** Reads the pieces of this server's data packs (logged and treated as "no pieces" when broken). */
    public static synchronized Loaded load(MinecraftServer server) {
        if (loadedFor == server && loaded != null) {
            return loaded;
        }
        long start = System.nanoTime();
        Loaded result;
        try {
            result = new Loaded(read(server.getResourceManager()));
        } catch (IOException | RuntimeException e) {
            LOG.error("The field set pieces {} could not be read; the fields generate without them", MANIFEST, e);
            result = new Loaded(FieldPieceData.empty());
        }
        loaded = result;
        loadedFor = server;
        if (!result.data.isEmpty()) {
            LOG.info("Field set pieces {}: {} pieces in {} chunks, read in {} ms", result.data.build(),
                    result.data.pieces().size(), result.data.chunkCount(), (System.nanoTime() - start) / 1_000_000L);
        }
        return result;
    }

    static FieldPieceData read(ResourceManager resources) throws IOException {
        Optional<Resource> manifest = resources.getResource(MANIFEST);
        if (manifest.isEmpty()) {
            return FieldPieceData.empty();
        }
        try (Reader reader = new InputStreamReader(manifest.get().open(), StandardCharsets.UTF_8)) {
            return FieldPieceData.read(JsonParser.parseReader(reader).getAsJsonObject(), name -> open(resources, name));
        }
    }

    private static InputStream open(ResourceManager resources, String name) throws IOException {
        ResourceLocation location = new ResourceLocation(ModInfo.MOD_ID, "fieldpieces/" + name);
        return resources.getResource(location).orElseThrow(() -> new IOException("missing " + location)).open();
    }

    /** The pieces of the running server (read on first use), or null outside a server. */
    public static Loaded current() {
        Loaded l = loaded;
        if (l != null) {
            return l;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : load(server);
    }

    /** The piece data of the running server (empty when there is none). */
    public static FieldPieceData data() {
        Loaded l = current();
        return l == null ? FieldPieceData.empty() : l.data;
    }

    // ------------------------------------------------------------------------------------------------ queries

    /** Whether a set piece writes anything in column (x, z) (decoration and terrain blending leave it alone). */
    public static boolean covers(int x, int z) {
        Loaded l = current();
        return l != null && l.data.covers(x, z);
    }

    /** Whether a set piece writes anything within {@code radius} blocks of column (x, z). */
    public static boolean near(int x, int z, int radius) {
        Loaded l = current();
        return l != null && l.data.near(x, z, radius);
    }

    // ------------------------------------------------------------------------------------------------ stamping

    /**
     * Writes the pieces of a freshly filled chunk (called by {@link FieldChunkGenerator#fillFromNoise} after the
     * terrain): blocks, foundations where the terrain lies below a piece, block entity data (with id and position),
     * fluid ticks, and the world generation heightmaps.
     */
    public static void stampTerrain(ChunkAccess chunk) {
        Loaded l = current();
        if (l == null || l.data.isEmpty()) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        List<FieldPieceData.TileRef> refs = l.data.tilesAt(pos.x, pos.z);
        if (refs.isEmpty()) {
            return;
        }
        int minBuild = chunk.getMinBuildHeight();
        int maxBuild = chunk.getMaxBuildHeight() - 1;
        Map<Long, BlockState> withEntities = new HashMap<>();
        for (FieldPieceData.TileRef ref : refs) {
            FieldPieceData.Tile tile = ref.decode();
            BlockState[] palette = l.palettes[ref.piece().index()];
            for (int column = 0; column < 256; column++) {
                if (!tile.touched()[column]) {
                    continue;
                }
                int lx = column >> 4;
                int lz = column & 15;
                int lowest = Integer.MAX_VALUE;
                for (int dy = 0; dy < tile.height(); dy++) {
                    int index = tile.voxels()[column * tile.height() + dy];
                    if (index == 0) {
                        continue;
                    }
                    int y = tile.minY() + dy;
                    lowest = Math.min(lowest, y);
                    BlockState state = palette[index];
                    if (state == null || y < minBuild || y > maxBuild) {
                        continue;
                    }
                    set(chunk, lx, y, lz, state);
                    long key = BlockPos.asLong(pos.getMinBlockX() + lx, y, pos.getMinBlockZ() + lz);
                    if (state.hasBlockEntity() && createsBlockEntity(state)) {
                        withEntities.put(key, state);
                    } else {
                        withEntities.remove(key);
                    }
                }
                int found = tile.foundation()[column];
                if (found != 0 && palette[found] != null && lowest != Integer.MAX_VALUE) {
                    int terrain = (int) Math.floor(FieldTerrain.heightAt(pos.getMinBlockX() + lx,
                            pos.getMinBlockZ() + lz));
                    for (int y = Math.max(minBuild, terrain + 1); y < lowest && y <= maxBuild; y++) {
                        set(chunk, lx, y, lz, palette[found]);
                    }
                }
            }
        }
        blockEntities(l, chunk, withEntities);
        for (int[] tick : l.fluidTicks.getOrDefault(FieldPieceData.chunkKey(pos.x, pos.z), List.of())) {
            BlockPos at = new BlockPos(tick[0], tick[1], tick[2]);
            if (!chunk.getFluidState(at).isEmpty()) {
                chunk.markPosForPostprocessing(at);
            }
        }
        if (chunk instanceof ProtoChunk proto) {
            for (FieldPieceData.Entity entity : l.entities.getOrDefault(FieldPieceData.chunkKey(pos.x, pos.z), List.of())) {
                try {
                    CompoundTag tag = TagParser.parseTag(entity.nbt());
                    tag.putString("id", entity.type());
                    ListTag at = new ListTag();
                    at.add(DoubleTag.valueOf(entity.x()));
                    at.add(DoubleTag.valueOf(entity.y()));
                    at.add(DoubleTag.valueOf(entity.z()));
                    tag.put("Pos", at);
                    ListTag rotation = new ListTag();
                    rotation.add(FloatTag.valueOf(entity.yaw()));
                    rotation.add(FloatTag.valueOf(0.0F));
                    tag.put("Rotation", rotation);
                    proto.addEntity(tag);
                } catch (Exception e) {
                    LOG.warn("Field piece entity {} at {}, {}, {} is invalid: {}", entity.type(), entity.x(), entity.y(),
                            entity.z(), e.getMessage());
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    /**
     * Puts a piece's blocks back where the chunk's decoration (trees, flowers, snow) changed them; blocks with block
     * entities are left as placed. Called at the end of {@link FieldChunkGenerator#applyBiomeDecoration}.
     */
    public static void restampAfterDecoration(ChunkAccess chunk) {
        Loaded l = current();
        if (l == null || l.data.isEmpty()) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        List<FieldPieceData.TileRef> refs = l.data.tilesAt(pos.x, pos.z);
        if (refs.isEmpty()) {
            return;
        }
        int minBuild = chunk.getMinBuildHeight();
        int maxBuild = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (FieldPieceData.TileRef ref : refs) {
            FieldPieceData.Tile tile = ref.decode();
            BlockState[] palette = l.palettes[ref.piece().index()];
            for (int column = 0; column < 256; column++) {
                if (!tile.touched()[column]) {
                    continue;
                }
                for (int dy = 0; dy < tile.height(); dy++) {
                    int index = tile.voxels()[column * tile.height() + dy];
                    int y = tile.minY() + dy;
                    if (index == 0 || palette[index] == null || y < minBuild || y > maxBuild) {
                        continue;
                    }
                    BlockState state = palette[index];
                    at.set(pos.getMinBlockX() + (column >> 4), y, pos.getMinBlockZ() + (column & 15));
                    BlockState current = chunk.getBlockState(at);
                    if (current != state && !state.hasBlockEntity() && !current.hasBlockEntity()) {
                        chunk.setBlockState(at, state, false);
                    }
                }
            }
        }
    }

    private static void set(ChunkAccess chunk, int lx, int y, int lz, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(lx, y & 15, lz, state, false);
    }

    /** Block entity data of the pieces in this chunk; blocks with a block entity but no data get an empty one. */
    private static void blockEntities(Loaded l, ChunkAccess chunk, Map<Long, BlockState> withEntities) {
        if (withEntities.isEmpty()) {
            return;
        }
        Map<Long, String> data = new HashMap<>();
        for (FieldPieceData.BlockEntity be : l.blockEntities.getOrDefault(
                FieldPieceData.chunkKey(chunk.getPos().x, chunk.getPos().z), List.of())) {
            data.put(BlockPos.asLong(be.x(), be.y(), be.z()), be.nbt());
        }
        for (Map.Entry<Long, BlockState> entry : withEntities.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            CompoundTag tag = new CompoundTag();
            String snbt = data.get(entry.getKey());
            String id = blockEntityId(entry.getValue());
            if (snbt != null && !DUMMY.equals(id)) {
                try {
                    tag = TagParser.parseTag(snbt);
                    tag.putString("id", id);
                } catch (Exception e) {
                    LOG.warn("Field piece block entity data at {} is invalid: {}", pos, e.getMessage());
                    tag = new CompoundTag();
                    tag.putString("id", DUMMY);
                }
            } else {
                tag.putString("id", DUMMY);
            }
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            chunk.setBlockEntityNbt(tag);
        }
    }

    private static final Map<BlockState, String> ENTITY_IDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<BlockState, Boolean> CREATES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether the block really creates a block entity in this state (the upper half of a waystone does not). */
    private static boolean createsBlockEntity(BlockState state) {
        return CREATES.computeIfAbsent(state, s -> {
            try {
                return s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock
                        && entityBlock.newBlockEntity(BlockPos.ZERO, s) != null;
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    /** Registry id of the block entity type of a block state ("DUMMY" if none is valid for it). */
    private static String blockEntityId(BlockState state) {
        return ENTITY_IDS.computeIfAbsent(state, s -> {
            for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                if (type.isValid(s)) {
                    ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                    return key == null ? DUMMY : key.toString();
                }
            }
            return DUMMY;
        });
    }
}
