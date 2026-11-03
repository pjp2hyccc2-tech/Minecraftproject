package com.hitori.world;

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
 * 白樺: コーポ白樺 and its rainy street. Every chunk is built in {@link #fillFromNoise} from code alone (ground, street,
 * houses, the apartment), writing only inside its own chunk, so the result never depends on the generation order or
 * the world seed: everybody gets the same apartment, identical every time.
 */
public class ShirakabaGenerator extends ChunkGenerator {
    public static final Codec<ShirakabaGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ShirakabaGenerator::getBiomeSource)
    ).apply(i, i.stable(ShirakabaGenerator::new)));

    public ShirakabaGenerator(BiomeSource biomeSource) {
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
        ApartmentBuilder.build(sink);
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
        return Terrain.GROUND + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int h = Terrain.GROUND;
        BlockState[] states = new BlockState[h + 1];
        for (int y = 0; y <= h; y++) {
            states[y] = y == 0 ? Blocks.BEDROCK.defaultBlockState() : y < h ? Blocks.STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return new NoiseColumn(0, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        Layout.Room r = Layout.roomAt(pos);
        info.add("SHIRAKABA: " + (r == null ? (Site.playable(pos.getX(), pos.getZ()) ? "outside" : "beyond") : r.key() + " " + r.nameJa()));
    }
}
