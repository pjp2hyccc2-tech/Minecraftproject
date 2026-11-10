package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * The final raid, 終焉の玉座: the throne hall at the top of the tower. Purpur and end stone over obsidian, rods of white
 * light, violet windows, dragon statues along the processional, and the throne itself carved into the arena's far wall
 * (a relief: the arena keeps its floor and air).
 */
final class ThroneStyle extends Style {
    ThroneStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:obsidian";
        dado = 2;
        trim = "minecraft:crying_obsidian";
        frieze = 6;
        pilaster = "minecraft:purpur_pillar[axis=y]";
        pilasterSpacing = 5;
        stairs = "minecraft:purpur_stairs";
        slab = "minecraft:purpur_slab";
        wallId = "minecraft:end_stone_brick_wall";
        vault = true;
        ceiling = "minecraft:purpur_block";
        ceilingLight = "minecraft:pearlescent_froglight";
        border = "minecraft:obsidian";
        inlay = "minecraft:purpur_block";
        inlay2 = "minecraft:end_stone_bricks";
        runner = "minecraft:crying_obsidian";
        floorLight = "minecraft:pearlescent_froglight";
        hangLamp = "minecraft:end_rod[facing=down]";
        torch = "minecraft:end_rod";
        fence = "minecraft:iron_bars";
        grate = "minecraft:purple_stained_glass_pane";
        grateLight = "minecraft:pearlescent_froglight";
        railing = "minecraft:end_stone_brick_wall";
        banners = new String[]{"purple", "black", "magenta"};
        carpets = new String[]{"purple", "black"};
        setPieces = new String[]{"none"};
        floorVariants = new String[]{"minecraft:end_stone", "minecraft:purpur_block"};
        wallVariants = new String[]{"minecraft:end_stone_bricks", "minecraft:obsidian"};
        clutterChance = 0.15;
    }

    /** Medallions of glazed purple tiles round a light, black glass rings. */
    @Override
    String medallionBlock(int d, int dx, int dz, int radius) {
        if (d == 0) {
            return floorLight;
        }
        if (d == radius) {
            return "minecraft:obsidian";
        }
        return d % 2 == 1 ? "minecraft:purple_glazed_terracotta[facing=" + (dx >= 0 ? (dz >= 0 ? "north" : "east")
                : (dz >= 0 ? "west" : "south")) + "]" : "minecraft:magenta_glazed_terracotta[facing=north]";
    }

    @Override
    String emblemBlock(int d, int dx, int dz, int radius) {
        String base = super.emblemBlock(d, dx, dz, radius);
        if (base == null && d % 9 == 4 && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) {
            return "minecraft:purple_glazed_terracotta[facing=north]";
        }
        return base;
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        c.set(x, y, z, r.nextBoolean() ? "minecraft:end_rod[facing=up]" : "minecraft:purple_candle[candles=3,lit=true]");
    }

    @Override
    void sconce(Wall w, int a, int y) {
        c.setOnAir(w.x(a, 1), y, w.z(a, 1), "minecraft:end_rod[facing=" + w.in() + "]");
    }

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        int a = bay.mid();
        if (!window(w, a, 2, 5, "minecraft:purple_stained_glass", "minecraft:pearlescent_froglight")) {
            c.setOnAir(w.x(a, 1), 5, w.z(a, 1), banner(pick(banners), w.in()));
        }
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // a dragon sentinel: an obsidian body, purpur wings folded, eyes of light
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:purpur_pillar[axis=y]");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k == height - 2 ? "minecraft:purpur_block" : "minecraft:obsidian");
        }
        piece.add(x, y + height - 1, z, "minecraft:crying_obsidian");
        if (piece.commit()) {
            c.setOnAir(x + facing.dx, y + height - 1, z + facing.dz, "minecraft:end_rod[facing=" + facing.name + "]");
            for (Side side : new Side[]{left(facing), left(facing).opposite()}) {
                c.setOnAir(x + side.dx, y + height - 2, z + side.dz, "minecraft:purpur_stairs[facing="
                        + side.opposite().name + ",half=top]");
            }
        }
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:obsidian");
        piece.add(x, y + 1, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        piece.commit();
    }

    @Override
    void corridorBay(Link l, int a, int index) {
        if (index % 2 == 0) {
            grateNiche(l, a, 1);
            grateNiche(l, a, -1);
        } else {
            wallLamp(l, a, 1);
            wallLamp(l, a, -1);
        }
    }

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the last light: an obsidian obelisk with a heart of crying obsidian and a crown of light rods
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:purpur_pillar[axis=y]"
                        : "minecraft:purpur_slab[type=bottom]");
            }
        }
        piece.add(x, y + 1, z, "minecraft:obsidian");
        piece.add(x, y + 2, z, "minecraft:crying_obsidian");
        piece.add(x, y + 3, z, "minecraft:obsidian");
        piece.add(x, y + 4, z, "minecraft:pearlescent_froglight");
        piece.add(x, y + 5, z, "minecraft:end_rod[facing=up]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 4, z + side.dz, "minecraft:end_rod[facing=" + side.name + "]");
        }
    }

    @Override
    void portal(Wall w, int a) {
        super.portal(w, a);
        // the frame of an end portal under the veil (unbreakable, a faint light)
        for (int k = -1; k <= 1; k++) {
            c.setOnAir(w.x(a + k, 2), 1, w.z(a + k, 2), "minecraft:end_portal_frame[eye=" + (k == 0) + ",facing="
                    + w.in() + "]");
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:pearlescent_froglight";
    }

    @Override
    String portalVeil() {
        return "minecraft:black_stained_glass_pane";
    }

    @Override
    void hallExtras(Spaces.Hall hall) {
        BlockBox g = hall.box();
        // dragon sentinels and void planters of chorus along the side walls, black glass in the floor
        for (Side side : new Side[]{left(hall.front()), left(hall.front()).opposite()}) {
            Wall w = new Wall(g, side);
            for (int a = w.min() + 2; a <= w.max() - 2; a += 5) {
                if (Math.abs(a - w.mid()) > 3) {
                    statue(w.x(a, 2), 1, w.z(a, 2), Side.valueOf(w.in().toUpperCase(java.util.Locale.ROOT)), 4);
                    Canvas.Piece planter = c.piece();
                    planter.add(w.x(a + 2, 1), 1, w.z(a + 2, 1), "minecraft:end_stone");
                    planter.add(w.x(a + 2, 1), 2, w.z(a + 2, 1), "minecraft:chorus_plant[down=true,east=false,"
                            + "north=false,south=false,up=true,west=false]");
                    planter.add(w.x(a + 2, 1), 3, w.z(a + 2, 1), "minecraft:chorus_plant[down=true,east=false,"
                            + "north=false,south=false,up=false,west=false]");
                    planter.commit();
                }
            }
        }
        for (int x = g.minX() + 2; x <= g.maxX() - 2; x += 4) {
            for (int z = g.minZ() + 2; z <= g.maxZ() - 2; z += 4) {
                if (c.state(x, 0, z).equals("#floor")) {
                    c.set(x, 0, z, "minecraft:black_stained_glass");
                }
            }
        }
        for (Wall w : Geometry.walls(g)) {
            if (w.side() == hall.front()) {
                continue;
            }
            for (int a = w.min() + 2; a <= w.max() - 2; a += 4) {
                if (Math.abs(a - w.mid()) > 3) {
                    window(w, a, 2, 7, "minecraft:purple_stained_glass", "minecraft:pearlescent_froglight");
                }
            }
        }
    }

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the crown of the king of the end over the gate
        for (int y = y0; y <= Math.min(top, y0 + 3); y++) {
            for (int s = -3; s <= 3; s++) {
                int ds = Math.abs(s);
                String state = y == y0 ? "minecraft:obsidian" : y == y0 + 1 ? "minecraft:crying_obsidian"
                        : ds % 2 == 1 && y == y0 + 2 ? "minecraft:purpur_block"
                        : ds % 2 == 1 && y == y0 + 3 ? "minecraft:pearlescent_froglight" : null;
                if (state != null) {
                    c.setOnSolid(l.x(wallA, s), y, l.z(wallA, s), state);
                }
            }
        }
    }

    @Override
    void setPieceContent(Spaces.SetPiece piece) {
    }

    @Override
    void arenaExtras(Spaces.Arena arena) {
        // the throne, carved into the wall opposite the gate: a relief in the wall plane
        Wall w = new Wall(arena.box(), arena.entrySide().opposite());
        int mid = w.mid();
        int top = arena.box().maxY() - 2;
        for (int a = mid - 6; a <= mid + 6; a++) {
            int da = Math.abs(a - mid);
            for (int y = 1; y <= Math.min(top, 22); y++) {
                String state = null;
                if (da <= 2 && y >= 4 && y <= 18) {
                    state = y == 4 ? "minecraft:purpur_block" : da == 0 && y % 4 == 0 ? "minecraft:pearlescent_froglight"
                            : "minecraft:crying_obsidian";
                } else if (da <= 4 && y <= 3) {
                    state = y == 3 ? "minecraft:purpur_block" : "minecraft:obsidian";
                } else if (da >= 3 && da <= 4 && y >= 4 && y <= 8) {
                    state = y == 8 ? "minecraft:purpur_pillar[axis=y]" : "minecraft:obsidian";
                } else if (da >= 3 && da <= 6 && y >= 19 && y <= 21 - (da - 3)) {
                    state = "minecraft:obsidian";
                } else if (da == 6 && y >= 9 && y <= 18) {
                    state = "minecraft:purpur_pillar[axis=y]";
                } else if (da <= 2 && y >= 19 && y <= 22) {
                    state = y == 22 && da % 2 == 0 ? "minecraft:pearlescent_froglight" : "minecraft:obsidian";
                }
                if (state != null && c.solid(w.x(a, 0), y, w.z(a, 0))) {
                    c.set(w.x(a, 0), y, w.z(a, 0), state);
                }
            }
        }
        for (int a = mid - 9; a <= mid + 9; a += 18) {
            for (int y = 16; y >= 10; y -= 2) {
                c.setOnAir(w.x(a, 1), y, w.z(a, 1), banner("purple", w.in()));
            }
        }
    }

    @Override
    String arenaFire() {
        return "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]";
    }

    @Override
    String arenaWindow() {
        return "minecraft:purple_stained_glass";
    }
}
