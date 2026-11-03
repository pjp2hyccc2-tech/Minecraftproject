package com.hitori.world;

import com.hitori.Hitori;
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
 * Where everything is in コーポ白樺 203 (docs/DESIGN.md §2). Owner: world. Everybody else only reads it.
 * Markers are feet positions plus a facing; single markers are lists of one. The world fills in the final positions to
 * match what it builds; names never change.
 */
public final class Layout {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Hitori.id("shirakaba"));
    /** Feet level inside room 203 (second floor; floor blocks at FEET_Y - 1). */
    public static final int FEET_Y = 70;
    /** Feet level on the street / ground floor. */
    public static final int GROUND_Y = 65;

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

    public static Marker marker(String name) {
        List<Marker> list = markers(name);
        if (list.isEmpty()) throw new IllegalArgumentException("empty marker list: " + name);
        return list.get(0);
    }

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

    /** The room containing this block, or null. */
    public static Room roomAt(BlockPos pos) {
        for (Room r : ROOMS) if (r.contains(pos)) return r;
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Placeholder data from the design's floor plan; the world replaces it with the built positions.

    private static void room(String key, String ja, int x0, int z0, int x1, int z1, int feetY, String acoustics) {
        ROOMS.add(new Room(key, ja, new AABB(x0, feetY - 1, z0, x1 + 1, feetY + 3, z1 + 1), acoustics));
    }

    private static void m(String name, int x, int y, int z, Direction facing) {
        MARKERS.computeIfAbsent(name, k -> new ArrayList<>()).add(new Marker(new BlockPos(x, y, z), facing));
    }

    private static void m(String name, int x, int z, Direction facing) {
        m(name, x, FEET_Y, z, facing);
    }

    static {
        int y = FEET_Y;
        room("closet", "押し入れ", 23, 10, 23, 12, y, "tiny_dead");
        room("genkan", "玄関", 23, 1, 25, 2, y - 1, "small_hard");
        room("hall", "廊下", 26, 1, 27, 7, y, "corridor");
        room("toilet", "トイレ", 29, 1, 29, 2, y, "restroom");
        room("bath", "浴室", 31, 1, 32, 2, y, "restroom");
        room("washroom", "脱衣所", 29, 4, 32, 5, y, "small_hard");
        room("dk", "DK", 23, 4, 25, 7, y, "small_hard");
        room("tatami", "和室", 23, 9, 27, 12, y, "small_soft");
        room("western", "洋室", 29, 7, 32, 12, y, "small_soft");

        Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;
        m("spawn", 30, 8, N);
        m("door_front", 24, 1, N);
        m("peephole", 24, 1, N);
        m("breaker", 23, y + 1, 1, E);
        m("tv", 27, 9, W);
        m("table", 25, 11, N);
        m("bathtub", 32, 1, W);
        m("bathtub", 32, 2, W);
        m("bath_tap", 32, y + 1, 1, W);
        m("washbasin", 32, 5, W);
        m("kitchen_sink", 23, 5, E);
        m("rice", 23, 7, E);
        m("salt", 23, y + 1, 6, E);
        m("cup", 23, y + 1, 5, E);
        m("knife", 23, y + 1, 4, E);
        m("lighter", 25, 7, N);
        m("scissors", 32, y + 1, 8, W);
        m("thread", 32, y + 1, 8, W);
        m("nail_clipper", 32, y + 1, 4, W);
        m("plush_shelf", 29, y + 1, 12, E);
        m("camera_bag", 29, 11, E);
        m("laptop", 32, y + 1, 8, W);
        m("phone", 31, y + 1, 8, W);
        m("printout", 32, y + 1, 9, W);
        for (String r : new String[]{"genkan", "hall", "toilet", "bath", "washroom", "dk", "tatami", "western"}) m("switch_" + r, 26, y + 1, 3, W);
        m("lights_tatami", 25, y + 2, 10, N);
        m("lights_western", 30, y + 2, 9, N);
        m("lights_dk", 24, y + 2, 5, N);
        m("lights_hall", 26, y + 2, 4, N);
        m("lights_bath", 31, y + 2, 1, N);
        m("lights_washroom", 30, y + 2, 4, N);
        m("lights_toilet", 29, y + 2, 1, N);
        m("lights_genkan", 24, y + 2, 1, N);
        for (int z = 10; z <= 12; z++) m("closet_hide", 23, z, E);
        for (int z = 10; z <= 12; z++) m("closet_fusuma", 24, z, E);
        m("closet_peek", 23, 11, E);
        m("western_closet", 32, 11, W);
        m("cam_suggest", 30, y + 1, 2, E);
        m("cam_suggest", 26, y + 1, 1, S);
        m("cam_suggest", 27, y + 1, 12, W);
        m("cam_suggest", 25, y + 1, 2, W);
        int[][] route = {{31, 2}, {30, 4}, {27, 5}, {24, 6}, {24, 9}, {26, 11}, {27, 6}, {30, 9}, {31, 11}, {26, 2}, {24, 2}};
        for (int[] p : route) m("doll_route", p[0], p[1], N);
        int[][] hides = {{25, 11}, {29, 5}, {30, 3}, {25, 6}, {31, 12}, {25, 14}};
        for (int[] p : hides) m("doll_hides", p[0], p[1], N);
        int[][] woman = {{23, 9}, {32, 7}, {23, 4}, {32, 4}};
        for (int[] p : woman) m("woman_spots", p[0], p[1], S);
        m("balcony", 25, 14, N);
        m("intercom", 25, y + 1, 1, W);
        m("morning", 31, 8, N);
    }
}
