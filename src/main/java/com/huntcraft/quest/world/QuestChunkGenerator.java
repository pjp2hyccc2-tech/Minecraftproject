package com.huntcraft.quest.world;

import com.huntcraft.quest.QuestRegions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Chunk generator of the quest maps: every column is filled from the designed {@link QuestMapData} tile, which repeats
 * every {@link QuestRegions#SPACING} blocks (one identical copy of the map per running quest). Landmarks, the base camp
 * and gathering points are stamped as voxels; vegetation comes from the biome features afterwards.
 */
public class QuestChunkGenerator extends ChunkGenerator {
    public static final Codec<QuestChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            Codec.STRING.fieldOf("map").forGetter(g -> g.mapId)
    ).apply(i, QuestChunkGenerator::new));

    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final int HEIGHT = 320;

    private final String mapId;
    private volatile QuestMapData data;

    public QuestChunkGenerator(BiomeSource biomeSource, String mapId) {
        super(biomeSource);
        this.mapId = mapId;
    }

    public String mapId() {
        return mapId;
    }

    public QuestMapData data() {
        QuestMapData d = data;
        if (d == null) {
            d = QuestMapData.get(mapId);
            data = d;
        }
        return d;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ terrain
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        fill(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    private void fill(ChunkAccess chunk) {
        QuestMapData d = data();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                int u = QuestRegions.tileU(x), v = QuestRegions.tileV(z);
                chunk.setBlockState(p.set(x, minY, z), BEDROCK, false);
                if (u < 0 || v < 0) {
                    fillOutside(chunk, p, x, z, d, minY, maxY);
                    continue;
                }
                int i = QuestMapData.idx(u, v);
                int h = Math.min(maxY, d.height[i] & 255);
                int cl = d.clo[i] & 255, ch = d.chi[i] & 255;
                BlockState top = d.spal[(d.top[i] & 255) % d.spal.length];
                BlockState sub = d.spal[(d.sub[i] & 255) % d.spal.length];
                BlockState rock = d.spal[(d.rock[i] & 255) % d.spal.length];
                BlockState ctop = d.spal[(d.ctop[i] & 255) % d.spal.length];
                int subFrom = h - d.subDepth;
                for (int y = minY + 1; y <= h; y++) {
                    if (cl > 0 && y >= cl && y <= ch) continue;
                    BlockState s;
                    if (cl > 0 && y == cl - 1) s = ctop;
                    else if (y == h) s = top;
                    else if (y >= subFrom) s = sub;
                    else s = rock;
                    chunk.setBlockState(p.set(x, y, z), s, false);
                }
                int f = d.fluid[i] & 255;
                if (f != QuestMapData.FLUID_NONE) {
                    int lvl = Math.min(maxY, d.level[i] & 255);
                    BlockState fs = f == QuestMapData.FLUID_LAVA ? LAVA : WATER;
                    for (int y = h + 1; y <= lvl; y++) {
                        chunk.setBlockState(p.set(x, y, z), f == QuestMapData.FLUID_FROZEN && y == lvl ? ICE : fs, false);
                    }
                }
            }
        }
        // voxels of this tile chunk (chunks are aligned with the tile because 256 and 2048 are multiples of 16)
        int tu = QuestRegions.tileU(x0), tv = QuestRegions.tileV(z0);
        if (tu < 0 || tv < 0) return;
        int ci = (tv >> 4) * 32 + (tu >> 4);
        int[] pos = d.chunkVoxPos[ci];
        int[] st = d.chunkVoxState[ci];
        for (int k = 0; k < pos.length; k++) {
            int pk = pos[k];
            int y = pk & 511;
            if (y <= minY || y > maxY) continue;
            int lx = ((pk >>> 18) & 511) & 15, lz = ((pk >>> 9) & 511) & 15;
            chunk.setBlockState(p.set(x0 + lx, y, z0 + lz), d.vpal[st[k]], false);
        }
    }

    private static void fillOutside(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int z, QuestMapData d, int minY, int maxY) {
        QuestMapData.Outside o = d.outside;
        int floor = Math.min(maxY, Math.max(minY + 1, o.floor()));
        BlockState top = d.spal[o.top() % d.spal.length];
        BlockState rock = d.spal[o.rock() % d.spal.length];
        for (int y = minY + 1; y <= floor; y++) chunk.setBlockState(p.set(x, y, z), y == floor ? top : rock, false);
        if (o.fluid() != QuestMapData.FLUID_NONE) {
            BlockState fs = o.fluid() == QuestMapData.FLUID_LAVA ? LAVA : WATER;
            for (int y = floor + 1; y <= Math.min(maxY, o.level()); y++) chunk.setBlockState(p.set(x, y, z), fs, false);
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        // surfaces are part of the designed data
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk,
                             GenerationStep.Carving step) {
        // caves are part of the designed data
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // quest monsters are spawned by the quest manager
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return data().sea;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    // ------------------------------------------------------------------ height queries
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        QuestMapData d = data();
        boolean withFluid = type != Heightmap.Types.OCEAN_FLOOR && type != Heightmap.Types.OCEAN_FLOOR_WG;
        int u = QuestRegions.tileU(x), v = QuestRegions.tileV(z);
        if (u < 0 || v < 0) {
            int top = d.outside.floor();
            if (withFluid && d.outside.fluid() != QuestMapData.FLUID_NONE) top = Math.max(top, d.outside.level());
            return top + 1;
        }
        int i = QuestMapData.idx(u, v);
        int top = d.height[i] & 255;
        if (withFluid && d.fluid[i] != 0) top = Math.max(top, d.level[i] & 255);
        return top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        QuestMapData d = data();
        int minY = level.getMinBuildHeight();
        BlockState[] col = new BlockState[level.getHeight()];
        java.util.Arrays.fill(col, Blocks.AIR.defaultBlockState());
        int u = QuestRegions.tileU(x), v = QuestRegions.tileV(z);
        int h, lvl = 0, f = 0;
        BlockState top, rock;
        if (u < 0 || v < 0) {
            h = d.outside.floor();
            top = d.spal[d.outside.top() % d.spal.length];
            rock = d.spal[d.outside.rock() % d.spal.length];
            f = d.outside.fluid();
            lvl = d.outside.level();
        } else {
            int i = QuestMapData.idx(u, v);
            h = d.height[i] & 255;
            top = d.spal[(d.top[i] & 255) % d.spal.length];
            rock = d.spal[(d.rock[i] & 255) % d.spal.length];
            f = d.fluid[i] & 255;
            lvl = d.level[i] & 255;
        }
        for (int y = minY; y <= h && y - minY < col.length; y++) col[y - minY] = y == minY ? BEDROCK : y == h ? top : rock;
        if (f != 0) {
            for (int y = h + 1; y <= lvl && y - minY < col.length; y++) col[y - minY] = f == QuestMapData.FLUID_LAVA ? LAVA : WATER;
        }
        return new NoiseColumn(minY, col);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        QuestMapData d = data();
        int u = QuestRegions.tileU(pos.getX()), v = QuestRegions.tileV(pos.getZ());
        info.add("HuntCraft quest map: " + mapId + (d.fallback ? " (FALLBACK)" : "") + "  tile " + u + "," + v
                + "  area " + (u >= 0 && v >= 0 ? d.zone(u, v) : 0));
    }
}
