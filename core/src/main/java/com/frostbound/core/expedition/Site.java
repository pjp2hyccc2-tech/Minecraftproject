package com.frostbound.core.expedition;

import com.frostbound.core.util.Tree;
import java.util.Map;

/** A place on the expedition map. Distance is travel time one way, in days. */
public final class Site {
    public final int id;
    public final SiteType type;
    public final double distance;
    public final double danger;
    /** Needs the long-range research before it can be visited. */
    public final boolean far;
    /** Map position (arbitrary units, the city at 0,0). */
    public final int mapX;
    public final int mapZ;
    public int visits;
    public boolean depleted;

    public Site(int id, SiteType type, double distance, double danger, boolean far, int mapX, int mapZ) {
        this.id = id;
        this.type = type;
        this.distance = distance;
        this.danger = danger;
        this.far = far;
        this.mapX = mapX;
        this.mapZ = mapZ;
    }

    public String nameKey() {
        return type.key();
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("id", id);
        m.put("type", type.name());
        m.put("dist", distance);
        m.put("danger", danger);
        m.put("far", far);
        m.put("mx", mapX);
        m.put("mz", mapZ);
        m.put("visits", visits);
        m.put("depleted", depleted);
        return m;
    }

    public static Site load(Map<String, Object> m) {
        SiteType type = Tree.e(m, "type", SiteType.class, null);
        if (type == null) {
            return null;
        }
        Site s = new Site(Tree.i(m, "id", 0), type, Tree.d(m, "dist", 2, 0.5, 10), Tree.d(m, "danger", 0.2, 0, 1),
                Tree.b(m, "far", false), Tree.i(m, "mx", 0), Tree.i(m, "mz", 0));
        s.visits = Tree.i(m, "visits", 0, 0, 1000);
        s.depleted = Tree.b(m, "depleted", false);
        return s;
    }
}
