package com.frostbound.core.city;

import com.frostbound.core.util.Msg;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Event history (most recent 500 entries). */
public final class HistoryLog {
    public static final int MAX = 500;

    public record Entry(long step, String kind, Msg msg) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(long step, String kind, Msg msg) {
        entries.add(new Entry(step, kind, msg));
        if (entries.size() > MAX) {
            entries.remove(0);
        }
    }

    public List<Entry> recent(int n) {
        int from = Math.max(0, entries.size() - n);
        return new ArrayList<>(entries.subList(from, entries.size()));
    }

    public int size() {
        return entries.size();
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        List<Object> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> em = e.msg().save();
            em.put("step", e.step());
            em.put("kind", e.kind());
            list.add(em);
        }
        m.put("entries", list);
        return m;
    }

    public void load(Map<String, Object> m) {
        entries.clear();
        for (Map<String, Object> em : Tree.maps(m, "entries")) {
            entries.add(new Entry(Tree.l(em, "step", 0L), Tree.s(em, "kind", "info"), Msg.load(em)));
        }
        while (entries.size() > MAX) {
            entries.remove(0);
        }
    }
}
