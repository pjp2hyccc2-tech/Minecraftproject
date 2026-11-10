package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.BlockRole;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Geometry.Wall;
import com.eternalrealm.dungeon.decor.Spaces.Bay;
import com.eternalrealm.dungeon.decor.Spaces.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The architecture every theme shares, driven by the theme's materials, and the hooks a theme fills with its own
 * vocabulary (props, statues, braziers, set-piece rooms). One instance per planned layout.
 *
 * <p>Rules every method keeps: combat-room furniture stands in the ring along the walls (the floor inside stays
 * open), corridor decoration stays in the corridor's walls and ceiling, the arena only gets its walls, floor and
 * ceiling restyled (plus banners, which nothing collides with), and floors are only ever replaced by other full
 * blocks. The {@link Canvas} refuses anything on reserved cells, so a hook may simply try.</p>
 */
abstract class Style {
    final Canvas c;
    final Random r;

    // ---------------------------------------------------------------- materials (set by the themes)
    /** Dado band at the foot of the walls, {@link #dado} blocks high. */
    String wallBase = "#wall";
    int dado = 1;
    /** Frieze band, lintels, keystones. */
    String trim = "#accent";
    /** Height of the frieze band in rooms (0 = none). */
    int frieze = 5;
    String pilaster = "#pillar";
    int pilasterSpacing = 5;
    /** Stairs, slab and wall ids of the main stone (cornices, arches, railings). */
    String stairs = "minecraft:stone_brick_stairs";
    String slab = "minecraft:stone_brick_slab";
    String wallId = "minecraft:stone_brick_wall";
    /** Log id for timber beams across room ceilings, or null. */
    String beam;
    /** Whether rooms get a raised, coved ceiling instead of a flat one. */
    boolean vault;
    String ceiling = "#wall";
    String ceilingLight = "minecraft:glowstone";
    String border = "#accent";
    String inlay = "#accent";
    String inlay2 = "#floor";
    String runner = "#accent";
    String floorLight = "minecraft:glowstone";
    String hangLamp = "minecraft:lantern[hanging=true]";
    String standLamp = "minecraft:lantern[hanging=false]";
    String torch = "minecraft:wall_torch";
    String fence = "minecraft:spruce_fence";
    String chain = "minecraft:chain[axis=y]";
    String grate = "minecraft:iron_bars";
    String grateLight = "minecraft:glowstone";
    String railing = "minecraft:stone_brick_wall";
    String[] banners = {"red"};
    String[] carpets = {"red"};
    String candle = "minecraft:candle";
    /** Set-piece kinds of the theme. */
    String[] setPieces = {"vault"};
    /** Blocks mixed into floors and walls for wear (cracks, moss, sand). */
    String[] floorVariants = {};
    String[] wallVariants = {};
    /** Share of free spots along the walls that get a small item ({@link #clutter}). */
    double clutterChance = 0.3;

    /** Feet position and facing of the revival point, set by {@link #hall}. */
    BlockPoint revival;
    float revivalYaw;

    Style(Canvas canvas, Random random) {
        this.c = canvas;
        this.r = random;
    }

    // ---------------------------------------------------------------- block helpers

    String stairs(String facing, boolean top) {
        return stairs(stairs, facing, top);
    }

