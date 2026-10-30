package com.nihonisshu.tabi.world;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.entity.TabiNpc;
import com.nihonisshu.tabi.registry.TabiEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Generates the "japan" dimension: hand-built areas from WorldData, procedural Japanese countryside and sea elsewhere. */
public class TabiChunkGenerator extends ChunkGenerator {
    public static final Codec<TabiChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, i.stable(TabiChunkGenerator::new)));

    private static final int MIN_Y = -64;
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SHORT_GRASS = Blocks.GRASS.defaultBlockState();

    public TabiChunkGenerator(BiomeSource source) {
        super(source);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** Writes block states straight into chunk sections, acquiring each section once. */
    private static final class Writer {
        final ChunkAccess chunk;
        final boolean[] acquired;

        Writer(ChunkAccess chunk) {
            this.chunk = chunk;
            this.acquired = new boolean[chunk.getSectionsCount()];
        }

        void set(int lx, int y, int lz, BlockState st) {
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= acquired.length) return;
            LevelChunkSection sec = chunk.getSection(si);
            if (!acquired[si]) {
                sec.acquire();
                acquired[si] = true;
            }
            sec.setBlockState(lx, y & 15, lz, st, false);
        }

        void release() {
            for (int i = 0; i < acquired.length; i++) {
                if (acquired[i]) chunk.getSection(i).release();
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        WorldData wd = WorldData.get();
        int off = wd.chunkOffset(cp.x, cp.z);
        Writer w = new Writer(chunk);
        try {
            if (off >= 0) {
                fillDesigned(w, wd, off);
            } else {
                fillProcedural(w, cp);
            }
        } catch (Exception e) {
            TabiMod.LOGGER.error("[tabi] chunk {} generation failed", cp, e);
        } finally {
            w.release();
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        if (off >= 0) {
            for (CompoundTag tag : wd.blockEntities(cp.x, cp.z)) {
                chunk.setBlockEntityNbt(tag.copy());
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private void fillDesigned(Writer w, WorldData wd, int off) {
        WorldData.Cursor c = new WorldData.Cursor(wd.data(), off);
        for (int col = 0; col < 256; col++) {
            int lz = col >> 4, lx = col & 15;
            int runs = c.varint();
            int firstY = Integer.MAX_VALUE;
            for (int r = 0; r < runs; r++) {
                int y0 = c.varint() + MIN_Y;
                int len = c.varint();
                BlockState st = wd.palette[c.varint()];
                if (y0 < firstY) firstY = y0;
                for (int y = y0; y < y0 + len; y++) w.set(lx, y, lz, st);
            }
            if (firstY == Integer.MAX_VALUE) firstY = 40;
            for (int y = MIN_Y; y < firstY; y++) w.set(lx, y, lz, y == MIN_Y ? BEDROCK : STONE);
        }
    }

    private void fillProcedural(Writer w, ChunkPos cp) {
        int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
        int[] heights = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = bx + lx, z = bz + lz;
                int h = Terrain.height(x, z);
                heights[lz * 16 + lx] = h;
                int surf = Terrain.surface(x, z, h);
                for (int y = MIN_Y; y <= h; y++) {
                    BlockState st;
                    if (y == MIN_Y) st = BEDROCK;
                    else if (y <= h - 4) st = STONE;
                    else if (surf == 0) st = (y == h) ? GRASS : DIRT;
                    else if (surf == 3) st = GRAVEL;
                    else st = SAND;
                    w.set(lx, y, lz, st);
                }
                for (int y = h + 1; y <= Terrain.WATER_TOP; y++) {
                    if (Terrain.inland(x, z) < 0) w.set(lx, y, lz, WATER);
                }
                if (surf == 0 && h > Terrain.WATER_TOP && Terrain.hash(x, z, 101) < 0.12) {
                    w.set(lx, h + 1, lz, SHORT_GRASS);
                }
            }
        }
        // a few trees, kept inside the chunk so no neighbour access is needed
        for (int t = 0; t < 6; t++) {
            int lx = 2 + (int) (Terrain.hash(cp.x * 7 + t, cp.z * 13, 211) * 12);
            int lz = 2 + (int) (Terrain.hash(cp.x * 11, cp.z * 5 + t, 223) * 12);
            int x = bx + lx, z = bz + lz;
            int h = heights[lz * 16 + lx];
            if (h <= 66 || Terrain.surface(x, z, h) != 0) continue;
            double f = Terrain.forest(x, z);
            if (f < 0.5 && Terrain.hash(x, z, 229) > 0.15) continue;
            boolean conifer = Terrain.hash(x, z, 233) < 0.55;
            placeTree(w, lx, h + 1, lz, conifer, 4 + (int) (Terrain.hash(x, z, 241) * 3));
        }
    }

    private void placeTree(Writer w, int lx, int y, int lz, boolean conifer, int trunk) {
        BlockState log = conifer ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = (conifer ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int top = y + trunk + (conifer ? 3 : 0);
        for (int dy = 0; dy < trunk + (conifer ? 3 : 0); dy++) w.set(lx, y + dy, lz, log);
        if (conifer) {
            for (int dy = 2; dy <= trunk + 4; dy++) {
                int r = Math.max(0, (trunk + 4 - dy) / 2);
                if (r > 2) r = 2;
                for (int ox = -r; ox <= r; ox++) for (int oz = -r; oz <= r; oz++) {
                    if (ox == 0 && oz == 0 && dy < trunk + 3) continue;
                    if (Math.abs(ox) + Math.abs(oz) > r + 1) continue;
                    w.set(lx + ox, y + dy, lz + oz, leaves);
                }
            }
            w.set(lx, top + 1, lz, leaves);
        } else {
            for (int dy = trunk - 2; dy <= trunk + 1; dy++) {
                int r = dy >= trunk ? 1 : 2;
                for (int ox = -r; ox <= r; ox++) for (int oz = -r; oz <= r; oz++) {
                    if (ox == 0 && oz == 0 && dy < trunk) continue;
                    if (Math.abs(ox) == 2 && Math.abs(oz) == 2) continue;
                    w.set(lx + ox, y + dy, lz + oz, leaves);
                }
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos cp = region.getCenter();
        List<JsonObject> list = WorldData.get().npcs(cp.x, cp.z);
        for (JsonObject o : list) {
            try {
                TabiNpc npc = TabiEntities.NPC.get().create(region.getLevel());
                if (npc == null) continue;
                npc.setupFromJson(o);
                region.addFreshEntity(npc);
            } catch (Exception e) {
                TabiMod.LOGGER.warn("[tabi] npc spawn failed {}", o, e);
            }
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int h = Terrain.height(x, z);
        if (Terrain.inland(x, z) < 0 && type != Heightmap.Types.OCEAN_FLOOR_WG && type != Heightmap.Types.OCEAN_FLOOR) {
            h = Terrain.WATER_TOP;
        }
        return h + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int h = Terrain.height(x, z);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y <= h) states[i] = STONE;
            else if (y <= Terrain.WATER_TOP && Terrain.inland(x, z) < 0) states[i] = WATER;
            else states[i] = Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        WorldData wd = WorldData.get();
        info.add("[tabi] designed chunk: " + (wd.chunkOffset(pos.getX() >> 4, pos.getZ() >> 4) >= 0));
    }
}
