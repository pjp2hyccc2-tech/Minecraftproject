package com.frostbound.server;

import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingType;
import com.frostbound.core.city.WorldAccess;
import com.frostbound.world.Terrain;
import net.minecraft.server.level.ServerLevel;

/**
 * The simulation's view of the frozen dimension. Ground checks use the deterministic terrain function (so the
 * simulation never depends on which chunks are loaded); structure changes are written into the world.
 */
public final class ForgeWorldAccess implements WorldAccess {
    /** Largest height difference inside a footprint that construction will level. */
    public static final int MAX_SLOPE = 4;

    private final ServerLevel level;

    public ForgeWorldAccess(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return level;
    }

    /** Shared with the client preview. Null when buildable, otherwise a language key. */
    public static String checkTerrain(int x, int z, int sizeX, int sizeZ) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (Terrain.isLake(x + dx, z + dz)) {
                    return "frostbound.reason.water";
                }
                int h = Terrain.height(x + dx, z + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return max - min > MAX_SLOPE ? "frostbound.reason.terrain" : null;
    }

    /** Floor height (first block above the ground) used for a footprint: the ground at its centre. */
    public static int floorHeight(int x, int z) {
        int h = Terrain.height(x, z);
        if (Terrain.isLake(x, z)) {
            h = Math.max(h, Terrain.SEA);
        }
        return h + 1;
    }

    @Override
    public String checkPlacement(BuildingType type, int x, int y, int z, int sizeX, int sizeZ) {
        return checkTerrain(x, z, sizeX, sizeZ);
    }

    @Override
    public int groundHeight(int x, int z) {
        return floorHeight(x, z);
    }

    @Override
    public void constructionStarted(Building b) {
        StructureBuilder.prepareSite(level, b);
    }

    @Override
    public void constructionFinished(Building b) {
        StructureBuilder.build(level, b);
    }

    @Override
    public void buildingUpgraded(Building b) {
        StructureBuilder.build(level, b);
    }

    @Override
    public void buildingRemoved(Building b) {
        StructureBuilder.clear(level, b);
    }
}
