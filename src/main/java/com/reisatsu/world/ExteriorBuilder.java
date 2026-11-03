package com.reisatsu.world;

import com.reisatsu.block.FacingBlock;
import com.reisatsu.block.LineBlock;
import com.reisatsu.block.Props;
import com.reisatsu.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The property around the restaurant: the entrance apron with its canopy props, the parking lot (lines, wheel stops,
 * puddles, sodium lamps, the pole sign), the staff path along the west wall to the back door, the back yard (A/C
 * units, gas cylinders, the exhaust, dumpsters, the staff bicycle) and the side strips with their hedges.
 */
public final class ExteriorBuilder {
    private ExteriorBuilder() {}

    static final int G = Terrain.GROUND, Y = Layout.FEET_Y;
    static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    /** Pole sign: pole column and the two layers of its 4x4 lit box. */
    public static final int SIGN_POLE_X = 46, SIGN_Z_BACK = 48, SIGN_Z_FRONT = 49, SIGN_X0 = 45, SIGN_Y0 = Y + 8;
    /** The lot's entrance from the road (no curb). */
    public static final int ENTRANCE_X1 = 38, ENTRANCE_X2 = 45;
    /** Lamps of the lot (pole positions) and their facing. */
    static final int[][] LOT_LAMPS = {{-8, 40, 1}, {4, 49, 2}, {20, 49, 2}, {36, 49, 2}, {48, 40, 3}};

    static BlockState st(RegistryObject<? extends Block> b) {
        return b.get().defaultBlockState();
    }

    static BlockState f(RegistryObject<? extends Block> b, Direction d) {
        return b.get().defaultBlockState().setValue(FacingBlock.FACING, d);
    }

    public static void build(BlockSink s) {
        if (!s.touches(Site.PROP_X1 - 1, Site.PROP_Z1 - 1, Site.LOT_X2 + 1, Site.LOT_Z2 + 1)) return;
        ground(s);
        lot(s);
        apron(s);
        sides(s);
        backYard(s);
        poleSign(s);
        for (int[] l : LOT_LAMPS) Terrain.lamp(s, l[0], l[1], l[2] == 1 ? E : l[2] == 2 ? N : W);
    }

