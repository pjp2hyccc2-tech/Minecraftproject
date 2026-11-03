package com.reisatsu.world;

import com.reisatsu.Reisatsu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where everything is in the restaurant HIDAMARI (docs/DESIGN.md §2). Owner: world. Everybody else only reads it.
 * <p>
 * Markers are feet positions (the block the entity stands in) plus a facing. Single markers are lists of one.
 * The world fills in the final positions to match what it builds; names never change.
 */
public final class Layout {
    /** The restaurant's dimension. */
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Reisatsu.id("hidamari"));
    /** Feet level inside the restaurant and on the parking lot (floor blocks at FEET_Y - 1). */
    public static final int FEET_Y = 65;

    public record Marker(BlockPos pos, Direction facing) {
        public Vec3 center() {
            return Vec3.atBottomCenterOf(pos);
        }

        public float yaw() {
            return facing.toYRot();
        }
    }

    /** A room: key (docs §2 table), Japanese name, box (block coordinates, max exclusive), acoustics preset (§10). */
    public record Room(String key, String nameJa, AABB box, String acoustics) {
        public boolean contains(BlockPos pos) {
            return box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
    }

    private static final Map<String, List<Marker>> MARKERS = new LinkedHashMap<>();
    private static final List<Room> ROOMS = new ArrayList<>();

    private Layout() {}

    /** The single marker with this name. Throws for unknown names (typos fail fast). */
    public static Marker marker(String name) {
        List<Marker> list = markers(name);
        if (list.isEmpty()) throw new IllegalArgumentException("empty marker list: " + name);
        return list.get(0);
    }

    /** All markers with this name (routes, hiding spots, buttons...). Throws for unknown names. */
    public static List<Marker> markers(String name) {
        List<Marker> list = MARKERS.get(name);
        if (list == null) throw new IllegalArgumentException("unknown layout marker: " + name);
        return list;
    }

    public static boolean has(String name) {
        return MARKERS.containsKey(name);
    }

    public static Map<String, List<Marker>> all() {
        return Collections.unmodifiableMap(MARKERS);
    }

    public static List<Room> rooms() {
        return Collections.unmodifiableList(ROOMS);
    }

    /** The room containing this block, or null (outside / walls). */
    public static Room roomAt(BlockPos pos) {
        for (Room r : ROOMS) if (r.contains(pos)) return r;
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Data. Placeholder positions from the design's floor plan; the world replaces them with the built ones.

    private static void room(String key, String ja, int x0, int z0, int x1, int z1, String acoustics) {
        ROOMS.add(new Room(key, ja, new AABB(x0, FEET_Y - 1, z0, x1 + 1, FEET_Y + 4, z1 + 1), acoustics));
    }

    private static void m(String name, int x, int y, int z, Direction facing) {
        MARKERS.computeIfAbsent(name, k -> new ArrayList<>()).add(new Marker(new BlockPos(x, y, z), facing));
    }

    private static void m(String name, int x, int z, Direction facing) {
        m(name, x, FEET_Y, z, facing);
    }

    static {
        room("vestibule", "風除室", 1, 27, 7, 29, "small_hard");
        room("waiting", "待合・レジ", 1, 23, 14, 25, "medium");
        room("waiting", "待合・レジ", 9, 26, 14, 29, "medium");
        room("dining", "客席", 15, 11, 39, 29, "dining");
        room("fridge", "冷蔵室", 15, 1, 18, 3, "tiny_dead");
        room("kitchen", "厨房", 15, 1, 39, 9, "kitchen");
        room("corridor", "通路", 1, 8, 13, 9, "corridor");
        room("staff", "休憩室", 1, 1, 6, 6, "small_soft");
        room("office", "事務所", 8, 1, 13, 6, "small_soft");
        room("storage", "倉庫", 1, 11, 5, 15, "small_hard");
        room("mens", "男子トイレ", 1, 17, 5, 21, "restroom");
        room("womens", "女子トイレ", 7, 17, 13, 21, "restroom");
        room("cavity", "鏡の向こう", 7, 11, 13, 15, "restroom");

        Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;
        m("spawn", 5, 40, N);
        m("medium", 2, 40, E);
        m("medium_car", -3, 41, N);
        m("wake", 3, 42, N);
        m("door_outer", 3, 30, S);
        m("door_inner", 3, 26, S);
        m("door_switch", 6, 31, S);
        for (int n = 1; n <= 7; n++) m("table_" + n, 13 + 3 * n, 28, N);
        for (int n = 8; n <= 11; n++) m("table_" + n, 38, 26 - 3 * (n - 8), N);
        for (int n = 12; n <= 17; n++) m("table_" + n, 20 + 5 * ((n - 12) % 3), (n - 12) < 3 ? 17 : 23, N);
        for (int n = 1; n <= 17; n++) {
            Marker t = MARKERS.get("table_" + n).get(0);
            m("button_" + n, t.pos().getX(), FEET_Y + 1, t.pos().getZ(), N);
        }
        m("seat_7", 33, 28, E);
        m("call_display", 12, FEET_Y + 2, 23, S);
        m("register", 12, FEET_Y + 1, 24, W);
        m("phone", 13, FEET_Y + 1, 24, W);
        m("visitor_spawn", 6, 46, N);
        int[][] vp = {{4, 36}, {4, 32}, {3, 30}, {3, 28}, {3, 26}, {5, 24}, {14, 26}, {24, 26}, {32, 27}, {33, 28}};
        for (int[] p : vp) m("visitor_path", p[0], p[1], N);
        int[][] wr = {{16, 26}, {24, 26}, {31, 26}, {36, 24}, {36, 19}, {36, 13}, {30, 14}, {23, 14}, {17, 15}, {17, 21}};
        for (int[] p : wr) m("waitress_route", p[0], p[1], E);
        m("waitress_station", 33, 12, N);
        m("waitress_bow", 4, 24, S);
        m("child_count", 12, 27, S);
        int[][] ch = {{19, 29}, {13, 28}, {11, 25}, {16, 12}, {25, 20}, {3, 13}};
        for (int[] p : ch) m("child_hides", p[0], p[1], N);
        m("chef_board", 25, 5, N);
        m("chef_stove", 25, 2, N);
        m("chef_pass", 24, 9, S);
        m("pass_bell", 24, FEET_Y + 1, 10, S);
        m("mirror_woman", 10, 13, S);
        for (int x = 9; x <= 11; x++) m("mirror", x, FEET_Y + 1, 16, S);
        for (int x = 9; x <= 11; x++) m("sink_w", x, 17, N);
        m("sink_m", 2, 17, N);
        m("sink_kitchen", 37, 2, N);
        m("manager_desk", 10, 3, W);
        int[][] mr = {{10, 3}, {10, 8}, {14, 9}, {20, 7}, {30, 7}, {30, 12}, {25, 20}, {16, 25}, {6, 24}, {12, 8}};
        for (int[] p : mr) m("manager_route", p[0], p[1], S);
        m("breaker", 7, FEET_Y + 1, 7, S);
        int[][] cams = {{15, 23}, {39, 11}, {39, 29}, {22, 8}, {6, 9}, {4, 29}};
        for (int[] p : cams) m("cameras", p[0], FEET_Y + 3, p[1], S);
        m("monitors", 8, FEET_Y + 1, 3, E);
        for (int z = 2; z <= 4; z++) m("lockers", 1, z, E);
        m("stall_occupied", 12, 21, N);
        m("tv", 6, 1, S);
        m("hand_dryer_w", 13, FEET_Y + 1, 19, W);
        m("hand_dryer_m", 5, FEET_Y + 1, 19, W);
        m("drink_bar", 17, 11, S);
        m("fryer", 30, 1, S);
        m("gas_range", 25, 1, S);
        m("dishwasher", 37, 6, S);
        m("fridge_door", 17, 4, S);
        m("stuffed_bear", 13, 29, N);
        m("menu_board", 7, 33, S);
        m("curve_mirror", 70, 49, W);
        m("flowers", 72, 49, S);
        m("film_1", 12, 24, W);
        m("film_2", 1, 3, E);
        m("film_3", 16, 2, S);
        m("key_office", 2, 6, N);
        m("exorcism_medium", 34, 27, S);
        int[][] ex = {{32, 27}, {36, 27}, {31, 28}, {37, 28}, {33, 26}, {35, 26}};
        for (int[] p : ex) m("exorcism_spirits", p[0], p[1], S);
        m("exorcism_player", 30, 25, E);
    }
}
