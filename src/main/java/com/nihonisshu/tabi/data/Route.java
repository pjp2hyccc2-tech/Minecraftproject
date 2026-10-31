package com.nihonisshu.tabi.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A train or bus line: a dense polyline (rail top / road surface) with stations placed by distance s. */
public final class Route {
    public final String id, name, type, design;
    /** line colour (0xRRGGBB) used for the in-car display; up/down direction labels ("〇〇方面") */
    public final int color;
    public final String towardEnd, towardStart;
    public final int cars;
    public final double carSpacing, maxSpeed, fareBase, farePer100;
    private final double[] xs, ys, zs, cum;
    public final double length;
    public final List<Station> stations = new ArrayList<>();

    public static final class Station {
        public final int index;
        public final String id, name, kana, en;
        public final double s;
        public final Vec3 exit;
        public final boolean terminal;
        /** sound id played on arrival (tabi:<melody>), door side relative to +s ("L"/"R"), transfer guidance */
        public final String melody, door, transfer;

        Station(int index, JsonObject o, boolean terminal) {
            this.index = index;
            this.id = TabiData.str(o, "id", "st" + index);
            this.name = TabiData.str(o, "name", id);
            this.kana = TabiData.str(o, "kana", "");
            this.en = TabiData.str(o, "en", "");
            this.s = o.get("s").getAsDouble();
            JsonArray e = o.getAsJsonArray("exit");
            this.exit = new Vec3(e.get(0).getAsDouble(), e.get(1).getAsDouble(), e.get(2).getAsDouble());
            this.terminal = terminal;
            this.melody = TabiData.str(o, "melody", "");
            this.door = TabiData.str(o, "door", "");
            this.transfer = TabiData.str(o, "transfer", "");
        }
    }

    /** a point along the route: scenic guide announcement or horn */
    public static final class Event {
        public final double s;
        public final String type, text, journal;

        Event(JsonObject o) {
            s = o.get("s").getAsDouble();
            type = TabiData.str(o, "type", "guide");
            text = TabiData.str(o, "text", "");
            journal = TabiData.str(o, "journal", "");
        }
    }

    public final List<Event> events = new ArrayList<>();

    private Route(JsonObject o) {
        id = TabiData.str(o, "id", "route");
        name = TabiData.str(o, "name", id);
        type = TabiData.str(o, "type", "train");
        design = TabiData.str(o, "design", "dmu");
        int c;
        try {
            c = Integer.parseInt(TabiData.str(o, "color", "#2FA05A").replace("#", ""), 16);
        } catch (NumberFormatException ex) {
            c = 0x2FA05A;
        }
        color = c;
        towardEnd = TabiData.str(o, "toward_end", "");
        towardStart = TabiData.str(o, "toward_start", "");
        cars = TabiData.integer(o, "cars", 2);
        carSpacing = TabiData.dbl(o, "car_spacing", 20.5);
        maxSpeed = TabiData.dbl(o, "max_speed", 0.8);
        fareBase = TabiData.dbl(o, "fare_base", 150);
        farePer100 = TabiData.dbl(o, "fare_per_100", 30);
        JsonArray pts = o.getAsJsonArray("points");
        int n = pts.size();
        xs = new double[n];
        ys = new double[n];
        zs = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            JsonArray p = pts.get(i).getAsJsonArray();
            xs[i] = p.get(0).getAsDouble();
            ys[i] = p.get(1).getAsDouble();
            zs[i] = p.get(2).getAsDouble();
            if (i > 0) {
                double dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1], dz = zs[i] - zs[i - 1];
                cum[i] = cum[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        length = n > 0 ? cum[n - 1] : 0;
        JsonArray evs = o.getAsJsonArray("events");
        if (evs != null) for (com.google.gson.JsonElement e : evs) events.add(new Event(e.getAsJsonObject()));
        JsonArray st = o.getAsJsonArray("stations");
        for (int i = 0; i < st.size(); i++) {
            stations.add(new Station(i, st.get(i).getAsJsonObject(), i == 0 || i == st.size() - 1));
        }
    }

    public Vec3 pos(double s) {
        int n = xs.length;
        if (n == 0) return Vec3.ZERO;
        if (s <= 0) return new Vec3(xs[0], ys[0], zs[0]);
        if (s >= length) return new Vec3(xs[n - 1], ys[n - 1], zs[n - 1]);
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] <= s) lo = mid;
            else hi = mid;
        }
        double seg = cum[hi] - cum[lo];
        double t = seg <= 1e-9 ? 0 : (s - cum[lo]) / seg;
        return new Vec3(xs[lo] + (xs[hi] - xs[lo]) * t, ys[lo] + (ys[hi] - ys[lo]) * t, zs[lo] + (zs[hi] - zs[lo]) * t);
    }

    /** next station strictly ahead of s in direction dir, or null. */
    public Station nextStation(double s, int dir) {
        Station best = null;
        for (Station st : stations) {
            double d = (st.s - s) * dir;
            if (d > 0.01 && (best == null || d < (best.s - s) * dir)) best = st;
        }
        return best;
    }

    public Station nearestStation(Vec3 p, double maxDist) {
        Station best = null;
        double bd = maxDist * maxDist;
        for (Station st : stations) {
            double d = pos(st.s).distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = st;
            }
        }
        return best;
    }

    public int fare(Station a, Station b) {
        double d = Math.abs(a.s - b.s);
        int f = (int) Math.round(fareBase + farePer100 * Math.ceil(d / 100.0));
        return Math.round(f / 10f) * 10;
    }

    // ---------------------------------------------------------------- registry

    private static Map<String, Route> ROUTES;

    public static synchronized Map<String, Route> map() {
        if (ROUTES == null) {
            ROUTES = new LinkedHashMap<>();
            JsonObject meta = TabiData.meta();
            JsonArray arr = meta.getAsJsonArray("routes");
            if (arr != null) {
                for (JsonElement e : arr) {
                    Route r = new Route(e.getAsJsonObject());
                    ROUTES.put(r.id, r);
                }
            }
        }
        return ROUTES;
    }

    public static Route get(String id) {
        return map().get(id);
    }

    public static Collection<Route> all() {
        return map().values();
    }
}
