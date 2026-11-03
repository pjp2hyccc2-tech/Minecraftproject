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
 * Markers are feet positions (the block the entity stands in) plus a facing; markers of things (buttons, the
 * register, cameras, mirror blocks, notes...) are the block positions of those things. Single markers are lists of one.
 * These are the positions the world actually builds ({@link RestaurantBuilder}, {@link ExteriorBuilder}).
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

    /** The first room with this key (rooms may consist of several boxes). */
    public static Room room(String key) {
        for (Room r : ROOMS) if (r.key().equals(key)) return r;
        throw new IllegalArgumentException("unknown room: " + key);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Data: the built positions.

    private static void room(String key, String ja, int x0, int z0, int x1, int z1, String acoustics) {
        ROOMS.add(new Room(key, ja, new AABB(x0, FEET_Y - 1, z0, x1 + 1, FEET_Y + 4, z1 + 1), acoustics));
    }

    private static void m(String name, int x, int y, int z, Direction facing) {
        MARKERS.computeIfAbsent(name, k -> new ArrayList<>()).add(new Marker(new BlockPos(x, y, z), facing));
    }

    private static void m(String name, int x, int z, Direction facing) {
        m(name, x, FEET_Y, z, facing);
    }

    /** x of the table of south booth n (1..7); its blocks are z 28 (aisle end) and 29 (window end). */
    public static int boothX(int n) {
        return 16 + 3 * (n - 1);
    }

    /** z of the table of east booth n (8..11); its blocks are x 38 (aisle end) and 39 (window end). */
    public static int eastBoothZ(int n) {
        return 24 - 3 * (n - 8);
    }

    /** West block x of centre table n (12..17); the tables are two blocks long (x0, x0+1). */
    public static int centreX(int n) {
        return 19 + 6 * ((n - 12) % 3);
    }

    public static int centreZ(int n) {
        return n < 15 ? 15 : 22;
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
        // ---- outside
        m("spawn", 4, 41, N);
        m("medium", -1, 39, E);
        m("medium_car", -3, 36, N);
        m("wake", 0, 37, N);
        m("visitor_spawn", 40, 47, N);
        // ---- doors (the left panel's bottom block of each pair; the opening is x 3..4)
        m("door_outer", 3, 30, S);
        m("door_inner", 3, 26, S);
        m("door_switch", 5, FEET_Y + 1, 31, S);
        // ---- tables (the table block at the aisle end) and their call buttons (on the table)
        for (int n = 1; n <= 7; n++) m("table_" + n, boothX(n), 28, N);
        for (int n = 8; n <= 11; n++) m("table_" + n, 38, eastBoothZ(n), W);
        for (int n = 12; n <= 17; n++) m("table_" + n, centreX(n), centreZ(n), N);
        for (int n = 1; n <= 7; n++) m("button_" + n, boothX(n), FEET_Y + 1, 28, N);
        for (int n = 8; n <= 11; n++) m("button_" + n, 38, FEET_Y + 1, eastBoothZ(n), W);
        for (int n = 12; n <= 17; n++) m("button_" + n, centreX(n) + 1, FEET_Y + 1, centreZ(n), N);
        m("seat_7", 33, 28, E);
        m("call_display", 13, FEET_Y + 3, 23, S);
        m("register", 12, FEET_Y + 1, 23, W);
        m("phone", 14, FEET_Y + 1, 23, W);
        // ---- the visitor: from in front of the entrance through both doors to his seat
        int[][] vp = {{4, 35}, {4, 32}, {4, 28}, {4, 24}, {9, 24}, {14, 25}, {20, 26}, {27, 26}, {33, 27}, {33, 28}};
        for (int[] p : vp) m("visitor_path", p[0], p[1], p[0] == 33 && p[1] == 28 ? E : (p[1] > 30 ? N : (p[0] < 5 ? N : E)));
        // ---- the waitress: a loop through the dining aisles
        int[][] wr = {{16, 13}, {21, 12}, {27, 13}, {33, 13}, {36, 15}, {36, 20}, {36, 25}, {31, 26}, {24, 26}, {17, 26}, {16, 21}, {16, 16}};
        Direction[] wf = {E, E, E, E, S, S, S, W, W, W, N, N};
        for (int i = 0; i < wr.length; i++) m("waitress_route", wr[i][0], wr[i][1], wf[i]);
        m("waitress_station", 32, 12, N);
        m("waitress_bow", 6, 25, W);
        // ---- the child
        m("child_count", 12, 27, N);
        int[][] ch = {{22, 28}, {9, 29}, {13, 23}, {20, 11}, {26, 19}, {2, 14}, {5, 18}};
        Direction[] cf = {N, E, W, S, N, E, S};
        for (int i = 0; i < ch.length; i++) m("child_hides", ch[i][0], ch[i][1], cf[i]);
        // ---- the chef
        m("chef_board", 25, 6, N);
        m("chef_stove", 25, 2, N);
        m("chef_pass", 24, 9, S);
        m("pass_bell", 24, FEET_Y + 1, 10, N);   // the bell's own segment of the pass shelf, in the pass window
        // ---- the mirror woman
        m("mirror_woman", 10, 14, S);
        for (int y = FEET_Y + 1; y <= FEET_Y + 2; y++) for (int x = 9; x <= 11; x++) m("mirror", x, y, 16, S);
        for (int x = 9; x <= 11; x++) m("sink_w", x, 17, S);
        m("sink_m", 1, 19, E);
        m("sink_m", 1, 20, E);
        m("sink_kitchen", 35, 1, S);
        m("sink_kitchen", 36, 1, S);
        // ---- the manager
        m("manager_desk", 9, 6, W);
        int[][] mr = {{10, 5}, {11, 6}, {11, 8}, {13, 8}, {16, 8}, {22, 8}, {28, 8}, {29, 9}, {29, 11}, {24, 13}, {16, 15}, {16, 24},
                {9, 24}, {5, 24}, {12, 25}, {22, 26}, {32, 26}, {36, 20}, {34, 12}, {30, 11}, {30, 8}, {20, 7}, {13, 9}, {11, 8}};
        for (int[] p : mr) m("manager_route", p[0], p[1], S);
        m("breaker", 7, FEET_Y + 1, 8, S);       // on the corridor wall (the wall cell E of the plan is behind it)
        m("breaker_stand", 7, 9, N);
        int[][] cams = {{14, 25}, {15, 12}, {38, 12}, {37, 27}, {39, 8}, {13, 9}, {7, 28}};
        Direction[] camf = {W, S, W, W, W, W, W};
        for (int i = 0; i < cams.length; i++) m("cameras", cams[i][0], FEET_Y + 3, cams[i][1], camf[i]);
        m("monitors", 8, FEET_Y + 1, 5, E);
        m("monitors", 8, FEET_Y + 1, 6, E);
        m("monitors", 8, FEET_Y + 2, 5, E);
        m("monitors", 8, FEET_Y + 2, 6, E);
        for (int z = 1; z <= 4; z++) m("lockers", 1, z, E);
        m("stall_occupied", 8, 21, W);
        m("tv", 6, 1, S);
        m("hand_dryer_w", 13, FEET_Y + 1, 19, W);
        m("hand_dryer_m", 5, FEET_Y + 1, 20, W);
        m("drink_bar", 17, FEET_Y + 1, 11, S);
        m("drink_bar", 18, FEET_Y + 1, 11, S);
        m("fryer", 28, 1, S);
        m("fryer", 29, 1, S);
        m("gas_range", 25, 1, S);
        m("gas_range", 26, 1, S);
        m("gas_range", 21, 1, S);
        m("gas_range", 22, 1, S);
        m("dishwasher", 39, 3, W);
        m("fridge_door", 17, 4, N);
        m("stuffed_bear", 13, 29, N);
        m("menu_board", 1, 32, S);
        m("curve_mirror", 66, 59, W);           // a two-block model: pole and mirror
        m("flowers", 76, 52, N);
        m("film_1", 12, FEET_Y + 1, 23, W);
        m("film_2", 1, 4, E);
        m("film_3", 17, 2, N);
        m("key_office", 4, FEET_Y + 1, 4, N);
        // ---- notes (§3.11): note_paper blocks or the book / boards that hold them; report + medium are handed over
        m("note_report", -1, 39, E);
        m("note_medium", -1, 39, E);
        m("note_handover", 6, FEET_Y + 1, 5, W);
        m("note_clipping", 6, FEET_Y + 1, 3, W);
        m("note_reservation", 11, FEET_Y + 1, 23, S);
        m("note_diary", 11, FEET_Y + 1, 1, S);
        m("note_drawing", 9, 27, E);
        m("note_flyer", 1, FEET_Y + 1, 28, E);
        m("note_fire", 15, FEET_Y + 1, 5, E);
        m("note_cleaning", 12, FEET_Y + 1, 17, S);
        // ---- the exorcism at table 7
        m("exorcism_medium", 34, 27, S);
        int[][] ex = {{33, 27}, {35, 27}, {32, 26}, {36, 26}, {31, 27}, {37, 27}};
        for (int[] p : ex) m("exorcism_spirits", p[0], p[1], S);
        m("exorcism_player", 34, 23, S);
    }

    /** Table number of a call button / table block position, or 0. */
    public static int tableAt(BlockPos pos) {
        for (int n = 1; n <= 17; n++) {
            if (marker("button_" + n).pos().equals(pos)) return n;
            if (marker("table_" + n).pos().equals(pos)) return n;
        }
        return 0;
    }
}
