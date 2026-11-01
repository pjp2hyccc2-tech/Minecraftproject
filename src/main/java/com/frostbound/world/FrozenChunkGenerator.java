package com.frostbound.world;

import com.frostbound.FrostboundMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Terrain of the frozen wastes, written column by column from {@link Terrain}: rock with coal and iron
 * seams, snow and ice surfaces, frozen lakes, and small buried ruins and rusted industrial frames.
 */
public class FrozenChunkGenerator extends ChunkGenerator {
    public static final Codec<FrozenChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, i.stable(FrozenChunkGenerator::new)));

    private static final int MIN_Y = -64;
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState COAL_ORE = Blocks.COAL_ORE.defaultBlockState();
    private static final BlockState DEEP_COAL = Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
    private static final BlockState IRON_ORE = Blocks.IRON_ORE.defaultBlockState();
    private static final BlockState DEEP_IRON = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();

    public FrozenChunkGenerator(BiomeSource source) {
        super(source);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private static final class Writer {
        final ChunkAccess chunk;
        final boolean[] acquired;

        Writer(ChunkAccess chunk) {
            this.chunk = chunk;
            this.acquired = new boolean[chunk.getSectionsCount()];
        }

        void set(int lx, int y, int lz, BlockState st) {
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) {
                return;
            }
            int si = chunk.getSectionIndex(y);
            if (si < 0 || si >= acquired.length) {
                return;
            }
            LevelChunkSection sec = chunk.getSection(si);
            if (!acquired[si]) {
                sec.acquire();
                acquired[si] = true;
            }
            sec.setBlockState(lx, y & 15, lz, st, false);
        }

        void release() {
            for (int i = 0; i < acquired.length; i++) {
                if (acquired[i]) {
                    chunk.getSection(i).release();
                }
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
            StructureManager structures, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        Writer w = new Writer(chunk);
        int[] heights = new int[256];
        try {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int x = cp.getMinBlockX() + lx;
                    int z = cp.getMinBlockZ() + lz;
                    heights[lx * 16 + lz] = fillColumn(w, lx, lz, x, z);
                }
            }
            decorate(w, cp, heights);
        } catch (Exception e) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] chunk {} generation failed", cp, e);
        } finally {
            w.release();
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    private static BlockState rock(int x, int y, int z, boolean ironRich) {
        double r = Terrain.rand(x * 3 + y, z * 7 - y, 0x0E5);
        if (y >= 5 && y <= 90 && r < 0.012) {
            return y < 0 ? DEEP_COAL : COAL_ORE;
        }
        if (y >= -40 && y <= 60 && r > 1.0 - (ironRich ? 0.012 : 0.006)) {
            return y < 0 ? DEEP_IRON : IRON_ORE;
        }
        return y < 0 ? DEEPSLATE : STONE;
    }

    /** Writes one column and returns its surface height. */
    private static int fillColumn(Writer w, int lx, int lz, int x, int z) {
        int h = Terrain.height(x, z);
        Terrain.Zone zone = Terrain.zone(x, z);
        boolean ironRich = zone == Terrain.Zone.INDUSTRIAL || zone == Terrain.Zone.FROST_PEAKS;
        int bedrockTop = MIN_Y + 1 + (int) (Terrain.rand(x, z, 5) * 3);
        for (int y = MIN_Y; y <= h; y++) {
            BlockState st;
            if (y <= bedrockTop) {
                st = BEDROCK;
            } else {
                st = rock(x, y, z, ironRich);
            }
            w.set(lx, y, lz, st);
        }
        switch (zone) {
            case BASIN, ICE_FIELD, BURIED_RUINS, INDUSTRIAL -> {
                w.set(lx, h, lz, SNOW_BLOCK);
                w.set(lx, h - 1, lz, SNOW_BLOCK);
                if (zone == Terrain.Zone.ICE_FIELD) {
                    w.set(lx, h - 2, lz, PACKED_ICE);
                }
            }
            case FROST_PEAKS -> {
                if (h > 118 || Terrain.rand(x, z, 11) < 0.6) {
                    w.set(lx, h, lz, SNOW_BLOCK);
                }
            }
            case GLACIER -> {
                for (int d = 0; d < 4; d++) {
                    w.set(lx, h - d, lz, Terrain.rand(x, z + d, 13) < 0.12 ? BLUE_ICE : PACKED_ICE);
                }
            }
            case FROZEN_LAKE -> {
                w.set(lx, h, lz, GRAVEL);
                for (int y = h + 1; y < Terrain.SEA; y++) {
                    w.set(lx, y, lz, WATER);
                }
                if (h < Terrain.SEA) {
                    w.set(lx, Terrain.SEA, lz, ICE);
                    return Terrain.SEA;
                }
            }
        }
        if (zone != Terrain.Zone.GLACIER) {
            double p = zone == Terrain.Zone.BASIN ? 0.12 : 0.35;
            double r = Terrain.rand(x, z, 17);
            if (r < p) {
                int layers = r < p * 0.3 ? 2 : 1;
                w.set(lx, h + 1, lz, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
            }
        }
        return h;
    }

    private static void decorate(Writer w, ChunkPos cp, int[] heights) {
        int cx = cp.getMinBlockX() + 8;
        int cz = cp.getMinBlockZ() + 8;
        Terrain.Zone zone = Terrain.zone(cx, cz);
        double roll = Terrain.rand(cp.x, cp.z, 77);
        if (zone == Terrain.Zone.BURIED_RUINS && roll < 0.22) {
            ruin(w, cp, heights);
        } else if (zone == Terrain.Zone.INDUSTRIAL && roll < 0.20) {
            industrial(w, cp, heights);
        }
    }

    private static void ruin(Writer w, ChunkPos cp, int[] heights) {
        int sx = 6 + (int) (Terrain.rand(cp.x, cp.z, 81) * 5);
        int sz = 6 + (int) (Terrain.rand(cp.x, cp.z, 82) * 5);
        int ox = 2 + (int) (Terrain.rand(cp.x, cp.z, 83) * (14 - sx));
        int oz = 2 + (int) (Terrain.rand(cp.x, cp.z, 84) * (14 - sz));
        int base = heights[(ox + sx / 2) * 16 + oz + sz / 2] - 1;
        BlockState[] walls = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState()};
        for (int lx = ox; lx < ox + sx; lx++) {
            for (int lz = oz; lz < oz + sz; lz++) {
                boolean edge = lx == ox || lz == oz || lx == ox + sx - 1 || lz == oz + sz - 1;
                int wx = cp.getMinBlockX() + lx;
                int wz = cp.getMinBlockZ() + lz;
                if (edge) {
                    int top = 1 + (int) (Terrain.rand(wx, wz, 85) * 4);
                    for (int dy = 0; dy <= top; dy++) {
                        if (Terrain.rand(wx, wz + dy, 86) < 0.18) {
                            continue;
                        }
                        w.set(lx, base + dy, lz, walls[(int) (Terrain.rand(wx + dy, wz, 87) * walls.length)]);
                    }
                } else {
                    w.set(lx, base, lz, Terrain.rand(wx, wz, 88) < 0.7 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : SNOW_BLOCK);
                    for (int dy = 1; dy <= 2; dy++) {
                        w.set(lx, base + dy, lz, Blocks.AIR.defaultBlockState());
                    }
                    if (Terrain.rand(wx, wz, 89) < 0.25) {
                        w.set(lx, base + 1, lz, SNOW_BLOCK);
                    }
                }
            }
        }
    }

    private static void industrial(Writer w, ChunkPos cp, int[] heights) {
        int ox = 3 + (int) (Terrain.rand(cp.x, cp.z, 91) * 4);
        int oz = 3 + (int) (Terrain.rand(cp.x, cp.z, 92) * 4);
        int size = 6;
        int base = heights[(ox + 3) * 16 + oz + 3];
        int tall = 6 + (int) (Terrain.rand(cp.x, cp.z, 93) * 5);
        BlockState pillar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState beam = Blocks.OXIDIZED_CUT_COPPER.defaultBlockState();
        int[][] corners = {{ox, oz}, {ox + size, oz}, {ox, oz + size}, {ox + size, oz + size}};
        for (int[] c : corners) {
            for (int dy = 1; dy <= tall; dy++) {
                w.set(c[0], base + dy, c[1], pillar);
            }
        }
        for (int d = 0; d <= size; d++) {
            if (Terrain.rand(cp.x + d, cp.z, 94) < 0.8) {
                w.set(ox + d, base + tall, oz, beam);
                w.set(ox + d, base + tall, oz + size, beam);
            }
            if (Terrain.rand(cp.x, cp.z + d, 95) < 0.8) {
                w.set(ox, base + tall, oz + d, beam);
                w.set(ox + size, base + tall, oz + d, beam);
            }
        }
        BlockState tank = Blocks.WEATHERED_COPPER.defaultBlockState();
        for (int dx = 2; dx <= 4; dx++) {
            for (int dz = 2; dz <= 4; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    w.set(ox + dx, base + dy, oz + dz, tank);
                }
            }
        }
        for (int dy = 1; dy < tall; dy++) {
            w.set(ox + 1, base + tall - dy + 1, oz + size / 2, dy % 2 == 0 ? Blocks.IRON_BARS.defaultBlockState()
                    : Blocks.CHAIN.defaultBlockState());
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return Terrain.SEA;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int h = Terrain.height(x, z);
        if (Terrain.isLake(x, z) && h < Terrain.SEA) {
            return Terrain.SEA + 1;
        }
        return h + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int h = Terrain.height(x, z);
        int top = Math.max(h, Terrain.isLake(x, z) ? Terrain.SEA : h);
        BlockState[] states = new BlockState[top - MIN_Y + 1];
        for (int y = MIN_Y; y <= top; y++) {
            BlockState st;
            if (y <= h) {
                st = y < MIN_Y + 2 ? BEDROCK : (y < 0 ? DEEPSLATE : STONE);
            } else if (y < Terrain.SEA) {
                st = WATER;
            } else {
                st = ICE;
            }
            states[y - MIN_Y] = st;
        }
        return new NoiseColumn(MIN_Y, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("FROSTBOUND zone: " + Terrain.zone(pos.getX(), pos.getZ()) + " h=" + Terrain.height(pos.getX(), pos.getZ()));
    }
}
