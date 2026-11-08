package com.eternalrealm.world.gen;

import com.eternalrealm.dungeon.DungeonTheme;

import java.util.Objects;

/**
 * Fixed place of one dungeon entrance in アストリア. Landmarks are built in a local frame whose front (+z) faces the
 * capital, then turned by {@link #quarterTurns()} clockwise quarter turns (Minecraft {@code Rotation} order: none,
 * 90, 180, 270) around the centre {@code (x, z)}.
 *
 * @param dungeonId  "d1".."d8"
 * @param area       area (1..7) the site lies in (exact rings)
 * @param x          centre block x
 * @param z          centre block z
 * @param padRadius  radius of the levelled ground around the centre
 * @param theme      palette of the gate (same theme as the dungeon instance)
 * @param entrance   local position in front of the gate (where players enter the dungeon)
 * @param npc        local position of the dungeon NPC next to the gate
 * @param waypoint   local position on the waypoint pad (teleport destination)
 */
public record LandmarkSite(String dungeonId, int area, int x, int z, int padRadius, DungeonTheme theme,
                           Local entrance, Local npc, Local waypoint) {
    public LandmarkSite {
        Objects.requireNonNull(dungeonId, "dungeonId");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(entrance, "entrance");
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(waypoint, "waypoint");
        if (padRadius < 8 || padRadius > 40) {
            throw new IllegalArgumentException(dungeonId + ": padRadius must be 8..40: " + padRadius);
        }
        if (AstoriaAreas.areaAt(x, z) != area) {
            throw new IllegalArgumentException(dungeonId + " at " + x + ", " + z + " is not in area " + area);
        }
    }

    /** Clockwise quarter turns so that the front of the landmark faces the capital. */
    public int quarterTurns() {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? 1 : 3;
        }
        return z > 0 ? 2 : 0;
    }

    /** World x of a local position. */
    public int worldX(int localX, int localZ) {
        return x + rotateX(localX, localZ, quarterTurns());
    }

    /** World z of a local position. */
    public int worldZ(int localX, int localZ) {
        return z + rotateZ(localX, localZ, quarterTurns());
    }

    /** World yaw (Minecraft degrees, 0 = south) of a local yaw. */
    public float worldYaw(float localYaw) {
        return normalizeYaw(localYaw + 90.0F * quarterTurns());
    }

    /** x of {@code (localX, localZ)} turned by {@code turns} clockwise quarter turns (Minecraft BlockPos#rotate). */
    public static int rotateX(int localX, int localZ, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> localX;
            case 1 -> -localZ;
            case 2 -> -localX;
            default -> localZ;
        };
    }

    public static int rotateZ(int localX, int localZ, int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> localZ;
            case 1 -> localX;
            case 2 -> -localZ;
            default -> -localX;
        };
    }

    /** Yaw in [0, 360). */
    public static float normalizeYaw(float yaw) {
        float result = yaw % 360.0F;
        return result < 0.0F ? result + 360.0F : result;
    }

    /**
     * A position in the landmark's local frame; {@code dy} is the height of the feet above the levelled ground
     * (1 = standing on it).
     */
    public record Local(int dx, int dy, int dz, float yaw) {
    }
}
