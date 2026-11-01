package com.frostbound.core.resources;

import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * City stockpile in thousandths of a unit (integers, so fractions never drift).
 *
 * <p>Amounts can never go below zero or above capacity: {@link #take} returns what was actually taken
 * and {@link #add} returns what actually fit. Production and consumption are recorded in 24 hourly
 * buckets, which gives the per-day rates and the estimated days left shown in the UI.
 */
public final class ResourceLedger {
    public static final long MILLI = 1000L;
    private static final int TYPES = ResourceType.values().length;

    private final long[] amount = new long[TYPES];
    private final long[] capacity = new long[TYPES];
    private final long[][] produced = new long[TYPES][24];
    private final long[][] consumed = new long[TYPES][24];
    private int hourIndex;
    private int filledHours = 1;

    public ResourceLedger() {
        for (ResourceType t : ResourceType.values()) {
            capacity[t.ordinal()] = baseCapacity(t) * MILLI;
        }
    }

    public static long baseCapacity(ResourceType t) {
        return t == ResourceType.STEAM_CORE ? 20 : 400;
    }

    public long amountMilli(ResourceType t) {
        return amount[t.ordinal()];
    }

    /** Whole units (rounded down) for display. */
    public int amount(ResourceType t) {
        return (int) (amount[t.ordinal()] / MILLI);
    }

    public long capacityMilli(ResourceType t) {
        return capacity[t.ordinal()];
    }

    public int capacity(ResourceType t) {
        return (int) (capacity[t.ordinal()] / MILLI);
    }

    public void setCapacityUnits(ResourceType t, long units) {
        capacity[t.ordinal()] = Math.max(0, units) * MILLI;
        if (amount[t.ordinal()] > capacity[t.ordinal()]) {
            amount[t.ordinal()] = capacity[t.ordinal()];
        }
    }

    /** Adds up to {@code milli}, limited by capacity; returns what was added. */
    public long add(ResourceType t, long milli) {
        if (milli <= 0) {
            return 0;
        }
        int i = t.ordinal();
        long added = Math.min(milli, Math.max(0, capacity[i] - amount[i]));
        amount[i] += added;
        produced[i][hourIndex] += added;
        return added;
    }

    /** Takes up to {@code milli}; returns what was taken (never more than the stock). */
    public long take(ResourceType t, long milli) {
        if (milli <= 0) {
            return 0;
        }
        int i = t.ordinal();
        long taken = Math.min(milli, amount[i]);
        amount[i] -= taken;
        consumed[i][hourIndex] += taken;
        return taken;
    }

    /** Sets a stock directly (founding, debug commands); clamped to [0, capacity]. */
    public void set(ResourceType t, long milli) {
        int i = t.ordinal();
        amount[i] = Math.max(0, Math.min(capacity[i], milli));
    }

    public boolean has(Cost cost) {
        for (Map.Entry<ResourceType, Integer> e : cost.asMap().entrySet()) {
            if (amount[e.getKey().ordinal()] < e.getValue() * MILLI) {
                return false;
            }
        }
        return true;
    }

    /** Resources missing for {@code cost}, as whole units (empty if affordable). */
    public List<String> missing(Cost cost) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<ResourceType, Integer> e : cost.asMap().entrySet()) {
            long need = e.getValue() * MILLI - amount[e.getKey().ordinal()];
            if (need > 0) {
                out.add(e.getKey().id());
            }
        }
        return out;
    }

    public boolean spend(Cost cost) {
        if (!has(cost)) {
            return false;
        }
        cost.asMap().forEach((t, v) -> take(t, v * MILLI));
        return true;
    }

    public void refund(Cost cost, double fraction) {
        cost.asMap().forEach((t, v) -> add(t, Math.round(v * MILLI * fraction)));
    }

    /** Called at every hour boundary after the hour's production and consumption. */
    public void rotateHour() {
        hourIndex = (hourIndex + 1) % 24;
        for (int i = 0; i < TYPES; i++) {
            produced[i][hourIndex] = 0;
            consumed[i][hourIndex] = 0;
        }
        filledHours = Math.min(24, filledHours + 1);
    }

    /** Production over the last 24 hours (scaled up while fewer hours have passed), whole units per day. */
    public double producedPerDay(ResourceType t) {
        return perDay(produced[t.ordinal()]);
    }

    public double consumedPerDay(ResourceType t) {
        return perDay(consumed[t.ordinal()]);
    }

    private double perDay(long[] buckets) {
        int hours = Math.min(23, filledHours - 1);
        if (hours <= 0) {
            return 0;
        }
        long sum = 0;
        for (int back = 1; back <= hours; back++) {
            sum += buckets[Math.floorMod(hourIndex - back, 24)];
        }
        return (sum / (double) MILLI) * 24.0 / hours;
    }

    /** Days until empty at the current net rate, or -1 if the stock is not shrinking. */
    public double daysLeft(ResourceType t) {
        double net = producedPerDay(t) - consumedPerDay(t);
        if (net >= -1e-6) {
            return -1;
        }
        return amount(t) / -net;
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        Map<String, Object> amounts = Tree.map();
        for (ResourceType t : ResourceType.values()) {
            amounts.put(t.id(), amount[t.ordinal()]);
        }
        m.put("amounts", amounts);
        List<Object> prod = new ArrayList<>();
        List<Object> cons = new ArrayList<>();
        for (int i = 0; i < TYPES; i++) {
            for (int k = 0; k < 24; k++) {
                prod.add(produced[i][k]);
                cons.add(consumed[i][k]);
            }
        }
        m.put("produced", prod);
        m.put("consumed", cons);
        m.put("hour", hourIndex);
        m.put("filled", filledHours);
        return m;
    }

    /** Capacities must be set (from warehouses) before loading so amounts clamp correctly. */
    public void load(Map<String, Object> m) {
        Map<String, Object> amounts = Tree.map(m, "amounts");
        for (ResourceType t : ResourceType.values()) {
            long v = Tree.l(amounts, t.id(), 0L, 0L, Long.MAX_VALUE / 4);
            amount[t.ordinal()] = v;
        }
        List<Object> prod = Tree.list(m, "produced");
        List<Object> cons = Tree.list(m, "consumed");
        for (int i = 0; i < TYPES; i++) {
            for (int k = 0; k < 24; k++) {
                int idx = i * 24 + k;
                produced[i][k] = idx < prod.size() && prod.get(idx) instanceof Number n ? Math.max(0, n.longValue()) : 0;
                consumed[i][k] = idx < cons.size() && cons.get(idx) instanceof Number n ? Math.max(0, n.longValue()) : 0;
            }
        }
        hourIndex = Tree.i(m, "hour", 0, 0, 23);
        filledHours = Tree.i(m, "filled", 1, 1, 24);
    }

    /** Clamps amounts to the current capacities (after loading and capacity recalculation). */
    public void clampToCapacity() {
        for (int i = 0; i < TYPES; i++) {
            if (amount[i] > capacity[i]) {
                Tree.repaired("resource over capacity");
                amount[i] = capacity[i];
            }
            if (amount[i] < 0) {
                amount[i] = 0;
            }
        }
    }
}
