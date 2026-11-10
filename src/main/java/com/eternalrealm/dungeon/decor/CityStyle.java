package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d1 失われた地下都市: a royal city buried under the capital, now a bandits' den. Payon-cave timber frames and beams
 * hold up the stone, paper lanterns hang from them, the walls are the fronts of old houses (half-timbered, with lit
 * windows), bandits' barrels, crates and map tables crowd the corners, canals run through the set pieces.
 */
final class CityStyle extends Style {
    private static final String TIMBER = "minecraft:spruce_log";
    private static final String STRIPPED = "minecraft:stripped_spruce_log";
    private static final String PLASTER = "minecraft:packed_mud";
    private static final String PAPER = "minecraft:pearlescent_froglight";

    CityStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:stone_bricks";
        dado = 1;
        trim = axis(STRIPPED, 'y');
        frieze = 0;
        pilaster = axis(TIMBER, 'y');
        pilasterSpacing = 6;
        stairs = "minecraft:stone_brick_stairs";
        slab = "minecraft:stone_brick_slab";
        wallId = "minecraft:stone_brick_wall";
        beam = TIMBER;
        ceiling = "minecraft:spruce_planks";
        border = "minecraft:polished_andesite";
        inlay = "minecraft:chiseled_stone_bricks";
        inlay2 = "minecraft:mossy_stone_bricks";
        runner = "minecraft:polished_andesite";
        floorLight = "minecraft:ochre_froglight";
        fence = "minecraft:spruce_fence";
        grate = "minecraft:iron_bars";
        grateLight = "minecraft:ochre_froglight";
        railing = "minecraft:spruce_fence";
        banners = new String[]{"red", "yellow", "red", "brown"};
        carpets = new String[]{"red", "brown"};
        setPieces = new String[]{"stash", "market"};
        floorVariants = new String[]{"minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks",
                "minecraft:cobblestone", "minecraft:andesite", "minecraft:stone"};
        wallVariants = new String[]{"minecraft:cracked_deepslate_bricks", "minecraft:mossy_stone_bricks",
                "minecraft:cobbled_deepslate"};
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(7)) {
            case 0 -> c.set(x, y, z, "minecraft:barrel[facing=up,open=false]");
            case 1 -> c.set(x, y, z, "minecraft:decorated_pot[facing=" + w.in() + ",waterlogged=false]");
            case 2 -> c.set(x, y, z, candles(1 + r.nextInt(3)));
            case 3 -> c.set(x, y, z, "minecraft:skeleton_skull[rotation=" + r.nextInt(16) + "]");
            case 4 -> c.set(x, y, z, "minecraft:cobblestone_slab[type=bottom]");
            case 5 -> c.set(x, y, z, "minecraft:flower_pot");
            default -> c.set(x, y, z, "minecraft:spruce_trapdoor[facing=" + w.in() + ",half=bottom,open=false]");
        }
    }

    // ---------------------------------------------------------------- lamps

    /** A paper lantern (Payon style): a glowing paper block under a timber cap. */
    boolean paperLantern(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y + 1, z, "minecraft:spruce_trapdoor[facing=north,half=top,open=false]");
        piece.add(x, y, z, PAPER);
        return piece.commit();
    }

    @Override
    void beamLamp(int x, int y, int z) {
        if (!(chance(0.6) && c.isAir(x, y - 1, z) && paperLanternHanging(x, y, z))) {
            c.setOnAir(x, y, z, hangLamp);
        }
    }

    /** A paper lantern hanging on a short chain from the block above {@code y}. */
    boolean paperLanternHanging(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, chain);
        piece.add(x, y - 1, z, PAPER);
        return piece.commit();
    }

    @Override
    void sconce(Wall w, int a, int y) {
        Canvas.Piece piece = c.piece();
        piece.add(w.x(a, 1), y + 1, w.z(a, 1), fence);
        piece.add(w.x(a, 1), y, w.z(a, 1), hangLamp);
        if (!piece.commit()) {
            c.setOnAir(w.x(a, 1), y, w.z(a, 1), torch(w.in()));
        }
    }

    @Override
    boolean chandelier(int x, int z, int ceilingY, int low) {
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y >= low + 1; y--) {
            piece.add(x, y, z, chain);
        }
        piece.add(x, low, z, PAPER);
        return piece.commit();
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        if (bay.width() >= 5 && chance(0.75)) {
            houseFront(w, bay.mid());
            return;
        }
        int a = bay.mid();
        switch (r.nextInt(4)) {
            case 0 -> barrels(w.x(a, 1), w.z(a, 1), 1 + r.nextInt(2));
            case 1 -> mapTable(w, a);
            case 2 -> {
                c.setOnAir(w.x(a, 1), 4, w.z(a, 1), banner(pick(banners), w.in()));
                crate(w.x(a, 1), 1, w.z(a, 1));
            }
            default -> weaponRack(w, a);
        }
    }

    /**
     * The front of a buried house in a wall bay: plaster between timber posts, a door, a lit window, a small awning
     * and lanterns on brackets.
     */
    void houseFront(Wall w, int mid) {
        for (int a = mid - 2; a <= mid + 2; a++) {
            for (int y = 1; y <= 5; y++) {
                boolean post = Math.abs(a - mid) == 2;
                c.setOnSolid(w.x(a, 0), y, w.z(a, 0), post ? axis(TIMBER, 'y')
                        : y == 5 ? axis(STRIPPED, w.alongX() ? 'x' : 'z') : y == 1 ? "minecraft:cobblestone" : PLASTER);
            }
        }
        int door = mid - 1;
        int window = mid + 1;
        Canvas.Piece entrance = c.piece();
        entrance.add(w.x(door, 1), 1, w.z(door, 1), "minecraft:spruce_door[facing=" + w.in()
                + ",half=lower,hinge=left,open=false,powered=false]");
        entrance.add(w.x(door, 1), 2, w.z(door, 1), "minecraft:spruce_door[facing=" + w.in()
                + ",half=upper,hinge=left,open=false,powered=false]");
        entrance.commit();
        litWindow(w, window, 2, 3, "minecraft:yellow_stained_glass_pane");
        for (int a = mid - 1; a <= mid + 1; a++) {
            c.setOnAir(w.x(a, 1), 4, w.z(a, 1), "minecraft:spruce_slab[type=top]");
        }
        for (int a : new int[]{mid - 2, mid + 2}) {
            Canvas.Piece lamp = c.piece();
            lamp.add(w.x(a, 1), 4, w.z(a, 1), fence);
            lamp.add(w.x(a, 1), 3, w.z(a, 1), hangLamp);
            lamp.commit();
        }
        c.setOnAir(w.x(window, 1), 1, w.z(window, 1), "minecraft:potted_red_tulip");
    }

    /** A window in the wall with warm light behind it (a lit room of the buried city). */
    boolean litWindow(Wall w, int a, int y0, int y1, String glass) {
        return window(w, a, y0, y1, glass, "minecraft:ochre_froglight");
    }

    void barrels(int x, int z, int height) {
        Canvas.Piece piece = c.piece();
        for (int y = 1; y <= height; y++) {
            piece.add(x, y, z, y == height && height > 1 ? "minecraft:barrel[facing=north,open=false]"
                    : "minecraft:barrel[facing=up,open=false]");
        }
        piece.commit();
    }

    void crate(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:spruce_planks");
        piece.add(x, y + 1, z, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]");
        piece.commit();
    }

    /** A table against the wall with a map and a candle. */
    void mapTable(Wall w, int a) {
        Canvas.Piece piece = c.piece();
        piece.add(w.x(a, 1), 1, w.z(a, 1), "minecraft:cartography_table");
        piece.add(w.x(a, 1), 2, w.z(a, 1), candles(2));
        piece.commit();
        Canvas.Piece side = c.piece();
        side.add(w.x(a + 1, 1), 1, w.z(a + 1, 1), "minecraft:spruce_stairs[facing=" + w.in() + ",half=bottom]");
        side.commit();
    }

    /** Spears and a grindstone against the wall. */
    void weaponRack(Wall w, int a) {
        Canvas.Piece piece = c.piece();
        piece.add(w.x(a, 1), 1, w.z(a, 1), "minecraft:grindstone[face=floor,facing=" + w.in() + "]");
        piece.commit();
        for (int k : new int[]{-1, 1}) {
            Canvas.Piece spear = c.piece();
            spear.add(w.x(a + k, 1), 1, w.z(a + k, 1), "minecraft:lightning_rod[facing=up,powered=false,"
                    + "waterlogged=false]");
            spear.add(w.x(a + k, 1), 2, w.z(a + k, 1), "minecraft:lightning_rod[facing=up,powered=false,"
                    + "waterlogged=false]");
            spear.commit();
        }
    }

    // ---------------------------------------------------------------- rooms

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        switch (r.nextInt(3)) {
            case 0 -> {
                barrels(x, z, 2);
                barrels(x + sx, z, 1);
                crate(x, 1, z + sz);
            }
            case 1 -> rubble(x, z, sx, sz);
            default -> {
                Canvas.Piece hay = c.piece();
                hay.add(x, 1, z, "minecraft:hay_block[axis=y]");
                hay.add(x, 2, z, "minecraft:barrel[facing=up,open=false]");
                hay.commit();
                c.setOnAir(x + sx, 1, z, "minecraft:composter[level=0]");
            }
        }
        cobwebs(x, z, sx, sz, top);
    }

    /** Fallen stone and a broken beam in a corner. */
    void rubble(int x, int z, int sx, int sz) {
        c.setOnAir(x, 1, z, "minecraft:cracked_stone_bricks");
        c.setOnAir(x, 2, z, "minecraft:stone_brick_slab[type=bottom]");
        c.setOnAir(x + sx, 1, z, "minecraft:cobblestone_slab[type=bottom]");
        c.setOnAir(x, 1, z + sz, stairs(sx > 0 ? "west" : "east", false));
        c.setOnAir(x + sx, 1, z + sz, axis(STRIPPED, 'x'));
    }

    void cobwebs(int x, int z, int sx, int sz, int top) {
        c.setOnAir(x, top, z, "minecraft:cobweb");
        if (chance(0.5)) {
            c.setOnAir(x + sx, top, z, "minecraft:cobweb");
        }
    }

    @Override
    void roomExtras(Spaces.Room room, int ceilingY) {
        // the bandits' banners hang from the beams over the middle
        BlockBox b = room.box();
        BlockPoint center = b.center(1);
        if (beam != null && chance(0.7)) {
            c.setOnAir(center.x(), ceilingY - 2, center.z(), "minecraft:" + pick(banners) + "_banner[rotation="
                    + r.nextInt(16) + "]");
        }
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // a royal guard of the old city: plinth, armoured legs and body, a helmeted head, a spear
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_stone_bricks");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k == height - 2 ? "minecraft:polished_andesite" : "minecraft:andesite_wall");
        }
        piece.add(x, y + height - 1, z, "minecraft:skeleton_skull[rotation=" + facing.rotation() + "]");
        piece.commit();
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:stone_brick_wall");
        piece.add(x, y + 1, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        piece.commit();
    }

    // ---------------------------------------------------------------- corridors

    @Override
    void corridorBay(Link l, int a, int index) {
        switch (index % 4) {
            case 0 -> {
                grateNiche(l, a, 1);
                grateNiche(l, a, -1);
            }
            case 1 -> {
                wallLamp(l, a, 1);
                c.setOnAir(l.x(a, 1 - l.half()), 3, l.z(a, 1 - l.half()), banner(pick(banners), l.sideFacing(1)));
            }
            case 2 -> {
                statueNiche(l, a, 1);
                statueNiche(l, a, -1);
            }
            default -> {
                c.setOnAir(l.x(a, l.half() - 1), 3, l.z(a, l.half() - 1), banner(pick(banners), l.sideFacing(-1)));
                wallLamp(l, a, -1);
            }
        }
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the memorial lantern of the old king: a stepped plinth, a stone shaft, a great lantern, royal banners
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean centre = dx == 0 && dz == 0;
                piece.add(x + dx, y, z + dz, centre ? "minecraft:chiseled_stone_bricks"
                        : "minecraft:stone_brick_slab[type=bottom]");
            }
        }
        piece.add(x, y + 1, z, "minecraft:polished_andesite");
        piece.add(x, y + 2, z, "minecraft:andesite_wall");
        piece.add(x, y + 3, z, "minecraft:andesite_wall");
        piece.add(x, y + 4, z, "minecraft:chiseled_stone_bricks");
        piece.add(x, y + 5, z, PAPER);
        piece.add(x, y + 6, z, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 4, z + side.dz, banner(side.ordinal() % 2 == 0 ? "red" : "yellow",
                    side.name));
            c.setOnAir(x + side.dx, y + 1, z + side.dz, candles(1 + r.nextInt(3)));
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:ochre_froglight";
    }

    @Override
    String portalVeil() {
        return "minecraft:yellow_stained_glass_pane";
    }

    @Override
    void board(Wall w, int a) {
        super.board(w, a);
        for (int k : new int[]{-2, 2}) {
            Canvas.Piece lamp = c.piece();
            lamp.add(w.x(a + k, 1), 4, w.z(a + k, 1), fence);
            lamp.add(w.x(a + k, 1), 3, w.z(a + k, 1), hangLamp);
            lamp.commit();
        }
        barrels(w.x(a - 3, 1), w.z(a - 3, 1), 2);
        crate(w.x(a + 3, 1), 1, w.z(a + 3, 1));
    }

    @Override
    void hallExtras(Spaces.Hall hall) {
        BlockBox g = hall.box();
        // street lamps along the old walls' line
        for (Wall w : Geometry.walls(g)) {
            if (w.side() == hall.front()) {
                continue;
            }
            for (int a = w.min() + 2; a <= w.max() - 2; a += 6) {
                streetLamp(w.x(a, 2), 1, w.z(a, 2));
            }
        }
    }

    /** A street lamp: stone post, timber arm, lantern. */
    void streetLamp(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:stone_brick_wall");
        piece.add(x, y + 1, z, "minecraft:spruce_fence");
        piece.add(x, y + 2, z, "minecraft:spruce_fence");
        piece.add(x, y + 3, z, hangLamp.replace("hanging=true", "hanging=false"));
        piece.commit();
    }

    // ---------------------------------------------------------------- antechamber, gate, boss door

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the royal crest: a crowned shield of chiseled stone, two banners
        for (int y = y0; y <= Math.min(top, y0 + 3); y++) {
            for (int s = -1; s <= 1; s++) {
                c.setOnSolid(l.x(wallA, s), y, l.z(wallA, s), y == y0 + 3 && s != 0 ? "minecraft:stone_bricks"
                        : (s + y) % 2 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:polished_andesite");
            }
        }
        int inside = l.step() > 0 ? wallA - 1 : wallA + 1;
        for (int s : new int[]{-3, 3}) {
            c.setOnAir(l.x(inside, s), y0 + 2, l.z(inside, s), banner("red", l.forward().equals("east") ? "west"
                    : l.forward().equals("west") ? "east" : l.forward().equals("south") ? "north" : "south"));
        }
    }

    // ---------------------------------------------------------------- set pieces

    @Override
    void setPieceContent(Spaces.SetPiece piece) {
        if (piece.kind().equals("market")) {
            market(piece);
        } else {
            stash(piece);
        }
    }

    /** The bandits' treasure stash: crates and barrels along the walls, a heap of loot, the chief's map table. */
    private void stash(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockBox in = b.interior();
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == piece.doorSide()) {
                continue;
            }
            for (int a = w.min(); a <= w.max(); a++) {
                switch (Math.floorMod(a * 7 + w.side().ordinal(), 5)) {
                    case 0 -> barrels(w.x(a, 1), w.z(a, 1), 2);
                    case 1 -> crate(w.x(a, 1), 1, w.z(a, 1));
                    case 2 -> c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:chest[facing=" + w.in()
                            + ",type=single,waterlogged=false]");
                    case 3 -> barrels(w.x(a, 1), w.z(a, 1), 1);
                    default -> c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:hay_block[axis=y]");
                }
            }
        }
        BlockPoint center = b.center(1);
        // the loot heap: honeycomb "gold", coins (gold plates), a bell
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                c.setOnAir(center.x() + dx, 1, center.z() + dz, dx == 0 && dz == 0 ? "minecraft:honeycomb_block"
                        : "minecraft:light_weighted_pressure_plate[power=0]");
            }
        }
        c.setOnAir(center.x(), 2, center.z(), "minecraft:bell[attachment=floor,facing=north,powered=false]");
        for (Side side : Side.values()) {
            int x = center.x() + side.dx * 3;
            int z = center.z() + side.dz * 3;
            c.setOnAir(x, 1, z, carpet("yellow"));
        }
        chandelier(center.x(), center.z(), b.maxY(), b.maxY() - 3);
        for (int dx = -4; dx <= 4; dx += 8) {
            for (int dz = -4; dz <= 4; dz += 8) {
                if (in.contains(center.x() + dx, 1, center.z() + dz)) {
                    c.setOnAir(center.x() + dx, 1, center.z() + dz, "minecraft:cartography_table");
                    c.setOnAir(center.x() + dx, 2, center.z() + dz, candles(3));
                }
            }
        }
    }

    /** An old market street: shop fronts on the walls, a canal with a bridge down the middle, street lamps. */
    private void market(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        Side door = piece.doorSide();
        boolean canalAlongX = door == Side.NORTH || door == Side.SOUTH;
        BlockPoint center = b.center(0);
        // canal across the room (perpendicular to the way in), three wide and one deep, a bridge in the middle
        BlockBox canal = canalAlongX
                ? new BlockBox(b.minX() + 1, -2, center.z() - 2, b.maxX() - 1, 0, center.z() + 2)
                : new BlockBox(center.x() - 2, -2, b.minZ() + 1, center.x() + 2, 0, b.maxZ() - 1);
        if (c.claim(new BlockBox(canal.minX(), -2, canal.minZ(), canal.maxX(), -1, canal.maxZ()))) {
            for (int x = canal.minX(); x <= canal.maxX(); x++) {
                for (int z = canal.minZ(); z <= canal.maxZ(); z++) {
                    int lateral = canalAlongX ? z - center.z() : x - center.x();
                    int along = canalAlongX ? x - center.x() : z - center.z();
                    c.set(x, -2, z, "minecraft:stone_bricks");
                    if (Math.abs(lateral) == 2) {
                        c.set(x, -1, z, "minecraft:stone_bricks");
                        c.set(x, 0, z, "minecraft:polished_andesite");
                        continue;
                    }
                    c.set(x, -1, z, "minecraft:mossy_stone_bricks");
                    c.set(x, 0, z, Math.abs(along) <= 1 ? "minecraft:spruce_planks" : "minecraft:water[level=0]");
                    if (Math.abs(along) == 1) {
                        c.setOnAir(x, 1, z, fence);
                    }
                }
            }
            c.fluid(new BlockBox(canal.minX(), -1, canal.minZ(), canal.maxX(), 0, canal.maxZ()));
        }
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == door) {
                continue;
            }
            for (int a = w.min() + 3; a + 2 <= w.max() - 1; a += 6) {
                houseFront(w, a);
            }
        }
        for (int dx = -5; dx <= 5; dx += 10) {
            for (int dz = -5; dz <= 5; dz += 10) {
                streetLamp(center.x() + dx, 1, center.z() + dz);
            }
        }
        // a market stall: barrels and a striped awning
        int sx = center.x() + (canalAlongX ? 3 : -4);
        int sz = center.z() + (canalAlongX ? -4 : 3);
        Canvas.Piece stall = c.piece();
        stall.add(sx, 1, sz, "minecraft:barrel[facing=up,open=false]");
        stall.add(sx + 1, 1, sz, "minecraft:barrel[facing=up,open=false]");
        stall.add(sx, 2, sz, fence);
        stall.add(sx + 1, 2, sz, fence);
        stall.add(sx, 3, sz, "minecraft:red_wool");
        stall.add(sx + 1, 3, sz, "minecraft:white_wool");
        stall.commit();
    }

    // ---------------------------------------------------------------- arena

    @Override
    void arenaExtras(Spaces.Arena arena) {
        // timber buttresses in the corners and lit windows high up: a cavern under the old palace
        BlockBox b = arena.box();
        for (Wall w : Geometry.walls(b)) {
            for (int a : new int[]{w.min() + 1, w.max() - 1}) {
                for (int y = 1; y <= b.maxY() - 1; y++) {
                    c.setOnSolid(w.x(a, 0), y, w.z(a, 0), axis(TIMBER, 'y'));
                }
            }
        }
    }
}
