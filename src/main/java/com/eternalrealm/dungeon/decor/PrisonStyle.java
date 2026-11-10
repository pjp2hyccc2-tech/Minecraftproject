package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d4 凍てつく地下牢: a prison carved into the ice. Deepslate and iron hold the ice walls, barred cells line the rooms
 * (bunks, shackles, prisoners frozen in ice behind the bars), chains and cages hang from the vaults, icicles glitter
 * under the ceilings, the warden's office and a cell block are the set pieces. Cold light: sea lanterns behind ice,
 * soul lanterns.
 */
final class PrisonStyle extends Style {
    private static final String ICICLE = "minecraft:end_rod[facing=down]";

    PrisonStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:polished_deepslate";
        dado = 2;
        trim = "minecraft:deepslate_tiles";
        frieze = 6;
        pilaster = "minecraft:deepslate_bricks";
        pilasterSpacing = 5;
        stairs = "minecraft:deepslate_tile_stairs";
        slab = "minecraft:deepslate_tile_slab";
        wallId = "minecraft:deepslate_tile_wall";
        ceiling = "#wall";
        ceilingLight = "minecraft:sea_lantern";
        border = "minecraft:polished_deepslate";
        inlay = "minecraft:blue_ice";
        inlay2 = "minecraft:packed_ice";
        runner = "minecraft:deepslate_tiles";
        floorLight = "minecraft:sea_lantern";
        hangLamp = "minecraft:lantern[hanging=true]";
        torch = "minecraft:soul_wall_torch";
        fence = "minecraft:iron_bars";
        grate = "minecraft:iron_bars";
        grateLight = "minecraft:sea_lantern";
        railing = "minecraft:iron_bars";
        banners = new String[]{"light_blue", "gray", "light_blue"};
        carpets = new String[]{"gray", "light_blue"};
        setPieces = new String[]{"cellblock", "warden"};
        floorVariants = new String[]{"minecraft:packed_ice", "minecraft:cracked_deepslate_tiles", "minecraft:blue_ice",
                "minecraft:snow_block"};
        wallVariants = new String[]{"minecraft:blue_ice", "minecraft:snow_block"};
        clutterChance = 0.25;
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(5)) {
            case 0 -> c.set(x, y, z, "minecraft:snow_block");
            case 1 -> c.set(x, y, z, "minecraft:white_carpet");
            case 2 -> c.set(x, y, z, "minecraft:skeleton_skull[rotation=" + r.nextInt(16) + "]");
            case 3 -> c.set(x, y, z, "minecraft:powder_snow_cauldron[level=3]");
            default -> c.set(x, y, z, "minecraft:chain[axis=" + (w.alongX() ? "x" : "z") + "]");
        }
    }

    /** Icicles: glittering rods hanging from the ceiling. */
    void icicle(int x, int top, int z) {
        c.setOnAir(x, top, z, ICICLE);
    }

    @Override
    void cornice(Wall w, int top) {
        super.cornice(w, top);
        for (int a = w.min() + 1; a <= w.max() - 1; a += 3) {
            icicle(w.x(a, 1), top - 1, w.z(a, 1));
        }
    }

    @Override
    void sconce(Wall w, int a, int y) {
        // a sea lantern frozen into the pillar behind clear ice
        c.setOnSolid(w.x(a, 0), y, w.z(a, 0), "minecraft:sea_lantern");
        c.setOnAir(w.x(a, 1), y + 1, w.z(a, 1), "minecraft:chain[axis=y]");
        c.setOnAir(w.x(a, 1), y, w.z(a, 1), "minecraft:soul_lantern[hanging=true]");
    }

    @Override
    boolean chandelier(int x, int z, int ceilingY, int low) {
        // an iron ring on a chain, lanterns under it, icicles between them
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y >= low + 2; y--) {
            piece.add(x, y, z, chain);
        }
        piece.add(x, low + 1, z, "minecraft:iron_bars");
        piece.add(x, low, z, ICICLE);
        for (Side side : Side.values()) {
            piece.add(x + side.dx, low + 1, z + side.dz, "minecraft:iron_bars");
            piece.add(x + side.dx, low, z + side.dz, side.ordinal() % 2 == 0 ? hangLamp
                    : "minecraft:soul_lantern[hanging=true]");
        }
        return piece.commit();
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        if (bay.width() >= 3 && chance(0.7) && cell(w, bay.mid() - 1, bay.mid() + 1)) {
            return;
        }
        int a = bay.mid();
        if (chance(0.5)) {
            shackles(w, a);
        } else {
            statue(w.x(a, 1), 1, w.z(a, 1), Side.valueOf(w.in().toUpperCase(java.util.Locale.ROOT)), 3);
        }
    }

    /**
     * A barred cell behind the wall from {@code a0} to {@code a1}: two blocks deep, a bunk, shackles, a prisoner frozen
     * in ice, a soul lantern; the bars stand in the wall plane, so the room is unchanged.
     */
    boolean cell(Wall w, int a0, int a1) {
        int xa = w.x(a0 - 1, -1);
        int xb = w.x(a1 + 1, -3);
        int za = w.z(a0 - 1, -1);
        int zb = w.z(a1 + 1, -3);
        if (!c.claim(new BlockBox(Math.min(xa, xb), 0, Math.min(za, zb), Math.max(xa, xb), 5, Math.max(za, zb)))) {
            return false;
        }
        for (int a = a0 - 1; a <= a1 + 1; a++) {
            for (int d = -3; d <= -1; d++) {
                for (int y = 0; y <= 5; y++) {
                    boolean inside = a >= a0 && a <= a1 && d >= -2 && y >= 1 && y <= 3;
                    c.set(w.x(a, d), y, w.z(a, d), inside ? Canvas.AIR : y == 0 ? "minecraft:polished_deepslate"
                            : "#wall");
                }
            }
        }
        for (int a = a0; a <= a1; a++) {
            for (int y = 1; y <= 3; y++) {
                c.set(w.x(a, 0), y, w.z(a, 0), "minecraft:iron_bars");
            }
        }
        c.set(w.x(a0, -2), 1, w.z(a0, -2), "minecraft:spruce_slab[type=top]");
        c.set(w.x(a0, -1), 1, w.z(a0, -1), "minecraft:spruce_slab[type=top]");
        c.set(w.x(a1, -2), 1, w.z(a1, -2), "minecraft:blue_ice");
        c.set(w.x(a1, -2), 2, w.z(a1, -2), "minecraft:packed_ice");
        c.set(w.x(a1, -2), 3, w.z(a1, -2), "minecraft:skeleton_skull[rotation=" + Side.valueOf(w.in()
                .toUpperCase(java.util.Locale.ROOT)).rotation() + "]");
        int mid = (a0 + a1) / 2;
        c.set(w.x(mid, -2), 3, w.z(mid, -2), "minecraft:soul_lantern[hanging=true]");
        c.set(w.x(mid, -2), 2, w.z(mid, -2), "minecraft:chain[axis=y]");
        c.set(w.x(mid, -1), 1, w.z(mid, -1), "minecraft:white_carpet");
        return true;
    }

    /** Shackles: chains down the wall and a skull below. */
    void shackles(Wall w, int a) {
        for (int k = -1; k <= 1; k += 2) {
            c.setOnAir(w.x(a + k, 1), 3, w.z(a + k, 1), "minecraft:chain[axis=y]");
            c.setOnAir(w.x(a + k, 1), 2, w.z(a + k, 1), "minecraft:tripwire_hook[attached=false,facing=" + w.in()
                    + ",powered=false]");
        }
        c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:skeleton_skull[rotation=" + r.nextInt(16) + "]");
    }

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        // a snow drift and an ice crystal
        Canvas.Piece drift = c.piece();
        drift.add(x, 1, z, "minecraft:snow_block");
        drift.add(x + sx, 1, z, "minecraft:white_carpet");
        drift.add(x, 1, z + sz, "minecraft:white_carpet");
        drift.commit();
        Canvas.Piece crystal = c.piece();
        crystal.add(x, 2, z, "minecraft:blue_ice");
        crystal.add(x, 3, z, "minecraft:light_blue_stained_glass");
        crystal.add(x, 4, z, "minecraft:end_rod[facing=up]");
        crystal.commit();
        c.setOnAir(x, top, z, "minecraft:cobweb");
    }

    @Override
    void roomExtras(Spaces.Room room, int ceilingY) {
        // chains and a cage hang from the ceiling
        BlockBox b = room.box();
        BlockPoint center = b.center(1);
        for (int k = -1; k <= 1; k += 2) {
            int x = center.x() + k * 4;
            int z = center.z() - k * 3;
            for (int y = ceilingY - 1; y >= ceilingY - 2; y--) {
                c.setOnAir(x, y, z, "minecraft:chain[axis=y]");
            }
        }
        cage(center.x() + 3, center.z() + 3, ceilingY);
    }

    /** A cage of bars hanging on a chain with a skull inside. */
    void cage(int x, int z, int ceilingY) {
        Canvas.Piece piece = c.piece();
        piece.add(x, ceilingY - 1, z, "minecraft:chain[axis=y]");
        piece.add(x, ceilingY - 2, z, "minecraft:skeleton_skull[rotation=3]");
        for (Side side : Side.values()) {
            piece.add(x + side.dx, ceilingY - 2, z + side.dz, "minecraft:iron_bars");
        }
        piece.commit();
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // a prisoner frozen solid: an ice block body, a skull, the chain still on
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:polished_deepslate");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k % 2 == 1 ? "minecraft:packed_ice" : "minecraft:blue_ice");
        }
        piece.add(x, y + height - 1, z, "minecraft:skeleton_skull[rotation=" + facing.rotation() + "]");
        piece.commit();
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:polished_deepslate");
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
                c.setOnAir(l.x(a, 0), Geometry.OPENING_TOP, l.z(a, 0), ICICLE);
            }
        }
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the frozen guardian: a knight of ice on a deepslate plinth holding up a soul lantern
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:polished_deepslate"
                        : "minecraft:deepslate_tile_slab[type=bottom]");
            }
        }
        piece.add(x, y + 1, z, "minecraft:blue_ice");
        piece.add(x, y + 2, z, "minecraft:packed_ice");
        piece.add(x, y + 3, z, "minecraft:blue_ice");
        piece.add(x, y + 4, z, "minecraft:sea_lantern");
        piece.add(x, y + 5, z, "minecraft:soul_lantern[hanging=false]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 4, z + side.dz, ICICLE.replace("down", side.name));
            c.setOnAir(x + side.dx, y + 1, z + side.dz, "minecraft:white_carpet");
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:sea_lantern";
    }

    @Override
    String portalVeil() {
        return "minecraft:light_blue_stained_glass_pane";
    }

    @Override
    void hallExtras(Spaces.Hall hall) {
        BlockBox g = hall.box();
        for (Wall w : Geometry.walls(g)) {
            if (w.side() == hall.front() || w.side() == hall.front().opposite()) {
                continue;
            }
            for (int a = w.min() + 1; a + 2 <= w.max() - 1; a += 5) {
                if (Math.abs(a + 1 - w.mid()) > 3) {
                    cell(w, a, a + 2);
                }
            }
        }
    }

    // ---------------------------------------------------------------- gate

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the warden's seal: a skull in an iron frame, chains to both sides
        for (int y = y0; y <= Math.min(top, y0 + 3); y++) {
            for (int s = -2; s <= 2; s++) {
                c.setOnSolid(l.x(wallA, s), y, l.z(wallA, s), Math.abs(s) == 2 || y == y0 || y == y0 + 3
                        ? "minecraft:polished_deepslate" : "minecraft:blue_ice");
            }
        }
        int inside = l.step() > 0 ? wallA - 1 : wallA + 1;
        for (int s = -2; s <= 2; s += 4) {
            for (int y = y0; y <= Math.min(top, y0 + 3); y++) {
                c.setOnAir(l.x(inside, s), y, l.z(inside, s), "minecraft:chain[axis=y]");
            }
        }
    }

    // ---------------------------------------------------------------- set pieces

    @Override
    void setPieceContent(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        if (piece.kind().equals("warden")) {
            warden(piece);
            return;
        }
        // the cell block: cells all round, a guard's lamp post in the middle
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == piece.doorSide()) {
                continue;
            }
            for (int a = w.min() + 1; a + 2 <= w.max() - 1; a += 4) {
                cell(w, a, a + 2);
            }
        }
        BlockPoint center = b.center(1);
        Canvas.Piece post = c.piece();
        post.add(center.x(), 1, center.z(), "minecraft:polished_deepslate");
        post.add(center.x(), 2, center.z(), "minecraft:deepslate_tile_wall");
        post.add(center.x(), 3, center.z(), "minecraft:deepslate_tile_wall");
        post.add(center.x(), 4, center.z(), "minecraft:lantern[hanging=false]");
        post.commit();
    }

    /** The warden's office: desk, ledger, keys on the wall, a stove, a rack of cells' keys. */
    private void warden(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockPoint center = b.center(1);
        Side facing = piece.doorSide();
        for (int k = -1; k <= 1; k++) {
            int x = center.x() + (facing.dx == 0 ? k : 0);
            int z = center.z() + (facing.dz == 0 ? k : 0);
            c.setOnAir(x, 1, z, "minecraft:dark_oak_slab[type=top]");
        }
        c.setOnAir(center.x(), 2, center.z(), "minecraft:lectern[facing=" + facing.name + ",has_book=false,"
                + "powered=false]");
        c.setOnAir(center.x() - facing.dx * 2, 1, center.z() - facing.dz * 2, "minecraft:spruce_stairs[facing="
                + facing.name + ",half=bottom]");
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == piece.doorSide()) {
                continue;
            }
            for (int a = w.min() + 1; a <= w.max() - 1; a += 2) {
                c.setOnAir(w.x(a, 1), 3, w.z(a, 1), "minecraft:tripwire_hook[attached=false,facing=" + w.in()
                        + ",powered=false]");
                if (Math.floorMod(a, 4) == 1) {
                    c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:chest[facing=" + w.in()
                            + ",type=single,waterlogged=false]");
                } else {
                    c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:barrel[facing=up,open=false]");
                }
            }
        }
        brazier(center.x() + 3, 1, center.z() + 3);
        chandelier(center.x(), center.z(), b.maxY(), b.maxY() - 3);
    }

    // ---------------------------------------------------------------- arena

    @Override
    void arenaExtras(Spaces.Arena arena) {
        // ice flowing down the arena walls in frozen streaks
        BlockBox b = arena.box();
        for (Wall w : Geometry.walls(b)) {
            for (int a = w.min() + 4; a <= w.max() - 4; a += 9) {
                for (int y = 3; y <= b.maxY() - 2; y++) {
                    if (c.state(w.x(a, 0), y, w.z(a, 0)).equals("#wall")) {
                        c.set(w.x(a, 0), y, w.z(a, 0), y % 3 == 0 ? "minecraft:packed_ice" : "minecraft:blue_ice");
                    }
                }
            }
        }
    }

    @Override
    String arenaFire() {
        return "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]";
    }
}
