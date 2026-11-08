package com.eternalrealm.world.forge;

import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.GenNoise;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.slf4j.Logger;

/**
 * Builds the capital アルセリア王都 around (0, 0) once per world: a levelled, walled square town (walls at ±38..40 with
 * crenellations, gatehouses with towers on all four sides, corner towers with slate-blue roofs), a paved cross of roads
 * and a round plaza with a fountain, the royal castle on the north side, the guild hall, hall of fame, job hall,
 * auction house, item shop, blacksmith and timber houses with interiors, market stalls with striped awnings, a
 * training arena, notice boards, a post box, the goddess statue, lamp posts, benches, flower beds and trees.
 * The NPC positions are in {@link HubLayout}. Runs on the server thread and takes about a second.
 */
public final class HubBuilder {
    private static final Logger LOG = LogUtils.getLogger();
    /** Outer face of the city walls. */
    static final int WALL = 40;
    /** Half-size of the exactly levelled square. */
    static final int CORE = 46;
    /** Half-size of the area eased back to the natural terrain. */
    static final int BLEND = 58;

    private HubBuilder() {
    }

    /** Builds the capital if this world does not have it yet; @return its layout */
    public static HubLayout ensureBuilt(ServerLevel astoria) {
        AstoriaWorldState state = AstoriaWorldState.get(astoria);
        if (state.hubBuilt()) {
            return new HubLayout(state.hubGroundY().getAsInt());
        }
        int groundY = HubLayout.groundY(astoria);
        long start = System.nanoTime();
        build(astoria, groundY);
        state.markHubBuilt(groundY);
        LOG.info("Built the capital of Astoria at y={} in {} ms", groundY, (System.nanoTime() - start) / 1_000_000L);
        return new HubLayout(groundY);
    }

    public static boolean isBuilt(ServerLevel astoria) {
        return AstoriaWorldState.get(astoria).hubBuilt();
    }

    static void build(ServerLevel level, int groundY) {
        TerrainShaper.loadChunks(level, 0, 0, BLEND + 12);
        TerrainShaper.level(level, 0, 0, CORE, BLEND, true, groundY, Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.DIRT.defaultBlockState());
        Painter p = new Painter(level, new BlockPos(0, groundY, 0), Rotation.NONE);
        ground(p);
        castle(p);
        buildings(p);
        arena(p);
        for (Rotation side : Rotation.values()) {
            wallSide(p.child(0, 0, 0, side));
        }
        for (int cx = -1; cx <= 1; cx += 2) {
            for (int cz = -1; cz <= 1; cz += 2) {
                cornerTower(p, cx * 39, cz * 39);
            }
        }
        plaza(p);
        market(p);
        decorations(p);
        p.finish();
    }

    // ------------------------------------------------------------------------------------------------------- ground

    private static void ground(Painter p) {
        for (int x = -46; x <= 46; x++) {
            for (int z = -46; z <= 46; z++) {
                boolean inside = Math.abs(x) <= 37 && Math.abs(z) <= 37;
                boolean road = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                double r = Math.sqrt(x * x + z * z);
                if (r <= 13.0) {
                    p.set(x, 0, z, plazaPaving(x, z, r));
                } else if (road) {
                    p.set(x, 0, z, roadPaving(x, z));
                } else if (inside && Math.abs(x) <= 6 && z >= -19 && z <= -13) {
                    p.set(x, 0, z, roadPaving(x, z)); // castle forecourt
                } else if (inside && (Math.abs(x) >= 36 || Math.abs(z) >= 36)) {
                    p.set(x, 0, z, Blocks.DIRT_PATH); // lane along the walls
                } else if (inside) {
                    p.set(x, 0, z, Blocks.GRASS_BLOCK);
                }
            }
        }
    }

