package com.eternalrealm.world.forge;

import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.FieldTerrain;
import com.eternalrealm.world.gen.GenNoise;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The terrain of アストリア's field map: every column is filled from the pure {@link FieldTerrain} heights (bedrock,
 * deepslate, stone with strata, the field's ground cover, then water up to its level; the falls as a curtain of falling
 * water). No noise router, no carvers: the land is exactly the hand-made map on every server, and biome decoration
 * (trees, rocks, ruins, ores, the area surface repaint) runs on top as usual. The hand-made set pieces of the fields
 * ({@link FieldPieces}) are stamped right after the terrain and restored after the decoration.
 */
public class FieldChunkGenerator extends ChunkGenerator {
    public static final Codec<FieldChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, instance.stable(FieldChunkGenerator::new)));

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final long SEED = FieldLayout.SEED + 77;
    private static final GenNoise STRATA = new GenNoise(FieldLayout.SEED + 78);
    private static final GenNoise PATCH = new GenNoise(FieldLayout.SEED + 79);

    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState FALLING_WATER = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);

    public FieldChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------------------------------------- terrain
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        double[] heights = new double[18 * 18];
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                heights[i * 18 + j] = FieldTerrain.heightAt(minX + i - 1, minZ + j - 1);
            }
        }
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int bottom = Math.max(MIN_Y, chunk.getMinBuildHeight());
        int ceiling = chunk.getMaxBuildHeight() - 1;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = minX + lx;
                int z = minZ + lz;
                double h = heights[(lx + 1) * 18 + lz + 1];
                int top = Math.min(ceiling - 1, (int) Math.floor(h));
                double slope = Math.max(Math.abs(heights[(lx + 2) * 18 + lz + 1] - heights[lx * 18 + lz + 1]),
                        Math.abs(heights[(lx + 1) * 18 + lz + 2] - heights[(lx + 1) * 18 + lz])) / 2.0;
                int water = FieldTerrain.waterTop(x, z, top);
                FieldTerrain.Surface surface = FieldTerrain.surfaceAt(x, z, top, water, slope);
                BlockState last = null;
                int lastY = bottom;
                for (int y = bottom; y <= top; y++) {
                    BlockState state = ground(x, y, z, top, bottom, surface);
                    set(chunk, lx, y, lz, state);
                    last = state;
                    lastY = y;
                }
                if (last != null) {
                    oceanFloor.update(lx, lastY, lz, last);
                    worldSurface.update(lx, lastY, lz, last);
                }
                if (water != FieldTerrain.NO_WATER && water > top) {
                    boolean falls = FieldTerrain.isFalls(x, z);
                    int waterTop = Math.min(ceiling, water);
                    for (int y = top + 1; y <= waterTop; y++) {
                        set(chunk, lx, y, lz, falls && y < waterTop ? FALLING_WATER : WATER);
                    }
                    worldSurface.update(lx, waterTop, lz, WATER);
                }
            }
        }
        FieldPieces.stampTerrain(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    /** Biome decoration as usual, then the field set pieces win back whatever the decoration grew into them. */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        super.applyBiomeDecoration(level, chunk, structures);
        FieldPieces.restampAfterDecoration(chunk);
    }

    private static void set(ChunkAccess chunk, int lx, int y, int lz, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(lx, y & 15, lz, state, false);
    }

    /** The block at height {@code y} of a column whose ground is at {@code top}. */
    static BlockState ground(int x, int y, int z, int top, int bottom, FieldTerrain.Surface surface) {
        if (y <= bottom + (int) (GenNoise.hash(SEED, x, y, z) * 4.0)) {
            return BEDROCK;
        }
        int depth = top - y;
        BlockState cover = cover(x, y, z, depth, surface);
        if (cover != null) {
            return cover;
        }
        if (y < 0 + (int) (STRATA.sample(x / 40.0, z / 40.0) * 4.0)) {
            return DEEPSLATE;
        }
        return strata(x, y, z, surface);
    }

    /** The ground cover of a column {@code depth} blocks below its top, or null for plain rock. */
    private static BlockState cover(int x, int y, int z, int depth, FieldTerrain.Surface surface) {
        double patch = PATCH.sample(x / 7.0, z / 7.0);
        return switch (surface) {
            case GRASS, FOREST_FLOOR -> depth == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : depth <= 3 ? Blocks.DIRT.defaultBlockState() : null;
            case DIRT_PATH -> depth == 0 ? Blocks.DIRT_PATH.defaultBlockState()
                    : depth <= 3 ? Blocks.DIRT.defaultBlockState() : null;
            case COARSE_DIRT -> depth == 0 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : depth <= 2 ? Blocks.DIRT.defaultBlockState() : null;
            case SAND -> depth <= 2 ? Blocks.SAND.defaultBlockState()
                    : depth <= 4 ? Blocks.SANDSTONE.defaultBlockState() : null;
            case GRAVEL -> depth <= 1 ? Blocks.GRAVEL.defaultBlockState() : null;
            case MUD -> depth <= 1 ? Blocks.MUD.defaultBlockState()
                    : depth <= 3 ? Blocks.DIRT.defaultBlockState() : null;
            case SNOW -> depth <= 1 ? Blocks.SNOW_BLOCK.defaultBlockState() : null;
            case RIVERBED -> depth == 0 ? (patch > 0.3 ? Blocks.SAND : patch < -0.4 ? Blocks.CLAY : Blocks.GRAVEL)
                    .defaultBlockState() : depth <= 2 ? Blocks.DIRT.defaultBlockState() : null;
            case LAKEBED -> depth <= 1 ? (patch > 0.1 ? Blocks.SAND : patch < -0.45 ? Blocks.CLAY : Blocks.GRAVEL)
                    .defaultBlockState() : depth <= 3 ? Blocks.DIRT.defaultBlockState() : null;
            case SEABED -> depth <= 2 ? (patch < -0.35 ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState()
                    : depth <= 4 ? Blocks.SANDSTONE.defaultBlockState() : null;
            case QUARRY -> depth == 0 ? (patch > 0.35 ? Blocks.GRAVEL : patch < -0.3 ? Blocks.COBBLESTONE
                    : Blocks.ANDESITE).defaultBlockState() : null;
            case ASH -> depth == 0 ? (patch > 0.0 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState()
                    : depth <= 2 ? Blocks.DIRT.defaultBlockState() : null;
            case STONE -> depth == 0 && patch > 0.4 ? Blocks.ANDESITE.defaultBlockState() : null;
            case CLIFF -> null;
        };
    }

    /** Rock with gently tilted strata: stone, andesite, tuff and a pale calcite band now and then. */
    private static BlockState strata(int x, int y, int z, FieldTerrain.Surface surface) {
        double band = y + 6.0 * STRATA.sample(x / 120.0, z / 120.0) + 0.04 * x;
        int layer = Math.floorMod((int) Math.floor(band / 5.0), 9);
        return switch (layer) {
            case 2 -> Blocks.ANDESITE.defaultBlockState();
            case 5 -> surface == FieldTerrain.Surface.CLIFF || surface == FieldTerrain.Surface.QUARRY
                    ? Blocks.TUFF.defaultBlockState() : STONE;
            case 7 -> surface == FieldTerrain.Surface.CLIFF ? Blocks.CALCITE.defaultBlockState() : STONE;
            default -> STONE;
        };
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int top = (int) Math.floor(FieldTerrain.heightAt(x, z));
        int water = FieldTerrain.waterTop(x, z, top);
        if (water != FieldTerrain.NO_WATER && water > top && type.isOpaque().test(WATER)) {
            return water + 1;
        }
        return top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int bottom = Math.max(MIN_Y, level.getMinBuildHeight());
        int top = (int) Math.floor(FieldTerrain.heightAt(x, z));
        int water = FieldTerrain.waterTop(x, z, top);
        int height = level.getHeight();
        BlockState[] states = new BlockState[height];
        FieldTerrain.Surface surface = FieldTerrain.surfaceAt(x, z, top, water, 0.0);
        for (int i = 0; i < height; i++) {
            int y = level.getMinBuildHeight() + i;
            if (y < bottom) {
                states[i] = Blocks.AIR.defaultBlockState();
            } else if (y <= top) {
                states[i] = ground(x, y, z, top, bottom, surface);
            } else if (water != FieldTerrain.NO_WATER && y <= water) {
                states[i] = WATER;
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    // ------------------------------------------------------------------------------------------------- the rest
    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        // no caves: the fields are about the surface; dungeons are instanced
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random,
                             ChunkAccess chunk) {
        // the ground cover is laid in fillFromNoise; biomes repaint it with the area surface feature
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos chunkPos = region.getCenter();
        Holder<Biome> biome = region.getBiome(chunkPos.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, chunkPos, random);
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return FieldLayout.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Astoria field " + FieldLayout.areaAt(pos.getX(), pos.getZ()) + ", ground "
                + (int) Math.floor(FieldTerrain.heightAt(pos.getX(), pos.getZ())));
    }
}
