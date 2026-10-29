package com.huntcraft.quest;

import com.huntcraft.quest.world.QuestMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Positions inside a quest region: the designed base camp (built by worldgen, identical in every region copy),
 * arrival spots, area anchors and spawn positions. Heightmap reads force-load the chunk first.
 */
public final class QuestCamps {
    private QuestCamps() {
    }

    // ------------------------------------------------------------------ camp
    /** Standing position at the centre of the base camp of the region around {@code center}. */
    public static BlockPos campPos(QuestMap map, BlockPos center) {
        return QuestRegions.toWorld(center, map.data().camp.pos());
    }

    public static int campRadius(QuestMap map) {
        return map.data().camp.radius();
    }

    public static float campYaw(QuestMap map) {
        return map.data().camp.yaw();
    }

    /** Arrival position for the n-th party member (free spots in front of the tent). */
    public static Vec3 arrival(QuestMap map, BlockPos center, int index) {
        List<BlockPos> list = map.data().camp.arrivals();
        BlockPos t = list.get(Math.floorMod(index, list.size()));
        BlockPos w = QuestRegions.toWorld(center, t);
        return new Vec3(w.getX() + 0.5, w.getY() + 0.05, w.getZ() + 0.5);
    }

    public static Vec3 arrival(QuestInstance inst, int index) {
        QuestDef q = inst.quest();
        if (q == null) return Vec3.atBottomCenterOf(inst.camp);
        return arrival(q.map, inst.center, index);
    }

    /** World position of a camp fixture (supply box, delivery box, flag, bed) of the region. */
    public static BlockPos fixture(QuestMap map, BlockPos center, BlockPos tilePos) {
        return QuestRegions.toWorld(center, tilePos);
    }

    // ------------------------------------------------------------------ areas
    @Nullable
    public static QuestMapData.Area area(QuestMap map, int n) {
        return map.data().area(n);
    }

    /** Areas where large monsters may live (excludes the camp area). */
    public static List<QuestMapData.Area> largeAreas(QuestMap map) {
        List<QuestMapData.Area> out = new ArrayList<>();
        for (QuestMapData.Area a : map.data().areas) if (a.large() && a.climate() != QuestMapData.CLIMATE_SAFE) out.add(a);
        if (out.isEmpty()) {
            for (QuestMapData.Area a : map.data().areas) if (a.climate() != QuestMapData.CLIMATE_SAFE) out.add(a);
        }
        return out;
    }

    public static int areaAt(QuestInstance inst, double x, double z) {
        QuestDef q = inst.quest();
        if (q == null || !QuestRegions.inside(inst.center, x, z, 0)) return 0;
        return QuestRegions.area(q.map, x, z);
    }

    /** Climate at a world position of the instance (camp radius is always safe). */
    public static int climateAt(QuestInstance inst, double x, double z) {
        QuestDef q = inst.quest();
        if (q == null) return QuestMapData.CLIMATE_NONE;
        double dx = x - (inst.camp.getX() + 0.5), dz = z - (inst.camp.getZ() + 0.5);
        int r = campRadius(q.map);
        if (dx * dx + dz * dz <= r * r) return QuestMapData.CLIMATE_SAFE;
        QuestMapData.Area a = area(q.map, areaAt(inst, x, z));
        return a == null ? QuestMapData.CLIMATE_NONE : a.climate();
    }

    // ------------------------------------------------------------------ surface helpers (chunk loaded first)
    public static void loadArea(ServerLevel level, int x, int z, int radius) {
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) level.getChunk(cx, cz);
        }
    }

    /** Standing position on the ground at x/z (forces the chunk to load), or null on fluids / hazards. */
    @Nullable
    public static BlockPos dryGround(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 2) return null;
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()) return null;
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.FIRE) || below.is(Blocks.LAVA)) return null;
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) return null;
        return pos;
    }

    /** Ground y at x/z for a standing entity (forces the chunk to load). */
    public static int surfaceY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /**
     * Random open, dry spot inside an area of the instance's region (checked against the designed data first, so
     * caves, water, lava and cliffs are avoided before any chunk is loaded). Returns null if nothing suitable was found.
     */
    @Nullable
    public static BlockPos randomPosInArea(ServerLevel level, QuestInstance inst, int areaN, RandomSource r, int spread) {
        QuestDef q = inst.quest();
        if (q == null) return null;
        QuestMapData d = q.map.data();
        QuestMapData.Area a = d.area(areaN);
        if (a == null) return null;
        int campR = campRadius(q.map) + 30;
        for (int i = 0; i < 24; i++) {
            int u = a.x() + (i == 0 ? 0 : r.nextInt(spread * 2 + 1) - spread);
            int v = a.z() + (i == 0 ? 0 : r.nextInt(spread * 2 + 1) - spread);
            if (!QuestMapData.inTile(u, v) || d.zone(u, v) != areaN) continue;
            if (d.fluid(u, v) != QuestMapData.FLUID_NONE || d.isCave(u, v) || d.roughness(u, v) > 2) continue;
            int x = QuestRegions.worldX(inst.center, u), z = QuestRegions.worldZ(inst.center, v);
            if (!QuestRegions.inside(inst.center, x, z, 12)) continue;
            double dx = x - inst.camp.getX(), dz = z - inst.camp.getZ();
            if (dx * dx + dz * dz < campR * campR) continue;
            BlockPos pos = dryGround(level, x, z);
            if (pos != null) return pos;
        }
        return null;
    }
}
