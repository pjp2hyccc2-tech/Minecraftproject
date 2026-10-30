package com.mcbesiege.physics;

import java.util.List;

/** Supplies immovable world geometry (e.g. Minecraft blocks) overlapping a region. */
public interface WorldCollisionProvider {
    /** Appends the static boxes overlapping the given world-space region. Called at most once per body per tick. */
    void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, List<StaticBox> out);

    /** True if the region is loaded / simulatable. Bodies in unloaded regions are frozen. */
    default boolean isLoaded(double x, double y, double z) { return true; }

    WorldCollisionProvider NONE = (a, b, c, d, e, f, out) -> {};
}