    // ------------------------------------------------------------------------------------------------ ground
    static void ground(BlockSink s) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = Site.PROP_X1; x <= Site.LOT_X2; x++) {
            for (int z = Site.PROP_Z1; z <= Site.LOT_Z2 + 1; z++) {
                if (!s.contains(x, z) || Plan.inside(x, z)) continue;
                BlockState top;
                if (Site.inLot(x, z)) top = st(ModBlocks.ASPHALT);
                else if (!Site.inProperty(x, z)) continue;
                else if (z >= 31) top = st(ModBlocks.SIDEWALK);                                  // the apron
                else if (z <= -1 && x >= -3 && x <= 44) top = st(ModBlocks.ASPHALT);               // the back yard
                else if (x >= -3 && x <= -1) top = st(ModBlocks.SIDEWALK);                        // the staff path
                else if (x >= 41 && x <= 42) top = st(ModBlocks.SIDEWALK);                        // the east walk
                else top = Site.rand(x, z, 71) < 0.25 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                s.set(x, G - 1, z, Blocks.DIRT.defaultBlockState());
                s.set(x, G, z, top);
                for (int y = G + 1; y <= G + 4; y++) s.set(x, y, z, air);
                boolean paved = top.is(ModBlocks.ASPHALT.get()) || top.is(ModBlocks.SIDEWALK.get());
                if (paved && Site.noise(x, z, 5, 72) > 0.66 && Site.rand(x, z, 73) < 0.9) s.set(x, G + 1, z, st(ModBlocks.PUDDLE));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ lot
    static final int[] NORTH_ROW_LINES = {-7, -4, -1, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45};
    static final int[] SOUTH_ROW_LINES = {-7, -4, -1, 2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35};

    static void lot(BlockSink s) {
        if (!s.touches(Site.LOT_X1, Site.LOT_Z1, Site.LOT_X2, Site.LOT_Z2 + 1)) return;
        BlockState line = st(ModBlocks.PARKING_LINE).setValue(LineBlock.AXIS, Direction.Axis.Z);
        for (int lx : NORTH_ROW_LINES) for (int z = 34; z <= 38; z++) setLine(s, lx, z, line);
        for (int lx : SOUTH_ROW_LINES) for (int z = 44; z <= 48; z++) setLine(s, lx, z, line);
        // wheel stops at the head of each space
        for (int i = 0; i + 1 < NORTH_ROW_LINES.length; i++) {
            int a = NORTH_ROW_LINES[i], b = NORTH_ROW_LINES[i + 1];
            if (b - a != 3) continue;
            s.set(a + 1, Y, 35, f(ModBlocks.WHEEL_STOP, S));
            s.set(a + 2, Y, 35, f(ModBlocks.WHEEL_STOP, S));
        }
        for (int i = 0; i + 1 < SOUTH_ROW_LINES.length; i++) {
            int a = SOUTH_ROW_LINES[i], b = SOUTH_ROW_LINES[i + 1];
            if (b - a != 3) continue;
            s.set(a + 1, Y, 47, f(ModBlocks.WHEEL_STOP, N));
            s.set(a + 2, Y, 47, f(ModBlocks.WHEEL_STOP, N));
        }
        // the curb between the lot and the road, open at the entrance
        for (int x = Site.LOT_X1; x <= Site.LOT_X2; x++) {
            if (x >= ENTRANCE_X1 && x <= ENTRANCE_X2) continue;
            if (s.contains(x, Site.LOT_Z2 + 1)) s.set(x, Y, Site.LOT_Z2 + 1, st(ModBlocks.CURB));
        }
        // a lane arrow: two short lines at the entrance
        for (int z = 44; z <= 46; z++) setLine(s, 41, z, line);
    }

    static void setLine(BlockSink s, int x, int z, BlockState line) {
        if (!s.contains(x, z)) return;
        s.set(x, G, z, line);
        if (s.get(x, Y, z).is(ModBlocks.PUDDLE.get())) return;
        s.set(x, Y, z, Blocks.AIR.defaultBlockState());
    }

    // ------------------------------------------------------------------------------------------------ apron
    static void apron(BlockSink s) {
        if (!s.touches(-10, 30, 45, 36)) return;
        // curb along the apron, cut for the ramp in front of the doors
        for (int x = Site.PROP_X1; x <= 44; x++) {
            if (x >= 1 && x <= 6) continue;
            if (s.contains(x, 33)) s.set(x, Y, 33, st(ModBlocks.CURB));
        }
        // the ramp's handrails
        for (int z = 34; z <= 35; z++) {
            s.post(1, Y, z, Blocks.IRON_BARS.defaultBlockState());
            s.post(6, Y, z, Blocks.IRON_BARS.defaultBlockState());
        }
        // brick sill ledge under the shop windows
        for (int x = 9; x <= 39; x++) s.set(x, Y, 31, st(ModBlocks.BRICK_SILL));
        // the key switch of the automatic doors, the vending machines and their trash station, the menu board, plants
        s.set(5, Y + 1, 31, f(ModBlocks.DOOR_SWITCH, S).setValue(Props.ON, false));
        s.set(6, Y, 31, f(ModBlocks.TRASH_STATION, S));
        for (int x = 7; x <= 8; x++) {
            BlockState v = f(ModBlocks.VENDING_MACHINE, S);
            s.set(x, Y, 31, v.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            s.set(x, Y + 1, 31, v.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        }
        s.set(1, Y, 32, f(ModBlocks.MENU_BOARD, S).setValue(Props.MESSAGE, 0));
        s.set(0, Y, 31, f(ModBlocks.OUTDOOR_PLANTER, S));
        s.set(-1, Y, 31, f(ModBlocks.OUTDOOR_PLANTER, S));
        s.set(40, Y, 31, f(ModBlocks.OUTDOOR_PLANTER, S));
        s.set(41, Y, 31, f(ModBlocks.OUTDOOR_PLANTER, S));
        // the customers' bicycle rack
        for (int x = -7; x <= -5; x++) s.set(x, Y, 32, f(ModBlocks.BIKE_RACK, S));
    }

    // ------------------------------------------------------------------------------------------------ sides
    static void sides(BlockSink s) {
        // west: hedge along the boundary, the staff path, the back door light
        for (int z = Site.PROP_Z1; z <= 32; z++) {
            if (!s.contains(-9, z)) continue;
            s.set(-9, Y, z, st(ModBlocks.HEDGE));
            s.set(-9, Y + 1, z, st(ModBlocks.HEDGE));
        }
        s.set(-1, Y + 3, 7, st(ModBlocks.STAINLESS));
        s.set(-1, Y + 3, 9, st(ModBlocks.STAINLESS));
        s.set(-1, Y + 3, 8, st(ModBlocks.DOWNLIGHT).setValue(Props.LIT, Circuit.SIGN.startOn()));
        s.set(-1, Y, 2, f(ModBlocks.BIKE_RACK, E));                              // the staff bicycle
        s.set(-6, Y, 12, f(ModBlocks.AC_UNIT, E));
        s.set(-6, Y, 13, f(ModBlocks.AC_UNIT, E));
        // east: hedge, the east walk with the sill ledge under the windows, the kitchen's outdoor units
        for (int z = Site.PROP_Z1; z <= 32; z++) {
            if (!s.contains(46, z)) continue;
            s.set(46, Y, z, st(ModBlocks.HEDGE));
            s.set(46, Y + 1, z, st(ModBlocks.HEDGE));
        }
        for (int z = 12; z <= 29; z++) s.set(41, Y, z, st(ModBlocks.BRICK_SILL));
        for (int z = 2; z <= 6; z += 2) s.set(41, Y, z, f(ModBlocks.AC_UNIT, E));
        for (int z = 1; z <= 7; z++) if (s.contains(44, z) && z % 3 == 1) s.set(44, Y, z, f(ModBlocks.OUTDOOR_PLANTER, W));
    }

    // ------------------------------------------------------------------------------------------------ back yard
    static void backYard(BlockSink s) {
        if (!s.touches(-10, -12, 47, 0)) return;
        // the yard wall along the north boundary
        for (int x = Site.PROP_X1; x <= Site.PROP_X2; x++) {
            if (!s.contains(x, Site.PROP_Z1)) continue;
            s.set(x, Y, Site.PROP_Z1, st(ModBlocks.CONCRETE_WALL));
            s.set(x, Y + 1, Site.PROP_Z1, st(ModBlocks.CONCRETE_WALL));
        }
        // outdoor A/C units behind the staff room and office, the walk-in fridge's condenser
        for (int x = 2; x <= 5; x++) s.set(x, Y, -1, f(ModBlocks.AC_UNIT, N));
        s.set(9, Y, -1, f(ModBlocks.AC_UNIT, N));
        s.set(16, Y, -1, f(ModBlocks.AC_UNIT, N));
        s.set(17, Y, -1, f(ModBlocks.AC_UNIT, N));
        s.set(16, Y + 1, -1, f(ModBlocks.AC_UNIT, N));
        // LPG cylinders in their cage
        for (int x = 20; x <= 22; x++) s.set(x, Y, -1, f(ModBlocks.GAS_CYLINDER, N));
        for (int y = Y; y <= Y + 1; y++) {
            s.post(19, y, -1, Blocks.IRON_BARS.defaultBlockState());
            s.post(23, y, -1, Blocks.IRON_BARS.defaultBlockState());
            s.post(19, y, -2, Blocks.IRON_BARS.defaultBlockState());
            s.post(23, y, -2, Blocks.IRON_BARS.defaultBlockState());
            for (int x = 20; x <= 22; x++) s.post(x, y, -2, Blocks.IRON_BARS.defaultBlockState());
        }
        // dumpsters, bins and boxes
        s.set(30, Y, -4, f(ModBlocks.DUMPSTER, S));
        s.set(31, Y, -4, f(ModBlocks.DUMPSTER, S));
        s.set(33, Y, -2, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(34, Y, -2, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(33, Y + 1, -2, f(ModBlocks.CARDBOARD_BOXES, N));
        s.set(36, Y, -2, f(ModBlocks.KITCHEN_TRASH, S));
        s.set(37, Y, -2, f(ModBlocks.KITCHEN_TRASH, S));
        s.set(12, Y, -9, f(ModBlocks.TRASH_STATION, S));
        // lights under the eave (outside lighting)
        s.set(12, Y + 4, -1, st(ModBlocks.DOWNLIGHT).setValue(Props.LIT, Circuit.SIGN.startOn()));
        s.set(33, Y + 4, -1, st(ModBlocks.DOWNLIGHT).setValue(Props.LIT, Circuit.SIGN.startOn()));
    }

    // ------------------------------------------------------------------------------------------------ pole sign
    static void poleSign(BlockSink s) {
        if (!s.touches(SIGN_X0, SIGN_Z_BACK, SIGN_X0 + 3, SIGN_Z_FRONT)) return;
        BlockState pole = f(ModBlocks.POLE_SIGN, S).setValue(Props.SIGN_PART, Props.SignPart.POLE);
        for (int y = Y; y < SIGN_Y0; y++) s.set(SIGN_POLE_X, y, SIGN_Z_FRONT, pole);
        for (BlockPos p : signTiles()) {
            int t = tileOf(p);
            Direction face = p.getZ() == SIGN_Z_FRONT ? S : N;
            s.set(p.getX(), p.getY(), p.getZ(), f(ModBlocks.POLE_SIGN, face).setValue(Props.SIGN_PART, Props.SignPart.TILE)
                    .setValue(Props.TILE16, t).setValue(Props.LIT, Circuit.SIGN.startOn()));
        }
    }

    /** The 32 tile positions of the box (front layer faces the road, back layer the lot). */
    public static List<BlockPos> signTiles() {
        List<BlockPos> out = new ArrayList<>();
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 4; col++) {
                int y = SIGN_Y0 + 3 - row;
                out.add(new BlockPos(SIGN_X0 + col, y, SIGN_Z_FRONT));          // faces south: column 0 at the west end
                out.add(new BlockPos(SIGN_X0 + 3 - col, y, SIGN_Z_BACK));       // faces north: column 0 at the east end
            }
        return out;
    }

    /** Tile index 0..15 (row-major from the top-left as seen by the viewer) of a sign tile position. */
    public static int tileOf(BlockPos p) {
        int row = SIGN_Y0 + 3 - p.getY();
        int col = p.getZ() == SIGN_Z_FRONT ? p.getX() - SIGN_X0 : SIGN_X0 + 3 - p.getX();
        return row * 4 + col;
    }

    /** Every light of the SIGN circuit: the pole sign tiles, the roof sign, the lot and road lamps, canopy and yard lights. */
    public static List<BlockPos> signLights() {
        List<BlockPos> out = new ArrayList<>(signTiles());
        for (int t = 0; t < 8; t++) out.add(new BlockPos(16 + t, RestaurantBuilder.CEIL + 3, 31));
        for (int[] l : LOT_LAMPS) out.add(new BlockPos(l[0], Terrain.LAMP_Y, l[1]));
        for (int[] l : Terrain.ROAD_LAMPS) out.add(new BlockPos(l[0], Terrain.LAMP_Y, l[1]));
        for (int x : RestaurantBuilder.SOFFIT_X) out.add(new BlockPos(x, RestaurantBuilder.CEIL, 31));
        out.add(new BlockPos(3, RestaurantBuilder.CEIL, 32));
        out.add(new BlockPos(4, RestaurantBuilder.CEIL, 32));
        out.add(new BlockPos(-1, Y + 3, 8));
        out.add(new BlockPos(12, Y + 4, -1));
        out.add(new BlockPos(33, Y + 4, -1));
        return out;
    }
}
