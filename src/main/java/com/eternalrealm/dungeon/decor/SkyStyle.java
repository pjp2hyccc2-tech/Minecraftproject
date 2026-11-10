package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d7 天空の古代遺跡: white ruins of a sky people whose machines still run (Juperos and Odin's temple in spirit).
 * Quartz and calcite with patina-green copper bands, broken columns and fallen blocks, vines and moss on white stone,
 * openings that glow with sky light, walls of gears, pistons and observers, guardian statues with glowing eyes; an
 * observatory with an orrery and an engine hall as set pieces.
 */
final class SkyStyle extends Style {
    private static final String COPPER = "minecraft:oxidized_cut_copper";

    SkyStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:smooth_stone";
        dado = 1;
        trim = COPPER;
        frieze = 5;
        pilaster = "minecraft:quartz_pillar[axis=y]";
        pilasterSpacing = 5;
        stairs = "minecraft:quartz_stairs";
        slab = "minecraft:smooth_quartz_slab";
        wallId = "minecraft:diorite_wall";
        vault = true;
        ceiling = "minecraft:smooth_quartz";
        ceilingLight = "minecraft:sea_lantern";
        border = "minecraft:prismarine_bricks";
        inlay = "minecraft:dark_prismarine";
        inlay2 = "minecraft:prismarine";
        runner = "minecraft:polished_diorite";
        floorLight = "minecraft:sea_lantern";
        hangLamp = "minecraft:lantern[hanging=true]";
        torch = "minecraft:end_rod";
        fence = "minecraft:iron_bars";
        grate = "minecraft:light_blue_stained_glass_pane";
        grateLight = "minecraft:sea_lantern";
        railing = "minecraft:diorite_wall";
        banners = new String[]{"cyan", "white", "light_blue"};
        carpets = new String[]{"cyan", "white"};
        setPieces = new String[]{"observatory", "engine"};
        floorVariants = new String[]{"minecraft:calcite", "minecraft:moss_block", "minecraft:smooth_quartz",
                "minecraft:cracked_stone_bricks"};
        wallVariants = new String[]{"minecraft:calcite", "minecraft:mossy_stone_bricks", "minecraft:moss_block",
                "minecraft:chiseled_quartz_block"};
        clutterChance = 0.3;
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(5)) {
            case 0 -> c.set(x, y, z, "minecraft:moss_carpet");
            case 1 -> c.set(x, y, z, "minecraft:smooth_quartz_slab[type=bottom]");
            case 2 -> c.set(x, y, z, "minecraft:potted_azure_bluet");
            case 3 -> c.set(x, y, z, "minecraft:quartz_stairs[facing=" + w.along() + ",half=bottom]");
            default -> c.set(x, y, z, "minecraft:fern");
        }
    }

    /** Vines (berry-less glow-berry vines, which never grow) hanging {@code length} blocks from (x, top, z). */
    void vines(int x, int top, int z, int length) {
        Canvas.Piece piece = c.piece();
        for (int k = 0; k < length; k++) {
            piece.add(x, top - k, z, k == length - 1 ? "minecraft:cave_vines[age=25,berries=false]"
                    : "minecraft:cave_vines_plant[berries=false]");
        }
        piece.commit();
    }

    @Override
    void cornice(Wall w, int top) {
        super.cornice(w, top);
        for (int a = w.min() + 1; a <= w.max() - 1; a++) {
            if (Math.floorMod(a * 7 + w.side().ordinal(), 5) == 0) {
                vines(w.x(a, 1), top - 1, w.z(a, 1), 1 + Math.floorMod(a, 3));
            }
        }
    }

    @Override
    boolean chandelier(int x, int z, int ceilingY, int low) {
        // a floating orb of the ancients: a sea lantern held by light rods
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y >= low + 2; y--) {
            piece.add(x, y, z, chain);
        }
        piece.add(x, low + 1, z, "minecraft:sea_lantern");
        piece.add(x, low, z, "minecraft:end_rod[facing=down]");
        for (Side side : Side.values()) {
            piece.add(x + side.dx, low + 1, z + side.dz, "minecraft:end_rod[facing=" + side.name + "]");
        }
        return piece.commit();
    }

    @Override
    void sconce(Wall w, int a, int y) {
        c.setOnAir(w.x(a, 1), y, w.z(a, 1), "minecraft:end_rod[facing=" + w.in() + "]");
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        int a = bay.mid();
        switch (r.nextInt(4)) {
            case 0 -> {
                if (!window(w, a, 2, 4, "minecraft:white_stained_glass", "minecraft:sea_lantern")) {
                    machine(w, a);
                }
                brokenColumn(w.x(a, 1), w.z(a, 1), 1 + r.nextInt(2));
            }
            case 1 -> machine(w, a);
            case 2 -> {
                c.setOnAir(w.x(a, 1), 5, w.z(a, 1), banner(pick(banners), w.in()));
                c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:flowering_azalea");
            }
            default -> brokenColumn(w.x(a, 1), w.z(a, 1), 2);
        }
    }

    /** A wall of old machinery: observers, lodestones, lamps, copper, a gear (grindstone) on it (nothing powered). */
    void machine(Wall w, int a) {
        for (int k = -1; k <= 1; k++) {
            for (int y = 2; y <= 4; y++) {
                if (!c.solid(w.x(a + k, 0), y, w.z(a + k, 0))) {
                    continue;
                }
                int pick = Math.floorMod((a + k) * 3 + y * 5, 6);
                String state = switch (pick) {
                    case 0 -> "minecraft:observer[facing=" + w.out() + ",powered=false]";
                    case 1 -> "minecraft:lodestone";
                    case 2 -> "minecraft:redstone_lamp[lit=false]";
                    case 3 -> "minecraft:dropper[facing=" + w.in() + ",triggered=false]";
                    default -> COPPER;
                };
                c.set(w.x(a + k, 0), y, w.z(a + k, 0), state);
            }
        }
        c.setOnAir(w.x(a, 1), 3, w.z(a, 1), "minecraft:grindstone[face=wall,facing=" + w.in() + "]");
        c.setOnAir(w.x(a - 1, 1), 2, w.z(a - 1, 1), "minecraft:lightning_rod[facing=" + w.in()
                + ",powered=false,waterlogged=false]");
    }

    /** A column broken off at {@code height}, its capital lying at its foot. */
    void brokenColumn(int x, int z, int height) {
        Canvas.Piece piece = c.piece();
        for (int y = 1; y <= height; y++) {
            piece.add(x, y, z, "minecraft:quartz_pillar[axis=y]");
        }
        piece.add(x, height + 1, z, "minecraft:smooth_quartz_slab[type=bottom]");
        piece.commit();
    }

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        brokenColumn(x, z, 3);
        c.setOnAir(x + sx, 1, z, "minecraft:quartz_stairs[facing=" + (sx > 0 ? "east" : "west") + ",half=bottom]");
        c.setOnAir(x, 1, z + sz, "minecraft:chiseled_quartz_block");
        c.setOnAir(x + sx, 1, z + sz, "minecraft:moss_carpet");
        vines(x, top, z, 2);
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // an ancient guardian: prismarine body, copper shoulders, a glowing eye
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:dark_prismarine");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k == height - 2 ? COPPER : "minecraft:prismarine_wall");
        }
        piece.add(x, y + height - 1, z, "minecraft:sea_lantern");
        if (piece.commit()) {
            c.setOnAir(x, y + height, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        }
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_quartz_block");
        piece.add(x, y + 1, z, "minecraft:sea_lantern");
        piece.add(x, y + 2, z, "minecraft:end_rod[facing=up]");
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
                for (int sign = -1; sign <= 1; sign += 2) {
                    int s = sign * l.half();
                    c.setOnSolid(l.x(a, s), 2, l.z(a, s), "minecraft:observer[facing=" + l.sideFacing(sign)
                            + ",powered=false]");
                    c.setOnSolid(l.x(a, s), 3, l.z(a, s), COPPER);
                    wallLamp(l, a, sign);
                }
            }
        }
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the beacon of the ancients: a quartz obelisk with a sea-lantern core and a copper crown
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:chiseled_quartz_block"
                        : "minecraft:smooth_quartz_slab[type=bottom]");
            }
        }
        piece.add(x, y + 1, z, "minecraft:quartz_pillar[axis=y]");
        piece.add(x, y + 2, z, "minecraft:quartz_pillar[axis=y]");
        piece.add(x, y + 3, z, "minecraft:sea_lantern");
        piece.add(x, y + 4, z, COPPER);
        piece.add(x, y + 5, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 3, z + side.dz, "minecraft:end_rod[facing=" + side.name + "]");
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:sea_lantern";
    }

    @Override
    String portalVeil() {
        return "minecraft:cyan_stained_glass_pane";
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
                    window(w, a, 2, 5, "minecraft:white_stained_glass", "minecraft:sea_lantern");
                }
            }
            brokenColumn(w.x(w.min() + 1, 1), w.z(w.min() + 1, 1), 2);
        }
    }

    // ---------------------------------------------------------------- gate

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the eye of the guardian machine: a sea-lantern eye in a copper ring
        for (int y = y0; y <= Math.min(top, y0 + 4); y++) {
            for (int s = -2; s <= 2; s++) {
                int ds = Math.abs(s);
                int dy = Math.abs(y - (y0 + 2));
                String state = ds == 0 && dy == 0 ? "minecraft:sea_lantern" : ds + dy <= 1 ? "minecraft:dark_prismarine"
                        : ds + dy == 2 ? COPPER : null;
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
        if (piece.kind().equals("engine")) {
            for (Wall w : Geometry.walls(b)) {
                if (w.side() == piece.doorSide()) {
                    continue;
                }
                for (int a = w.min() + 1; a <= w.max() - 1; a += 3) {
                    machine(w, a);
                }
            }
            for (int dx = -2; dx <= 2; dx += 4) {
                for (int dz = -2; dz <= 2; dz += 4) {
                    Canvas.Piece pylon = c.piece();
                    for (int y = 1; y <= 4; y++) {
                        pylon.add(center.x() + dx, y, center.z() + dz, y == 4 ? "minecraft:sea_lantern" : COPPER);
                    }
                    pylon.add(center.x() + dx, 5, center.z() + dz, "minecraft:lightning_rod[facing=up,powered=false,"
                            + "waterlogged=false]");
                    pylon.commit();
                }
            }
            return;
        }
        // the observatory: an orrery over a star map, a telescope at the wall
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                if (d <= 3) {
                    c.setOnSolid(center.x() + dx, 0, center.z() + dz, d == 3 ? COPPER : (dx + dz) % 3 == 0
                            ? "minecraft:sea_lantern" : "minecraft:dark_prismarine");
                }
            }
        }
        Canvas.Piece orrery = c.piece();
        orrery.add(center.x(), 1, center.z(), "minecraft:chiseled_quartz_block");
        orrery.add(center.x(), 2, center.z(), "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
        orrery.add(center.x(), 3, center.z(), "minecraft:sea_lantern");
        for (Side side : Side.values()) {
            orrery.add(center.x() + side.dx, 3, center.z() + side.dz, "minecraft:end_rod[facing=" + side.name + "]");
            orrery.add(center.x() + side.dx * 2, 3, center.z() + side.dz * 2, side.ordinal() % 2 == 0
                    ? "minecraft:light_blue_stained_glass" : "minecraft:orange_stained_glass");
        }
        orrery.commit();
        Wall back = new Wall(b, piece.doorSide().opposite());
        Canvas.Piece scope = c.piece();
        scope.add(back.x(back.mid(), 2), 1, back.z(back.mid(), 2), "minecraft:birch_fence");
        scope.add(back.x(back.mid(), 2), 2, back.z(back.mid(), 2), "minecraft:lightning_rod[facing=" + back.out()
                + ",powered=false,waterlogged=false]");
        scope.commit();
        window(back, back.mid(), 3, 6, "minecraft:light_blue_stained_glass", "minecraft:sea_lantern");
    }

    @Override
    String arenaWindow() {
        return "minecraft:white_stained_glass";
    }

    @Override
    String arenaFire() {
        return "minecraft:sea_lantern";
    }
}
