package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.forge.AstoriaBiomeSource;
import com.eternalrealm.world.forge.AstoriaDimension;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.DarkTowerShape;
import com.eternalrealm.world.forge.HubBuilder;
import com.eternalrealm.world.forge.HubLayout;
import com.eternalrealm.world.forge.LandmarkBuilder;
import com.eternalrealm.world.forge.Landmarks;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.gen.HubSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The アストリア dimension inside a real server: the level and its biome source exist, biomes follow the area rings,
 * chunks of every area generate with their surface theme, and the capital and every dungeon entrance build with free
 * NPC/spawn positions. Chunk generation and building run synchronously, so these tests hold the server thread for a
 * while (their tick timeouts are generous but only count ticks).
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldGameTests {
    private static final String EMPTY = "empty";
    /** Blocks that show the surface theme of each area (natural blocks count for the green areas 1, 2 and 6). */
    private static final Map<Integer, Set<Block>> THEMES = Map.of(
            1, Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.DIRT_PATH,
                    Blocks.FARMLAND, Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE, Blocks.COBBLESTONE),
            2, Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.MYCELIUM, Blocks.COARSE_DIRT,
                    Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.DIRT, Blocks.GRAVEL, Blocks.STONE, Blocks.ANDESITE,
                    Blocks.GRANITE, Blocks.DIORITE),
            3, Set.of(Blocks.SAND, Blocks.SANDSTONE, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
                    Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.WHITE_TERRACOTTA,
                    Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.GRASS_BLOCK, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE),
            4, Set.of(Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.ICE, Blocks.POWDER_SNOW),
            5, Set.of(Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.TUFF, Blocks.GRAVEL,
                    Blocks.COARSE_DIRT, Blocks.OBSIDIAN, Blocks.LAVA, Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BASALT,
                    Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.SHROOMLIGHT),
            6, Set.of(Blocks.GRASS_BLOCK, Blocks.CALCITE, Blocks.DIORITE, Blocks.MOSS_BLOCK, Blocks.QUARTZ_BLOCK,
                    Blocks.SAND, Blocks.DIRT, Blocks.GRAVEL, Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE),
            7, Set.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.END_STONE,
                    Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.SOUL_SOIL, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
                    Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE_BRICKS,
                    Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.BONE_BLOCK));

    private WorldGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void astoriaDimensionIsLoaded(GameTestHelper helper) {
        ServerLevel astoria = astoria(helper);
        BiomeSource source = astoria.getChunkSource().getGenerator().getBiomeSource();
        check(source instanceof AstoriaBiomeSource, "astoria uses " + source.getClass().getName());
        check(source.possibleBiomes().size() == AstoriaBiome.values().length,
                "possible biomes " + source.possibleBiomes().size());
        check(astoria.getSeaLevel() == 63, "the overworld noise settings give sea level 63, got " + astoria.getSeaLevel());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void biomeSourceFollowsTheAreaRings(GameTestHelper helper) {
        ServerLevel astoria = astoria(helper);
        BiomeSource source = astoria.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = astoria.getChunkSource().randomState().sampler();
        int[] distances = {0, 60, 150, 450, 750, 1050, 1350, 1650, 1950, 2600};
        for (int distance : distances) {
            for (int angle = 0; angle < 360; angle += 30) {
                int x = (int) Math.round(distance * Math.cos(Math.toRadians(angle)));
                int z = (int) Math.round(distance * Math.sin(Math.toRadians(angle)));
                Holder<Biome> holder = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(64),
                        QuartPos.fromBlock(z), sampler);
                AstoriaBiome biome = AstoriaDimension.biome(holder);
                check(biome != null, "no Astoria biome at " + x + ", " + z + ": " + holder.unwrapKey());
                check(biome.area() == AstoriaAreas.areaAtDistance(distance),
                        biome + " (area " + biome.area() + ") at distance " + distance + " (" + x + ", " + z + ")");
            }
        }
        check(AstoriaDimension.biome(source.getNoiseBiome(0, 16, 0, sampler)) == AstoriaBiome.ROYAL_CAPITAL,
                "the capital stands in the royal capital meadows");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 2400)
    public static void everyAreaGeneratesWithItsSurfaceTheme(GameTestHelper helper) {
        ServerLevel astoria = astoria(helper);
        StringBuilder report = new StringBuilder();
        for (int area = 1; area <= AstoriaAreas.COUNT; area++) {
            double angle = Math.toRadians(25 + area * 47);
            int distance = area == AstoriaAreas.COUNT ? 2000 : (area - 1) * AstoriaAreas.RING_WIDTH + 150;
            int x = (int) Math.round(distance * Math.cos(angle));
            int z = (int) Math.round(distance * Math.sin(angle));
            LevelChunk chunk = astoria.getChunk(x >> 4, z >> 4);
            Set<Block> theme = THEMES.get(area);
            int themed = 0;
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    if (columnShowsTheme(astoria, baseX + dx, baseZ + dz, theme)) {
                        themed++;
                    }
                }
            }
            report.append(" area ").append(area).append(" @").append(chunk.getPos()).append(": ").append(themed)
                    .append("/256");
            int required = area == 1 || area == 2 || area == 6 ? 128 : 154;
            check(themed >= required, "surface theme missing:" + report);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 2400)
    public static void capitalIsBuiltOnceWithFreeSpots(GameTestHelper helper) {
        ServerLevel astoria = astoria(helper);
        HubLayout planned = HubLayout.of(astoria);
        HubLayout layout = HubBuilder.ensureBuilt(astoria);
        check(HubBuilder.isBuilt(astoria), "the capital should be marked as built");
        check(layout.groundY() == planned.groundY(), "planned floor " + planned.groundY() + " != built " + layout.groundY());
        for (HubSpot spot : HubSpot.values()) {
            BlockPos pos = layout.position(spot);
            check(standable(astoria, pos), spot + " at " + pos + " is blocked: " + describe(astoria, pos));
        }
        int y = layout.groundY();
        check(astoria.getBlockState(new BlockPos(0, y + 4, 0)).is(Blocks.CHISELED_QUARTZ_BLOCK), "fountain missing");
        check(!astoria.getBlockState(new BlockPos(20, y + 5, 39)).isAir(), "south wall missing");
        check(astoria.getBlockState(new BlockPos(0, y + 3, -20)).isAir(), "castle gate should be open");
        check(!astoria.getBlockState(new BlockPos(0, y + 6, -20)).isAir(), "castle front wall missing");
        check(HubBuilder.ensureBuilt(astoria).groundY() == y, "the capital must not move on a second call");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 2400)
    public static void dungeonEntrancesAreBuiltWithFreeSpots(GameTestHelper helper) {
        ServerLevel astoria = astoria(helper);
        check(Landmarks.dungeonIds().size() == 8, "expected 8 dungeon entrances, got " + Landmarks.dungeonIds());
        for (String id : Landmarks.dungeonIds()) {
            BlockPos planned = Landmarks.entrance(astoria, id);
            LandmarkBuilder.ensureBuilt(astoria, id);
            check(LandmarkBuilder.isBuilt(astoria, id), id + " should be marked as built");
            check(!LandmarkBuilder.ensureBuilt(astoria, id), id + " must be built only once");
            check(planned.equals(Landmarks.entrance(astoria, id)), id + " entrance moved after building");
            for (BlockPos pos : List.of(Landmarks.entrance(astoria, id), Landmarks.npc(astoria, id),
                    Landmarks.waypoint(astoria, id))) {
                check(standable(astoria, pos), id + " spot " + pos + " is blocked: " + describe(astoria, pos));
                check(AstoriaTravel.areaAt(pos.getX(), pos.getZ()) == Landmarks.area(id), id + " spot outside its area");
            }
        }
        // the dark tower itself is drawn by world generation: its wall stands 101 blocks above the plaza (not a floor level)
        int towerFloor = Landmarks.entrance(astoria, "d8").getY() - 1;
        BlockPos wall = new BlockPos(DarkTowerShape.CENTER_X, towerFloor + 101, DarkTowerShape.CENTER_Z + 11);
        check(!astoria.getBlockState(wall).isAir(), "the dark tower wall is missing at " + wall);
        check(astoria.getBlockState(wall.north(3)).isAir(), "the dark tower should be hollow at " + wall.north(3));
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------------ helpers

    /** The top terrain block of the column (or one of the 3 below it) is a theme block; plants and trees are skipped. */
    private static boolean columnShowsTheme(ServerLevel level, int x, int z, Set<Block> theme) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y > top - 64 && y > level.getMinBuildHeight(); y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (theme.contains(state.getBlock())) {
                return true;
            }
            if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.LOGS) || !state.isSolidRender(level, pos)) {
                continue;
            }
            for (int below = 1; below <= 3; below++) {
                if (theme.contains(level.getBlockState(pos.set(x, y - below, z)).getBlock())) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /** Feet and head are free (a carpet is fine) and the block below carries the player. */
    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        boolean carpet = feetState.getBlock() instanceof CarpetBlock;
        boolean feetFree = carpet || feetState.getCollisionShape(level, feet).isEmpty();
        boolean headFree = level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        boolean floor = carpet || !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
        return feetFree && headFree && floor;
    }

    private static String describe(ServerLevel level, BlockPos feet) {
        return "below=" + level.getBlockState(feet.below()) + " feet=" + level.getBlockState(feet) + " head="
                + level.getBlockState(feet.above());
    }

    private static ServerLevel astoria(GameTestHelper helper) {
        ServerLevel astoria = helper.getLevel().getServer().getLevel(AstoriaDimension.LEVEL);
        if (astoria == null) {
            throw new GameTestAssertException("the dimension " + AstoriaDimension.LEVEL.location() + " is not loaded");
        }
        return astoria;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
