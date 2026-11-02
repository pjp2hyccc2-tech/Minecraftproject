package com.sounan.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sounan.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
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

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 帰らず山: the baked mountain. Every chunk is built in {@link #fillFromNoise} from the terrain data alone (terrain,
 * water, cedars, undergrowth and all POI structures), writing only inside its own chunk, so the result never depends
 * on generation order and the world seed is irrelevant: everybody gets the same mountain.
 */
public class MountainGenerator extends ChunkGenerator {
    public static final Codec<MountainGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(MountainGenerator::getBiomeSource)
    ).apply(i, i.stable(MountainGenerator::new)));

    public MountainGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures,
                             ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        // everything is placed in fillFromNoise
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 256;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures,
                                                        ChunkAccess chunk) {
        generate(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    private void generate(ChunkAccess chunk) {
        TerrainData t = TerrainData.get();
        ChunkSink sink = new ChunkSink(chunk);
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState rock = ModBlocks.MOUNTAIN_ROCK.get().defaultBlockState();
        BlockState dirt = ModBlocks.FOREST_DIRT.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx;
                int z = z0 + dz;
                int h = t.height(x, z);
                int steep = t.steepness(x, z);
                BlockState top = surfaceBlock(t.surface(x, z), x, z);
                boolean soil = steep < 3 && t.surface(x, z) != TerrainData.SURF_ROCK;
                for (int y = 0; y <= h; y++) {
                    BlockState s;
                    if (y == 0) s = bedrock;
                    else if (y == h) s = top;
                    else if (soil && y >= h - 3) s = dirt;
                    else s = rock;
                    chunk.setBlockState(pos.set(x, y, z), s, false);
                }
                int w = t.water(x, z);
                for (int y = h + 1; y <= w; y++) chunk.setBlockState(pos.set(x, y, z), water, false);
            }
        }
        Decorator.decorate(sink, t);
        PoiBuilder.build(sink, t, Pois.get());
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    static BlockState surfaceBlock(int type, int x, int z) {
        return switch (type) {
            case TerrainData.SURF_MOSS -> ModBlocks.MOSS_FLOOR.get().defaultBlockState();
            case TerrainData.SURF_TRAIL -> ModBlocks.TRAIL.get().defaultBlockState();
            case TerrainData.SURF_ROCK -> ModBlocks.MOUNTAIN_ROCK.get().defaultBlockState();
            case TerrainData.SURF_STONES -> ModBlocks.RIVER_STONES.get().defaultBlockState();
            case TerrainData.SURF_GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case TerrainData.SURF_ASPHALT -> ModBlocks.ASPHALT.get().defaultBlockState();
            case TerrainData.SURF_DIRT -> (Decorator.hash(x, z, 5) & 3) == 0 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : ModBlocks.FOREST_DIRT.get().defaultBlockState();
            case TerrainData.SURF_MOSSROCK -> ModBlocks.MOSSY_ROCK.get().defaultBlockState();
            default -> ModBlocks.FOREST_FLOOR.get().defaultBlockState();
        };
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        TerrainData t = TerrainData.get();
        int h = t.height(x, z);
        if (type == Heightmap.Types.WORLD_SURFACE_WG || type == Heightmap.Types.WORLD_SURFACE
                || type == Heightmap.Types.MOTION_BLOCKING || type == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) {
            h = Math.max(h, t.water(x, z));
        }
        return h + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        TerrainData t = TerrainData.get();
        int h = t.height(x, z);
        int w = t.water(x, z);
        BlockState[] states = new BlockState[Math.max(h, w) + 1];
        for (int y = 0; y < states.length; y++) {
            if (y == 0) states[y] = Blocks.BEDROCK.defaultBlockState();
            else if (y < h) states[y] = ModBlocks.MOUNTAIN_ROCK.get().defaultBlockState();
            else if (y == h) states[y] = surfaceBlock(t.surface(x, z), x, z);
            else states[y] = Blocks.WATER.defaultBlockState();
        }
        return new NoiseColumn(0, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        TerrainData t = TerrainData.get();
        info.add("SOUNAN area: " + TerrainData.areaKey(t.area(pos.getX(), pos.getZ())) + " h=" + t.height(pos.getX(), pos.getZ()));
    }
}
