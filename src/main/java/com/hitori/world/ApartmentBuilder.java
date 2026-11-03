package com.hitori.world;

import com.hitori.block.FacingBlock;
import com.hitori.block.Props;
import com.hitori.block.SlidingDoorBlock;
import com.hitori.block.TwoSidedWallBlock;
import com.hitori.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds コーポ白樺: eight units on two floors from the shared {@link Plan} (floors, walls with each side in its room's
 * finish, doors, fusuma, windows, ceilings), the outdoor corridor with its stairs, the balconies, the slate roof, the
 * façade's details, the yard around it, and room 203's furniture (docs/DESIGN.md §2). Deterministic; runs for every
 * chunk during generation (clipped by the sink) and again on the live level to restore the start state.
 */
public final class ApartmentBuilder {
    private ApartmentBuilder() {}

    public static final int F1 = Layout.GROUND_Y, F2 = Layout.FEET_Y, G2 = Layout.GENKAN_Y;
    /** The unit that is furnished and enterable. */
    public static final int X0 = 22;
    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    // ------------------------------------------------------------------------------------------------ helpers
    static BlockState st(RegistryObject<? extends Block> b) {
        return b.get().defaultBlockState();
    }

    static BlockState f(RegistryObject<? extends Block> b, Direction d) {
        return b.get().defaultBlockState().setValue(FacingBlock.FACING, d);
    }

    static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    static boolean chance(int x, int y, int z, int salt, double p) {
        return Site.rand(x * 7 + y * 131, z * 13 - y * 17, salt) < p;
    }

