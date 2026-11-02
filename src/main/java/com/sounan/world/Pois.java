package com.sounan.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Points of interest of the mountain (tools/terrain/terrain.py → data/sounan/terrain/pois.json). Read-only. */
public final class Pois {
    public record Point(String id, int x, int y, int z, Direction facing, JsonObject json) {
        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public Vec3 center() {
            return new Vec3(x + 0.5, y, z + 0.5);
        }

        public int intValue(String key, int def) {
            return json.has(key) ? json.get(key).getAsInt() : def;
        }
    }

    private static volatile Pois instance;

    public final Map<String, Point> points;
    public final List<Point> checkpoints;
    public final List<int[]> ribbons;
    public final List<Point> memos;
    public final List<Point> jizo;
    public final List<Point> signs;
    public final double fogCx, fogCz, fogNx, fogNz, fogDepth;
    public final double cliffCx, cliffCz, cliffNx, cliffNz;
    public final double gorgeZ, gorgeHalf;
    public final int gorgeBottom;
    public final double tunnelX, tunnelZ0, tunnelZ1;
    public final List<int[]> route;

    private Pois(JsonObject root) {
        Map<String, Point> pts = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("points")) {
            Point p = point(e.getAsJsonObject());
            pts.put(p.id(), p);
        }
        points = Collections.unmodifiableMap(pts);
        checkpoints = list(root.getAsJsonArray("checkpoints"));
        memos = list(root.getAsJsonArray("memos"));
        jizo = list(root.getAsJsonArray("jizo"));
        signs = list(root.getAsJsonArray("signs"));
        List<int[]> rib = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("ribbons")) {
            JsonArray a = e.getAsJsonArray();
            rib.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
        }
        ribbons = Collections.unmodifiableList(rib);
        List<int[]> r = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("route")) {
            JsonArray a = e.getAsJsonArray();
            r.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
        }
        route = Collections.unmodifiableList(r);
        JsonObject fog = root.getAsJsonObject("fog");
        fogCx = fog.get("cx").getAsDouble();
        fogCz = fog.get("cz").getAsDouble();
        fogNx = fog.get("nx").getAsDouble();
        fogNz = fog.get("nz").getAsDouble();
        fogDepth = fog.get("depth").getAsDouble();
        JsonObject cliff = root.getAsJsonObject("cliff");
        cliffCx = cliff.get("cx").getAsDouble();
        cliffCz = cliff.get("cz").getAsDouble();
        cliffNx = cliff.get("nx").getAsDouble();
        cliffNz = cliff.get("nz").getAsDouble();
        JsonObject gorge = root.getAsJsonObject("gorge");
        gorgeZ = gorge.get("z").getAsDouble();
        gorgeHalf = gorge.get("half").getAsDouble();
        gorgeBottom = gorge.get("bottom").getAsInt();
        JsonObject tunnel = root.getAsJsonObject("tunnel");
        tunnelX = tunnel.get("x").getAsDouble();
        tunnelZ0 = tunnel.get("z0").getAsDouble();
        tunnelZ1 = tunnel.get("z1").getAsDouble();
    }

    private static Point point(JsonObject o) {
        String id = o.has("id") ? o.get("id").getAsString() : (o.has("note") ? "memo" + o.get("note").getAsInt() : "spot");
        Direction facing = Direction.NORTH;
        if (o.has("facing")) {
            Direction d = Direction.byName(o.get("facing").getAsString());
            if (d != null && d.getAxis().isHorizontal()) facing = d;
        }
        return new Point(id, o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(), facing, o);
    }

    private static List<Point> list(JsonArray a) {
        List<Point> out = new ArrayList<>();
        for (JsonElement e : a) out.add(point(e.getAsJsonObject()));
        return Collections.unmodifiableList(out);
    }

    public static Pois get() {
        Pois p = instance;
        if (p == null) {
            synchronized (Pois.class) {
                p = instance;
                if (p == null) {
                    p = load();
                    instance = p;
                }
            }
        }
        return p;
    }

    private static Pois load() {
        try (InputStream in = Pois.class.getResourceAsStream("/data/sounan/terrain/pois.json")) {
            if (in == null) throw new IllegalStateException("pois.json is missing from the jar");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Pois(root);
        } catch (Exception e) {
            throw new IllegalStateException("[sounan] could not load pois.json", e);
        }
    }

    public Point point(String id) {
        Point p = points.get(id);
        if (p == null) throw new IllegalArgumentException("no poi " + id);
        return p;
    }

    @Nullable
    public Point checkpoint(String id) {
        for (Point p : checkpoints) if (p.id().equals(id)) return p;
        return null;
    }

    /** Signed distance into the shrine fog band (0..depth = inside the band). */
    public double fogDepthAt(double x, double z) {
        return (x - fogCx) * fogNx + (z - fogCz) * fogNz;
    }

    public boolean inFogBand(double x, double z) {
        double d = fogDepthAt(x, z);
        return d > 0.0 && d < fogDepth;
    }

    /** Signed distance from the cliff line (positive = below the cliff). */
    public double cliffSide(double x, double z) {
        return (x - cliffCx) * cliffNx + (z - cliffCz) * cliffNz;
    }

    public boolean inTunnel(double x, double z) {
        return Math.abs(x - tunnelX) <= 4.5 && z >= tunnelZ0 - 1 && z <= tunnelZ1 + 1;
    }
}
