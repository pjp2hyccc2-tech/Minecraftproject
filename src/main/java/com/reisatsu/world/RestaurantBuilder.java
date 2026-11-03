package com.reisatsu.world;

import com.reisatsu.block.FacingBlock;
import com.reisatsu.block.Props;
import com.reisatsu.block.TwoSidedWallBlock;
import com.reisatsu.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the restaurant HIDAMARI: floors, walls (each face in its room's finish), doors, windows, the suspended
 * ceiling with its lights, the terracotta roof, and every room's furniture (docs/DESIGN.md §2). Deterministic; runs
 * for every chunk during generation (clipped by the sink) and again on the live level to restore the start state.
 */
public final class RestaurantBuilder {
    private RestaurantBuilder() {}

    public static final int FLOOR = Layout.FEET_Y - 1, Y = Layout.FEET_Y, CEIL = Layout.FEET_Y + 4;
    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    /** Downlights over the register on the night circuit: always on (unless burst). */
    public static final Set<BlockPos> NIGHT_LIGHTS = Set.of(new BlockPos(12, CEIL, 24), new BlockPos(13, CEIL, 23));
    /** Soffit downlights under the front eave (x positions at z 31). */
    public static final int[] SOFFIT_X = {12, 20, 28, 36};
    /** Downpipes {x, z, skipBottom}: the building's corners and the canopy's sides. */
    static final int[][] DOWNPIPES = {{-1, -1, 0}, {41, -1, 0}, {-1, 31, 1}, {41, 31, 1}};

    /** Ceiling lights placed broken (they flicker whenever their circuit is on). */
    public static final Set<BlockPos> BROKEN_LIGHTS = Set.of(new BlockPos(3, CEIL, 8), new BlockPos(10, CEIL, 19), new BlockPos(3, CEIL, 19));

    // ------------------------------------------------------------------------------------------------ helpers
    static BlockState st(RegistryObject<? extends Block> b) {
        return b.get().defaultBlockState();
    }

    static BlockState f(RegistryObject<? extends Block> b, Direction d) {
        return b.get().defaultBlockState().setValue(FacingBlock.FACING, d);
    }

    static int h(int x, int y, int z, int salt) {
        return Site.hash(x * 7 + y * 131, z * 13 - y * 17, salt);
    }

    static boolean chance(int x, int y, int z, int salt, double p) {
        return (h(x, y, z, salt) & 0xFFFF) / 65536.0 < p;
    }

    static void door(BlockSink s, RegistryObject<? extends Block> b, int x, int z, Direction facing, DoorHingeSide hinge, boolean open,
                     BlockState extra) {
        BlockState lo = b.get().defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, open);
        if (extra != null) {
            if (lo.hasProperty(Props.LOCKED) && extra.hasProperty(Props.LOCKED)) lo = lo.setValue(Props.LOCKED, extra.getValue(Props.LOCKED));
        }
        s.set(x, Y, z, lo);
        s.set(x, Y + 1, z, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static BlockState lockedState(boolean locked) {
        return st(ModBlocks.OFFICE_DOOR).setValue(Props.LOCKED, locked);
    }

    static void stallDoor(BlockSink s, int x, int z, Direction facing, boolean open, boolean occupied) {
        BlockState lo = st(ModBlocks.STALL_DOOR).setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, open).setValue(Props.OCCUPIED, occupied);
        s.set(x, Y, z, lo);
        s.set(x, Y + 1, z, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    static BlockState light(RegistryObject<? extends Block> b, boolean lit) {
        return b.get().defaultBlockState().setValue(Props.LIT, lit);
    }

    static BlockState panel(boolean lit, boolean broken) {
        return st(ModBlocks.CEILING_LIGHT).setValue(Props.LIT, lit).setValue(Props.BROKEN, broken);
    }

    // ================================================================================================ entry point
    public static void build(BlockSink s) {
        if (!s.touches(-2, -2, 42, 34)) return;
        shell(s);
        roof(s);
        canopy(s);
        vestibule(s);
        waiting(s);
        dining(s);
        kitchen(s);
        fridge(s);
        backOfHouse(s);
        restrooms(s);
        cavity(s);
        lights(s);
    }

    // ================================================================================================ shell
    static BlockState floorOf(String room, int x, int z) {
        return switch (room) {
            case "vestibule" -> st(ModBlocks.VESTIBULE_FLOOR);
            case "waiting", "dining", "staff", "office" -> st(ModBlocks.DINING_FLOOR);
            case "kitchen", "fridge", "corridor", "storage" -> st(ModBlocks.KITCHEN_FLOOR);
            case "mens", "womens" -> st(ModBlocks.RESTROOM_FLOOR);
            case "cavity" -> st(ModBlocks.RESTROOM_FLOOR_DARK);
            default -> st(ModBlocks.CONCRETE_WALL);
        };
    }

    static BlockState ceilingOf(String room, int x, int z) {
        return switch (room) {
            case "fridge" -> st(ModBlocks.STAINLESS);
            case "cavity" -> st(ModBlocks.CEILING_TILE_STAINED);
            case "staff", "office", "corridor", "storage", "kitchen" -> chance(x, CEIL, z, 3, 0.22) ? st(ModBlocks.CEILING_TILE_STAINED) : st(ModBlocks.CEILING_TILE);
            default -> chance(x, CEIL, z, 3, 0.10) ? st(ModBlocks.CEILING_TILE_STAINED) : st(ModBlocks.CEILING_TILE);
        };
    }

    /** The finish of a wall face seen from a room cell (nx, nz) at height y; room null = outside. */
    static Props.Face face(String room, int nx, int y, int nz, int wx, int wz) {
        if (room == null) return y == Y ? Props.Face.FACADE_BRICK : Props.Face.EXTERIOR_WALL;
        boolean low = y == Y;
        int salt = (wx * 3 + wz * 5) ^ (nx * 11 + nz * 7);
        return switch (room) {
            case "dining", "waiting", "vestibule" -> low ? Props.Face.WAINSCOT
                    : chance(wx, y, wz, 40 + salt, 0.07) ? Props.Face.WALLPAPER_STAINED : Props.Face.WALLPAPER;
            case "staff", "office" -> low ? Props.Face.WAINSCOT
                    : chance(wx, y, wz, 41 + salt, 0.35) ? Props.Face.WALLPAPER_STAINED : Props.Face.WALLPAPER;
            case "corridor" -> low ? Props.Face.WHITE_TILE_DIRTY
                    : chance(wx, y, wz, 42 + salt, 0.40) ? Props.Face.WALLPAPER_STAINED : Props.Face.WALLPAPER;
            case "storage" -> Props.Face.CONCRETE_WALL;
            case "kitchen" -> (nz == 1 && nx >= 19 && nx <= 33) || (nx == 39 && nz <= 7 && y <= Y + 2) ? Props.Face.STAINLESS
                    : (low || chance(wx, y, wz, 43 + salt, 0.30)) ? Props.Face.WHITE_TILE_DIRTY : Props.Face.WHITE_TILE;
            case "fridge" -> Props.Face.STAINLESS;
            case "mens", "womens" -> chance(wx, y, wz, 44 + salt, 0.10) ? Props.Face.WHITE_TILE_DIRTY : Props.Face.WHITE_TILE;
            case "cavity" -> chance(wx, y, wz, 45 + salt, 0.15) ? Props.Face.WALLPAPER_STAINED : Props.Face.WHITE_TILE_DIRTY;
            default -> Props.Face.CONCRETE_WALL;
        };
    }

    static BlockState plain(Props.Face f) {
        return switch (f) {
            case WALLPAPER -> st(ModBlocks.WALLPAPER);
            case WALLPAPER_STAINED -> st(ModBlocks.WALLPAPER_STAINED);
            case WAINSCOT -> st(ModBlocks.WAINSCOT);
            case WHITE_TILE -> st(ModBlocks.WHITE_TILE);
            case WHITE_TILE_DIRTY -> st(ModBlocks.WHITE_TILE_DIRTY);
            case STAINLESS -> st(ModBlocks.STAINLESS);
            case CONCRETE_WALL -> st(ModBlocks.CONCRETE_WALL);
            case EXTERIOR_WALL -> st(ModBlocks.EXTERIOR_WALL);
            case FACADE_BRICK -> st(ModBlocks.FACADE_BRICK);
        };
    }

    /** The block of a wall-line cell at height y: one face per neighbouring room, two different ones become a two-sided wall. */
    public static BlockState wall(int x, int y, int z) {
        Props.Face[] faces = new Props.Face[4];
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            Props.Face fc;
            if (!Plan.inside(nx, nz)) fc = face(null, nx, y, nz, x, z);
            else {
                String room = Plan.roomAt(nx, nz);
                fc = room == null ? null : face(room, nx, y, nz, x, z);
            }
            faces[d.get2DDataValue()] = fc;
        }
        Props.Face outer = null, inner = null;
        Direction innerDir = null;
        int best = 0;
        for (Props.Face cand : faces) {
            if (cand == null) continue;
            int n = 0;
            for (Props.Face o : faces) if (o == cand) n++;
            boolean ext = cand == Props.Face.EXTERIOR_WALL || cand == Props.Face.FACADE_BRICK;
            if (n > best || (n == best && ext)) {
                best = n;
                outer = cand;
            }
        }
        if (outer == null) return st(ModBlocks.CONCRETE_WALL);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Props.Face fc = faces[d.get2DDataValue()];
            if (fc != null && fc != outer) {
                inner = fc;
                innerDir = d;
                break;
            }
        }
        if (inner == null) return plain(outer);
        return ((TwoSidedWallBlock) ModBlocks.TWO_SIDED_WALL.get()).of(innerDir, inner, outer);
    }

    static void wallColumn(BlockSink s, int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) s.set(x, y, z, wall(x, y, z));
    }

    static boolean exteriorCell(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (!Plan.inside(x + d.getStepX(), z + d.getStepZ())) return true;
        return false;
    }

    static void shell(BlockSink s) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState concrete = st(ModBlocks.CONCRETE_WALL);
        for (int x = 0; x < Plan.W; x++) {
            for (int z = 0; z < Plan.D; z++) {
                if (!s.contains(x, z)) continue;
                char c = Plan.at(x, z);
                String room = Plan.roomOf(c);
                for (int y = FLOOR - 3; y < FLOOR; y++) s.set(x, y, z, concrete);
                if (room != null) {
                    s.set(x, FLOOR, z, floorOf(room, x, z));
                    for (int y = Y; y < CEIL; y++) s.set(x, y, z, air);
                    s.set(x, CEIL, z, ceilingOf(room, x, z));
                } else {
                    shellCell(s, x, z, c);
                }
                // above the ceiling: the attic is closed by the roof; clear whatever the ground put there
                for (int y = CEIL + 1; y <= CEIL + 6; y++) s.set(x, y, z, air);
            }
        }
    }

    static void shellCell(BlockSink s, int x, int z, char c) {
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean ext = exteriorCell(x, z);
        s.set(x, FLOOR, z, st(ModBlocks.CONCRETE_WALL));
        s.set(x, CEIL, z, ext ? st(ModBlocks.EXTERIOR_WALL) : st(ModBlocks.CEILING_TILE));
        switch (c) {
            case 'd' -> {
                wallColumn(s, x, z, Y + 2, Y + 3);
                doorAt(s, x, z);
            }
            case 'B' -> {
                s.set(x, FLOOR, z, st(ModBlocks.KITCHEN_FLOOR));
                wallColumn(s, x, z, Y + 2, Y + 3);
                door(s, ModBlocks.BACK_DOOR, x, z, E, DoorHingeSide.LEFT, false, lockedState(true));
            }
            case 'F' -> {
                s.set(x, FLOOR, z, st(ModBlocks.KITCHEN_FLOOR));
                wallColumn(s, x, z, Y + 2, Y + 3);
                door(s, ModBlocks.FRIDGE_DOOR, x, z, N, DoorHingeSide.LEFT, false, null);
            }
            case 'A' -> {
                s.set(x, FLOOR, z, st(ModBlocks.VESTIBULE_FLOOR));
                Props.Side side = x == 3 ? Props.Side.LEFT : Props.Side.RIGHT;
                BlockState d = st(ModBlocks.AUTO_DOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, S).setValue(Props.SIDE, side)
                        .setValue(Props.OPEN, false);
                s.set(x, Y, z, d.setValue(Props.DOOR_PART, Props.DoorPart.BOTTOM));
                s.set(x, Y + 1, z, d.setValue(Props.DOOR_PART, Props.DoorPart.MIDDLE));
                s.set(x, Y + 2, z, d.setValue(Props.DOOR_PART, Props.DoorPart.TOP));
                wallColumn(s, x, z, Y + 3, Y + 3);
            }
            case 'S' -> {
                s.set(x, FLOOR, z, st(ModBlocks.KITCHEN_FLOOR));
                Props.Side side = x == 29 ? Props.Side.LEFT : Props.Side.RIGHT;
                BlockState d = st(ModBlocks.SWING_DOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, S).setValue(Props.SIDE, side)
                        .setValue(Props.SWING, 0);
                s.set(x, Y, z, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
                s.set(x, Y + 1, z, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
                wallColumn(s, x, z, Y + 2, Y + 3);
            }
            case 'P' -> {
                s.set(x, Y, z, f(ModBlocks.PASS_SHELF, N).setValue(Props.LIT, Circuit.KITCHEN.startOn()));
                s.set(x, Y + 1, z, air);
                s.set(x, Y + 2, z, air);
                wallColumn(s, x, z, Y + 3, Y + 3);
            }
            case 'W' -> {
                wallColumn(s, x, z, Y, Y);
                for (int y = Y + 1; y <= Y + 3; y++) s.set(x, y, z, st(ModBlocks.SHOP_WINDOW));
            }
            case 'w' -> {
                wallColumn(s, x, z, Y, Y);
                s.set(x, Y + 1, z, st(ModBlocks.SHOP_WINDOW));
                s.set(x, Y + 2, z, st(ModBlocks.SHOP_WINDOW));
                wallColumn(s, x, z, Y + 3, Y + 3);
            }
            case 'M' -> {
                wallColumn(s, x, z, Y, Y);
                s.set(x, Y + 1, z, f(ModBlocks.MIRROR, S));
                s.set(x, Y + 2, z, f(ModBlocks.MIRROR, S));
                wallColumn(s, x, z, Y + 3, Y + 3);
            }
            case 'E' -> {
                wallColumn(s, x, z, Y, Y);
                s.set(x, Y + 1, z, f(ModBlocks.BREAKER_PANEL, S));
                wallColumn(s, x, z, Y + 2, Y + 3);
            }
            default -> wallColumn(s, x, z, Y, Y + 3);
        }
    }

    static void doorAt(BlockSink s, int x, int z) {
        if (x == 3 && z == 7) {
            s.set(x, FLOOR, z, st(ModBlocks.KITCHEN_FLOOR));
            door(s, ModBlocks.STAFF_DOOR, x, z, N, DoorHingeSide.LEFT, false, null);
        } else if (x == 11 && z == 7) {
            s.set(x, FLOOR, z, st(ModBlocks.KITCHEN_FLOOR));
            door(s, ModBlocks.OFFICE_DOOR, x, z, N, DoorHingeSide.RIGHT, false, lockedState(true));
        } else if (x == 14 && z == 9) {
            s.set(x, FLOOR, z, st(ModBlocks.KITCHEN_FLOOR));
            door(s, ModBlocks.STAFF_DOOR, x, z, E, DoorHingeSide.RIGHT, false, null);
        } else if (x == 3 && z == 10) {
            s.set(x, FLOOR, z, st(ModBlocks.KITCHEN_FLOOR));
            door(s, ModBlocks.STAFF_DOOR, x, z, S, DoorHingeSide.LEFT, false, null);
        } else if (x == 3 && z == 22) {
            s.set(x, FLOOR, z, st(ModBlocks.RESTROOM_FLOOR));
            door(s, ModBlocks.RESTROOM_DOOR_M, x, z, N, DoorHingeSide.LEFT, false, null);
        } else if (x == 10 && z == 22) {
            s.set(x, FLOOR, z, st(ModBlocks.RESTROOM_FLOOR));
            door(s, ModBlocks.RESTROOM_DOOR_W, x, z, N, DoorHingeSide.RIGHT, false, null);
        } else {
            door(s, ModBlocks.STAFF_DOOR, x, z, N, DoorHingeSide.LEFT, false, null);
        }
    }

    // ================================================================================================ roof
    static void roof(BlockSink s) {
        if (!s.touches(-2, -2, 42, 32)) return;
        // the wall rises one block above the ceiling under the eave
        for (int x = 0; x <= 40; x++)
            for (int z = 0; z <= 30; z++) {
                if ((x == 0 || x == 40 || z == 0 || z == 30) && s.contains(x, z)) s.set(x, CEIL + 1, z, st(ModBlocks.EXTERIOR_WALL));
            }
        // fascia band at the overhang edge
        ring(s, -1, CEIL + 1, (x, z, d) -> st(ModBlocks.FASCIA));
        // four rings of terracotta tiles rising inward, then the flat top
        for (int k = 0; k < 4; k++) {
            int y = CEIL + 2 + k;
            ring(s, -1 + k, y, (x, z, d) -> st(ModBlocks.ROOF_TILE_STAIRS).setValue(StairBlock.FACING, d.getOpposite())
                    .setValue(StairBlock.HALF, Half.BOTTOM));
        }
        int top = CEIL + 5;
        for (int x = 3; x <= 37; x++)
            for (int z = 3; z <= 27; z++) if (s.contains(x, z)) s.set(x, top, z, st(ModBlocks.ROOF_TILE));
        // the roof sign 「ひだまり」 on the front slope
        for (int t = 0; t < 8; t++) {
            s.set(16 + t, CEIL + 3, 31, f(ModBlocks.ROOF_SIGN, S).setValue(Props.TILE8, t).setValue(Props.LIT, Circuit.SIGN.startOn()));
        }
        // the kitchen exhaust duct up the back wall and through the eave
        for (int y = Y + 2; y <= top + 1; y++) {
            s.set(25, y, -1, st(ModBlocks.STAINLESS));
            s.set(26, y, -1, st(ModBlocks.STAINLESS));
        }
        s.set(25, top + 2, -1, Blocks.IRON_BARS.defaultBlockState());
        s.set(26, top + 2, -1, Blocks.IRON_BARS.defaultBlockState());
        // soffit lights under the front eave (outside lighting, the SIGN circuit)
        for (int x : SOFFIT_X) s.set(x, CEIL, 31, light(ModBlocks.DOWNLIGHT, Circuit.SIGN.startOn()));
        // downpipes from the gutters at the corners and beside the canopy
        for (int[] p : DOWNPIPES) {
            for (int y = Y; y <= CEIL; y++) {
                if (y == Y && p[2] == 1) continue;
                s.post(p[0], y, p[1], Blocks.IRON_BARS.defaultBlockState());
            }
        }
    }

    interface RingCell {
        BlockState at(int x, int z, Direction outward);
    }

    /** A rectangle ring at offset k from the building's border (k = -1 is the overhang). */
    static void ring(BlockSink s, int k, int y, RingCell cell) {
        int x1 = k, x2 = 40 - k, z1 = k, z2 = 30 - k;
        for (int x = x1; x <= x2; x++) {
            ringSet(s, x, y, z1, cell.at(x, z1, N));
            ringSet(s, x, y, z2, cell.at(x, z2, S));
        }
        for (int z = z1 + 1; z < z2; z++) {
            ringSet(s, x1, y, z, cell.at(x1, z, W));
            ringSet(s, x2, y, z, cell.at(x2, z, E));
        }
    }

    static void ringSet(BlockSink s, int x, int y, int z, BlockState st) {
        if (!s.contains(x, z)) return;
        if (st.getBlock() instanceof StairBlock) s.post(x, y, z, st);
        else s.set(x, y, z, st);
    }

    // ================================================================================================ entrance canopy
    static void canopy(BlockSink s) {
        if (!s.touches(0, 30, 7, 34)) return;
        for (int x = 1; x <= 6; x++)
            for (int z = 31; z <= 33; z++) {
                boolean edge = x == 1 || x == 6 || z == 33;
                s.set(x, CEIL, z, edge ? st(ModBlocks.FASCIA) : st(ModBlocks.CEILING_TILE));
                if (z >= 32) s.set(x, CEIL + 1, z, st(ModBlocks.ROOF_TILE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
            }
        s.set(3, CEIL, 32, light(ModBlocks.DOWNLIGHT, Circuit.SIGN.startOn()));
        s.set(4, CEIL, 32, light(ModBlocks.DOWNLIGHT, Circuit.SIGN.startOn()));
        for (int y = Y; y < CEIL; y++) {
            s.set(1, y, 33, st(ModBlocks.PILLAR));
            s.set(6, y, 33, st(ModBlocks.PILLAR));
        }
    }

    // ================================================================================================ rooms
    static void vestibule(BlockSink s) {
        if (!s.touches(0, 26, 8, 30)) return;
        for (int z = 27; z <= 29; z++) {
            s.set(3, Y, z, f(ModBlocks.ENTRANCE_MAT, S));
            s.set(4, Y, z, f(ModBlocks.ENTRANCE_MAT, S));
        }
        s.set(2, Y, 29, f(ModBlocks.UMBRELLA_STAND, N));
        s.set(1, Y + 1, 28, f(ModBlocks.NOTICE_BOARD, E));      // the lost-child flyer (note "flyer")
        s.set(6, Y, 29, f(ModBlocks.CAPSULE_MACHINE, N));
        s.set(7, Y, 29, f(ModBlocks.CAPSULE_MACHINE, N));
        s.set(7, Y, 28, f(ModBlocks.CAPSULE_MACHINE, W));
        s.set(3, Y + 3, 29, f(ModBlocks.EXIT_SIGN, N));
        s.set(1, Y, 27, f(ModBlocks.OUTDOOR_PLANTER, E));
    }

    static void waiting(BlockSink s) {
        if (!s.touches(0, 22, 15, 30)) return;
        for (int x = 5; x <= 8; x++) s.set(x, Y, 23, f(ModBlocks.WAITING_BENCH, S));
        s.set(1, Y, 24, f(ModBlocks.MAGAZINE_RACK, E));
        s.set(1, Y, 25, f(ModBlocks.OUTDOOR_PLANTER, E));
        s.set(1, Y + 1, 23, f(ModBlocks.NOTICE_BOARD, E));
        // the register counter: an L of counter and the lit cake case, the register, the book, the bell
        s.set(11, Y, 23, f(ModBlocks.COUNTER, S));
        s.set(12, Y, 23, f(ModBlocks.COUNTER, W));
        s.set(12, Y, 24, f(ModBlocks.CAKE_CASE, W));
        s.set(12, Y + 1, 23, f(ModBlocks.REGISTER, W));
        s.set(11, Y + 1, 23, f(ModBlocks.RESERVATION_BOOK, S)); // note "reservation"
        s.set(12, Y + 1, 24, f(ModBlocks.SERVICE_BELL, W));
        s.set(14, Y, 23, f(ModBlocks.COUNTER, W));
        s.set(14, Y + 1, 23, f(ModBlocks.BUSINESS_PHONE, W));
        s.set(13, Y + 3, 23, f(ModBlocks.CALL_DISPLAY, S));
        s.set(3, Y + 3, 25, f(ModBlocks.EXIT_SIGN, N));
        // the kids' corner by the window: mat, toy shelves, the bear, a low fence and the pillar the child counts at
        for (int x = 11; x <= 13; x++)
            for (int z = 27; z <= 29; z++) s.set(x, Y, z, f(ModBlocks.KIDS_MAT, N));
        s.set(10, Y, 28, f(ModBlocks.TOY_SHELF, E));
        s.set(10, Y, 29, f(ModBlocks.TOY_SHELF, E));
        s.set(10, Y + 1, 28, f(ModBlocks.NOTE_PAPER, E));       // the crayon drawing (note "drawing")
        s.set(13, Y, 29, f(ModBlocks.STUFFED_BEAR, N));
        s.set(13, Y, 26, f(ModBlocks.KIDS_FENCE, N));
        s.set(14, Y, 26, f(ModBlocks.KIDS_FENCE, N));
        for (int y = Y; y < CEIL; y++) s.set(12, y, 26, st(ModBlocks.PILLAR));
    }

    static void booth(BlockSink s, int tx, int tz, Direction across, Direction toWindow, boolean lit) {
        // across: from the table toward the "+" bench; the benches face the table
        int ax = across.getStepX(), az = across.getStepZ();
        int wx = toWindow.getStepX(), wz = toWindow.getStepZ();
        for (int k = 0; k <= 1; k++) {
            int x = tx + wx * k, z = tz + wz * k;
            s.set(x, Y, z, f(ModBlocks.RESTAURANT_TABLE, toWindow.getOpposite()));
            s.set(x - ax, Y, z - az, f(ModBlocks.BOOTH_SEAT, across));
            s.set(x + ax, Y, z + az, f(ModBlocks.BOOTH_SEAT, across.getOpposite()));
            s.set(x - ax, Y + 1, z - az, f(ModBlocks.BOOTH_PARTITION, across));
            s.set(x + ax, Y + 1, z + az, f(ModBlocks.BOOTH_PARTITION, across.getOpposite()));
        }
        Direction face = toWindow.getOpposite();
        s.set(tx, Y + 1, tz, f(ModBlocks.CALL_BUTTON, face));
        s.set(tx + wx, Y + 1, tz + wz, f(ModBlocks.MENU_STAND, face));
        s.set(tx, Y + 3, tz, light(ModBlocks.PENDANT_LAMP, lit));
    }

    static void centreTable(BlockSink s, int x0, int z) {
        for (int k = 0; k <= 1; k++) {
            s.set(x0 + k, Y, z, f(ModBlocks.RESTAURANT_TABLE, N));
            s.set(x0 + k, Y, z - 1, f(ModBlocks.RESTAURANT_CHAIR, S));
            s.set(x0 + k, Y, z + 1, f(ModBlocks.RESTAURANT_CHAIR, N));
        }
        s.set(x0, Y + 1, z, f(ModBlocks.MENU_STAND, N));
        s.set(x0 + 1, Y + 1, z, f(ModBlocks.CALL_BUTTON, N));
    }

    static void dining(BlockSink s) {
        if (!s.touches(14, 10, 41, 31)) return;
        boolean lit = Circuit.DINING.startOn();
        // south window booths 1..7 (table at boothX(n), z 28..29), east window booths 8..11
        for (int n = 1; n <= 7; n++) booth(s, Layout.boothX(n), 28, E, S, lit);
        for (int n = 8; n <= 11; n++) booth(s, 38, Layout.eastBoothZ(n), S, E, lit);
        // centre four-seat tables 12..17 and the long planter divider with its pillars
        for (int n = 12; n <= 17; n++) centreTable(s, Layout.centreX(n), Layout.centreZ(n));
        for (int x = 17; x <= 35; x++) {
            if (x == 17 || x == 23 || x == 29 || x == 35) {
                for (int y = Y; y < CEIL; y++) s.set(x, y, 18, st(ModBlocks.PILLAR));
            } else {
                s.set(x, Y, 18, f(ModBlocks.PLANTER, x < 26 ? N : S));
            }
        }
        // the drink bar on the north wall (x 15..19) with its alcove, closed by a pillar at x 21
        for (int x = 15; x <= 19; x++) s.set(x, Y, 11, f(ModBlocks.COUNTER, S));
        s.set(15, Y + 1, 11, f(ModBlocks.COFFEE_MACHINE, S));
        s.set(16, Y + 1, 11, f(ModBlocks.ICE_MACHINE, S));
        s.set(17, Y + 1, 11, f(ModBlocks.DRINK_BAR, S));
        s.set(18, Y + 1, 11, f(ModBlocks.DRINK_BAR, S));
        s.set(19, Y + 1, 11, f(ModBlocks.SOUP_WARMER, S));
        s.set(15, Y + 2, 11, f(ModBlocks.CUP_SHELF, S));
        s.set(16, Y + 2, 11, f(ModBlocks.CUP_SHELF, S));
        for (int y = Y; y < CEIL; y++) s.set(21, y, 11, st(ModBlocks.PILLAR));
        // the server station by the swing doors
        s.set(32, Y, 11, f(ModBlocks.SERVER_STATION, S));
        s.set(33, Y, 11, f(ModBlocks.SERVER_STATION, S));
        s.set(34, Y, 11, f(ModBlocks.KITCHEN_TRASH, S));
        s.set(31, Y, 11, f(ModBlocks.CLEANING_CART, S));
        // corners: plants
        s.set(39, Y, 11, f(ModBlocks.PLANTER, W));
        s.set(38, Y, 11, f(ModBlocks.OUTDOOR_PLANTER, W));
        for (int x = 37; x <= 39; x++) s.set(x, Y, 29, f(ModBlocks.PLANTER, N));
        s.set(39, Y, 28, f(ModBlocks.OUTDOOR_PLANTER, W));
        // window blinds (top row closed; lowered further at some windows, open around tables 5..7)
        for (int x = 9; x <= 39; x++) {
            boolean open = x >= 27 && x <= 35;
            s.set(x, Y + 3, 29, f(ModBlocks.WINDOW_BLIND, N).setValue(Props.CLOSED, !open));
            if (!open && x >= 15 && (x % 7 == 1 || x % 7 == 2 || x % 7 == 3)) {
                s.set(x, Y + 2, 29, f(ModBlocks.WINDOW_BLIND, N).setValue(Props.CLOSED, true));
            }
        }
        for (int z = 12; z <= 28; z++) {
            s.set(39, Y + 3, z, f(ModBlocks.WINDOW_BLIND, W).setValue(Props.CLOSED, true));
            if (z % 6 == 0 || z % 6 == 1) s.set(39, Y + 2, z, f(ModBlocks.WINDOW_BLIND, W).setValue(Props.CLOSED, true));
        }
        // exit signs hanging from the ceiling, posters on the walls
        s.set(15, Y + 3, 24, f(ModBlocks.EXIT_SIGN, E).setValue(Props.HANGING, true));
        s.set(24, Y + 3, 26, f(ModBlocks.EXIT_SIGN, E).setValue(Props.HANGING, true));
        s.set(36, Y + 3, 17, f(ModBlocks.EXIT_SIGN, S).setValue(Props.HANGING, true));
        s.set(15, Y + 1, 15, f(ModBlocks.NOTICE_BOARD, E));
        s.set(27, Y + 1, 11, f(ModBlocks.NOTICE_BOARD, S));
    }

    static void kitchen(BlockSink s) {
        if (!s.touches(14, 0, 41, 10)) return;
        boolean on = Circuit.KITCHEN.startOn();
        // the cooking line along the north wall, the hood above it
        Object[][] line = {
                {20, ModBlocks.KITCHEN_COUNTER}, {21, ModBlocks.GAS_RANGE}, {22, ModBlocks.GAS_RANGE}, {23, ModBlocks.GRIDDLE},
                {24, ModBlocks.KITCHEN_COUNTER}, {25, ModBlocks.GAS_RANGE}, {26, ModBlocks.GAS_RANGE}, {27, ModBlocks.KITCHEN_COUNTER},
                {28, ModBlocks.FRYER}, {29, ModBlocks.FRYER}, {30, ModBlocks.KITCHEN_COUNTER}, {31, ModBlocks.POT_SHELF},
                {32, ModBlocks.KITCHEN_COUNTER}};
        for (Object[] o : line) {
            @SuppressWarnings("unchecked") RegistryObject<? extends Block> b = (RegistryObject<? extends Block>) o[1];
            s.set((Integer) o[0], Y, 1, f(b, S));
        }
        for (int x = 20; x <= 31; x++) s.set(x, Y + 3, 1, f(ModBlocks.EXHAUST_HOOD, S));
        s.set(20, Y + 1, 1, Blocks.CAULDRON.defaultBlockState());
        // the prep island; the chef's board faces him (he stands at z 6, his back to the pass window)
        for (int x = 22; x <= 28; x++) s.set(x, Y, 5, f(ModBlocks.PREP_TABLE, S));
        s.set(25, Y + 1, 5, f(ModBlocks.CUTTING_BOARD, S));
        s.set(27, Y + 1, 5, Blocks.CAULDRON.defaultBlockState());
        // west part: counters and shelves along the wall (the fire inspection record lies on the counter)
        s.set(15, Y, 5, f(ModBlocks.KITCHEN_COUNTER, E));
        s.set(15, Y, 6, f(ModBlocks.KITCHEN_COUNTER, E));
        s.set(15, Y, 7, f(ModBlocks.POT_SHELF, E));
        s.set(15, Y + 1, 5, f(ModBlocks.NOTE_PAPER, E));          // note "fire"
        s.set(18, Y, 9, f(ModBlocks.PREP_TABLE, N));
        s.set(19, Y, 9, f(ModBlocks.PREP_TABLE, N));
        s.set(20, Y, 9, f(ModBlocks.KITCHEN_TRASH, N));
        // the dish pit in the east
        s.set(34, Y, 1, f(ModBlocks.KITCHEN_COUNTER, S));
        s.set(35, Y, 1, f(ModBlocks.KITCHEN_SINK, S));
        s.set(36, Y, 1, f(ModBlocks.KITCHEN_SINK, S));
        s.set(37, Y, 1, f(ModBlocks.KITCHEN_COUNTER, S));
        s.set(38, Y, 1, f(ModBlocks.DISH_RACK, S));
        s.set(39, Y, 1, f(ModBlocks.DISH_RACK, S));
        s.set(39, Y, 3, f(ModBlocks.DISHWASHER, W));
        s.set(39, Y, 4, f(ModBlocks.KITCHEN_COUNTER, W));
        s.set(39, Y, 5, f(ModBlocks.KITCHEN_COUNTER, W));
        s.set(39, Y, 6, f(ModBlocks.DISH_RACK, W));
        s.set(39, Y, 7, f(ModBlocks.DISH_RACK, W));
        for (int x = 33; x <= 37; x++) s.set(x, Y, 9, f(ModBlocks.POT_SHELF, N));
        s.set(32, Y, 9, f(ModBlocks.KITCHEN_TRASH, N));
        s.set(38, Y, 9, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(39, Y, 9, f(ModBlocks.CARDBOARD_BOXES, W));
        s.set(39, Y + 1, 9, f(ModBlocks.CARDBOARD_BOXES, W));
        // the pass: the bell on the shelf (the shelves are part of the wall line)
        s.set(24, Y + 1, 10, f(ModBlocks.PASS_BELL, N));
        s.set(16, Y + 3, 9, f(ModBlocks.EXIT_SIGN, E).setValue(Props.HANGING, true));
    }

    static void fridge(BlockSink s) {
        if (!s.touches(14, 0, 20, 5)) return;
        for (int x = 15; x <= 18; x++) {
            s.set(x, Y, 1, f(ModBlocks.WALKIN_SHELF, S));
            s.set(x, Y + 1, 1, f(ModBlocks.WALKIN_SHELF, S));
        }
        s.set(15, Y, 2, f(ModBlocks.WALKIN_SHELF, E));
        s.set(15, Y + 1, 2, f(ModBlocks.WALKIN_SHELF, E));
        s.set(15, Y, 3, f(ModBlocks.CARDBOARD_BOXES, E));
        s.set(18, Y, 3, f(ModBlocks.CARDBOARD_BOXES, W));
        s.set(16, Y, 3, f(ModBlocks.FROST, N));
        s.set(18, Y, 2, f(ModBlocks.FROST, E));
        s.set(16, Y, 2, f(ModBlocks.FROST, S));
    }

    static void backOfHouse(BlockSink s) {
        if (!s.touches(0, 0, 14, 16)) return;
        // ---- staff room
        for (int z = 1; z <= 4; z++) {
            BlockState lo = f(ModBlocks.LOCKER, E).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
            s.set(1, Y, z, lo);
            s.set(1, Y + 1, z, lo.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        }
        s.set(4, Y, 3, f(ModBlocks.STAFF_TABLE, N));
        s.set(4, Y, 4, f(ModBlocks.STAFF_TABLE, N));
        s.set(3, Y, 3, f(ModBlocks.FOLDING_CHAIR, E));
        s.set(3, Y, 4, f(ModBlocks.FOLDING_CHAIR, E));
        s.set(5, Y, 4, f(ModBlocks.FOLDING_CHAIR, W));
        s.set(4, Y + 1, 3, f(ModBlocks.NOTE_PAPER, N));           // the handover notebook (note "handover")
        s.set(6, Y, 1, f(ModBlocks.CRT_TV, S));
        s.set(5, Y + 1, 6, f(ModBlocks.TIME_RECORDER, N));
        s.set(6, Y + 1, 3, f(ModBlocks.BULLETIN_BOARD, W));        // the 2009 news clipping (note "clipping")
        s.set(6, Y, 5, f(ModBlocks.KITCHEN_COUNTER, W));
        s.set(1, Y, 6, f(ModBlocks.CARDBOARD_BOXES, E));
        s.set(2, Y, 6, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(1, Y + 1, 6, f(ModBlocks.CARDBOARD_BOXES, E));
        // ---- office: the manager's desk along the west wall, the bank of monitors, his chair by the window
        for (int z = 3; z <= 6; z++) s.set(8, Y, z, f(ModBlocks.OFFICE_DESK, E));
        boolean cams = Circuit.CAMERAS.startOn();
        for (int z = 5; z <= 6; z++)
            for (int y = Y + 1; y <= Y + 2; y++) s.set(8, y, z, f(ModBlocks.SECURITY_MONITORS, E).setValue(Props.ON, cams));
        s.set(8, Y + 1, 3, f(ModBlocks.PC, E));
        s.set(8, Y + 1, 4, f(ModBlocks.NOTE_PAPER, E));           // the manager's work diary (note "diary")
        s.set(9, Y, 6, f(ModBlocks.OFFICE_CHAIR, W));
        s.set(12, Y, 1, f(ModBlocks.FILING_CABINET, S));
        s.set(13, Y, 1, f(ModBlocks.FILING_CABINET, S));
        s.set(13, Y, 3, f(ModBlocks.SAFE, W));
        s.set(10, Y + 1, 1, f(ModBlocks.BULLETIN_BOARD, S));       // the shift schedule
        s.set(13, Y, 6, f(ModBlocks.CARDBOARD_BOXES, W));
        s.set(13, Y, 5, f(ModBlocks.CARDBOARD_BOXES, W));
        // ---- corridor
        s.set(5, Y, 9, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(6, Y, 9, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(5, Y + 1, 9, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(8, Y, 9, f(ModBlocks.CLEANING_CART, N));
        s.set(1, Y + 3, 8, f(ModBlocks.EXIT_SIGN, E));
        // ---- storage
        for (int z = 12; z <= 15; z++) {
            s.set(1, Y, z, f(ModBlocks.POT_SHELF, E));
            s.set(1, Y + 1, z, f(ModBlocks.CARDBOARD_BOXES, E));
        }
        s.set(5, Y, 12, f(ModBlocks.CARDBOARD_BOXES, W));
        s.set(5, Y, 13, f(ModBlocks.CARDBOARD_BOXES, W));
        s.set(5, Y + 1, 12, f(ModBlocks.CARDBOARD_BOXES, W));
        s.set(4, Y, 11, f(ModBlocks.CLEANING_CART, S));
        s.set(5, Y, 15, f(ModBlocks.RESTAURANT_CHAIR, W));
        s.set(4, Y, 15, f(ModBlocks.RESTAURANT_CHAIR, N));
        s.set(3, Y, 15, f(ModBlocks.HIGH_CHAIR, N));
    }

    static void restrooms(BlockSink s) {
        if (!s.touches(0, 16, 14, 22)) return;
        // ---- men's: two urinals, one stall, two sinks with an ordinary mirror, the hand dryer
        s.set(2, Y, 17, f(ModBlocks.URINAL, S));
        s.set(3, Y, 17, f(ModBlocks.URINAL, S));
        s.set(5, Y, 17, f(ModBlocks.TOILET, S));
        for (int z = 17; z <= 18; z++) {
            s.set(4, Y, z, f(ModBlocks.STALL_WALL, E));
            s.set(4, Y + 1, z, f(ModBlocks.STALL_WALL, E));
        }
        stallDoor(s, 5, 18, N, false, false);
        for (int z = 19; z <= 20; z++) {
            s.set(1, Y, z, f(ModBlocks.RESTROOM_SINK, E));
            s.set(1, Y + 1, z, f(ModBlocks.RESTROOM_MIRROR, E));
        }
        s.set(5, Y + 1, 20, f(ModBlocks.HAND_DRYER, W));
        s.set(5, Y + 1, 21, f(ModBlocks.PAPER_TOWEL, W));
        // ---- women's: the sink counter under the dark mirror, three stalls along the west wall (the third occupied)
        for (int x = 9; x <= 11; x++) s.set(x, Y, 17, f(ModBlocks.RESTROOM_SINK, S));
        s.set(12, Y, 17, st(ModBlocks.GRANITE_COUNTER));
        s.set(12, Y + 1, 17, f(ModBlocks.NOTE_PAPER, S));        // the cleaning checklist (note "cleaning")
        womensStalls(s, false);
        s.set(13, Y + 1, 19, f(ModBlocks.HAND_DRYER, W));
        s.set(13, Y + 1, 18, f(ModBlocks.PAPER_TOWEL, W));
        s.set(13, Y, 21, f(ModBlocks.KITCHEN_TRASH, W));
    }

    /** Toilets at x 7 facing east, stall doors at x 8 (leaf on their east edge), partitions between; mirrored for the cavity. */
    static void womensStalls(BlockSink s, boolean mirrored) {
        int[] stalls = {17, 19, 21};
        int[] walls = {18, 20};
        for (int i = 0; i < 3; i++) {
            int z = mirrored ? 32 - stalls[i] : stalls[i];
            s.set(7, Y, z, f(ModBlocks.TOILET, E));
            boolean occupied = !mirrored && i == 2;
            boolean open = mirrored && i == 1;
            stallDoor(s, 8, z, W, open, occupied);
        }
        for (int z0 : walls) {
            int z = mirrored ? 32 - z0 : z0;
            for (int x = 7; x <= 8; x++) {
                s.set(x, Y, z, f(ModBlocks.STALL_WALL, S));
                s.set(x, Y + 1, z, f(ModBlocks.STALL_WALL, S));
            }
        }
    }

    static void cavity(BlockSink s) {
        if (!s.touches(6, 10, 14, 16)) return;
        // the mirror image of the women's restroom, dead and decayed (z' = 32 - z)
        for (int x = 9; x <= 11; x++) s.set(x, Y, 15, f(ModBlocks.RESTROOM_SINK, N));
        s.set(12, Y, 15, st(ModBlocks.GRANITE_COUNTER));
        womensStalls(s, true);
        s.set(13, Y + 1, 13, f(ModBlocks.HAND_DRYER, W));
        s.set(13, Y + 1, 14, f(ModBlocks.PAPER_TOWEL, W));
        s.set(13, Y, 11, f(ModBlocks.KITCHEN_TRASH, W));
        int[][] wet = {{10, 13}, {11, 12}, {9, 14}, {12, 13}, {10, 11}, {11, 14}};
        for (int[] p : wet) s.set(p[0], Y, p[1], st(ModBlocks.PUDDLE));
    }

    // ================================================================================================ lights
    /** Every ceiling light / downlight position inside, with its room (for the circuits). */
    public static final List<BlockPos> CEILING_LIGHTS = new ArrayList<>();
    public static final List<BlockPos> DOWNLIGHTS = new ArrayList<>();
    /** Pendant lamps over the booth tables. */
    public static final List<BlockPos> PENDANTS = new ArrayList<>();

    static {
        int[][] dining = {{20, 15}, {26, 15}, {32, 15}, {20, 22}, {26, 22}, {32, 22}, {19, 26}, {29, 26}, {36, 13}, {36, 20},
                {36, 26}, {24, 12}, {30, 12}, {17, 20}};
        int[][] kitchen = {{22, 3}, {28, 3}, {34, 3}, {22, 7}, {28, 7}, {34, 7}, {17, 7}, {16, 2}};
        int[][] back = {{3, 8}, {9, 9}, {12, 8}, {3, 3}, {5, 5}, {11, 3}, {3, 13}};
        int[][] rest = {{3, 19}, {10, 19}};
        for (int[][] set : new int[][][]{dining, kitchen, back, rest}) for (int[] p : set) CEILING_LIGHTS.add(new BlockPos(p[0], CEIL, p[1]));
        int[][] down = {{4, 28}, {3, 24}, {7, 24}, {11, 27}, {12, 24}, {13, 23}, {17, 12}};
        for (int[] p : down) DOWNLIGHTS.add(new BlockPos(p[0], CEIL, p[1]));
        for (int n = 1; n <= 7; n++) PENDANTS.add(new BlockPos(Layout.boothX(n), Y + 3, 28));
        for (int n = 8; n <= 11; n++) PENDANTS.add(new BlockPos(38, Y + 3, Layout.eastBoothZ(n)));
    }

    /** True if a light at this position is lit when the night starts. */
    public static boolean startLit(BlockPos p) {
        if (NIGHT_LIGHTS.contains(p)) return true;
        Layout.Room r = Layout.roomAt(new BlockPos(p.getX(), Y, p.getZ()));
        Circuit c = Circuit.ofRoom(r == null ? null : r.key());
        return c != null && c.startOn();
    }

    static void lights(BlockSink s) {
        for (BlockPos p : CEILING_LIGHTS) {
            if (!s.contains(p.getX(), p.getZ())) continue;
            s.set(p.getX(), p.getY(), p.getZ(), panel(startLit(p), BROKEN_LIGHTS.contains(p)));
        }
        for (BlockPos p : DOWNLIGHTS) {
            if (!s.contains(p.getX(), p.getZ())) continue;
            s.set(p.getX(), p.getY(), p.getZ(), light(ModBlocks.DOWNLIGHT, startLit(p)));
        }
        // security cameras below the ceiling
        boolean cams = Circuit.CAMERAS.startOn();
        for (Layout.Marker m : Layout.markers("cameras")) {
            BlockPos p = m.pos();
            if (s.contains(p.getX(), p.getZ())) s.set(p.getX(), p.getY(), p.getZ(), f(ModBlocks.SECURITY_CAMERA, m.facing()).setValue(Props.ON, cams));
        }
    }

    /** All light positions inside (ceiling panels, downlights, pendants). */
    public static List<BlockPos> allLights() {
        List<BlockPos> all = new ArrayList<>(CEILING_LIGHTS);
        all.addAll(DOWNLIGHTS);
        all.addAll(PENDANTS);
        return all;
    }

    /** Positions of the restaurant's own lights that are in a room (by key). */
    public static List<BlockPos> lightsOf(String roomKey) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : allLights()) {
            Layout.Room r = Layout.roomAt(new BlockPos(p.getX(), Y, p.getZ()));
            if (r != null && r.key().equals(roomKey) && seen.add(p)) out.add(p);
        }
        return out;
    }
}
