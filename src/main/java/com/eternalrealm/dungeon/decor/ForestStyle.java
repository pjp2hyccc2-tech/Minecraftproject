package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d2 古代樹の迷宮: the hollow inside of an ancient tree. Root trunks for pilasters, bark walls over a soil foot, root
 * beams across the ceilings with glow-berry vines and spore blossoms hanging from them, giant mushrooms and fallen logs
 * in the corners, moss, ferns and flowers along the walls, a druid grove and a mushroom grotto as set pieces.
 */
final class ForestStyle extends Style {
    private static final String ROOT = "minecraft:mangrove_log";
    private static final String BARK = "minecraft:dark_oak_wood";

    ForestStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:rooted_dirt";
        dado = 1;
        trim = "minecraft:moss_block";
        frieze = 0;
        pilaster = axis(ROOT, 'y');
        pilasterSpacing = 5;
        stairs = "minecraft:dark_oak_stairs";
        slab = "minecraft:dark_oak_slab";
        wallId = "minecraft:mossy_cobblestone_wall";
        beam = "minecraft:dark_oak_log";
        ceiling = BARK;
        border = "minecraft:moss_block";
        inlay = "minecraft:coarse_dirt";
        inlay2 = "minecraft:podzol";
        runner = "minecraft:coarse_dirt";
        floorLight = "minecraft:shroomlight";
        hangLamp = "minecraft:lantern[hanging=true]";
        torch = "minecraft:wall_torch";
        fence = "minecraft:dark_oak_fence";
        grate = "minecraft:dark_oak_fence";
        grateLight = "minecraft:shroomlight";
        railing = "minecraft:mossy_cobblestone_wall";
        banners = new String[]{"green", "lime", "green"};
        carpets = new String[]{"green", "lime"};
        setPieces = new String[]{"grove", "grotto"};
        floorVariants = new String[]{"minecraft:rooted_dirt", "minecraft:podzol", "minecraft:coarse_dirt",
                "minecraft:mossy_cobblestone", "minecraft:moss_block"};
        clutterChance = 0.45;
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(6)) {
            case 0 -> c.set(x, y, z, "minecraft:moss_carpet");
            case 1 -> c.set(x, y, z, "minecraft:fern");
            case 2 -> c.set(x, y, z, flower());
            case 3 -> c.set(x, y, z, chance(0.5) ? "minecraft:red_mushroom" : "minecraft:brown_mushroom");
            case 4 -> c.set(x, y, z, "minecraft:azalea");
            default -> c.set(x, y, z, "minecraft:grass");
        }
    }

    static String berries(boolean tip) {
        return tip ? "minecraft:cave_vines[age=25,berries=true]" : "minecraft:cave_vines_plant[berries=true]";
    }

    /** Glow berries hanging {@code length} blocks down from under (x, top + 1, z). */
    boolean vines(int x, int top, int z, int length) {
        Canvas.Piece piece = c.piece();
        for (int k = 0; k < length; k++) {
            piece.add(x, top - k, z, k == length - 1 ? berries(true) : "minecraft:cave_vines_plant[berries="
                    + (k % 2 == 1) + "]");
        }
        return piece.commit();
    }

    @Override
    void beamLamp(int x, int y, int z) {
        switch (r.nextInt(3)) {
            case 0 -> c.setOnAir(x, y, z, "minecraft:spore_blossom");
            case 1 -> vines(x, y, z, 2);
            default -> {
                Canvas.Piece piece = c.piece();
                piece.add(x, y, z, "minecraft:shroomlight");
                piece.add(x, y - 1, z, "minecraft:hanging_roots");
                piece.commit();
            }
        }
    }

    @Override
    void sconce(Wall w, int a, int y) {
        // a shroomlight knot grown into the root trunk, glow lichen around it
        c.setOnSolid(w.x(a, 0), y + 1, w.z(a, 0), "minecraft:shroomlight");
        c.setOnAir(w.x(a, 1), y, w.z(a, 1), "minecraft:glow_lichen[" + w.out() + "=true,waterlogged=false]");
    }

    @Override
    boolean chandelier(int x, int z, int ceilingY, int low) {
        // a hanging cluster: roots down from the ceiling, a shroomlight heart, glow berries around it
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y > low; y--) {
            piece.add(x, y, z, "minecraft:dark_oak_log[axis=y]");
        }
        piece.add(x, low, z, "minecraft:shroomlight");
        for (Side side : Side.values()) {
            piece.add(x + side.dx, low + 1, z + side.dz, "minecraft:azalea_leaves[distance=1,persistent=true,"
                    + "waterlogged=false]");
            piece.add(x + side.dx, low, z + side.dz, berries(true));
        }
        return piece.commit();
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bands(Wall w, int top) {
        super.bands(w, top);
        // bark between the root trunks, moss creeping up from the soil
        for (int a = w.min(); a <= w.max(); a++) {
            for (int y = 2; y <= top; y++) {
                if (c.state(w.x(a, 0), y, w.z(a, 0)).equals("#wall")) {
                    c.set(w.x(a, 0), y, w.z(a, 0), y == 2 && Math.floorMod(a * 13 + y, 3) == 0
                            ? "minecraft:moss_block" : BARK);
                }
            }
        }
    }

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        int a = bay.mid();
        switch (r.nextInt(5)) {
            case 0 -> fallenLog(w, bay.a0(), bay.a1());
            case 1 -> {
                fern(w.x(a, 1), w.z(a, 1));
                c.setOnAir(w.x(a + 1, 1), 1, w.z(a + 1, 1), "minecraft:flowering_azalea");
                c.setOnAir(w.x(a - 1, 1), 1, w.z(a - 1, 1), flower());
            }
            case 2 -> {
                c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:azalea");
                c.setOnAir(w.x(a, 1), 5, w.z(a, 1), banner(pick(banners), w.in()));
            }
            case 3 -> rootKnot(w, a);
            default -> {
                mushroom(w.x(a, 1), w.z(a, 1), 2, chance(0.5));
                c.setOnAir(w.x(a + 1, 1), 1, w.z(a + 1, 1), "minecraft:brown_mushroom");
            }
        }
        // glow berries down the wall from the ceiling
        if (chance(0.6)) {
            vines(w.x(bay.a0(), 1), bay.top(), w.z(bay.a0(), 1), 2 + r.nextInt(2));
        }
    }

    String flower() {
        return pick(new String[]{"minecraft:blue_orchid", "minecraft:allium", "minecraft:lily_of_the_valley",
                "minecraft:azure_bluet", "minecraft:oxeye_daisy"});
    }

    void fern(int x, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, 1, z, "minecraft:large_fern[half=lower]");
        piece.add(x, 2, z, "minecraft:large_fern[half=upper]");
        piece.commit();
    }

    /** A fallen log along the wall with moss and mushrooms on it. */
    void fallenLog(Wall w, int a0, int a1) {
        int from = Math.max(a0, (a0 + a1) / 2 - 1);
        int to = Math.min(a1, from + 2);
        Canvas.Piece piece = c.piece();
        for (int a = from; a <= to; a++) {
            piece.add(w.x(a, 1), 1, w.z(a, 1), axis("minecraft:oak_log", w.alongX() ? 'x' : 'z'));
        }
        if (piece.commit()) {
            c.setOnAir(w.x(from, 1), 2, w.z(from, 1), "minecraft:moss_carpet");
            c.setOnAir(w.x(to, 1), 2, w.z(to, 1), "minecraft:red_mushroom");
        }
    }

    /** Roots bulging out of the wall. */
    void rootKnot(Wall w, int a) {
        Canvas.Piece piece = c.piece();
        piece.add(w.x(a, 1), 1, w.z(a, 1), "minecraft:rooted_dirt");
        piece.add(w.x(a, 1), 2, w.z(a, 1), axis(ROOT, 'y'));
        piece.add(w.x(a, 1), 3, w.z(a, 1), stairs(w.out(), true));
        if (piece.commit()) {
            c.setOnAir(w.x(a + 1, 1), 1, w.z(a + 1, 1), "minecraft:moss_carpet");
            c.setOnAir(w.x(a - 1, 1), 1, w.z(a - 1, 1), "minecraft:fern");
        }
    }

    /** A mushroom: stem {@code stem} high, a red or brown cap; a 3x3 cap when there is room. */
    boolean mushroom(int x, int z, int stem, boolean red) {
        String cap = red ? "minecraft:red_mushroom_block" : "minecraft:brown_mushroom_block";
        Canvas.Piece big = c.piece();
        for (int y = 1; y <= stem; y++) {
            big.add(x, y, z, "minecraft:mushroom_stem");
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                big.add(x + dx, stem + 1, z + dz, cap);
            }
        }
        if (big.commit()) {
            return true;
        }
        Canvas.Piece small = c.piece();
        for (int y = 1; y <= stem; y++) {
            small.add(x, y, z, "minecraft:mushroom_stem");
        }
        small.add(x, stem + 1, z, cap);
        return small.commit();
    }

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        if (!mushroom(x + sx, z + sz, 3, chance(0.5))) {
            mushroom(x, z, 2, chance(0.5));
        }
        c.setOnAir(x, 1, z, "minecraft:moss_carpet");
        c.setOnAir(x, top, z, "minecraft:cobweb");
        c.setOnAir(x + sx, top, z, "minecraft:cobweb");
        c.setOnAir(x, top, z + sz, "minecraft:spore_blossom");
    }

    @Override
    void roomExtras(Spaces.Room room, int ceilingY) {
        BlockBox b = room.box();
        // roots hang everywhere under the ceiling, spore blossoms between them
        for (int x = b.minX() + 2; x <= b.maxX() - 2; x += 3) {
            for (int z = b.minZ() + 2; z <= b.maxZ() - 2; z += 3) {
                if (Math.floorMod(x * 7 + z * 3, 4) == 0) {
                    c.setOnAir(x, ceilingY - 1, z, "minecraft:hanging_roots");
                } else if (Math.floorMod(x * 5 + z, 7) == 0) {
                    c.setOnAir(x, ceilingY - 1, z, "minecraft:spore_blossom");
                }
            }
        }
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // a treant guardian: a mossy stump with a leafy crown and glowing eyes
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:rooted_dirt");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k == height - 2 ? "minecraft:oak_wood" : axis("minecraft:oak_log", 'y'));
        }
        piece.add(x, y + height - 1, z, "minecraft:flowering_azalea_leaves[distance=1,persistent=true,"
                + "waterlogged=false]");
        if (piece.commit()) {
            c.setOnAir(x + facing.dx, y + height - 2, z + facing.dz, "minecraft:glow_lichen[" + facing.opposite().name
                    + "=true,waterlogged=false]");
        }
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:mossy_cobblestone");
        piece.add(x, y + 1, z, "minecraft:shroomlight");
        piece.add(x, y + 2, z, "minecraft:red_mushroom");
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
                for (int sign = -1; sign <= 1; sign += 2) {
                    int s = sign * l.half();
                    c.setOnSolid(l.x(a, s), 3, l.z(a, s), "minecraft:shroomlight");
                }
            }
            default -> {
                statueNiche(l, a, 1);
                c.setOnAir(l.x(a, 1 - l.half()), 3, l.z(a, 1 - l.half()), banner(pick(banners), l.sideFacing(1)));
            }
        }
        int inner = l.half() - 1;
        c.setOnAir(l.x(a, inner), Geometry.OPENING_TOP, l.z(a, inner), "minecraft:hanging_roots");
    }

    @Override
    void corridor(Link l) {
        super.corridor(l);
        // moss creeping over the corridor walls
        for (int a = l.start(); a <= l.end(); a++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int s = sign * l.half();
                for (int y = 1; y <= Geometry.OPENING_TOP; y++) {
                    if (c.state(l.x(a, s), y, l.z(a, s)).equals("#wall")) {
                        c.set(l.x(a, s), y, l.z(a, s), y == 1 || Math.floorMod(a * 7 + y * 3 + sign, 5) == 0
                                ? "minecraft:moss_block" : BARK);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the elder sapling: a young tree in a ring of mossy stones, a glowing heart in its crown
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:rooted_dirt"
                        : "minecraft:mossy_stone_brick_slab[type=bottom]");
            }
        }
        for (int k = 1; k <= 3; k++) {
            piece.add(x, y + k, z, axis("minecraft:oak_log", 'y'));
        }
        piece.add(x, y + 4, z, "minecraft:shroomlight");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    piece.add(x + dx, y + 4, z + dz, "minecraft:flowering_azalea_leaves[distance=1,persistent=true,"
                            + "waterlogged=false]");
                }
            }
        }
        piece.add(x, y + 5, z, "minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 3, z + side.dz, berries(true));
            c.setOnAir(x + side.dx * 2, y, z + side.dz * 2, flower());
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:verdant_froglight";
    }

    @Override
    String portalVeil() {
        return "minecraft:lime_stained_glass_pane";
    }

    @Override
    void hallExtras(Spaces.Hall hall) {
        BlockBox g = hall.box();
        for (Wall w : Geometry.walls(g)) {
            for (int a = w.min() + 1; a <= w.max() - 1; a += 3) {
                if (chance(0.5)) {
                    vines(w.x(a, 1), g.maxY() - 1, w.z(a, 1), 2 + r.nextInt(3));
                }
            }
            if (w.side() != hall.front()) {
                fern(w.x(w.min() + 1, 1), w.z(w.min() + 1, 1));
                fern(w.x(w.max() - 1, 1), w.z(w.max() - 1, 1));
            }
        }
        for (int x = g.minX() + 2; x <= g.maxX() - 2; x += 4) {
            for (int z = g.minZ() + 2; z <= g.maxZ() - 2; z += 4) {
                c.setOnAir(x, g.maxY() - 1, z, "minecraft:spore_blossom");
            }
        }
    }

    // ---------------------------------------------------------------- gate

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the face of the elder tree over the gate: bark brows, shroomlight eyes, a mossy beard
        for (int y = y0; y <= Math.min(top, y0 + 4); y++) {
            for (int s = -2; s <= 2; s++) {
                String state = "minecraft:oak_wood";
                if (y == y0 + 2 && Math.abs(s) == 1) {
                    state = "minecraft:shroomlight";
                } else if (y == y0) {
                    state = "minecraft:moss_block";
                } else if (y == y0 + 3) {
                    state = "minecraft:dark_oak_wood";
                }
                c.setOnSolid(l.x(wallA, s), y, l.z(wallA, s), state);
            }
        }
    }

    @Override
    void bossDoor(Wall w, int[] o, int top) {
        super.bossDoor(w, o, top);
        for (int a = o[0] - 2; a <= o[1] + 2; a++) {
            vines(w.x(a, 1), top, w.z(a, 1), 2);
        }
    }

    // ---------------------------------------------------------------- set pieces

    @Override
    void setPieceContent(Spaces.SetPiece piece) {
        if (piece.kind().equals("grotto")) {
            grotto(piece);
        } else {
            grove(piece);
        }
    }

    /** A druid grove: a ring of mossy standing stones round a flowering altar and a pool, candles at its feet. */
    private void grove(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockPoint center = b.center(1);
        int ring = Math.min(b.sizeX(), b.sizeZ()) / 2 - 3;
        for (int k = 0; k < 8; k++) {
            double angle = Math.PI / 4 * k + Math.PI / 8;
            int x = center.x() + (int) Math.round(Math.cos(angle) * ring);
            int z = center.z() + (int) Math.round(Math.sin(angle) * ring);
            Canvas.Piece stone = c.piece();
            stone.add(x, 1, z, "minecraft:mossy_stone_bricks");
            stone.add(x, 2, z, "minecraft:mossy_cobblestone");
            stone.add(x, 3, z, k % 2 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:mossy_stone_brick_wall");
            if (stone.commit()) {
                c.setOnAir(x, 4, z, k % 2 == 0 ? "minecraft:moss_carpet" : candles(2));
            }
        }
        // the pool around the altar
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int d = Math.abs(dx) + Math.abs(dz);
                if (d == 2 || d == 3 && Math.abs(dx) != 3 && Math.abs(dz) != 3) {
                    if (c.claim(new BlockBox(center.x() + dx, -1, center.z() + dz, center.x() + dx, -1,
                            center.z() + dz))) {
                        c.set(center.x() + dx, -1, center.z() + dz, "minecraft:moss_block");
                        c.set(center.x() + dx, 0, center.z() + dz, "minecraft:water[level=0]");
                        c.fluid(new BlockBox(center.x() + dx, 0, center.z() + dz, center.x() + dx, 0,
                                center.z() + dz));
                    }
                }
            }
        }
        Canvas.Piece altar = c.piece();
        altar.add(center.x(), 1, center.z(), "minecraft:mossy_stone_bricks");
        altar.add(center.x(), 2, center.z(), "minecraft:flowering_azalea");
        altar.commit();
        for (Side side : Side.values()) {
            c.setOnAir(center.x() + side.dx, 1, center.z() + side.dz, candles(1 + r.nextInt(4)));
        }
        c.setOnAir(center.x(), b.maxY() - 1, center.z(), "minecraft:spore_blossom");
        for (Wall w : Geometry.walls(b)) {
            for (int a = w.min() + 1; a <= w.max() - 1; a += 2) {
                if (chance(0.6)) {
                    vines(w.x(a, 1), b.maxY() - 1, w.z(a, 1), 2 + r.nextInt(3));
                }
                if (w.side() != piece.doorSide() && chance(0.5)) {
                    c.setOnAir(w.x(a, 1), 1, w.z(a, 1), chance(0.5) ? flower() : "minecraft:fern");
                }
            }
        }
    }

    /** A mushroom grotto: giant mushrooms of both colours, shroomlight clusters, moss underfoot. */
    private void grotto(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockBox in = b.interior();
        BlockPoint center = b.center(1);
        int[][] spots = {{-4, -4}, {4, -3}, {-3, 4}, {4, 4}, {0, -5}, {-5, 0}};
        for (int[] spot : spots) {
            int x = center.x() + spot[0];
            int z = center.z() + spot[1];
            if (in.contains(x, 1, z)) {
                mushroom(x, z, 3 + r.nextInt(3), chance(0.5));
            }
        }
        for (int x = in.minX(); x <= in.maxX(); x++) {
            for (int z = in.minZ(); z <= in.maxZ(); z++) {
                if (Math.floorMod(x * 31 + z * 17, 5) == 0) {
                    c.setOnAir(x, 1, z, "minecraft:moss_carpet");
                } else if (Math.floorMod(x * 13 + z * 7, 11) == 0) {
                    c.setOnAir(x, 1, z, chance(0.5) ? "minecraft:red_mushroom" : "minecraft:brown_mushroom");
                }
                if (Math.floorMod(x * 3 + z * 11, 13) == 0) {
                    c.setOnAir(x, b.maxY() - 1, z, "minecraft:shroomlight");
                }
            }
        }
    }

    // ---------------------------------------------------------------- arena

    @Override
    void arenaExtras(Spaces.Arena arena) {
        // root buttresses rising up the arena walls, glow berries hanging from the rim
        BlockBox b = arena.box();
        for (Wall w : Geometry.walls(b)) {
            for (int a = w.min() + 3; a <= w.max() - 3; a += 6) {
                for (int y = 1; y <= b.maxY() - 1; y++) {
                    if (c.solid(w.x(a, 0), y, w.z(a, 0))) {
                        c.set(w.x(a, 0), y, w.z(a, 0), axis(ROOT, 'y'));
                    }
                }
            }
        }
    }
}
