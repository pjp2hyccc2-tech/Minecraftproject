package com.eternalrealm.client.map;

import com.eternalrealm.world.map.MapMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Map colours of the chunks the client has loaded, one ARGB value per column (0 = nothing to show), computed a few
 * chunks per tick around the player and cached (least recently used chunks are dropped).
 *
 * <ul>
 *     <li>Surface mode: the top block's map colour (grass, leaves and water tinted by their biome), shaded by the
 *         height step to the column north of it like vanilla maps; water darker with depth.</li>
 *     <li>Cave mode (the dungeon dimension, dimensions with a ceiling, deep underground): the floor below the player's
 *         feet, walls dark and the void transparent - which draws dungeon rooms and corridors by themselves.</li>
 * </ul>
 * Client thread only.
 */
final class ChunkColors {
    /** Chunks around the player that are kept computed (the minimap texture spans 16 chunks). */
    static final int RADIUS = 9;
    private static final int MAX_ENTRIES = 1600;
    /** Chunks right around the player are recomputed this often, so changes show up. */
    private static final long NEAR_REFRESH_TICKS = 40;
    private static final int CAVE_SCAN_DEPTH = 16;
    private static final int[][] SPIRAL = spiral();

    private record Cached(int[] colors, long computedAt, boolean cave, int refY) {
    }

