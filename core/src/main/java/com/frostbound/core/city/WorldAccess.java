package com.frostbound.core.city;

/**
 * What the simulation needs from the Minecraft world. The Forge layer implements it; tests use the
 * no-op default. The simulation never depends on chunks being loaded.
 */
public interface WorldAccess {
    WorldAccess NONE = new WorldAccess() {
    };

    /** Null if the footprint can be built on, otherwise a language key for the reason. */
    default String checkPlacement(BuildingType type, int x, int y, int z, int sizeX, int sizeZ) {
        return null;
    }

    default int groundHeight(int x, int z) {
        return 64;
    }

    default void constructionStarted(Building b) {
    }

    default void constructionFinished(Building b) {
    }

    default void buildingUpgraded(Building b) {
    }

    default void buildingRemoved(Building b) {
    }
}