    private static BlockState plazaPaving(int x, int z, double r) {
        if (r > 12.0) {
            return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        }
        if (r > 9.5 && r <= 10.5) {
            return Blocks.POLISHED_ANDESITE.defaultBlockState();
        }
        return (((x + z) & 1) == 0 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
    }

    private static BlockState roadPaving(int x, int z) {
        boolean edge = (Math.abs(x) == 2 && Math.abs(z) > 2) || (Math.abs(z) == 2 && Math.abs(x) > 2);
        if (edge) {
            return Blocks.POLISHED_ANDESITE.defaultBlockState();
        }
        double hash = GenNoise.hash(AstoriaLayout.SEED + 7, x, 0, z);
        return (hash < 0.08 ? Blocks.CRACKED_STONE_BRICKS : hash < 0.14 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS)
                .defaultBlockState();
    }

    private static BlockState stone(int x, int y, int z) {
        double hash = GenNoise.hash(AstoriaLayout.SEED + 11, x, y, z);
        return (hash < 0.08 ? Blocks.CRACKED_STONE_BRICKS : hash < 0.14 && y < 4 ? Blocks.MOSSY_STONE_BRICKS
                : Blocks.STONE_BRICKS).defaultBlockState();
    }

    // ------------------------------------------------------------------------------------------------------ walls

    /** The south wall with its gatehouse, in a frame turned to each side of the city. */
    private static void wallSide(Painter s) {
        for (int x = -35; x <= 35; x++) {
            for (int z = 38; z <= 40; z++) {
                for (int y = 1; y <= 8; y++) {
                    s.set(x, y, z, stone(x, y, z));
                }
            }
            if (Math.floorMod(x, 2) == 0) {
                s.set(x, 9, 40, Blocks.STONE_BRICKS);
            }
            s.set(x, 9, 38, Blocks.STONE_BRICK_WALL);
            if (Math.floorMod(x, 8) == 4 && Math.abs(x) > 9) {
                s.set(x, 4, 37, Painter.wallTorch(Direction.NORTH));
            }
        }
        // gate passage with an arch and a raised portcullis
        for (int x = -2; x <= 2; x++) {
            for (int z = 38; z <= 40; z++) {
                for (int y = 1; y <= 6; y++) {
                    if (y == 6 && Math.abs(x) == 2) {
                        s.set(x, y, z, Painter.stairs(Blocks.STONE_BRICK_STAIRS, x < 0 ? Direction.WEST : Direction.EAST,
                                Half.TOP));
                    } else {
                        s.set(x, y, z, Blocks.AIR);
                    }
                }
                s.set(x, 0, z, roadPaving(x, z + 3));
            }
            if (Math.abs(x) <= 1) {
                s.set(x, 6, 39, Blocks.IRON_BARS);
            }
        }
        for (int x = -2; x <= 2; x++) {
            s.set(x, 7, 41, Blocks.CHISELED_STONE_BRICKS);
        }
        // gate towers
        for (int side = -1; side <= 1; side += 2) {
            int x1 = side < 0 ? -7 : 3;
            int x2 = side < 0 ? -3 : 7;
            for (int x = x1; x <= x2; x++) {
                for (int z = 36; z <= 41; z++) {
                    boolean edge = x == x1 || x == x2 || z == 36 || z == 41;
                    for (int y = 1; y <= 13; y++) {
                        boolean corner = (x == x1 || x == x2) && (z == 36 || z == 41);
                        s.set(x, y, z, edge ? (corner ? Blocks.POLISHED_ANDESITE.defaultBlockState() : stone(x, y, z))
                                : Blocks.AIR.defaultBlockState());
                    }
                    s.set(x, 13, z, Blocks.STONE_BRICKS);
                    if (edge && Math.floorMod(x + z, 2) == 0) {
                        s.set(x, 14, z, Blocks.STONE_BRICKS);
                    }
                }
            }
            int mid = (x1 + x2) / 2;
            s.set(mid, 6, 41, Blocks.IRON_BARS);
            s.set(mid, 7, 41, Blocks.IRON_BARS);
            s.set(mid, 10, 42, Painter.facing(Blocks.RED_WALL_BANNER, Direction.SOUTH));
            s.set(mid, 10, 35, Painter.facing(Blocks.RED_WALL_BANNER, Direction.NORTH));
            s.set(mid, 14, 38, Blocks.LANTERN);
            s.set(side < 0 ? x2 : x1, 4, 42, Painter.wallTorch(Direction.SOUTH));
        }
        // approach road outside the gate
        for (int x = -2; x <= 2; x++) {
            for (int z = 41; z <= 46; z++) {
                s.set(x, 0, z, roadPaving(x, z));
            }
        }
    }

    private static void cornerTower(Painter p, int cx, int cz) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                boolean edge = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                for (int y = 1; y <= 12; y++) {
                    p.set(x, y, z, edge ? stone(x, y, z) : Blocks.AIR.defaultBlockState());
                }
                p.set(x, 0, z, Blocks.STONE_BRICKS);
                p.set(x, 12, z, Blocks.STONE_BRICKS);
            }
        }
        for (int i = -3; i <= 3; i += 6) {
            p.set(cx + i, 7, cz, Blocks.IRON_BARS);
            p.set(cx, 7, cz + i, Blocks.IRON_BARS);
        }
        pyramidRoof(p, cx, cz, 4, 13, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE);
    }

    /** Square pyramid of stairs with a block and a lightning rod on top; {@code half} includes a 1 block overhang. */
    private static void pyramidRoof(Painter p, int cx, int cz, int half, int y, Block stairs, Block cap) {
        for (int k = 0; k < half; k++) {
            int r = half - k;
            for (int i = -r; i <= r; i++) {
                p.set(cx + i, y + k, cz - r, Painter.stairs(stairs, Direction.SOUTH));
                p.set(cx + i, y + k, cz + r, Painter.stairs(stairs, Direction.NORTH));
                if (Math.abs(i) < r) {
                    p.set(cx - r, y + k, cz + i, Painter.stairs(stairs, Direction.EAST));
                    p.set(cx + r, y + k, cz + i, Painter.stairs(stairs, Direction.WEST));
                }
            }
            if (r > 1) {
                p.fill(cx - r + 1, y + k, cz - r + 1, cx + r - 1, y + k, cz + r - 1, Blocks.AIR.defaultBlockState());
            }
        }
        p.set(cx, y + half - 1, cz, cap);
        p.set(cx, y + half, cz, Blocks.LIGHTNING_ROD);
    }

    // ----------------------------------------------------------------------------------------------------- castle

    private static void castle(Painter p) {
        int x1 = -13;
        int x2 = 13;
        int z1 = -34;
        int z2 = -20;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                p.set(x, 0, z, wall ? Blocks.STONE_BRICKS.defaultBlockState()
                        : ((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 1; y <= 12; y++) {
                    BlockState state = wall ? (Math.floorMod(x, 4) == 1 && (z == z1 || z == z2)
                            ? Blocks.POLISHED_ANDESITE.defaultBlockState() : stone(x, y, z)) : Blocks.AIR.defaultBlockState();
                    p.set(x, y, z, state);
                }
                p.set(x, 12, z, Blocks.STONE_BRICKS);
                if (wall && Math.floorMod(x + z, 2) == 0) {
                    p.set(x, 13, z, Blocks.STONE_BRICKS);
                }
            }
        }
        // tall windows
        for (int x : new int[]{-10, -6, 6, 10}) {
            for (int y = 4; y <= 8; y++) {
                p.set(x, y, z2, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
                p.set(x, y, z1, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            }
        }
        for (int z : new int[]{-31, -27, -23}) {
            for (int y = 4; y <= 8; y++) {
                p.set(x1, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
                p.set(x2, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            }
        }
        // great door with an arch, banners and lanterns; a back door to the north gate
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 4; y++) {
                p.set(x, y, z2, Blocks.AIR);
            }
        }
        p.set(-1, 4, z2, Painter.stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, Half.TOP));
        p.set(1, 4, z2, Painter.stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, Half.TOP));
        p.set(0, 5, z2, Blocks.CHISELED_STONE_BRICKS);
        for (int side = -1; side <= 1; side += 2) {
            p.set(side * 3, 7, z2 + 1, Painter.facing(Blocks.BLUE_WALL_BANNER, Direction.SOUTH));
            p.set(side * 2, 3, z2 + 1, Painter.wallTorch(Direction.SOUTH));
        }
        p.door(0, 1, z1, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
        // corner towers with slate-blue spires
        for (int cx = -13; cx <= 13; cx += 26) {
            for (int cz : new int[]{z1, z2}) {
                for (int x = cx - 2; x <= cx + 2; x++) {
                    for (int z = cz - 2; z <= cz + 2; z++) {
                        boolean edge = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
                        p.set(x, 0, z, Blocks.STONE_BRICKS);
                        for (int y = 1; y <= 17; y++) {
                            p.set(x, y, z, edge ? stone(x, y, z) : Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                p.set(cx, 9, cz + (cz == z2 ? 2 : -2), Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
                p.set(cx, 10, cz + (cz == z2 ? 2 : -2), Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
                pyramidRoof(p, cx, cz, 3, 18, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE);
            }
        }
        // central keep rising above the hall
        for (int x = -5; x <= 5; x++) {
            for (int z = -31; z <= -23; z++) {
                boolean edge = Math.abs(x) == 5 || z == -31 || z == -23;
                for (int y = 13; y <= 22; y++) {
                    p.set(x, y, z, edge ? stone(x, y, z) : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int y = 16; y <= 19; y++) {
            p.set(-2, y, -23, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            p.set(2, y, -23, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        }
        p.set(0, 17, -22, Painter.facing(Blocks.YELLOW_WALL_BANNER, Direction.SOUTH));
        pyramidRoof(p, 0, -27, 6, 23, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE);
        // throne hall: carpet, pillars, throne, banners, chandeliers
        for (int z = z1 + 3; z <= z2 - 1; z++) {
            for (int x = -1; x <= 1; x++) {
                p.set(x, 1, z, Blocks.RED_CARPET);
            }
        }
        for (int z : new int[]{-23, -27, -31}) {
            for (int side = -1; side <= 1; side += 2) {
                for (int y = 1; y <= 11; y++) {
                    p.set(side * 7, y, z, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
                }
                p.set(side * 7, 6, z + 1, Painter.wallTorch(Direction.SOUTH));
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = z1 + 1; z <= z1 + 2; z++) {
                p.set(x, 1, z, Blocks.POLISHED_ANDESITE);
            }
            p.set(x, 1, z1 + 3, Painter.stairs(Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH));
        }
        p.set(0, 2, z1 + 2, Painter.stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        p.set(-1, 2, z1 + 2, Painter.slab(Blocks.QUARTZ_SLAB, SlabType.BOTTOM));
        p.set(1, 2, z1 + 2, Painter.slab(Blocks.QUARTZ_SLAB, SlabType.BOTTOM));
        p.set(0, 2, z1 + 1, Blocks.GOLD_BLOCK);
        p.set(0, 3, z1 + 1, Blocks.GOLD_BLOCK);
        p.set(0, 4, z1 + 1, Blocks.CHISELED_QUARTZ_BLOCK);
        for (int side = -1; side <= 1; side += 2) {
            p.set(side * 3, 6, z1 + 1, Painter.facing(Blocks.RED_WALL_BANNER, Direction.SOUTH));
            p.set(side * 2, 2, z1 + 1, Blocks.LANTERN);
            p.set(side * 11, 1, z1 + 1, Blocks.BOOKSHELF);
            p.set(side * 11, 2, z1 + 1, Blocks.BOOKSHELF);
            p.set(side * 11, 1, z1 + 2, Blocks.BOOKSHELF);
        }
        for (int z : new int[]{-24, -29}) {
            p.set(0, 11, z, Painter.axis(Blocks.CHAIN, Direction.Axis.Y));
            p.set(0, 10, z, Painter.hanging(Blocks.LANTERN));
            for (int side = -1; side <= 1; side += 2) {
                p.set(side * 4, 11, z, Painter.axis(Blocks.CHAIN, Direction.Axis.Y));
                p.set(side * 4, 10, z, Painter.hanging(Blocks.LANTERN));
            }
        }
    }

    // -------------------------------------------------------------------------------------------------- buildings

    private enum Interior { GUILD, HALL_OF_FAME, JOB, AUCTION, ITEM_SHOP, BLACKSMITH, HOME }

    /** Materials of a building. */
    private record Style(Block foundation, Block wall, Block corner, Block beam, Block roofStairs, Block ridge,
                         Block floor, Block window, Block door, int height) {
        static final Style STONE = new Style(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE,
                Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES,
                Blocks.DARK_OAK_PLANKS, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, 6);
        static final Style QUARTZ = new Style(Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR,
                Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_STAIRS, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ,
                Blocks.GLASS_PANE, Blocks.BIRCH_DOOR, 6);
        static final Style TIMBER_RED = new Style(Blocks.COBBLESTONE, Blocks.CALCITE, Blocks.OAK_LOG,
                Blocks.STRIPPED_OAK_LOG, Blocks.BRICK_STAIRS, Blocks.BRICKS, Blocks.OAK_PLANKS, Blocks.GLASS_PANE,
                Blocks.OAK_DOOR, 4);
        static final Style TIMBER_DARK = new Style(Blocks.COBBLESTONE, Blocks.CALCITE, Blocks.DARK_OAK_LOG,
                Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS,
                Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, 4);
        static final Style TIMBER_SLATE = new Style(Blocks.STONE_BRICKS, Blocks.BIRCH_PLANKS, Blocks.SPRUCE_LOG,
                Blocks.STRIPPED_SPRUCE_LOG, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_PLANKS,
                Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, 4);
    }

    private static void buildings(Painter p) {
        building(lot(p, -35, -35, -19, -25, Direction.SOUTH), 17, 11, Style.STONE, Interior.GUILD);
        building(lot(p, 19, -35, 35, -25, Direction.SOUTH), 17, 11, Style.QUARTZ, Interior.HALL_OF_FAME);
        building(lot(p, -36, -20, -23, -9, Direction.SOUTH), 14, 12, Style.STONE, Interior.JOB);
        building(lot(p, 23, -20, 36, -9, Direction.SOUTH), 14, 12, Style.STONE, Interior.AUCTION);
        building(lot(p, -34, 6, -23, 15, Direction.NORTH), 12, 10, Style.TIMBER_RED, Interior.ITEM_SHOP);
        building(lot(p, 23, 6, 34, 15, Direction.NORTH), 12, 10, Style.TIMBER_DARK, Interior.BLACKSMITH);
        building(lot(p, -21, -19, -17, -12, Direction.SOUTH), 5, 8, Style.TIMBER_SLATE, Interior.HOME);
        building(lot(p, 17, -19, 21, -12, Direction.SOUTH), 5, 8, Style.TIMBER_RED, Interior.HOME);
        building(lot(p, -36, 21, -28, 29, Direction.NORTH), 9, 9, Style.TIMBER_DARK, Interior.HOME);
        building(lot(p, -25, 21, -17, 29, Direction.NORTH), 9, 9, Style.TIMBER_RED, Interior.HOME);
        building(lot(p, -14, 21, -6, 29, Direction.EAST), 9, 9, Style.TIMBER_SLATE, Interior.HOME);
        building(lot(p, 4, 21, 11, 29, Direction.WEST), 9, 8, Style.TIMBER_RED, Interior.HOME);
    }

    /**
     * A painter for a rectangular lot whose door side faces {@code door}: in its frame the building spans
     * x 0..w-1 and z 0..d-1 with the front wall at z = d-1.
     */
    private static Painter lot(Painter city, int x1, int z1, int x2, int z2, Direction door) {
        return switch (door) {
            case SOUTH -> city.child(x1, 0, z1, Rotation.NONE);
            case NORTH -> city.child(x2, 0, z2, Rotation.CLOCKWISE_180);
            case WEST -> city.child(x2, 0, z1, Rotation.CLOCKWISE_90);
            default -> city.child(x1, 0, z2, Rotation.COUNTERCLOCKWISE_90);
        };
    }

    private static void building(Painter b, int w, int d, Style style, Interior interior) {
        int h = style.height();
        int door = w / 2;
        boolean openFront = interior == Interior.BLACKSMITH;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                b.set(x, 0, z, edge ? style.foundation() : style.floor());
                for (int y = 1; y <= h + 1; y++) {
                    b.set(x, y, z, Blocks.AIR);
                }
                if (!edge) {
                    b.set(x, h, z, style.floor()); // ceiling
                    continue;
                }
                boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                for (int y = 1; y <= h; y++) {
                    BlockState state;
                    if (corner) {
                        state = Painter.axis(style.corner(), Direction.Axis.Y);
                    } else if (y == h) {
                        state = Painter.axis(style.beam(), z == 0 || z == d - 1 ? Direction.Axis.X : Direction.Axis.Z);
                    } else if (openFront && z == d - 1) {
                        state = Blocks.AIR.defaultBlockState();
                    } else {
                        state = style.wall().defaultBlockState();
                    }
                    b.set(x, y, z, state);
                }
            }
        }
        // windows: every third block of the side and back walls, two on the front
        int top = h >= 6 ? 4 : 3;
        for (int z = 2; z < d - 2; z += 3) {
            for (int y = 2; y <= top; y++) {
                b.set(0, y, z, style.window());
                b.set(w - 1, y, z, style.window());
            }
        }
        for (int x = 2; x < w - 2; x += 3) {
            if (Math.abs(x - door) <= 2) {
                continue;
            }
            for (int y = 2; y <= top; y++) {
                b.set(x, y, 0, style.window());
                if (!openFront) {
                    b.set(x, y, d - 1, style.window());
                }
            }
        }
        // door, lights and a path to the street
        if (!openFront) {
            if (w >= 12) {
                b.door(door - 1, 1, d - 1, style.door(), Direction.NORTH, false);
                b.door(door, 1, d - 1, style.door(), Direction.NORTH, true);
            } else {
                b.door(door, 1, d - 1, style.door(), Direction.NORTH, false);
            }
            b.set(door - 2, 3, d, Painter.wallTorch(Direction.SOUTH));
            b.set(door + 1, 3, d, Painter.wallTorch(Direction.SOUTH));
        }
        for (int z = d; z <= d + 1; z++) {
            for (int x = door - 1; x <= door; x++) {
                if (b.get(x, 0, z).is(Blocks.GRASS_BLOCK)) {
                    b.set(x, 0, z, Blocks.DIRT_PATH);
                }
            }
        }
        gableRoof(b, w, d, h, style);
        b.set(door, h - 1, d / 2, Painter.hanging(Blocks.LANTERN));
        switch (interior) {
            case GUILD -> guildInterior(b, w, d);
            case HALL_OF_FAME -> hallOfFameInterior(b, w, d);
            case JOB -> jobInterior(b, w, d);
            case AUCTION -> auctionInterior(b, w, d);
            case ITEM_SHOP -> itemShopInterior(b, w, d);
            case BLACKSMITH -> blacksmithInterior(b, w, d);
            default -> homeInterior(b, w, d, h);
        }
    }

    /** Gable roof with a 1 block overhang; the ridge runs along x. */
    private static void gableRoof(Painter b, int w, int d, int h, Style style) {
        for (int layer = 0; ; layer++) {
            int back = -1 + layer;
            int front = d - layer;
            int y = h + layer;
            if (back > front) {
                break;
            }
            for (int x = -1; x <= w; x++) {
                if (back == front) {
                    b.set(x, y, back, style.ridge());
                } else {
                    b.set(x, y, back, Painter.stairs(style.roofStairs(), Direction.SOUTH));
                    b.set(x, y, front, Painter.stairs(style.roofStairs(), Direction.NORTH));
                }
            }
            if (layer >= 1) {
                for (int z = back + 1; z <= front - 1; z++) {
                    b.set(0, y, z, style.wall());
                    b.set(w - 1, y, z, style.wall());
                    for (int x = 1; x < w - 1; x++) {
                        b.set(x, y, z, Blocks.AIR);
                    }
                }
            }
            if (back + 1 >= front) {
                break;
            }
        }
    }

    private static void guildInterior(Painter b, int w, int d) {
        int c = w / 2;
        for (int x = c - 2; x <= c + 2; x++) {
            b.set(x, 1, 4, Blocks.DARK_OAK_PLANKS);
        }
        b.set(c - 2, 2, 4, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3)
                .setValue(BlockStateProperties.LIT, true));
        b.set(c + 1, 1, 3, Painter.facing(Blocks.LECTERN, Direction.SOUTH));
        Block[] banners = {Blocks.BLUE_WALL_BANNER, Blocks.RED_WALL_BANNER, Blocks.GREEN_WALL_BANNER};
        for (int i = 0; i < 3; i++) {
            b.set(3 + i * ((w - 7) / 2), 4, 1, Painter.facing(banners[i], Direction.SOUTH));
        }
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 2 : w - 6;
            for (int x = x0; x < x0 + 4; x++) {
                b.set(x, 1, 7, Blocks.DARK_OAK_FENCE);
                b.set(x, 2, 7, Blocks.DARK_OAK_PRESSURE_PLATE);
                b.set(x, 1, 6, Painter.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
                b.set(x, 1, 8, Painter.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            }
        }
        for (int y = 1; y <= 3; y++) {
            b.set(1, y, 1, Blocks.BOOKSHELF);
            b.set(1, y, 2, Blocks.BOOKSHELF);
            b.set(w - 2, y, 1, Blocks.BOOKSHELF);
            b.set(w - 2, y, 2, Blocks.BOOKSHELF);
        }
        chandelier(b, c, 5, 6);
    }

    private static void hallOfFameInterior(Painter b, int w, int d) {
        int c = w / 2;
        for (int z = 2; z < d - 1; z++) {
            b.set(c, 1, z, Blocks.RED_CARPET);
        }
        b.set(c, 1, 1, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
        b.set(c, 2, 1, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
        b.set(c, 3, 1, Blocks.CHISELED_QUARTZ_BLOCK);
        b.set(c, 4, 1, Blocks.END_ROD);
        for (int x : new int[]{3, w - 4}) {
            for (int z : new int[]{3, 6}) {
                b.set(x, 1, z, Blocks.CHISELED_QUARTZ_BLOCK);
                b.set(x, 2, z, (z == 3 ? Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT,
                        BellAttachType.FLOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        : Blocks.DECORATED_POT.defaultBlockState()));
            }
            b.set(x, 4, 1, Painter.facing(Blocks.YELLOW_WALL_BANNER, Direction.SOUTH));
        }
        for (int z = 2; z < d - 1; z += 3) {
            b.set(1, 1, z, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
            b.set(w - 2, 1, z, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
            b.set(1, 2, z, Blocks.LANTERN);
            b.set(w - 2, 2, z, Blocks.LANTERN);
        }
        chandelier(b, c, 5, 5);
    }

    private static void jobInterior(Painter b, int w, int d) {
        int c = w / 2;
        b.set(c, 1, 5, Painter.facing(Blocks.LECTERN, Direction.SOUTH));
        for (int x = 1; x < w - 1; x++) {
            if (Math.abs(x - c) > 1) {
                for (int y = 1; y <= 3; y++) {
                    b.set(x, y, 1, Blocks.BOOKSHELF);
                }
            }
        }
        b.set(2, 1, 3, Blocks.ENCHANTING_TABLE);
        b.set(w - 3, 1, 3, Blocks.CARTOGRAPHY_TABLE);
        b.set(2, 1, d - 3, Blocks.FLETCHING_TABLE);
        b.set(w - 3, 1, d - 3, Blocks.SMITHING_TABLE);
        Block[] jobs = {Blocks.RED_WALL_BANNER, Blocks.BLUE_WALL_BANNER, Blocks.GREEN_WALL_BANNER, Blocks.WHITE_WALL_BANNER};
        b.set(1, 3, 4, Painter.facing(jobs[0], Direction.EAST));
        b.set(1, 3, 7, Painter.facing(jobs[1], Direction.EAST));
        b.set(w - 2, 3, 4, Painter.facing(jobs[2], Direction.WEST));
        b.set(w - 2, 3, 7, Painter.facing(jobs[3], Direction.WEST));
        for (int x = c - 1; x <= c + 1; x++) {
            for (int z = 6; z <= 9; z++) {
                b.set(x, 1, z, Blocks.BLUE_CARPET);
            }
        }
        chandelier(b, c, 5, 7);
    }

    private static void auctionInterior(Painter b, int w, int d) {
        int c = w / 2;
        for (int x = c - 3; x <= c + 3; x++) {
            b.set(x, 1, 5, Blocks.SPRUCE_PLANKS);
        }
        b.set(c - 2, 2, 5, Blocks.LANTERN);
        b.set(c + 2, 2, 5, Blocks.LANTERN);
        for (int x = 1; x < w - 1; x++) {
            b.set(x, 1, 1, x % 2 == 0 ? Painter.facing(Blocks.CHEST, Direction.SOUTH)
                    : Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            b.set(x, 2, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            b.set(x, 3, 1, Painter.facing(Blocks.YELLOW_WALL_BANNER, Direction.SOUTH));
        }
        for (int z = 8; z <= 9; z++) {
            for (int x = 2; x < w - 2; x++) {
                if (Math.abs(x - c) > 1) {
                    b.set(x, 1, z, Painter.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
                }
            }
        }
        b.set(c, 1, 3, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        chandelier(b, c, 5, 7);
    }

    private static void itemShopInterior(Painter b, int w, int d) {
        int c = w / 2;
        for (int x = 2; x < w - 2; x++) {
            b.set(x, 1, 4, x % 3 == 0 ? Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP)
                    : Blocks.OAK_PLANKS.defaultBlockState());
        }
        b.set(2, 2, 4, Blocks.BREWING_STAND);
        b.set(w - 3, 2, 4, Blocks.POTTED_RED_TULIP);
        b.set(c + 2, 2, 4, Blocks.LANTERN);
        for (int x = 1; x < w - 1; x++) {
            b.set(x, 1, 1, x % 2 == 0 ? Painter.facing(Blocks.CHEST, Direction.SOUTH) : Blocks.BOOKSHELF.defaultBlockState());
            b.set(x, 2, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            b.set(x, 3, 1, x % 2 == 0 ? Blocks.POTTED_FERN.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        b.set(1, 1, d - 3, Blocks.CAULDRON);
        b.set(w - 2, 1, d - 3, Painter.facing(Blocks.CARVED_PUMPKIN, Direction.WEST));
        b.set(c, 3, d, Painter.facing(Blocks.GREEN_WALL_BANNER, Direction.SOUTH));
    }

    private static void blacksmithInterior(Painter b, int w, int d) {
        int c = w / 2;
        b.set(c, 1, 5, Painter.facing(Blocks.ANVIL, Direction.EAST));
        b.set(c + 3, 1, 2, Blocks.SMITHING_TABLE);
        b.set(c + 3, 1, 5, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        b.set(c - 3, 1, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        // forge: brick hearth with a lava cauldron and a blast furnace, chimney through the roof with smoke
        for (int x = 1; x <= 3; x++) {
            b.set(x, 1, 1, Blocks.BRICKS);
            b.set(x, 2, 1, Blocks.BRICKS);
        }
        b.set(1, 1, 2, Blocks.BRICKS);
        b.set(2, 1, 2, Blocks.LAVA_CAULDRON);
        b.set(3, 1, 2, Painter.facing(Blocks.BLAST_FURNACE, Direction.SOUTH).setValue(BlockStateProperties.LIT, true));
        for (int y = 3; y <= 12; y++) {
            b.set(2, y, 1, Blocks.BRICKS);
        }
        b.set(2, 13, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.SIGNAL_FIRE, false));
        b.set(1, 3, 2, Painter.hanging(Blocks.LANTERN));
        for (int x = c + 1; x <= c + 3; x++) {
            b.set(x, 3, 1, Blocks.IRON_BARS);
        }
        b.set(c, 3, 3, Painter.axis(Blocks.CHAIN, Direction.Axis.Y));
        b.set(c, 2, 3, Painter.hanging(Blocks.LANTERN));
        b.set(w - 2, 1, d - 2, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        b.set(w - 2, 1, d - 3, Painter.axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
    }

    private static void homeInterior(Painter b, int w, int d, int h) {
        b.bed(1, 1, 2, Blocks.RED_BED, Direction.NORTH);
        b.set(w - 2, 1, 1, Blocks.CRAFTING_TABLE);
        b.set(w - 2, 1, 2, Painter.facing(Blocks.FURNACE, Direction.WEST));
        b.set(w - 2, 1, 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        if (w >= 8) {
            b.set(w - 4, 1, 3, Blocks.OAK_FENCE);
            b.set(w - 4, 2, 3, Blocks.OAK_PRESSURE_PLATE);
            b.set(w - 4, 1, 4, Painter.stairs(Blocks.OAK_STAIRS, Direction.SOUTH));
        }
        b.set(1, 1, d - 3, Blocks.BOOKSHELF);
        b.set(1, 2, d - 3, Blocks.POTTED_POPPY);
        for (int x = 2; x <= Math.min(4, w - 3); x++) {
            for (int z = 3; z <= Math.min(5, d - 3); z++) {
                b.set(x, 1, z, Blocks.WHITE_CARPET);
            }
        }
        // chimney with a little smoke
        for (int y = 1; y <= h + 6; y++) {
            b.set(w - 2, y, 0, Blocks.BRICKS);
        }
        b.set(w - 2, h + 7, 0, Blocks.CAMPFIRE);
    }

    private static void chandelier(Painter b, int x, int y, int z) {
        b.set(x, y, z, Painter.axis(Blocks.CHAIN, Direction.Axis.Y));
        b.set(x, y - 1, z, Painter.hanging(Blocks.LANTERN));
    }

    // ------------------------------------------------------------------------------------------------- the arena

    private static void arena(Painter p) {
        int cx = 25;
        int cz = 28;
        for (int x = 14; x <= 36; x++) {
            for (int z = 20; z <= 36; z++) {
                double ex = (x - cx) / 10.0;
                double ez = (z - cz) / 7.0;
                double e = ex * ex + ez * ez;
                boolean gate = z < cz && Math.abs(x - cx) <= 1;
                if (e <= 1.0) {
                    p.set(x, 0, z, Blocks.SAND);
                } else if (e <= 1.3) {
                    p.set(x, 0, z, Blocks.STONE_BRICKS);
                    if (gate) {
                        p.set(x, 0, z, roadPaving(x, z));
                        continue;
                    }
                    p.set(x, 1, z, stone(x, 1, z));
                    p.set(x, 2, z, stone(x, 2, z));
                    p.set(x, 3, z, Blocks.STONE_BRICK_WALL);
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= 3; y++) {
                p.set(cx + side * 2, y, 20, Blocks.STONE_BRICKS);
            }
            p.set(cx + side * 2, 4, 20, Blocks.LANTERN);
        }
        // training dummies and targets
        for (int x : new int[]{20, 30}) {
            p.set(x, 1, 26, Blocks.OAK_FENCE);
            p.set(x, 2, 26, Painter.axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
            p.set(x, 3, 26, Painter.facing(Blocks.CARVED_PUMPKIN, Direction.NORTH));
            p.set(x, 1, 31, Blocks.OAK_FENCE);
            p.set(x, 2, 31, Blocks.TARGET);
        }
        p.set(25, 1, 34, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        p.set(24, 1, 34, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        p.set(26, 1, 34, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        p.set(cx - 2, 3, 19, Painter.facing(Blocks.RED_WALL_BANNER, Direction.NORTH));
    }

    // ----------------------------------------------------------------------------------------------- the plaza

    private static void plaza(Painter p) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 3.6) {
                    p.set(x, -2, z, r < 1.5 ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS);
                    p.set(x, -1, z, Blocks.WATER);
                    p.set(x, 0, z, Blocks.WATER);
                } else if (r <= 4.7) {
                    p.set(x, -1, z, Blocks.STONE_BRICKS);
                    p.set(x, 0, z, Blocks.CHISELED_STONE_BRICKS);
                    p.set(x, 1, z, Painter.slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM));
                }
            }
        }
        for (int y = -1; y <= 3; y++) {
            p.set(0, y, 0, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
        }
        p.set(0, 4, 0, Blocks.CHISELED_QUARTZ_BLOCK);
        p.set(0, 5, 0, Blocks.WATER);
        p.level().scheduleTick(p.world(0, 5, 0), net.minecraft.world.level.material.Fluids.WATER, 1);
        for (int[] lamp : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            lampPost(p, lamp[0], lamp[1]);
        }
        bench(p, -7, 0, Direction.EAST);
        bench(p, 7, 0, Direction.WEST);
        bench(p, 0, -7, Direction.SOUTH);
        for (int[] bed : new int[][]{{-10, -5}, {9, -5}, {-10, 4}, {9, 4}}) {
            flowerBed(p, bed[0], bed[1], bed[0] + 1, bed[1] + 1);
        }
    }

    // -------------------------------------------------------------------------------------------------- market

    private static void market(Painter p) {
        Block[][] awnings = {{Blocks.RED_WOOL, Blocks.WHITE_WOOL}, {Blocks.BLUE_WOOL, Blocks.WHITE_WOOL},
                {Blocks.YELLOW_WOOL, Blocks.ORANGE_WOOL}, {Blocks.GREEN_WOOL, Blocks.LIME_WOOL}};
        int i = 0;
        for (int x : new int[]{-18, -14, 14, 18}) {
            stall(p, x, -5, Direction.SOUTH, awnings[i % 4], i);
            stall(p, x, 5, Direction.NORTH, awnings[(i + 1) % 4], i + 1);
            i += 2;
        }
    }

    /** A 3x3 market stall at (x, z) whose counter faces {@code front}, with a striped awning and goods. */
    private static void stall(Painter city, int x, int z, Direction front, Block[] colors, int kind) {
        Rotation rotation = rotationFor(Direction.SOUTH, front);
        Painter s = city.child(x, 0, z, rotation);
        for (int lx = -1; lx <= 1; lx += 2) {
            for (int lz = -1; lz <= 1; lz += 2) {
                for (int y = 1; y <= 3; y++) {
                    s.set(lx, y, lz, Blocks.SPRUCE_FENCE);
                }
            }
        }
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 2; lz++) {
                s.set(lx, 4, lz, colors[Math.floorMod(lx, 2)]);
                if (lz <= 1) {
                    s.set(lx, 0, lz, Blocks.SPRUCE_PLANKS);
                }
            }
        }
        s.set(0, 1, 1, Blocks.SPRUCE_PLANKS);
        s.set(0, 1, -1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        BlockState goods = switch (Math.floorMod(kind, 6)) {
            case 0 -> Blocks.MELON.defaultBlockState();
            case 1 -> Blocks.POTTED_CORNFLOWER.defaultBlockState();
            case 2 -> Blocks.BREWING_STAND.defaultBlockState();
            case 3 -> Painter.facing(Blocks.CARVED_PUMPKIN, Direction.SOUTH);
            case 4 -> Blocks.POTTED_RED_TULIP.defaultBlockState();
            default -> Painter.axis(Blocks.HAY_BLOCK, Direction.Axis.Y);
        };
        s.set(0, 2, 1, goods);
    }

    // --------------------------------------------------------------------------------------------- decorations

    private static void decorations(Painter p) {
        // notice boards: quests (white notes) north-east, rankings (golden notes) north-west of the plaza
        noticeBoard(p, 10, -11, Blocks.WHITE_WALL_BANNER);
        noticeBoard(p, -10, -11, Blocks.YELLOW_WALL_BANNER);
        // post box south-east of the plaza
        p.set(12, 1, 10, Blocks.DARK_OAK_FENCE);
        p.set(12, 2, 10, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
        p.set(12, 3, 10, Blocks.RED_CARPET);
        p.set(12, 1, 11, Blocks.LANTERN);
        // goddess statue (respawn point) south-west of the plaza
        for (int x = -13; x <= -11; x++) {
            for (int z = 11; z <= 13; z++) {
                p.set(x, 1, z, x == -12 && z == 12 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE);
                if (x != -12 || z != 12) {
                    p.set(x, 2, z, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true)
                            .setValue(BlockStateProperties.CANDLES, 1 + Math.floorMod(x + z, 3)));
                }
            }
        }
        p.set(-12, 2, 12, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
        p.set(-12, 3, 12, Painter.axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
        p.set(-12, 4, 12, Blocks.CHISELED_QUARTZ_BLOCK);
        p.set(-12, 5, 12, Blocks.END_ROD);
        p.set(-13, 4, 12, Painter.stairs(Blocks.QUARTZ_STAIRS, Direction.EAST, Half.TOP));
        p.set(-11, 4, 12, Painter.stairs(Blocks.QUARTZ_STAIRS, Direction.WEST, Half.TOP));
        // castle forecourt: flower beds and trees
        flowerBed(p, -6, -18, -4, -14);
        flowerBed(p, 4, -18, 6, -14);
        tree(p, -9, -16);
        tree(p, 9, -16);
        // lamp posts along the roads
        for (int d : new int[]{16, 24, 32}) {
            for (int side = -1; side <= 1; side += 2) {
                if (d != 24) {
                    lampPost(p, side * 3, d);
                }
                lampPost(p, d * side, 3);
                lampPost(p, d * side, -3);
            }
        }
        lampPost(p, -3, -16);
        lampPost(p, 3, -16);
        lampPost(p, 6, 34);
        // garden trees and beds in the residential quarter and along the walls
        for (int[] t : new int[][]{{-20, 33}, {-31, 33}, {-9, 33}}) {
            tree(p, t[0], t[1]);
        }
        flowerBed(p, -27, 31, -24, 32);
        flowerBed(p, 4, 31, 7, 32);
        bench(p, -16, 31, Direction.NORTH);
        // banner over the gatekeeper
        p.set(5, 6, 35, Painter.facing(Blocks.RED_WALL_BANNER, Direction.NORTH));
    }

    private static void noticeBoard(Painter p, int x, int z, Block note) {
        for (int y = 1; y <= 3; y++) {
            p.set(x - 2, y, z, Painter.axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            p.set(x + 2, y, z, Painter.axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        }
        for (int dx = -1; dx <= 1; dx++) {
            p.set(x + dx, 2, z, Blocks.SPRUCE_PLANKS);
            p.set(x + dx, 3, z, Blocks.SPRUCE_PLANKS);
            p.set(x + dx, 2, z + 1, Painter.facing(note, Direction.SOUTH));
        }
        for (int dx = -2; dx <= 2; dx++) {
            p.set(x + dx, 4, z, Painter.slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
        }
        p.set(x, 3, z + 1, Painter.wallTorch(Direction.SOUTH));
    }

    private static void lampPost(Painter p, int x, int z) {
        if (!p.get(x, 1, z).isAir()) {
            return;
        }
        p.set(x, 1, z, Blocks.STONE_BRICK_WALL);
        p.set(x, 2, z, Blocks.DARK_OAK_FENCE);
        p.set(x, 3, z, Blocks.DARK_OAK_FENCE);
        p.set(x, 4, z, Blocks.LANTERN);
    }

    /** A 3-seat bench where the sitters look towards {@code looking}, with trapdoor armrests. */
    private static void bench(Painter city, int x, int z, Direction looking) {
        Painter b = city.child(x, 0, z, rotationFor(Direction.NORTH, looking));
        for (int lx = -1; lx <= 1; lx++) {
            b.set(lx, 1, 0, Painter.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        }
        b.set(-2, 1, 0, Painter.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.WEST, true, Half.BOTTOM));
        b.set(2, 1, 0, Painter.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.EAST, true, Half.BOTTOM));
    }

    private static void flowerBed(Painter p, int x1, int z1, int x2, int z2) {
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.CORNFLOWER,
                Blocks.OXEYE_DAISY, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP, Blocks.WHITE_TULIP,
                Blocks.LILY_OF_THE_VALLEY, Blocks.BLUE_ORCHID};
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                p.set(x, 0, z, Blocks.GRASS_BLOCK);
                double hash = GenNoise.hash(AstoriaLayout.SEED + 13, x, 0, z);
                if (hash < 0.12) {
                    p.set(x, 1, z, Blocks.FLOWERING_AZALEA);
                } else {
                    p.set(x, 1, z, flowers[(int) (hash * 997) % flowers.length]);
                }
            }
        }
    }

    /** A small round garden tree on a grass patch. */
    private static void tree(Painter p, int x, int z) {
        p.set(x, 0, z, Blocks.GRASS_BLOCK);
        for (int y = 1; y <= 4; y++) {
            p.set(x, y, z, Painter.axis(Blocks.OAK_LOG, Direction.Axis.Y));
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 3; dy <= 6; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double dist = dx * dx + (dy - 4.5) * (dy - 4.5) * 1.4 + dz * dz;
                    if (dist <= 5.2 && p.get(x + dx, dy, z + dz).isAir()) {
                        p.set(x + dx, dy, z + dz, leaves);
                    }
                }
            }
        }
    }

    /** The rotation that turns {@code local} into {@code world}. */
    static Rotation rotationFor(Direction local, Direction world) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(local) == world) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }
}