    /** A vanilla-style door, both halves. */
    static void door(BlockSink s, RegistryObject<? extends Block> b, int x, int y, int z, Direction facing, DoorHingeSide hinge) {
        BlockState lo = b.get().defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, false);
        s.set(x, y, z, lo);
        s.set(x, y + 1, z, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A two-block thing with a half property (sliding doors, balcony doors). */
    static void halves(BlockSink s, BlockState lower, int x, int y, int z) {
        s.set(x, y, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        s.set(x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    // ================================================================================================ entry point
    public static void build(BlockSink s) {
        if (!s.touches(-10, -6, 47, 17)) return;
        for (int u = 0; u < 4; u++) unit(s, u, false);
        for (int u = 0; u < 4; u++) unit(s, u, true);
        corridor(s);
        stairs(s);
        balconies(s);
        roof(s);
        facade(s);
        yard(s);
        furnish203(s);
        lights203(s);
    }

    // ================================================================================================ the units
    static int feetOf(boolean upper) {
        return upper ? F2 : F1;
    }

    /** Feet level of a room (the genkan of the upper floor is one step lower). */
    static int roomFeet(String room, boolean upper) {
        return upper && "genkan".equals(room) ? G2 : feetOf(upper);
    }

    static BlockState floorOf(String room) {
        return switch (room) {
            case "genkan" -> st(ModBlocks.GENKAN_TILE);
            case "hall", "western" -> st(ModBlocks.OLD_FLOORING);
            case "toilet", "bath" -> st(ModBlocks.BATH_TILE_FLOOR);
            case "washroom", "dk" -> st(ModBlocks.VINYL_FLOOR);
            case "closet", "western_closet" -> st(ModBlocks.OSHIIRE_FLOOR);
            default -> st(ModBlocks.CONCRETE);
        };
    }

    /** The tatami: mats two blocks long; the row along the north side runs east-west, the rest north-south. */
    static BlockState tatami(int x, int z) {
        int col = x - Site.UNIT_X0[Math.max(0, Plan.unitOf(x))];
        Direction.Axis axis = z % 14 == 9 || (z % 14 == 12 && col >= 2) ? Direction.Axis.X : Direction.Axis.Z;
        boolean worn = Site.noise(x + 0.5, z + 0.5, 1.7, 401) > 0.62;
        return (worn ? ModBlocks.TATAMI_WORN : ModBlocks.TATAMI_FLOOR).get().defaultBlockState().setValue(com.hitori.block.TatamiBlock.AXIS, axis);
    }

    // ------------------------------------------------------------------------------------------------ damage
    /** Where the roof and the pipes leak (world x, z): stains spread on the ceiling and run down the walls nearby. */
    static final int[][] LEAKS = {{31, 3}, {24, 6}, {32, 12}, {27, 12}, {29, 1}, {9, 6}, {41, 11}, {15, 2}};

    static double leakDistance(int x, int z) {
        double best = 99;
        for (int[] l : LEAKS) best = Math.min(best, Math.sqrt((x - l[0]) * (x - l[0]) + (z - l[1]) * (z - l[1])));
        return best;
    }

    static boolean stainedCeiling(int x, int z) {
        double d = leakDistance(x, z);
        if (d < 1.1) return true;
        if (d < 2.1) return Site.rand(x, z, 411) < 0.45;
        return Site.noise(x + 13.0, z + 7.0, 2.4, 412) > 0.8 && Site.rand(x, z, 413) < 0.7;
    }

    static boolean stainedWall(int wx, int y, int wz, int nx, int nz, double threshold) {
        double d = leakDistance(nx, nz);
        int side = (nx - wx) * 3 + (nz - wz) * 7;
        if (d < 2.3) {
            double p = y % 5 == 2 ? 0.7 : y % 5 == 1 ? 0.45 : 0.2;
            if (Site.rand(wx * 5 + side, wz * 3 + y * 11, 421) < p) return true;
        }
        boolean alongX = wz != nz;
        double along = alongX ? wx : wz;
        double across = alongX ? wz : wx;
        return Site.noise(along + side * 31.0 + across * 7.0, y * 1.9, 2.2, 422) > threshold && Site.rand(wx + side, wz * 13 + y, 423) < 0.85;
    }

    // ------------------------------------------------------------------------------------------------ wall faces
    /** The finish of a wall face seen from room cell (nx, nz) at height y. */
    static Props.Face face(String room, boolean upper, int nx, int y, int nz, int wx, int wz) {
        int rel = y - roomFeet(room, upper);
        boolean low = rel <= 0;
        return switch (room) {
            case "genkan", "hall", "toilet" -> low ? Props.Face.WOOD_WAINSCOT
                    : stainedWall(wx, y, wz, nx, nz, 0.8) ? Props.Face.PLASTER_WALL_STAINED : Props.Face.PLASTER_WALL;
            case "dk", "washroom" -> stainedWall(wx, y, wz, nx, nz, 0.72) ? Props.Face.PLASTER_WALL_STAINED : Props.Face.PLASTER_WALL;
            case "tatami" -> stainedWall(wx, y, wz, nx, nz, 0.82) ? Props.Face.PLASTER_WALL_STAINED : Props.Face.PLASTER_WALL;
            case "bath" -> Props.Face.BATH_TILE_WALL;
            case "western" -> Props.Face.WALLPAPER_OLD;
            case "closet", "western_closet" -> Props.Face.OSHIIRE_FLOOR;
            default -> Props.Face.PLASTER_WALL;
        };
    }

    static BlockState plain(Props.Face f) {
        return switch (f) {
            case PLASTER_WALL -> st(ModBlocks.PLASTER_WALL);
            case PLASTER_WALL_STAINED -> st(ModBlocks.PLASTER_WALL_STAINED);
            case WALLPAPER_OLD -> st(ModBlocks.WALLPAPER_OLD);
            case WOOD_WAINSCOT -> st(ModBlocks.WOOD_WAINSCOT);
            case BATH_TILE_WALL -> st(ModBlocks.BATH_TILE_WALL);
            case OSHIIRE_FLOOR -> st(ModBlocks.OSHIIRE_FLOOR);
            case APARTMENT_SIDING -> st(ModBlocks.APARTMENT_SIDING);
            case CONCRETE -> st(ModBlocks.CONCRETE);
        };
    }

    /** The face a wall cell shows toward its neighbour (nx, nz) at height y; null when the neighbour is wall too. */
    static Props.Face neighbourFace(int wx, int y, int wz, int nx, int nz) {
        boolean upper = y >= F2 - 1;
        if (!Site.inBuilding(nx, nz)) return y < F1 ? Props.Face.CONCRETE : Props.Face.APARTMENT_SIDING;
        String room = Plan.roomAtWorld(nx, nz);
        return room == null ? null : face(room, upper, nx, y, nz, wx, wz);
    }

    /** The block of a wall-line cell at height y: one face per neighbouring room, two different ones become a two-sided wall. */
    public static BlockState wall(int x, int y, int z) {
        Props.Face[] faces = new Props.Face[4];
        for (Direction d : Direction.Plane.HORIZONTAL) faces[d.get2DDataValue()] = neighbourFace(x, y, z, x + d.getStepX(), z + d.getStepZ());
        Props.Face outer = null;
        int best = 0;
        for (Props.Face cand : faces) {
            if (cand == null) continue;
            int n = 0;
            for (Props.Face o : faces) if (o == cand) n++;
            boolean ext = cand == Props.Face.APARTMENT_SIDING || cand == Props.Face.CONCRETE;
            if (n > best || (n == best && ext)) {
                best = n;
                outer = cand;
            }
        }
        if (outer == null) return st(ModBlocks.PLASTER_WALL);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Props.Face fc = faces[d.get2DDataValue()];
            if (fc != null && fc != outer) return ((TwoSidedWallBlock) ModBlocks.TWO_SIDED_WALL.get()).of(d, fc, outer);
        }
        return plain(outer);
    }

    static boolean exteriorCell(int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (!Site.inBuilding(x + d.getStepX(), z + d.getStepZ())) return true;
        return false;
    }

    static void wallColumn(BlockSink s, int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) s.set(x, y, z, wall(x, y, z));
    }

    // ------------------------------------------------------------------------------------------------ one unit
    static void unit(BlockSink s, int u, boolean upper) {
        int x0 = Site.UNIT_X0[u];
        if (!s.touches(x0, 0, x0 + 11, 13)) return;
        int feet = feetOf(upper), floorY = feet - 1, ceil = feet + 3;
        boolean is203 = upper && x0 == X0;
        for (int col = 0; col < Plan.W; col++) {
            for (int row = 0; row < Plan.D; row++) {
                int x = x0 + col, z = row;
                if (!s.contains(x, z)) continue;
                char c = Plan.at(col, row);
                // shared walls belong to the western unit; the eastern one does not build them again
                if (col == 0 && u > 0) continue;
                String room = Plan.roomOf(c);
                if (!upper) s.set(x, floorY - 1, z, st(ModBlocks.CONCRETE));
                if (room != null) {
                    roomCell(s, x, z, room, col, upper, floorY, feet, ceil, is203);
                } else {
                    wallCell(s, x, z, c, col, row, upper, floorY, feet, ceil, is203, u);
                }
            }
        }
    }

    static void roomCell(BlockSink s, int x, int z, String room, int col, boolean upper, int floorY, int feet, int ceil, boolean is203) {
        if ("genkan".equals(room) && upper) {
            s.set(x, G2 - 1, z, st(ModBlocks.GENKAN_TILE));
            s.set(x, G2, z, col == 3 ? st(ModBlocks.GENKAN_STEP) : air());
        } else {
            s.set(x, floorY, z, "tatami".equals(room) ? tatami(x, z) : floorOf(room));
        }
        for (int y = feet; y < ceil; y++) s.set(x, y, z, air());
        BlockState ceiling = switch (room) {
            case "bath" -> st(ModBlocks.BATH_TILE_WALL);
            case "closet", "western_closet" -> st(ModBlocks.OSHIIRE_FLOOR);
            default -> stainedCeiling(x, z) ? st(ModBlocks.CEILING_BOARD_STAINED) : st(ModBlocks.CEILING_BOARD);
        };
        s.set(x, ceil, z, ceiling);
    }

    static void wallCell(BlockSink s, int x, int z, char c, int col, int row, boolean upper, int floorY, int feet, int ceil, boolean is203, int u) {
        int bottom = floorY;
        // the floor under openings: the threshold
        switch (c) {
            case 'D' -> {
                int g = upper ? G2 : feet;
                s.set(x, g - 1, z, st(ModBlocks.GENKAN_TILE));
                if (is203) door(s, ModBlocks.FRONT_DOOR, x, g, z, S, DoorHingeSide.LEFT);
                else door(s, ModBlocks.UNIT_DOOR, x, g, z, S, DoorHingeSide.LEFT);
                wallColumn(s, x, z, g + 2, ceil - 1);
            }
            case 'd' -> {
                s.set(x, floorY, z, st(ModBlocks.OLD_FLOORING));
                if (row == 3) door(s, ModBlocks.BATH_DOOR, x, feet, z, N, DoorHingeSide.LEFT);
                else if (row == 2) door(s, ModBlocks.TOILET_DOOR, x, feet, z, E, DoorHingeSide.LEFT);
                else door(s, ModBlocks.INTERIOR_DOOR, x, feet, z, E, DoorHingeSide.LEFT);
                wallColumn(s, x, z, feet + 2, ceil - 1);
            }
            case 'f' -> {
                s.set(x, floorY, z, st(ModBlocks.OLD_FLOORING));
                halves(s, f(ModBlocks.FUSUMA, N).setValue(Props.OPEN3, 0), x, feet, z);
                wallColumn(s, x, z, feet + 2, ceil - 1);
            }
            case 'W' -> {
                s.set(x, floorY, z, st(ModBlocks.OLD_FLOORING));
                halves(s, st(ModBlocks.BALCONY_DOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, N).setValue(Props.OPEN, false), x, feet, z);
                s.set(x, feet + 2, z, f(ModBlocks.RAIN_WINDOW, N));
            }
            default -> {
                wallColumn(s, x, z, bottom, ceil - 1);
                windows(s, x, z, col, row, upper, feet, u);
            }
        }
        // the ceiling layer: walls end under the ceiling boards, the outside shows the siding
        s.set(x, ceil, z, exteriorCell(x, z) ? st(ModBlocks.APARTMENT_SIDING) : st(ModBlocks.PLASTER_WALL));
    }

    /** Small frosted windows on the corridor side (genkan, toilet, bath) and windows in the building's end walls. */
    static void windows(BlockSink s, int x, int z, int col, int row, boolean upper, int feet, int u) {
        if (row == 0) {
            if (col == 1) s.set(x, (upper ? G2 : feet) + 2, z, f(ModBlocks.RAIN_WINDOW, N));
            if (col == 7 || col == 9 || col == 10) s.set(x, feet + 2, z, f(ModBlocks.RAIN_WINDOW, N));
        }
        // the building's ends: 201/101's DK looks west, 204/104's western room looks east
        if (x == Site.BX1 && (row == 5 || row == 6)) s.set(x, feet + 1, z, f(ModBlocks.RAIN_WINDOW, W));
        if (x == Site.BX2 && (row == 9 || row == 10)) {
            s.set(x, feet + 1, z, f(ModBlocks.RAIN_WINDOW, E));
            s.set(x, feet, z, wall(x, feet, z));
        }
    }

    // ================================================================================================ corridor and stairs
    public static final int CORR_X1 = -1, CORR_X2 = 44;

    static void corridor(BlockSink s) {
        if (!s.touches(CORR_X1 - 1, -5, CORR_X2 + 1, 0)) return;
        BlockState slab = st(ModBlocks.CORRIDOR_CONCRETE);
        for (int x = CORR_X1; x <= CORR_X2; x++) {
            for (int z = -4; z <= -1; z++) {
                if (!s.contains(x, z)) continue;
                // the ground floor's path under it
                if (z >= -3) s.set(x, Site.GROUND, z, st(ModBlocks.CONCRETE));
                for (int y = F1; y < G2 - 1; y++) s.set(x, y, z, air());
                if (z >= -3) {
                    s.set(x, G2 - 1, z, slab);
                    for (int y = G2; y < 73; y++) s.set(x, y, z, air());
                }
                // the soffit (the corridor's ceiling under the roof's eave)
                s.set(x, 73, z, slab);
            }
            // the parapet: a waist-high wall of siding on the north edge
            if (s.contains(x, -3)) s.set(x, G2, -3, st(ModBlocks.APARTMENT_SIDING));
        }
        // the landing's open south side at the stairs
        if (s.contains(CORR_X1, 0)) s.set(CORR_X1, G2, 0, f(ModBlocks.STAIRS_RAIL, N));
        // posts under the corridor
        for (int x = CORR_X1; x <= CORR_X2; x += 7) {
            int px = Math.min(x, CORR_X2);
            for (int y = F1; y < G2 - 1; y++) s.post(px, y, -3, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
        }
        for (int y = F1; y < G2 - 1; y++) s.post(CORR_X2, y, -3, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
        // lamps over every door (upstairs and down)
        for (int x0 : Site.UNIT_X0) {
            s.set(x0 + 4, 72, -2, st(ModBlocks.FLUORESCENT_RING).setValue(Props.LIT, true));
            s.set(x0 + 4, G2 - 2, -2, st(ModBlocks.FLUORESCENT_RING).setValue(Props.LIT, true));
        }
    }

    /** The steel stairs at the west end, rising east to the corridor's landing. */
    static void stairs(BlockSink s) {
        if (!s.touches(-8, -4, 0, 1)) return;
        BlockState step = Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS.defaultBlockState().setValue(StairBlock.FACING, E).setValue(StairBlock.HALF, Half.BOTTOM);
        for (int i = 0; i < 4; i++) {
            int x = -5 + i, y = F1 + i;
            for (int z = -2; z <= -1; z++) {
                s.post(x, y, z, step);
                for (int yy = y + 1; yy < 73; yy++) s.set(x, yy, z, air());
            }
            // the rails on both sides (the art's rail rises toward its model's north: facing east it climbs with the steps)
            s.set(x, y, -3, f(ModBlocks.STAIRS_RAIL, E));
            s.set(x, y, 0, f(ModBlocks.STAIRS_RAIL, E));
        }
    }

    // ================================================================================================ balconies
    static void balconies(BlockSink s) {
        if (!s.touches(0, 14, 44, 17)) return;
        BlockState pad = st(ModBlocks.CONCRETE);
        for (int x = Site.BX1; x <= Site.BX2; x++) {
            for (int z = 14; z <= 15; z++) {
                if (!s.contains(x, z)) continue;
                // the upper balcony's slab, the ground floor's terrace under it
                s.set(x, Site.GROUND, z, pad);
                for (int y = F1; y < F2 - 1; y++) s.set(x, y, z, air());
                s.set(x, F2 - 1, z, pad);
                for (int y = F2; y < 73; y++) s.set(x, y, z, air());
                s.set(x, 73, z, st(ModBlocks.CORRIDOR_CONCRETE));
            }
            if (s.contains(x, 16)) s.set(x, 73, 16, st(ModBlocks.CORRIDOR_CONCRETE));
            // railings along the front (upstairs) and a low wall in front of the terraces
            if (s.contains(x, 15)) {
                s.post(x, F2, 15, Blocks.IRON_BARS.defaultBlockState());
                s.set(x, F1, 15, st(ModBlocks.BLOCK_WALL));
            }
        }
        // partitions between the balconies (隔て板) and at the ends
        for (int x : new int[]{0, 11, 22, 33, 44}) {
            for (int z = 14; z <= 15; z++) {
                for (int y = F2; y <= F2 + 1; y++) s.set(x, y, z, st(ModBlocks.APARTMENT_SIDING));
                for (int y = F1; y <= F1 + 1; y++) s.set(x, y, z, st(ModBlocks.BLOCK_WALL));
            }
        }
        // every unit's air conditioner outside unit; 203's balcony has more life (furnish203)
        for (int x0 : Site.UNIT_X0) {
            if (x0 != 11) s.set(x0 + 10, F2, 14, f(ModBlocks.AC_OUTDOOR_UNIT, S));
            s.set(x0 + 10, F1, 14, f(ModBlocks.AC_OUTDOOR_UNIT, S));
        }
    }

    // ================================================================================================ roof
    public static final int ROOF_X1 = -2, ROOF_X2 = 46, ROOF_Z1 = -4, ROOF_Z2 = 16, RIDGE_Z = 6;

    /** Courses from the eave (0) to the ridge (10). */
    static int course(int z) {
        return z <= RIDGE_Z ? z - ROOF_Z1 : ROOF_Z2 - z;
    }

    static void roof(BlockSink s) {
        if (!s.touches(ROOF_X1, ROOF_Z1, ROOF_X2, ROOF_Z2)) return;
        BlockState slabB = st(ModBlocks.ROOF_SLATE_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState slabT = st(ModBlocks.ROOF_SLATE_SLAB).setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int x = ROOF_X1; x <= ROOF_X2; x++) {
            for (int z = ROOF_Z1; z <= ROOF_Z2; z++) {
                if (!s.contains(x, z)) continue;
                int i = course(z);
                int y = 74 + i / 2;
                for (int yy = 74; yy <= 80; yy++) s.set(x, yy, z, air());
                s.set(x, y, z, i % 2 == 0 ? slabB : slabT);
                // the gable walls close the attic at the building's ends
                if ((x == Site.BX1 || x == Site.BX2) && z >= Site.BZ1 - 3 && z <= Site.BZ2 + 2) {
                    for (int yy = 74; yy < y; yy++) s.set(x, yy, z, st(ModBlocks.APARTMENT_SIDING));
                }
            }
            // the ridge cap
            if (s.contains(x, RIDGE_Z)) s.set(x, 79, RIDGE_Z, st(ModBlocks.ROOF_SLATE));
        }
        // eaves: the soffit edge over the corridor and over the balconies
        for (int x = ROOF_X1; x <= ROOF_X2; x++) {
            if (s.contains(x, ROOF_Z1)) s.set(x, 73, ROOF_Z1, st(ModBlocks.CORRIDOR_CONCRETE));
            if (s.contains(x, ROOF_Z2)) s.set(x, 73, ROOF_Z2, st(ModBlocks.CORRIDOR_CONCRETE));
        }
        for (int z = ROOF_Z1; z <= ROOF_Z2; z++) {
            for (int x : new int[]{ROOF_X1, ROOF_X1 + 1, ROOF_X2 - 1, ROOF_X2}) {
                if (s.contains(x, z) && (z < Site.BZ1 || z > Site.BZ2 || x < Site.BX1 || x > Site.BX2)) s.set(x, 73, z, st(ModBlocks.CORRIDOR_CONCRETE));
            }
        }
        // downpipes from the gutters at the corners
        int[][] pipes = {{ROOF_X1, ROOF_Z1}, {ROOF_X2, ROOF_Z1}, {ROOF_X1, ROOF_Z2}, {ROOF_X2, ROOF_Z2}};
        for (int[] p : pipes) for (int y = F1; y <= 72; y++) s.post(p[0], y, p[1], Blocks.IRON_BARS.defaultBlockState());
    }

    // ================================================================================================ façade details
    static void facade(BlockSink s) {
        if (!s.touches(-2, -2, 45, 1)) return;
        for (int u = 0; u < 4; u++) {
            int x0 = Site.UNIT_X0[u];
            // nameplates beside every door, outside on the corridor side
            s.set(x0 + 3, G2 + 1, -1, f(ModBlocks.NAMEPLATE, N).setValue(Props.UNIT, 4 + u));
            s.set(x0 + 3, F1 + 1, -1, f(ModBlocks.NAMEPLATE, N).setValue(Props.UNIT, u));
        }
        // 202 is empty: 「入居者募集」 on the wall beside its door
        s.set(Site.UNIT_X0[1] + 1, G2 + 1, -1, f(ModBlocks.VACANCY_SIGN, N));
        // a broken umbrella and a bag of rubbish by 201's door, a puddle blown in by the rain
        s.set(Site.UNIT_X0[0] + 1, G2, -1, f(ModBlocks.UMBRELLA, N));
        for (int x = CORR_X1; x <= CORR_X2; x++) {
            if (Site.noise(x, -2.5, 3.0, 431) > 0.64) s.set(x, G2, -2, st(ModBlocks.PUDDLE));
        }
    }

    // ================================================================================================ the yard
    static void yard(BlockSink s) {
        // the mailboxes on the building's west wall by the stairs
        s.set(Site.BX1 - 1, F1 + 1, 1, f(ModBlocks.MAILBOXES, W));
        s.set(Site.BX1 - 1, F1 + 1, 2, f(ModBlocks.MAILBOXES, W));
        // the bicycle shelter: four posts and a sheet-metal roof over three bicycles
        if (s.touches(-9, 3, -4, 11)) {
            for (int[] p : new int[][]{{-8, 3}, {-4, 3}, {-8, 10}, {-4, 10}}) {
                for (int y = F1; y <= F1 + 2; y++) s.post(p[0], y, p[1], Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            }
            BlockState sheet = Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(TrapDoorBlock.OPEN, false);
            for (int x = -8; x <= -4; x++) for (int z = 3; z <= 10; z++) s.set(x, F1 + 3, z, sheet);
            s.set(-6, F1, 4, f(ModBlocks.BICYCLE, W));
            s.set(-6, F1, 6, f(ModBlocks.BICYCLE, W));
            s.set(-7, F1, 9, f(ModBlocks.BICYCLE, E));
        }
        // the rubbish point by the stairs, a bag left in front of 201
        s.set(-2, F1, 3, f(ModBlocks.TRASH_BAG, W));
        s.set(-2, F1, 4, f(ModBlocks.TRASH_BAG, S));
        s.set(3, G2, -1, f(ModBlocks.TRASH_BAG, N));
        // the building's name over the mailboxes (its text is written by Fixtures.ensureBuilt)
        s.set(Site.BX1 - 1, F1 + 2, 1, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, W));
        // a couple of potted plants left to die
        s.set(-1, F1, 12, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        s.set(45, F1, -4, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
    }

    // ================================================================================================ room 203
    static void furnish203(BlockSink s) {
        if (!s.touches(X0, -1, X0 + 11, 16)) return;
        int y = F2, g = G2;
        // ---- genkan
        s.set(23, g, 2, f(ModBlocks.SHOE_CABINET, E));
        s.set(23, g, 1, f(ModBlocks.UMBRELLA, E));
        s.set(24, g, 2, f(ModBlocks.SHOES, N));
        s.set(24, g, 1, f(ModBlocks.SHOES, W));
        s.set(23, y, 2, f(ModBlocks.INTERCOM, E));
        s.set(23, y + 1, 1, f(ModBlocks.BREAKER_PANEL, E).setValue(Props.LIT, true));
        // ---- hall: the switches by the genkan, the mirror, the calendar
        light(s, "switch_genkan", false);
        light(s, "switch_hall", false);
        light(s, "switch_toilet", false);
        light(s, "switch_washroom", false);
        light(s, "switch_bath", false);
        light(s, "switch_dk", false);
        light(s, "switch_western", true);
        s.set(26, y + 1, 3, f(ModBlocks.MIRROR_SMALL, E));
        s.set(27, y + 1, 6, f(ModBlocks.CALENDAR, W));
        // ---- toilet
        s.set(29, y, 1, f(ModBlocks.TOILET_SEAT, S));
        // ---- bath: the deep tub along the east wall, its tap, the shower over the washing place, stool and bucket
        s.set(32, y, 1, f(ModBlocks.BATHTUB, N).setValue(Props.PART, Props.Part.HEAD));
        s.set(32, y, 2, f(ModBlocks.BATHTUB, N).setValue(Props.PART, Props.Part.FOOT));
        s.set(32, y + 1, 1, f(ModBlocks.BATH_TAP, S));
        s.set(31, y + 1, 1, f(ModBlocks.SHOWER, E));
        s.set(31, y, 1, f(ModBlocks.BATH_STOOL, E));
        s.set(31, y, 2, f(ModBlocks.BUCKET, N));
        // ---- washroom
        s.set(29, y, 4, f(ModBlocks.WASHING_MACHINE, S));
        s.set(32, y, 5, f(ModBlocks.WASHBASIN, W));
        s.set(32, y + 1, 5, f(ModBlocks.MIRROR_SMALL, W));
        s.set(32, y + 1, 4, Blocks.LIGHT_BLUE_WALL_BANNER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, W));
        // ---- DK: the kitchen line along the west wall, the small table, the rice
        s.set(23, y, 4, f(ModBlocks.FRIDGE_OLD, E).setValue(Props.ON, true));
        s.set(23, y + 1, 4, f(ModBlocks.MICROWAVE, E));
        s.set(23, y, 5, f(ModBlocks.KITCHEN_SINK, E));
        s.set(23, y + 1, 5, f(ModBlocks.CUP_GLASS, E));
        s.set(23, y, 6, f(ModBlocks.GAS_STOVE, E));
        s.set(23, y + 1, 6, f(ModBlocks.KNIFE_RACK, E).setValue(Props.HAS_KNIFE, true));
        s.set(23, y, 7, f(ModBlocks.DRAWER, E));
        s.set(23, y + 1, 7, f(ModBlocks.RICE_COOKER, E));
        s.set(24, y, 4, f(ModBlocks.DRAWER, S));
        s.set(24, y + 1, 4, f(ModBlocks.SALT_SHAKER, S));
        s.set(25, y, 4, f(ModBlocks.RICE_BAG, S));
        s.set(25, y, 5, f(ModBlocks.DINING_TABLE, N));
        s.set(25, y + 1, 5, f(ModBlocks.CUP_GLASS, N));
        s.set(26, y, 5, f(ModBlocks.DINING_CHAIR, W));
        s.set(25, y, 6, f(ModBlocks.DINING_CHAIR, N));
        s.set(24, y + 2, 4, f(ModBlocks.WALL_CLOCK, S));
        s.set(26, y, 4, f(ModBlocks.CARDBOARD_BOX, W));
        s.set(26, y, 7, f(ModBlocks.TRASH_BAG, W));
        s.set(23, y + 2, 4, f(ModBlocks.CALENDAR, E));
        // ---- the tatami room
        s.set(27, y, 9, f(ModBlocks.TV_STAND, W));
        s.set(27, y + 1, 9, f(ModBlocks.CRT_TV, W));
        s.set(25, y, 11, f(ModBlocks.CHABUDAI, N));
        s.set(25, y, 10, f(ModBlocks.ZABUTON, S));
        s.set(26, y, 11, f(ModBlocks.ZABUTON, W));
        s.set(25, y + 2, 11, f(ModBlocks.HANGING_LAMP, N));
        s.set(25, y + 1, 11, f(ModBlocks.PULL_SWITCH, N));
        s.set(27, y, 12, f(ModBlocks.ALTAR_SHELF, W));
        for (int yy = y; yy <= y + 2; yy++) {
            s.set(24, yy, 12, f(ModBlocks.CURTAIN, N).setValue(Props.CLOSED, true));
            s.set(25, yy, 12, f(ModBlocks.CURTAIN, N).setValue(Props.CLOSED, true));
            s.set(26, yy, 12, f(ModBlocks.CURTAIN, N).setValue(Props.CLOSED, true));
        }
        // ---- the 押し入れ: two-high fusuma in the closet's column, futons and a box on the middle shelf
        for (int z = 10; z <= 12; z++) halves(s, f(ModBlocks.OSHIIRE_FUSUMA, E).setValue(Props.OPEN3, 0), 23, y, z);
        s.set(23, y + 2, 10, f(ModBlocks.FUTON_FOLDED, E).setValue(Props.SHELF, true));
        s.set(23, y + 2, 11, f(ModBlocks.FUTON_FOLDED, E).setValue(Props.SHELF, true));
        s.set(23, y + 2, 12, f(ModBlocks.CARDBOARD_BOX, E).setValue(Props.SHELF, true));
        // ---- the western room
        s.set(30, y, 7, f(ModBlocks.DESK, S));
        s.set(31, y, 7, f(ModBlocks.DESK, S));
        s.set(32, y, 7, f(ModBlocks.DESK, S));
        s.set(32, y, 8, f(ModBlocks.DESK, W));
        s.set(30, y + 1, 7, f(ModBlocks.PRINTOUT, S));
        s.set(31, y + 1, 7, f(ModBlocks.SEWING_BOX, S));
        s.set(32, y + 1, 7, f(ModBlocks.DESK_LAMP, W).setValue(Props.LIT, true));
        s.set(32, y + 1, 8, f(ModBlocks.LAPTOP_BLOCK, W).setValue(Props.OPEN, true));
        s.set(31, y, 8, f(ModBlocks.DESK_CHAIR, E));
        s.set(29, y, 9, f(ModBlocks.CAMERA_BAG, E));
        s.set(29, y, 10, f(ModBlocks.SHELF, E));
        s.set(29, y + 1, 10, f(ModBlocks.PLUSH_DOLL, E).setValue(Props.PRESENT, true));
        s.set(29, y, 11, f(ModBlocks.BED, N).setValue(Props.PART, Props.Part.HEAD));
        s.set(29, y, 12, f(ModBlocks.BED, N).setValue(Props.PART, Props.Part.FOOT));
        s.set(29, y + 1, 7, f(ModBlocks.POSTER, S));
        for (int yy = y; yy <= y + 2; yy++) {
            s.set(30, yy, 12, f(ModBlocks.CURTAIN, N).setValue(Props.CLOSED, false));
            s.set(31, yy, 12, f(ModBlocks.CURTAIN, N).setValue(Props.CLOSED, true));
        }
        s.set(31, y + 2, 7, f(ModBlocks.POSTER, S));
        // washing hung up indoors on a rainy night (部屋干し)
        s.set(30, y + 2, 11, f(ModBlocks.LAUNDRY_POLE, N));
        s.set(31, y + 2, 11, f(ModBlocks.LAUNDRY_POLE, N));
        s.set(30, y + 1, 11, f(ModBlocks.LAUNDRY_HANGING, N));
        s.set(31, y + 1, 11, f(ModBlocks.LAUNDRY_HANGING, S));
        // the western closet: sliding doors in its own column, boxes on the shelf above
        for (int z = 10; z <= 12; z++) halves(s, f(ModBlocks.OSHIIRE_FUSUMA, W).setValue(Props.OPEN3, 0), 32, y, z);
        s.set(32, y + 2, 10, f(ModBlocks.CARDBOARD_BOX, W).setValue(Props.SHELF, true));
        s.set(32, y + 2, 12, f(ModBlocks.FUTON_FOLDED, W).setValue(Props.SHELF, true));
        // ---- the balcony: the laundry pole, a forgotten towel, the air conditioner, a dead plant
        for (int x = 24; x <= 30; x++) s.set(x, y + 2, 14, f(ModBlocks.LAUNDRY_POLE, N));
        s.set(27, y + 1, 14, f(ModBlocks.LAUNDRY_HANGING, N));
        s.set(29, y + 1, 14, f(ModBlocks.LAUNDRY_HANGING, S));
        s.set(23, y, 14, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        s.set(27, y, 15, st(ModBlocks.PUDDLE));
        s.set(31, y, 14, st(ModBlocks.PUDDLE));
        // the other units: curtains drawn (202 is empty), a TV's glow behind 204's
        for (int x0 : Site.UNIT_X0) {
            if (x0 == X0) continue;
            for (boolean upper : new boolean[]{false, true}) {
                int fy = feetOf(upper);
                if (upper && x0 == 11) continue;
                for (int cx : new int[]{2, 3, 4, 8, 9}) {
                    for (int yy = fy; yy <= fy + 2; yy++) s.set(x0 + cx, yy, 12, f(ModBlocks.CURTAIN, N).setValue(Props.CLOSED, true));
                }
            }
        }
        s.set(Site.UNIT_X0[3] + 3, F2 + 1, 10, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 6));
    }

    /** A light switch at its marker (on = its initial position). */
    static void light(BlockSink s, String marker, boolean on) {
        Layout.Marker m = Layout.marker(marker);
        BlockPos p = m.pos();
        s.set(p.getX(), p.getY(), p.getZ(), f(ModBlocks.LIGHT_SWITCH, m.facing()).setValue(Props.ON, on));
    }

    /** 203's ceiling lights, lit as at the start of the evening (only the western room). */
    static void lights203(BlockSink s) {
        if (!s.touches(X0, 0, X0 + 11, 13)) return;
        for (String room : Layout.LIGHT_ROOMS) {
            boolean on = "western".equals(room);
            for (Layout.Marker m : Layout.markers("lights_" + room)) {
                BlockPos p = m.pos();
                if ("tatami".equals(room)) continue; // the hanging lamp is placed with the furniture
                s.set(p.getX(), p.getY(), p.getZ(), st(ModBlocks.FLUORESCENT_RING).setValue(Props.LIT, on));
            }
        }
    }

    /** Every lamp of 203 with its room (for the circuits). */
    /** The sign with the building's name. */
    public static final BlockPos NAME_SIGN = new BlockPos(Site.BX1 - 1, F1 + 2, 1);

    public static List<BlockPos> lamps203() {
        List<BlockPos> out = new ArrayList<>();
        for (String room : Layout.LIGHT_ROOMS) for (Layout.Marker m : Layout.markers("lights_" + room)) out.add(m.pos());
        return out;
    }
}
