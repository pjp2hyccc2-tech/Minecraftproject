package com.hitori.world;

import com.hitori.block.FacingBlock;
import com.hitori.block.Props;
import com.hitori.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The quiet street around コーポ白樺, generated per chunk: flat ground, the wet asphalt street with its gutters and
 * puddles, the concrete block walls, dark neighbouring houses (one window lit), the utility poles with their wires and
 * the street lamp in front of 203, the vending machine, the small park with its swing at the street's end, and the
 * invisible walls around everything the player can reach.
 */
public final class Terrain {
    private Terrain() {}

    public static final int GROUND = Site.GROUND;

    // ------------------------------------------------------------------------------------------------ chunk
    public static void build(ChunkSink s, ChunkAccess chunk) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (int x = s.minX; x <= s.maxX; x++) {
            for (int z = s.minZ; z <= s.maxZ; z++) {
                s.set(x, 0, z, bedrock);
                for (int y = 1; y < GROUND - 3; y++) s.set(x, y, z, stone);
                for (int y = GROUND - 3; y < GROUND; y++) s.set(x, y, z, dirt);
                s.set(x, GROUND, z, surface(x, z));
                if (puddle(x, z)) s.set(x, GROUND + 1, z, ModBlocks.PUDDLE.get().defaultBlockState());
            }
        }
        walls(s);
        houses(s);
        park(s);
        poles(s);
        street(s);
        barriers(s);
    }

    /** The ground block of a column. */
    static BlockState surface(int x, int z) {
        if (Site.inStreet(x, z)) {
            if (z == Site.SZ1 - 1 || z == Site.SZ2 + 1) return ModBlocks.CONCRETE.get().defaultBlockState();
            return ModBlocks.ASPHALT_WET.get().defaultBlockState();
        }
        if (Site.inProperty(x, z) || (z == Site.PZ2 + 1 && x >= Site.GATE_X1 && x <= Site.GATE_X2)) {
            // concrete around the building and on the side path, gravel elsewhere
            boolean path = (x >= -3 && x <= -1) || (z >= -3 && z <= -1 && x >= -1) || (x >= Site.GATE_X1 && x <= Site.GATE_X2 && z >= 13)
                    || (x >= -8 && x <= -4 && z >= 3 && z <= 10) || Site.inBuilding(x, z);
            if (path) return ModBlocks.CONCRETE.get().defaultBlockState();
            return Site.rand(x, z, 501) < 0.3 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        }
        if (Site.inPark(x, z)) {
            return Site.noise(x, z, 4, 502) > 0.55 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return Site.rand(x, z, 503) < 0.2 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
    }

    /** Rain water standing in the low spots of the asphalt and the concrete. */
    static boolean puddle(int x, int z) {
        if (Site.inStreet(x, z)) {
            boolean edge = z <= Site.SZ1 + 1 || z >= Site.SZ2 - 1;
            return Site.noise(x, z, edge ? 3.0 : 4.5, 511) > (edge ? 0.6 : 0.7);
        }
        if (Site.inProperty(x, z) && !Site.inBuilding(x, z) && !(z >= -3 && z <= -1 && x >= -1)) {
            return Site.noise(x, z, 2.5, 512) > 0.74;
        }
        return false;
    }

    static BlockState f(net.minecraftforge.registries.RegistryObject<? extends net.minecraft.world.level.block.Block> b, Direction d) {
        return b.get().defaultBlockState().setValue(FacingBlock.FACING, d);
    }

    // ------------------------------------------------------------------------------------------------ walls
    /** Concrete block walls around the property (two high), with the gate gap toward the street. */
    static void walls(ChunkSink s) {
        BlockState wall = ModBlocks.BLOCK_WALL.get().defaultBlockState();
        int x1 = Site.PX1 - 1, x2 = Site.PX2 + 1, z1 = Site.PZ1 - 1, z2 = Site.PZ2 + 1;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (!edge || !s.contains(x, z)) continue;
                if (z == z2 && x >= Site.GATE_X1 && x <= Site.GATE_X2) continue;
                // the park side has a fence instead
                for (int y = GROUND + 1; y <= GROUND + 2; y++) s.set(x, y, z, wall);
            }
        }
        // the walls of the houses' yards across the street
        for (int x = Site.SX1; x <= Site.SX2; x++) {
            int z = Site.SZ2 + 2;
            if (!s.contains(x, z)) continue;
            if (Math.floorMod(x + 3, 13) == 0 || Math.floorMod(x + 4, 13) == 0) continue;
            for (int y = GROUND + 1; y <= GROUND + 2; y++) s.set(x, y, z, wall);
        }
    }

    // ------------------------------------------------------------------------------------------------ houses
    /** x0, z0, width (x), depth (z), lit window side (0 none, 1 south, 2 north, 3 east, 4 west). */
    static final int[][] HOUSES = {
            {-30, 28, 10, 8, 0}, {-15, 28, 10, 8, 0}, {0, 29, 10, 8, 0}, {21, 28, 11, 9, 2}, {36, 29, 10, 7, 0},
            {-8, -18, 11, 9, 0}, {8, -19, 12, 10, 0}, {25, -18, 11, 9, 0}, {41, -18, 10, 9, 0},
            {-27, -4, 11, 12, 0}, {67, 14, 10, 9, 0}, {-30, 10, 9, 8, 0}};

    static void houses(ChunkSink s) {
        for (int[] h : HOUSES) house(s, h[0], h[1], h[2], h[3], h[4]);
    }

    /** A dark two-storey Japanese house with a tiled hip roof; one window may be lit from inside. */
    static void house(ChunkSink s, int x0, int z0, int w, int d, int lit) {
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        if (!s.touches(x0 - 2, z0 - 2, x1 + 2, z1 + 2)) return;
        BlockState wall = Blocks.WHITE_TERRACOTTA.defaultBlockState();
        BlockState wall2 = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
        BlockState wood = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        BlockState dark = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
        BlockState glow = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        int g = GROUND;
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (!s.contains(x, z)) continue;
                s.set(x, g, z, Blocks.GRAVEL.defaultBlockState());
                for (int y = g + 1; y <= g + 14; y++) s.set(x, y, z, Blocks.AIR.defaultBlockState());
            }
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                s.set(x, g, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                s.set(x, g + 4, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (!edge) continue;
                for (int y = g + 1; y <= g + 7; y++) s.set(x, y, z, corner ? wood : (y <= g + 4 ? wall : wall2));
                s.set(x, g + 4, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                // windows: dark glass on both floors, one of them lit from inside
                boolean winCol = !corner && ((z == z0 || z == z1) ? (x - x0) % 3 == 1 : (z - z0) % 3 == 1);
                if (!winCol) continue;
                for (int y : new int[]{g + 2, g + 6}) {
                    boolean litSide = (lit == 1 && z == z1) || (lit == 2 && z == z0) || (lit == 3 && x == x1) || (lit == 4 && x == x0);
                    boolean thisOne = litSide && y == g + 6 && ((z == z0 || z == z1) ? Math.abs((x - x0) - w / 2) <= 1 : Math.abs((z - z0) - d / 2) <= 1);
                    s.set(x, y, z, thisOne ? glow : dark);
                    if (thisOne) {
                        int ix = x == x0 ? x + 1 : x == x1 ? x - 1 : x, iz = z == z0 ? z + 1 : z == z1 ? z - 1 : z;
                        s.set(ix, y, iz, Blocks.SHROOMLIGHT.defaultBlockState());
                    }
                }
            }
        // dark tiled hip roof
        for (int k = 0; k <= Math.min(w, d) / 2 + 1; k++) {
            int ax = x0 - 1 + k, bx = x1 + 1 - k, az = z0 - 1 + k, bz = z1 + 1 - k;
            if (ax > bx || az > bz) break;
            int y = g + 8 + k;
            for (int x = ax; x <= bx; x++) {
                stair(s, x, y, az, Direction.SOUTH);
                stair(s, x, y, bz, Direction.NORTH);
            }
            for (int z = az + 1; z < bz; z++) {
                stair(s, ax, y, z, Direction.EAST);
                stair(s, bx, y, z, Direction.WEST);
            }
            if (bx - ax <= 1 || bz - az <= 1) {
                for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) if (s.contains(x, z)) s.set(x, y, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
                break;
            }
        }
        // a lean-to roof over the entrance on the street side
        int ez = z0 - 1;
        for (int x = x0 + 1; x <= x0 + 3; x++) {
            if (s.contains(x, ez)) s.set(x, g + 4, ez, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
    }

    static void stair(ChunkSink s, int x, int y, int z, Direction facing) {
        if (!s.contains(x, z)) return;
        s.post(x, y, z, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
    }

    // ------------------------------------------------------------------------------------------------ the park
    static void park(ChunkSink s) {
        if (!s.touches(Site.KX1 - 1, Site.KZ1 - 1, Site.KX2 + 1, Site.KZ2 + 1)) return;
        BlockState fence = Blocks.IRON_BARS.defaultBlockState();
        for (int x = Site.KX1 - 1; x <= Site.KX2 + 1; x++)
            for (int z = Site.KZ1 - 1; z <= Site.KZ2 + 1; z++) {
                boolean edge = x == Site.KX1 - 1 || x == Site.KX2 + 1 || z == Site.KZ1 - 1 || z == Site.KZ2 + 1;
                if (!edge || !s.contains(x, z)) continue;
                // the entrance from the street's end
                if (x == Site.KX1 - 1 && z >= Site.SZ1 && z <= Site.SZ2) continue;
                s.post(x, GROUND + 1, z, fence);
            }
        // the swing (a two-seat frame), a bench, a sandbox, trees, the park's lamp
        if (s.contains(56, 23)) s.set(56, GROUND + 1, 23, f(ModBlocks.SWING_SET, Direction.WEST));
        for (int z = 19; z <= 20; z++) {
            if (s.contains(52, z)) s.post(52, GROUND + 1, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        }
        for (int x = 58; x <= 61; x++)
            for (int z = 14; z <= 17; z++) {
                if (!s.contains(x, z)) continue;
                boolean rim = x == 58 || x == 61 || z == 14 || z == 17;
                s.set(x, GROUND, z, rim ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.SAND.defaultBlockState());
                if (rim) s.set(x, GROUND + 1, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            }
        tree(s, 51, 14, 6);
        tree(s, 62, 29, 7);
        tree(s, 50, 29, 5);
        tree(s, 62, 21, 6);
        lampPost(s, 54, 27, Direction.NORTH, 5);
    }

    static void tree(ChunkSink s, int x, int z, int tall) {
        if (!s.touches(x - 3, z - 3, x + 3, z + 3)) return;
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int base = GROUND + 1;
        for (int y = base; y < base + tall; y++) if (s.contains(x, z)) s.set(x, y, z, log);
        int top = base + tall;
        for (int y = top - 3; y <= top; y++) {
            int rad = y == top ? 1 : 2;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz > rad * rad + 1) continue;
                    if (dx == 0 && dz == 0 && y < top) continue;
                    if (Site.rand(x + dx, z + dz * 7 + y, 521) < 0.12) continue;
                    s.soft(x + dx, y, z + dz, leaves);
                }
        }
    }

    // ------------------------------------------------------------------------------------------------ poles and wires
    /** Utility poles along the north edge of the street (z 18); the one at x 26 carries the street lamp over 203's balcony. */
    public static final int POLE_Z = 18;
    public static final int[] POLES_X = {-40, -24, -8, 9, 26, 43};
    public static final int POLE_TOP = GROUND + 10;
    public static final int LAMP_X = 26, LAMP_Y = 71;

    static void poles(ChunkSink s) {
        for (int i = 0; i < POLES_X.length; i++) {
            pole(s, POLES_X[i], POLE_Z);
            if (i + 1 < POLES_X.length) wire(s, POLES_X[i] + 1, POLES_X[i + 1] - 1, POLE_TOP, POLE_Z);
        }
        // the old street lamp on the pole in front of 203, lighting the balconies (and leaking through the curtains);
        // its pool of light on the wet asphalt (an invisible light: the lamp itself is too high to reach the street)
        if (s.contains(LAMP_X, POLE_Z - 1)) s.set(LAMP_X, LAMP_Y, POLE_Z - 1, f(ModBlocks.STREET_LAMP_OLD, Direction.NORTH).setValue(Props.LIT, true));
        if (s.contains(LAMP_X, POLE_Z + 2)) s.set(LAMP_X, GROUND + 1, POLE_Z + 2, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 9));
        // a lamp at the far end of the street too, and the vending machine by the gate
        if (s.contains(-24, POLE_Z - 1)) s.set(-24, LAMP_Y, POLE_Z - 1, f(ModBlocks.STREET_LAMP_OLD, Direction.NORTH).setValue(Props.LIT, true));
        if (s.contains(-24, POLE_Z + 2)) s.set(-24, GROUND + 1, POLE_Z + 2, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8));
        if (s.contains(0, POLE_Z)) {
            s.set(0, GROUND + 1, POLE_Z, f(ModBlocks.VENDING_MACHINE_SMALL, Direction.SOUTH));
            s.set(0, GROUND + 2, POLE_Z, Blocks.AIR.defaultBlockState());
        }
    }

    static void pole(ChunkSink s, int x, int z) {
        if (!s.contains(x, z)) return;
        BlockState p = ModBlocks.UTILITY_POLE.get().defaultBlockState().setValue(FacingBlock.FACING, Direction.EAST);
        for (int y = GROUND + 1; y < POLE_TOP; y++) s.set(x, y, z, p);
        s.set(x, POLE_TOP, z, p.setValue(Props.TOP, true));
    }

    static void wire(ChunkSink s, int a, int b, int y, int z) {
        BlockState w = ModBlocks.POWER_WIRE.get().defaultBlockState().setValue(FacingBlock.FACING, Direction.EAST);
        for (int x = a; x <= b; x++) if (s.contains(x, z)) s.set(x, y, z, w);
    }

    /** A short post with a lamp (the park). */
    static void lampPost(ChunkSink s, int x, int z, Direction facing, int height) {
        if (!s.contains(x, z)) return;
        BlockState p = ModBlocks.UTILITY_POLE.get().defaultBlockState().setValue(FacingBlock.FACING, Direction.EAST);
        for (int y = GROUND + 1; y <= GROUND + height; y++) s.set(x, y, z, p);
        int lx = x + facing.getStepX(), lz = z + facing.getStepZ();
        if (s.contains(lx, lz)) s.set(lx, GROUND + height, lz, f(ModBlocks.STREET_LAMP_OLD, facing).setValue(Props.LIT, true));
    }

    // ------------------------------------------------------------------------------------------------ the street
    static void street(ChunkSink s) {
        // a drain cover now and then in the gutters
        for (int x = s.minX; x <= s.maxX; x++) {
            for (int z : new int[]{Site.SZ1 - 1, Site.SZ2 + 1}) {
                if (!s.contains(x, z) || !Site.inStreet(x, z)) continue;
                if (Math.floorMod(x, 9) == 4) s.set(x, GROUND, z, Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HALF, Half.TOP).setValue(BlockStateProperties.OPEN, false));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ barriers
    static void barriers(ChunkSink s) {
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (int x = s.minX; x <= s.maxX; x++)
            for (int z = s.minZ; z <= s.maxZ; z++) {
                if (!Site.boundary(x, z)) continue;
                for (int y = GROUND + 1; y <= GROUND + 18; y++) s.soft(x, y, z, barrier);
            }
    }
}
