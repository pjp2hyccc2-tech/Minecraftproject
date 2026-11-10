package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d5 氷結の城: the ice queen's palace. White quartz and blue ice, coved and coffered ceilings, tall stained-glass
 * windows glowing pale blue, crystal chandeliers, light-blue and white banners, ice-crystal clusters and snow drifts,
 * ice-knight statues; a throne room and a frozen garden as set pieces.
 */
final class CastleStyle extends Style {
    CastleStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:polished_diorite";
        dado = 1;
        trim = "minecraft:chiseled_quartz_block";
        frieze = 5;
        pilaster = "minecraft:quartz_pillar[axis=y]";
        pilasterSpacing = 6;
        stairs = "minecraft:quartz_stairs";
        slab = "minecraft:quartz_slab";
        wallId = "minecraft:diorite_wall";
        vault = true;
        ceiling = "minecraft:quartz_bricks";
        ceilingLight = "minecraft:sea_lantern";
        border = "minecraft:blue_ice";
        inlay = "minecraft:packed_ice";
        inlay2 = "minecraft:calcite";
        runner = "minecraft:blue_wool";
        floorLight = "minecraft:sea_lantern";
        hangLamp = "minecraft:lantern[hanging=true]";
        torch = "minecraft:soul_wall_torch";
        fence = "minecraft:iron_bars";
        grate = "minecraft:light_blue_stained_glass_pane";
        grateLight = "minecraft:sea_lantern";
        railing = "minecraft:diorite_wall";
        banners = new String[]{"light_blue", "white", "light_blue", "blue"};
        carpets = new String[]{"light_blue", "white"};
        setPieces = new String[]{"throne", "garden"};
        floorVariants = new String[]{"minecraft:calcite", "minecraft:packed_ice", "minecraft:snow_block"};
        wallVariants = new String[]{"minecraft:packed_ice", "minecraft:chiseled_quartz_block"};
        clutterChance = 0.2;
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(4)) {
            case 0 -> c.set(x, y, z, "minecraft:snow_block");
            case 1 -> c.set(x, y, z, "minecraft:white_carpet");
            case 2 -> c.set(x, y, z, "minecraft:potted_azure_bluet");
            default -> c.set(x, y, z, "minecraft:white_candle[candles=3,lit=true]");
        }
    }

    /** The vault's top: quartz coffers framed in blue ice, a sea lantern in every other coffer. */
    @Override
    String ceilingAt(int lx, int lz, int d) {
        if (d < 4) {
            return ceiling;
        }
        if (lx % 4 == 0 || lz % 4 == 0) {
            return "minecraft:blue_ice";
        }
        return lx % 4 == 2 && lz % 4 == 2 ? "minecraft:sea_lantern" : "minecraft:smooth_quartz";
    }

    @Override
    boolean chandelier(int x, int z, int ceilingY, int low) {
        // a crystal chandelier: a sea lantern heart, glass rods pointing out, an icicle under it
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y >= low + 2; y--) {
            piece.add(x, y, z, chain);
        }
        piece.add(x, low + 1, z, "minecraft:sea_lantern");
        piece.add(x, low, z, "minecraft:end_rod[facing=down]");
        for (Side side : Side.values()) {
            piece.add(x + side.dx, low + 1, z + side.dz, "minecraft:end_rod[facing=" + side.name + "]");
            piece.add(x + side.dx, low + 2, z + side.dz, "minecraft:white_candle[candles=4,lit=true]");
        }
        return piece.commit();
    }

    @Override
    void sconce(Wall w, int a, int y) {
        // a candle shelf on the column
        Canvas.Piece piece = c.piece();
        piece.add(w.x(a, 1), y, w.z(a, 1), "minecraft:quartz_slab[type=top]");
        piece.add(w.x(a, 1), y + 1, w.z(a, 1), "minecraft:white_candle[candles=3,lit=true]");
        piece.commit();
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        int a = bay.mid();
        if (window(w, a, 2, 5, "minecraft:light_blue_stained_glass", "minecraft:sea_lantern")) {
            c.setOnSolid(w.x(a, 0), 6, w.z(a, 0), "minecraft:chiseled_quartz_block");
            if (bay.width() >= 3) {
                c.setOnAir(w.x(a - 1, 1), 5, w.z(a - 1, 1), banner(pick(banners), w.in()));
                c.setOnAir(w.x(a + 1, 1), 5, w.z(a + 1, 1), banner(pick(banners), w.in()));
            }
        } else {
            c.setOnAir(w.x(a, 1), 5, w.z(a, 1), banner(pick(banners), w.in()));
        }
        if (chance(0.5)) {
            crystal(w.x(a, 1), 1, w.z(a, 1), 2);
        } else {
            Canvas.Piece table = c.piece();
            table.add(w.x(a, 1), 1, w.z(a, 1), "minecraft:quartz_slab[type=top]");
            table.add(w.x(a, 1), 2, w.z(a, 1), "minecraft:white_carpet");
            table.commit();
        }
    }

    /** An ice crystal cluster: blue ice, clear ice, a glittering tip. */
    boolean crystal(int x, int y, int z, int height) {
        Canvas.Piece piece = c.piece();
        for (int k = 0; k < height; k++) {
            piece.add(x, y + k, z, k == 0 ? "minecraft:blue_ice" : "minecraft:light_blue_stained_glass");
        }
        piece.add(x, y + height, z, "minecraft:end_rod[facing=up]");
        return piece.commit();
    }

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        if (!crystal(x, 1, z, 3)) {
            return;
        }
        crystal(x + sx, 1, z, 1);
        crystal(x, 1, z + sz, 2);
        c.setOnAir(x + sx, 1, z + sz, "minecraft:snow_block");
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // an ice knight: quartz plinth, diorite legs, a blue-ice breastplate, a glass helm with a white plume
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_quartz_block");
        for (int k = 1; k < height - 2; k++) {
            piece.add(x, y + k, z, "minecraft:diorite_wall");
        }
        piece.add(x, y + height - 2, z, "minecraft:blue_ice");
        piece.add(x, y + height - 1, z, "minecraft:white_stained_glass");
        if (piece.commit()) {
            c.setOnAir(x, y + height, z, "minecraft:end_rod[facing=up]");
        }
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:chiseled_quartz_block");
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
                for (int sign = -1; sign <= 1; sign += 2) {
                    int s = sign * (l.half() - 1);
                    c.setOnAir(l.x(a, s), 3, l.z(a, s), banner(pick(banners), l.sideFacing(-sign)));
                }
            }
        }
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the queen's crystal: a pillar of blue ice and glass with a sea-lantern heart on a quartz dais
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:chiseled_quartz_block"
                        : "minecraft:quartz_slab[type=bottom]");
            }
        }
        piece.add(x, y + 1, z, "minecraft:blue_ice");
        piece.add(x, y + 2, z, "minecraft:light_blue_stained_glass");
        piece.add(x, y + 3, z, "minecraft:sea_lantern");
        piece.add(x, y + 4, z, "minecraft:light_blue_stained_glass");
        piece.add(x, y + 5, z, "minecraft:end_rod[facing=up]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 1, z + side.dz, "minecraft:light_blue_stained_glass");
            c.setOnAir(x + side.dx, y + 2, z + side.dz, "minecraft:end_rod[facing=up]");
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:sea_lantern";
    }

    @Override
    String portalVeil() {
        return "minecraft:white_stained_glass_pane";
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
                    window(w, a, 2, 6, "minecraft:light_blue_stained_glass", "minecraft:sea_lantern");
                }
            }
        }
    }

    // ---------------------------------------------------------------- gate

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // the queen's crown in ice over the gate
        for (int y = y0; y <= Math.min(top, y0 + 3); y++) {
            for (int s = -3; s <= 3; s++) {
                int ds = Math.abs(s);
                String state = y == y0 ? "minecraft:chiseled_quartz_block" : y == y0 + 1 ? "minecraft:blue_ice"
                        : y == y0 + 2 && ds % 2 == 1 ? "minecraft:packed_ice"
                        : y == y0 + 3 && ds == 0 ? "minecraft:sea_lantern" : null;
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
        Side door = piece.doorSide();
        if (piece.kind().equals("garden")) {
            garden(piece);
            return;
        }
        // the throne room: a carpet from the door to a dais with the throne of ice, banners down the walls
        Side back = door.opposite();
        int depth = (door.dx != 0 ? b.sizeX() : b.sizeZ()) / 2 - 2;
        int tx = center.x() + back.dx * depth;
        int tz = center.z() + back.dz * depth;
        for (int k = -depth; k <= depth - 2; k++) {
            int x = center.x() + back.dx * k;
            int z = center.z() + back.dz * k;
            c.setOnAir(x, 1, z, carpet("light_blue"));
        }
        for (int s = -2; s <= 2; s++) {
            int x = tx + (back.dx == 0 ? s : 0) - back.dx;
            int z = tz + (back.dz == 0 ? s : 0) - back.dz;
            c.setOnAir(x, 1, z, "minecraft:quartz_stairs[facing=" + back.name + ",half=bottom]");
            c.setOnAir(tx + (back.dx == 0 ? s : 0), 1, tz + (back.dz == 0 ? s : 0), "minecraft:quartz_bricks");
        }
        Canvas.Piece throne = c.piece();
        throne.add(tx, 2, tz, "minecraft:quartz_stairs[facing=" + back.name + ",half=bottom]");
        throne.add(tx + back.dx, 2, tz + back.dz, "minecraft:blue_ice");
        throne.add(tx + back.dx, 3, tz + back.dz, "minecraft:blue_ice");
        throne.add(tx + back.dx, 4, tz + back.dz, "minecraft:light_blue_stained_glass");
        throne.add(tx + back.dx, 5, tz + back.dz, "minecraft:end_rod[facing=up]");
        throne.commit();
        for (int s = -1; s <= 1; s += 2) {
            int x = tx + (back.dx == 0 ? s : 0);
            int z = tz + (back.dz == 0 ? s : 0);
            c.setOnAir(x, 2, z, "minecraft:quartz_stairs[facing=" + (back.dx == 0 ? (s > 0 ? "west" : "east")
                    : (s > 0 ? "north" : "south")) + ",half=bottom]");
            crystal(tx + (back.dx == 0 ? s * 2 : 0), 2, tz + (back.dz == 0 ? s * 2 : 0), 2);
        }
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == door) {
                continue;
            }
            for (int a = w.min() + 1; a <= w.max() - 1; a += 2) {
                c.setOnAir(w.x(a, 1), 6, w.z(a, 1), banner(a % 4 == 1 ? "light_blue" : "white", w.in()));
            }
        }
    }

    /** A frozen garden: trees of ice with snowy crowns, a frozen fountain, snow paths. */
    private void garden(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        BlockPoint center = b.center(1);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                c.setOnAir(center.x() + dx, 1, center.z() + dz, dx == 0 && dz == 0 ? "minecraft:packed_ice"
                        : "minecraft:quartz_slab[type=bottom]");
            }
        }
        crystal(center.x(), 2, center.z(), 2);
        int[][] trees = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
        for (int[] t : trees) {
            int x = center.x() + t[0];
            int z = center.z() + t[1];
            Canvas.Piece tree = c.piece();
            tree.add(x, 1, z, "minecraft:packed_ice");
            tree.add(x, 2, z, "minecraft:packed_ice");
            tree.add(x, 3, z, "minecraft:blue_ice");
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    tree.add(x + dx, 4, z + dz, "minecraft:snow_block");
                }
            }
            tree.add(x, 5, z, "minecraft:snow_block");
            tree.commit();
        }
        for (int x = b.minX() + 1; x <= b.maxX() - 1; x++) {
            for (int z = b.minZ() + 1; z <= b.maxZ() - 1; z++) {
                if (Math.floorMod(x * 7 + z * 11, 6) == 0) {
                    c.setOnAir(x, 1, z, "minecraft:white_carpet");
                }
            }
        }
    }

    // ---------------------------------------------------------------- arena

    @Override
    String arenaFire() {
        return "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]";
    }

    @Override
    String arenaWindow() {
        return "minecraft:light_blue_stained_glass";
    }
}