    static String stairs(String id, String facing, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    String slab(boolean top) {
        return slab(slab, top);
    }

    static String slab(String id, boolean top) {
        return id + "[type=" + (top ? "top" : "bottom") + "]";
    }

    static String axis(String log, char axis) {
        return log + "[axis=" + axis + "]";
    }

    String torch(String facing) {
        return torch + "[facing=" + facing + "]";
    }

    static String banner(String color, String facing) {
        return "minecraft:" + color + "_wall_banner[facing=" + facing + "]";
    }

    static String carpet(String color) {
        return "minecraft:" + color + "_carpet";
    }

    String candles(int count) {
        return candle + "[candles=" + count + ",lit=true]";
    }

    static String facing(String id, String facing) {
        return id + "[facing=" + facing + "]";
    }

    static String skull(String id, int rotation) {
        return id + "[rotation=" + (rotation & 15) + "]";
    }

    <T> T pick(T[] options) {
        return options[r.nextInt(options.length)];
    }

    boolean chance(double p) {
        return r.nextDouble() < p;
    }

    // ---------------------------------------------------------------- combat rooms

    void room(Spaces.Room room) {
        BlockBox b = room.box();
        int top = b.maxY() - 1;
        boolean vaulted = vault && vaultCeiling(b);
        for (Wall w : Geometry.walls(b)) {
            List<int[]> open = openingsOn(w, room.openings());
            bands(w, top);
            for (int[] o : open) {
                boolean boss = room.bossDoor() && room.exit() != null && w.side() == Geometry.sideOf(b, room.exit())
                        && o[0] == along(w, room.exit());
                doorFrame(w, o, top, boss);
            }
            List<Integer> posts = posts(w, open);
            for (int i = 0; i < posts.size(); i++) {
                pilaster(w, posts.get(i), top, i % 2 == 0);
            }
            for (Bay bay : bays(w, open, posts, top, Kind.ROOM)) {
                bay(bay);
            }
            cornice(w, top);
        }
        dressPillars(b, top);
        floor(b, room.openings());
        scatter(b, top);
        for (Wall w : Geometry.walls(b)) {
            clutterAlong(w);
        }
        int ceilingY = vaulted ? b.maxY() + 3 : b.maxY();
        if (!vaulted && beam != null) {
            beams(b, top);
        } else {
            chandeliers(b, ceilingY, top - 1);
        }
        corners(b, top);
        roomExtras(room, ceilingY);
    }

    /** Along-wall coordinate of an opening's first block. */
    static int along(Wall w, BlockBox opening) {
        return w.alongX() ? opening.minX() : opening.minZ();
    }

    /** The openings in wall {@code w}: {min along, max along, minY, maxY}. */
    static List<int[]> openingsOn(Wall w, List<BlockBox> openings) {
        List<int[]> found = new ArrayList<>();
        for (BlockBox o : openings) {
            if (Geometry.sideOf(w.box(), o) == w.side()) {
                found.add(w.alongX() ? new int[]{o.minX(), o.maxX(), o.minY(), o.maxY()}
                        : new int[]{o.minZ(), o.maxZ(), o.minY(), o.maxY()});
            }
        }
        return found;
    }

    static boolean nearOpening(List<int[]> open, int a, int margin) {
        for (int[] o : open) {
            if (a >= o[0] - margin && a <= o[1] + margin) {
                return true;
            }
        }
        return false;
    }

    /** Dado and frieze bands in the wall plane. */
    void bands(Wall w, int top) {
        for (int a = w.min() - 1; a <= w.max() + 1; a++) {
            for (int y = 1; y <= dado; y++) {
                c.setOnSolid(w.x(a, 0), w.box().minY() + y, w.z(a, 0), wallBase);
            }
            if (frieze > 0 && frieze < top) {
                c.setOnSolid(w.x(a, 0), w.box().minY() + frieze, w.z(a, 0), trim);
            }
        }
    }

    /** Pilasters framing an opening, a lintel over it. */
    void doorFrame(Wall w, int[] o, int top, boolean boss) {
        int lintel = o[3] + 1;
        for (int y = 1; y <= Math.min(top, lintel); y++) {
            c.setOnSolid(w.x(o[0] - 1, 0), y, w.z(o[0] - 1, 0), pilaster);
            c.setOnSolid(w.x(o[1] + 1, 0), y, w.z(o[1] + 1, 0), pilaster);
        }
        for (int a = o[0] - 1; a <= o[1] + 1; a++) {
            c.setOnSolid(w.x(a, 0), lintel, w.z(a, 0), trim);
        }
        if (boss) {
            bossDoor(w, o, top);
        }
    }

    /** Pilaster positions along a wall: symmetric around its middle, clear of openings. */
    List<Integer> posts(Wall w, List<int[]> open) {
        List<Integer> posts = new ArrayList<>();
        int mid = w.mid();
        int reach = (w.length() - 1) / 2;
        for (int k = -reach / pilasterSpacing; k <= reach / pilasterSpacing; k++) {
            int a = mid + k * pilasterSpacing;
            if (a > w.min() && a < w.max() && !nearOpening(open, a, 2)) {
                posts.add(a);
            }
        }
        return posts;
    }

    void pilaster(Wall w, int a, int top, boolean lamp) {
        for (int y = 1; y <= top; y++) {
            c.setOnSolid(w.x(a, 0), y, w.z(a, 0), pilaster);
        }
        c.setOnAir(w.x(a, 1), top - 1, w.z(a, 1), stairs(w.out(), true));
        if (lamp) {
            sconce(w, a, 3);
        }
    }

    /** The stretches of wall between posts and openings. */
    static List<Bay> bays(Wall w, List<int[]> open, List<Integer> posts, int top, Kind kind) {
        List<Bay> bays = new ArrayList<>();
        int start = w.min();
        for (int a = w.min(); a <= w.max() + 1; a++) {
            boolean stop = a > w.max() || posts.contains(a) || nearOpening(open, a, 1);
            if (stop) {
                if (a - 1 >= start) {
                    bays.add(new Bay(w, start, a - 1, top, kind));
                }
                start = a + 1;
            }
        }
        return bays;
    }

    void cornice(Wall w, int top) {
        for (int a = w.min(); a <= w.max(); a++) {
            c.setOnAir(w.x(a, 1), top, w.z(a, 1), stairs(w.out(), true));
        }
    }

    /** Capitals on the layout's free-standing pillars. */
    void dressPillars(BlockBox b, int top) {
        for (int x = b.minX() + 1; x <= b.maxX() - 1; x++) {
            for (int z = b.minZ() + 1; z <= b.maxZ() - 1; z++) {
                if (c.role(x, 1, z) == BlockRole.PILLAR && c.state(x, 1, z).equals("#pillar")) {
                    for (Side side : Side.values()) {
                        c.setOnAir(x + side.dx, top - 1, z + side.dz, stairs(side.opposite().name, true));
                        c.setOnAir(x + side.dx, 1, z + side.dz, stairs(side.opposite().name, false));
                    }
                    pillarDressing(x, z, top);
                }
            }
        }
    }

    /** Floor: border ring, a medallion in the middle, runners from the openings to it. */
    void floor(BlockBox b, List<BlockBox> openings) {
        int y = b.minY();
        for (int x = b.minX() + 1; x <= b.maxX() - 1; x++) {
            c.setOnSolid(x, y, b.minZ() + 1, border);
            c.setOnSolid(x, y, b.maxZ() - 1, border);
        }
        for (int z = b.minZ() + 1; z <= b.maxZ() - 1; z++) {
            c.setOnSolid(b.minX() + 1, y, z, border);
            c.setOnSolid(b.maxX() - 1, y, z, border);
        }
        BlockPoint center = b.center(y);
        int radius = Math.max(1, Math.min(b.sizeX(), b.sizeZ()) / 2 - 4);
        for (BlockBox o : openings) {
            runner(b, o, center, radius);
        }
        medallion(center.x(), y, center.z(), radius);
    }

    /** A straight then turning path of runner blocks from an opening to the middle. */
    void runner(BlockBox b, BlockBox o, BlockPoint center, int radius) {
        Side side = Geometry.sideOf(b, o);
        if (side == null) {
            return;
        }
        int y = b.minY();
        int x = (o.minX() + o.maxX()) / 2;
        int z = (o.minZ() + o.maxZ()) / 2;
        x -= side.dx;
        z -= side.dz;
        for (int guard = 0; guard < 200; guard++) {
            int dx = center.x() - x;
            int dz = center.z() - z;
            if (dx * dx + dz * dz <= radius * radius) {
                return;
            }
            c.setOnSolid(x, y, z, runner);
            if (side.dx != 0 ? dx != 0 : dz == 0) {
                x += Integer.signum(dx);
            } else {
                z += Integer.signum(dz);
            }
        }
    }

    /** A round floor medallion: light at the centre, rings of inlay, light studs on the axes. */
    void medallion(int cx, int y, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                if (d > radius) {
                    continue;
                }
                String state = medallionBlock(d, dx, dz, radius);
                if (state != null) {
                    c.setOnSolid(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    String medallionBlock(int d, int dx, int dz, int radius) {
        if (d == 0) {
            return floorLight;
        }
        if (d == radius) {
            return inlay;
        }
        if (d == radius - 1 && radius >= 3 && (dx == 0 || dz == 0)) {
            return floorLight;
        }
        return d % 2 == 0 ? inlay2 : inlay;
    }

    /** Timber beams across the shorter span, posts under their ends, lamps hanging from them. */
    void beams(BlockBox b, int top) {
        boolean acrossZ = b.sizeX() >= b.sizeZ();
        int from = acrossZ ? b.minX() : b.minZ();
        int to = acrossZ ? b.maxX() : b.maxZ();
        int mid = (from + to) / 2;
        for (int p = mid - 4 * ((mid - from - 2) / 4); p <= to - 2; p += 4) {
            int lo = acrossZ ? b.minZ() : b.minX();
            int hi = acrossZ ? b.maxZ() : b.maxX();
            for (int q = lo + 1; q <= hi - 1; q++) {
                int x = acrossZ ? p : q;
                int z = acrossZ ? q : p;
                c.set(x, top, z, axis(beam, acrossZ ? 'z' : 'x'));
            }
            for (int y = 1; y < top; y++) {
                c.setOnSolid(acrossZ ? p : lo, y, acrossZ ? lo : p, axis(beam, 'y'));
                c.setOnSolid(acrossZ ? p : hi, y, acrossZ ? hi : p, axis(beam, 'y'));
            }
            int span = hi - lo;
            for (int q = lo + 3; q <= hi - 3; q += Math.max(4, span / 3)) {
                int x = acrossZ ? p : q;
                int z = acrossZ ? q : p;
                beamLamp(x, top - 1, z);
            }
        }
    }

    void beamLamp(int x, int y, int z) {
        c.setOnAir(x, y, z, hangLamp);
    }

    /** Chandeliers on a grid under the ceiling, lamps no lower than {@code low}. */
    void chandeliers(BlockBox b, int ceilingY, int low) {
        BlockBox in = b.interior();
        BlockPoint center = b.center(0);
        int spacing = 8;
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                int x = center.x() + i * spacing;
                int z = center.z() + j * spacing;
                if (x - 2 < in.minX() || x + 2 > in.maxX() || z - 2 < in.minZ() || z + 2 > in.maxZ()) {
                    continue;
                }
                if (in.sizeX() < 13 && i != 0 || in.sizeZ() < 13 && j != 0) {
                    continue;
                }
                chandelier(x, z, ceilingY, low);
            }
        }
    }

    /** A ceiling-hung lamp: chain down from the ceiling, four arms with lamps. */
    boolean chandelier(int x, int z, int ceilingY, int low) {
        Canvas.Piece piece = c.piece();
        for (int y = ceilingY - 1; y >= low + 2; y--) {
            piece.add(x, y, z, chain);
        }
        piece.add(x, low + 1, z, fence);
        piece.add(x, low, z, hangLamp);
        for (Side side : Side.values()) {
            piece.add(x + side.dx, low + 1, z + side.dz, fence);
            piece.add(x + side.dx, low, z + side.dz, hangLamp);
        }
        return piece.commit();
    }

    /** Raises a room's ceiling by three blocks with a stepped cove. @return false if there is no room above */
    boolean vaultCeiling(BlockBox b) {
        int y0 = b.maxY();
        BlockBox above = new BlockBox(b.minX(), y0, b.minZ(), b.maxX(), y0 + 3, b.maxZ());
        if (!c.claim(above, b)) {
            return false;
        }
        for (int y = y0; y <= y0 + 3; y++) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    int d = Math.min(Math.min(x - b.minX(), b.maxX() - x), Math.min(z - b.minZ(), b.maxZ() - z));
                    String state;
                    if (y == y0 + 3) {
                        state = d == 0 ? "#wall" : ceilingAt(x - b.minX(), z - b.minZ(), d);
                    } else if (d == 0) {
                        state = "#wall";
                    } else {
                        int step = y - y0 + 1; // 1..3: the cove steps in one block per level
                        state = d < step ? ceiling : d == step ? coveStairs(b, x, z) : Canvas.AIR;
                    }
                    c.set(x, y, z, state);
                }
            }
        }
        return true;
    }

    private String coveStairs(BlockBox b, int x, int z) {
        int west = x - b.minX();
        int east = b.maxX() - x;
        int north = z - b.minZ();
        int south = b.maxZ() - z;
        int min = Math.min(Math.min(west, east), Math.min(north, south));
        String facing = min == west ? "west" : min == east ? "east" : min == north ? "north" : "south";
        return stairs(facing, true);
    }

    /** The raised ceiling: coffers of trim every four blocks, a light in every coffer's middle. */
    String ceilingAt(int lx, int lz, int d) {
        if (d < 4) {
            return ceiling;
        }
        if (lx % 4 == 0 || lz % 4 == 0) {
            return trim;
        }
        return lx % 4 == 2 && lz % 4 == 2 && (lx / 4 + lz / 4) % 2 == 0 ? ceilingLight : ceiling;
    }

    void corners(BlockBox b, int top) {
        int[][] corners = {{b.minX() + 1, b.minZ() + 1, 1, 1}, {b.maxX() - 1, b.minZ() + 1, -1, 1},
                {b.minX() + 1, b.maxZ() - 1, 1, -1}, {b.maxX() - 1, b.maxZ() - 1, -1, -1}};
        for (int[] corner : corners) {
            corner(corner[0], corner[1], corner[2], corner[3], top);
        }
    }

    // ---------------------------------------------------------------- corridors

    void corridor(Link l) {
        int inner = l.half() - 1;
        for (int a = l.start(); a <= l.end(); a++) {
            for (int s = -inner; s <= inner; s++) {
                c.setOnSolid(l.x(a, s), 0, l.z(a, s), s == 0 ? runner : border);
            }
        }
        List<Integer> ribs = ribs(l);
        for (int a : ribs) {
            rib(l, a);
        }
        int bay = 0;
        for (int i = 0; i + 1 < ribs.size(); i++) {
            int a = (ribs.get(i) + ribs.get(i + 1)) / 2;
            corridorBay(l, a, bay++);
        }
    }

    /** Arch positions: every four blocks, centred on the corridor. */
    static List<Integer> ribs(Link l) {
        List<Integer> ribs = new ArrayList<>();
        int length = l.length();
        if (length < 3) {
            return ribs;
        }
        int offset = ((length - 3) % 4) / 2;
        for (int a = l.start() + 1 + offset; a <= l.end() - 1; a += 4) {
            ribs.add(a);
        }
        return ribs;
    }

    /** An arch: pilasters in both walls, a band across the ceiling, stairs rounding its top corners. */
    void rib(Link l, int a) {
        int half = l.half();
        int inner = half - 1;
        for (int y = 1; y <= Geometry.OPENING_TOP; y++) {
            c.setOnSolid(l.x(a, half), y, l.z(a, half), pilaster);
            c.setOnSolid(l.x(a, -half), y, l.z(a, -half), pilaster);
        }
        int ceilingY = Geometry.OPENING_TOP + 1;
        for (int s = -inner; s <= inner; s++) {
            if (c.role(l.x(a, s), ceilingY, l.z(a, s)) != BlockRole.LIGHT) {
                c.setOnSolid(l.x(a, s), ceilingY, l.z(a, s), trim);
            }
        }
        c.setOnAir(l.x(a, inner), Geometry.OPENING_TOP, l.z(a, inner), stairs(l.sideFacing(1), true));
        c.setOnAir(l.x(a, -inner), Geometry.OPENING_TOP, l.z(a, -inner), stairs(l.sideFacing(-1), true));
    }

    /** Between two arches: a lamp niche, wall lamps or banners, in turn. */
    void corridorBay(Link l, int a, int index) {
        switch (index % 3) {
            case 0 -> {
                grateNiche(l, a, 1);
                grateNiche(l, a, -1);
            }
            case 1 -> {
                wallLamp(l, a, 1);
                wallLamp(l, a, -1);
            }
            default -> {
                String color = pick(banners);
                c.setOnAir(l.x(a, l.half() - 1), 3, l.z(a, l.half() - 1), banner(color, l.sideFacing(-1)));
                c.setOnAir(l.x(a, 1 - l.half()), 3, l.z(a, 1 - l.half()), banner(color, l.sideFacing(1)));
            }
        }
    }

    /** A torch (or the theme's wall lamp) on the corridor wall at side {@code sign}. */
    void wallLamp(Link l, int a, int sign) {
        int s = sign * (l.half() - 1);
        c.setOnAir(l.x(a, s), 3, l.z(a, s), torch(l.sideFacing(-sign)));
    }

    /** The block column just outside a corridor wall, claimed for a niche. @return false if taken */
    boolean claimBehind(Link l, int a, int sign, int y0, int y1) {
        int s = sign * (l.half() + 1);
        return c.claim(new BlockBox(l.x(a, s), y0, l.z(a, s), l.x(a, s), y1, l.z(a, s)));
    }

    /** A light behind a grate set into the corridor wall. */
    boolean grateNiche(Link l, int a, int sign) {
        if (!claimBehind(l, a, sign, 0, Geometry.OPENING_TOP + 1)) {
            return false;
        }
        int wall = sign * l.half();
        int back = sign * (l.half() + 1);
        for (int y = 0; y <= Geometry.OPENING_TOP + 1; y++) {
            c.set(l.x(a, back), y, l.z(a, back), y == 2 || y == 3 ? grateLight : "#wall");
        }
        c.set(l.x(a, wall), 2, l.z(a, wall), grate);
        c.set(l.x(a, wall), 3, l.z(a, wall), grate);
        return true;
    }

    /** A statue standing in the corridor wall (its column replaces the wall), backed by wall. */
    boolean statueNiche(Link l, int a, int sign) {
        if (!claimBehind(l, a, sign, 0, Geometry.OPENING_TOP + 1)) {
            return false;
        }
        int wall = sign * l.half();
        int back = sign * (l.half() + 1);
        for (int y = 0; y <= Geometry.OPENING_TOP + 1; y++) {
            c.set(l.x(a, back), y, l.z(a, back), "#wall");
        }
        for (int y = 1; y <= 3; y++) {
            c.set(l.x(a, wall), y, l.z(a, wall), Canvas.AIR);
        }
        Side facing = Side.valueOf(l.sideFacing(-sign).toUpperCase(java.util.Locale.ROOT));
        statue(l.x(a, wall), 1, l.z(a, wall), facing, 3);
        c.setOnAir(l.x(a, wall), 4, l.z(a, wall), stairs(l.sideFacing(sign), true));
        return true;
    }

    // ---------------------------------------------------------------- antechamber

    /**
     * Rebuilds the corridor before the boss arena as a processional hall: the corridor's passage stays the walkway
     * (so its traps work as before), its walls become a colonnade with a railing, behind which aisles hold statues
     * and braziers under a high vault; the opening into the arena gets a gate.
     *
     * @return the antechamber's box, or null when there was no room to widen it (then it is decorated as a corridor)
     */
    BlockBox antechamber(Link l, Spaces.Arena arena) {
        int half = l.half();
        int inner = half - 1;
        int outer = half + 4;
        int height = 12;
        int len = l.length();
        if (len < 4) {
            corridor(l);
            gate(l, 6);
            return null;
        }
        BlockBox box = l.alongX()
                ? new BlockBox(l.corridor().minX(), 0, l.lateral() - outer, l.corridor().maxX(), height - 1,
                l.lateral() + outer)
                : new BlockBox(l.lateral() - outer, 0, l.corridor().minZ(), l.lateral() + outer, height - 1,
                l.corridor().maxZ());
        if (!c.claim(box, l.corridor())) {
            corridor(l);
            gate(l, 6);
            return null;
        }
        int step = l.step();
        int roomEnd = step > 0 ? l.start() : l.end();
        for (int u = 0; u < len; u++) {
            int a = roomEnd + step * u;
            boolean endRow = u == 0 || u == len - 1;
            int beyond = u == 0 ? a - step : a + step;
            for (int s = -outer; s <= outer; s++) {
                for (int y = 0; y < height; y++) {
                    if (Math.abs(s) <= inner && y >= 1 && y <= Geometry.OPENING_TOP) {
                        continue; // the walkway stays as it is
                    }
                    String state;
                    if (y == 0) {
                        state = Math.abs(s) <= inner ? (s == 0 ? runner : border) : "#floor";
                    } else if (y == height - 1 || Math.abs(s) == outer
                            || endRow && !c.solid(l.x(beyond, s), y, l.z(beyond, s))) {
                        state = "#wall";
                    } else {
                        state = Canvas.AIR;
                    }
                    c.set(l.x(a, s), y, l.z(a, s), state);
                }
            }
            // the vault: the hall narrows in steps towards the ceiling
            for (int k = 0; k < 3; k++) {
                int y = height - 2 - k;
                int s = outer - 3 + k;
                c.set(l.x(a, s), y, l.z(a, s), stairs(l.sideFacing(1), true));
                c.set(l.x(a, -s), y, l.z(a, -s), stairs(l.sideFacing(-1), true));
                for (int t = s + 1; t < outer; t++) {
                    c.set(l.x(a, t), y, l.z(a, t), ceiling);
                    c.set(l.x(a, -t), y, l.z(a, -t), ceiling);
                }
            }
        }
        // a colonnade on the old walls' line: columns at both ends and every third block, a railing between them,
        // so the walkway is exactly the corridor it was
        List<Integer> columns = new ArrayList<>();
        for (int u = len - 1; u > 0; u -= 3) {
            columns.add(u);
        }
        columns.add(0);
        for (int u = 0; u < len; u++) {
            int a = roomEnd + step * u;
            boolean column = columns.contains(u);
            for (int sign = -1; sign <= 1; sign += 2) {
                int s = sign * half;
                if (column) {
                    for (int y = 1; y <= height - 5; y++) {
                        c.set(l.x(a, s), y, l.z(a, s), pilaster);
                    }
                    c.setOnAir(l.x(a, s - sign), height - 5, l.z(a, s - sign), stairs(l.sideFacing(sign), true));
                } else {
                    c.set(l.x(a, s), 1, l.z(a, s), railing);
                }
                c.set(l.x(a, s), height - 4, l.z(a, s), trim);
            }
        }
        // the aisles: statues and braziers between the columns, banners and lamps on the outer walls
        int bayIndex = 0;
        for (int i = 0; i + 1 < columns.size(); i++) {
            int hi = columns.get(i);
            int lo = columns.get(i + 1);
            if (hi - lo < 2) {
                continue;
            }
            int a = roomEnd + step * ((hi + lo) / 2);
            for (int sign = -1; sign <= 1; sign += 2) {
                int s = sign * (half + 2);
                Side facing = Side.valueOf(l.sideFacing(-sign).toUpperCase(java.util.Locale.ROOT));
                if (bayIndex % 2 == 0) {
                    statue(l.x(a, s), 1, l.z(a, s), facing, 5);
                } else {
                    brazier(l.x(a, s), 1, l.z(a, s));
                }
                int wallS = sign * (outer - 1);
                c.setOnAir(l.x(a, wallS), 4, l.z(a, wallS), torch(l.sideFacing(-sign)));
            }
            bayIndex++;
        }
        for (int u : columns) {
            int a = roomEnd + step * u;
            for (int sign = -1; sign <= 1; sign += 2) {
                int wallS = sign * (outer - 1);
                c.setOnAir(l.x(a, wallS), 7, l.z(a, wallS), banner(pick(banners), l.sideFacing(-sign)));
            }
        }
        // chandeliers over the walkway, high above anything walking it
        for (int u = len - 3; u >= 2; u -= 5) {
            int a = roomEnd + step * u;
            chandelier(l.x(a, 0), l.z(a, 0), height - 1, 7);
        }
        gate(l, height - 2);
        return box;
    }

    /** The gate into the arena: a frame in the arena's wall around the opening, the theme's sculpture over it. */
    void gate(Link l, int top) {
        int inner = l.half() - 1;
        int wallA = l.step() > 0 ? l.end() + 1 : l.start() - 1;
        for (int y = 1; y <= top; y++) {
            for (int s = inner + 1; s <= inner + 2; s++) {
                c.setOnSolid(l.x(wallA, s), y, l.z(wallA, s), pilaster);
                c.setOnSolid(l.x(wallA, -s), y, l.z(wallA, -s), pilaster);
            }
        }
        for (int s = -inner - 2; s <= inner + 2; s++) {
            c.setOnSolid(l.x(wallA, s), Geometry.OPENING_TOP + 1, l.z(wallA, s), trim);
        }
        gateSculpture(l, wallA, Geometry.OPENING_TOP + 2, top);
    }

    // ---------------------------------------------------------------- arena

    /** Edge decoration only: wall bands, pilasters, fire behind grates, banners, a floor emblem, ceiling coffers. */
    void arena(Spaces.Arena arena) {
        BlockBox b = arena.box();
        int top = b.maxY() - 1;
        List<BlockBox> openings = List.of(arena.opening());
        for (Wall w : Geometry.walls(b)) {
            List<int[]> open = openingsOn(w, openings);
            for (int a = w.min() - 1; a <= w.max() + 1; a++) {
                for (int y = 1; y <= 2; y++) {
                    c.setOnSolid(w.x(a, 0), y, w.z(a, 0), wallBase);
                }
                c.setOnSolid(w.x(a, 0), 8, w.z(a, 0), trim);
                c.setOnSolid(w.x(a, 0), top - 2, w.z(a, 0), trim);
            }
            List<Integer> posts = new ArrayList<>();
            int spacing = 6;
            for (int k = -(w.length() / 2) / spacing; k <= (w.length() / 2) / spacing; k++) {
                int a = w.mid() + k * spacing;
                if (a > w.min() + 1 && a < w.max() - 1 && !nearOpening(open, a, 4)) {
                    posts.add(a);
                    for (int y = 1; y <= top; y++) {
                        c.setOnSolid(w.x(a, 0), y, w.z(a, 0), pilaster);
                    }
                }
            }
            int index = 0;
            for (Bay bay : bays(w, open, posts, top, Kind.ARENA)) {
                if (bay.width() >= 3 && !nearOpening(open, bay.mid(), 3)) {
                    arenaBay(bay, index++);
                }
            }
        }
        arenaFloor(b);
        arenaCeiling(b);
        arenaExtras(arena);
    }

    /** One stretch of arena wall: fire behind a grate low down, a glowing window or tall banners higher up. */
    void arenaBay(Bay bay, int index) {
        Wall w = bay.wall();
        int a = bay.mid();
        if (claimOutside(w, a, 0, 4)) {
            c.set(w.x(a, -1), 1, w.z(a, -1), "#wall");
            c.set(w.x(a, -1), 2, w.z(a, -1), arenaFire());
            c.set(w.x(a, -1), 3, w.z(a, -1), grateLight);
            c.set(w.x(a, -1), 4, w.z(a, -1), "#wall");
            c.set(w.x(a, -1), 0, w.z(a, -1), "#wall");
            c.set(w.x(a, 0), 2, w.z(a, 0), grate);
            c.set(w.x(a, 0), 3, w.z(a, 0), grate);
        }
        int top = bay.top();
        if (index % 2 == 0 && top >= 18) {
            int y0 = 11;
            if (claimOutside(w, a, y0 - 1, y0 + 4)) {
                for (int y = y0 - 1; y <= y0 + 4; y++) {
                    boolean glow = y >= y0 && y <= y0 + 3;
                    c.set(w.x(a, -1), y, w.z(a, -1), glow ? grateLight : "#wall");
                    if (glow) {
                        c.set(w.x(a, 0), y, w.z(a, 0), arenaWindow());
                    }
                }
            }
        } else {
            String color = pick(banners);
            for (int y = 13; y >= 10; y -= 2) {
                c.setOnAir(w.x(a, 1), y, w.z(a, 1), banner(color, w.in()));
            }
        }
    }

    /** Claims the column just outside a wall at {@code a} from {@code y0} to {@code y1}. */
    boolean claimOutside(Wall w, int a, int y0, int y1) {
        return c.claim(new BlockBox(w.x(a, -1), y0, w.z(a, -1), w.x(a, -1), y1, w.z(a, -1)));
    }

    /**
     * A window in wall {@code w} at {@code a} from {@code y0} to {@code y1}: glass in the wall plane, light behind it in
     * a claimed column outside. @return false when the column outside is taken
     */
    boolean window(Wall w, int a, int y0, int y1, String glass, String light) {
        if (!claimOutside(w, a, y0 - 1, y1 + 1)) {
            return false;
        }
        for (int y = y0 - 1; y <= y1 + 1; y++) {
            boolean glow = y >= y0 && y <= y1;
            c.set(w.x(a, -1), y, w.z(a, -1), glow ? light : "#wall");
            if (glow) {
                c.set(w.x(a, 0), y, w.z(a, 0), glass);
            }
        }
        return true;
    }

    String arenaFire() {
        return "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]";
    }

    String arenaWindow() {
        return grate;
    }

    /** Rim rings and a large emblem with light studs, all flush with the floor. */
    void arenaFloor(BlockBox b) {
        BlockBox in = b.interior();
        int y = b.minY();
        for (int x = in.minX(); x <= in.maxX(); x++) {
            for (int z = in.minZ(); z <= in.maxZ(); z++) {
                int edge = Math.min(Math.min(x - in.minX(), in.maxX() - x), Math.min(z - in.minZ(), in.maxZ() - z));
                if (edge == 0) {
                    c.setOnSolid(x, y, z, inlay);
                } else if (edge == 1) {
                    c.setOnSolid(x, y, z, inlay2);
                } else if (edge == 2) {
                    c.setOnSolid(x, y, z, border);
                }
            }
        }
        BlockPoint center = b.center(y);
        int radius = Math.min(in.sizeX(), in.sizeZ()) / 2 - 5;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                int d = (int) Math.round(dist);
                if (d > radius) {
                    continue;
                }
                String state = emblemBlock(d, dx, dz, radius);
                if (state != null) {
                    c.setOnSolid(center.x() + dx, y, center.z() + dz, state);
                }
            }
        }
    }

    /** The arena emblem: rings every few blocks, spokes, glowing studs spaced along the rings. */
    String emblemBlock(int d, int dx, int dz, int radius) {
        if (d <= 1) {
            return floorLight;
        }
        if (d == 2) {
            return inlay;
        }
        if (d == radius || d == radius - 7 || d == 6) {
            int around = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
            int studs = Math.max(8, d / 2);
            int pitch = 360 / studs;
            return around % pitch < (d == radius ? 3 : 4) ? floorLight : inlay;
        }
        if (d == 3 || d == radius - 1) {
            return inlay2;
        }
        if ((dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) && d > 6 && d < radius - 7) {
            return inlay;
        }
        return null;
    }

    void arenaCeiling(BlockBox b) {
        int y = b.maxY();
        for (int x = b.minX() + 1; x <= b.maxX() - 1; x++) {
            for (int z = b.minZ() + 1; z <= b.maxZ() - 1; z++) {
                if (c.role(x, y, z) == BlockRole.LIGHT) {
                    continue;
                }
                int lx = x - b.minX();
                int lz = z - b.minZ();
                if (lx % 6 == 0 || lz % 6 == 0) {
                    c.setOnSolid(x, y, z, trim);
                }
            }
        }
    }

    // ---------------------------------------------------------------- entrance hall

    /**
     * The entrance hall: the layout's small hall opened up into wings (built by {@link DungeonDecor}), with the exit
     * portal on the back wall, the revival monument on the left, the notice board on the right, columns along the
     * old walls and the entrance door framed as the way in.
     */
    void hall(Spaces.Hall hall) {
        BlockBox g = hall.box();
        int top = g.maxY() - 1;
        Side front = hall.front();
        Wall frontWall = new Wall(g, front);
        Wall backWall = new Wall(g, front.opposite());
        Side left = left(front);
        Wall leftWall = new Wall(g, left);
        Wall rightWall = new Wall(g, left.opposite());
        List<BlockBox> openings = List.of(hall.door());
        for (Wall w : Geometry.walls(g)) {
            bands(w, top);
            List<int[]> open = openingsOn(w, openings);
            for (int[] o : open) {
                doorFrame(w, o, top, false);
                entranceCrest(w, o);
            }
            List<Integer> posts = w.side() == front ? frontPosts(w, open) : posts(w, open);
            for (int i = 0; i < posts.size(); i++) {
                pilaster(w, posts.get(i), top, true);
            }
            cornice(w, top);
        }
        BlockBox core = hall.core();
        // revival monument on the left, its point in front of it facing the door
        int monumentA = leftWall.mid();
        int depth = 3;
        revivalMonument(leftWall.x(monumentA, depth), 1, leftWall.z(monumentA, depth), left.opposite());
        int pointD = depth + 3;
        revival = new BlockPoint(leftWall.x(monumentA, pointD), 1, leftWall.z(monumentA, pointD));
        revivalYaw = front.yaw();
        c.reserve(new BlockBox(revival.x() - 1, 1, revival.z() - 1, revival.x() + 1, 3, revival.z() + 1),
                Canvas.HARD);
        int labelX = leftWall.x(monumentA + 2, depth + 2);
        int labelZ = leftWall.z(monumentA + 2, depth + 2);
        if (c.setOnAir(labelX, 1, labelZ, "minecraft:dark_oak_sign[rotation=" + left.opposite().rotation()
                + ",waterlogged=false]")) {
            c.nbt(labelX, 1, labelZ, sign("{\"translate\":\"" + REVIVAL + "\"}"));
        }
        // notice board on the right
        board(rightWall, rightWall.mid());
        // exit portal on the back wall
        portal(backWall, backWall.mid());
        // columns where the small hall's side walls were (not in front of the monument or the board)
        for (Side side : new Side[]{left, left.opposite()}) {
            Wall old = new Wall(core, side);
            int keepClear = side == left ? monumentA : rightWall.mid();
            for (int a = old.min() + 1; a <= old.max() - 1; a += 4) {
                if (Math.abs(a - keepClear) <= 2) {
                    continue;
                }
                int x = old.x(a, 0);
                int z = old.z(a, 0);
                if (Math.abs((front.dx != 0 ? x : z) - (front.dx != 0 ? frontWall.x(0, 0) : frontWall.z(0, 0))) < 3) {
                    continue;
                }
                column(x, z, top);
            }
        }
        BlockPoint center = core.center(0);
        medallion(center.x(), 0, center.z(), Math.min(4, core.sizeX() / 2 - 1));
        runner(g, hall.door(), center, 4);
        for (Side side : new Side[]{left, left.opposite()}) {
            Wall w = new Wall(g, side);
            for (int a = w.min() + 2; a <= w.max() - 2; a += 4) {
                if (Math.abs(a - w.mid()) > 3) {
                    c.setOnAir(w.x(a, 1), 6, w.z(a, 1), banner(pick(banners), w.in()));
                }
            }
        }
        chandelier(center.x(), center.z(), g.maxY(), Math.max(5, top - 3));
        hallExtras(hall);
        scatter(g, top);
        for (Wall w : Geometry.walls(g)) {
            clutterAlong(w);
        }
    }

    static Side left(Side front) {
        return switch (front) {
            case NORTH -> Side.WEST;
            case WEST -> Side.SOUTH;
            case SOUTH -> Side.EAST;
            case EAST -> Side.NORTH;
        };
    }

    List<Integer> frontPosts(Wall w, List<int[]> open) {
        List<Integer> posts = new ArrayList<>();
        for (int[] o : open) {
            for (int a : new int[]{o[0] - 4, o[1] + 4}) {
                if (a > w.min() && a < w.max()) {
                    posts.add(a);
                }
            }
        }
        return posts;
    }

    /** A free-standing column with a base and a capital. */
    void column(int x, int z, int top) {
        Canvas.Piece piece = c.piece();
        for (int y = 1; y <= top; y++) {
            piece.add(x, y, z, pilaster);
        }
        if (!piece.force()) {
            return;
        }
        for (Side side : Side.values()) {
            c.setOnAir(x + side.dx, 1, z + side.dz, stairs(side.opposite().name, false));
            c.setOnAir(x + side.dx, top, z + side.dz, stairs(side.opposite().name, true));
        }
    }

    // ---------------------------------------------------------------- set pieces

    /** Walls, floor, ceiling and the doorway of a set-piece room, then the theme's content. */
    void setPiece(Spaces.SetPiece piece) {
        BlockBox b = piece.box();
        int top = b.maxY() - 1;
        List<BlockBox> openings = List.of(piece.door());
        for (Wall w : Geometry.walls(b)) {
            bands(w, top);
            List<int[]> open = openingsOn(w, openings);
            for (int[] o : open) {
                doorFrame(w, o, top, false);
            }
            List<Integer> posts = posts(w, open);
            for (int i = 0; i < posts.size(); i++) {
                pilaster(w, posts.get(i), top, i % 2 == 1);
            }
            cornice(w, top);
        }
        setPieceContent(piece);
        scatter(b, top);
        for (Wall w : Geometry.walls(b)) {
            clutterAlong(w);
        }
    }

    // ---------------------------------------------------------------- wear and clutter

    /** Wear: a share of the plain floor and wall blocks swapped for the theme's variants. */
    void scatter(BlockBox b, int top) {
        if (floorVariants.length > 0) {
            for (int x = b.minX() + 1; x <= b.maxX() - 1; x++) {
                for (int z = b.minZ() + 1; z <= b.maxZ() - 1; z++) {
                    if (c.state(x, b.minY(), z).equals("#floor") && r.nextDouble() < 0.16) {
                        c.set(x, b.minY(), z, pick(floorVariants));
                    }
                }
            }
        }
        if (wallVariants.length > 0) {
            for (Wall w : Geometry.walls(b)) {
                for (int a = w.min(); a <= w.max(); a++) {
                    for (int y = b.minY() + 1; y <= top; y++) {
                        if (c.state(w.x(a, 0), y, w.z(a, 0)).equals("#wall") && r.nextDouble() < 0.12) {
                            c.set(w.x(a, 0), y, w.z(a, 0), pick(wallVariants));
                        }
                    }
                }
            }
        }
    }

    /** Small items in free spots along a wall (one block in, on the floor). */
    void clutterAlong(Wall w) {
        int y = w.box().minY() + 1;
        for (int a = w.min(); a <= w.max(); a++) {
            int x = w.x(a, 1);
            int z = w.z(a, 1);
            if (c.isAir(x, y, z) && c.isAir(x, y + 1, z) && c.solid(x, y - 1, z) && r.nextDouble() < clutterChance) {
                clutter(x, y, z, w);
            }
        }
    }

    /** One small item at (x, y, z) against wall {@code w} (default: nothing). */
    void clutter(int x, int y, int z, Wall w) {
    }

    // ---------------------------------------------------------------- light fill

    /**
     * Brings a dark spot of a fighting space up with the theme's light: a lamp on the nearest wall in reach, else a
     * chandelier above it. @return whether something was placed
     */
    boolean lightUp(Spaces.Space space, int x, int z) {
        BlockBox in = space.inside();
        if (space.kind() == Kind.ROOM || space.kind() == Kind.HALL || space.kind() == Kind.SET_PIECE) {
            if (chandelier(x, z, space.ceiling(), Math.max(in.minY() + 4, space.ceiling() - 3))) {
                return true;
            }
            return c.setOnAir(x, space.ceiling() - 1, z, hangLamp);
        }
        if (space.kind() == Kind.ARENA) {
            return c.setOnSolid(x, in.minY() - 1, z, floorLight);
        }
        return false;
    }

    // ---------------------------------------------------------------- hooks

    /** A wall bay of a combat room (default: a banner). */
    void bay(Bay bay) {
        if (bay.width() >= 1) {
            Wall w = bay.wall();
            c.setOnAir(w.x(bay.mid(), 1), 5, w.z(bay.mid(), 1), banner(pick(banners), w.in()));
        }
    }

    /** A lamp on a pilaster at height {@code y} (default: a lamp hanging from a bracket). */
    void sconce(Wall w, int a, int y) {
        Canvas.Piece piece = c.piece();
        piece.add(w.x(a, 1), y + 1, w.z(a, 1), fence);
        piece.add(w.x(a, 1), y, w.z(a, 1), hangLamp);
        piece.commit();
    }

    /** An interior corner of a combat room; {@code sx/sz} point into the room. */
    void corner(int x, int z, int sx, int sz, int top) {
    }

    /** More dressing on a layout pillar. */
    void pillarDressing(int x, int z, int top) {
    }

    /** Anything else a theme puts in a combat room. */
    void roomExtras(Spaces.Room room, int ceilingY) {
    }

    /** A statue {@code height} blocks high standing at feet position (x, y, z), looking {@code facing}. */
    void statue(int x, int y, int z, Side facing, int height) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, pilaster);
        for (int k = 1; k < height - 1; k++) {
            piece.add(x, y + k, z, wallId);
        }
        piece.add(x, y + height - 1, z, skull("minecraft:skeleton_skull", facing.rotation()));
        piece.commit();
    }

    /** A brazier standing at feet position (x, y, z). */
    void brazier(int x, int y, int z) {
        Canvas.Piece piece = c.piece();
        piece.add(x, y, z, trim);
        piece.add(x, y + 1, z, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        piece.commit();
    }

    /** The monument fallen members revive at, centred at (x, z), its face towards {@code facing}. */
    void revivalMonument(int x, int y, int z, Side facing) {
        Canvas.Piece piece = c.piece();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                piece.add(x + dx, y, z + dz, slab(false));
            }
        }
        for (int k = 1; k <= 4; k++) {
            piece.add(x, y + k, z, pilaster);
        }
        piece.add(x, y + 5, z, standLamp);
        piece.force();
    }

    /** The notice board on the hall wall around {@code a}: the dungeon's name and notices on signs. */
    void board(Wall w, int a) {
        String[] texts = {"{\"translate\":\"" + NOTICE + "\"}", "{\"translate\":\"" + nameKey()
                + "\",\"bold\":true}", "{\"translate\":\"" + WARNING + "\"}"};
        for (int k = -1; k <= 1; k++) {
            for (int y = 2; y <= 3; y++) {
                c.setOnSolid(w.x(a + k, 0), y, w.z(a + k, 0), "minecraft:dark_oak_planks");
                if (c.setOnAir(w.x(a + k, 1), y, w.z(a + k, 1), "minecraft:dark_oak_wall_sign[facing=" + w.in()
                        + ",waterlogged=false]") && y == 3) {
                    c.nbt(w.x(a + k, 1), y, w.z(a + k, 1), sign(texts[k + 1]));
                }
            }
        }
    }

    static final String NOTICE = "sign.eternalrealm.dungeon.notice";
    static final String WARNING = "sign.eternalrealm.dungeon.warning";
    static final String REVIVAL = "sign.eternalrealm.dungeon.revival";

    /** Translation key of this instance's name (a dungeon, or the final raid). */
    String nameKey() {
        String id = c.layout.id();
        return com.eternalrealm.dungeon.Dungeons.byId(id).map(com.eternalrealm.dungeon.DungeonDefinition::translationKey)
                .orElse(com.eternalrealm.dungeon.Dungeons.FINAL_RAID.translationKey());
    }

    /** Block entity data of a waxed sign whose second line is the JSON text {@code line}. */
    static String sign(String line) {
        return "{front_text:{messages:['\"\"','" + line + "','\"\"','\"\"'],color:\"black\","
                + "has_glowing_text:0b},is_waxed:1b}";
    }

    /** The exit portal on the hall's back wall around {@code a}: a frame with a glowing veil. */
    void portal(Wall w, int a) {
        int height = 6;
        for (int y = 1; y <= height; y++) {
            for (int k = -2; k <= 2; k++) {
                boolean frame = Math.abs(k) == 2 || y == height;
                c.set(w.x(a + k, 0), y, w.z(a + k, 0), frame ? pilaster : portalGlow());
                if (!frame) {
                    c.setOnAir(w.x(a + k, 1), y, w.z(a + k, 1), portalVeil());
                }
            }
        }
        for (int k = -3; k <= 3; k++) {
            c.setOnAir(w.x(a + k, 1), height + 1, w.z(a + k, 1), stairs(w.out(), true));
        }
        c.setOnAir(w.x(a - 2, 1), 1, w.z(a - 2, 1), stairs(w.out(), false));
        c.setOnAir(w.x(a + 2, 1), 1, w.z(a + 2, 1), stairs(w.out(), false));
    }

    String portalGlow() {
        return "minecraft:sea_lantern";
    }

    String portalVeil() {
        return "minecraft:light_blue_stained_glass_pane";
    }

    /** Above the entrance door inside the hall. */
    void entranceCrest(Wall w, int[] o) {
        int a = (o[0] + o[1]) / 2;
        c.setOnAir(w.x(a, 1), o[3] + 2, w.z(a, 1), banner(banners[0], w.in()));
    }

    /** More dressing in the hall. */
    void hallExtras(Spaces.Hall hall) {
    }

    /** The gate's sculpture above the arena opening, in the arena wall plane at {@code wallA}. */
    void gateSculpture(Link l, int wallA, int y0, int top) {
        for (int y = y0; y <= Math.min(top, y0 + 2); y++) {
            c.setOnSolid(l.x(wallA, 0), y, l.z(wallA, 0), trim);
        }
    }

    /** The grand frame of the door that leads to the boss (in the last combat room). */
    void bossDoor(Wall w, int[] o, int top) {
        for (int y = o[3] + 2; y <= top; y++) {
            for (int a = o[0] - 2; a <= o[1] + 2; a++) {
                c.setOnSolid(w.x(a, 0), y, w.z(a, 0), (a + y) % 2 == 0 ? trim : pilaster);
            }
        }
        Side facing = Side.valueOf(w.in().toUpperCase(java.util.Locale.ROOT));
        for (int a : new int[]{o[0] - 3, o[1] + 3}) {
            if (a >= w.min() && a <= w.max()) {
                statue(w.x(a, 1), 1, w.z(a, 1), facing, 4);
            }
        }
    }

    /** The content of a set-piece room. */
    abstract void setPieceContent(Spaces.SetPiece piece);

    /** Theme decoration on the arena's edges. */
    void arenaExtras(Spaces.Arena arena) {
    }
}
