package com.reisatsu.world;

import com.reisatsu.block.FacingBlock;
import com.reisatsu.block.LineBlock;
import com.reisatsu.block.Props;
import com.reisatsu.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The land around HIDAMARI, generated per chunk: dark harvested rice paddies with levees and puddles, the national
 * road with its lines, curbs and guardrails bending north at the curve (chevrons, the curve mirror, the flowers),
 * utility poles and their wires, a blinking traffic light at the farm road, a wooded hill to the north, a few far
 * houses with one lit window, and the invisible walls around the playable area.
 */
public final class Terrain {
    private Terrain() {}

    public static final int GROUND = 64;

    // ------------------------------------------------------------------------------------------------ height
    /** Height of the ground block of a column. */
    public static int height(int x, int z) {
        if (hill(x, z) > 0) return GROUND + hill(x, z);
        if (paddy(x, z)) return GROUND - 1;
        return GROUND;
    }

    /** Extra height of the wooded hill north of the property. */
    static int hill(int x, int z) {
        if (z > -14) return 0;
        double d = (-14 - z) * 0.42 + (Site.noise(x, z, 18, 7) - 0.5) * 6.0 + (Site.noise(x, z, 7, 8) - 0.5) * 2.0;
        if (z > -18) d = Math.min(d, (-14 - z) * 0.5);
        return (int) Math.max(0, Math.min(26, Math.round(d)));
    }

    /** Low paddy plots: everywhere outside the property, the road and its embankment, the levees and farm roads. */
    static boolean paddy(int x, int z) {
        if (Site.inProperty(x, z) || Site.inLot(x, z)) return false;
        if (x >= Site.PROP_X1 - 1 && x <= Site.LOT_X2 + 1 && z >= Site.PROP_Z1 - 1 && z <= Site.LOT_Z2 + 1) return false;
        double r = Site.rho(x, z);
        if (r >= 11 && r < 27) return false;           // road + embankment
        if (farmRoad(x, z)) return false;
        if (levee(x, z)) return false;
        return z > -14;
    }

    static boolean farmRoad(int x, int z) {
        return x >= -31 && x <= -29;
    }

    static boolean levee(int x, int z) {
        return Math.floorMod(x + 3, 15) == 0 || Math.floorMod(z + 5, 12) == 0;
    }

    // ------------------------------------------------------------------------------------------------ chunk
    public static void build(ChunkSink s, ChunkAccess chunk) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (int x = s.minX; x <= s.maxX; x++) {
            for (int z = s.minZ; z <= s.maxZ; z++) {
                int h = height(x, z);
                s.set(x, 0, z, bedrock);
                for (int y = 1; y < h - 3; y++) s.set(x, y, z, stone);
                for (int y = Math.max(1, h - 3); y < h; y++) s.set(x, y, z, dirt);
                s.set(x, h, z, surface(x, z, h));
                if (paddy(x, z) && Site.noise(x, z, 6, 21) > 0.62 && Site.rand(x, z, 22) < 0.85) s.set(x, h + 1, z, ModBlocks.PUDDLE.get().defaultBlockState());
                else if (paddy(x, z) && Site.rand(x, z, 23) < 0.05) s.set(x, h + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
            }
        }
        road(s);
        hillTrees(s);
        farHouses(s);
        poles(s);
        trafficLights(s);
        barriers(s);
    }

    static BlockState surface(int x, int z, int h) {
        if (hill(x, z) > 0) return Site.rand(x, z, 31) < 0.15 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        if (paddy(x, z)) return ModBlocks.RICE_PADDY.get().defaultBlockState();
        double r = Site.rho(x, z);
        if (r >= Site.RHO_CURB && r < Site.RHO_RAIL) return ModBlocks.ASPHALT.get().defaultBlockState();
        if (farmRoad(x, z)) return Site.rand(x, z, 32) < 0.5 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
        if (r >= Site.RHO_RAIL && r < Site.RHO_END + 1) return Blocks.GRAVEL.defaultBlockState();
        return Site.rand(x, z, 33) < 0.1 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
    }

