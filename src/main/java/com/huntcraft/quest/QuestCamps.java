package com.huntcraft.quest;

import com.huntcraft.HuntCraft;
import com.huntcraft.quest.block.SupplyBoxBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Terrain helpers for quest regions: base camp site selection + construction, surface lookups and
 * spawn position search. Every heightmap read force-loads the chunk first (unloaded chunks report min height).
 */
public final class QuestCamps {
    /** Half size of the flattened camp pad. */
    public static final int PAD = 8;
    /** Template is 15x?x15, its (7, 1, 7) sits on the camp position. */
    public static final int TEMPLATE_HALF = 7;
    /** Player arrival offsets relative to the camp position (kept free in the camp template). */
    private static final int[][] ARRIVALS = {{0, 1}, {-2, 1}, {2, 1}, {0, 2}, {-2, 2}, {2, 2}};

    private QuestCamps() {
    }

    // ------------------------------------------------------------------ noise-based estimates (no chunk loading)
    private static int baseHeight(ServerLevel level, int x, int z, Heightmap.Types type) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        return gen.getBaseHeight(x, z, type, level, rs);
    }

    /** Picks a flat, dry spot near the centre of area 1 of the region. */
    public static BlockPos findCampSite(ServerLevel level, BlockPos center) {
        int bx = QuestRegions.areaCenterX(center.getX(), QuestRegions.CAMP_AREA);
        int bz = QuestRegions.areaCenterZ(center.getZ(), QuestRegions.CAMP_AREA);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        BlockPos best = new BlockPos(bx, sea + 2, bz);
        double bestScore = Double.MAX_VALUE;
        int[][] ring = {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}, {0, -8}, {0, 8}, {-8, 0}, {8, 0}};
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int x = bx + i * 20, z = bz + j * 20;
                int h = baseHeight(level, x, z, Heightmap.Types.WORLD_SURFACE_WG);
                int floor = baseHeight(level, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
                double score = (Math.abs(i) + Math.abs(j)) * 4.0;
                if (floor < h) score += 1000; // covered by water / lava
                if (h < sea + 2) score += 400;
                int maxDiff = 0;
                for (int[] o : ring) {
                    int hh = baseHeight(level, x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG);
                    int ff = baseHeight(level, x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG);
                    maxDiff = Math.max(maxDiff, Math.abs(hh - h));
                    if (ff < hh) score += 60;
                }
                score += maxDiff * 12.0;
                if (score < bestScore) {
                    bestScore = score;
                    best = new BlockPos(x, Math.max(h, sea + 2), z);
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ camp construction
    /** Flattens the pad and places the camp template. Returns the camp position (ground level where players stand). */
    public static BlockPos buildCamp(ServerLevel level, BlockPos site, QuestMap map) {
        loadArea(level, site.getX(), site.getZ(), PAD + 3);
        int y = site.getY();
        BlockState top = map.padTop(), fill = map.padFill();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -PAD - 2; dx <= PAD + 2; dx++) {
            for (int dz = -PAD - 2; dz <= PAD + 2; dz++) {
                boolean core = Math.abs(dx) <= PAD && Math.abs(dz) <= PAD;
                int x = site.getX() + dx, z = site.getZ() + dz;
                if (core) {
                    for (int yy = y - 7; yy < y - 1; yy++) {
                        p.set(x, yy, z);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) level.setBlock(p, fill, 2);
                    }
                    level.setBlock(p.set(x, y - 1, z), top, 2);
                    for (int yy = y; yy <= y + 14; yy++) {
                        p.set(x, yy, z);
                        if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                } else {
                    // soft rim: remove fluids / plants right next to the pad so nothing spills in
                    for (int yy = y - 1; yy <= y + 3; yy++) {
                        p.set(x, yy, z);
                        BlockState s = level.getBlockState(p);
                        if (!s.getFluidState().isEmpty()) level.setBlock(p, yy < y ? fill : Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        BlockPos camp = new BlockPos(site.getX(), y, site.getZ());
        if (!placeTemplate(level, camp, map)) placeFallbackCamp(level, camp);
        return camp;
    }

    private static boolean placeTemplate(ServerLevel level, BlockPos camp, QuestMap map) {
        try {
            Optional<StructureTemplate> opt = level.getStructureManager().get(map.campTemplate());
            if (opt.isEmpty()) {
                HuntCraft.LOGGER.warn("Camp template {} missing", map.campTemplate());
                return false;
            }
            BlockPos origin = camp.offset(-TEMPLATE_HALF, -1, -TEMPLATE_HALF);
            StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
            opt.get().placeInWorld(level, origin, origin, settings, RandomSource.create(camp.asLong()), Block.UPDATE_CLIENTS);
            return true;
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.error("Failed to place camp template", e);
            return false;
        }
    }

    private static void placeFallbackCamp(ServerLevel level, BlockPos camp) {
        level.setBlock(camp.offset(-5, 0, 0), QuestRegistry.SUPPLY_BOX.get().defaultBlockState().setValue(SupplyBoxBlock.FACING, Direction.EAST), 3);
        level.setBlock(camp.offset(-5, 0, 2), QuestRegistry.DELIVERY_BOX.get().defaultBlockState().setValue(SupplyBoxBlock.FACING, Direction.EAST), 3);
        level.setBlock(camp.offset(0, 0, 4), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
    }

    /** Arrival position for the n-th party member. */
    public static Vec3 arrival(BlockPos camp, int index) {
        int[] o = ARRIVALS[Math.floorMod(index, ARRIVALS.length)];
        return new Vec3(camp.getX() + o[0] + 0.5, camp.getY() + 0.05, camp.getZ() + o[1] + 0.5);
    }

    // ------------------------------------------------------------------ surface helpers (chunk loaded first)
    public static void loadArea(ServerLevel level, int x, int z, int radius) {
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) level.getChunk(cx, cz);
        }
    }

    /** Standing position on the ground at x/z (forces the chunk to load), or null when covered by fluid. */
    @Nullable
    public static BlockPos dryGround(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 2) return null;
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()) return null;
        if (level.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK)) return null;
        return pos;
    }

    /** Ground y at x/z for a standing entity (forces the chunk to load). */
    public static int surfaceY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Random dry position in an area of the region (tries a few times). Loads the chosen chunk. */
    @Nullable
    public static BlockPos randomPosInArea(ServerLevel level, QuestInstance inst, int area, RandomSource r, int spread) {
        int ax = QuestRegions.areaCenterX(inst.center.getX(), area);
        int az = QuestRegions.areaCenterZ(inst.center.getZ(), area);
        int sea = level.getChunkSource().getGenerator().getSeaLevel();
        for (int i = 0; i < 12; i++) {
            int x = ax + r.nextInt(spread * 2 + 1) - spread;
            int z = az + r.nextInt(spread * 2 + 1) - spread;
            if (!QuestRegions.inside(inst.center, x, z, 12)) continue;
            if (Math.abs(x - inst.camp.getX()) < 48 && Math.abs(z - inst.camp.getZ()) < 48) continue;
            int h = baseHeight(level, x, z, Heightmap.Types.WORLD_SURFACE_WG);
            int f = baseHeight(level, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
            if (f < h || h <= sea) continue;
            BlockPos pos = dryGround(level, x, z);
            if (pos != null) return pos;
        }
        return null;
    }
}
