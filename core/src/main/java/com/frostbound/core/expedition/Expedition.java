package com.frostbound.core.expedition;

import com.frostbound.core.resources.Cost;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A team on the way to a site or on the way back. */
public final class Expedition {
    public final int id;
    public final int siteId;
    public final List<Integer> members = new ArrayList<>();
    public boolean returning;
    /** Days travelled on the current leg. */
    public double progress;
    public Cost loot = Cost.NONE;
    public double researchPoints;
    public int survivors;
    public int lost;
    public String findTech;

    public Expedition(int id, int siteId) {
        this.id = id;
        this.siteId = siteId;
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("id", id);
        m.put("site", siteId);
        m.put("members", new ArrayList<Object>(members));
        m.put("returning", returning);
        m.put("progress", progress);
        m.put("loot", loot.save());
        m.put("research", researchPoints);
        m.put("survivors", survivors);
        m.put("lost", lost);
        if (findTech != null) {
            m.put("tech", findTech);
        }
        return m;
    }

    public static Expedition load(Map<String, Object> m) {
        Expedition e = new Expedition(Tree.i(m, "id", 0), Tree.i(m, "site", -1));
        e.members.addAll(Tree.ints(m, "members"));
        e.returning = Tree.b(m, "returning", false);
        e.progress = Tree.d(m, "progress", 0, 0, 100);
        e.loot = Cost.load(Tree.map(m, "loot"));
        e.researchPoints = Tree.d(m, "research", 0, 0, 10_000);
        e.survivors = Tree.i(m, "survivors", 0, 0, 100);
        e.lost = Tree.i(m, "lost", 0, 0, 100);
        e.findTech = Tree.s(m, "tech", null);
        return e;
    }
}
