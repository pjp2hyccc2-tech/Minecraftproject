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
 * Markers are positions plus a facing; single markers are lists of one. "Feet" markers are where something stands
 * (the block its feet are in); "block" markers are the block of the fixture itself. The positions are the ones
 * {@link ApartmentBuilder} builds (checked by {@code debug/WorldCheck}).
 * <p>
 * Heights: rooms of 203 have their floor blocks at y 69 (feet 70), the ceiling at y 73. The genkan and the outdoor
 * corridor are one step lower (feet 69); between the genkan and the hall there is a wooden half step (上がり框, x 25).
 * Wall-mounted things (switches, the breaker, the intercom, the knife rack...) are at y 71, ceiling lights at y 72.
 */
public final class Layout {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Hitori.id("shirakaba"));
    /** Feet level inside room 203 (second floor; floor blocks at FEET_Y - 1). */
    public static final int FEET_Y = 70;
    /** Feet level on the street / ground floor. */
    public static final int GROUND_Y = 65;
    /** Feet level of the genkan and the outdoor corridor of the second floor. */
    public static final int GENKAN_Y = 69;

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

    public static Room room(String key) {
        for (Room r : ROOMS) if (r.key().equals(key)) return r;
        return null;
    }

    /** The light rooms of 203 (each has {@code switch_<room>} and {@code lights_<room>}). */
    public static final String[] LIGHT_ROOMS = {"genkan", "hall", "toilet", "bath", "washroom", "dk", "tatami", "western"};

    /** Keys of {@link Fixtures#setDoor}. */
    public static final String[] DOORS = {"front", "toilet", "washroom", "bath", "western", "tatami_dk", "tatami_hall", "western_closet",
            "balcony_tatami", "balcony_western"};

    // ------------------------------------------------------------------------------------------------------------

    private static void room(String key, String ja, int x0, int z0, int x1, int z1, int y0, int y1, String acoustics) {
        ROOMS.add(new Room(key, ja, new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1), acoustics));
    }

    private static void m(String name, int x, int y, int z, Direction facing) {
        MARKERS.computeIfAbsent(name, k -> new ArrayList<>()).add(new Marker(new BlockPos(x, y, z), facing));
    }

    private static void m(String name, int x, int z, Direction facing) {
        m(name, x, FEET_Y, z, facing);
    }

    static {
        int y = FEET_Y;
        // rooms: from the floor block to the block under the ceiling (closets first: they are inside the bigger boxes)
        room("closet", "押し入れ", 23, 10, 23, 12, 69, 72, "tiny_dead");
        room("western_closet", "クローゼット", 32, 10, 32, 12, 69, 72, "tiny_dead");
        room("genkan", "玄関", 23, 1, 25, 2, 68, 72, "small_hard");
        room("hall", "廊下", 26, 1, 27, 7, 69, 72, "corridor");
        room("toilet", "トイレ", 29, 1, 29, 2, 69, 72, "restroom");
        room("bath", "浴室", 31, 1, 32, 2, 69, 72, "restroom");
        room("washroom", "脱衣所", 29, 4, 32, 5, 69, 72, "small_hard");
        room("dk", "DK", 23, 4, 25, 7, 69, 72, "small_hard");
        room("tatami", "和室", 24, 9, 27, 12, 69, 72, "small_soft");
        room("western", "洋室", 29, 7, 32, 12, 69, 72, "small_soft");
        room("balcony", "ベランダ", 23, 14, 32, 15, 69, 73, "outside");
        room("corridor", "外廊下", -1, -2, 44, -1, 68, 72, "outside");

        Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;
        int g = GENKAN_Y;
        // ---- the player
        m("spawn", 30, 8, E);                          // feet: in the western room by the desk, facing it
        m("morning", 31, 8, E);                        // block: the desk chair (the good ending's playback)
        // ---- the front door
        m("door_front", 24, g, 0, N);                  // block: the lower half of the front door (opens to the corridor, north)
        m("peephole", 24, g, -1, N);                   // feet: the eye of the peephole (the corridor cell outside; the door's face is at z 0)
        m("breaker", 23, y + 1, 1, E);                 // block: the breaker panel, high on the genkan's west wall
        m("intercom", 23, y, 2, E);                    // block: the intercom handset over the shoe cabinet
        // ---- the tatami room
        m("tv", 27, y + 1, 9, W);                      // block: the CRT (on its low stand at y 70); stand in front at (26, 70, 9)
        m("table", 25, y, 11, N);                      // block: the ちゃぶ台
        m("hanging_lamp", 25, y + 2, 11, N);           // block
        // ---- the bathroom
        m("bathtub", 32, y, 1, W);                     // blocks: head (z 1) and foot (z 2); the washing area is west of it
        m("bathtub", 32, y, 2, W);
        m("bath_tap", 32, y + 1, 1, S);                // block: the tub's tap on the north wall
        m("washbasin", 32, y, 5, W);                   // block
        m("kitchen_sink", 23, y, 5, E);                // block
        // ---- the ritual's things (blocks the player right-clicks)
        m("rice", 25, y, 4, S);                        // rice_bag on the DK floor
        m("salt", 24, y + 1, 4, S);                    // salt_shaker on the second drawer
        m("cup", 23, y + 1, 5, E);                     // cup_glass on the sink
        m("knife", 23, y + 1, 6, E);                   // knife_rack over the gas stove
        m("lighter", 23, y, 7, E);                     // the drawer by the stove
        m("scissors", 31, y + 1, 7, S);                // the sewing box on the desk
        m("thread", 31, y + 1, 7, S);
        m("nail_clipper", 32, y + 1, 5, W);            // the mirror cabinet over the washbasin
        m("plush_shelf", 29, y + 1, 10, E);            // plush_doll on the shelf
        m("camera_bag", 29, y, 9, E);
        m("laptop", 32, y + 1, 8, W);                  // laptop_block on the desk
        m("printout", 30, y + 1, 7, S);                // the printout on the desk
        m("phone", 30, y + 1, 7, S);                   // the phone starts on the desk next to the printout (desk top at y 70.75)
        // ---- other fixtures
        m("fridge", 23, y, 4, E);
        m("microwave", 23, y + 1, 4, E);
        m("gas_stove", 23, y, 6, E);
        m("rice_cooker", 23, y + 1, 7, E);
        m("washing_machine", 29, y, 4, S);
        m("desk_lamp", 32, y + 1, 7, W);
        m("wall_clock", 24, y + 2, 4, S);
        m("altar_shelf", 27, y, 12, W);
        m("mirror", 26, y + 1, 3, E);                  // the hall's mirror
        m("calendar", 27, y + 1, 6, W);
        // ---- light switches (blocks; the tatami room's is the lamp's pull-cord) and lamps
        m("switch_genkan", 25, y + 1, 1, S);
        m("switch_hall", 26, y + 1, 1, S);
        m("switch_toilet", 27, y + 1, 3, W);
        m("switch_washroom", 27, y + 1, 4, W);
        m("switch_bath", 30, y + 1, 4, S);
        m("switch_dk", 25, y + 1, 4, S);
        m("switch_western", 29, y + 1, 8, E);
        m("switch_tatami", 25, y + 1, 11, N);
        m("lights_genkan", 24, y + 2, 1, N);
        m("lights_hall", 26, y + 2, 3, N);
        m("lights_hall", 27, y + 2, 6, N);
        m("lights_toilet", 29, y + 2, 1, N);
        m("lights_bath", 32, y + 2, 2, N);
        m("lights_washroom", 30, y + 2, 5, N);
        m("lights_dk", 24, y + 2, 5, N);
        m("lights_tatami", 25, y + 2, 11, N);
        m("lights_western", 30, y + 2, 10, N);
        // ---- doors (lower blocks)
        m("door_toilet", 28, y, 2, E);
        m("door_washroom", 28, y, 5, E);
        m("door_western", 28, y, 7, E);
        m("door_bath", 31, y, 3, S);
        m("door_tatami_dk", 24, y, 8, N);
        m("door_tatami_dk", 25, y, 8, N);
        m("door_tatami_hall", 27, y, 8, N);
        m("door_western_closet", 32, y, 10, W);
        m("door_western_closet", 32, y, 11, W);
        m("door_western_closet", 32, y, 12, W);
        for (int x = 24; x <= 26; x++) m("door_balcony_tatami", x, y, 13, N);
        for (int x = 30; x <= 31; x++) m("door_balcony_western", x, y, 13, N);
        m("curtains_tatami", 24, y, 12, N);
        m("curtains_tatami", 25, y, 12, N);
        m("curtains_tatami", 26, y, 12, N);
        m("curtains_western", 30, y, 12, N);
        m("curtains_western", 31, y, 12, N);
        // ---- the closets
        for (int z = 10; z <= 12; z++) m("closet_hide", 23, z, E);       // feet, facing the fusuma (the panels stand in these cells' east edge)
        for (int z = 10; z <= 12; z++) m("closet_fusuma", 23, y, z, E);  // blocks: the lower halves of the 押し入れ's fusuma
        m("closet_peek", 23, 10, E);                   // feet: the crack opens at the row's north end (x 23.9, z 10.0..10.25)
        m("closet_futons", 23, y + 2, 10, E);          // blocks: the futons / box on the middle shelf (upper level)
        m("closet_futons", 23, y + 2, 11, E);
        m("closet_futons", 23, y + 2, 12, E);
        m("western_closet", 32, 11, W);                // feet: hiding place inside the western closet (forbidden by the printout)
        // ---- camera suggestions (where a camera could stand, facing what it should watch)
        m("cam_suggest", 31, y + 2, 2, E);             // the bathtub, from the bathroom's high corner
        m("cam_suggest", 27, y + 2, 1, S);             // the hallway, from its north end
        m("cam_suggest", 27, y + 2, 12, W);            // the tatami room and the 押し入れ, from the south-east corner
        m("cam_suggest", 26, y + 2, 2, W);             // the genkan and the front door, from the hall
        // ---- the doll
        int[][] route = {{31, y, 2}, {30, y, 5}, {27, y, 4}, {24, y, 6}, {24, y, 9}, {26, y, 12}, {26, y, 10}, {25, y, 7}, {27, y, 7}, {29, y, 7},
                {30, y, 11}, {31, y, 9}, {27, y, 6}, {29, y, 2}, {26, y, 1}, {24, g, 1}};
        for (int[] p : route) m("doll_route", p[0], p[1], p[2], N);
        // spots where it may lurk (some are furniture cells: under the ちゃぶ台, under the dining table)
        int[][] hides = {{25, y, 11}, {30, y, 4}, {31, y, 2}, {25, y, 14}, {24, g, 2}, {25, y, 5}, {32, y, 10}, {29, y, 2}, {24, y, 7}};
        Direction[] hideFacing = {W, W, S, N, E, N, W, S, S};
        for (int i = 0; i < hides.length; i++) m("doll_hides", hides[i][0], hides[i][1], hides[i][2], hideFacing[i]);
        // corners where the woman stands, each facing the nearest camera suggestion
        m("woman_spots", 24, 9, S);                    // tatami room, by the pier (cam 3)
        m("woman_spots", 26, 7, N);                    // the hall's south end (cam 2)
        m("woman_spots", 31, 1, S);                    // the bathroom's corner (cam 1)
        m("woman_spots", 23, g, 1, E);                 // the genkan's corner by the door (cam 4)
        m("woman_spots", 30, 12, N);                   // the western room's curtains
        m("woman_spots", 24, 7, N);                    // the DK by the fusuma
        // ---- outside
        m("balcony", 25, 14, N);                       // feet: on 203's balcony, looking in through the tatami room's doors
        m("balcony_western", 30, 14, N);
        for (int x = 0; x <= 43; x += 3) m("corridor", x, g, -2, E);      // feet: the outdoor corridor from the stairs to 204
        m("corridor_door", 24, g, -2, S);              // feet: in front of 203's door, outside
        m("stairs_top", -1, g, -2, W);
        m("stairs_bottom", -7, GROUND_Y, -2, E);
        m("street", 26, GROUND_Y, 21, N);              // feet: the street in front of the building, under the street lamp
        m("street_lamp", 26, 71, 17, N);
        m("vending_machine", 0, GROUND_Y, 18, S);
        m("swing", 56, GROUND_Y, 23, W);
        m("unit_204", 35, g, 0, N);                    // the door of 204 (a TV murmurs behind it)
        m("unit_202", 13, g, 0, N);                    // the door of 202 (入居者募集)
    }
}
