package com.reisatsu.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * ひだまり: the restaurant by the deadly curve. Every chunk is built in {@link #fillFromNoise} from code alone
 * (land, road, property, restaurant), writing only inside its own chunk, so the result never depends on the
 * generation order or the world seed: everybody gets the same restaurant, identical every time.
 */
public class HidamariGenerator extends ChunkGenerator {
    public static final Codec<HidamariGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(HidamariGenerator::getBiomeSource)
    ).apply(i, i.stable(HidamariGenerator::new)));

    public HidamariGenerator(BiomeSource biomeSource) {
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

    /** Builds one chunk (also used by tests). */
    public static void generate(ChunkAccess chunk) {
        ChunkSink sink = new ChunkSink(chunk);
        Terrain.build(sink, chunk);
        ExteriorBuilder.build(sink);
        RestaurantBuilder.build(sink);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
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
        return Terrain.height(x, z) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int h = Terrain.height(x, z);
        BlockState[] states = new BlockState[h + 1];
        for (int y = 0; y <= h; y++) {
            states[y] = y == 0 ? Blocks.BEDROCK.defaultBlockState() : y < h ? Blocks.STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return new NoiseColumn(0, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        Layout.Room r = Layout.roomAt(pos);
        info.add("HIDAMARI: " + (r == null ? (Site.playable(pos.getX(), pos.getZ()) ? "outside" : "beyond") : r.key() + " " + r.nameJa())
                + " rho=" + String.format(java.util.Locale.ROOT, "%.1f", Site.rho(pos.getX(), pos.getZ())));
    }
}
