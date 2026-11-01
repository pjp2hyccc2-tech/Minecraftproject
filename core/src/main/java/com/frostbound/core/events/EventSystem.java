package com.frostbound.core.events;

import com.frostbound.core.city.City;
import com.frostbound.core.city.Notice;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.util.Msg;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Opens at most one choice event at a time. Unanswered events resolve with their last choice after 12
 * game hours. Random events are rolled once per hour (5%) among those whose condition holds.
 */
public final class EventSystem {
    public static final long TIMEOUT = 12L * SimClock.STEPS_PER_HOUR;
    private static final double HOURLY_CHANCE = 0.05;

    public record Pending(String id, long start, List<String> args) {
    }

    public record Scheduled(String id, long at, List<String> args) {
    }

    private Pending pending;
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    public Pending pending() {
        return pending;
    }

    public List<Scheduled> scheduled() {
        return List.copyOf(scheduled);
    }

    public void schedule(String id, long at, List<String> args) {
        if (Events.get(id) != null) {
            scheduled.add(new Scheduled(id, at, List.copyOf(args)));
        }
    }

    /** Opens a scripted event now, or queues it if another event is open. */
    public void trigger(City city, String id, List<String> args) {
        if (pending == null) {
            open(city, id, args);
        } else {
            schedule(id, city.clock().step(), args);
        }
    }

    public boolean onCooldown(String id, long step) {
        Long until = cooldownUntil.get(id);
        return until != null && step < until;
    }

    public void hourly(City city) {
        long now = city.clock().step();
        if (pending != null) {
            if (now - pending.start() >= TIMEOUT) {
                EventDef def = Events.get(pending.id());
                int fallback = def == null ? 0 : def.choices.size() - 1;
                if (def != null) {
                    for (int i = def.choices.size() - 1; i >= 0; i--) {
                        if (def.choices.get(i).available().test(city)) {
                            fallback = i;
                            break;
                        }
                    }
                }
                answer(city, fallback);
            }
            return;
        }
        Iterator<Scheduled> it = scheduled.iterator();
        while (it.hasNext()) {
            Scheduled s = it.next();
            if (s.at() <= now) {
                it.remove();
                open(city, s.id(), s.args());
                return;
            }
        }
        if (!city.rng().chance(HOURLY_CHANCE)) {
            return;
        }
        List<EventDef> candidates = new ArrayList<>();
        double total = 0;
        for (EventDef def : Events.all()) {
            if (def.scripted || onCooldown(def.id, now) || !def.condition.test(city)) {
                continue;
            }
            candidates.add(def);
            total += weight(def, city);
        }
        if (candidates.isEmpty()) {
            return;
        }
        double r = city.rng().nextDouble() * total;
        for (EventDef def : candidates) {
            r -= weight(def, city);
            if (r < 0) {
                open(city, def.id, def.args.apply(city));
                return;
            }
        }
    }

    private static double weight(EventDef def, City city) {
        double w = def.weight;
        if (def.id.equals("mining_accident")) {
            if (city.modifiers().shiftEndHour > 18) {
                w *= 2;
            }
            if (city.society().emergencyActive(city.clock().step())) {
                w *= 3;
            }
        }
        return w;
    }

    private void open(City city, String id, List<String> args) {
        EventDef def = Events.get(id);
        if (def == null) {
            return;
        }
        pending = new Pending(id, city.clock().step(), List.copyOf(args));
        Msg title = Msg.of(def.titleKey());
        city.notify(def.major ? Notice.Kind.MAJOR : Notice.Kind.WARNING, Msg.of("frostbound.msg.event_opened", Msg.tr(def.titleKey())),
                def.major ? "event_major" : "alert");
        city.history().add(city.clock().step(), "event", title);
    }

    /** Applies a choice of the open event; returns the result message, or null if nothing is open. */
    public Msg answer(City city, int choice) {
        if (pending == null) {
            return null;
        }
        EventDef def = Events.get(pending.id());
        Pending p = pending;
        pending = null;
        if (def == null || def.choices.isEmpty()) {
            return null;
        }
        int idx = Math.max(0, Math.min(def.choices.size() - 1, choice));
        EventDef.Choice c = def.choices.get(idx);
        if (!c.available().test(city)) {
            for (int i = def.choices.size() - 1; i >= 0; i--) {
                if (def.choices.get(i).available().test(city)) {
                    c = def.choices.get(i);
                    break;
                }
            }
        }
        Msg result = c.effect().apply(city, p.args());
        cooldownUntil.put(def.id, city.clock().step() + (long) def.cooldownDays * SimClock.STEPS_PER_DAY);
        if (result != null) {
            city.history().add(city.clock().step(), "choice", result);
        }
        return result;
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        if (pending != null) {
            Map<String, Object> p = Tree.map();
            p.put("id", pending.id());
            p.put("start", pending.start());
            p.put("args", new ArrayList<Object>(pending.args()));
            m.put("pending", p);
        }
        List<Object> list = new ArrayList<>();
        for (Scheduled s : scheduled) {
            Map<String, Object> e = Tree.map();
            e.put("id", s.id());
            e.put("at", s.at());
            e.put("args", new ArrayList<Object>(s.args()));
            list.add(e);
        }
        m.put("scheduled", list);
        Map<String, Object> cd = Tree.map();
        cooldownUntil.forEach(cd::put);
        m.put("cooldowns", cd);
        return m;
    }

    public void load(Map<String, Object> m) {
        pending = null;
        scheduled.clear();
        cooldownUntil.clear();
        Map<String, Object> p = Tree.map(m, "pending");
        String pid = Tree.s(p, "id", null);
        if (pid != null && Events.get(pid) != null) {
            pending = new Pending(pid, Tree.l(p, "start", 0L), Tree.strings(p, "args"));
        }
        for (Map<String, Object> e : Tree.maps(m, "scheduled")) {
            String id = Tree.s(e, "id", null);
            if (id != null && Events.get(id) != null) {
                scheduled.add(new Scheduled(id, Tree.l(e, "at", 0L), Tree.strings(e, "args")));
            }
        }
        Map<String, Object> cd = Tree.map(m, "cooldowns");
        for (String k : cd.keySet()) {
            cooldownUntil.put(k, Tree.l(cd, k, 0L));
        }
    }
}