    // ------------------------------------------------------------------------------------------------ road
    static void road(ChunkSink s) {
        BlockState curb = ModBlocks.CURB.get().defaultBlockState();
        for (int x = s.minX; x <= s.maxX; x++) {
            for (int z = s.minZ; z <= s.maxZ; z++) {
                double r = Site.rho(x, z);
                if (r < Site.RHO_CURB || r >= Site.RHO_END) continue;
                Direction.Axis axis = Site.roadAxis(x, z);
                boolean lotFront = x >= Site.LOT_X1 && x <= Site.LOT_X2 && z == Site.LOT_Z2 + 1;
                if (r < Site.RHO_LANE) {
                    if (!lotFront && !farmRoad(x, z)) s.set(x, GROUND + 1, z, curb);
                } else if (r < Site.RHO_SHOULDER) {
                    BlockState line = null;
                    if (r < Site.RHO_LANE + 1 || r >= Site.RHO_SHOULDER - 1) line = ModBlocks.PARKING_LINE.get().defaultBlockState();
                    else if (r >= Site.RHO_CENTRE - 0.5 && r < Site.RHO_CENTRE + 0.5) line = ModBlocks.ROAD_LINE.get().defaultBlockState();
                    if (line != null) s.set(x, GROUND, z, line.setValue(LineBlock.AXIS, axis));
                    if ((r < Site.RHO_LANE + 1.5 || r > Site.RHO_SHOULDER - 1.5) && Site.noise(x, z, 4, 41) > 0.68) {
                        s.set(x, GROUND + 1, z, ModBlocks.PUDDLE.get().defaultBlockState());
                    }
                } else if (r < Site.RHO_RAIL) {
                    if (Site.noise(x, z, 3, 42) > 0.6) s.set(x, GROUND + 1, z, ModBlocks.PUDDLE.get().defaultBlockState());
                } else {
                    if (!farmRoad(x, z) || r < 20) {
                        s.set(x, GROUND + 1, z, f(ModBlocks.GUARDRAIL, Site.inward(x, z)));
                    }
                }
            }
        }
        // close the corners of the guardrail's staircase on the curve so it reads as one continuous rail
        for (int x = s.minX; x <= s.maxX; x++) {
            for (int z = s.minZ; z <= s.maxZ; z++) {
                double r = Site.rho(x, z);
                if (r < Site.RHO_END || r >= Site.RHO_END + 0.75 || x + 0.5 < Site.CX || z + 0.5 < Site.CZ) continue;
                if (rail(x - 1, z) && rail(x, z - 1) && !rail(x - 1, z - 1)) s.set(x, GROUND + 1, z, f(ModBlocks.GUARDRAIL, Site.inward(x, z)));
                else if (rail(x - 1, z) && rail(x, z + 1) && !rail(x - 1, z + 1)) s.set(x, GROUND + 1, z, f(ModBlocks.GUARDRAIL, Site.inward(x, z)));
                else if (rail(x + 1, z) && rail(x, z - 1) && !rail(x + 1, z - 1)) s.set(x, GROUND + 1, z, f(ModBlocks.GUARDRAIL, Site.inward(x, z)));
            }
        }
        // the curve: chevrons outside the guardrail, the curve mirror, the flowers, a sodium lamp over the crash site
        for (int a = 8; a <= 84; a += 11) {
            double rad = Math.toRadians(a);
            int x = (int) Math.floor(Site.CX + 25.5 * Math.cos(rad)), z = (int) Math.floor(Site.CZ + 25.5 * Math.sin(rad));
            if (s.contains(x, z)) s.set(x, GROUND + 1, z, f(ModBlocks.CHEVRON_SIGN, Site.inward(x, z)));
        }
        if (s.contains(66, 59)) {
            s.set(66, GROUND + 1, 59, Blocks.IRON_BARS.defaultBlockState());
            s.set(66, GROUND + 2, 59, Blocks.IRON_BARS.defaultBlockState());
            s.set(66, GROUND + 3, 59, f(ModBlocks.CURVE_MIRROR, Direction.WEST));
        }
        if (s.contains(76, 52)) s.set(76, GROUND + 1, 52, f(ModBlocks.FLOWER_OFFERING, Direction.NORTH));
        lamp(s, ROAD_LAMPS[0][0], ROAD_LAMPS[0][1], Direction.NORTH);
        lamp(s, ROAD_LAMPS[1][0], ROAD_LAMPS[1][1], Direction.SOUTH);
        lamp(s, ROAD_LAMPS[2][0], ROAD_LAMPS[2][1], Direction.SOUTH);
        lamp(s, ROAD_LAMPS[3][0], ROAD_LAMPS[3][1], Direction.WEST);
    }

    /** A guardrail cell of the ring (not the farm road gap). */
    static boolean rail(int x, int z) {
        double r = Site.rho(x, z);
        return r >= Site.RHO_RAIL && r < Site.RHO_END && !farmRoad(x, z);
    }

