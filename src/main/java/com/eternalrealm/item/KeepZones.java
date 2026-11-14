package com.eternalrealm.item;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Short-lived spots where items appearing on the ground are a player's own: where a player just broke a block that held
 * things (a chest, a barrel, a furnace, a shelf). What spills from it appears in the same tick within a block or so of
 * it; a zone lasts that tick and the next. Pure; {@code VanillaDrops} feeds it.
 */
public final class KeepZones {
    /** How far from the block centre spilled items land (vanilla scatters them inside the block, plus a little). */
    public static final double RADIUS = 1.75;
    /** Ticks a zone lasts after the tick it opened in. */
    public static final long LINGER = 1;
    /** Zones kept at most (old ones go first). */
    public static final int MAX = 64;

    private record Zone(String dimension, double x, double y, double z, long tick) {
    }

    private final Deque<Zone> zones = new ArrayDeque<>();

    /** A player broke a block holding things at the given centre. */
    public void open(String dimension, double x, double y, double z, long tick) {
        prune(tick);
        if (zones.size() >= MAX) {
            zones.pollFirst();
        }
        zones.addLast(new Zone(dimension, x, y, z, tick));
    }

    /** Whether an item appearing here now spilled from such a block. */
    public boolean covers(String dimension, double x, double y, double z, long tick) {
        prune(tick);
        for (Zone zone : zones) {
            if (zone.dimension.equals(dimension) && zone.tick <= tick) {
                double dx = x - zone.x;
                double dy = y - zone.y;
                double dz = z - zone.z;
                if (dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    public int size() {
        return zones.size();
    }

    private void prune(long tick) {
        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            if (zone.tick + LINGER < tick || zone.tick > tick + 20) {
                it.remove();
            }
        }
    }
}
