package com.frostbound.core.research;

import com.frostbound.core.city.Modifiers;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One active research at a time; research labs and workshops add points every hour. */
public final class ResearchSystem {
    private String current;
    private double progress;
    private final LinkedHashSet<String> completed = new LinkedHashSet<>();
    private double lastHourPoints;
    private double bankedPoints;

    public String current() {
        return current;
    }

    public double progress() {
        return progress;
    }

    public double lastHourPoints() {
        return lastHourPoints;
    }

    public Set<String> completed() {
        return Collections.unmodifiableSet(completed);
    }

    public boolean isCompleted(String id) {
        return completed.contains(id);
    }

    /** Null if the tech can be started, otherwise a language key for the reason (cost checked by the city). */
    public String check(Tech t) {
        if (t == null) {
            return "frostbound.research.reason.unknown";
        }
        if (completed.contains(t.id)) {
            return "frostbound.research.reason.done";
        }
        if (t.id.equals(current)) {
            return "frostbound.research.reason.current";
        }
        for (String r : t.requires) {
            if (!completed.contains(r)) {
                return "frostbound.research.reason.requires";
            }
        }
        return null;
    }

    public void start(Tech t) {
        current = t.id;
        progress = Math.min(bankedPoints, t.points);
        bankedPoints -= progress;
    }

    public void cancel() {
        current = null;
        progress = 0;
    }

    /** Adds points; returns the tech that completed this hour, or null. Points without a target are banked (max 60). */
    public Tech addPoints(double points) {
        lastHourPoints = points;
        if (current == null) {
            bankedPoints = Math.min(60, bankedPoints + points * 0.5);
            return null;
        }
        Tech t = Techs.get(current);
        if (t == null) {
            current = null;
            return null;
        }
        progress += points;
        if (progress >= t.points) {
            completed.add(t.id);
            current = null;
            progress = 0;
            return t;
        }
        return null;
    }

    /** Grants a completed tech directly (expedition finds); returns false if already known. */
    public boolean grant(String id) {
        return Techs.get(id) != null && completed.add(id);
    }

    public void apply(Modifiers m) {
        for (String id : completed) {
            Tech t = Techs.get(id);
            if (t != null) {
                t.apply(m);
                m.techs.add(id);
            }
        }
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        if (current != null) {
            m.put("current", current);
        }
        m.put("progress", progress);
        m.put("completed", new ArrayList<Object>(completed));
        m.put("banked", bankedPoints);
        return m;
    }

    public void load(Map<String, Object> m) {
        current = Tree.s(m, "current", null);
        if (current != null && Techs.get(current) == null) {
            Tree.repaired("unknown research " + current);
            current = null;
        }
        progress = Tree.d(m, "progress", 0, 0, 100_000);
        completed.clear();
        for (String id : Tree.strings(m, "completed")) {
            if (Techs.get(id) != null) {
                completed.add(id);
            } else {
                Tree.repaired("unknown tech " + id);
            }
        }
        bankedPoints = Tree.d(m, "banked", 0, 0, 60);
    }
}