    static BlockState f(net.minecraftforge.registries.RegistryObject<? extends net.minecraft.world.level.block.Block> b, Direction d) {
        return b.get().defaultBlockState().setValue(FacingBlock.FACING, d);
    }

    /** Height of the lamp heads (the light source). */
    public static final int LAMP_Y = GROUND + 6;
    /** Road lamps (pole positions). */
    public static final int[][] ROAD_LAMPS = {{77, 56}, {58, 49}, {-34, 49}, {87, 20}};

    /** A sodium street lamp: a pole and a lit head on top (SIGN circuit). */
    public static void lamp(BlockSink s, int x, int z, Direction facing) {
        if (!s.contains(x, z)) return;
        BlockState pole = f(ModBlocks.STREET_LAMP, facing).setValue(Props.LAMP_PART, Props.LampPart.POLE);
        for (int y = GROUND + 1; y < LAMP_Y; y++) s.set(x, y, z, pole);
        s.set(x, LAMP_Y, z, pole.setValue(Props.LAMP_PART, Props.LampPart.HEAD).setValue(Props.LIT, Circuit.SIGN.startOn()));
    }

    // ------------------------------------------------------------------------------------------------ hill
    static void hillTrees(ChunkSink s) {
        if (s.maxZ > -15) return;
        for (int gx = Math.floorDiv(s.minX - 4, 5); gx <= Math.floorDiv(s.maxX + 4, 5); gx++) {
            for (int gz = Math.floorDiv(s.minZ - 4, 5); gz <= Math.floorDiv(s.maxZ + 4, 5); gz++) {
                int x = gx * 5 + Math.floorMod(Site.hash(gx, gz, 51), 4);
                int z = gz * 5 + Math.floorMod(Site.hash(gx, gz, 52), 4);
                if (z > -17 || Site.rand(gx, gz, 53) < 0.18) continue;
                tree(s, x, z, height(x, z) + 1, 6 + Math.floorMod(Site.hash(gx, gz, 54), 5));
            }
        }
    }

