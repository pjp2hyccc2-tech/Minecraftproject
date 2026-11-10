package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d3 砂塵の神殿: a sea temple swallowed by the desert (Pyramid and Sphinx in spirit). Sandstone walls carved with
 * hieroglyph panels and a frieze of waves, coved ceilings painted blue with golden stars, sarcophagi and canopic jars,
 * braziers, obelisks and pharaoh statues, sand drifting into the corners, a treasure vault and a tomb as set pieces.
 */
final class TempleStyle extends Style {
    private static final String WAVES = "minecraft:light_blue_glazed_terracotta";
    private static final String GOLD = "minecraft:honeycomb_block";
    private static final String MASK = "minecraft:yellow_glazed_terracotta";

    TempleStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:smooth_red_sandstone";
        dado = 1;
        trim = "minecraft:cut_sandstone";
        frieze = 5;
        pilaster = "minecraft:smooth_sandstone";
        pilasterSpacing = 5;
        stairs = "minecraft:sandstone_stairs";
        slab = "minecraft:smooth_sandstone_slab";
        wallId = "minecraft:sandstone_wall";
        vault = true;
        ceiling = "minecraft:smooth_sandstone";
        ceilingLight = "minecraft:ochre_froglight";
        border = "minecraft:cut_red_sandstone";
        inlay = "minecraft:blue_terracotta";
        inlay2 = "minecraft:cut_sandstone";
        runner = "minecraft:orange_terracotta";
        floorLight = "minecraft:ochre_froglight";
        hangLamp = "minecraft:lantern[hanging=true]";
        fence = "minecraft:birch_fence";
        grate = "minecraft:iron_bars";
        grateLight = "minecraft:sea_lantern";
        railing = "minecraft:sandstone_wall";
        banners = new String[]{"blue", "yellow", "blue"};
        carpets = new String[]{"blue", "orange"};
        setPieces = new String[]{"treasury", "tomb"};
        floorVariants = new String[]{"minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sand",
                "minecraft:smooth_sandstone"};
        wallVariants = new String[]{"minecraft:sandstone", "minecraft:chiseled_sandstone"};
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(6)) {
            case 0 -> jar(x, y, z);
            case 1 -> c.set(x, y, z, "minecraft:sand");
            case 2 -> c.set(x, y, z, "minecraft:skeleton_skull[rotation=" + r.nextInt(16) + "]");
            case 3 -> c.set(x, y, z, "minecraft:dead_bush");
            case 4 -> c.set(x, y, z, "minecraft:bone_block[axis=" + (w.alongX() ? "x" : "z") + "]");
            default -> c.set(x, y, z, "minecraft:sandstone_slab[type=bottom]");
        }
    }

    /** The vault's top: a night sky painted blue with golden stars, framed in cut sandstone. */
    @Override
    String ceilingAt(int lx, int lz, int d) {
        if (d <= 3) {
            return d == 3 ? "minecraft:cut_sandstone" : ceiling;
        }
        int k = Math.floorMod(lx * 7 + lz * 13 + lx * lz, 13);
        return k == 0 ? "minecraft:ochre_froglight" : k == 6 || k == 9 ? "minecraft:yellow_terracotta"
                : "minecraft:blue_terracotta";
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bands(Wall w, int top) {
        super.bands(w, top);
        int y0 = w.box().minY();
        for (int a = w.min() - 1; a <= w.max() + 1; a++) {
            // the frieze of waves, chiseled glyphs between
            if (c.solid(w.x(a, 0), y0 + frieze, w.z(a, 0))) {
                c.set(w.x(a, 0), y0 + frieze, w.z(a, 0), Math.floorMod(a, 3) == 0 ? "minecraft:chiseled_sandstone"
                        : WAVES + "[facing=" + (Math.floorMod(a, 2) == 0 ? w.in() : w.out()) + "]");
            }
            if (c.solid(w.x(a, 0), top, w.z(a, 0))) {
                c.set(w.x(a, 0), top, w.z(a, 0), "minecraft:cut_sandstone");
            }
        }
    }

    @Override
    void pilaster(Wall w, int a, int top, boolean lamp) {
        super.pilaster(w, a, top, false);
        c.setOnSolid(w.x(a, 0), top - 1, w.z(a, 0), "minecraft:chiseled_sandstone");
        c.setOnSolid(w.x(a, 0), 1, w.z(a, 0), "minecraft:chiseled_red_sandstone");
        if (lamp) {
            sconce(w, a, 3);
        }
    }

    @Override
    void sconce(Wall w, int a, int y) {
        // a sea-lantern slot in the column behind a grille
        if (claimOutside(w, a, y - 1, y + 1)) {
            c.set(w.x(a, -1), y - 1, w.z(a, -1), "#wall");
            c.set(w.x(a, -1), y, w.z(a, -1), "minecraft:sea_lantern");
            c.set(w.x(a, -1), y + 1, w.z(a, -1), "#wall");
            c.set(w.x(a, 0), y, w.z(a, 0), "minecraft:orange_stained_glass_pane");
        } else {
            c.setOnAir(w.x(a, 1), y, w.z(a, 1), torch(w.in()));
        }
    }

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        int a = bay.mid();
        hieroglyphs(w, bay.a0(), bay.a1());
        switch (r.nextInt(4)) {
            case 0 -> standingCoffin(w, a);
            case 1 -> {
                jar(w.x(a, 1), 1, w.z(a, 1));
                if (bay.width() >= 3) {
                    jar(w.x(a - 1, 1), 1, w.z(a - 1, 1));
                    jar(w.x(a + 1, 1), 1, w.z(a + 1, 1));
                }
            }
            case 2 -> brazier(w.x(a, 1), 1, w.z(a, 1));
            default -> c.setOnAir(w.x(a, 1), 5, w.z(a, 1), banner(pick(banners), w.in()));
        }
    }

    /** Carved panels: chiseled and cut sandstone in a pattern, picked out with a little blue. */
    void hieroglyphs(Wall w, int a0, int a1) {
        for (int a = a0; a <= a1; a++) {
            for (int y = 2; y <= 4; y++) {
                if (!c.solid(w.x(a, 0), y, w.z(a, 0))) {
                    continue;
                }
                int k = Math.floorMod(a * 3 + y * 5, 7);
                c.set(w.x(a, 0), y, w.z(a, 0), k == 0 ? "minecraft:chiseled_sandstone" : k == 3
                        ? "minecraft:chiseled_red_sandstone" : k == 5 && y == 3 ? "minecraft:blue_terracotta"
                        : "minecraft:cut_sandstone");
            }
        }
    }

    /** A mummy's coffin standing in the wall: painted case, a golden mask. */
    void standingCoffin(Wall w, int a) {
        if (!c.solid(w.x(a, 0), 1, w.z(a, 0))) {
            return;
        }
        c.set(w.x(a, 0), 1, w.z(a, 0), "minecraft:orange_terracotta");
        c.set(w.x(a, 0), 2, w.z(a, 0), "minecraft:blue_terracotta");
        c.set(w.x(a, 0), 3, w.z(a, 0), MASK + "[facing=" + w.in() + "]");
        c.set(w.x(a, 0), 4, w.z(a, 0), "minecraft:blue_terracotta");
        c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:sandstone_stairs[facing=" + w.out() + ",half=bottom]");
    }

    /** A canopic jar on a small stand. */
    void jar(int x, int y, int z) {
        c.setOnAir(x, y, z, "minecraft:decorated_pot[facing=north,waterlogged=false]");
    }

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        // sand drifted into the corner, a skull and a dead bush on it
        Canvas.Piece drift = c.piece();
        drift.add(x, 1, z, "minecraft:sand");
        drift.add(x, 2, z, "minecraft:sand");
        drift.add(x + sx, 1, z, "minecraft:sand");
        drift.add(x, 1, z + sz, "minecraft:sand");
        if (drift.commit()) {
            c.setOnAir(x, 3, z, "minecraft:dead_bush");
            c.setOnAir(x + sx, 2, z, "minecraft:skeleton_skull[rotation=" + r.nextInt(16) + "]");
            c.setOnAir(x + sx, 1, z + sz, "minecraft:sandstone_slab[type=bottom]");
        }
        c.setOnAir(x, top, z, "minecraft:cobweb");
    }

    @Override
    void pillarDressing(int x, int z, int top) {
        c.set(x, 1, z, "minecraft:chiseled_red_sandstone");
        c.set(x, top, z, "minecraft:chiseled_sandstone");
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // a pharaoh: sandstone feet, a pillar of a body, a golden mask under a striped headdress
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_sandstone");
        for (int k = 1; k < height - 2; k++) {
            piece.add(x, y + k, z, k == height - 3 ? "minecraft:cut_sandstone" : "minecraft:sandstone_wall");
        }
        piece.add(x, y + height - 2, z, MASK + "[facing=" + facing.name + "]");
        piece.add(x, y + height - 1, z, "minecraft:blue_terracotta");
        piece.commit();
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_sandstone");
        piece.add(x, y + 1, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        piece.commit();
    }

    /** An obelisk with a golden tip. */
    void obelisk(int x, int y, int z, int height) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_sandstone");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k % 2 == 0 ? "minecraft:cut_sandstone" : "minecraft:smooth_sandstone");
        }
        piece.add(x, y + height - 1, z, GOLD);
        piece.commit();
    }

    /** A sarcophagus lying along {@code axis} ('x' or 'z') from (x, z), three long. */
    void sarcophagus(int x, int y, int z, char axis, int dir) {
        Canvas.Piece piece = c.piece();
        for (int k = 0; k < 3; k++) {
            int px = axis == 'x' ? x + k * dir : x;
            int pz = axis == 'z' ? z + k * dir : z;
            piece.add(px, y, pz, k == 0 ? "minecraft:chiseled_sandstone" : "minecraft:orange_terracotta");
            piece.add(px, y + 1, pz, k == 0 ? MASK + "[facing=north]" : "minecraft:smooth_sandstone_slab[type=bottom]");
        }
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
                    for (int y = 2; y <= 3; y++) {
                        c.setOnSolid(l.x(a, sign * l.half()), y, l.z(a, sign * l.half()),
                                y == 2 ? "minecraft:chiseled_sandstone" : "minecraft:chiseled_red_sandstone");
                    }
                }
            }
        }
    }

    @Override
    void rib(Link l, int a) {
        super.rib(l, a);
        int y = Geometry.OPENING_TOP + 1;
        c.setOnSolid(l.x(a, 0), y, l.z(a, 0), "minecraft:chiseled_sandstone");
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the sun obelisk: a stepped base, a tall shaft, a golden capstone, braziers at its corners
        Canvas.Piece base = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                base.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:chiseled_sandstone"
                        : "minecraft:smooth_sandstone_slab[type=bottom]");
            }
        }
        base.force();
        obelisk(x, y + 1, z, 5);
        c.setOnAir(x + facing.dx, y + 3, z + facing.dz, "minecraft:ochre_froglight");
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                c.setOnAir(x + dx, y + 1, z + dz, "minecraft:campfire[facing=north,lit=true,signal_fire=false,"
                        + "waterlogged=false]");
            }
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
            if (w.side() == hall.front()) {
                continue;
            }
            for (int a = w.min() + 1; a <= w.max() - 1; a += 4) {
                hieroglyphs(w, a, a + 1);
            }
            obelisk(w.x(w.min() + 1, 1), 1, w.z(w.min() + 1, 1), 4);
            obelisk(w.x(w.max() - 1, 1), 1, w.z(w.max() - 1, 1), 4);
        }
    }

    // ---------------------------------------------------------------- gate, boss door

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the winged sun: a golden disc with a glowing heart, wings of sandstone to both sides
        for (int y = y0; y <= Math.min(top, y0 + 3); y++) {
            for (int s = -4; s <= 4; s++) {
                int ds = Math.abs(s);
                String state = null;
                if (ds <= 1 && y >= y0 + 1 && y <= y0 + 2) {
                    state = ds == 0 && y == y0 + 1 ? "minecraft:ochre_froglight" : GOLD;
                } else if (ds == 0 && (y == y0 || y == y0 + 3)) {
                    state = GOLD;
                } else if (ds >= 2 && y == y0 + 2) {
                    state = "minecraft:cut_sandstone";
                } else if (ds >= 2 && ds <= 3 && y == y0 + 1) {
                    state = "minecraft:chiseled_sandstone";
                }
                if (state != null) {
                    c.setOnSolid(l.x(wallA, s), y, l.z(wallA, s), state);
                }
            }
        }
    }

    // ---------------------------------------------------------------- set pieces

    @Override
    void setPieceContent(Spaces.SetPiece piece) {
        if (piece.kind().equals("tomb")) {
            tomb(piece);
        } else {
            treasury(piece);
        }
    }

    /** The pharaoh's treasury: heaps of gold, jars on shelves, chests, a golden idol on a dais. */
    private void treasury(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockPoint center = b.center(1);
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == piece.doorSide()) {
                continue;
            }
            for (int a = w.min(); a <= w.max(); a++) {
                switch (Math.floorMod(a, 4)) {
                    case 0 -> jar(w.x(a, 1), 1, w.z(a, 1));
                    case 1 -> c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:chest[facing=" + w.in()
                            + ",type=single,waterlogged=false]");
                    case 2 -> goldPile(w.x(a, 1), w.z(a, 1));
                    default -> {
                        c.setOnAir(w.x(a, 1), 1, w.z(a, 1), GOLD);
                        c.setOnAir(w.x(a, 1), 2, w.z(a, 1), "minecraft:light_weighted_pressure_plate[power=0]");
                    }
                }
            }
        }
        // the dais with the idol
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                c.setOnAir(center.x() + dx, 1, center.z() + dz, d == 2 ? "minecraft:cut_sandstone_slab[type=bottom]"
                        : "minecraft:cut_sandstone");
            }
        }
        Canvas.Piece idol = c.piece();
        idol.add(center.x(), 2, center.z(), GOLD);
        idol.add(center.x(), 3, center.z(), MASK + "[facing=" + piece.doorSide().name + "]");
        idol.commit();
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                goldPile(center.x() + dx * 4, center.z() + dz * 4);
                c.setOnAir(center.x() + dx, 2, center.z() + dz, "minecraft:light_weighted_pressure_plate[power=0]");
            }
        }
    }

    void goldPile(int x, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, 1, z, GOLD);
        piece.add(x, 2, z, "minecraft:light_weighted_pressure_plate[power=0]");
        piece.commit();
    }

    /** The embalming tomb: rows of sarcophagi, standing coffins in the walls, braziers. */
    private void tomb(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockBox in = b.interior();
        boolean alongX = piece.doorSide() == Side.EAST || piece.doorSide() == Side.WEST;
        BlockPoint center = b.center(1);
        for (int k = -1; k <= 1; k += 2) {
            for (int step = -4; step <= 4; step += 4) {
                int x = alongX ? center.x() + step : center.x() + k * 3;
                int z = alongX ? center.z() + k * 3 : center.z() + step;
                if (in.contains(x, 1, z)) {
                    sarcophagus(alongX ? x : x - 1, 1, alongX ? z - 1 : z, alongX ? 'z' : 'x', 1);
                }
            }
        }
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == piece.doorSide()) {
                continue;
            }
            for (int a = w.min() + 1; a <= w.max() - 1; a += 2) {
                standingCoffin(w, a);
            }
        }
        brazier(center.x(), 1, center.z());
    }

    // ---------------------------------------------------------------- arena

    @Override
    void arenaExtras(Spaces.Arena arena) {
        BlockBox b = arena.box();
        for (Wall w : Geometry.walls(b)) {
            for (int a = w.min() - 1; a <= w.max() + 1; a++) {
                int y = 9;
                if (c.solid(w.x(a, 0), y, w.z(a, 0))) {
                    c.set(w.x(a, 0), y, w.z(a, 0), WAVES + "[facing=" + (Math.floorMod(a, 2) == 0 ? w.in() : w.out())
                            + "]");
                }
            }
        }
    }
}