    private final Map<Long, Cached> cache = new LinkedHashMap<>(512, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cached> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private ResourceKey<Level> dimension;
    private ClientLevel level;
    private boolean cave;
    private int refY;
    /** Chunks whose colours changed since the minimap last asked (it repaints just those). */
    private final Set<Long> changed = new HashSet<>();
    /** Every colour may have changed (another world or dimension, cave mode switched): repaint everything. */
    private boolean allChanged = true;

    /** Whether everything has to be repainted since the last call (and forgets it). */
    boolean takeAllChanged() {
        boolean all = allChanged;
        allChanged = false;
        if (all) {
            changed.clear();
        }
        return all;
    }

    /** Hands the chunks whose colours changed since the last call to {@code sink} (and forgets them). */
    void drainChanged(java.util.function.LongConsumer sink) {
        for (long key : changed) {
            sink.accept(key);
        }
        changed.clear();
    }

    boolean caveMode() {
        return cave;
    }

    void clear() {
        cache.clear();
        dimension = null;
        level = null;
        allChanged = true;
    }

    /** Computes up to {@code budget} missing or outdated chunks, nearest first. */
    void tick(ClientLevel clientLevel, LocalPlayer player, long gameTime, int budget) {
        if (clientLevel != level || clientLevel.dimension() != dimension) {
            cache.clear();
            level = clientLevel;
            dimension = clientLevel.dimension();
            allChanged = true;
        }
        boolean caveNow = caveMode(clientLevel, player);
        int feet = player.getBlockY();
        if (caveNow != cave || (caveNow && Math.abs(feet - refY) >= 2)) {
            allChanged |= caveNow != cave; // a chunk of the other mode is not shown: everything changes at once
            cave = caveNow;
            refY = feet;
        }
        int playerChunkX = player.getBlockX() >> 4;
        int playerChunkZ = player.getBlockZ() >> 4;
        int done = 0;
        for (int[] offset : SPIRAL) {
            int chunkX = playerChunkX + offset[0];
            int chunkZ = playerChunkZ + offset[1];
            long key = ChunkPos.asLong(chunkX, chunkZ);
            Cached entry = cache.get(key);
            boolean near = Math.abs(offset[0]) <= 1 && Math.abs(offset[1]) <= 1;
            boolean stale = entry == null || entry.cave() != cave || (cave && entry.refY() != refY)
                    || (near && gameTime - entry.computedAt() > NEAR_REFRESH_TICKS);
            if (!stale) {
                continue;
            }
            LevelChunk chunk = clientLevel.getChunkSource().getChunk(chunkX, chunkZ, false);
            if (chunk == null) {
                continue;
            }
            int[] colors = cave ? caveColors(clientLevel, chunk, refY) : surfaceColors(clientLevel, chunk);
            cache.put(key, new Cached(colors, gameTime, cave, refY));
            if (entry == null || entry.cave() != cave || !Arrays.equals(entry.colors(), colors)) {
                changed.add(key); // a refresh that finds the chunk as it was paints nothing
            }
            if (++done >= budget) {
                break;
            }
        }
    }

    /**
     * Colours of a chunk in the current mode (row-major, z then x), or null if not computed. In cave mode a chunk
     * computed for another height is still shown until it has been recomputed (no flicker on slopes).
     */
    int[] get(int chunkX, int chunkZ) {
        Cached entry = cache.get(ChunkPos.asLong(chunkX, chunkZ));
        return entry == null || entry.cave() != cave ? null : entry.colors();
    }

    /** Dungeons, dimensions with a ceiling and deep caves show the floor at the player's height. */
    private boolean caveMode(ClientLevel clientLevel, LocalPlayer player) {
        if (clientLevel.dimensionType().hasCeiling() || ClientMapState.inDungeonDimension(clientLevel)) {
            return true;
        }
        BlockPos feet = player.blockPosition();
        int surface = clientLevel.getHeight(Heightmap.Types.WORLD_SURFACE, feet.getX(), feet.getZ());
        if (cave) {
            return feet.getY() < surface - 6 && !clientLevel.canSeeSky(feet);
        }
        return feet.getY() < surface - 12 && !clientLevel.canSeeSky(feet);
    }

    private static int[] surfaceColors(ClientLevel level, LevelChunk chunk) {
        int[] colors = new int[256];
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int[] northRow = new int[16];
        LevelChunk north = level.getChunkSource().getChunk(chunk.getPos().x, chunk.getPos().z - 1, false);
        for (int x = 0; x < 16; x++) {
            northRow[x] = north == null ? Integer.MIN_VALUE : north.getHeight(Heightmap.Types.WORLD_SURFACE, x, 15);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] previousRow = northRow;
        int[] row = new int[16];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                pos.set(baseX + x, y, baseZ + z);
                BlockState state = chunk.getBlockState(pos);
                MapColor mapColor = state.getMapColor(level, pos);
                for (int guard = 0; mapColor == MapColor.NONE && y > minY && guard < 32; guard++) {
                    y--;
                    pos.setY(y);
                    state = chunk.getBlockState(pos);
                    mapColor = state.getMapColor(level, pos);
                }
                if (mapColor == MapColor.NONE) {
                    row[x] = y;
                    colors[z * 16 + x] = 0;
                    continue;
                }
                int depth = 0;
                int rgb = mapColor.col;
                if (state.getFluidState().is(FluidTags.WATER)) {
                    int tint = tint(state, level, pos);
                    rgb = tint == -1 ? rgb : MapMath.mix(tint, MapColor.WATER.col, 0.35);
                    BlockPos.MutableBlockPos below = pos.mutable();
                    while (depth < 10) {
                        depth++;
                        below.move(0, -1, 0);
                        if (below.getY() < minY || !chunk.getBlockState(below).getFluidState().is(FluidTags.WATER)) {
                            break;
                        }
                    }
                } else {
                    int tint = tint(state, level, pos);
                    if (tint != -1) {
                        rgb = MapMath.mix(MapMath.shade(tint, 0.85), rgb, 0.3);
                    }
                }
                row[x] = y;
                int northHeight = previousRow[x] == Integer.MIN_VALUE ? y : previousRow[x];
                colors[z * 16 + x] = MapMath.opaque(MapMath.column(rgb, y, northHeight, depth));
            }
            previousRow = row;
            row = new int[16];
        }
        return colors;
    }

    private static int tint(BlockState state, ClientLevel level, BlockPos pos) {
        try {
            int color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            return color == -1 ? -1 : color & 0xFFFFFF;
        } catch (RuntimeException e) {
            return -1; // a broken colour provider of another mod must not break the map
        }
    }

    private static int[] caveColors(ClientLevel level, LevelChunk chunk, int refY) {
        int[] colors = new int[256];
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int color = 0;
                pos.set(baseX + x, refY + 1, baseZ + z);
                BlockState head = chunk.getBlockState(pos);
                pos.setY(refY);
                BlockState feet = chunk.getBlockState(pos);
                if (solid(level, head, pos.above()) || solid(level, feet, pos)) {
                    BlockState wall = solid(level, feet, pos) ? feet : head;
                    MapColor mapColor = wall.getMapColor(level, pos);
                    int rgb = mapColor == MapColor.NONE ? 0x707070 : mapColor.col;
                    color = MapMath.opaque(MapMath.shade(rgb, 0.45));
                } else {
                    for (int depth = 1; depth <= CAVE_SCAN_DEPTH; depth++) {
                        pos.setY(refY - depth);
                        BlockState state = chunk.getBlockState(pos);
                        MapColor mapColor = state.getMapColor(level, pos);
                        if (mapColor != MapColor.NONE) {
                            color = MapMath.opaque(MapMath.shade(mapColor.col, Math.max(0.35, 1.0 - 0.07 * (depth - 1))));
                            break;
                        }
                    }
                }
                colors[z * 16 + x] = color;
            }
        }
        return colors;
    }

    private static boolean solid(ClientLevel level, BlockState state, BlockPos pos) {
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /** Chunk offsets within {@link #RADIUS}, nearest first. */
    private static int[][] spiral() {
        List<int[]> offsets = new ArrayList<>();
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
        return offsets.toArray(new int[0][]);
    }
}
