package com.eternalrealm.world.forge;

import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.GenNoise;
import com.eternalrealm.world.gen.LandmarkSite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The themed dungeon gates. Every gate is drawn in the landmark frame of {@link Painter}: the gate at the origin with
 * its portal at z = -1, players arriving from +z (the side facing the capital), floor at y = 0.
 */
final class LandmarkStructures {
    private LandmarkStructures() {
    }

    /** Blocks of one theme. */
    record Palette(Block base, Block alt, Block pillar, Block accent, Block stairs, Block slab, Block portal, Block light,
                   Block path, Block top, Block filler) {
        static Palette of(DungeonTheme theme) {
            return switch (theme) {
                case FOREST -> new Palette(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.DARK_OAK_LOG,
                        Blocks.SHROOMLIGHT, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB,
                        Blocks.GREEN_STAINED_GLASS, Blocks.LANTERN, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.DIRT);
                case DESERT_TEMPLE -> new Palette(Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.CHISELED_SANDSTONE,
                        Blocks.ORANGE_TERRACOTTA, Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB,
                        Blocks.ORANGE_STAINED_GLASS, Blocks.LANTERN, Blocks.SMOOTH_SANDSTONE, Blocks.SAND, Blocks.SANDSTONE);
                case ICE_PRISON -> new Palette(Blocks.STONE_BRICKS, Blocks.PACKED_ICE, Blocks.POLISHED_DIORITE,
                        Blocks.BLUE_ICE, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.LIGHT_BLUE_STAINED_GLASS,
                        Blocks.SOUL_LANTERN, Blocks.POLISHED_ANDESITE, Blocks.SNOW_BLOCK, Blocks.STONE);
                case ICE_CASTLE -> new Palette(Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.QUARTZ_PILLAR, Blocks.SEA_LANTERN,
                        Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.CYAN_STAINED_GLASS, Blocks.SEA_LANTERN,
                        Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.STONE);
                case VOLCANO -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BASALT, Blocks.POLISHED_BASALT,
                        Blocks.MAGMA_BLOCK, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
                        Blocks.RED_STAINED_GLASS, Blocks.LANTERN, Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE,
                        Blocks.BASALT);
                case SKY_RUINS -> new Palette(Blocks.QUARTZ_BRICKS, Blocks.CALCITE, Blocks.QUARTZ_PILLAR,
                        Blocks.PRISMARINE_BRICKS, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.WHITE_STAINED_GLASS,
                        Blocks.END_ROD, Blocks.SMOOTH_QUARTZ, Blocks.GRASS_BLOCK, Blocks.DIRT);
                case DARK_TOWER, END_THRONE -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DEEPSLATE_TILES,
                        Blocks.POLISHED_BASALT, Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
                        Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.PURPLE_STAINED_GLASS, Blocks.SOUL_LANTERN,
                        Blocks.POLISHED_DEEPSLATE, Blocks.BLACKSTONE, Blocks.DEEPSLATE);
                default -> new Palette(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
                        Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
                        Blocks.BLACK_STAINED_GLASS, Blocks.LANTERN, Blocks.COBBLESTONE, Blocks.GRASS_BLOCK, Blocks.DIRT);
            };
        }

        BlockState wall(int x, int y, int z) {
            return (GenNoise.hash(AstoriaLayout.SEED + 31, x, y, z) < 0.22 ? alt : base).defaultBlockState();
        }

        BlockState pillarY() {
            return Painter.axis(pillar, Direction.Axis.Y);
        }
    }

    /** The standard gate of d1..d7: a framed portal with an arch, a back wall, themed extras and a path. */
    static void gate(Painter p, DungeonTheme theme, LandmarkSite site) {
        Palette c = Palette.of(theme);
        int height = theme == DungeonTheme.ICE_CASTLE ? 9 : 7;
        // floor of the gate and the approach path to the waypoint pad
        for (int x = -5; x <= 5; x++) {
            for (int z = -4; z <= 2; z++) {
                p.set(x, 0, z, Math.abs(x) <= 2 && z >= -1 ? c.path().defaultBlockState() : c.wall(x, 0, z));
            }
        }
        for (int z = 3; z <= site.waypoint().dz() - 2; z++) {
            for (int x = -1; x <= 1; x++) {
                p.set(x, 0, z, (x == 0 || GenNoise.hash(AstoriaLayout.SEED + 41, x, 0, z) < 0.7 ? c.path() : c.alt())
                        .defaultBlockState());
            }
        }
        // pillars, back wall, lintel, arch and portal
        for (int y = 1; y <= height; y++) {
            for (int z = -2; z <= 0; z++) {
                for (int side = -1; side <= 1; side += 2) {
                    p.set(side * 3, y, z, z == 0 ? c.pillarY() : c.wall(side * 3, y, z));
                    p.set(side * 4, y, z, c.wall(side * 4, y, z));
                }
            }
            for (int x = -2; x <= 2; x++) {
                p.set(x, y, -2, c.wall(x, y, -2));
            }
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = -2; z <= 0; z++) {
                p.set(x, height + 1, z, Math.abs(x) <= 4 ? c.wall(x, height + 1, z) : Painter.slab(c.slab(), SlabType.BOTTOM));
            }
            if (Math.abs(x) <= 3) {
                p.set(x, height + 2, -1, Painter.slab(c.slab(), SlabType.BOTTOM));
            }
        }
        p.set(0, height + 1, 1, c.accent());
        p.set(0, height + 2, 0, c.accent());
        for (int side = -1; side <= 1; side += 2) {
            p.set(side * 2, height, 0, Painter.stairs(c.stairs(), side < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
            p.set(side * 2, height, -1, Painter.stairs(c.stairs(), side < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
            p.set(side * 4, height + 2, 0, c.light());
            p.set(side * 3, 1, 1, Painter.stairs(c.stairs(), Direction.SOUTH));
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y < height; y++) {
                if (Math.abs(x) == 2 && y == height) {
                    continue;
                }
                p.set(x, y, -1, c.portal());
            }
            p.set(x, height, -1, Math.abs(x) < 2 ? c.portal().defaultBlockState()
                    : Painter.stairs(c.stairs(), x < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
            for (int y = 1; y <= height; y++) {
                if (Math.abs(x) < 2 || y < height) {
                    p.set(x, y, 0, Blocks.AIR);
                }
            }
        }
        switch (theme) {
            case UNDERGROUND_CITY -> undergroundExtras(p, c);
            case FOREST -> forestExtras(p, c, height);
            case DESERT_TEMPLE -> desertExtras(p, c, height);
            case ICE_PRISON -> prisonExtras(p, c, height);
            case ICE_CASTLE -> castleExtras(p, c, height);
            case VOLCANO -> volcanoExtras(p, c, height);
            case SKY_RUINS -> skyExtras(p, c, height);
            default -> {
            }
        }
        spots(p, c, site);
    }

    /** A marked NPC spot, the entrance plate and the waypoint pad with its waystone. */
    static void spots(Painter p, Palette c, LandmarkSite site) {
        LandmarkSite.Local entrance = site.entrance();
        p.set(entrance.dx(), 0, entrance.dz(), Blocks.CHISELED_STONE_BRICKS);
        LandmarkSite.Local npc = site.npc();
        p.set(npc.dx(), 0, npc.dz(), c.pillarY());
        for (int y = 1; y <= npc.dy() + 1; y++) {
            p.set(npc.dx(), y, npc.dz(), Blocks.AIR);
        }
        p.set(npc.dx() + 1, 1, npc.dz(), Blocks.DARK_OAK_FENCE);
        p.set(npc.dx() + 1, 2, npc.dz(), c.light());
        LandmarkSite.Local waypoint = site.waypoint();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
                p.set(waypoint.dx() + x, 0, waypoint.dz() + z, ring ? c.base().defaultBlockState()
                        : (x == 0 && z == 0 ? Blocks.CHISELED_STONE_BRICKS : c.path()).defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    p.set(waypoint.dx() + x, y, waypoint.dz() + z, Blocks.AIR);
                }
            }
        }
        p.set(waypoint.dx() + 3, 0, waypoint.dz(), c.base());
        p.set(waypoint.dx() + 3, 1, waypoint.dz(), c.pillarY());
        p.set(waypoint.dx() + 3, 2, waypoint.dz(), Blocks.LODESTONE);
        p.set(waypoint.dx() + 3, 3, waypoint.dz(), c.light());
    }

    private static void undergroundExtras(Painter p, Palette c) {
        // the arch opens onto a roofed stairwell leading down to a sealed iron gate
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= -1; z++) {
                for (int y = 1; y <= 6; y++) {
                    p.set(x, y, z, Blocks.AIR);
                }
                p.set(x, 0, z, c.path());
            }
        }
        for (int step = 0; step < 5; step++) {
            int z = -3 - step;
            for (int x = -2; x <= 2; x++) {
                for (int y = -step; y <= 4; y++) {
                    p.set(x, y, z, Math.abs(x) == 2 ? c.wall(x, y, z) : Blocks.AIR.defaultBlockState());
                }
                p.set(x, -step - 1, z, Math.abs(x) == 2 ? c.wall(x, -step - 1, z)
                        : Painter.stairs(c.stairs(), Direction.SOUTH));
                p.set(x, 5, z, c.wall(x, 5, z));
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = -5; y <= 0; y++) {
                p.set(x, y, -8, Math.abs(x) <= 1 && y >= -4 && y <= -2 ? Blocks.IRON_BARS.defaultBlockState()
                        : c.wall(x, y, -8));
            }
        }
        p.set(0, 4, -5, Painter.hanging(Blocks.LANTERN));
        for (int side = -1; side <= 1; side += 2) {
            p.set(side * 5, 1, 0, Blocks.MOSSY_COBBLESTONE_WALL);
            p.set(side * 5, 2, 0, Blocks.LANTERN);
            p.set(side * 4, 5, 1, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.NORTH, true));
        }
    }

    private static void forestExtras(Painter p, Palette c, int height) {
        // the gate grows out of two ancient trunks whose roots spread over the ground
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 0; y <= height + 4; y++) {
                p.set(side * 5, y, -1, Painter.axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
                p.set(side * 5, y, 0, Painter.axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
            }
            for (int i = 1; i <= 3; i++) {
                p.set(side * (5 + i), 0, i - 1, Painter.axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
                p.set(side * (5 + i), 1, i - 2, Painter.axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
            }
        }
        for (int x = -5; x <= 5; x++) {
            p.set(x, height + 3, -1, Painter.axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        }
        BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        for (int x = -7; x <= 7; x++) {
            for (int y = height + 4; y <= height + 7; y++) {
                for (int z = -4; z <= 2; z++) {
                    double d = x * x / 49.0 + (y - height - 5.0) * (y - height - 5.0) / 6.0 + (z + 1) * (z + 1) / 9.0;
                    if (d <= 1.0 && p.get(x, y, z).isAir()) {
                        p.set(x, y, z, leaves);
                    }
                }
            }
        }
        for (int x = -4; x <= 4; x += 2) {
            p.set(x, height + 1, 1, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.NORTH, true));
            p.set(x, 1, 2, Blocks.MOSS_CARPET);
        }
        p.set(-2, height + 4, -1, c.accent());
        p.set(2, height + 4, -1, c.accent());
    }

    private static void desertExtras(Painter p, Palette c, int height) {
        // temple front: pediment, blue and orange terracotta, half buried in sand
        for (int layer = 0; layer <= 5; layer++) {
            for (int x = -6 + layer; x <= 6 - layer; x++) {
                int y = height + 2 + layer;
                boolean edge = Math.abs(x) == 6 - layer;
                p.set(x, y, 0, edge ? Painter.stairs(c.stairs(), x < 0 ? Direction.EAST : Direction.WEST)
                        : c.wall(x, y, 0));
                p.set(x, y, -1, c.wall(x, y, -1));
            }
        }
        p.set(0, height + 4, 1, Blocks.BLUE_TERRACOTTA);
        p.set(-1, height + 3, 1, Blocks.ORANGE_TERRACOTTA);
        p.set(1, height + 3, 1, Blocks.ORANGE_TERRACOTTA);
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= height; y++) {
                p.set(side * 6, y, 1, y == height ? Blocks.CHISELED_SANDSTONE.defaultBlockState()
                        : Painter.axis(Blocks.CUT_SANDSTONE, Direction.Axis.Y));
            }
            for (int x = 7; x <= 9; x++) {
                for (int y = 1; y <= 9 - x + 1; y++) {
                    p.set(side * x, y, -1, Blocks.SAND);
                    p.set(side * x, y, 0, Blocks.SAND);
                }
            }
            p.set(side * 4, 1, 2, Blocks.DECORATED_POT);
        }
    }

    private static void prisonExtras(Painter p, Palette c, int height) {
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y < height; y++) {
                p.set(x, y, 0, Blocks.IRON_BARS);
            }
            p.set(x, 1, 0, Blocks.AIR);
            p.set(x, 2, 0, Blocks.AIR);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int y = height - 2; y <= height; y++) {
                p.set(side * 2, y, 1, Painter.axis(Blocks.CHAIN, Direction.Axis.Y));
            }
            p.set(side * 2, height - 3, 1, Painter.hanging(Blocks.SOUL_LANTERN));
            for (int y = 1; y <= height + 2; y++) {
                p.set(side * 6, y, -1, c.wall(side * 6, y, -1));
                p.set(side * 6, y, 0, Blocks.PACKED_ICE);
            }
            p.set(side * 6, height + 3, 0, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 2));
        }
    }

    private static void castleExtras(Painter p, Palette c, int height) {
        // two ice towers with crenellations flank the gate
        for (int side = -1; side <= 1; side += 2) {
            int cx = side * 7;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = -3; z <= 1; z++) {
                    boolean edge = Math.abs(x - cx) == 2 || z == -3 || z == 1;
                    for (int y = 0; y <= height + 5; y++) {
                        p.set(x, y, z, edge ? (y % 4 == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState()
                                : Blocks.AIR.defaultBlockState());
                    }
                    p.set(x, height + 5, z, Blocks.PACKED_ICE);
                    if (edge && Math.floorMod(x + z, 2) == 0) {
                        p.set(x, height + 6, z, Blocks.BLUE_ICE);
                        p.set(x, height + 7, z, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1));
                    }
                }
            }
            p.set(cx, height + 6, -1, Blocks.SEA_LANTERN);
            p.set(cx, 5, 2, Painter.facing(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.SOUTH));
        }
        for (int x = -4; x <= 4; x++) {
            p.set(x, height + 3, 0, (x % 2 == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState());
        }
    }

    private static void volcanoExtras(Painter p, Palette c, int height) {
        // the gate is the mouth of a basalt mound; lava falls into two basins on its sides
        for (int x = -9; x <= 9; x++) {
            for (int z = -8; z <= 1; z++) {
                double d = Math.sqrt(x * x / 81.0 + (z + 3.0) * (z + 3.0) / 25.0);
                if (d > 1.0) {
                    continue;
                }
                int top = (int) Math.round((height + 5) * Math.sqrt(1.0 - d * d));
                for (int y = 1; y <= top; y++) {
                    if (p.get(x, y, z).isAir() && !(Math.abs(x) <= 2 && z >= -1 && y <= height)) {
                        boolean crack = GenNoise.hash(AstoriaLayout.SEED + 51, x, y, z) < 0.07;
                        p.set(x, y, z, crack ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                : (y % 3 == 0 ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState());
                    }
                }
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 6;
            // basin
            for (int bx = x - 1; bx <= x + 1; bx++) {
                for (int bz = 1; bz <= 3; bz++) {
                    boolean rim = bx != x || bz != 2;
                    p.set(bx, 0, bz, Blocks.BLACKSTONE);
                    p.set(bx, 1, bz, rim ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.LAVA.defaultBlockState());
                }
            }
            // a spout whose lava falls straight down into the basin
            int spout = 6;
            p.set(x, spout, 2, Blocks.LAVA);
            p.set(x - 1, spout, 2, Blocks.BLACKSTONE);
            p.set(x + 1, spout, 2, Blocks.BLACKSTONE);
            p.set(x, spout, 3, Blocks.BLACKSTONE);
            p.set(x, spout, 1, Blocks.BLACKSTONE);
            p.set(x, spout + 1, 2, Blocks.MAGMA_BLOCK);
            for (int y = 2; y < spout; y++) {
                p.set(x, y, 2, Blocks.AIR);
            }
            p.level().scheduleTick(p.world(x, spout, 2), net.minecraft.world.level.material.Fluids.LAVA, 1);
        }
    }

    private static void skyExtras(Painter p, Palette c, int height) {
        // broken colonnade, floating stones and light rods
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -6; z <= 6; z += 4) {
                int x = side * 8;
                int top = 2 + (int) (GenNoise.hash(AstoriaLayout.SEED + 61, x, 0, z) * 6);
                for (int y = 1; y <= top; y++) {
                    p.set(x, y, z, c.pillarY());
                }
                p.set(x, top + 1, z, Blocks.CHISELED_QUARTZ_BLOCK);
            }
        }
        int[][] floating = {{-3, 4, -1}, {2, 6, 0}, {0, 8, -3}, {-5, 3, 1}, {4, 2, -2}};
        for (int[] f : floating) {
            p.set(f[0], height + f[1], f[2], f[1] % 2 == 0 ? Blocks.QUARTZ_BRICKS : Blocks.PRISMARINE_BRICKS);
        }
        for (int x = -3; x <= 3; x += 3) {
            p.set(x, height + 3, -1, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        }
        p.set(0, height + 3, 0, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
    }

    /** The great gate of the dark tower, cut into its south wall (d8). */
    static void towerGate(Painter p, LandmarkSite site) {
        Palette c = Palette.of(DungeonTheme.DARK_TOWER);
        // opening through the wall
        for (int x = -3; x <= 3; x++) {
            for (int z = 10; z <= 14; z++) {
                for (int y = 1; y <= 9; y++) {
                    p.set(x, y, z, Blocks.AIR);
                }
            }
        }
        // frame: pillars, lintel, arch, sealed portal
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= 11; y++) {
                p.set(side * 4, y, 14, c.pillarY());
                p.set(side * 4, y, 13, c.wall(side * 4, y, 13));
            }
            p.set(side * 3, 9, 14, Painter.stairs(c.stairs(), side < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
            p.set(side * 3, 9, 13, Painter.stairs(c.stairs(), side < 0 ? Direction.WEST : Direction.EAST, Half.TOP));
            p.set(side * 5, 7, 14, Painter.facing(Blocks.PURPLE_WALL_BANNER, Direction.SOUTH));
            p.set(side * 6, 1, 17, Blocks.POLISHED_BLACKSTONE_BRICKS);
            p.set(side * 6, 2, 17, Blocks.SOUL_CAMPFIRE);
            for (int y = 7; y <= 9; y++) {
                p.set(side * 2, y, 15, Painter.axis(Blocks.CHAIN, Direction.Axis.Y));
            }
            p.set(side * 2, 6, 15, Painter.hanging(Blocks.SOUL_LANTERN));
        }
        for (int x = -4; x <= 4; x++) {
            p.set(x, 10, 14, x == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : c.base().defaultBlockState());
            p.set(x, 11, 14, Math.abs(x) <= 3 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                    : c.base().defaultBlockState());
            p.set(x, 10, 15, Painter.slab(c.slab(), SlabType.TOP));
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 8; y++) {
                p.set(x, y, 11, Math.abs(x) == 2 || y == 8 ? Blocks.CRYING_OBSIDIAN : c.portal());
            }
            p.set(x, 9, 11, c.base());
        }
        // a processional path of deepslate tiles to the entrance plate
        for (int z = 15; z <= site.waypoint().dz() - 2; z++) {
            for (int x = -2; x <= 2; x++) {
                p.set(x, 0, z, Math.abs(x) == 2 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_DEEPSLATE);
            }
        }
        spots(p, c, site);
    }
}
