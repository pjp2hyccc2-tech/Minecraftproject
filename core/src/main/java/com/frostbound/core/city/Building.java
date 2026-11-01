package com.frostbound.core.city;

import com.frostbound.core.util.Tree;
import java.util.Map;

/**
 * One placed building. {@code x, y, z} is the footprint's minimum corner at floor level; rotation 1 and 3
 * swap width and depth. Worker counts are derived from citizens every hour and are not saved.
 */
public final class Building {
    public enum State {
        CONSTRUCTION,
        ACTIVE
    }

    public final int id;
    public final String typeId;
    public final int x;
    public final int y;
    public final int z;
    public final int rotation;
    public int level = 1;
    public State state = State.CONSTRUCTION;
    /** Worker-hours of construction done. */
    public double buildProgress;
    /** Steps left until a running upgrade completes (0 = none). */
    public long upgradeStepsLeft;
    public double durability = 100.0;
    /** Indoor temperature, degrees C. */
    public double temperature;
    public int desiredWorkers;
    public boolean paused;

    // Derived (not saved)
    public transient int workers;
    public transient double eta;
    public transient double heatBoost;
    public transient double efficiency;
    public transient double lastOutput;
    public transient int residents;
    public transient int patients;

    public Building(int id, String typeId, int x, int y, int z, int rotation) {
        this.id = id;
        this.typeId = typeId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = Math.floorMod(rotation, 4);
    }

    public BuildingType type() {
        BuildingType t = BuildingTypes.get(typeId);
        return t != null ? t : BuildingTypes.TENT;
    }

    public int sizeX() {
        return rotation % 2 == 0 ? type().width : type().depth;
    }

    public int sizeZ() {
        return rotation % 2 == 0 ? type().depth : type().width;
    }

    public int maxX() {
        return x + sizeX() - 1;
    }

    public int maxZ() {
        return z + sizeZ() - 1;
    }

    public double centerX() {
        return x + sizeX() / 2.0;
    }

    public double centerZ() {
        return z + sizeZ() / 2.0;
    }

    public boolean active() {
        return state == State.ACTIVE;
    }

    public boolean working() {
        return state == State.ACTIVE && !paused;
    }

    /** Footprints overlap or touch with less than {@code gap} blocks between them. */
    public boolean overlaps(int ox0, int oz0, int ox1, int oz1, int gap) {
        return ox0 <= maxX() + gap && ox1 >= x - gap && oz0 <= maxZ() + gap && oz1 >= z - gap;
    }

    public boolean contains(int bx, int bz) {
        return bx >= x && bx <= maxX() && bz >= z && bz <= maxZ();
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("id", id);
        m.put("type", typeId);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("rot", rotation);
        m.put("level", level);
        m.put("state", state.name());
        m.put("progress", buildProgress);
        m.put("upgrade", upgradeStepsLeft);
        m.put("durability", durability);
        m.put("temp", temperature);
        m.put("desired", desiredWorkers);
        m.put("paused", paused);
        return m;
    }

    /** Returns null when the record is unusable (unknown type); the caller quarantines it. */
    public static Building load(Map<String, Object> m) {
        String type = Tree.s(m, "type", null);
        if (type == null || BuildingTypes.get(type) == null) {
            Tree.repaired("unknown building type " + type);
            return null;
        }
        int id = Tree.i(m, "id", -1);
        if (id < 0) {
            Tree.repaired("building without id");
            return null;
        }
        Building b = new Building(id, type, Tree.i(m, "x", 0), Tree.i(m, "y", 64, -64, 320), Tree.i(m, "z", 0),
                Tree.i(m, "rot", 0, 0, 3));
        BuildingType t = b.type();
        b.level = Tree.i(m, "level", 1, 1, t.maxLevel);
        b.state = Tree.e(m, "state", State.class, State.ACTIVE);
        b.buildProgress = Tree.d(m, "progress", 0, 0, 1e6);
        b.upgradeStepsLeft = Tree.l(m, "upgrade", 0L, 0L, 1_000_000L);
        b.durability = Tree.d(m, "durability", 100, 0, 100);
        b.temperature = Tree.d(m, "temp", -20, -120, 60);
        b.desiredWorkers = Tree.i(m, "desired", 0, 0, t.maxWorkers(t.maxLevel));
        b.paused = Tree.b(m, "paused", false);
        return b;
    }
}
