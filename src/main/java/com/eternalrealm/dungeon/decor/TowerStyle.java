package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d8 終焉の塔: the dark tower (Glast Heim and Thanatos in spirit). Blackstone and deepslate under gothic vaults, purple
 * crystal clusters growing from obsidian, tall purple windows, chains and hanging cages, soul-fire braziers, shelves of
 * forbidden books with candles, hooded statues; a forbidden library and a ritual chamber as set pieces.
 */
final class TowerStyle extends Style {
    TowerStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:deepslate_tiles";
        dado = 1;
        trim = "minecraft:amethyst_block";
        frieze = 5;
        pilaster = "minecraft:polished_deepslate";
        pilasterSpacing = 5;
        stairs = "minecraft:polished_blackstone_brick_stairs";
        slab = "minecraft:polished_blackstone_brick_slab";
        wallId = "minecraft:polished_blackstone_brick_wall";
        vault = true;
        ceiling = "minecraft:polished_blackstone_bricks";
        ceilingLight = "minecraft:pearlescent_froglight";
        border = "minecraft:polished_deepslate";
        inlay = "minecraft:amethyst_block";
        inlay2 = "minecraft:obsidian";
        runner = "minecraft:crying_obsidian";
        floorLight = "minecraft:pearlescent_froglight";
        hangLamp = "minecraft:soul_lantern[hanging=true]";
        torch = "minecraft:soul_wall_torch";
        fence = "minecraft:iron_bars";
        grate = "minecraft:purple_stained_glass_pane";
        grateLight = "minecraft:pearlescent_froglight";
        railing = "minecraft:polished_blackstone_brick_wall";
        banners = new String[]{"purple", "black", "magenta"};
        carpets = new String[]{"purple", "black"};
        setPieces = new String[]{"library", "ritual"};
        floorVariants = new String[]{"minecraft:cracked_deepslate_tiles", "minecraft:polished_deepslate",
                "minecraft:obsidian"};
        wallVariants = new String[]{"minecraft:crying_obsidian", "minecraft:cracked_polished_blackstone_bricks",
                "minecraft:obsidian"};
        clutterChance = 0.3;
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(5)) {
            case 0 -> c.set(x, y, z, "minecraft:purple_candle[candles=" + (1 + r.nextInt(4)) + ",lit=true]");
            case 1 -> c.set(x, y, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
            case 2 -> c.set(x, y, z, "minecraft:wither_skeleton_skull[rotation=" + r.nextInt(16) + "]");
            case 3 -> c.set(x, y, z, "minecraft:black_candle[candles=" + (1 + r.nextInt(4)) + ",lit=true]");
            default -> c.set(x, y, z, "minecraft:chain[axis=" + (w.alongX() ? "x" : "z") + "]");
        }
    }

    /** The vault's top: blackstone ribs, crying obsidian between, purple lights. */
    @Override
    String ceilingAt(int lx, int lz, int d) {
        if (d < 4) {
            return ceiling;
        }
        if ((lx + lz) % 4 == 0 || (lx - lz) % 4 == 0) {
            return "minecraft:polished_blackstone_bricks";
        }
        return Math.floorMod(lx * 5 + lz * 3, 9) == 0 ? "minecraft:pearlescent_froglight" : "minecraft:obsidian";
    }

    /** A crystal growth: amethyst on obsidian, clusters around it. */
    boolean crystals(int x, int z, int height) {
        Canvas.Piece piece = c.piece();
        piece.add(x, 1, z, "minecraft:obsidian");
        for (int k = 2; k <= height; k++) {
            piece.add(x, k, z, k == height ? "minecraft:budding_amethyst" : "minecraft:amethyst_block");
        }
        piece.add(x, height + 1, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        if (!piece.commit()) {
            return false;
        }
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, 1, z + side.dz, side.ordinal() % 2 == 0
                    ? "minecraft:large_amethyst_bud[facing=up,waterlogged=false]"
                    : "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        }
        return true;
    }

    @Override
    boolean chandelier(int x, int z, int ceilingY, int low) {
        // a ring of black iron with soul lanterns and purple candles
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y >= low + 2; y--) {
            piece.add(x, y, z, chain);
        }
        piece.add(x, low + 1, z, "minecraft:iron_bars");
        piece.add(x, low, z, "minecraft:amethyst_cluster[facing=down,waterlogged=false]");
        for (Side side : Side.values()) {
            piece.add(x + side.dx, low + 1, z + side.dz, "minecraft:iron_bars");
            piece.add(x + side.dx, low, z + side.dz, side.ordinal() % 2 == 0 ? hangLamp : "minecraft:lantern[hanging=true]");
        }
        return piece.commit();
    }

    @Override
    void sconce(Wall w, int a, int y) {
        Canvas.Piece piece = c.piece();
        piece.add(w.x(a, 1), y, w.z(a, 1), "minecraft:polished_blackstone_brick_slab[type=top]");
        piece.add(w.x(a, 1), y + 1, w.z(a, 1), "minecraft:purple_candle[candles=3,lit=true]");
        if (!piece.commit()) {
            c.setOnAir(w.x(a, 1), y, w.z(a, 1), torch(w.in()));
        }
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        int a = bay.mid();
        switch (r.nextInt(4)) {
            case 0 -> {
                if (window(w, a, 2, 5, "minecraft:purple_stained_glass", "minecraft:pearlescent_froglight")) {
                    c.setOnSolid(w.x(a, 0), 6, w.z(a, 0), "minecraft:chiseled_polished_blackstone");
                }
            }
            case 1 -> bookshelves(w, bay.a0(), bay.a1());
            case 2 -> {
                c.setOnAir(w.x(a, 1), 5, w.z(a, 1), banner(pick(banners), w.in()));
                brazier(w.x(a, 1), 1, w.z(a, 1));
            }
            default -> crystals(w.x(a, 1), w.z(a, 1), 2);
        }
    }

    /** Shelves of forbidden books against the wall, candles on top. */
    void bookshelves(Wall w, int a0, int a1) {
        for (int a = Math.max(a0, (a0 + a1) / 2 - 1); a <= Math.min(a1, (a0 + a1) / 2 + 1); a++) {
            Canvas.Piece shelf = c.piece();
            shelf.add(w.x(a, 1), 1, w.z(a, 1), "minecraft:bookshelf");
            shelf.add(w.x(a, 1), 2, w.z(a, 1), a % 2 == 0 ? "minecraft:chiseled_bookshelf[facing=" + w.in()
                    + ",slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,"
                    + "slot_4_occupied=false,slot_5_occupied=true]" : "minecraft:bookshelf");
            if (shelf.commit()) {
                c.setOnAir(w.x(a, 1), 3, w.z(a, 1), "minecraft:purple_candle[candles=" + (1 + Math.floorMod(a, 3))
                        + ",lit=true]");
            }
        }
    }

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        if (!crystals(x, z, 3)) {
            c.setOnAir(x, 1, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        }
        for (int y = top; y >= top - 2; y--) {
            c.setOnAir(x + sx, y, z + sz, "minecraft:chain[axis=y]");
        }
    }

    @Override
    void roomExtras(Spaces.Room room, int ceilingY) {
        // cages hanging from the vault, chains of different lengths
        BlockBox b = room.box();
        BlockPoint center = b.center(1);
        for (int k = -1; k <= 1; k += 2) {
            cage(center.x() + k * 4, center.z() + k * 3, ceilingY);
            for (int y = ceilingY - 1; y >= ceilingY - 3; y--) {
                c.setOnAir(center.x() - k * 3, y, center.z() + k * 4, "minecraft:chain[axis=y]");
            }
        }
    }

    void cage(int x, int z, int ceilingY) {
        Canvas.Piece piece = c.piece();
        piece.add(x, ceilingY - 1, z, "minecraft:chain[axis=y]");
        piece.add(x, ceilingY - 2, z, "minecraft:chain[axis=y]");
        piece.add(x, ceilingY - 3, z, "minecraft:wither_skeleton_skull[rotation=5]");
        for (Side side : Side.values()) {
            piece.add(x + side.dx, ceilingY - 3, z + side.dz, "minecraft:iron_bars");
        }
        piece.commit();
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // a hooded acolyte: blackstone robe, an obsidian hood, candles at its feet
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:polished_blackstone");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k == height - 2 ? "minecraft:crying_obsidian" : "minecraft:blackstone_wall");
        }
        piece.add(x, y + height - 1, z, "minecraft:wither_skeleton_skull[rotation=" + facing.rotation() + "]");
        piece.commit();
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_polished_blackstone");
        piece.add(x, y + 1, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        piece.commit();
    }

    // ---------------------------------------------------------------- corridors

    @Override
    void corridorBay(Link l, int a, int index) {
        switch (index % 3) {
            case 0 -> {
                grateNiche(l, a, 1);
                grateNiche(l, a, -1);
            }
            case 1 -> {
                statueNiche(l, a, 1);
                statueNiche(l, a, -1);
            }
            default -> {
                wallLamp(l, a, 1);
                wallLamp(l, a, -1);
                for (int sign = -1; sign <= 1; sign += 2) {
                    c.setOnSolid(l.x(a, sign * l.half()), 3, l.z(a, sign * l.half()), "minecraft:crying_obsidian");
                }
            }
        }
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the void crystal: an obsidian spire crowned with amethyst, crying obsidian at its heart
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:chiseled_polished_blackstone"
                        : "minecraft:polished_blackstone_brick_slab[type=bottom]");
            }
        }
        piece.add(x, y + 1, z, "minecraft:obsidian");
        piece.add(x, y + 2, z, "minecraft:crying_obsidian");
        piece.add(x, y + 3, z, "minecraft:obsidian");
        piece.add(x, y + 4, z, "minecraft:amethyst_block");
        piece.add(x, y + 5, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 1, z + side.dz, "minecraft:purple_candle[candles=4,lit=true]");
            c.setOnAir(x + side.dx, y + 4, z + side.dz, "minecraft:amethyst_cluster[facing=" + side.name
                    + ",waterlogged=false]");
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:pearlescent_froglight";
    }

    @Override
    String portalVeil() {
        return "minecraft:purple_stained_glass_pane";
    }

    @Override
    void hallExtras(Spaces.Hall hall) {
        BlockBox g = hall.box();
        for (Wall w : Geometry.walls(g)) {
            if (w.side() == hall.front()) {
                continue;
            }
            for (int a = w.min() + 2; a <= w.max() - 2; a += 4) {
                if (Math.abs(a - w.mid()) > 3) {
                    window(w, a, 2, 6, "minecraft:purple_stained_glass", "minecraft:pearlescent_froglight");
                }
            }
        }
    }

    // ---------------------------------------------------------------- gate

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the black seal: a crying-obsidian eye in an obsidian arch, crystal points around it
        for (int y = y0; y <= Math.min(top, y0 + 4); y++) {
            for (int s = -3; s <= 3; s++) {
                int ds = Math.abs(s);
                int dy = y - y0;
                String state = ds == 0 && dy == 2 ? "minecraft:pearlescent_froglight"
                        : ds <= 1 && dy >= 1 && dy <= 3 ? "minecraft:crying_obsidian"
                        : ds + Math.abs(dy - 2) == 3 ? "minecraft:amethyst_block"
                        : ds <= 2 && dy == 4 ? "minecraft:obsidian" : null;
                if (state != null) {
                    c.setOnSolid(l.x(wallA, s), y, l.z(wallA, s), state);
                }
            }
        }
    }

    // ---------------------------------------------------------------- set pieces

    @Override
    void setPieceContent(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockPoint center = b.center(1);
        if (piece.kind().equals("ritual")) {
            // a summoning circle: rings of candles and soul fire round a crying-obsidian altar
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                    if (d == 4) {
                        c.setOnSolid(center.x() + dx, 0, center.z() + dz, "minecraft:crying_obsidian");
                        if ((dx + dz) % 2 == 0) {
                            c.setOnAir(center.x() + dx, 1, center.z() + dz, "minecraft:purple_candle[candles=3,"
                                    + "lit=true]");
                        }
                    } else if (d == 2) {
                        c.setOnSolid(center.x() + dx, 0, center.z() + dz, "minecraft:amethyst_block");
                    }
                }
            }
            Canvas.Piece altar = c.piece();
            altar.add(center.x(), 1, center.z(), "minecraft:crying_obsidian");
            altar.add(center.x(), 2, center.z(), "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,"
                    + "waterlogged=false]");
            altar.commit();
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    crystals(center.x() + dx * 5, center.z() + dz * 5, 3);
                }
            }
            return;
        }
        // the forbidden library: shelves in rows, lecterns, candles, a reading table
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == piece.doorSide()) {
                continue;
            }
            for (int a = w.min(); a <= w.max(); a++) {
                for (int y = 1; y <= 3; y++) {
                    c.setOnAir(w.x(a, 1), y, w.z(a, 1), y == 2 && a % 3 == 0 ? "minecraft:chiseled_bookshelf[facing="
                            + w.in() + ",slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,"
                            + "slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]" : "minecraft:bookshelf");
                }
                if (a % 4 == 0) {
                    c.setOnAir(w.x(a, 1), 4, w.z(a, 1), "minecraft:purple_candle[candles=2,lit=true]");
                }
            }
        }
        boolean alongX = piece.doorSide() == Side.NORTH || piece.doorSide() == Side.SOUTH;
        for (int k = -2; k <= 2; k += 4) {
            for (int step = -3; step <= 3; step++) {
                int x = center.x() + (alongX ? step : k);
                int z = center.z() + (alongX ? k : step);
                c.setOnAir(x, 1, z, "minecraft:bookshelf");
                c.setOnAir(x, 2, z, Math.floorMod(step, 2) == 0 ? "minecraft:bookshelf"
                        : "minecraft:black_candle[candles=2,lit=true]");
            }
        }
        c.setOnAir(center.x(), 1, center.z(), "minecraft:lectern[facing=" + piece.doorSide().name
                + ",has_book=false,powered=false]");
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
