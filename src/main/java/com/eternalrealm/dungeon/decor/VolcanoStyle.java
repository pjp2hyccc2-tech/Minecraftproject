package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;

import java.util.Random;

/**
 * d6 灼熱の火山洞窟: a volcanic cave where a dragon sleeps. Basalt columns and blackstone, magma veins in the walls,
 * lava falling behind iron grates (fully enclosed, so it can never flow or set anything alight), fire bowls hanging
 * on chains, stalactites, the ribs of dead dragons along the walls; a dragon forge and a bone hall as set pieces.
 * No wood, wool or carpet anywhere near the lava.
 */
final class VolcanoStyle extends Style {
    VolcanoStyle(Canvas canvas, Random random) {
        super(canvas, random);
        wallBase = "minecraft:polished_basalt[axis=y]";
        dado = 1;
        trim = "minecraft:gilded_blackstone";
        frieze = 5;
        pilaster = "minecraft:basalt[axis=y]";
        pilasterSpacing = 5;
        stairs = "minecraft:polished_blackstone_brick_stairs";
        slab = "minecraft:polished_blackstone_brick_slab";
        wallId = "minecraft:polished_blackstone_brick_wall";
        ceiling = "#wall";
        border = "minecraft:polished_basalt[axis=y]";
        inlay = "minecraft:gilded_blackstone";
        inlay2 = "minecraft:blackstone";
        runner = "minecraft:polished_blackstone_bricks";
        floorLight = "minecraft:shroomlight";
        hangLamp = "minecraft:lantern[hanging=true]";
        torch = "minecraft:wall_torch";
        fence = "minecraft:nether_brick_fence";
        grate = "minecraft:iron_bars";
        grateLight = "minecraft:shroomlight";
        railing = "minecraft:polished_blackstone_brick_wall";
        banners = new String[]{"red", "black", "orange"};
        carpets = new String[]{"black"};
        setPieces = new String[]{"forge", "bones"};
        floorVariants = new String[]{"minecraft:blackstone", "minecraft:basalt[axis=y]", "minecraft:smooth_basalt",
                "minecraft:cracked_polished_blackstone_bricks"};
        wallVariants = new String[]{"minecraft:magma_block", "minecraft:cracked_polished_blackstone_bricks",
                "minecraft:gilded_blackstone", "minecraft:blackstone"};
        clutterChance = 0.25;
    }

    @Override
    void clutter(int x, int y, int z, Wall w) {
        switch (r.nextInt(5)) {
            case 0 -> c.set(x, y, z, "minecraft:wither_skeleton_skull[rotation=" + r.nextInt(16) + "]");
            case 1 -> c.set(x, y, z, "minecraft:blackstone_slab[type=bottom]");
            case 2 -> c.set(x, y, z, "minecraft:bone_block[axis=" + (w.alongX() ? "x" : "z") + "]");
            case 3 -> c.set(x, y, z, "minecraft:polished_blackstone_button[face=floor,facing=north,powered=false]");
            default -> c.set(x, y, z, "minecraft:magma_block");
        }
    }

    /**
     * Lava falling behind a grate in wall {@code w} at {@code a}: a column of lava sources from y=1 to {@code top}
     * one block behind the wall, walled in on every side, the grate in the wall plane in front of it.
     */
    boolean lavaFall(Wall w, int a, int top) {
        int xa = w.x(a - 1, -1);
        int xb = w.x(a + 1, -2);
        int za = w.z(a - 1, -1);
        int zb = w.z(a + 1, -2);
        BlockBox box = new BlockBox(Math.min(xa, xb), 0, Math.min(za, zb), Math.max(xa, xb), top + 1,
                Math.max(za, zb));
        if (!c.claim(box)) {
            return false;
        }
        for (int k = -1; k <= 1; k++) {
            for (int d = -2; d <= -1; d++) {
                for (int y = 0; y <= top + 1; y++) {
                    boolean lava = k == 0 && d == -1 && y >= 1 && y <= top;
                    c.set(w.x(a + k, d), y, w.z(a + k, d), lava ? "minecraft:lava[level=0]" : "minecraft:blackstone");
                }
            }
        }
        for (int y = 1; y <= top; y++) {
            c.set(w.x(a, 0), y, w.z(a, 0), grate);
        }
        c.fluid(new BlockBox(w.x(a, -1), 1, w.z(a, -1), w.x(a, -1), top, w.z(a, -1)));
        return true;
    }