    static void tree(ChunkSink s, int x, int z, int base, int tall) {
        if (!s.touches(x - 3, z - 3, x + 3, z + 3)) return;
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = base; y < base + tall; y++) s.set(x, y, z, log);
        int top = base + tall;
        for (int y = base + 2; y <= top; y++) {
            int rad = Math.max(0, Math.min(3, (top - y + 1) / 2));
            if ((top - y) % 2 == 1) rad = Math.max(0, rad - 1);
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz > rad * rad + 1) continue;
                    if (dx == 0 && dz == 0 && y < top) continue;
                    s.soft(x + dx, y, z + dz, leaves);
                }
        }
        s.soft(x, top + 1, z, leaves);
    }

    // ------------------------------------------------------------------------------------------------ far houses
    static final int[][] HOUSES = {
            // x0, z0, width (x), depth (z), lit window side (0 none, 1 south, 2 north, 3 east, 4 west)
            {-24, 94, 9, 7, 2}, {34, 101, 8, 6, 0}, {-62, 8, 7, 9, 3}, {104, 70, 8, 7, 4}, {-70, 66, 9, 7, 0}};

    static void farHouses(ChunkSink s) {
        for (int[] hs : HOUSES) house(s, hs[0], hs[1], hs[2], hs[3], hs[4]);
    }

    static void house(ChunkSink s, int x0, int z0, int w, int d, int lit) {
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        if (!s.touches(x0 - 2, z0 - 2, x1 + 2, z1 + 2)) return;
        BlockState wall = Blocks.WHITE_TERRACOTTA.defaultBlockState();
        BlockState wood = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        BlockState dark = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
        BlockState glow = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        int g = GROUND;
        for (int x = x0 - 1; x <= x1 + 1; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (!s.contains(x, z)) continue;
                for (int y = g - 1; y <= g; y++) s.set(x, y, z, Blocks.GRAVEL.defaultBlockState());
                for (int y = g + 1; y <= g + 9; y++) s.set(x, y, z, Blocks.AIR.defaultBlockState());
            }
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                s.set(x, g, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (!edge) continue;
                for (int y = g + 1; y <= g + 4; y++) s.set(x, y, z, corner ? wood : wall);
                // windows: dark glass, one of them lit from inside
                boolean winRow = !corner && (x - x0) % 3 == 1 || !corner && (z - z0) % 3 == 1;
                if (winRow) {
                    boolean litSide = (lit == 1 && z == z1) || (lit == 2 && z == z0) || (lit == 3 && x == x1) || (lit == 4 && x == x0);
                    boolean thisOne = litSide && ((x - x0) == w / 2 || (z - z0) == d / 2);
                    s.set(x, g + 2, z, thisOne ? glow : dark);
                    if (thisOne) {
                        int ix = x == x0 ? x + 1 : x == x1 ? x - 1 : x, iz = z == z0 ? z + 1 : z == z1 ? z - 1 : z;
                        s.set(ix, g + 2, iz, Blocks.SHROOMLIGHT.defaultBlockState());
                    }
                }
            }
        // dark tiled hip roof
        for (int k = 0; k <= Math.min(w, d) / 2 + 1; k++) {
            int ax = x0 - 1 + k, bx = x1 + 1 - k, az = z0 - 1 + k, bz = z1 + 1 - k;
            if (ax > bx || az > bz) break;
            int y = g + 5 + k;
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
    }

    static void stair(ChunkSink s, int x, int y, int z, Direction facing) {
        if (!s.contains(x, z)) return;
        s.post(x, y, z, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
    }

    // ------------------------------------------------------------------------------------------------ poles and wires
    static final int POLE_Z = 61;
    static final int[] POLES_X = {-106, -84, -62, -40, -18, 4, 26, 48};
    static final int POLE_X_NORTH = 86;
    static final int[] POLES_Z_NORTH = {20, -2, -24, -46, -68};

    static void poles(ChunkSink s) {
        int top = GROUND + 9;
        for (int i = 0; i < POLES_X.length; i++) {
            pole(s, POLES_X[i], POLE_Z, Direction.SOUTH);
            if (i + 1 < POLES_X.length) wire(s, POLES_X[i] + 1, POLES_X[i + 1] - 1, top, POLE_Z, Direction.Axis.X);
        }
        for (int i = 0; i < POLES_Z_NORTH.length; i++) {
            pole(s, POLE_X_NORTH, POLES_Z_NORTH[i], Direction.WEST);
            if (i + 1 < POLES_Z_NORTH.length) wire(s, POLES_Z_NORTH[i + 1] + 1, POLES_Z_NORTH[i] - 1, top, POLE_X_NORTH, Direction.Axis.Z);
        }
    }

    static void pole(ChunkSink s, int x, int z, Direction facing) {
        if (!s.contains(x, z)) return;
        int g = height(x, z);
        BlockState p = f(ModBlocks.UTILITY_POLE, facing).setValue(Props.POLE_PART, Props.PolePart.POLE);
        for (int y = g + 1; y < GROUND + 9; y++) s.set(x, y, z, p);
        s.set(x, GROUND + 9, z, p.setValue(Props.POLE_PART, Props.PolePart.TOP));
    }

    static void wire(ChunkSink s, int a, int b, int y, int c, Direction.Axis axis) {
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, axis);
        for (int t = a; t <= b; t++) {
            int x = axis == Direction.Axis.X ? t : c, z = axis == Direction.Axis.X ? c : t;
            // a slight sag toward the middle of the span
            int mid = (a + b) / 2;
            int sag = Math.abs(t - mid) < (b - a) / 4 ? 1 : 0;
            if (s.contains(x, z)) s.set(x, y - sag, z, chain);
        }
    }

    // ------------------------------------------------------------------------------------------------ traffic light
    static void trafficLights(ChunkSink s) {
        signalPole(s, -28, 49, Direction.EAST);
        signalPole(s, -32, 59, Direction.WEST);
    }

    static void signalPole(ChunkSink s, int x, int z, Direction facing) {
        if (!s.contains(x, z)) return;
        for (int y = GROUND + 1; y <= GROUND + 5; y++) s.set(x, y, z, Blocks.IRON_BARS.defaultBlockState());
        s.set(x, GROUND + 6, z, f(ModBlocks.TRAFFIC_LIGHT, facing).setValue(Props.LIT, true));
    }

    // ------------------------------------------------------------------------------------------------ barriers
    static void barriers(ChunkSink s) {
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (int x = s.minX; x <= s.maxX; x++)
            for (int z = s.minZ; z <= s.maxZ; z++) {
                if (!Site.boundary(x, z)) continue;
                int g = height(x, z);
                for (int y = g + 1; y <= GROUND + 6; y++) s.soft(x, y, z, barrier);
            }
    }
}
