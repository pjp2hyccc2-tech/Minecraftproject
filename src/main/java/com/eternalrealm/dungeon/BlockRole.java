package com.eternalrealm.dungeon;

/**
 * What a block of a generated instance is for. The platform maps {@link DungeonTheme} + role to a real block (e.g.
 * ice castle + WALL = packed ice), so layouts stay Minecraft-independent and every theme reuses the same generator.
 */
public enum BlockRole {
    /** Walkable floor (rooms, corridors, arenas). */
    FLOOR,
    /** Walls and ceilings: the closed shell around every space. */
    WALL,
    /** Decorative columns inside rooms and arenas (full height, never in front of an opening). */
    PILLAR,
    /** Light sources set into ceilings. */
    LIGHT,
    /** Decorative floor markings (room emblem, arena border). */
    ACCENT,
    /** Barrier filling a sealed door opening; replaced by {@link #AIR} when the door opens. */
    DOOR,
    /** Empty space (carved interiors and open passages); always plain air, independent of the theme. */
    AIR
}