    /** A dragon's rib curving up the wall and over (bone blocks). */
    void rib(Wall w, int a, int top) {
        Canvas.Piece piece = c.piece();
        for (int y = 1; y <= top - 2; y++) {
            piece.add(w.x(a, 1), y, w.z(a, 1), "minecraft:bone_block[axis=y]");
        }
        char across = w.alongX() ? 'z' : 'x';
        piece.add(w.x(a, 2), top - 1, w.z(a, 2), "minecraft:bone_block[axis=" + across + "]");
        piece.add(w.x(a, 3), top, w.z(a, 3), "minecraft:bone_block[axis=" + across + "]");
        piece.commit();
    }

    @Override
    boolean chandelier(int x, int z, int ceilingY, int low) {
        // a fire bowl: a cauldron of lava on chains (the lava is part of the cauldron, it cannot flow)
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y > low; y--) {
            piece.add(x, y, z, chain);
        }
        piece.add(x, low, z, "minecraft:lava_cauldron");
        return piece.commit();
    }

    @Override
    void sconce(Wall w, int a, int y) {
        c.setOnSolid(w.x(a, 0), y, w.z(a, 0), "minecraft:magma_block");
        c.setOnAir(w.x(a, 1), y + 1, w.z(a, 1), torch(w.in()));
    }

    @Override
    void cornice(Wall w, int top) {
        super.cornice(w, top);
        for (int a = w.min() + 1; a <= w.max() - 1; a += 2) {
            if (Math.floorMod(a * 5, 3) == 0) {
                stalactite(w.x(a, 2), top, w.z(a, 2), 1 + r.nextInt(2));
            }
        }
    }

    /** Dripstone hanging from under (x, top + 1, z). */
    void stalactite(int x, int top, int z, int length) {
        Canvas.Piece piece = c.piece();
        for (int k = 0; k < length; k++) {
            String thickness = k == length - 1 ? "tip" : k == 0 && length > 2 ? "base" : "frustum";
            piece.add(x, top - k, z, "minecraft:pointed_dripstone[thickness=" + thickness
                    + ",vertical_direction=down,waterlogged=false]");
        }
        piece.commit();
    }

    // ---------------------------------------------------------------- walls

    @Override
    void bay(Bay bay) {
        Wall w = bay.wall();
        int a = bay.mid();
        if (bay.width() >= 3 && chance(0.6) && lavaFall(w, a, 4)) {
            c.setOnAir(w.x(a - 1, 1), 1, w.z(a - 1, 1), "minecraft:blackstone");
            c.setOnAir(w.x(a + 1, 1), 1, w.z(a + 1, 1), "minecraft:blackstone");
            return;
        }
        if (chance(0.5)) {
            rib(w, a, bay.top());
        } else {
            forge(w, a);
        }
    }

    /** A smith's corner against the wall: anvil, smithing table, grindstone. */
    void forge(Wall w, int a) {
        c.setOnAir(w.x(a, 1), 1, w.z(a, 1), "minecraft:anvil[facing=" + w.along() + "]");
        c.setOnAir(w.x(a - 1, 1), 1, w.z(a - 1, 1), "minecraft:smithing_table");
        c.setOnAir(w.x(a + 1, 1), 1, w.z(a + 1, 1), "minecraft:blast_furnace[facing=" + w.in() + ",lit=false]");
    }

    @Override
    void corner(int x, int z, int sx, int sz, int top) {
        // an obsidian outcrop with crying obsidian, a skull on top
        Canvas.Piece rock = c.piece();
        rock.add(x, 1, z, "minecraft:obsidian");
        rock.add(x, 2, z, "minecraft:crying_obsidian");
        rock.add(x + sx, 1, z, "minecraft:blackstone");
        rock.add(x, 1, z + sz, "minecraft:basalt[axis=y]");
        if (rock.commit()) {
            c.setOnAir(x, 3, z, "minecraft:wither_skeleton_skull[rotation=" + r.nextInt(16) + "]");
        }
        stalactite(x, top, z, 2);
    }

    @Override
    void roomExtras(Spaces.Room room, int ceilingY) {
        BlockBox b = room.box();
        for (int x = b.minX() + 3; x <= b.maxX() - 3; x += 4) {
            for (int z = b.minZ() + 3; z <= b.maxZ() - 3; z += 4) {
                if (Math.floorMod(x * 3 + z * 7, 3) == 0) {
                    stalactite(x, ceilingY - 1, z, 1);
                }
            }
        }
    }

    @Override
    void statue(int x, int y, int z, Side facing, int height) {
        // a dragon guard: blackstone body, a gilded breastplate, a wither skull helm
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:polished_blackstone");
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, k == height - 2 ? "minecraft:gilded_blackstone" : "minecraft:blackstone_wall");
        }
        piece.add(x, y + height - 1, z, "minecraft:wither_skeleton_skull[rotation=" + facing.rotation() + "]");
        piece.commit();
    }

    @Override
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, "minecraft:polished_blackstone_bricks");
        piece.add(x, y + 1, z, "minecraft:lava_cauldron");
        piece.commit();
    }

    // ---------------------------------------------------------------- corridors

    @Override
    void corridorBay(Link l, int a, int index) {
        switch (index % 3) {
            case 0 -> {
                lavaNiche(l, a, 1);
                lavaNiche(l, a, -1);
            }
            case 1 -> {
                statueNiche(l, a, 1);
                statueNiche(l, a, -1);
            }
            default -> {
                wallLamp(l, a, 1);
                wallLamp(l, a, -1);
                for (int sign = -1; sign <= 1; sign += 2) {
                    c.setOnSolid(l.x(a, sign * l.half()), 2, l.z(a, sign * l.half()), "minecraft:magma_block");
                }
            }
        }
    }

    /** Lava behind bars in a corridor wall, enclosed on every side. */
    boolean lavaNiche(Link l, int a, int sign) {
        int back = sign * (l.half() + 1);
        int far = sign * (l.half() + 2);
        BlockBox box = new BlockBox(Math.min(l.x(a - 1, back), l.x(a + 1, far)), 0,
                Math.min(l.z(a - 1, back), l.z(a + 1, far)), Math.max(l.x(a - 1, back), l.x(a + 1, far)),
                Geometry.OPENING_TOP + 1, Math.max(l.z(a - 1, back), l.z(a + 1, far)));
        if (!c.claim(box)) {
            return false;
        }
        for (int k = -1; k <= 1; k++) {
            for (int s : new int[]{back, far}) {
                for (int y = 0; y <= Geometry.OPENING_TOP + 1; y++) {
                    boolean lava = k == 0 && s == back && y >= 1 && y <= 3;
                    c.set(l.x(a + k, s), y, l.z(a + k, s), lava ? "minecraft:lava[level=0]" : "minecraft:blackstone");
                }
            }
        }
        int wall = sign * l.half();
        for (int y = 1; y <= 3; y++) {
            c.set(l.x(a, wall), y, l.z(a, wall), grate);
        }
        c.fluid(new BlockBox(l.x(a, back), 1, l.z(a, back), l.x(a, back), 3, l.z(a, back)));
        return true;
    }

    // ---------------------------------------------------------------- hall

    @Override
    void revivalMonument(int x, int y, int z, Side facing) {
        // the phoenix brazier: a basalt obelisk with an eternal flame, gilded at its foot
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, dx == 0 && dz == 0 ? "minecraft:gilded_blackstone"
                        : "minecraft:polished_blackstone_brick_slab[type=bottom]");
            }
        }
        piece.add(x, y + 1, z, "minecraft:polished_basalt[axis=y]");
        piece.add(x, y + 2, z, "minecraft:basalt[axis=y]");
        piece.add(x, y + 3, z, "minecraft:polished_basalt[axis=y]");
        piece.add(x, y + 4, z, "minecraft:shroomlight");
        piece.add(x, y + 5, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        piece.force();
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, y + 1, z + side.dz, "minecraft:wither_skeleton_skull[rotation="
                    + side.rotation() + "]");
        }
    }

    @Override
    String portalGlow() {
        return "minecraft:shroomlight";
    }

    @Override
    String portalVeil() {
        return "minecraft:orange_stained_glass_pane";
    }

    @Override
    void hallExtras(Spaces.Hall hall) {
        BlockBox g = hall.box();
        for (Wall w : Geometry.walls(g)) {
            if (w.side() == hall.front()) {
                continue;
            }
            for (int a = w.min() + 2; a <= w.max() - 2; a += 5) {
                if (Math.abs(a - w.mid()) > 3) {
                    lavaFall(w, a, 6);
                }
            }
        }
    }

    // ---------------------------------------------------------------- gate

    @Override
    void gateSculpture(Link l, int wallA, int y0, int top) {
        // a dragon skull of bone over the gate: brow, glowing eye sockets, jaw and teeth
        for (int y = y0; y <= Math.min(top, y0 + 4); y++) {
            for (int s = -3; s <= 3; s++) {
                int ds = Math.abs(s);
                String state = null;
                if (y == y0 + 4 && ds <= 2 || y == y0 + 3 && ds <= 3) {
                    state = "minecraft:bone_block[axis=y]";
                } else if (y == y0 + 2) {
                    state = ds == 2 ? "minecraft:shroomlight" : ds <= 3 ? "minecraft:bone_block[axis=y]" : null;
                } else if (y == y0 + 1 && ds <= 2) {
                    state = ds == 1 ? "minecraft:blackstone" : "minecraft:bone_block[axis=y]";
                } else if (y == y0 && ds <= 2 && ds % 2 == 0) {
                    state = "minecraft:bone_block[axis=y]";
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
        BlockBox b = piece.box();
        BlockPoint center = b.center(1);
        if (piece.kind().equals("bones")) {
            // the bone hall: a dragon's skeleton along the room, ribs on both walls
            for (Wall w : Geometry.walls(b)) {
                if (w.side() == piece.doorSide()) {
                    continue;
                }
                for (int a = w.min() + 1; a <= w.max() - 1; a += 2) {
                    rib(w, a, b.maxY() - 1);
                }
            }
            boolean alongX = piece.doorSide() == Side.EAST || piece.doorSide() == Side.WEST;
            for (int k = -4; k <= 4; k++) {
                int x = center.x() + (alongX ? k : 0);
                int z = center.z() + (alongX ? 0 : k);
                c.setOnAir(x, 1, z, "minecraft:bone_block[axis=" + (alongX ? "x" : "z") + "]");
            }
            Canvas.Piece skull = c.piece();
            int sx = center.x() + (alongX ? 5 : 0);
            int sz = center.z() + (alongX ? 0 : 5);
            skull.add(sx, 1, sz, "minecraft:bone_block[axis=y]");
            skull.add(sx, 2, sz, "minecraft:bone_block[axis=y]");
            skull.add(sx + (alongX ? 1 : 0), 1, sz + (alongX ? 0 : 1), "minecraft:bone_block[axis=y]");
            skull.commit();
            return;
        }
        // the dragon forge: a lava channel across the floor with a basalt bridge, anvils and furnaces round it
        boolean alongX = piece.doorSide() == Side.NORTH || piece.doorSide() == Side.SOUTH;
        BlockBox channel = alongX
                ? new BlockBox(b.minX() + 2, -1, center.z() + 2, b.maxX() - 2, 0, center.z() + 2)
                : new BlockBox(center.x() + 2, -1, b.minZ() + 2, center.x() + 2, 0, b.maxZ() - 2);
        if (c.claim(new BlockBox(channel.minX() - 1, -2, channel.minZ() - 1, channel.maxX() + 1, -1,
                channel.maxZ() + 1))) {
            for (int x = channel.minX() - 1; x <= channel.maxX() + 1; x++) {
                for (int z = channel.minZ() - 1; z <= channel.maxZ() + 1; z++) {
                    c.set(x, -2, z, "minecraft:blackstone");
                    c.set(x, -1, z, "minecraft:blackstone");
                }
            }
            for (int x = channel.minX(); x <= channel.maxX(); x++) {
                for (int z = channel.minZ(); z <= channel.maxZ(); z++) {
                    c.set(x, -1, z, "minecraft:lava[level=0]");
                    c.set(x, 0, z, "minecraft:iron_bars");
                }
            }
            c.fluid(new BlockBox(channel.minX(), -1, channel.minZ(), channel.maxX(), -1, channel.maxZ()));
        }
        for (Wall w : Geometry.walls(b)) {
            if (w.side() == piece.doorSide()) {
                continue;
            }
            for (int a = w.min() + 1; a <= w.max() - 1; a += 4) {
                forge(w, a + 1);
            }
        }
        brazier(center.x() - 2, 1, center.z() - 2);
        brazier(center.x() + 2, 1, center.z() - 2);
    }

    // ---------------------------------------------------------------- arena

    @Override
    void arenaBay(Bay bay, int index) {
        Wall w = bay.wall();
        if (index % 2 == 0 && lavaFall(w, bay.mid(), Math.min(bay.top() - 2, 14))) {
            return;
        }
        super.arenaBay(bay, index);
    }

    @Override
    String arenaWindow() {
        return "minecraft:orange_stained_glass";
    }
}
