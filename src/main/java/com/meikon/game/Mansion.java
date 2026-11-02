package com.meikon.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything the game needs to know about one generated mansion (recorded by {@link MansionBuilder}). */
public final class Mansion {
    public final long seed;
    public final List<Room> rooms = new ArrayList<>();
    public Room safeRoom;
    public AABB safeBox;
    public BlockPos spawn = BlockPos.ZERO;
    public float spawnYaw;
    public BlockPos safeDoor = BlockPos.ZERO;
    /** A spot in the corridor just outside the safe room. */
    public BlockPos safeOutside = BlockPos.ZERO;
    public final List<BlockPos> waypoints = new ArrayList<>();
    public final List<BlockPos> darkSpots = new ArrayList<>();
    public final List<Closet> closets = new ArrayList<>();
    public final List<BlockPos> andons = new ArrayList<>();
    public final List<BlockPos> scrolls = new ArrayList<>();
    public final List<BlockPos> shoji = new ArrayList<>();
    public final List<BlockPos> doors = new ArrayList<>();
    public final List<BlockPos> clocks = new ArrayList<>();
    public final Map<Long, String> tansuLoot = new HashMap<>();
    public final Map<Long, Integer> letters = new HashMap<>();
    public final List<BlockPos> bellSpots = new ArrayList<>();
    public final List<BlockPos> dollSpawns = new ArrayList<>();
    public final List<BlockPos> decorDolls = new ArrayList<>();
    /** Butsuma candle stands, index = colour (0 gold, 1 silver, 2 black). */
    public final BlockPos[] candles = new BlockPos[3];
    /** The order (colours) in which the candles must be lit. */
    public final int[] candleOrder = new int[3];
    @Nullable public BlockPos butsumaBell;
    @Nullable public BlockPos headlessDoll;
    @Nullable public BlockPos dollBell;
    @Nullable public BlockPos kuraDoor;
    @Nullable public BlockPos kuraBell;
    @Nullable public Room kura;
    @Nullable public BlockPos well;
    @Nullable public BlockPos sakazuki;
    public BlockPos toriiCenter = BlockPos.ZERO;
    public final List<BlockPos> gateBlocks = new ArrayList<>();
    public BlockPos brideStart = BlockPos.ZERO;
    @Nullable public Room weddingHall;

    public Mansion(long seed) {
        this.seed = seed;
    }

    @Nullable
    public Room roomAt(double x, double z) {
        for (Room r : rooms) {
            if (r.contains(x, z)) return r;
        }
        return null;
    }

    @Nullable
    public Room room(Room.Type type) {
        for (Room r : rooms) {
            if (r.type == type) return r;
        }
        return null;
    }

    public int closetAt(BlockPos feet) {
        for (Closet c : closets) {
            if (c.contains(feet)) return c.index();
        }
        return -1;
    }
}
