package com.eternalrealm.world.forge.feature;

import com.eternalrealm.world.field.FieldTerrain;
import com.eternalrealm.world.forge.AstoriaDimension;
import com.eternalrealm.world.forge.FieldPieces;
import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.GenNoise;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * First decoration step of every アストリア chunk: re-skins the top layers of each column by its biome (sand and
 * sandstone, terracotta bands, snow and packed ice, basalt/blackstone/magma, calcite, deepslate/end stone...), adds dune
 * ripples, snow drifts and basalt stubs, turns surface water of the volcano into lava and draws lava rivers in the
 * caldera. Placed once per chunk at the chunk corner; it only writes inside its own chunk.
 */
public final class AreaSurfaceFeature extends Feature<NoneFeatureConfiguration> {
    private static final GenNoise PATCH = new GenNoise(AstoriaLayout.SEED + 101);
    private static final GenNoise DETAIL = new GenNoise(AstoriaLayout.SEED + 102);
    private static final GenNoise DUNES = new GenNoise(AstoriaLayout.SEED + 103);
    private static final GenNoise RIVERS = new GenNoise(AstoriaLayout.SEED + 104);
    private static final GenNoise SPOTS = new GenNoise(AstoriaLayout.SEED + 105);
    private static final Block[] MESA_BANDS = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
            Blocks.TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.TERRACOTTA,
            Blocks.RED_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
            Blocks.TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.TERRACOTTA,
            Blocks.YELLOW_TERRACOTTA};

    public AreaSurfaceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int chunkX = context.origin().getX() & ~15;
        int chunkZ = context.origin().getZ() & ~15;
        int seaLevel = context.chunkGenerator().getSeaLevel();
        // ground heights of the chunk and a one block border, read before anything changes (under a set piece:
        // the terrain's own top, not the piece's)
        int[][] ground = new int[18][18];
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                int gx = chunkX + i - 1;
                int gz = chunkZ + j - 1;
                ground[i][j] = FieldPieces.covers(gx, gz) ? (int) Math.floor(FieldTerrain.heightAt(gx, gz))
                        : level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, gx, gz) - 1;
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkX + dx;
                int z = chunkZ + dz;
                int y = ground[dx + 1][dz + 1];
                boolean piece = FieldPieces.covers(x, z);
                if (y <= level.getMinBuildHeight() + 8 || piece && FieldPieces.ownsGround(x, z)) {
                    continue;   // a hand-made set piece shapes its own ground here
                }
                AstoriaBiome biome = AstoriaDimension.biome(level.getBiome(pos.set(x, y, z)));
                if (biome == null) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (piece) {
                    // the piece stands on the ground here: paint the terrain under it (its own blocks above are
                    // never replaced, and are put back after decoration in any case)
                    int water = FieldTerrain.waterTop(x, z, y);
                    surface = water != FieldTerrain.NO_WATER && water > y ? water : y;
                }
                Column column = new Column(level, pos, x, z, y, surface, seaLevel, ground, dx + 1, dz + 1);
                paint(biome, column);
                changed = true;
            }
        }
        return changed;
    }

    private static void paint(AstoriaBiome biome, Column c) {
        double patch = PATCH.fractal(c.x / 26.0, c.z / 26.0, 2);
        double detail = DETAIL.sample(c.x / 6.0, c.z / 6.0);
        double speck = GenNoise.hash(AstoriaLayout.SEED, c.x, 0, c.z);
        double mix = patch + 0.3 * detail;
        switch (biome) {
            case ROYAL_CAPITAL, ARCELIA_PLAINS, ARCELIA_FLOWER_MEADOW, ARCELIA_FARMLANDS -> {
                if (c.wet()) {
                    c.layers(patch > 0.15 ? Blocks.SAND : patch < -0.3 ? Blocks.CLAY : Blocks.GRAVEL, Blocks.DIRT, 2);
                }
            }
            case ELDER_FOREST -> {
                if (c.wet()) {
                    c.layers(patch > 0.0 ? Blocks.MUD : Blocks.GRAVEL, Blocks.DIRT, 2);
                } else if (mix > 0.38) {
                    c.top(Blocks.PODZOL);
                } else if (mix < -0.45) {
                    c.top(Blocks.MOSS_BLOCK);
                } else if (speck < 0.04) {
                    c.top(Blocks.COARSE_DIRT);
                }
            }
            case ELDER_OLD_GROWTH -> {
                if (c.wet()) {
                    c.layers(Blocks.MUD, Blocks.DIRT, 2);
                } else if (mix > 0.12) {
                    c.top(Blocks.PODZOL);
                } else if (mix < -0.2) {
                    c.top(Blocks.MOSS_BLOCK);
                    if (speck < 0.35) {
                        c.above(1, Blocks.MOSS_CARPET.defaultBlockState());
                    }
                } else if (speck > 0.96) {
                    c.top(Blocks.ROOTED_DIRT);
                }
            }
            case ELDER_MUSHROOM_GLADE -> {
                if (c.wet()) {
                    c.layers(Blocks.MUD, Blocks.DIRT, 2);
                } else {
                    c.top(mix > -0.15 ? Blocks.MYCELIUM : speck < 0.08 ? Blocks.MOSS_BLOCK : Blocks.PODZOL);
                }
            }
            case SANDRIA_DUNES -> {
                c.layers(Blocks.SAND, Blocks.SAND, 3, Blocks.SANDSTONE, 5);
                if (!c.wet()) {
                    double warp = DUNES.sample(c.z / 90.0, c.x / 90.0);
                    double ridge = DUNES.ridge(c.x / 34.0 + 0.6 * warp, c.z / 58.0);
                    int extra = (int) Math.floor(ridge * ridge * ridge * 4.2);
                    c.pile(Blocks.SAND.defaultBlockState(), extra);
                }
            }
            case SANDRIA_MESA -> {
                if (c.wet()) {
                    c.layers(Blocks.RED_SAND, Blocks.RED_SAND, 2);
                } else {
                    int offset = (int) Math.round(PATCH.sample(c.x / 90.0, c.z / 90.0) * 3.0);
                    boolean steep = c.slope() >= 3;
                    c.mesa(steep ? 0 : 1 + (speck < 0.5 ? 1 : 0), offset, 22);
                }
            }
            case SANDRIA_OASIS -> {
                if (c.wet()) {
                    c.layers(Blocks.SAND, Blocks.SAND, 3, Blocks.SANDSTONE, 2);
                } else if (c.y <= c.seaLevel + 3 || mix > 0.2) {
                    c.layers(Blocks.GRASS_BLOCK, Blocks.DIRT, 2, Blocks.SAND, 2);
                } else {
                    c.layers(Blocks.SAND, Blocks.SAND, 3, Blocks.SANDSTONE, 3);
                }
            }
            case NORDIA_SNOWFIELD -> {
                if (c.wet()) {
                    c.layers(Blocks.GRAVEL, Blocks.DIRT, 1);
                } else if (mix > -0.1) {
                    c.layers(Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, 1, Blocks.DIRT, 1);
                    c.drift(detail);
                } else {
                    c.snowyGrass(detail);
                }
            }
            case NORDIA_TAIGA -> {
                if (c.wet()) {
                    c.layers(Blocks.GRAVEL, Blocks.DIRT, 1);
                } else if (mix > 0.3) {
                    c.top(Blocks.PODZOL);
                } else if (mix < -0.42) {
                    c.top(Blocks.COARSE_DIRT);
                }
            }
            case NORDIA_ICE_SPIKES -> {
                if (c.wet()) {
                    c.layers(Blocks.GRAVEL, Blocks.DIRT, 1);
                } else {
                    c.layers(mix > 0.45 ? Blocks.PACKED_ICE : Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, 2, Blocks.DIRT, 1);
                }
            }
            case NORDIA_CRYSTAL_HOLLOW -> {
                if (c.wet()) {
                    c.layers(speck < 0.5 ? Blocks.GRAVEL : Blocks.PACKED_ICE, Blocks.DIRT, 1);
                } else if (mix > 0.3) {
                    c.layers(detail > 0.55 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE, Blocks.PACKED_ICE, 1, Blocks.SNOW_BLOCK, 1);
                } else {
                    c.layers(Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, 1, Blocks.DIRT, 1);
                    c.drift(detail);
                }
            }
            case DRAGONIA_ASHLANDS -> {
                c.lavaInsteadOfWater();
                Block top = mix > 0.35 ? Blocks.TUFF : mix > 0.05 ? Blocks.COARSE_DIRT : mix > -0.25 ? Blocks.GRAVEL
                        : Blocks.BASALT;
                c.layers(speck < 0.015 ? Blocks.MAGMA_BLOCK : top, Blocks.BLACKSTONE, 3, Blocks.BASALT, 3);
            }
            case DRAGONIA_BASALT_FIELDS -> {
                c.lavaInsteadOfWater();
                Block top = mix > 0.2 ? Blocks.BASALT : mix > -0.3 ? Blocks.SMOOTH_BASALT : Blocks.BLACKSTONE;
                c.layers(speck < 0.03 ? Blocks.MAGMA_BLOCK : top, Blocks.BLACKSTONE, 4, Blocks.BASALT, 3);
                if (!c.wet() && detail > 0.68) {
                    c.pile(FeatureKit.axis(Blocks.BASALT, Direction.Axis.Y), (int) ((detail - 0.68) * 12.0));
                }
            }
            case DRAGONIA_CALDERA -> {
                c.lavaInsteadOfWater();
                Block top = mix > 0.45 ? Blocks.MAGMA_BLOCK : mix > -0.1 ? Blocks.BLACKSTONE : Blocks.BASALT;
                c.layers(top, Blocks.BLACKSTONE, 4, Blocks.BASALT, 3);
                double warp = RIVERS.sample(c.z / 70.0, c.x / 70.0);
                double ridge = RIVERS.ridge(c.x / 52.0 + 0.8 * warp, c.z / 52.0);
                if (!c.wet() && ridge > 0.93 && c.isLowestAround()) {
                    c.lavaChannel();
                }
            }
            case CELESTIA_MEADOW -> {
                if (c.wet()) {
                    c.layers(patch > 0.0 ? Blocks.SAND : Blocks.CALCITE, Blocks.DIRT, 2);
                } else if (mix > 0.5) {
                    c.layers(Blocks.CALCITE, Blocks.CALCITE, 1);
                } else if (mix < -0.55) {
                    c.top(Blocks.MOSS_BLOCK);
                }
            }
            case CELESTIA_CHERRY_GROVE -> {
                if (c.wet()) {
                    c.layers(Blocks.SAND, Blocks.DIRT, 2);
                } else if (mix > 0.6) {
                    c.layers(Blocks.CALCITE, Blocks.CALCITE, 1);
                }
            }
            case CELESTIA_WHITE_WOODS -> {
                if (c.wet()) {
                    c.layers(Blocks.CALCITE, Blocks.DIORITE, 2);
                } else if (speck < 0.012) {
                    c.layers(Blocks.QUARTZ_BLOCK, Blocks.CALCITE, 1);
                } else if (mix > 0.25) {
                    c.layers(Blocks.CALCITE, Blocks.DIORITE, 2);
                }
            }
            case ABYSS_WASTES -> {
                double end = SPOTS.fractal(c.x / 30.0, c.z / 30.0, 2);
                Block top = end > 0.42 ? Blocks.END_STONE : mix > 0.3 ? Blocks.COBBLED_DEEPSLATE
                        : mix > -0.15 ? Blocks.DEEPSLATE : mix > -0.45 ? Blocks.BLACKSTONE : Blocks.TUFF;
                c.layers(c.wet() ? Blocks.BLACKSTONE : speck < 0.008 ? Blocks.CRYING_OBSIDIAN : top, Blocks.DEEPSLATE, 5);
            }
            case ABYSS_DEAD_FOREST -> {
                Block top = mix > 0.2 ? Blocks.SOUL_SOIL : mix > -0.25 ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT;
                c.layers(c.wet() ? Blocks.BLACKSTONE : top, Blocks.COARSE_DIRT, 2, Blocks.DEEPSLATE, 3);
            }
            case ABYSS_VOID_RIFT -> {
                Block top = mix > -0.2 ? Blocks.END_STONE : mix < -0.5 ? Blocks.OBSIDIAN : Blocks.BLACKSTONE;
                c.layers(c.wet() ? Blocks.BLACKSTONE : speck < 0.02 ? Blocks.CRYING_OBSIDIAN : top, Blocks.END_STONE, 3,
                        Blocks.DEEPSLATE, 3);
            }
            case ABYSS_SPIRE -> {
                Block top = mix > 0.3 ? Blocks.BLACKSTONE : mix > -0.2 ? Blocks.DEEPSLATE : Blocks.COBBLED_DEEPSLATE;
                c.layers(c.wet() ? Blocks.BLACKSTONE : speck < 0.02 ? Blocks.CRYING_OBSIDIAN : top, Blocks.DEEPSLATE, 5);
            }
        }
    }

    /** One column being painted; {@code y} is its top solid block. */
    private static final class Column {
        private final WorldGenLevel level;
        private final BlockPos.MutableBlockPos pos;
        private final int x;
        private final int z;
        private final int y;
        private final int surface;
        private final int seaLevel;
        private final int[][] ground;
        private final int gx;
        private final int gz;

        Column(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y, int surface, int seaLevel,
               int[][] ground, int gx, int gz) {
            this.level = level;
            this.pos = pos;
            this.x = x;
            this.z = z;
            this.y = y;
            this.surface = surface;
            this.seaLevel = seaLevel;
            this.ground = ground;
            this.gx = gx;
            this.gz = gz;
        }

        /** Fluid (water or lava) lies on the ground. */
        boolean wet() {
            return surface > y;
        }

        /** Largest height step to the four neighbours. */
        int slope() {
            int max = 0;
            max = Math.max(max, Math.abs(ground[gx - 1][gz] - y));
            max = Math.max(max, Math.abs(ground[gx + 1][gz] - y));
            max = Math.max(max, Math.abs(ground[gx][gz - 1] - y));
            return Math.max(max, Math.abs(ground[gx][gz + 1] - y));
        }

        /** No neighbour is lower, so a fluid placed here cannot flow away. */
        boolean isLowestAround() {
            return ground[gx - 1][gz] >= y && ground[gx + 1][gz] >= y && ground[gx][gz - 1] >= y
                    && ground[gx][gz + 1] >= y;
        }

        void top(Block block) {
            if (FeatureKit.isTerrain(get(y))) {
                set(y, block.defaultBlockState());
            }
        }

        void layers(Block top, Block under, int underDepth) {
            layers(top, under, underDepth, under, 0);
        }

        /** Replaces the top block and the layers below it, stopping at caves. */
        void layers(Block top, Block under, int underDepth, Block deep, int deepDepth) {
            int total = 1 + underDepth + deepDepth;
            for (int i = 0; i < total; i++) {
                int yy = y - i;
                if (yy <= level.getMinBuildHeight() + 1 || !FeatureKit.isTerrain(get(yy))) {
                    return;
                }
                Block block = i == 0 ? top : i <= underDepth ? under : deep;
                set(yy, block.defaultBlockState());
            }
        }

        /** Terracotta bands by height, with {@code sand} red sand layers on top. */
        void mesa(int sand, int offset, int depth) {
            for (int i = 0; i < depth; i++) {
                int yy = y - i;
                if (yy <= level.getMinBuildHeight() + 1 || !FeatureKit.isTerrain(get(yy))) {
                    return;
                }
                Block block = i < sand ? Blocks.RED_SAND : MESA_BANDS[Math.floorMod(yy + offset, MESA_BANDS.length)];
                set(yy, block.defaultBlockState());
            }
        }

        /** Stacks {@code count} blocks on top of the ground (dunes, basalt stubs). */
        void pile(BlockState state, int count) {
            for (int i = 1; i <= count; i++) {
                if (!get(y + i).isAir()) {
                    return;
                }
                set(y + i, state);
            }
        }

        void above(int dy, BlockState state) {
            if (get(y + dy).isAir()) {
                set(y + dy, state);
            }
        }

        /** Snow layers of varying depth on snow blocks. */
        void drift(double detail) {
            if (detail > 0.1 && get(y + 1).isAir()) {
                int layers = Math.min(4, 1 + (int) ((detail - 0.1) * 5.0));
                set(y + 1, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, layers));
            }
        }

        void snowyGrass(double detail) {
            BlockState top = get(y);
            if (top.is(Blocks.GRASS_BLOCK) && get(y + 1).isAir()) {
                set(y, Blocks.GRASS_BLOCK.defaultBlockState().setValue(BlockStateProperties.SNOWY, true));
                int layers = detail > 0.3 ? 2 : 1;
                set(y + 1, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, layers));
            }
        }

        /** Surface water of the volcano area becomes lava. */
        void lavaInsteadOfWater() {
            if (!wet()) {
                return;
            }
            for (int yy = surface; yy > y; yy--) {
                BlockState state = get(yy);
                if (state.getFluidState().isEmpty()) {
                    continue;
                }
                set(yy, Blocks.LAVA.defaultBlockState());
            }
        }

        /** A shallow channel of lava with a magma bed (only where the lava cannot run off). */
        void lavaChannel() {
            if (!get(y + 1).isAir()) {
                return;
            }
            set(y - 1, Blocks.MAGMA_BLOCK.defaultBlockState());
            set(y, Blocks.LAVA.defaultBlockState());
        }

        private BlockState get(int yy) {
            return level.getBlockState(pos.set(x, yy, z));
        }

        private void set(int yy, BlockState state) {
            level.setBlock(pos.set(x, yy, z), state, Block.UPDATE_CLIENTS);
        }
    }
}
