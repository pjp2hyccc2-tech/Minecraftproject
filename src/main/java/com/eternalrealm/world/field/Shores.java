package com.eternalrealm.world.field;

import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;

import java.util.Set;

/**
 * Shores one can climb out onto (owner, 2026-11-14: 「川に落ちたら登れなくなってるから川沿いのブロックを1段ずつ下げて」). The
 * banks of the rivers, the lake, the pond, the plunge pool and the sea stand one block above the water's top block, two
 * here and there (a rock): from the water a player could not get out. The terrain function keeps its banks — the set
 * pieces, habitats and waystones were laid out on it — and every chunk with a shore is mended once as it loads, in
 * worlds made before and after alike (the platform's {@code ShorePatch}), by these rules:
 * <ul>
 *     <li>a column of natural ground next to standing water whose top is {@value #MAX_LOWERED} blocks or less above the
 *     water's top block goes down one block (level with the water: one walks out; one above: one jumps out);</li>
 *     <li>so that the way on stays a walk, a neighbour farther from the water that would now stand two or more blocks
 *     above the lowered column goes down one block too ({@link #ripples});</li>
 *     <li>nothing in the capital, near a waystone or a dungeon entrance, in or next to a set piece of the field, nor
 *     anything built (only natural blocks move).</li>
 * </ul>
 * Pure.
 */
public final class Shores {
    /** Bank columns at most this high above the water's top block are lowered by one. */
    public static final int MAX_LOWERED = 2;
    /** Waystones and dungeon entrances keep this much of their ground (blocks). */
    public static final double KEEP_CLEAR = 24.0;
    /** A chunk is looked at when its centre is this close to a shore (half a chunk's diagonal and a margin). */
    public static final double CHUNK_MARGIN = 16.0;

    private static final Set<String> NATURAL = Set.of(
            "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
            "minecraft:rooted_dirt", "minecraft:mud", "minecraft:mycelium", "minecraft:moss_block", "minecraft:sand",
            "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:snow_block", "minecraft:stone",
            "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:calcite", "minecraft:tuff",
            "minecraft:sandstone", "minecraft:dirt_path", "minecraft:packed_mud", "minecraft:mossy_cobblestone");

    private Shores() {
    }

    /** Whether a block of this id is natural ground that may be lowered. */
    public static boolean natural(String blockId) {
        return NATURAL.contains(blockId);
    }

    /** Whether a bank column with its top block at {@code bankTop} next to water topped at {@code waterTop} goes down. */
    public static boolean lowers(int bankTop, int waterTop) {
        int above = bankTop - waterTop;
        return above >= 1 && above <= MAX_LOWERED;
    }

    /**
     * Whether a neighbour (farther from the water) of a column lowered to {@code loweredTop} goes down too: it would
     * stand two or more blocks above it, and it is still low ground of the shore.
     */
    public static boolean ripples(int neighbourTop, int loweredTop, int waterTop) {
        return neighbourTop - loweredTop >= 2 && neighbourTop - waterTop <= MAX_LOWERED + 1;
    }

    /** Whether ground here may be lowered at all: not in the capital, not near a waystone or a dungeon entrance. */
    public static boolean mayChange(double x, double z) {
        if (FieldLayout.inCapital(x, z)) {
            return false;
        }
        for (FieldLayout.Waystone waystone : FieldLayout.waystones()) {
            if (Math.hypot(waystone.x() - x, waystone.z() - z) < KEEP_CLEAR) {
                return false;
            }
        }
        for (LandmarkSite site : LandmarkSites.all()) {
            if (Math.hypot(site.x() - x, site.z() - z) < site.padRadius() + KEEP_CLEAR) {
                return false;
            }
        }
        return true;
    }

    /** Whether a point is within {@code margin} blocks of a shore: a river's banks, the lake, the pond, the pool, the sea. */
    public static boolean nearWater(double x, double z, double margin) {
        double[] river = FieldLayout.nearestRiver(x, z);
        if (river[1] >= 0) {
            FieldLayout.River nearest = FieldLayout.rivers().get((int) river[1]);
            double half = nearest.sourceHalfWidth()
                    + (nearest.mouthHalfWidth() - nearest.sourceHalfWidth()) * river[2];
            if (river[0] < half + margin) {
                return true;
            }
        }
        double lake = FieldLayout.lakeRadius(x, z);
        if (Math.abs(lake - 1.0) < 0.3 + margin / FieldLayout.LAKE_RZ) {
            return true; // the lake's shore band (its deep middle has no shore)
        }
        if (Math.hypot(x - FieldLayout.ISLAND_X, z - FieldLayout.ISLAND_Z) < FieldLayout.ISLAND_R * 1.25 + margin) {
            return true; // the island in the lake
        }
        if (Math.abs(FieldLayout.coastDistance(x, z)) < margin + 4.0) {
            return true;
        }
        if (Math.hypot(x - FieldLayout.POND_X, z - FieldLayout.POND_Z) < FieldLayout.POND_R + margin) {
            return true;
        }
        return Math.hypot(x - FieldLayout.POOL_X, z - FieldLayout.POOL_Z) < FieldLayout.POOL_R + margin;
    }

    /** Whether the chunk {@code (chunkX, chunkZ)} may hold a shore to mend. */
    public static boolean chunkNearWater(int chunkX, int chunkZ) {
        return nearWater(chunkX * 16 + 8.0, chunkZ * 16 + 8.0, CHUNK_MARGIN);
    }
}
