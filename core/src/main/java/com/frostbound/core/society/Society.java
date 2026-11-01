package com.frostbound.core.society;

import com.frostbound.core.city.Modifiers;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enacted laws, decrees, strikes and the temporary mood effects of events. Citizens' individual hope and
 * discontent drift toward targets that include these values; the city values are their averages.
 */
public final class Society {
    public static final long LAW_COOLDOWN = SimClock.STEPS_PER_DAY;
    public static final long EMERGENCY_LENGTH = SimClock.STEPS_PER_DAY;
    public static final long EMERGENCY_COOLDOWN = 3L * SimClock.STEPS_PER_DAY;

    private final LinkedHashSet<String> enacted = new LinkedHashSet<>();
    private long lastEnactStep = -LAW_COOLDOWN;
    private long emergencyUntil = -1;
    private long emergencyReadyAt;
    private long strikeUntil = -1;
    private int emergencyUses;

    /** Temporary mood shifts from events; they fade every day. */
    public double eventHope;
    public double eventDiscontent;
    /** Mood shifts from staffed social buildings; recomputed hourly. */
    public double socialHope;
    public double socialDiscontent;
    /** Grief from recent deaths; fades every day. */
    public double grief;

    public int highDiscontentHours;
    public int riotHours;
    public int lowHopeHours;
    public int criticalHours;

    public Set<String> enacted() {
        return Collections.unmodifiableSet(enacted);
    }

    public boolean isEnacted(String id) {
        return enacted.contains(id);
    }

    /** Null if the law can be enacted now, otherwise a language key for the reason. */
    public String checkEnact(Law law, long step) {
        if (law.decree) {
            for (String r : law.requires) {
                if (!enacted.contains(r)) {
                    return "frostbound.law.reason.requires";
                }
            }
            return step < emergencyReadyAt ? "frostbound.law.reason.cooldown" : null;
        }
        if (enacted.contains(law.id)) {
            return "frostbound.law.reason.enacted";
        }
        for (String r : law.requires) {
            if (!enacted.contains(r)) {
                return "frostbound.law.reason.requires";
            }
        }
        for (String c : law.conflicts) {
            if (enacted.contains(c)) {
                return "frostbound.law.reason.conflict";
            }
        }
        return step < lastEnactStep + LAW_COOLDOWN ? "frostbound.law.reason.cooldown" : null;
    }

    public void enact(Law law, long step) {
        if (law.decree) {
            emergencyUntil = step + EMERGENCY_LENGTH;
            emergencyReadyAt = step + EMERGENCY_COOLDOWN;
            emergencyUses++;
        } else {
            enacted.add(law.id);
            lastEnactStep = step;
        }
    }

    public long lawReadyAt() {
        return lastEnactStep + LAW_COOLDOWN;
    }

    public long emergencyReadyAt() {
        return emergencyReadyAt;
    }

    public boolean emergencyActive(long step) {
        return step < emergencyUntil;
    }

    public long emergencyUntil() {
        return emergencyUntil;
    }

    public int emergencyUses() {
        return emergencyUses;
    }

    public boolean strikeActive(long step) {
        return step < strikeUntil;
    }

    public long strikeUntil() {
        return strikeUntil;
    }

    public void startStrike(long step, long length) {
        strikeUntil = Math.max(strikeUntil, step + length);
    }

    public void applyLaws(Modifiers m) {
        for (String id : enacted) {
            Law law = Laws.get(id);
            if (law != null && !law.decree) {
                law.apply(m);
            }
        }
    }

    public void dailyFade() {
        eventHope *= 0.7;
        eventDiscontent *= 0.7;
        grief *= 0.6;
        if (Math.abs(eventHope) < 0.5) {
            eventHope = 0;
        }
        if (Math.abs(eventDiscontent) < 0.5) {
            eventDiscontent = 0;
        }
        if (grief < 0.5) {
            grief = 0;
        }
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("enacted", new ArrayList<Object>(enacted));
        m.put("lastEnact", lastEnactStep);
        m.put("emergencyUntil", emergencyUntil);
        m.put("emergencyReady", emergencyReadyAt);
        m.put("emergencyUses", emergencyUses);
        m.put("strikeUntil", strikeUntil);
        m.put("eventHope", eventHope);
        m.put("eventDisc", eventDiscontent);
        m.put("grief", grief);
        m.put("highDiscH", highDiscontentHours);
        m.put("riotH", riotHours);
        m.put("lowHopeH", lowHopeHours);
        m.put("criticalH", criticalHours);
        return m;
    }

    public void load(Map<String, Object> m) {
        enacted.clear();
        List<String> ids = Tree.strings(m, "enacted");
        for (String id : ids) {
            Law law = Laws.get(id);
            if (law != null && !law.decree) {
                enacted.add(id);
            } else {
                Tree.repaired("unknown law " + id);
            }
        }
        lastEnactStep = Tree.l(m, "lastEnact", -LAW_COOLDOWN);
        emergencyUntil = Tree.l(m, "emergencyUntil", -1L);
        emergencyReadyAt = Tree.l(m, "emergencyReady", 0L);
        emergencyUses = Tree.i(m, "emergencyUses", 0, 0, 100_000);
        strikeUntil = Tree.l(m, "strikeUntil", -1L);
        eventHope = Tree.d(m, "eventHope", 0, -100, 100);
        eventDiscontent = Tree.d(m, "eventDisc", 0, -100, 100);
        grief = Tree.d(m, "grief", 0, 0, 100);
        highDiscontentHours = Tree.i(m, "highDiscH", 0, 0, 100_000);
        riotHours = Tree.i(m, "riotH", 0, 0, 100_000);
        lowHopeHours = Tree.i(m, "lowHopeH", 0, 0, 100_000);
        criticalHours = Tree.i(m, "criticalH", 0, 0, 100_000);
    }
}
